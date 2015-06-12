(ns frereth.server.auth-socket-test
  (:require [clojure.test :refer (are is deftest testing)]
            [frereth.server.system :as sys]
            [frereth.server.auth-socket :as auth]
            [cljeromq.constants :as mqk]
            [cljeromq.core :as mq]))

(defn setup
  "Because I need a socket to send these requests from.
Although this particular idea is half-baked and needs to move
*way* down my priority list."
  [world]
  (let [ctx @(world :network-context)
        s (mq/socket! @ctx (:req (mqk/const :socket-types)))
        address (format "tcp://127.0.0.1:%d" (:auth (:ports world)))]
    (mq/connect! s address)
    {:socket s}))

(defn teardown
  "It's tough to believe that expectations doesn't build this in...
but I don't see it anywhere"
  [locals]
  (.close (:socket locals)))

(defn suite
  "Let sys set up the world for testing, pass that to setup, call f, then call teardown
and util will finish.
Whew! I'm more than a little amazed that I still don't have any use for a macro.
I'm more than a little leery that my unit tests are needing to be so fancy.
Then again, I guess this is saving a ton of duplicated code.

This *so* does not belong in here."
  [f]
  (let [local (fn [world]
                (let [locals (setup world)]
                     (try
                       (f world locals)
                       (finally (teardown locals)))))]))

(defn req<->rep
  "Send a request to the authentication socket's auth port, return its response"
  [r s]
  (mq/send! s r)
  (mq/recv-str! s))

;; I don't particularly feel happy about using strings like this...
;; I feel like I should really be doing this with symbols, or
;; possibly keywords
(let [kill (fn [world locals]
             (reset! (:done @world) true)
             ;; TODO: But we do need a socket to send it to!
             (mq/send! "ping") ; Shouldn't matter what goes here
             (Thread/sleep 50) ; Should be plenty of time for it to shut down

             (testing "Should be done"
                   (let [auth-thread (:authentication-thread world)]
                     (is (not (.isAlive auth-thread)))
                     (when (.isAlive auth-thread)
                       (throw (RuntimeException. "Listener thread still alive"))))))]
  ;; Wow. This seems either really sweet or really sick.
  ;; Worry about it later...this is really just clearing a path so
  ;; I can move forward with something that resembles real work.
  (suite kill))

;; Yeah. This approach just does not seem realistic at all.
;; It's the sort of thing that should be tested, but not at this level.
(comment (let [basic-login (fn [world locals]
                             ;; This is trickier than I realized.
                             ;; I really want to send a multi-part message.
                             ;; Something along these lines:
                             ;; 1) ib <user-id>
                             ;; 2) me-wantz-play <protocol versions client understands>
                             ;; 3) <signature>
                             ;; That approach seems like it will just make
                             ;; life simpler from any angle that comes to mind.
                             ;; Dispatcher gets more complicated,but that isn't
                             ;; a big deal.
                             ;; Meh. Do the Q&A version or block sequence.
                             ;; Whatever. They should both work here.
                             ;; And I really don't care what they look like over
                             ;; the wire...until we get to security.
                             ;;
                             ;; c.f. http://rfc.zeromq.org/spec:27
                             ;; (the 0mq Authentication Protocol)
                             (let [login-sequence ["ohai"
                                                   ;; Q: Can I make a client dumber than this?
                                                   ;; A: I really shouldn't challenge
                                                   ;; myself that way.
                                                   ["icanhaz?" "me-speekz"
                                                    [:youre-kidding nil "login-id" nil]]
                                                   ["yarly" "ib" "test"]

                                                   ["yarly" "Really secure signature"]
                                                   ["icanhaz?" "me-wantz-play"]]
                                   client (:socket locals)]
                               ;; client is now an mq/req socket, that should be
                               ;; connected to the server we're testing
                               (expect "What?"
                                       (req<->rep client login-sequence))))]
           (suite basic-login)))

"Pathological login mess.
Deliberately designed to be as ugly and painful as I can make it.
I *want* to try to break the server here.
Actually, I need lots and lots of these.
And something that builds a million threads doing this sort of thing
and throws them all at the server at once.
Baby steps."
(comment (let [path-log (fn
                          [world locals]
                          ;; Q: Why am I even thinking about subjecting myself to this?
                          ;; A: Because this is what unit tests are for.
                          ;; Just write the obnoxious thing so it's documented.

                          ;; Brain-dead client. Don't care about the response
                          ;; at all.
                          ;; Server needs to be robust enough to handle clients
                          ;; this evil. And worse.
                          ;; Skip reading the response...this is really a regression
                          ;; test case. Should test the normal flow control first.

                          ;; Actually, this client isn't particularly evil.
                          ;; Barely a kissing cousin, in fact.
                          (let [s (:socket locals)]
                            (expect 'ohai
                                    (req<->rep 'hai s))
                            (expect 'lolz
                                    (req<->rep ['me-speekz nil]))
                            (expect 'oryl?
                                    (req<->rep (list 'ib 'test)))

                            ;; Now we're getting into something deeper...
                            ;; really assumes that the server is maintaining some sort
                            ;; of state with the connection.
                            ;; Need to test that.
                            ;; At a bare minimum, auth keys need to be passed around.
                            ;; Better yet:
                            ;; How does this work in the wild and whose previous art
                            ;; can I use for a foundation?
                            ;; This is commented out in the original...apparently I
                            ;; wrote this and then decided that it's *way* too
                            ;; ambitious for this version.
                            (comment (expect 'wachu-wantz?
                                             (req<->rep ['yarly "Really secure signature"])))
                            (expect "RDYPLYR1"
                                    (req<->rep ['icanhaz? 'play]))))]))


(comment
  (let [s (-> system :action-socket :socket)]
    (mq/raw-recv! s :dont-wait)))
