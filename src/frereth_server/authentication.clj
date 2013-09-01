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
  ;; TODO: change this to some sort of case. Duh.
  ;; Why isn't this using basic multi-methods?
  (if (or (string? msg) (symbol? msg) (keyword? msg))
    msg
    (if (seq? msg)
      ;; Just got a batch of messages. What do they mean?
      ;; This could be something that looks like a function call.
      ;; Or it could be a list of basic messages.
      ;; Should probably just throw out the 'looks like a function call'
      ;; list possibility...although accepting EDN makes sense.
      (let [note "Now things start to get interesting. Trying to parse: "]
        (throw (RuntimeException. (str note msg ))))
      (throw (RuntimeException. "What are my other options?")))))

(defmulti ^:private dispatch
  "Based on message, generate response.
Q: Do these make sense here?
A: No. Absolutely not.
The auth socket shouldn't be dealing with server management."
  dispatcher)

(defmethod dispatch "dieDieDIE!!"
  [_]
  ;; Really just a placeholder message indicting that it's OK to quit
  (throw (RuntimeException. "Totally misplaced!!")))

(defmethod dispatch "ping"
  [_]
  "pong")

(defmethod dispatch :ohai
  [_]
  :oryl?)

(defmethod dispatch :default
  [echo]
  echo)


