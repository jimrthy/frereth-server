(ns com.frereth.server.sentinal
  "Add a flag to tell everything to shut down"
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def monitor {:done (class (promise))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor [_] :- monitor
  (throw (ex-info "Obsolete" {:reason "Re-inventing wheel"}))
  {:done (promise)})
