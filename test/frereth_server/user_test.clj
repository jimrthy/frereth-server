(ns frereth-server.user-test
  (:require [frereth-server.system :as sys]
            [frereth-server.user :as u])
  (:use clojure.test))

(defn initial-system
  []
  (u/start {}))

;; Trying to be fancy never seems to work
(defn empty-fixture [test]
  (let [sys (initial-system)]
    (test)))

(use-fixtures :each empty-fixture)

(with-test
  (let [sys (initial-system)]
    (letfn [(create-user [{:keys [user-id roles password] :as credentials}]
              (u/add-user credentials sys))]
      (testing "Creation"
        (testing "minimal"
          (is (= (count (u/existing-user-ids (create-user {:user-id :tom :roles [] :password "abc"}))) 
                 1))
          ;; TODO: Does this count as a separate test?
          (is (u/existing-user? :tom sys))
          (is (not (u/existing-user? :mickey sys))))))))


