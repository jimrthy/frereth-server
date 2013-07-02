(ns frereth-server.system-spec
  (:require [frereth-server.system :as sys])
  (:use [speclj.core]))

(describe "Basic System Manipulation"
          (let [world (atom nil)]
              (before-all
               (println "Preparing")
               (reset! world (sys/init)))

              (after-all
               (println "Ending")
               (reset! world nil))

              (before (println "\tTesting...")
                      (swap! world sys/start))

              (after (println "\t...Tested")
                     (swap! world sys/stop))

              (it "World setup/teardown"
                  (let [world @world]
                    (should world)
                    (should @(:network-context world))
                    (should @(:master-connection world))
                    (should-not @(:done world))))))
