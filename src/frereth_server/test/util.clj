(ns frereth-server.test.util
  (:require [frereth-server.system :as sys]))

(defn wrap [test]
  ;; This almost seems 
  (let [stopped-world (sys/init)
        world (sys/start stopped-world)]
    (try
      (test world)
      (finally
        (sys/stop world)))))
