(ns com.frereth.server.logging
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Logger []
  component/Lifecycle

  (start
    [this]
    (log/merge-config!
     ;; Q: What happened to log rotator?
     ;; A: Doesn't matter.
     {:appenders {:rotor (comment (rotor/rotor-appender {:path "logs/app.log"
                                                         :max-size (* 512 1024)
                                                         :backlog 5}))}})
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
