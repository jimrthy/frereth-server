(ns frereth-server.core
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]
   [cljeromq.core :as mq]
   [frereth-server.config :as config]
   [frereth-server.system :as sys]))

(defn get-cpu-count 
  "How many CPUs are available?"
  []
  ;; Note that it's extremely overly simplistic to try to max out the
  ;; threads for message processing, for most intents and purposes.
  ;; At least,  it really seems that way from my current perspective.
  ;; At the very worst, should use the min of this and a value specified in
  ;; config.
  ;; Whatever. This is a start.
  (.availableProcessors (Runtime/getRuntime)))

(defn -main
  [& args]
  ;; The basic gist:
  (let [;; Basic system first
        dead-system (sys/init)
        system (sys/start dead-system)

        ;; FIXME: Should load up some sort of info about which and how many
        ;; thread are actually running now.
        ]
    (log/trace "Have " (Thread/activeCount) " threads")))
