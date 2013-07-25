(ns frereth-server.core
  (:gen-class)
  (:require ;[qbits.jilch.mq :as mq]
   [zguide.zhelpers :as mq]
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
        
        ;; More specific pieces...tempting to separate these into their own
        ;; function just to emphasize that this is really the next step.
        ;; Then again, I've been fantasizing lately about setting up
        ;; something resembling the BSD init thing, complete with runlevels.
        ;; Adding complications at this point leads to madness.
        thread-count (get-cpu-count)
        master-port (:master (:ports system))]
    (println "Listening on " thread-count " thread connections")
    (mq/with-context [context thread-count]
      (do
        (with-open [master-socket (-> context
                                      (mq/socket (mq/const :dealer))
                                      (mq/bind (str "tcp://127.0.0.1:" master-port)))]
          ;; Let's start with ECHO
          (let [pull (future (mq/recv master-socket))]
            (let [msg @pull]
              (mq/send master-socket msg))))))))
