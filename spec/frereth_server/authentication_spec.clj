(ns frereth-server.authentication-spec
  (:require [frereth-server.authentication :as auth]
            [frereth-server.system :as sys])
  (:use [speclj.core :as spec])
  (:gen-class))

(let [world (atom nil)
      client-atom (atom nil)]

  (describe "Check authentication"
            (before-all
             (println "Preparing")
             (reset! world (sys/init)))

            (after-all
             (println "Ending")
             (if-let [client @client-atom]
               (do (.close client)
                   (reset! client-atom nil))
               (println "NULL client. Huh?"))

             (reset! world nil))

            (before
             (println "\tTesting...")
             (swap! world sys/start)
             (reset! client-atom (throw (RuntimeException.
                                         "This probably doesn't make any sense at all"))))

            (after
             (println "\t...Tested")
             (swap! world sys/stop))

            (it "Kill"
                (should= "K" (#'auth/dispatch "dieDieDIE!!")))

            
            ;; What else makes sense to test here?
))

(run-specs)
