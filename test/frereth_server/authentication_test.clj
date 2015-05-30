(ns frereth-server.authentication-test
  (:require [clojure.test :refer (are deftest is testing)]
 [frereth-server.authentication :as auth]
            ;; Do I have any actual use for util in this?
            [frereth-server.util-test :as util]))

;;; These messages just flat-out do not make sense in isolation.

;;; Q: How are midje tests actually broken up?
;;; Can I set up a bunch of facts, interlace code, and declare facts
;;; as needed?
;;; That seems a whole lot more useful (and in-keeping with every
;;; other unit testing framework on the planet) than the alternatives.
;;; Or maybe I'm missing something really obvious.
(testing "basic authentication (not really)"
  ;; This pretty much completely and totally belongs in authorization instead.

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
  (is (= (auth/dispatch :ohai nil) :oryl?) "Basic greeting should have led to a challenge")

  ;; Server should totally reject this.

  ;; Actually, the server should force us to completely and start over
  ;; from the beginning after that.
  (is (= (auth/dispatch (list 'icanhaz? {:me-speekz
                                         [[:blah [nil]] 
                                          [:whatever [nil]]]}) nil)
         :lolz))

  ;; This seems like a reasonable handshake that ought to be
  ;; allowed to proceed. It's requesting something and announcing
  ;; a realistic hypothetical protocol.
  ;; Of course, anything realistic would have to specify what it
  ;; was requesting.
  (is (= (auth/dispatch (list 'icanhaz? {:me-speekz
                                         {:blah [1 2 3]
                                          :frereth [[0 0 1]
                                                    [3 2 1]]}}) nil)
         [:frereth {:major 0 :minor 0 :build 1}]) "Reasonable handshake")

       ;; This next sequence is totally horrid. I like the modularity, but
       ;; it all needs to happen in one step.
       (comment (do
                  ;; Finally getting to something that resembles authentication
                  (is (= (#'auth/dispatch (list 'ib "test"))
                         :oryl?)
                      "Announcing identity")
                  (is (= (#'auth/dispatch (list 'yarly  "Really secure signature"))
                         :wachu-wantz?) "Authorization failed")
                  (is (= (#'auth/dispatch (list 'icanhaz? :play))
                         "RDYPLYR1")
                      "Final connection")))

       ;; Desperately need counter-examples that fail
       (is (= (auth/dispatch (list 'ib {:user-id "test"
                                      :password "Really secure signature"
                                      :icanhaz? :play}))
              "RDYPLYR1")
           "Authenticating"))




