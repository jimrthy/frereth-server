(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

  For starters, everything's going to be an App. I'll almost definitely
  want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
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

(declare load-plugin!)
(s/defrecord PluginManager [base-port :- s/Int
                            ctx :- ContextWrapper
                            done ; Should probably turn that promise into a record
                            processes :- ProcessMap]
  cpt/Lifecycle
  (start [this]
    (let [this (assoc this :processes (atom {}))
          ;; TODO: This needs to be configurable
          process-key :login
          getty-process (load-plugin! this process-key)]
      this))
  (stop [this]
    (when processes
      (doseq [p (vals @processes)]
        (cpt/stop p))
      (reset! processes {}))
    this))

(def base-event {:event/handler [s/Any]  ;; Really a fn definition
                 :event/events frereth-schema/korks})

(def dom-event
  ;; Q: Limit :event/events to standard legal DOM events?
  (into base-event {:event/element s/Str ; what triggered this event?
                    ;; Which elements of the tree of the DOM that we created
                    ;; should this event handler know about?
                    (s/Maybe :event/dependencies) [s/Str]}))

(def internal-event (into base-event {}))

(def render-event (s/either dom-event internal-event))

(def rendering-engines
  "Most interesting Apps will involve multiple rendering engines.

Otherwise, why would you bother?"
  (s/Either
   rendering-engine-types
   {s/Keyword rendering-engine-types}))

(def sem-ver
  "This is where I really need to switch to Spec.

Since, really, this is a Sequential of three ints"
  [s/Int])

(def SourceCode
  "This should really be defined in frereth-app instead

  But that really means upgrading this to use spec instead of schema, and I'm not
  ready for that just yet.

  And I have to start somewhere"
  ;; TODO: All these keys need to be namespaced
  {
   :app/description s/Str  ; human-readable
   ;; These are really the Z85-encoded versions.
   ;; Don't need the server's public key here.
   :app/keys {:private s/Str
              :public s/Str}
   :app/name s/Str
   ;; This is really the initial state
   :app/state {:s/Any s/Any}
   :app/uuid UUID
   :app/version sem-ver

   ;; What version of frereth was this written to run against?
   ;; Note that, by definition, only the major/minor versions matter
   ;; here. Since breaking changes require incrementing those.
   ;; The downside to that approach is that, really, bug fixes are
   ;; breaking changes.
   :frereth/version sem-ver

   ;; More-or-less arbitrary code to be run inside the App's sandbox on
   ;; the renderer.
   ;; This should be defining helper functions to assist the render-side
   ;; events.
   ;; This is really the dangerous part
   ;; The spec here should really just be a sequence of defn forms.
   ;; Although a map a function name symbols to a defn's parameters/body
   ;; sequence is also very tempting.
   :render/code [[s/Any]]
   ;; Dependencies that will be require'd on the renderer side
   ;; This is also pretty dangerous, of course
   :render/dependencies
   ;; What does the drawing?
   ;; Note that composites are very likely.
   ;; Maybe we have react.js for the HTML parts, D3 for 2d data visualization,
   ;; and ThreeD for drawing the "fun" stuff
   :render/engine rendering-engines
   :render/events [render-event]

   ;; Map of event keys to handler definitions
   :universe/events {s/Keyword [s/Any]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defn start-event-loop! :- EventPairInterface
  [this :- PluginManager
   source :- SourceCode]
  (let [ctx (:ctx this)

        ;; This shouldn't be created here.
        ;; We need to set it up as a server socket, complete with encryption key.
        ;; Q: Is it more appropriate to have one key per app, or to just have
        ;; 1 shared across the entire server?
        ;; (Obviously need multiple keys if we're serving multiple apps from different
        ;; sources, but that's a different thing...isn't it?)
        ex-sock (mq/socket! ctx :router)
        ;; Probably *is* appropriate to bind like this here
        port (mq/bind-random-port! ex-sock "tcp://*")
        ;; We use it to send messages to the Client over 0mq.
        ;; It really seems like it should be supplied here as a
        ;; parameter, but where would it make more sense?
        in-chan (async/chan)
        ;; Q: What happens on incoming data?
        external-reader nil
        ;; Q: What do we do on data heading out?
        external-writer nil
        -name (:name source)]
    ;; Just initialized pretty much everything to nil as a place-holder
    ;; to get the basic app shape slapped together
    (throw (ex-info "FIXME: Implement reader/writer" {}))
    (assoc
     (async-zmq/event-system {:ex-sock ex-sock
                              :in-chan in-chan
                              :external-reader external-reader
                              :external-writer external-writer
                              :_name -name})
     :port port)))

(defn process-map
  [process-key]
  (process-key {:login '[login]
                :shell '[sh]}))

(s/defn load-app-source!
  [path :- [s/Symbol]]
  (if-let [app-path (process-map path)]
    (let [path-names (map str app-path)
          ;; Q: What's the equivalent for python's os.path_separator?
          ;; A: Don't care. Since this is really going to be
          ;; querying a database anyway, this approach is just
          ;; an over-simplification that must go away. It's
          ;; strictly for the sake of getting that rope thrown
          ;; across the gorge.
          resource-path (clojure.string/join "/" path-names)
          file-path (str resource-path ".edn")]
      (if-let [url (io/resource file-path)]
        (with-open [rdr (java.io.PushbackReader. (io/reader url))]
          (edn/read rdr))
        (throw (ex-info "Missing installed App" {:path resource-path}))))
    (throw (ex-info "App not found" {:path path}))))

(s/defn load-plugin! :- EventPairInterface
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
    (if-let [existing-app (get @processes-atom path)]
      existing-app   ; Q: What about processes that don't want to be multi-user? Since they're quite a bit simpler
      (let [source-code (load-app-source! path)
            ;; TODO: Refine and protect, based on these comments
            ;; Note that we really need a callback for when the browser side
            ;; of an App exits so the server side can decide what to do.
            ;; Note also that this is really where sandboxes start to come
            ;; into play. Maybe something like OSGi or even clojail
            ;; (that's what convinced me to take a serious look at
            ;; clojure in the first place, after all).
            result (start-event-loop! this source-code)]
        (swap! (:processes this) assoc path result)
        result))
    (throw (ex-info "Trying to load a plugin with an unstarted PluginManager" (assoc this
                                                                                     :requested path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- PluginManager
  [options]
  (map->PluginManager (select-keys options [:base-port])))
