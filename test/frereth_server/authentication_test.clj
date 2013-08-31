(ns frereth-server.authentication-test
  (:use expectations)
  (:require [frereth-server.authentication :as auth]
            ;; Do I have any actual use for util in this?
            [frereth-server.util-test :as util]))

;;;; This initial version should be totally stateless.

(defn isolated-expect
  "Minimalist request-response, one message at a time.
If expect is really set up so that I can't use it in a function call
like this...that makes it pretty much totally useless to me.
Note that these tests have pretty much nothing to do with authentication.
TODO: Move them to authorization testing."
  [req rep]
  (expect rep (#'auth/dispatch req)))

;;; Except that the obvious next step is to make this stateful.
;;; Still want multiple tests, but that's about the complete
;;; sequence, rather than validating each individual message
;;; the way I want to now.
;;; These messages just flat-out do not make sense in isolation.


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
(isolated-expect 'ohai 'oryl?)

;; Server should totally reject this.
(isolated-expect 'lolz 
                 (list 'icanhaz? 'me-speekz
                       [:blah nil :whatever nil]))

;; I should get the previous tests working before I try something
;; ambitious like this.
(isolated-expect 'oryl?
                 (list 'icanhaz? 'me-speekz
                       [:frereth [0 0 1]]))

(isolated-expect 'oryl?
                 (list 'ib 'test))

(isolated-expect 'wachu-wantz?
                 (list 'yarly "Really secure signature"))

(isolated-expect "RDYPLYR1"
                 ['icanz? 'play])


