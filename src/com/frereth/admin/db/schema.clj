(ns com.frereth.admin.db.schema
  "Because I have to start declaring schema somewhere"
  (:require [com.frereth.common.util :as util]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [datomic-schema.schema :refer [defdbfn
                                           fields
                                           generate-parts
                                           generate-schema
                                           part
                                           schema*]]
            [com.frereth.server.db.core :as db]
            [io.rkn.conformity :as conformity]
            [ribol.core :refer [raise]]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Prismatic Schema

;; Q: Is there anything to do w/ startup/shutdown?
(comment (s/defrecord DatabaseSchema [schema-resource-name :- s/Str
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
(def DatabaseSchema {:schema-resource-name s/Str
                     :uri db/UriDescription})

(def uniqueness (s/enum :db.unique/identity  ; attempts to insert dupe value for different entity will fail
                        :db.unique/value))   ; attempts to insert dupe value for entity w/ tempid will merge existing entity
(def value-types (s/enum :db.type/bigdec
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
                         :db.type/uuid))
(def cardinality-options (s/enum :db.cardinality/one
                                 :db.cardinality/many))

(def SchemaTransaction (into db/BaseTransaction {:db/ident s/Keyword
                                                 :db/cardinality cardinality-options
                                                 :db/valueType value-types
                                                 ;; TODO: We could also do alterations
                                                 :db.install/_attribute s/Keyword ; must be :db.part/db
                                                 (s/optional-key :db/doc) s/Str
                                                 (s/optional-key :db/fulltext) s/Bool ; Generate an eventually consistent fulltext search
                                                 (s/optional-key :db/index) s/Bool
                                                 (s/optional-key :db/isComponent) s/Bool ; ref attributes become sub-components
                                                 (s/optional-key :db/no-history) s/Bool
                                                 (s/optional-key :db.unique) uniqueness}))
;; This could be done as two steps in one transaction...but why?
(def PartitionTransaction (into db/BaseTransaction {:db/ident s/Keyword
                                                    ;; Must be :db.part/db
                                                    ;; Q: Does Prismatic Schema break if I specify that?
                                                    :db.install/_partition s/Keyword}))
(def IndividualTxn (s/either SchemaTransaction PartitionTransaction db/UpsertTransaction db/RetractTxn))
(def TransactionSequence [IndividualTxn])

;; Really just a sequence of names
(def PartTxnDescrSeq [s/Str])
(def AttrTypeTxn
  "Symbol that describes the type, mapped to a tuple of the primitive type
(as a keyword) and an optional set of options (most importantly, the doc string)"
  {s/Symbol {s/Symbol [(s/one s/Keyword "primitive-type") (s/optional [s/Any] "options")]}})

(def AttrTxnDescr
  "Transaction that builds an individual attribute

These are almost value-types,
but YuppieChef adds the namespace for us"
  {s/Symbol [(s/one s/Keyword "primitive-type")
             (s/optional #{(s/either s/Str s/Keyword)} "options")]})
(def AttrTxnDescrSeq [AttrTxnDescr])
(def TxnDescrSeq {:partitions PartTxnDescrSeq
                  :attribute-types [AttrTypeTxn]
                  :attributes AttrTxnDescrSeq})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn ^:always-validate do-schema-installation :- [{:norm-name (s/either s/Str s/Keyword)
                                                      :tx-index s/Int
                                                      ;; Q: What is tx-result?
                                                      :tx-result s/Any}]
  "Add schema/partition"
  [uri :- s/Str
   transactions :- TransactionSequence]
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

(s/defn load-transactions-from-resource :- TxnDescrSeq
  [resource-name :- s/Str]
  (log/debug "Getting ready to load schema from: " resource-name)
  (util/load-resource resource-name))

(defn schema-black-magic
  [attr-descr]
  (log/debug "Splitting " (util/pretty attr-descr) " into an attr/descr pair")
  (let [[attr field-descrs] attr-descr]
    (log/debug "Individual attribute: " attr
               "\nDescription:\n" field-descrs)
    ;; I'm duplicating some of the functionality from
    ;; Yuppiechef's library because he has it hidden
    ;; behind macros
    (schema* (name attr)
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

(s/defn expand-schema-descr
  "Isolating a helper function to start expanding attribute descriptions into transactions"
  [descr :- AttrTxnDescrSeq]
  (log/info "Expanding Schema Description:\n" (util/pretty descr))
  (map schema-black-magic
       descr))

(defn expanded-descr->schema
  "Take the output of expand-schema-descr (which should be identical
to the output of a seq of Yuppiechef's schema macro) and run it
through generate-schema to generate actual transactions"
  [attrs]
  (comment (map (fn [namespace]
                  (generate-schema namespace {:index-all? true}))
                attrs))
  (generate-schema attrs {:index-all? true}))

(s/defn expand-txn-descr :- TransactionSequence
  "Convert from a slightly-more-readable high-level description
to the actual datastructure that datomic uses"
  [descr :- TxnDescrSeq]
  (let [parts (map part (:partitions descr))
        attr-types (expand-schema-descr (:attribute-types descr))
        generated-schema (expanded-descr->schema attr-types)
        entities (nth descr 2)]
    (raise {:not-implemented "Still have to cope w/ :attributes"})
    [(concat (generate-parts parts)
             generated-schema)
     entities]))

(s/defn install-schema!
  [uri-description :- db/UriDescription
   tx-description :- TxnDescrSeq]
  (let [uri (db/build-connection-string uri-description)]
        (comment (log/debug "Expanding high-level schema transaction description:\n"
                            (util/pretty tx-description)
                            "from" resource-name))
        (let [[schema-tx primer-tx] (expand-txn-descr tx-description)]
          (comment (log/debug "Setting up schema using\n"
                              (util/pretty tx) "at\n" uri))
          (try
            (s/validate TransactionSequence schema-tx)
            (catch ExceptionInfo ex
              (log/error ex "Installing schema based on\n"
                         #_(util/pretty tx) schema-tx
                         "\nwhich has" (count schema-tx) "members"
                         "is going to fail")
              (doseq [step schema-tx]
                (try
                  (s/validate IndividualTxn step)
                  (catch ExceptionInfo ex
                    (log/error ex "Step:\n" step))))))
          (do-schema-installation uri schema-tx)

          ;; This has to happen as a Step 2:
          ;; We can't assign attributes to entities until
          ;; after the transaction that generates the schema
          (db/upsert! uri primer-tx)))  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn install-schema-from-resource!
  [this :- DatabaseSchema]
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

(s/defn ctor :- DatabaseSchema
  [config]
  (select-keys config [:schema-resource-name :uri]))
