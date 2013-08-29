(ns frereth-server.auth-socket
  (:require [zguide.zhelpers :as mqh]
            [zeromq.zmq :as mq]
            ;; Next requirement is (so far, strictly speaking) DEBUG ONLY
            [clojure.java.io :as io]
            )
  (:gen-class))

(defn- log [msg]
  "FIXME: Debug only.
If nothing else, it does not belong here. And should not be writing to
anything in /tmp.
For that matter...whatever. I need to get around to implementing 'real'
logging.
This is a decent placeholder until I have enough written to justify
thinking about crap like this in issues."
  (with-open [w (io/writer "/tmp/log.txt" :append true)]
    (.write w (str msg))
    (.write w "\n")))

(defn- read-message [s]
  (mqh/recv-all-str s))

(defn- dispatcher [msg]
  "This is just screaming for something like cond.
It seems pretty important to remember that this isn't
exactly performance-critical code.
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
  (if (string? msg)
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
  (log "Quit permission")
  "K")

(defmethod dispatch "ping"
  [_]
  (log "Heartbeat")
  "pong")

(defmethod dispatch :list [msgs]
  ;; Based on the next line, I have something like a Vector of byte arrays.
  ;; Which seems pretty reasonable.
  (log "Dispatching :list:\n")
  (log msgs)
  (log "\n(that's some sort of SEQ of messages)\n")

  ;; Q: What are the odds that I can call a method this way?
  ;; A: Not very good.
  (comment (dorun dispatch msgs))
  (log (str "Dispatching a sequence of messages: " msgs))
  
  ;; This fails in the same way: cannot convert a MultiFn to a java.lang.Number:
  (comment (dorun #(dispatch %) msgs))
  ;; Or possibly my real problem is laziness?
  (comment (for [m msgs]
             (throw (RuntimeException. (str m)))))

  (comment (log "Anything interesting?\n"))

  (log msgs)
  (if-let [car (first msgs)]
    (do
      (log car)
      (dispatch (rest msgs)))
    (do
      (log (str "Empty CAR. CDR: " (rest msgs))))))

(defmethod dispatch :default
  [echo]
  echo)

(defn- send-message
  "Like with read-message, this theoretically gets vaguely interesting 
with multi-part messages.
Oops. Backwards parameters."
  [s msgs]
  ;; Performance isn't a consideration, really.
  ;; Is it?
  ;; Not for authentication. We expect this to take a while.
  ;; By its very nature, it pretty much has to.
  (if (string? msgs)
    (mq/send s msgs)
    (do
      ;; Just assume that means it's a sequence.
      ;; I hate to break this up like this, but it just is not
      ;; a performance-critical section.
      ;; Probably.
      (log (format "Sending: %s" msgs))
      (log (format "\nto\n%s\n" s))
      (mqh/send-all s msgs))))


(defn- authenticator
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
  ;; FIXME: Rewrite this using with-socket.
  (let [listener (mq/socket ctx :dealer)]
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
      ;; FIXME: What should this actually be listening on?
      (mq/bind listener (format "tcp://*:%d" auth-port))

      ;; FIXME: Rewrite this using with-poller.
      (let [poller (mq/poller ctx)]
        (mq/register poller listener :pollin :pollerr)
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

            ;; poll for a request.
            ;; Do I need to specify a timeout?
            (mq/poll poller)

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
            (when (mq/check-poller poller 0 :pollin)
              (let [request (read-message listener)]
                ;; I can see request being a lazy sequence.
                ;; But (doall ...) is documented to realize the entire sequence.
                ;; Here's a hint: it still returns a LazySeq, apparently
                (log (str "REQUEST: " (doall request) "\nMessages in request:\n"))
                (doseq [msg request]
                  (log msg))
                (log "Dispatching response:\n")
                (let [response (dispatch request)]
                  (send-message listener response))))
            (when (mq/check-poller poller 0 :pollerr)
              (throw (RuntimeException. "What should happen here?"))))
          (finally
            (mq/unregister poller listener))))
      (finally
        ;; Really need to set the ZMQ_LINGER option so
        ;; this won't block if there are unsent messages in
        ;; the queue.
        ;; Or maybe not...see what happens.
        (mq/close listener)))))


(defn runner
  "Set up the authenticator.
ctx is the zmq context for the authenticator to listen on.
done-reference is some sort of deref-able instance that will tell the thread to quit.
This feels like an odd approach, but nothing more obvious springs to mind.
This gets called by system/start. It needs system as a parameter to do
its thing. Circular references are bad, mmkay?"
  [ctx done-reference auth-port]
  (log (str "Kicking off the authentication runner thread in context: "
            ctx "\nwaiting on Done Reference " done-reference))
  (.start (Thread. (fn []
                     (authenticator ctx done-reference auth-port)))))
