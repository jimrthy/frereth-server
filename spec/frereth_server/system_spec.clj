(ns frereth-server.system-spec
  (:require [frereth-server.system :as sys])
  (:use [speclj.core]))

(let [world (atom nil)
      log "events.txt"]
  (describe "Basic System Manipulation"
            (before-all
             (spit log "Preparing\n")
             (reset! world (sys/init)))

            (after-all
             (spit log "Ending\n")
             (reset! world nil))

            (before (spit log "\tTesting...\n")
                    (swap! world sys/start))

            (after (spit log "\t...Tested\n")
                   (swap! world sys/stop))

            (it "World setup/teardown"
                (let [msg (format "%s : %s\n" world @world)]
                  (spit log msg)
                  (let [world @world]
                    (should world)
                    (should @(:network-context world))
                    (should @(:master-connection world))
                    (should-not @(:done world)))))))

(run-specs)
