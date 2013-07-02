(ns frereth-server.authentication-spec
  (:require [speclj.core :as spec]
            [frereth-server.authentication :as auth]
            [frereth-server.config :as config]
            [frereth-server.system :as sys]
            [zguide.zhelpers :as mq])
  (:gen-class))

(spec/describe "Validate authentication"
               (let [world (atom nil)
                     client (atom nil)]

                 (spec/before-all
                  (println "Preparing")
                  (reset! world (sys/init))
                  (reset! client (let [ctx (world :network-context)
                                       s (mq/socket ctx mq/req)]
                                   (mq/connect s (format "tcp://127.0.0.1/%d" config/auth-port)))))

                 (spec/after-all
                  (println "Ending")
                  (.close @client)
                  (reset! client nil)
                  (reset! world nil))

                 (spec/before
                  (println "\tTesting...")
                  (swap! world sys/start))

                 (spec/after
                  (println "/t...Tested")
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
