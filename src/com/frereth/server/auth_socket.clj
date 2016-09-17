(ns com.frereth.server.auth-socket
  "Messaging pieces for handling the authentication socket.

This is really my original prototype code, trying to set up a
handshake with a client.

It's really still a 'next step' sort of thing."
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async :refer (<! <!! >! >!!)]
            ;; Next requirement is (so far, strictly speaking) DEBUG ONLY
            [clojure.java.io :as io]
            [clojure.spec :as s]
            [com.frereth.common.async-zmq :as async-zmq]
            [com.frereth.common.communication :as com-comm]
            [com.frereth.common.schema :as common-schema]
            [com.frereth.common.util :as util]
            [hara.event :refer (raise)]
            [taoensso.timbre :as log
             :refer [trace debug info warn error fatal spy with-log-level]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::message (s/or :bytes :com.frereth.common.schema/byte-array-type
                       :string string?))

(s/def ::messages (s/coll-of ::message))

;;; com-comm is responsible for low-level details.
;;; It should probably just go ahead and finish converting
;;; the contents into something meaningful, but I suppose
;;; there might be some scenarios where we want to do
;;; something different
;;; TODO: Move this elsewhere
(s/def ::contents any?)
(s/def ::router-message (s/merge :com.frereth.common.communication/router-message
                                 (s/keys :req [::contents])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn- dispatcher [msg]
  "This is just screaming for something like cond.

Or, better yet, core.match.

It seems pretty important to remember that the auth
piece isn't exactly performance-critical code.

But it sort-of is.

*If* a server's successful, people will hammer it pretty
much constantly after it boots.
Might be nice to allow players to download new worlds in
the background and switch over to them fairly seamlessly,
ignoring the entire patch day BS. Actually, that'd be
a delightful advantage of using freenet.
With the downside of supporting old servers until clients
switch to the new version. It seems like an interesting
thought."
  (if (or (string? msg) (keyword? msg))
    msg
    (when (seq msg)
      ;; Just got a batch of messages. What do they mean?
      ;; For starters, go with super-easy
      ;; I bet this is at least part of my problem: I *really* have a
      ;; java-style byte[]. I bet that qualifies as a seq.
      :list)))

(defmulti ^:private dispatch
  "Based on message, generate response"
  dispatcher)

(defmethod dispatch "dieDieDIE!!"
  [_]
  ;; Really just a placeholder message indicting that it's OK to quit
  ;; Note that we are *not* getting here
  (warn "Quit permission")
  ;; Should *not* be coming over an AUTH/client socket.
  ;; This should happen over a command/control socket.
  (throw (RuntimeException. "Obsolete?"))
  "K")

(defmethod dispatch "ping"
  [_]
  (trace "Heartbeat")
  "pong")

(defmethod dispatch :icanhaz?
  [_]
  ;; This seemed like a good idea, but it doesn't make any sense
  (throw (RuntimeException. "Don't bother...should have been part of a list.")))

(defmethod dispatch :list [msgs]
  ;; Based on the next line, I have something like a Vector of byte arrays.
  ;; Which seems pretty reasonable.
  (trace (str "Dispatching :list:\n"
                  msgs
                  "\n(that's some sort of SEQ of messages)\n"))

  ;; Honestly, this approach is half-baked, at best.
  ;; If I have a list, I should probably EDN it and dispatch on that.
  ;; Or something along those lines. Since it's an attempt at a
  ;; function call.
  ;; If it's a general sequence, though, this approach makes total sense.
  (if-let [car (first msgs)]
    (do
      (log/debug car)
      (dispatch (rest msgs)))
    (do
      (log/debug (str "Empty CAR. CDR: " (rest msgs))))))

(defmethod dispatch :where-should-my-people-call-your-people
  [_]
  (raise :not-implemented))

(defmethod dispatch :default
  [echo]
  echo)

(comment
  (s/defn authenticator
    "Deal with authentication requests.
done-reference lets someone else trigger a 'stop' signal.
What seems particularly obnoxious about this: I don't really care
about this just now, and it probably isn't relevant in the long run.
I just want the basic testing to work. So count it as a homework
assignment and don't surrender to laziness."
    [ctx done-reference auth-port]
    ;; This looks like an ugly weakness in my scheme:
    ;; The client needs to connect to a dealer socket
    ;; to auth. Then, realistically, it needs to switch to
    ;; another dealer socket for actual command/control and
    ;; a sub socket for game state updates.
    ;; It's very tempting to just re-use this auth socket
    ;; for game data.
    ;; Not doing so may qualify as premature optimization,
    ;; but it seems pretty blindingly obvious.
    ;; The alternative simplifies the client ("I only need 1 socket!")
    ;; but not by much. Certainly not enough to qualify as an upside.

    (log/trace "Creating Listener")
    ;; FIXME: Rewrite this using with-socket.
    (let [listener (mq/socket! ctx :dealer)]
      (try
        ;; Note that this is really at the heart of how the server
        ;; works:
        ;; No one except localhost should realistically be connecting
        ;; to the master socket. Except most 'real' servers will
        ;; want to work exactly that way...the alternative is
        ;; to ssh in before connecting.
        ;; Which is way more secure, of course.
        ;; But that doesn't have anything to do with *this*
        ;; socket.
        ;; This socket is for general client connections.
        ;; So remote clients should probably be rejected
        ;; by default, but some user totally needs to have
        ;; the ability to open up to a wider audience.
        ;; Or limiting to, say, the LAN.
        ;; Which really means killing and restarting this
        ;; socket at runtime.
        ;; Have to think my way through this, but not now.
        ;; Right now, I have other priorities.
        (log/trace "Binding Listener to Port " auth-port)
        ;; FIXME: What should this actually be listening on?
        (mq/bind! listener (format "tcp://*:%d" auth-port))

        (raise :not-implemented)
        (comment (mq/with-poller [authenticator ctx listener]
                   (try
                     (while (not @done-reference)
                       ;; FIXME: I really don't want to do busy polling here.

                       ;; This is where the handshake and such actually goes.
                       ;; Doesn't it?

                       ;; Well...how much, if any, of this should happen here?
                       ;; It seems like this probably *should* be pretty transparent,
                       ;; so I can implement something blindly stupid here, then
                       ;; not need to rewrite a bunch of crap when it's time to be less
                       ;; stupid.
                       ;; I strongly suspect that I'm actually looking for the
                       ;; majordomo pattern.

                       (log/trace "Polling authenticator...")
                       ;; poll for a request.
                       ;; Q: Do I need to specify a timeout?
                       (mq/poll! authenticator)

                       ;; That means that system/stop needs to send a "die" message
                       ;; to this port.
                       ;; Or reset done to true.
                       ;; Still need the die message: otherwise we stay frozen at the
                       ;; (poll)

                       ;; FIXME: Bind a specific socket to a specific port just for
                       ;; that message?

                       ;; What's the memory overhead involved in that?
                       ;; It seems like it can't possibly be worth worrying about.

                       ;; For other message types, work through a login sequence.
                       ;; Possibly present a potential client with details about
                       ;; what to do/where to go next.
                       (when (mq/check-poller authenticator 0 :pollin)  ; Q: What's this now?
                         (let [request (read-message! listener)]
                           ;; I can see request being a lazy sequence.
                           ;; But (doall ...) is documented to realize the entire sequence.
                           ;; Here's a hint: it still returns a LazySeq, apparently
                           (log/trace (str "REQUEST: " (doall request) "\nMessages in request:\n"))
                           (doseq [msg request]
                             (log/trace msg))
                           (log/trace "Dispatching response:\n")
                           (let [response (dispatch request)]
                             (send-message! listener response))))
                       (when (mq/check-poller authenticator 0 :pollerr)
                         (throw (RuntimeException. "What should happen here?")))))))
        (finally
          (mq/set-linger! listener 0)
          (mq/close! listener)))))

  (s/defn runner :- common-schema/async-channel
    "Set up the authenticator.
ctx is the zmq context for the authenticator to listen on.
done-reference is some sort of deref-able instance that will tell the thread to quit.
This feels like an odd approach, but nothing more obvious springs to mind.
This gets called by system/start. It needs system as a parameter to do
its thing. Circular references are bad, mmkay?"
    [ctx :- mq/Context
     done-reference
     auth-port]
    (log/info (str "Kicking off the authentication runner thread in context: "
                   ctx "\nwaiting on Done Reference " done-reference))

    (async/thread (authenticator ctx done-reference auth-port))))

(s/fdef reader
        :args (s/cat :sock :cljeromq.core/socket)
        :ret ::router-message)
(defn reader
  ;; This is responsible for translating
  ;; a message that it reads from the socket into
  ;; something useful
  [sock]
  ;; This takes a low-level Socket instead of its own ::socket-description.
  ;; This seems like a poor API decision.
  (com-comm/router-recv! sock))

(s/fdef writer
        :args (s/cat :sock :cljeromq.core/socket
                     :msg ::router-message)
        :ret any?)
(defn writer
  [sock msg]
  (log/debug "Sending router message:\n"
             (util/pretty msg))
  (com-comm/router-send! sock msg))

(comment
  ;; For testing
  (def ctx (mq/context 3))
  (def sock (mq/socket! ctx :router))
  (def url "tcp://127.0.0.1:7843")
  (mq/bind! sock url)
  ;; Note that this will never return True until after
  ;; we've received the first frame
  (mq/has-more? sock)
  (mq/recv! sock :dont-wait)
  (mq/unbind! sock url)
  (mq/set-linger! sock 0)
  (mq/close! sock)
  (mq/terminate! ctx)
  (defn test-echo
    []
    (let [ctx (mq/context 3)
          sock (mq/socket! ctx :router)
          url "tcp://127.0.0.1:7843"
          _ (mq/bind! sock url)]
      (try
        (Thread/sleep 5000)
        (mq/has-more? sock)
        (finally
          (mq/unbind! sock url)
          (mq/set-linger! sock 0)
          (mq/close! sock)
          (mq/terminate! ctx))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef ctor-interface
        :args (s/cat :_ any?)
        :ret :com.frereth.common.async-zmq/event-pair-interface)
(defn ctor-interface
  [_]
  (let [in-chan (async/chan)
        external-reader reader
        external-writer writer]
    (async-zmq/ctor-interface {:in-chan in-chan
                               :external-reader external-reader
                               :external-writer external-writer})))
