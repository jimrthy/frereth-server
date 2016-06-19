(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

For starters, everything's going to be an App. I'll almost definitely
want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.frereth.common.schema :as frereth-schema]
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
  (let [absolute-path (into [(:root-namespace this)] path)
        path-names (map str absolute-path)
        ;; Q: What's the equivalent for python's os.path_separator?
        resource-path (clojure.string/join "/" path-names)]
    ;; Q: Would this be worth putting in a database instead?
    (if-let [url (io/resource resource-path)]
      ;; Note that we really need a callback for when the App exits
      (with-open [rdr (io/reader url)]
        (let [source-code (edn/read rdr)]
          ;; Note that this is really where sandboxes start to come
          ;; into play. Maybe something like OSGi or even clojail
          ;; (that's what convinced me to take a serious look it
          ;; clojure in the first place, after all).
          (throw (ex-info "Start Here" {}))))
      (throw (ex-info "Missing App" {:path resource-path})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- PluginManager
  [options]
  (map->PluginManager (select-in options :default-port :root-namespace)))
