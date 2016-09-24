(ns com.frereth.admin.db.core
  "Fundamental 12-factor rule: admin functions should be completely separate from standard user's interface"
  (:require [clojure.spec :as s]
            [com.jimrthy.substratum.platform :as db-schema]
            [com.stuartsierra.component :as component]
            [hara.event :refer (raise)]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;; TODO: Need to spec this
(s/def ::database-schema any?)
(s/def ::connection-string string?)
(s/def ::database-uri (s/keys :req-un [::connection-string]))
;; This must be a duplicate from somewhere
;; TODO: Find that and reference it from here without getting circular
(s/def ::db-owner (s/keys :req-un [::database-schema ::database-uri]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef configure-base-schema
        :args (s/cat :this :db-owner)
        ;; Q: What does this return on success?
        :ret any?)
(defn configure-base-schema
  [system]
  (let [connection-string (-> system :database-uri :connection-string)]
    (log/info "Running schema installation transaction")
    (let [result
          (db-schema/install-schema! (:database-schema system) connection-string)]
      (log/info "Schema installed")
      result)))
