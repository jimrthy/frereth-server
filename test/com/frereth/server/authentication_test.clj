(ns com.frereth.server.authentication-test
  (:require [clojure.pprint :refer (pprint)]  ; TODO: Switch to puget
            [clojure.test :refer (are deftest is testing)]
            [com.frereth.server.authentication :as auth])
  (:import [clojure.lang ExceptionInfo]))

;;; These messages just flat-out do not make sense in isolation.

(deftest complicated-auth
  (testing
      "basic authentication (not really)"
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

    ;; Server should totally reject this
    ;; (the schema's wrong).
    ;; Actually, the server should force us to completely and start over
    ;; from the beginning after it.
    (is (= :lolz (auth/dispatch (list 'icanhaz? {:com.frereth.server.authentication/protocol-versions [[:blah [nil]]
                                                                                                       [:whatever [nil]]]})
                                nil))
        "Missing protocols should fail")

    (is (= :lolz
           (auth/dispatch (list 'icanhaz? {:com.frereth.server.authentication/protocol-versions
                                           {:blah [1 2 3]
                                            :whatever [:a "1.2.5"]}})
                          nil))
        "Invalid protocols should fail")

    (let [legal-protocol (first (auth/known-protocols))]
      (is (= :lolz
             (auth/dispatch (list 'icanhaz? {:com.frereth.server.authentication/protocol-versions
                                             {:blah [nil]
                                              :frereth nil
                                              :whatever [nil]}})
                            nil))
          "Asking for no versions should have gotten laughed at")

      ;; This seems like a reasonable handshake that ought to be
      ;; allowed to proceed. It's requesting something and announcing
      ;; a realistic hypothetical protocol.
      ;; Of course, anything realistic would have to specify what it
      ;; was requesting.
      (is (= {:name :frereth :version [0 0 1]}
             (auth/dispatch (list 'icanhaz? {:com.frereth.server.authentication/protocol-versions
                                             {:blah [1 2 3]
                                              :frereth [[0 0 1]
                                                        [3 2 1]]}}) nil)) "Reasonable handshake"))

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
    (try (auth/dispatch (list 'ib {:user-id "test"
                                   :password "Really secure signature"
                                   :icanhaz? :play})
                        nil)
         (is false "Can't authenticate without a system")
         (catch ExceptionInfo ex
           (let [data (.getData ex)]
             (is (= (:problem data) "Missing users atom")))))))

(comment
  (let [message (list 'icanhaz? {:com.frereth.server.authentication/protocol-versions
                                  {:blah [1 2 3]
                                   :frereth [[0 0 1]
                                             [3 2 1]]}})]
    (auth/dispatch message nil)
    #_(auth/extract-known-protocols (second message))
    #_(s/conform :com.frereth.server.authentication/protocol-versions (second message)))
  )
