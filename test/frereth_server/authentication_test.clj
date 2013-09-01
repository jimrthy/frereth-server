(ns frereth-server.authentication-test
  (:use midje.sweet
        clojure.test)
  (:require [frereth-server.authentication :as auth]
            ;; Do I have any actual use for util in this?
            [frereth-server.util-test :as util]))

;;;; This initial version should be totally stateless.
(comment (defn isolated-expect
           "Minimalist request-response, one message at a time.
If expect is really set up so that I can't use it in a function call
like this...that makes it pretty much totally useless to me.
Note that these tests have pretty much nothing to do with authentication.
TODO: Move them to authorization testing."
           [req rep]
           (throw (RuntimeException. "Obsolete and broken"))
           (is (= rep (#'auth/dispatch req)))))

;;; Except that the obvious next step is to make this stateful.
;;; Still want multiple tests, but that's about the complete
;;; sequence, rather than validating each individual message
;;; the way I want to now.
;;; These messages just flat-out do not make sense in isolation.

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
             (#'auth/dispatch :ohai)
             => :oryl?)
       (fact "Doomed handshake"
;; Server should totally reject this.
             (#'auth/dispatch (list 'icanhaz? {:me-speekz
                                               [:blah [nil] :whatever [nil]]}))
             ;; Actually, the server should force us to completely and start over
             ;; from the beginning after that.
             => :lolz)
       ;; This next sequence totally fails under anything resembling real auth/auth.
       ;; Then again, I'd be out of my mind to try to write that myself.
       ;; So just leave what I have as totally stateless for now.
       (fact "Reasonable handshake"
             ;; This seems like a reasonable handshake that ought to be
             ;; allowed to proceed. It's requesting something and announcing
             ;; a realistic hypothetical protocol.
             ;; Of course, anything realistic would have to specify what it
             ;; was requesting.
             (#'auth/dispatch (list 'icanhaz? {:me-speekz
                                               [:frereth [0 0 1]]}))
             => :oryl?)
       (fact "Announcing identity"
             ;; Finally getting to something that resembles authentication
             (#'auth/dispatch (list 'ib "test"))
             => :oryl?)
       (fact (list 'yarly  "Really secure signature")
             => :wachu-wantz?)
       (fact (list 'icanhaz? :play)
             => "RDYPLYR1"))




