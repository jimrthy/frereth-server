(ns com.frereth.admin.db.schema
  "Because I have to start declaring schema somewhere"
  (:require [com.frereth.common.util :as common]
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
(def AttrTxnDescr {s/Symbol
                   [;; These are almost value-types,
                    ;; but YuppieChef adds the namespace for us
                    (s/one s/Keyword "primitive-type")
                    (s/optional #{(s/either s/Str s/Keyword)} "options")]})
(def AttrTxnDescrSeq {s/Symbol AttrTxnDescr})
(def TxnDescrSeq [(s/one PartTxnDescrSeq "parts")
                  (s/one AttrTxnDescrSeq "attributes")])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn ^:always-validate do-schema-installation
  "Add schema/partition"
  [uri :- s/Str
   transactions :- TransactionSequence]
  (d/create-database uri)
  (let [conn (d/connect uri)
        norms-map {:frereth/base-data-platform {:txes [transactions]}}]
    ;; Returns nil on success
    (conformity/ensure-conforms conn norms-map [:fishfrog/base-schema])))

(s/defn load-transactions-from-resource :- TxnDescrSeq
  [resource-name :- s/Str]
  (common/load-resource resource-name))

(s/defn expand-schema-descr
  "Isolating a helper function to start expanding attribute descriptions into transactions"
  [descr :- AttrTxnDescrSeq]
  (map (fn [[attr field-descrs]]
         ;; I'm duplicating some of the functionality from
         ;; Yuppiechef's library because he has it hidden
         ;; behind macros
         (schema* (name attr)
                  {:fields (reduce (fn [acc [k v]]
                                     (comment (log/debug "Setting up field" k "with characteristics" v))
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
                                   field-descrs)}))
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
  (let [parts (map part (first descr))
        attrs (expand-schema-descr (second descr))
        generated-schema (expanded-descr->schema attrs)
        entities (nth descr 2)]
    [(concat (generate-parts parts)
             generated-schema)
     entities]))

(s/defn install-schema!
  [uri-description :- db/UriDescription
   tx-description :- TxnDescrSeq]
  (let [uri (db/build-connection-string uri-description)]
        (comment (log/debug "Expanding high-level schema transaction description:\n"
                            (common/pretty tx-description)
                            "from" resource-name))
        (let [[schema-tx primer-tx] (expand-txn-descr tx-description)]
          (comment (log/debug "Setting up schema using\n"
                              (common/pretty tx) "at\n" uri))
          (try
            (s/validate TransactionSequence schema-tx)
            (catch ExceptionInfo ex
              (log/error ex "Installing schema based on\n"
                         #_(common/pretty tx) schema-tx
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
    (comment (log/debug "Installing schema for\n" (common/pretty this)
                        "at" (common/pretty base-uri)
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
