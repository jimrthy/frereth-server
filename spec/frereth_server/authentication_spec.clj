(ns frereth-server.authentication-spec
  (:require [frereth-server.authentication :as auth]
            [frereth-server.system :as sys])
  (:use [speclj.core :as spec])
  (:gen-class))

(let [world (atom nil)
      client-atom (atom nil)]

  (describe "Check authentication"
            (before-all
             (println "Preparing")
             (reset! world (sys/init)))

            (after-all
             (println "Ending")
             (if-let [client @client-atom]
               (do (.close client)
                   (reset! client-atom nil))
               (println "NULL client. Huh?"))

             (reset! world nil))

            (before
             (println "\tTesting...")
             (swap! world sys/start)
             (reset! client-atom (throw (RuntimeException.
                                         "This probably doesn't make any sense at all"))))

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
                (let [helo (auth/dispatch 'ohai)]
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
                (let [signature-request (auth/dispatch '(icanhaz? me-speekz
                                                                  [:blah nil :whatever nil]))]
                  (should (= signature-request 'lolz)))
                ;; I should get the previous tests working before I try something
                ;; ambitious like this.
                (let [signature-request 
                      (auth/dispatch '(icanhaz? me-speekz
                                                [:frereth [0 0 1]]))]
                  (should (= signature-request 'oryl?)))
                
                (let [signature-request
                      (auth/dispatch '(ib test))]
                  (should (= signature-request 'oryl?)))

                (let [signature-validated (auth/dispatch '(yarly "Really secure signature"))]
                  (should (= signature-validated 'wachu-wantz?)))

                (let [home-page (auth/dispatch '(icanhaz? play))]
                  ;; This message needs to move further down the chain
                  ;; (after the player has the home page, etc), but it's
                  ;; a start.
                  (should (= home-page "RDYPLYR1"))))))

(run-specs)
