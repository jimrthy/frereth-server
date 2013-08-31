(ns frereth-server.authentication
  (:gen-class))

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
      (throw (RuntimeException. "Now things start to get interesting")))))

(defmulti ^:private dispatch
  "Based on message, generate response.
Q: Do these make sense here?
A: No. Absolutely not.
The auth socket shouldn't be dealing with server management."
  dispatcher)

(defmethod dispatch "dieDieDIE!!"
  [_]
  ;; Really just a placeholder message indicting that it's OK to quit
  "K")

(defmethod dispatch "ping"
  [_]
  "pong")

(defmethod dispatch :default
  [echo]
  echo)


