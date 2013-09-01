(ns frereth-server.system-test
  (:use midje.sweet)
  (:require [frereth-server.system :as sys]))

;;;; What sort of tests (if any) make sense here?
;;;; Maybe just verify that setup/teardown doesn't throw any errors?

(defn active?
  "The downside to this approach is that it short-circuits."
  [world]
  (and
   @(:network-context world)
   @(:master-connection world)
   (not @(:done world))))

(facts "How does this sequence actually work?"
       ;; Seems more than a little wrong to be using an atom here. Oh well.
       (let [world (atom nil)]
         (reset! world (sys/init))
         ;; Can I use midje this way?
         (fact "World created"
               (active? world)
               => false?)

         (swap! world sys/start)
         (try
           (fact "World started" (active? world) => true?)
           (finally
             ;; sys/stop is totally broken. That breaks everything else.
             ;; In all fairness, it should. I just really didn't want to
             ;; deal with that tonight.
             (swap! world sys/stop)
             (fact "World stopped" (active? world) => false?)))))
