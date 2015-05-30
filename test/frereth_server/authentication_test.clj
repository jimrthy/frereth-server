(ns frereth-server.authentication-test
  (:use midje.sweet
        clojure.test)
  (:require [frereth-server.authentication :as auth]
            ;; Do I have any actual use for util in this?
            [frereth-server.util-test :as util]))

;;; These messages just flat-out do not make sense in isolation.

;;; Q: How are midje tests actually broken up?
;;; Can I set up a bunch of facts, interlace code, and declare facts
;;; as needed?
;;; That seems a whole lot more useful (and in-keeping with every
;;; other unit testing framework on the planet) than the alternatives.
;;; Or maybe I'm missing something really obvious.
(facts "basic authentication (not really)"
       ;; This pretty much completely and totally belongs in authorization instead.
       (fact "Basic greeting leads to challenge"
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
             (auth/dispatch :ohai nil)
             => :oryl?)
       (fact "Doomed handshake"
             ;; Server should totally reject this.
             (auth/dispatch (list 'icanhaz? {:me-speekz
                                             [[:blah [nil]] 
                                              [:whatever [nil]]]}) nil)
             ;; Actually, the server should force us to completely and start over
             ;; from the beginning after that.
             => :lolz)
       (fact "Reasonable handshake"
             ;; This seems like a reasonable handshake that ought to be
             ;; allowed to proceed. It's requesting something and announcing
             ;; a realistic hypothetical protocol.
             ;; Of course, anything realistic would have to specify what it
             ;; was requesting.
             (auth/dispatch (list 'icanhaz? {:me-speekz
                                             {:blah [1 2 3]
                                              :frereth [[0 0 1]
                                                        [3 2 1]]}}) nil)
             => [:frereth [0 0 1]])

       ;; This next sequence is totally horrid. I like the modularity, but
       ;; it all needs to happen in one step.
       (comment (do
                  (fact "Announcing identity"
                        ;; Finally getting to something that resembles authentication
                        (#'auth/dispatch (list 'ib "test"))
                        => :oryl?)
                  (fact (list 'yarly  "Really secure signature")
                        => :wachu-wantz?)
                  (fact (list 'icanhaz? :play)
                        => "RDYPLYR1")))
       ;; Desperately need counter-examples that fail
       (fact "Authenticating"
             auth/dispatch (list 'ib {:user-id "test"
                                      :password "Really secure signature"
                                      :icanhaz? :play})
             => "RDYPLYR1"))




