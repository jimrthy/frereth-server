(ns frereth-server.authentication-spec
  (:require [frereth-server.authentication :as auth]
            [frereth-server.config :as config]
            [frereth-server.system :as sys]
            [zguide.zhelpers :as mq])
  (:use [speclj.core :as spec])
  (:gen-class))

(let [world (atom nil)
      client (atom nil)]

  (describe "Check authentication"
            (before-all
             (println "Preparing")
             (reset! world (sys/init)))

            (after-all
             (println "Ending")
             (if-let [client @client]
               (do (.close @client)
                   (reset! client nil))
               (println "NULL client. Huh?"))

             (reset! world nil))

            (before
             (println "\tTesting...")
             (swap! world sys/start)
             (reset! client (let [ctx (@world :network-context)
                                  s (mq/socket @ctx mq/req)
                                  address (format 
                                           "tcp://127.0.0.1:%d"
                                           config/auth-port)]
                              (mq/connect s address)
                              s)))

            (after
             (println "\t...Tested")
             (swap! world sys/stop))

            (it "Kill"
                (should= "K" (#'auth/dispatch "dieDieDIE!!")))

            (it "Kill Message"
                (reset! (:done @world) true)
                (let [auth-thread (:authentication-thread world)]
                  (mq/send @client "dieDieDIE!!")
                  (let [response (mq/recv-str @client)]
                    (should= "K" response)
                    (.join @auth-thread 50)
                    (should (not (.isAlive @auth-thread))))))

            (it "Basic Login Sequence"
                ;; This is trickier than I realized.
                ;; I really want to send a multi-part message.
                ;; Something along these lines:
                ;; 1) ib <user-id>
                ;; 2) me-wantz-play <protocol versions client understands>
                ;; 3) <signature>
                ;; That approach seems like it will just make
                ;; life simpler from any angle that comes to mind.
                ;; Dispatcher gets more complicated,but that isn't
                ;; a big deal.
                ;; Meh. Do the Q&A version or block sequence.
                ;; Whatever. They should both work here.
                ;; And I really don't care what they look like over
                ;; the wire...until we get to security.
                (let [login-sequence [["ib" "test"]
                                      ["me-wantz-play" 
                                       ;; Can I make a client dumber than this?
                                       [:youre-kidding nil "login-id" nil]
                                       ;; These represent multiple frames
                                       ;; that would be received over the wire.
                                       ;; And, of course, any decent server
                                       ;; should reject crap like this.
                                       ["Really secure signature"]]]
                      ]
                  ;; So...how does this work?
                  (should false)))

            (it "Painful Login Sequence"
                ;; Q: Why am I even thinking about subjecting myself to this?
                ;; A: Because this is what unit tests are for.
                ;; Just write the obnoxious thing so it's documented.
                (should false))))

(run-specs)
