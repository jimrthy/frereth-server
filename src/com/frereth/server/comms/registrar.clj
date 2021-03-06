(ns com.frereth.server.comms.registrar
  (:require [clj-time.core :as date]
            [clojure.core.async :as async]
            [clojure.spec :as s]
            ;; Q: How do I combine these to avoid the copy/paste?
            ;; (i.e. I really just want [com.frereth.common [async-zmq ...]]
            [com.frereth.common.async-zmq]  ; just for the specs
            [com.frereth.common.communication :as com-comm]
            [com.frereth.common.schema :as com-skm]
            [com.frereth.common.util :as util]

            [com.frereth.server.auth-socket :as auth-socket]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/def ::backgrand-worker :com.frereth.common.schema/async-channel)
(s/def ::done :com.frereth.common.schema/async-channel)
(s/def ::event-loop :com.frereth.common.async-zmq/event-pair)
(s/def ::registrar (s/keys :req-un [::background-worker
                                    ::done
                                    ::event-loop]))
(s/def ::registrar-options (s/keys :opt-un [::background-worker
                                            ::done
                                            ::event-loop]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn define-world-om
  []
  (let [br {:tag :br, :attrs nil, :content nil}]
    {:type :om
     :version [0 9 0]
     ;; TODO: At the very least, use something like enlive/kioo instead
     ;; Note that this nesting seems pretty awfully incorrect
     :body '(form {:attr {:id "authenticate"}
                   :content ["User name:"
                             (br)
                             (input {:attr {:name "principal-name",
                                            :type "text"}
                                     :content [(br)
                                               "Password:"
                                               (input {:attr {:name "auth-token"
                                                              :type "password"}
                                                       :content [(br)]})
                                               (input {:attr {:name "submit"
                                                              :type "submit"
                                                              :value "Log In"}})]})]})}))

(defn define-world-in-sablono
  []
  {:type :sablono
   :version [0 3 6]
   :body [:div {:id "authenticate"}
          "User name:"
          :br
          :input {:name "principal-name"
                  :type "text"}
          :br
          "Password:"
          :br
          :input {:name "auth-token"
                  :type "password"}
          :br
          :input {:name "submit"
                  :type "submit"
                  :value "Log In"}]})

(defn define-initial-auth-world
  []
  (let [body (define-world-in-sablono)]
    {:data (assoc body
                  :name "Initial Local Login"
                  ;; This basic script was taken from
                  ;; http://swannodette.github.io/2013/11/07/clojurescript-101/
                  ;; Vital assumptions here:
                  ;; 1. Basic clojurescript environment
                  ;; 2. use'ing the core.async ns
                  ;; 3. require'd goog.dom as dom and goog.events as events
                  ;; Or, at least, that we're in an interpreter
                  ;; environment/namespace
                  ;; that acts as if those assumptions are true
                  :script '[(ns empty.world
                                 "Need a naming scheme
Although, honestly, for now, user makes as much sense as any"
                                 (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
                                 (:require [cljs.core.async :as async]
                                           [goog.dom :as dom]
                                           [goog.events :as events]))
                            (defn listen [element event-type]
                              (let [out (chan)]
                                (events/listen element event-type
                                               (fn [e]
                                                 (put! out e)))
                                out))
                            (let [clicks (listen (dom/getElement "submit") "click")]
                              (go (while true
                                    (let [clicked (<! clicks)]
                                      (throw (ex-info ":start-here" {}))))))]
                  ;; TODO: Definitely use garden to build something here
                  :css [])}))

(s/fdef authcz
        :args (s/cat :msg :com.frereth.common.communications/router-message)
        :ret :com.frereth.server.auth-socket/router-message)
(defn authcz
  "This needs to do much, much more.
Check the database for rbac. Set up a real session
(and register that with the database).

Or, at the very least, validate that an
OpenID (et al) token is valid.

c.f. auth-socket's dispatch"
  [msg]
  (let [pretty-msg (try (util/pretty msg)
                        (catch RuntimeException ex
                          (log/error ex "Trying to pretty-format for logging REQUEST\n"
                                     "N.B. This should absolutely never happen")))
        ;; For when there's a web server to "really" serve things like the login dialog
        ;; Note that the :action-url is grossly inappropriate here
        ;; It shouldn't turn up until after the end-user has been authcz'd
        information-to-share-later {:action-url {:port 7841  ; FIXME: magic number
                                                 ;; TODO: Pull this from a config
                                                 ;; file/env var instead
                                                 :address (util/my-ip)
                                                 :protocol :tcp}
                                    ;; FIXME: This needs to be the public key of the action-url
                                    :public-key (util/random-uuid)

                                    ;; Where to download the actual world data
                                    ;; As opposed to just specifying the :html directly
                                    ;; This seems like the approach that makes much more sense,
                                    ;; especially when we're talking about native renderers
                                    ;; It's tempting to make this a "real" URL instance
                                    ;; But clojure doesn't auto-serialize those over EDN
                                    :world-url "http://localhost:9000/index.html"}]
       (log/debug "Trying to supply the Action channel in response to:\n"
                  pretty-msg))
  (log/warn "Set up a web server and switch back to serving data that way")
  ;; TODO: Set up a web server and go back to just sending the URL
  (assoc msg
         :expires (date/plus (date/date-time) (date/days 1))
         ;; TODO: Make this something meaningful
         :session-token (util/random-uuid)
         :world (define-initial-auth-world)))

(s/fdef possibly-authorize!
        :args (s/cat :->out :com.frereth.common.schema/async-channel
                     :msg :com.frereth.common.communications/router-message)
        :ret any?)
(defn possibly-authorize!
  "TODO: This desperately needs to happen in a background thread.

TODO: Fix the comment rot!
Return a channel where the response will be written. Add that to
an accumulator in do-registrations.

Of course, that direction gets complicated quickly. KISS for now."
  [->out msg]
  (log/debug "Possibly authorizing to: " ->out)
  (try
    (let [response-body (authcz (:contents msg))
          response (assoc msg :contents response-body)
          _ (log/debug "Sending\n" (util/pretty response) "\nin response to AUTH request")
          [sent? c] (async/alts!! [[->out response] (async/timeout 500)])]
      (when-not sent?
        (log/error "possibly-authorize: timed out trying to respond with\n"
                   (util/pretty response))))
    (catch RuntimeException ex
      (log/error ex "At this level, really should have been handled via ribol")
      ;; Q: Does this retain the stack trace?
      (throw ex))))

(s/fdef do-registrations
        :args (s/cat :this ::registrar)
        :ret :com.frereth.common.schema/async-channel)
(defn do-registrations
  [{:keys [done event-loop ex-chan]}]
  (let [done (promise)
        interface (:interface event-loop)
        ->out (:in-chan interface)
        in<- (:ex-chan event-loop)
        raw-sources [in<-]
        minutes-5 (partial async/timeout (* 5 (util/minute)))]
    (async/go
      (log/debug "Entering do-registrations loop. sources w/out timeout: " raw-sources)
      (loop [[v c] (async/alts! (conj raw-sources (minutes-5)))]
        (if v
          (try
            (possibly-authorize! ->out v)
            ;; Don't want buggy inner handling code to break the external interface
            (catch RuntimeException ex
              (log/error ex "Trying to authorize:\n" v))
            (catch Exception ex
              (log/error ex "Trying to authorize:\n" v)))
          (if (not= c in<-)
            (log/debug "do-registrations: internal heartbeat")
            (do
              (log/debug "Signalling loop exit")
              (deliver done true))))
        (when-not (realized? done)
          (recur (async/alts! (conj raw-sources (minutes-5))))))
      (log/debug "do-registration: Exiting"))))

(comment
  (let [ch (-> dev/system :auth-loop :ex-chan)]
    (async/alts!! [(async/timeout 750) [ch {:contents {:xz 456}
                                            :id 789}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Component
(defrecord Registrar [background-worker
                      done
                      event-loop]
  ;; Make this the absolute dumbest registration manager I can possibly get away with
  ;; I have unit tests that actually set up an authentication
  ;; protocol, of sorts.
  ;; In authentication.clj, which is named incorrectly.
  ;; TODO: Add them into this mix
  ;; After I get the rope thrown across the gorge.
  component/Lifecycle
  (start
   [this]
   (let [done (async/chan)
         almost-started (assoc this
                               :done done)
         background-worker (do-registrations almost-started)]
     (assoc almost-started
                        :background-worker background-worker)))
  (stop
   [this]
   (when-let [done (:done this)]
     (async/close! done)
     (let [[v c]
           (async/alts!! [(async/timeout 250) background-worker])]
       (when-not v
         (log/warn "Telling the background worker to stop timed out"))))
   (assoc this
          :done nil
          :background-worker nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef ctor
        :args (s/cat :cfg ::registrar-options)
        :ret ::registrar)
(defn ctor
  [cfg]
  (map->Registrar cfg))
