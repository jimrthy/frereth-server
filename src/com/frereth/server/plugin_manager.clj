(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

  For starters, everything's going to be an App. I'll almost definitely
  want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [cljeromq.common :as mq-cmn]
            [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.frereth.common.async-zmq :as async-zmq]
            [com.frereth.common.schema :as frereth-schema]
            [component-dsl.done-manager :as sentinal]
            [com.stuartsierra.component :as cpt]
            [schema.core :as s])
  (:import [com.frereth.common.async_zmq EventPairInterface]
           [com.frereth.common.zmq_socket SocketDescription]
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

(declare load-plugin! start-event-loop!)
(s/defrecord PluginManager [base-port :- s/Int
                            ;; Q: Do we really need access to done?
                            done ; Should probably turn that promise into a record
                            ;; Note that this is upside-down. event-loop is really
                            ;; just a separate Component that depends on the PluginManager
                            event-loop :- EventPairInterface
                            processes :- ProcessMap
                            socket-description :- SocketDescription]
  cpt/Lifecycle
  (start [this]
    (let [this (assoc this
                      :processes (atom {})
                      :event-loop (start-event-loop! this))
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

(def dependency-declaration
  "This is another place where spec will be a big win

This is basically an entry in the :require field of an ns declaration"
  [(s/either s/Symbol s/Keyword)])

(def base-event {:event/handler [s/Any]  ;; Really a fn definition
                 :event/events frereth-schema/korks})

(def dom-event
  ;; Q: Limit :event/events to standard legal DOM events?
  (into base-event {:event/element s/Str ; what triggered this event?
                    ;; Which elements of the tree of the DOM that we created
                    ;; should this event handler know about?
                    (s/optional-key :event/dependencies) [s/Str]}))

(def internal-event (into base-event {}))

(def render-event (s/either dom-event internal-event))

(def rendering-engine-types (s/enum :google/incremental-dom))

(def rendering-engines
  "Most interesting Apps will involve multiple rendering engines.

Otherwise, why would you bother?"
  (s/either
   rendering-engine-types
   {s/Keyword rendering-engine-types}))

(def sem-ver
  "This belongs in Common, assuming I don't already have a duplicate there"
  {:major s/Int
   :minor s/Int
   :build (s/either s/Int s/Str)})

(def SourceCode
  "This should really be defined in frereth-app instead

  But that really means upgrading this to use spec instead of schema, and I'm not
  ready for that just yet.

  And I have to start somewhere"
  {
   :app/description s/Str  ; human-readable
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
   :render/dependencies [dependency-declaration]
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

(s/defn incoming-event-handler :- frereth-schema/java-byte-array
  [sock :- mq-cmn/Socket]
  (throw (ex-info "What should this do?" {})))

(s/defn outgoing-event-writer
  [sock :- mq-cmn/Socket
   packet :- frereth-schema/java-byte-array]
  (throw (ex-info "When does this really get called?" {})))

(s/defn start-event-loop! :- EventPairInterface
  [this :- PluginManager]
  (throw (ex-info "Needs to be its own component instead" {}))
  (let [;; We use it to send messages to the Client over 0mq.
        ;; It really seems like it should be supplied here as a
        ;; parameter, but where would it make more sense to own it?
        in-chan (async/chan)
        ;; Q: What happens on incoming data?
        external-reader incoming-event-handler
        ;; Q: What do we do on data heading out?
        external-writer outgoing-event-writer]
    (async-zmq/event-system {:ex-sock (:socket-description this)
                             :in-chan in-chan
                             ;; In the async-zmq Component, I have comments
                             ;; that the incoming messages really need to
                             ;; be demarshalled and pre-processed by these
                             ;; in order for anything useful to happen.
                             ;; Q: When would that really be appropriate?
                             ;; (I don't think I need it here)
                             :external-reader (comment external-reader)
                             :external-writer (comment external-writer)
                             :_name "frereth.io"})))

(defn process-map
  [process-key]
  (process-key {:login '[login]
                :shell '[sh]}))

(s/defn ^:always-validate load-app-source! :- SourceCode
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
        (io!
         (with-open [rdr (java.io.PushbackReader. (io/reader url))]
           (edn/read rdr)))
        (throw (ex-info "Missing installed App" {:path resource-path}))))
    (throw (ex-info "App not found" {:path path}))))

(s/defn register-active-plugin!
  [this :- PluginManager
   path :- [s/Symbol]
   source-code :- SourceCode]
  ;; This really needs to set things up so the external-reader/writer
  ;; in the EventPairInterface know how to properly route messages.
  ;; Or something along those lines.
  ;; They don't do the routing.
  ;; Part of my problem is that I've been looking at EventPairInterface
  ;; rather than EventPair (which owns the interface. This is really
  ;; a naming problem).
  ;; We read messages from its ex-chan and write them to its
  ;; interface's in-chan.
  ;; TODO: Revisit that fundamental API
  (throw (ex-info "Overly simplistic" {}))
  ;; This approach neither works nor makes sense. I'm just trying
  ;; to get a build-breaking checkin passing
  (swap! (:processes this) assoc path source-code))

(s/defn load-plugin!
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
      (let [source-code (load-app-source! path)]
        ;; TODO: Refine and protect, based on these comments
        ;; Note that we really need a callback for when the browser side
        ;; of an App exits so the server side can decide what to do.
        ;; Note also that this is really where sandboxes start to come
        ;; into play. Maybe something like OSGi or even clojail
        ;; (that's what convinced me to take a serious look at
        ;; clojure in the first place, after all).
        (register-active-plugin! this path source-code)))
    (throw (ex-info "Trying to load a plugin with an unstarted PluginManager" (assoc this
                                                                                     :requested path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- PluginManager
  [options]
  (map->PluginManager (select-keys options [:base-port])))
