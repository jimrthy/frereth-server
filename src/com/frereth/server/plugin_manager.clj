(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

  For starters, everything's going to be an App. I'll almost definitely
  want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.frereth.common.async-zmq :as async-zmq]
            [com.frereth.common.schema :as frereth-schema]
            [component-dsl.done-manager :as sentinal]
            [com.stuartsierra.component :as cpt]
            [schema.core :as s])
  (:import [com.frereth.common.async_zmq EventPairInterface]
           [com.frereth.common.zmq_socket ContextWrapper]
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema/specs

(def ProcessMap
  "Really a  (atom {UUID EventPairInterface})

  Note that this is really a fairly drastic departure from my initial
  plans (or, at least, it looks that way as I'm trying to remember where
  I was last time).

  My original intention was to have 3 distinct and long-lived EventLoop
  instances with very firm delineations between duties.

  That doesn't make the current plan of just creating one EventLoop per
  App all that inferior (it does limit Apps to available ports, but that
  doesn't seem too awful), but I do need to decide whether I want to segregate
  Apps at the socket level or just have them all sharing the same socket.

  There are always trade-offs, but one socket/event loop per App really
  does seem like the obvious approach here.
  TODO: Think about the down sides. They must exist.

  First obvious one: more holes in your firewall."
  frereth-schema/atom-type)

(declare load-plugin)
(s/defrecord PluginManager [base-port :- s/Int
                            ctx :- ContextWrapper
                            done ; Should probably turn that promise into a record
                            processes :- ProcessMap]
  cpt/Lifecycle
  (start [this]
    (let [this (assoc this :processes (atom {}))
          ;; TODO: This needs to be configurable
          process-key [:login]
          getty-process (load-plugin this process-key)]
      this))
  (stop [this]
    (when processes
      (doseq [p (vals @processes)]
        (cpt/stop p))
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

(s/defn start-event-loop! :- EventPairInterface
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
    ;; Just initialized pretty much everything to nil as a place-holder
    ;; to get the basic app shape slapped together
    (throw (ex-info "FIXME: Need meaningful values" {}))
    ;; Q: What did I really intend to happen here?
    ;; Maybe async-zmq/ctor-interface?
    ;; Except that it would still need to be started
    (async-zmq/event-system {:ex-sock ex-sock
                             :in-chan in-chan
                             :external-reader external-reader
                             :external-writer external-writer
                             :_name -name})))

(defn process-map
  [process-key]
  (process-key {:login '[getty]
                :shell '[sh]}))

(s/defn load-plugin :- EventPairInterface
  [this :- PluginManager
   ;; Note that this is deliberately over-simplified
   ;; Need to set up something like a PATH environment
   ;; That we can both search and override (for the sake
   ;; of things like python virtualenv)
   path :- [s/Symbol]]
  ;; This is really just a stepping stone. Apps have to go to a database
  ;; rather than having anything that resembles file system access
  ;; (even though, yes, eventually a database means a file)
  (if-let [processes-atom (:processes this)]
    (if-let [app-key (get @processes-atom path)]
      (if-let [app-path (process-map app-key)]
        (let [path-names (map str app-path)
              ;; Q: What's the equivalent for python's os.path_separator?
              ;; A: Don't care. Since this is really going to be
              ;; querying a database anyway, this approach is just
              ;; an over-simplification that must go away. It's
              ;; strictly for the sake of getting that rope thrown
              ;; across the gorge.
              resource-path (clojure.string/join "/" path-names)
              file-path (str resource-path ".edn")]
          (if-let [url (io/resource app-path)]
            ;; Note that we really need a callback for when the App exits
            (with-open [rdr (io/reader url)]
              (let [source-code (edn/read rdr)
                    result (start-event-loop! source-code)]
                ;; Note that this is really where sandboxes start to come
                ;; into play. Maybe something like OSGi or even clojail
                ;; (that's what convinced me to take a serious look it
                ;; clojure in the first place, after all).
                (swap! (:processes this) assoc path result)
                result))
            (throw (ex-info "Missing App" {:path resource-path}))))
        (throw (ex-info "No App registered" {:path app-key})))
      (throw (ex-info "Unknown App" {:path path})))
    (throw (ex-info "Trying to load a plugin with an unstarted PluginManager" (assoc this
                                                                                     :requested path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- PluginManager
  [options]
  (map->PluginManager (select-keys options [:base-port])))
