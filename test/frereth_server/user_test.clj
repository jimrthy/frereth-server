(ns frereth-server.user-test
  (:require [frereth-server.user :as u])
  (:use clojure.test))

(defn initial-system (u/start {}))

(defn empty-fixture [test]
  (let [sys (initial-system)]
    (test)))

(use-fixture :each empty-fixture)

(with-test
  (defn create-user [{:keys [user-id roles password :as credentials]}]
    (u/add-user credentials sys))
  (testing "Creation"
    (testing "minimal"
      (is (= (count (u/existing-user-ids (create-user {:user-id :tom :roles [] :password "abc"}))) 
             1))
      ;; TODO: Does this count as a separate test?
      (is (u/existing-user? :tom sys))
      (is (not (u/existing-user? :mickey sys))))))


