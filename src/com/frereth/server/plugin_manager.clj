(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

For starters, everything's going to be an App. I'll almost definitely
want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [com.frereth.common.schema :as frereth-schema]
            [com.frereth.system.sentinal :as sentinal]
            [com.stuartsierra.component :as cpt]
            [schema.core :as s])
  (:import [com.frereth.common.async_zmq EventPair]
           [com.frereth.common.zmq_socket ContextWrapper]
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema/specs

(declare load-plugin)
(s/defrecord PluginManager [ctx :- ContextWrapper
                            default-port :- s/Int
                            done :- sentinal/monitor
                            processes :- frereth-schema/atom-type ; {UUID EventPair}
                            root-namespace :- s/Symbol]
  cpt/Lifecycle
  (start [this]
    (let [process-key ['getty]
          getty-process (load-plugin this process-key)
          process-map {process-key getty-process}]
      (assoc this processes (atom process-map))))
  (stop [this]
    (when processes
      (doseq [p (vals @processes)]
        (component/stop p))
      (reset! processes {}))
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn load-plugin :- EventPair
  [this :- PluginManager
   path :- [s/Symbol]]
  ;; Note that we really need a callback for when the App exits
  (throw (ex-info "Start Here" {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- PluginManager
  [options]
  (map->PluginManager (select-in options :default-port :root-namespace)))
