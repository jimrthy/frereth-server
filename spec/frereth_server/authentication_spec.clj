(ns frereth-server.authentication-spec
  (:require [frereth-server.authentication :as auth]
            [frereth-server.system :as sys])
  (:use [speclj.core :as spec])
  (:gen-class))

;; This whole thing needs to just go away.

(let [world (atom nil)]

  (describe "Check authentication"
            (before-all
             (println "Preparing")
             ;; FIXME: sys/init binds the actual auth socket, which does
             ;; not make any sense here.
             ;; refs to world and client-atom should be able to happily go away.
             ;;
             ;; Well, for a first pass.
             ;; Anything resembling a second version will need some sort of
             ;; stateful server on the other end that tracks login requests
             ;; and hands out auth keys.
             ;; It seems like overkill here and now, but I know that I need it, eventually.
             ;;
             ;; The (well, one) obnoxious piece is that I don't have any reason at
             ;; all to initialize a socket interface for this.
             ;;
             ;; The very idea is stupid and needs a re-think.
             (reset! world (sys/init)))

            (after-all
             (println "Ending")
             ;; Is there anything at all I can do here that's worthwhile?
             (reset! world nil))

            (before
             (println "\tTesting...")
             (swap! world sys/start))

            (after
             (println "\t...Tested")
             (swap! world sys/stop))

            ;; This really does not make sense here. It's a communications detail.
            (comment (it "Kill"
                         (should= "K" (#'auth/dispatch "dieDieDIE!!"))))

            
            ;; Q: What else makes sense to test here?
            ;; A: The messaging sequences from auth_socket_spec, at a minimum
            ;; This initial version should be totally stateless.
            (it "Handshake. This should really be multiple tests."
                ;; Except that the obvious next step is to make this stateful.
                ;; Still want multiple tests, but that's about the complete
                ;; sequence, rather than validating each individual message
                ;; the way I want to now.
                ;; These messages just flat-out do not make sense in isolation.
                (let [helo (#'auth/dispatch 'ohai)]
                  ;; Q: Why?
                  ;; Most servers don't *need* any sort of auth. In general.
                  ;; A: Yes, they do.
                  ;; This is something that's horribly broken about the www in general.
                  ;; IPv6 is supposed to fix it, but who knows if or when it will ever
                  ;; happen.
                  ;; People are malicious and vindictive. Servers should need to go
                  ;; out of their way to totally ignore identity, while still
                  ;; respecting privacy. It's vital to build this sort of thing into
                  ;; the foundation.
                  (should (= helo 'oryl?)))
                ;; Server should totally reject this.
                (let [signature-request (#'auth/dispatch '(icanhaz? me-speekz
                                                                  [:blah nil :whatever nil]))]
                  (should (= signature-request 'lolz)))
                ;; I should get the previous tests working before I try something
                ;; ambitious like this.
                (let [signature-request 
                      (#'auth/dispatch '(icanhaz? me-speekz
                                                [:frereth [0 0 1]]))]
                  (should (= signature-request 'oryl?)))
                
                (let [signature-request
                      (#'auth/dispatch '(ib test))]
                  (should (= signature-request 'oryl?)))

                (let [signature-validated (#'auth/dispatch '(yarly "Really secure signature"))]
                  (should (= signature-validated 'wachu-wantz?)))

                (let [home-page (#'auth/dispatch '(icanhaz? play))]
                  ;; This message needs to move further down the chain
                  ;; (after the player has the home page, etc), but it's
                  ;; a start.
                  (should (= home-page "RDYPLYR1"))))))

(run-specs)
