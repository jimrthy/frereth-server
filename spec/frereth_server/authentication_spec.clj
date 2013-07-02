(ns frereth-server.authentication-spec
  (:require [speclj.core :as spec]
            [frereth-server.authentication :as auth]
            [frereth-server.config :as config]
            [frereth-server.system :as sys]
            [zguide.zhelpers :as mq])
  (:gen-class))

(let [world (atom nil)
      client (atom nil)]

  (spec/describe "Validate authentication"
                 (spec/before-all
                  (println "Preparing")
                  (reset! world (sys/init)))

                 (spec/after-all
                  (println "Ending")
                  ;; Next line is causing an NPE. WTF?
                  (if-let [client @client]
                    (do (.close @client)
                        (reset! client nil))
                    (println "NULL client. Huh?"))

                  (reset! world nil))

                 (spec/before
                  (println "\tTesting...")
                  (swap! world sys/start)
                  (reset! client (let [ctx (@world :network-context)
                                       s (mq/socket @ctx mq/req)
                                       address (format 
                                                "tcp://127.0.0.1:%d"
                                                config/auth-port)]
                                   (mq/connect s address)
                                   s)))

                 (spec/after
                  (println "\t...Tested")
                  (swap! world sys/stop))

                 (spec/it "Kill"
                          (spec/should= "K" (#'auth/dispatch "dieDieDIE!!")))

                 (spec/it "Kill Message"
                          (reset! (:done @world) true)
                          (let [auth-thread (:authentication-thread world)]
                            (mq/send @client "dieDieDIE!!")
                            (let [response (mq/recv-str @client)]
                              (spec/should= "K" response)
                              (.join @auth-thread 50)
                              (spec/should (not (.isAlive @auth-thread))))))))

(spec/run-specs)
