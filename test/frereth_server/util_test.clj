(ns frereth-server.util-test
  (:require [com.stuartsierra.component :as component]
 [frereth-server.system :as sys]))

(defn wrap [test]
  ;; This almost seems...what?
  ;; I wonder what I was planning for that comment.
  (let [stopped-world (sys/init)
        world (component/start stopped-world)]
    (try
      (test world)
      (finally
        (component/stop world)))))
