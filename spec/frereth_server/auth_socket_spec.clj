(ns frereth-server.auth-socket-spec
  (:require [frereth-server.auth-socket :as auth]
            [frereth-server.config :as config]
            [frereth-server.system :as sys]
            [zguide.zhelpers :as mq])
  (:use [speclj.core :as spec])
  (:gen-class))

(let [world (atom nil)
      client-atom (atom nil)]

  (describe "Check auth messaging"
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
             (reset! client-atom (let [ctx (@world :network-context)
                                       s (mq/socket @ctx mq/req)
                                       address (format 
                                                "tcp://127.0.0.1:%d"
                                                config/auth-port)]
                                   (mq/connect s address)
                                   s)))

            (after
             (println "\t...Tested")
             (.close @client-atom)
             (reset! client-atom nil)

             (swap! world sys/stop))

            (it "Kill"
                (should= "K" (#'auth/dispatch "dieDieDIE!!")))

            (it "Kill Message"
                (reset! (:done @world) true)
                (let [auth-thread (:authentication-thread world)]
                  (mq/send @client-atom "dieDieDIE!!")
                  (let [response (mq/recv-str @client)]
                    (should= "K" response)
                    (.join @auth-thread 50)
                    (should (not (.isAlive @auth-thread))))))

            (it "Painful Login Sequence"
                ;; Q: Why am I even thinking about subjecting myself to this?
                ;; A: Because this is what unit tests are for.
                ;; Just write the obnoxious thing so it's documented.

                ;; Brain-dead client. Don't care about the response
                ;; at all.
                ;; Server needs to be robust enough to handle clients
                ;; this evil. And worse.
                ;; Skip reading the response...this is really a regression
                ;; test case. Should test the normal flow control first.

                ;; Basic authorization exchange
                (mq/send client 'hai)
                (comment (let [resp (mq/recv-obj client)]
                           (should (= resp 'ohai))))

                (mq/send client ['me-speekz nil])
                (comment (let [resp (mq/recv-obj client)]
                           (should (= 'lolz resp))))
                
                (mq/send client '(ib test))
                (comment (let [resp (mq/recv-obj client)]
                           (should (= resp 'prove-it))))

                (mq/send client "Really secure signature")

                ;; This is a different layer
                (comment (let [resp (mq/recv-obj client)]
                           (should (= resp 'wachu-wants?))))

                (mq/send client 'me-wantz-play)

                ;; Do I really want to read here?
                ;; Or just run obliviously to verify that no exceptions
                ;; get thrown?
                ;; The latter option seems like a completely useless test.
                (let [response (mq/recv-all client)
                        (should (= response "What?"))]))

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
                ;;
                ;; c.f. http://rfc.zeromq.org/spec:27
                ;; (the 0mq Authentication Protocol)
                (let [login-sequence ["hai"
                                      ;; Q: Can I make a client dumber than this?
                                      ;; A: I really shouldn't challenge
                                      ;; myself that way.
                                      ["me-speekz" 
                                       [:youre-kidding nil "login-id" nil]] 
                                      ["ib" "test"]
                                      "Really secure signature"
                                      "me-wantz-play"]
                      client @client-atom]
                  ;; client is now an mq/req socket, that should be
                  ;; connected to the server we're testing
                  (mq/send client login-sequence 0)
                  (let [response (mq/recv client)
                        (should (= response "What?"))])))))

(run-specs)
