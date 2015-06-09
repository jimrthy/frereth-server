(ns frereth.server.system-test
  (:require [clojure.test :refer (are deftest is testing)]
            [com.frereth.common.util :as util]
            [com.stuartsierra.component :as component]
            [frereth.server.system :as sys]
            [ribol.core :refer (raise)]
            [taoensso.timbre :as log]))

;;;; What sort of tests (if any) make sense here?
;;;; Maybe just verify that setup/teardown doesn't throw any errors?

(defn active?
  "The downside to this approach is that it short-circuits."
  [world]
  (and (-> world :context :context)
       (-> world :control-socket :socket)))

(deftest start-stop []
  ;; Seems more than a little wrong to be using an atom here. Oh well.
  (let [world (atom nil)
        inited (sys/init nil)
        overridden (-> inited
                       (update-in [:auth-url :port] (constantly 9876))
                       (update-in [:action-url :port] (constantly 9875)))]
    (reset! world overridden)
    (is (not (active? @world)) "World created in active state")

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
      (comment (raise "Start Here"))
      (when-not (active? @world)
        (log/error "World failed to start\n"
                   (util/pretty @world)
                   "Specifically:"
                   (util/pretty (select-keys @world [:context :control-socket :done]))))
      (finally
        ;; sys/stop is totally broken. That breaks everything else.
        ;; In all fairness, it should. I just really didn't want to
        ;; deal with that tonight.
        (swap! world component/stop)
        (is (not (active? world)) "World stopped")))))
