(ns com.frereth.server.authentication
  "This is my initial approach toward the initial
client hand-shake.
TODO: Add this into the mix before we ever start
setting up things like the login dialog.

We have to make sure things are speaking the same language
before they start trying to communicate about complex things.

TODO: Rename this to something like protocol-handshake"
  (:require [clojure.pprint :refer (pprint)]
            [clojure.spec :as s]
            [com.frereth.server.connection-manager :as connection-manager]
            [hara.event :refer (raise)]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; specs

;; It's tempting to force this into major.minor.build
;; That seems like too heavy-handed an approach for something this
;; embryonic
(s/def ::version any?)

(s/def ::versions (s/coll-of ::version))

;; Keyword names of protocols and the supported versions
(s/def ::protocol-versions (s/map-of keyword? ::versions))

;; Protocol description that can be scored
(s/def ::name keyword?)
(s/def ::scorable-protocol (s/keys :req [::name ::version]))
(s/def ::scorable-protocols (s/coll-of ::scorable-protocol))

(s/def ::score (s/map-of (s/and keyword?
                                #(= ::score %))
                         integer?))
;; Weighted value describing how 'good' a particular protocol/version is
(s/def ::protocol-score (s/merge ::scorable-protocol ::score))

;; Q: Is this the right spec?
;; This very much seems like it should have been
;; (s/alt keyword? any?)
;; I strongly suspect I just did it this way originally because
;; I couldn't come up with a good way to describe the other in schema
;; TODO: Make sure this is what I really want
(s/def ::message (s/coll-of (s/tuple keyword? any?)))

(defmulti dispatch
  "Based on message, generate response.
Q: Do these make sense here?
A: No. Absolutely not.
The auth <s>socket</s> app shouldn't be dealing with server management."
  (fn [msg _]
    "TODO: Refactor this to use core.match
TODO: Swap the parameter order

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

(s/fdef known-protocols
        :args (s/cat)
        :ret (s/coll-of ::name))
(defn known-protocols
  []
  #{:frereth})

(s/fdef known-protocol?
        :args (s/cat :p ::name)
        :ret boolean?)
(defn known-protocol?
  "Is this a protocol the server knows how to speak?
  FIXME: This isn't a setting to be buried somewhere in implementation
  details like this. Should really have a fully configurable set of
  dynamic plugins that make these easy to swap in and out at will.
  I'm starting as small as I can get away with."
  [p]
  ((known-protocols) p))

(s/fdef compare-score
        :args (s/cat :p1 ::protocol-versions
                     :p2 ::protocol-versions)
        :ret ::protocol-score)
(defn compare-score
  "This is really something half-baked that should just go away"
  [p1 p2]
  (comment (fn [pair previous]
             (let [score1 (second pair)
                   score2 (second previous)]
               (if (> score1 score2)
                 pair
                 previous))))
  (let []
    (raise :not-implemented)))

(s/fdef protocol-versions->scorable
        :args (s/cat :protocol-name ::name
                     :vs ::versions)
        :ret ::scorable-protocols)
(defn  protocol-versions->scorable
  [protocol-name vs]
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

(s/fdef extract-known-protocols
        :args (s/cat :protocols ::protocol-versions)
        :ret (s/coll-of ::name))
(defn extract-known-protocols
  [protocols]
  (->> protocols
       ;; TODO: Really should catch errors here
       (s/conform ::protocol-versions)
       ::protocol-versions
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

(s/fdef score-protocol
        :args (s/cat :protocol ::scorable-protocol)
        :ret (s/nilable ::protocol-score))
(defn score-protocol
  "How does one particular protocol/version
weigh against the rest?

Returns nil if the version isn't supported"
  [protocol]
  ;; FIXME: This should probably get a lot more
  ;; complicated
  (assoc protocol :score 1))

(s/fdef calculate-sorted-protocol-scores
        :args (s/cat :recognized (s/coll-of ::name)
                     :protocols ::protocol-versions)
        :ret (s/nilable ::protocol-score))
(defn calculate-sorted-protocol-scores
  [recognized protocols]
  (let [f-spec (s/fspec :args (s/cat :p ::name)
                        :ret ::scorable-protocols)
        f (fn [p]
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
                                             protocols))

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
  (let [protocol-versions (second msg)]
    (if protocol-versions
      (let [spec (s/keys :req [::protocol-versions])]
        (if (s/valid? spec protocol-versions)
          (let [protocols (::protocol-versions protocol-versions)]
            (if-let [recognized
                     (extract-known-protocols protocol-versions)]
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
                (comment) (log/warn "No known protocol in\n" protocols)
                :lolz)))
          (do
            (log/warn (str "Invalid :me-speekz\n"
                           {:expected-format spec
                            :actual msg
                            :problem (s/explain spec msg)}))
            :lolz)))
      (do
        (log/warn "Missing protocol list from\n" msg)
        :lolz))))

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
