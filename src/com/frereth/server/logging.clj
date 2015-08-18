(ns com.frereth.server.logging
  (require [com.stuartsierra.component :as component]
           [taoensso.timbre :as log
            :refer (trace debug info warn error fatal spy with-log-level)]
           [taoensso.timbre.appenders.3rd-party.rotor :as rotor]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Logger []
  component/Lifecycle

  (start
    [this]
    (log/merge-config!
     {:appenders {:rotor (rotor/rotor-appender {:path "logs/app.log"
                                                :max-size (* 512 1024)
                                                :backlog 5})}})
    (log/warn "FIXME: Log to a database instead")
    this)

  (stop
    [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [_]
  (->Logger))
