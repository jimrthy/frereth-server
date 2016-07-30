(ns com.frereth.server.message-dispatcher
  (:require [com.stuartsierra.component :as component]
   [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord MessageDispatcher
    [event-interface
     plugin-manager]
  component/Lifecycle
  (start
      [this]
    this)
  (stop
      [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- MessageDispatcher
  [options]
  (map->MessageDispatcher options))
