(ns frereth-server.core
  (:gen-class)
  (:require [qbits.jilch.mq :as mq]
            [frereth-server.config :as config]))

(defn get-cpu-count 
  "How many CPUs are available?"
  []
  ;; This seems like it really should just be a very simple java interop call.
  (throw (RuntimeException. "I still have to figure out what it is")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; The basic gist:
  (let [thread-count (get-cpu-count)]
    (mq/with-context context thread-count
      (do
        ;; Note that config/*port* controls where to listen
        (let [sockets (build-sockets)]
          (do-stuff))))))
