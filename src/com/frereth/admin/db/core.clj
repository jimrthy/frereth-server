(ns com.frereth.admin.db.core
  "Fundamental 12-factor rule: admin functions should be completely separate from standard user's interface"
  (:require [com.frereth.admin.db.schema :as db-schema]
            [com.stuartsierra.component :as component]
            [ribol.core :refer [raise]]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn configure-base-schema
  [system]
  (let [connection-string (-> system :database-uri :connection-string)]
    (log/info "Running schema installation transaction")
    (db-schema/install-schema! (:database-schema system) connection-string)
    (log/info "Schema installed")))
