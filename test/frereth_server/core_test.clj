(ns frereth-server.core-test
  (:use clojure.test
        expectations
        frereth-server.core))

(deftest a-test
  (testing "Verify basic testing framework"
    (is (= 1 1))))

(expect nil? nil)

(defn check-expect
  "Verify that expect is at least vaguely flexible enough for what
seems like simplest usage. (i.e. I can use it in a function, can't I?"
  [expected actual]
  (expect expected actual))

(check-expect 1 1)
(check-expect :a :a)
(check-expect "string" "string") ; I'm a little iffy about this one.
