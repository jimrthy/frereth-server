(ns com.frereth.server.db.core
  "Core database functionality

Used by both frereth.server and frereth.admin.
Which means I'll need two more repos when I decide to
split admin into its own.

There are already far too many of those. Stick with this
approach for now.

Then again...

TODO: Most/all of this really belongs in substratum

Actually, most of them belong in datomic"
  (:require [clojure.spec :as s]
            [com.frereth.common.schema :as fr-skm]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [clojure.spec :as s]
            [com.frereth.common.util :as common]
            [hara.event :refer (raise)]
            [taoensso.timbre :as log])
  (:import [datomic Datom]
           [datomic.db Db DbId]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::protocol #{::dev ::free ::ram ::sql})
(s/def ::sql-driver #{::postgres})

(s/def ::name :cljeromq.common/zmq-address)
(s/def ::port :cljeromq.common/port)
(s/def ::server :cljeromq.common/zmq-address)
(s/def ::user string?)
(s/def ::password string?)
;; Tempting to try to find overlap with cljeromq.common/zmq-url
;; They're two very different beasts.
;; TODO: Narrow the details down based on the protocol.
;; The ::ram protocol is significantly different from, say, ::dynamo-db
(s/def ::uri-description (s/keys :req [::name ::port ::protocol ::server]
                                 :opt [::sql-driver ::user ::password]))

(s/def ::connection-string string?)
(s/def ::url (s/keys :req [::uri-description ::connection-string]))

(s/def ::protocol-type keyword?)
(defmulti protocol-type ::protocol-type)
(s/def ::connection-string-builder (s/multi-spec protocol-type ::protocol))
(defmulti build-connection-string
  "Convert a URI description map to the string that datomic actually uses"
  :protocol)

;;; Q: Does this really use the same spec as the build-connection-string?
(s/def ::disconnect-spec (s/multi-spec protocol-type ::protocol))
(defmulti disconnect
  "Disconnect from a database's URI description"
  :protocol)

;;; The description of datomic datalog
;;; TODO: Surely these have already been captured somewhere

(s/def ::find (s/coll-of symbol?))
(s/def ::in (s/coll-of symbol?))
(s/def ::where-clause (s/or :tuple-2 (s/tuple symbol? symbol?)
                            :tuple-3 (s/tuple symbol? symbol? symbol?)
                            :tuple-4 (s/tuple symbol? symbol? symbol? symbol?)))
(s/def ::datomic-query (s/keys :req-un [::find ::where]
                               :opt-un [::in]))

(s/def :db/id (s/or :id #(instance? DbId %)
                    :key keyword?))
(s/def ::base-transaction (s/keys :req [:db/id]))
;; Really just a bunch of attribute/value pairs
(s/def ::upsert-transaction (s/merge ::base-transaction
                                     (s/map-of keyword? any?)))
;;; Q: What's a good way to represent these?
;; UpsertTransactions get translated into these.
;; Note that retractions cannot be represented as a map
(s/def ::action #{:add :retract})
(s/def ::which (s/or :entity-id integer?
                     :keyname keyword?))
(s/def ::retract-txn (s/tuple ::action ::which any?))

(s/def ::db-before (fr-skm/class-predicate Db))
(s/def ::db-after (fr-skm/class-predicate Db))
(s/def ::tx-data (s/coll-of (fr-skm/class-predicate Datom)))
(s/def ::temp-ids (s/map-of integer? integer?))
(s/def ::transaction-result (s/keys :req-un [::db-before ::db-after ::tx-data ::temp-ids]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/fdef sql-driver
        :args (s/cat :key ::sql-drivers)
        :ret string?)
(defn sql-driver
  "Return the string representation of a JDBC driver"
  [key]
  (let [ms {:postgres "postgresql"}]
    (ms key)))

(defmethod protocol-type ::dev [_]
  (s/keys :req [::name ::port ::server]))
(defmethod build-connection-string ::dev
  [{:keys [name port server]}]
  (str "datomic:dev://" server ":" port "/" name))

(defmethod protocol-type ::free [_]
  (s/keys :req [::name ::port ::server]))
(defmethod build-connection-string ::free
  [{:keys [name port server]}]
  (str "datomic:free://" server ":" port "/" name))

(defmethod protocol-type ::ram [_]
  (s/keys :req [::name]))
(defmethod build-connection-string ::ram
  [{:keys [name]}]
  (str "datomic:mem://" name))

(defmethod protocol-type ::sql [_]
  (s/keys :req [::name ::port ::driver ::user ::password ::server]))
(defmethod build-connection-string ::sql
  [{:keys [::name ::port ::driver ::user ::password ::server]
    ;; Q: What, if any, of these defaults make sense?
    :or {port 5432
         user "datomic"
         password "datomic"
         server "localhost"}
    :as descr}]
  (when-not driver
    (raise :missing-driver))
  ;; Next construct is weird because I've shadowed a builtin
  (str "datomic:sql://" name "?jdbc:" (sql-driver driver)
       "://" server ":" port "/datomic?user="
       user "&password=" password))

(defmethod protocol-type :default [_]
  any?)
(defmethod build-connection-string :default
  [details]
  (raise {:not-implemented details}))

(defmethod disconnect ::ram
  [descr]
  ;; We really don't want to keep a reference around to these
  (let [cxn-str (build-connection-string descr)]
    (d/delete-database cxn-str)))

(s/fdef general-disconnect
        :args (s/cat :descr ::uri-description)
        :ret any?)
(defn general-disconnect
  "Generally don't want to delete the database

  This should be done when the entire process loses interest
  in the connection.
  Its results are async.

I'm mainly including this on the theory that I might want to switch
to a different connection during a reset, and multiple connections
really aren't legal (and probably won't work)."
  [descr]
  (-> descr build-connection-string d/connect d/release))

(defmethod disconnect ::sql
  [descr]
  (general-disconnect descr))

(defmethod disconnect :default
  [descr]
  (throw (ex-info "Not Implemented"
                  {:description descr})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Components

(defrecord url [description connection-string]
  component/Lifecycle
  (start
   [this]
   "Main point is to verify that we can connect
Although this also serves to cache the connection"
   (comment (log/debug "Starting up the URL. Description: " (common/pretty description)
                       "with keys:" (keys description)))
   (let [connection-string (build-connection-string description)]
     (when (d/create-database connection-string)
       (log/warn "Created new database"))
     (d/connect connection-string)
     (assoc this :connection-string connection-string)))
  (stop
   [this]
   (disconnect description)
   ;; Can't just dissoc...that would return
   ;; an ordinary map that couldn't be started
   ;; At least, that seems to be what I'm picking up
   ;; from the mailing list
   (assoc this :connection-string nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef q
        :args (s/cat :query ::datomic-query
                     :uri string?)
        :ret (s/coll-of any?))
(defn q
  "Convenience function for querying the database.
Probably shouldn't actually use very often.

In general, we should probably be running queries against database values
using d/q. But this approach does save some typing"
  [query uri]
  (d/q query (-> uri d/connect d/db)))

(s/fdef pretend-upsert!
        :args (s/cat :uri string?
                     :txns (s/coll-of ::upsert-transaction))
        :ret ::transaction-result)
(defn pretend-upsert!
  "Re-bind upsert! to this for experimentation
or unit testing

Then again, that would mean making it dynamic, which
seems like a bad idea. If nothing else, I think it
has noticeable performance impact because of the
var lookup"
  [uri txns]
  (let [conn (d/connect uri)
        database-value (d/db conn)]
    (d/with database-value txns)))

(s/fdef upsert!
        :args (s/cat :uri string?
                     :txns (s/coll-of ::upsert-transaction))
        :ret ::transaction-result)
(defn upsert!
  [uri txns]
  (let [conn (d/connect uri)]
    @(d/transact conn txns)))
