(ns frereth-server.auth-socket
  (:require [frereth-server.config :as config]
            [zguide.zhelpers :as mq]
            ;; Next requirement is (so far, strictly speaking) DEBUG ONLY
            [clojure.java.io :as io]
            )
  (:gen-class))

(defn- read-message [s]
  (mq/recv-all s))

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
      :list)))

(defmulti ^:private dispatch
  "Based on message, generate response"
  dispatcher)

(defmethod dispatch "dieDieDIE!!"
  [_]
  ;; Really just a placeholder message indicting that it's OK to quit
  "K")

(defmethod dispatch "ping"
  [_]
  "pong")

(defmethod dispatch :list [msgs]
  ;; Based on the next line, I have something like a Vector of byte arrays.
  ;; Which seems pretty reasonable.
  (spit "/tmp/log.txt" msgs)

  ;; What are the odds that I can call a method this way?
  (comment (dorun dispatch msgs))
  ;; That's failing. Then again, so is this:
  (print "Dispatching a sequence of messages: " msgs)
  ;; Apparently, expectations redirects STDOUT
  ;;(throw (RuntimeException. "Why didn't that show up?"))
  (comment (dorun #(dispatch %) msgs))
  (for [m msgs]
    (throw (RuntimeException. (str m)))))

(defmethod dispatch :default
  [echo]
  echo)

(defn- send-message
  "Like with read-message, this theoretically gets vaguely interesting with multi-part messages.
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
      (spit "/tmp/log.txt" (format "Sending: %s\nto\n%s" msgs s))
      (mq/send-all s msgs))))


(defn- authenticator
  "Deal with authentication requests.
done-reference lets someone else trigger a 'stop' signal."
  [ctx done-reference]
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
  (let [listener (mq/socket ctx mq/dealer)]
    (mq/bind listener (format "tcp://*:%d" config/auth-port))

    (let [poller (mq/socket-poller-in [listener])]
      (try
        (while (not @done-reference)
          ;; Well, this is where the handshake and such actually goes.
          ;; Doesn't it?

          ;; Well...how much, if any, of this should happen here?
          ;; It seems like this probably *should* be pretty transparent,
          ;; so I can implement something blindly stupid here, then
          ;; not need to rewrite a bunch of crap when it's time to be less
          ;; stupid.
          ;; I strongly suspect that I'm actually looking for the
          ;; majordomo pattern.

          ;; poll for a request.
          (.poll poller)

          ;; That means that system/stop needs to send a "die" message
          ;; to this port.
          ;; Or reset done to true.
          ;; Still need the die message: otherwise we stay frozen at the (poll)

          ;; FIXME: Bind a specific socket to a specific port just for
          ;; that message?

          ;; What's the memory overhead involved in that?
          ;; It seems like it can't possibly be worth worrying about.

          ;; For other message types, work through a login sequence.
          ;; Possibly present a potential client with details about
          ;; what to do/where to go next.
          (if (.pollin poller 0)
            (let [request (read-message listener)
                  response (dispatch request)]
              (send-message listener response))
            (throw (RuntimeException. "How'd we get here?"))))
        (finally
          (.close listener))))))


(defn runner
  "Set up the authenticator.
ctx is the zmq context for the authenticator to listen on.
done-reference is some sort of deref-able instance that will tell the thread to quit.
This feels like an odd approach, but nothing more obvious springs to mind."
  [ctx done-reference]
  (.start (Thread. (fn []
                     (authenticator ctx done-reference)))))
