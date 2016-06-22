(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

For starters, everything's going to be an App. I'll almost definitely
want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
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

(def ProcessMap
  "Really a  (atom {UUID EventInterface})"
  frereth-schema/atom-type)

(declare load-plugin)
(s/defrecord PluginManager [base-port :- s/Int
                            ctx :- ContextWrapper
                            done :- sentinal/monitor
                            processes :- ProcessMap]
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

(def SourceCode
  "This should really be defined in frereth-app instead

  But that really means upgrading this to use spec instead of schema, and I'm not
  ready for that just yet.

  And I have to start somewhere"
  {:guid UUID
   :name s/Str
   ;; Seems like this really should be something structured. But I'm still
   ;; trying to get the rope thrown across the gorge.
   :static-html s/Str})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn start-event-loop! :- EventInterface
  [this :- PluginManager
   source :- SourceCode]
  (let [ex-sock nil  ;; TODO: Need to create/bind this to a random(?) port
        ;; This shouldn't be created here.
        ;; We use it to send messages to the Client over 0mq.
        ;; It really seems like it should be supplied here as a
        ;; parameter, but where would it make more sense?
        in-chan (async/chan)
        external-reader nil
        external-writer nil
        -name (:name source)]
    (throw (ex-info "FIXME: Need meaningful values" {}))
    (async-zmq/event-system {:ex-sock ex-sock
                             :in-chan in-chan
                             :external-reader external-reader
                             :external-writer external-writer
                             :_name -name})))

(s/defn load-plugin :- EventInterface
  [this :- PluginManager
   path :- [s/Symbol]]
  (if-let [app (-> this :processes deref (get path))]
    app
    (let [path-names (map str path)
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
            (swap! (:processes this) assoc path (start-event-loop! source-code))))
        (throw (ex-info "Missing App" {:path resource-path}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- PluginManager
  [options]
  (map->PluginManager (select-in options :base-port)))
