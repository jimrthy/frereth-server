(ns frereth-server.core-test
  (:use clojure.test
        midje.sweet
        frereth-server.core))

(facts "How much effort will it take to wrap my brain around midje?"
       (fact "Equality checks are boring"
             ;; This next test is an utter lie
             1 => 1)
       (fact "Although that seems to ultimately be what's really going on"
             (into {:a 1 :b 2} {:c 3 :d 4})
             => {:a 1 :b 2 :c 3 :d 4}))

