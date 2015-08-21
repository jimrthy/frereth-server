(ns com.frereth.server.comms.registrar
  (:require [clojure.core.async :as async]
            ;; Q: How do I combine these?
            [com.frereth.common.async-zmq]  ; just for the imports
            [com.frereth.common.communication :as com-comm]
            [com.frereth.common.schema :as com-skm]
            [com.frereth.common.util :as util]

            [com.frereth.server.auth-socket :as auth-socket]
            [com.stuartsierra.component :as component]
            [joda-time :as date]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.frereth.common.async_zmq EventPair]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare do-registrations)
(s/defrecord Registrar [background-worker :- com-skm/async-channel
                        done :- com-skm/async-channel
                        event-loop :- EventPair]
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
;;; Internal

(s/defn authcz :- auth-socket/router-message
  "This needs to do much, much more.
Check the database for rbac. Set up a real session
(and register that with the database).

Or, at the very least, validate that an
OpenID (et al) token is valid.

c.f. auth-socket's dispatch"
  [msg :- com-comm/router-message]
  (let [pretty-msg (try (util/pretty msg)
                        (catch RuntimeException ex
                          (log/error ex "Trying to pretty-format for logging REQUEST\n"
                                     "N.B. This should absolutely never happen")))
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
  ;; TODO: Set up a web server and go back to doing it this way
  (let [br {:tag :br, :attrs nil, :content nil}]
    (assoc msg
           :expires (date/to-java-date (date/plus (date/date-time) (date/days 1)))
           :session-token (util/random-uuid)
           :world {:data {:type :html
                          :version 5
                          ;; TODO: At the very least, use something like enlive/kioo instead
                          :body {:tag :form
                                 :attrs {:id "authenticate"}
                                 :content ["User name:"
                                           br
                                           {:tag :input
                                            :attrs {:name "principal-name" :type "text"}
                                            :content nil}
                                           br
                                           "\nPassword:"
                                           br
                                           {:tag :input
                                            :attrs {:name "auth-token" :type "password"}
                                            :content nil}
                                           br
                                           {:tag :input
                                            :attrs {:name "submit" :type "submit" :value "Log In"}
                                            :content nil}]}}
                   ;; This basic script was taken from
                   ;; http://swannodette.github.io/2013/11/07/clojurescript-101/
                   ;; Vital assumptions here:
                   ;; 1. Basic clojurescript environment
                   ;; 2. use'ing the core.async ns
                   ;; 3. require'd goog.dom as dom and goog.events as events
                   ;; Or, at least, that we're in an interpreter
                   ;; environment/namespace
                   ;; that acts as if those assumptions are true
                   :script [(defn listen [element event-type]
                              (let [out (chan)]
                                (events/listen element event-type
                                               (fn [e]
                                                 (put! out e)))
                                out))
                            (let [clicks (listen (dom/getElement "submit") "click")]
                              (go (while true
                                    (raise :start-here)
                                    (let [clicked (<! clicks)
                                          ]))))]
                   ;; TODO: Definitely use garden to build something here
                   :css []})))

(comment
  (require '[clojure.xml :as xml])
  ;; Figure out how to go from html string to internal XML format
  (let [form "<form>
User name:<br />
<input type=\"text\" name=\"principal-name\" /><br />
Password:<br />
<input type=\"password\" name=\"auth-token\" /><br />
<input type=\"submit\" value=\"Log In\" />
</form>"
        istream (-> form .getBytes java.io.ByteArrayInputStream.)]
    (xml/parse istream))
  )

(s/defn possibly-authorize!
  "TODO: This desperately needs to happen in a background thread.

Return a channel where the response will be written, add that to
an accumulator in do-registrations.

Of course, that direction gets complicated quickly. KISS for now."
  [->out :- com-skm/async-channel
   msg :- com-comm/router-message]
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
      (throw))))

(s/defn do-registrations :- com-skm/async-channel
  ;; TODO: This really seems like it should be a defnk
  [{:keys [done event-loop ex-chan]} :- Registrar]
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
            (log/debug "do-registrations: heartbeat")
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
;;; Public

(s/defn ctor :- Registrar
  [cfg]
  (map->Registrar cfg))
