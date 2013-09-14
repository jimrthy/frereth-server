(ns frereth-server.authentication
  (:require [frereth-server.user :as user])
  (:gen-class))

(defn- dispatcher [msg _]
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
      (if (list? msg)
        (let [fn (first msg)]
          (if (symbol? fn)
            fn
            ::multiple))
        ::multiple)
      (throw (RuntimeException. "What are my other options?")))))

(defmulti dispatch
  "Based on message, generate response.
Q: Do these make sense here?
A: No. Absolutely not.
The auth socket shouldn't be dealing with server management."
  dispatcher)

(defmethod dispatch "ping"
  [_ _]
  "pong")

(defmethod dispatch ::multiple
  [msgs system]
  "Callers shouldn't see all the results. That would be a nasty security hole.
Then again, the callers are really programs on the other end of a network that
wound up here through at least a couple of layers. Allow them to decide how
much of the actual results to share."
  (map (fn [m] (dispatch m system) msgs)))

(defmethod dispatch :ohai
  [_ _]
  :oryl?)

(defn known-protocol?
  "Is this a protocol the server knows how to speak?
FIXME: This isn't a setting to be buried somewhere in implementation
details like this. Should really have a fully configurable set of
dynamic plugins that make these easy to swap in and out at will.
I'm starting as small as I can get away with."
  [p]
  (= p :frereth))

(defmethod dispatch 'icanhaz?
  [msg system]
  "Loop through the protocols the client claims to speak. Choose the best
one this server also speaks. Return that, or :lolz if there are no matches.
This is *screaming* to be broken out into multiple methods for simplification"
  (let [headers (second msg)
        protocols (:me-speekz headers)]
    (if protocols
      ;; Expect protocals to be a map.
      (let [ps (filter known-protocol? (keys protocols))
            rankings (map (fn [p]
                            (if (seq? (protocols p))
                              (reduce (fn [pair previous]
                                        (let [score1 (second pair)
                                              score2 (second previous)]
                                          (if (> score1 score2)
                                            pair
                                            previous))))))
                          ps)]
        ;; This is buggy and wrong.
        ;; Really need to flatten the tree of potential protocols,
        ;; pick the best one, and go with it.
        ;; Or, at the very least, pick the "best" version of each
        ;; known protocol and then sort them.
        ;; TODO: Don't be stupid about this.
        (first rankings))
      :lolz)))

(defn authenticate
  "TODO: This really should go through something like Apache Shiro.
As well as checking that the user exists (for example)"
  [{:keys [user-id roles password icanhaz? :as credentials]} system]
  (= icanhaz? :play))

(defmethod dispatch 'ib
  [[_ credentials] system]
  (if (user/existing-user? (:user-id credentials) system)
    (authenticate credentials system)
    :lolz))

(defmethod dispatch :default
  [echo _]
  (throw (RuntimeException. (str "Illegal request: " echo))))
