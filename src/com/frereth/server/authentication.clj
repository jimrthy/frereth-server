(ns com.frereth.server.authentication
  (:require [clojure.pprint :refer (pprint)]
            [com.frereth.server.connection-manager :as connection-manager]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def version
  "It's tempting to force this into major.minor.build

That seems like too heavy-handed an approach for something this
embryonic"
  s/Any)

(def versions [version])

(def protocol-versions
  "Keyword names of protocols and the supported versions"
  {s/Keyword versions})

(def scorable-protocol
  "Protocol description that can be scored"
    {:name s/Keyword
     :version version})
(def scorable-protocols [scorable-protocol])

(def protocol-score
  "Weighted value describing how 'good' a particular protocol/version is"
  (assoc scorable-protocol :score s/Int))

(def message [(s/one s/Keyword "type")
              s/Any])

(defmulti dispatch
  "Based on message, generate response.
Q: Do these make sense here?
A: No. Absolutely not.
The auth socket shouldn't be dealing with server management."
  (fn [msg _]
    "TODO: Refactor this to use core.match

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
        (do
          (raise {:problem msg
                  :message "What are my other options?"}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internals

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

(s/defn known-protocols :- s/Keyword
  []
  [:frereth])

(defn known-protocol?
  "Is this a protocol the server knows how to speak?
FIXME: This isn't a setting to be buried somewhere in implementation
details like this. Should really have a fully configurable set of
dynamic plugins that make these easy to swap in and out at will.
I'm starting as small as I can get away with."
  [p]
  (some #(= p %) (known-protocols)))

(s/defn compare-score :- protocol-score
  "This is really something half-baked that should just go away"
  [p1 :- protocol-versions
   p2 :- protocol-versions]
  (comment (fn [pair previous]
             (let [score1 (second pair)
                   score2 (second previous)]
               (if (> score1 score2)
                 pair
                 previous))))
  (let []
    (raise :not-implemented)))

(s/defn protocol-versions->scorable :- scorable-protocols
  [protocol-name :- s/Keyword
   vs :- versions]
  (map (fn [v]
         {:name protocol-name, :version v})
       vs))

(comment
  (let [sample {:blah [1 2 3]
                :frereth [[0 0 1]
                          [3 2 1]]}]
    (->> sample
         (s/validate protocol-versions)
         keys
         (filter known-protocol?))))

(s/defn ^:always-validate extract-known-protocols :- [s/Keyword]
  [protocols :- protocol-versions]
  (->> protocols
       (s/validate protocol-versions)
       keys
       (filter known-protocol?)
       seq))

(comment
  (let [sample {:blah [1 2 3]
                :frereth [[0 0 1]
                          [3 2 1]]}
        recognized (extract-known-protocols sample)
        f (s/fn :- scorable-protocols
            [p :- s/Keyword]
            (println "Checking for versions of" p)
            (let [versions (sample p)]
              (println "Scoring" versions)
              (protocol-versions->scorable p versions)))]
    (comment (pprint recognized))
    (->> recognized
         (mapcat f))))
(comment
  (let [protocols {:blah [1 2 3]
                   :frereth [[0 0 1]
                             [3 2 1]]}
        f (s/fn :- scorable-protocols
            [p :- s/Keyword]
            (let [versions (get protocols p)]
              (log/info "Protocol" p "has versions" versions)
              (protocol-versions->scorable p versions)))
        filtered (extract-known-protocols protocols)]
    (mapcat f filtered)))

(s/defn score-protocol :- protocol-score
  "How does one particular protocol/version
weigh against the rest?

Returns nil if the version isn't supported
Q: How do I specify that w/ schema?"
  [protocol :- scorable-protocol]
  ;; FIXME: This should probably get a lot more
  ;; complicated
  (assoc protocol :score 1))

(s/defn calculate-sorted-protocol-scores
  [recognized :- [s/Keyword]
   protocols :- protocol-versions]
  (let [f (s/fn :- scorable-protocols
            [p :- s/Keyword]
            (let [versions (get protocols p)]
              (protocol-versions->scorable p versions)))]
    (->> recognized
         (mapcat f)
         (map score-protocol)
         (filter identity)
         (sort-by :score))))

(comment (let [protocols {:blah [1 2 3]
                          :frereth [[0 0 1]
                                    [3 2 1]]}]
           (calculate-sorted-protocol-scores [:frereth]
                                             protocols)))

(comment
  (let [protocols {:blah [1 2 3]
                   :frereth [[0 0 1]
                             [3 2 1]]}
        recognized (extract-known-protocols protocols)
        scored (calculate-sorted-protocol-scores
                recognized
                protocols)
        best (first scored)
        response (dissoc best :score)]
    response))

(defmethod dispatch 'icanhaz?
  [msg system]
  "Loop through the protocols the client claims to speak. Choose the best
one this server also speaks. Return that, or :lolz if there are no matches."
  (let [schema [(s/one s/Symbol "call-sign") {:me-speekz {s/Keyword [s/Any]}}]]
    ;; :me-speekz maps protocol names to available versions
    (s/validate schema msg))
  (if-let [protocols (-> msg second :me-speekz)]
    (if-let [recognized
             (extract-known-protocols protocols)]
      (let [scored
            (calculate-sorted-protocol-scores recognized
                                              protocols)]
        (if-let [result (dissoc (first scored) :score)]
          result
          (do
            ;; This really shouldn't be possible
            (log/warn "Lost all protocols in\n" recognized)
            :lolz)))
      (do
        (comment (log/warn "No known protocol in\n" protocols))
        :lolz))
    (do
      (log/warn "Missing protocol list from\n" msg)
      :lolz)))

(defn authenticate
  "TODO: This really should go through something like Apache Shiro.
As well as checking that the user exists (for example)"
  [{:keys [user-id roles password icanhaz?] :as credentials} system]
  (= icanhaz? :play))

(defmethod dispatch 'ib
  [[_ credentials] system]
  (log/debug "Trying to validate " (dissoc credentials :password) "\nin\n" system)
  (if (connection-manager/existing-user? system (:user-id credentials))
    (authenticate credentials system)
    :lolz))

(defmethod dispatch :default
  [echo _]
  (throw (RuntimeException. (str "Illegal request: " echo))))
