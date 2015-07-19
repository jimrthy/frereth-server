(ns com.frereth.server.comms.registrar
  (:require [clojure.core.async :as async]
            ;; Q: How do I combine these?
            [com.frereth.common.async-zmq]  ; just for the imports
            [com.frereth.common.communication :as com-comm]
            [com.frereth.common.schema :as com-skm]
            [com.frereth.common.util :as util]

            [com.frereth.server.auth-socket :as auth-socket]
            [com.stuartsierra.component :as component]
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
  [msg :- auth-socket/router-message]
  (assoc msg
         :contents {:action-url {:port 7841  ; FIXME: magic number
                                 ;; TODO: Pull this from a config
                                 ;; file/env var instead
                                 :address (util/my-ip)
                                 :protocol :tcp}
                    :session-token (util/random-uuid)}))

(s/defn possibly-authorize
  "TODO: This desperately needs to happen in a background thread.

Return a channel where the response will be written, add that to
an accumulator in do-registrations.

Of course, that direction gets complicated quickly. KISS for now."
  [->out :- com-skm/async-channel
   msg :- com-comm/router-message]
  (log/debug "Possibly authorizing to: " ->out)
  (let [response-body (authcz (:contents msg))
        response (assoc msg :contents response-body)
        [sent? c] (async/alts!! [[->out response] (async/timeout 500)])]
    (when-not sent?
      (log/error "possibly-authorize: timed out trying to respond with\n"
                 (util/pretty response)))))

(s/defn do-registrations :- com-skm/async-channel
  ;; TODO: This really seems like it should be a defnk
  [{:keys [done event-loop ex-chan]} :- Registrar]
  (let [interface (:interface event-loop)
        ->out (:in-chan interface)
        in<- ex-chan
        raw-sources [done in<-]
        minutes-5 (partial async/timeout (* 5 (util/minute)))]
    (async/go-loop [[v c] (async/alts! (conj raw-sources (minutes-5)))]
      (if (= c done)
        (log/debug "do-registrations loop stop signalled")
        (do
          (if v
            (possibly-authorize ->out v)
            (log/debug "do-registrations: heartbeat"))
          (recur (async/alts! (conj raw-sources (async/timeout (minutes-5))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn ctor :- Registrar
  [cfg]
  (map->Registrar cfg))
