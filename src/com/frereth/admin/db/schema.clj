(ns com.frereth.admin.db.schema
  "Because I have to start declaring schema somewhere

Note that pretty much all these pieces belong in substratum"
  (:require [clojure.spec :as s]
            [com.frereth.common.util :as util]
            [com.frereth.server.db.core :as db]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [datomic-schema.schema
             :as yuppie-schema]
            [hara.event :refer (raise)]
            [io.rkn.conformity :as conformity]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::schema-resource-name string?)
(s/def ::uri :com.frereth.server.db.core/uri-description)

;; Q: Is there anything to do w/ startup/shutdown?
(comment (defrecord DatabaseSchema [schema-resource-name :- s/Str
                                      uri :- db/UriDescription]
           component/Lifecycle
           (start
            [this]
            (raise :not-implemented))
           (stop
            [this]
            (raise :not-implemented))))
;; A: Until there is, just use a plain hashmap
;; It's tempting to make (start) convert the uri to a connection-string.
;; That temptation seems like a mistake.
(s/def ::database-schema (s/keys :req [::schema-resource-name ::uri]))
;; The parameters to create that
(s/def ::opt-database-schema (s/keys :opt [::schema-resource-name ::uri]))

(s/def ::uniqueness #{:db.unique/identity ; attempts to insert dupe value for different entity will fail
                      :db.unique/value})  ; attempts to insert dupe value for entity w/ tempid will merge existing entity

(s/def ::value-types #{:db.type/bigdec
                       :db.type/bigint
                       :db.type/boolean
                       :db.type/bytes
                       :db.type/double
                       :db.type/float
                       :db.type/instant
                       :db.type/keyword
                       :db.type/long
                       :db.type/ref
                       :db.type/string
                       :db.type/uri
                       :db.type/uuid})

(s/def ::cardinality-options #{:db.cardinality/one
                               :db.cardinality/many})

(s/def :db/ident keyword?)
(s/def :db/cardinality ::cardinality-options)
(s/def :db/valueType ::value-types)
(s/def :db.install/_attribute #(= :db.part/db %))
(s/def :db/doc string?)
(s/def :db/fulltext boolean?) ; Generate an eventually consistent fulltext search
(s/def :db/index boolean?)
(s/def :db/isComponent boolean?) ; ref attributes become sub-components
(s/def :db/no-history boolean?)
(s/def :db/unique ::uniqueness)
;;; For adding schema
(s/def ::schema-transaction (s/merge :com.frereth.server.db.core/base-transaction
                                     (s/keys :req [:db/ident
                                                   :db/cardinality
                                                   :db/valueType
                                                   ;; TODO: We could also do alterations
                                                   :db.install/_attribute]
                                             :opt [:db/doc
                                                   :db/fulltext
                                                   :db/index
                                                   :db/isComponent
                                                   :db/no-history
                                                   ;; FIXME: Before I started porting, this was :db.unique
                                                   ;; Which I'm about 90% certain was wrong.
                                                   ;; But it was working (for a very loose, not-really-
                                                   ;; implemented definition of "working"), so I'm
                                                   ;; leery about changing this without double-checking.
                                                   ;; But the previous version quit compiling, making
                                                   ;; this that much more likely
                                                   :db/unique])))

(s/def :db.install/_partition #(= :db.part/db %))
;; This could be done as two steps in one transaction...but why?
(s/def ::partition-transaction (s/merge :com.frereth.server.db.core/base-transaction
                                        (s/keys :req [:db/ident
                                                      :db.install/_partition])))

(s/def ::individual-txn (s/or ::schema-transaction
                              ::partition-transaction
                              :com.frereth.server.db.core/upsert-transaction
                              :com.frereth.server.db.core/retract-txn))
(s/def ::transaction-sequence (s/coll-of ::individual-txn))

;; Really just a sequence of names
(s/def ::part-txn-descr-seq (s/coll-of string?))
(s/def ::attribute-options (s/cat :primitive-type ::primitive-type
                                  ;; Punt on this one for now
                                  :options (s/coll-of any?)))
(s/def ::type-description (s/map-of symbol? ::attribute-options))
;; Symbol that describes the type, mapped to a tuple of the primitive type
;; (as a keyword) and an optional set of options (most importantly, the doc string)
(s/def ::attr-type-txn
  (s/map-of symbol? ::type-description))

;;; Transaction that builds an individual attribute
;;;
;;; These are almost value-types,
;;; but YuppieChef adds the namespace for us
(s/def ::attr-txn-descr (s/map-of symbol? (s/coll-of ::attribute-options)))
(s/def ::attr-txn-descr-seq (s/coll-of ::attr-txn-descr))
(s/def ::partitions ::part-txn-descr-seq)
(s/def ::attribute-types (s/coll-of ::attr-type-txn))
(s/def ::attriutes ::attr-txn-descr-seq)
(s/def ::txn-descr-seq (s/keys :req [::partitions ::attribute-types ::attributes]))

(s/def ::norm-name (s/or :string string?
                         :keyword keyword?))
(s/def ::tx-index integer?)
;;; Q: What is this?
(s/def ::tx-result any?)
(s/def ::conformation (s/keys :req-un [::norm-name
                                       ::tx-index
                                       ::tx-result]))
(s/def ::conformation-sequence (s/coll-of ::conformation))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;; TODO: ^:always-validate
(s/fdef do-schema-installation
        :args (s/cat :uri string?
                     :transactions ::transaction-sequence)
        :ret ::conformation-sequence)
(defn do-schema-installation
  "Add schema/partition"
  [uri transactions]
  (d/create-database uri)
  (let [conn (d/connect uri)
        ;; Need a better way to hide the API.
        ;; Since any given conformation will only be applied once per
        ;; name, the thing calling this should really be setting this up.
        ;; And, honestly, it's not like it's asking a lot to have them
        ;; wrap the transactions into a norms-map shape.
        ;; Or maybe I shouldn't be trying to hide it in the first place.
        norms-map {:frereth/base-data-platform {:txes [transactions]}}]
    (let [result(conformity/ensure-conforms conn norms-map)]
      (log/debug "ensure-conforms returned:\n" result)
      result)))

(s/fdef load-transactions-from-resource
        :args (s/cat :resource-name string?)
        :ret ::txn-descr-seq)
(defn load-transactions-from-resource
  [resource-name]
  (log/debug "Getting ready to load schema from: " resource-name)
  (util/load-resource resource-name))

(defn schema-black-magic
  [attr-descr]
  (log/debug "Splitting "
             attr-descr
             " into an attr/descr pair")
  ;; This fails because we have a map rather than
  ;; the key/value pairs we'd get if we were mapping over
  ;; a hashmap
  ;; Q: But why is the log message about splitting disappearing?
  ;; A: It isn't. It's just showing up far later than expected.
  ;; For now, at least, chalk it up to laziness and fix the root problem
  (comment
    (raise {:whats-going-on? :Im-definitely-getting-here}))
  (let [[attr field-descrs] attr-descr]
    (log/debug "Individual attribute: " attr
               "\nDescription:\n" field-descrs)
    ;; I'm duplicating some of the functionality from
    ;; Yuppiechef's library because he has it hidden
    ;; behind macros
    (yuppie-schema/schema* (name attr)
             {:fields (reduce (fn [acc [k v]]
                                (comment )(log/debug "Setting up field" k "with characteristics" v)
                                (assoc acc (name k)
                                       (if (= (count v) 1)
                                         ;; If there isn't an option set,
                                         ;; use vec to make sure one gets appended
                                         (do
                                           (comment (log/debug "Adding default empty set"))
                                           (conj (vec v) #{}))
                                         (if (= (count v) 2)
                                           v
                                           (raise {:illegal-field-description v
                                                   :field-id k})))))
                              {}
                              field-descrs)})))

;;; Q: What does this return?
(s/fdef expand-schema-descr
        :args (s/cat :descr ::attr-txn-descr-seq))
(defn expand-schema-descr
  "Isolating a helper function to start expanding attribute descriptions into transactions"
  [descr]
  (log/info "Expanding Schema Description:\n"
            (util/pretty descr)
            "\na " (class descr))
  (map schema-black-magic
       descr))

(defn expanded-descr->schema
  "Take the output of expand-schema-descr (which should be identical
to the output of a seq of Yuppiechef's schema macro) and run it
through generate-schema to generate actual transactions"
  [attrs]
  (log/debug "expanded-descr->schema -- calling generate-schema on:\n"
             (util/pretty attrs) "\na " (class attrs))
  (comment (map (fn [namespace]
                  (yuppie-schema/generate-schema namespace {:index-all? true}))
                attrs))
  (yuppie-schema/generate-schema attrs {:index-all? true}))

(s/fdef expand-txn-descr
        :args (s/cat :descr ::txn-descr-seq)
        :ret ::transaction-sequence)
(defn expand-txn-descr
  "Convert from a slightly-more-readable high-level description
to the actual datastructure that datomic uses"
  [descr]
  (let [parts (map yuppie-schema/part (:partitions descr))
        attr-types (expand-schema-descr (:attribute-types descr))
        generated-schema (expanded-descr->schema attr-types)
        entities (:attributes descr)]
    {:structure (concat (yuppie-schema/generate-parts parts)
                        generated-schema)
     :data entities}))

(s/fdef install-schema!
        :args (s/cat :uri-description :com.frereth.system.db.core/uri-description
                     :tx-description :txn-descr-seq)
        :ret any?)  ; Q: What does this return?
(defn install-schema!
  [uri-description tx-description]
  (let [uri (db/build-connection-string uri-description)]
    (comment) (log/debug "Expanding high-level schema transaction description:\n"
                         (util/pretty tx-description))
        (let [{:keys [structure data]} (expand-txn-descr tx-description)]
          (comment) (log/debug "Setting up schema using\n"
                               (util/pretty structure) "at\n" uri)
          (try
            (if (s/valid? ::transaction-sequence structure)
              (do
                (doseq [step structure]
                  (do-schema-installation uri structure))
                          ;; This has to happen as a Step 2:
                ;; We can't assign attributes to entities until
                ;; after the transaction that generates the schema
                (db/upsert! uri data))
              (log/error (s/explain ::transaction-sequence structure)
                         "Installing schema based on\n"
                         #_(util/pretty tx) structure
                         "\nwhich has" (count structure) "members"
                         "wouldn't"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef install-schema-from-resource!
        :args (s/cat :this ::database-schema)
        :ret any?)
(defn install-schema-from-resource!
  [this]
  (let [uri-description (-> this :uri :description)
        resource-name (:schema-resource-name this)]
    (comment (log/debug "Installing schema for\n" (util/pretty this)
                        "at" (util/pretty base-uri)
                        "using" (-> base-uri :description :protocol)
                        "\nfrom" resource-name))
    (if-let [tx-description (load-transactions-from-resource resource-name)]
      (install-schema! uri-description tx-description)
      (raise {:missing-transactions this
              :resource-name (:schema-resource-name this)
              :keys (keys this)}))))

(s/fdef ctor
        :args (s/cat :config ::opt-database-schema)
        :ret ::database-schema)
(defn ctor
  [config]
  (select-keys config [:schema-resource-name :uri]))
