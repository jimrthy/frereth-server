(ns frereth-server.util-test
  (:require [frereth-server.system :as sys]))

(defn wrap [test]
  ;; This almost seems...what?
  ;; I wonder what I was planning for that comment.
  (let [stopped-world (sys/init)
        world (sys/start stopped-world)]
    (try
      (test world)
      (finally
        (sys/stop world)))))
