(ns com.frereth.server.sentinal
  "Add a flag to tell everything to shut down")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor [_]
  (throw (ex-info "Obsolete"
                  {:replacement "Built into component-dsl"})))
