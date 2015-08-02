(ns com.frereth.server.db.core
  "Core database functionality

Used by both frereth.server and frereth.admin.
Which means I'll need two more repos when I decide to
split admin into its own.

There are already far too many of those. Stick with this
approach for now."
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [com.frereth.common.util :as common]
            [ribol.core :refer [raise]]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [datomic Datom]
           [datomic.db Db DbId]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema
;;; N.B. Database schema definitions belong in admin.
;;; This is definitely for Prismatic-style schema and
;;; the way it interacts with Stuart Sierra Components

(def protocols (s/enum :dev :free :ram :sql))
(def sql-drivers (s/enum :postgres))

(def UriDescription {:name s/Str
                     :port s/Int
                     :protocol protocols
                     :server s/Str   ; TODO: This should really be an IP address
                     (s/optional-key :driver) sql-drivers
                     (s/optional-key :user) s/Str
                     (s/optional-key :password) s/Str})

(defmulti build-connection-string :protocol)
(defmulti disconnect :protocol)

(s/defrecord URL [description :- UriDescription
                  connection-string :- s/Str]
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

;;; TODO: Surely these have already been captured somewhere

;; Q: What's a good way to specify that this might have
;; a length of 2-4?
(def where-clause [s/Symbol])

(def datomic-query {:find [s/Symbol]
                    (s/optional-key :in) [s/Symbol]
                    :where [[where-clause]]})

(def BaseTransaction {:db/id (s/either DbId s/Keyword)})
;; Really just a bunch of attribute/value pairs
(def UpsertTransaction (into BaseTransaction {s/Keyword s/Any}))
;;; Q: What's a good way to represent these?
;; UpsertTransactions get translated into these.
;; Note that retractions cannot be represented as a map
(def RetractTxn [(s/one (s/enum :db/add :db/retract) "Action")
                 (s/one (s/either s/Int s/Keyword) "Which entity")
                 s/Any])

(def TransactionResult {:db-before Db
                        :db-after Db
                        :tx-data [Datom]
                        :temp-ids {s/Int s/Int}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn sql-driver :- s/Str
  "Return the string representation of a JDBC driver"
  [key :- sql-drivers]
  (let [ms {:postgres "postgresql"}]
    (ms key)))

(s/defmethod build-connection-string :dev :- s/Str
  [{:keys [name port server]}]
  (str "datomic:dev://" server ":" port "/" name))

(s/defmethod build-connection-string :free :- s/Str
  [{:keys [name port server]}]
  (str "datomic:free://" server ":" port "/" name))

(s/defmethod build-connection-string :ram :- s/Str
  [{:keys [name]}]
  (str "datomic:mem://" name))

(s/defmethod build-connection-string :sql :- s/Str
  [{:keys [name port driver user password server]
    :or {port 5432
         user "datomic"
         password "datomic"
         server "localhost"}
    :as descr} :- UriDescription]
  (when-not driver
    (raise :missing-driver))
  ;; Next construct is weird because I've shadowed a builtin
  (str "datomic:sql://" name "?jdbc:" (sql-driver driver)
       "://" server ":" port "/datomic?user="
       user "&password=" password))

(s/defmethod build-connection-string :default :- s/Str
  [details]
  (raise {:not-implemented details}))

(s/defmethod disconnect :ram
  [descr :- UriDescription]
  ;; We really don't want to keep a reference around to these
  (let [cxn-str (build-connection-string descr)]
    (d/delete-database cxn-str)))

(s/defn general-disconnect
  "Generally don't want to delete the database

  This should be done when the entire process loses interest
  in the connection.
  Its results are async.

I'm mainly including this on the theory that I might want to switch
to a different connection during a reset, and multiple connections
really aren't legal (and probably won't work)."
  [descr :- UriDescription]
  (-> descr build-connection-string d/connect d/release))

(s/defmethod disconnect :sql
  [descr :- UriDescription]
  (general-disconnect descr))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn q :- [s/Any]
  "Convenience function for querying the database.
Probably shouldn't actually use very often.

In general, we should probably be running queries against database values
using d/q. But this approach does save some typing"
  [query :- datomic-query
   uri :- s/Str]
  (d/q query (-> uri d/connect d/db)))

(s/defn pretend-upsert! :- TransactionResult
  "Re-bind upsert! to this for experimentation
or unit testing

Then again, that would mean making it dynamic, which
seems like a bad idea. If nothing else, I think it
has noticeable performance impact because of the
var lookup"
  [uri :- s/Str
   txns :- [UpsertTransaction]]
  (let [conn (d/connect uri)
        database-value (d/db conn)]
    (d/with database-value txns)))

(s/defn upsert! :- TransactionResult
  [uri :- s/Str
   txns :- [UpsertTransaction]]
  (let [conn (d/connect uri)]
    @(d/transact conn txns)))
