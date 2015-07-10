(ns com.frereth.server.core-test
  (:require [clojure.test :refer (are deftest is)]
            [com.frereth.server.core :refer :all]))

(deftest basic-equality []
  (is (= 1 1) "Reality is real")
  (is (= (into {:a 1 :b 2} {:c 3 :d 4})
         {:a 1 :b 2 :c 3 :d 4})
      "Immutable data structures work"))
