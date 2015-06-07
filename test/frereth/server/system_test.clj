(ns frereth.server.system-test
  (:require [clojure.test :refer (are deftest is testing)]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [frereth.server.system :as sys]))

;;;; What sort of tests (if any) make sense here?
;;;; Maybe just verify that setup/teardown doesn't throw any errors?

(defn active?
  "The downside to this approach is that it short-circuits."
  [world]
  (when-let [net-ctx (:context world)]
    (when-let [master-conn (:control-socket world)]
      (when-let [done (:done world)]
        (and
         @net-ctx
         @master-conn
         (not @done))))))

(deftest start-stop []
  ;; Seems more than a little wrong to be using an atom here. Oh well.
  (let [world (atom nil)]
    (reset! world (sys/init nil))
    ;; Can I use midje this way?
    (is (not (active? world)) "World created in active state")

    ;; N.B. if this throws an exception, all bets are off.
    ;; Very specifically, if one component binds a socket, then
    ;; a later component throws an exception, you'll probably
    ;; have to restart the JVM.
    ;; This just continues the original lesson from the
    ;; Workflow Reloaded blog post: your System start/stop
    ;; must be bullet-proof.
    (swap! world component/start)
    (try
      ;; FIXME: This is failing
      (raise "Start Here")
      (is (active? world) "World started")
      (finally
        ;; sys/stop is totally broken. That breaks everything else.
        ;; In all fairness, it should. I just really didn't want to
        ;; deal with that tonight.
        (swap! world component/stop)
        (is (not (active? world)) "World stopped")))))
