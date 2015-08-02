(ns com.frereth.server.system-test
  (:require [clojure.test :refer (are deftest is testing)]
            [com.frereth.common.util :as util]
            [com.frereth.server.system :as sys]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]))

;;;; What sort of tests (if any) make sense here?
;;;; Maybe just verify that setup/teardown doesn't throw any errors?

(defn active?
  "The downside to this approach is that it short-circuits."
  [world]
  (and (-> world :context :ctx)
       (-> world :control-socket :socket)))

(deftest start-stop []
  ;; Seems more than a little wrong to be using an atom here. Oh well.
  (let [world (atom nil)
        inited (sys/init nil)
        ;; Avoid both reserved and ephemeral ports
        ;; At least on most systems.
        auth-port (+ (rand-int 48109) 1025)
        action-port (+ (rand-int 48109) 1025)
        overridden (-> inited
                       (update-in [:auth-socket :url :port] (constantly auth-port))
                       (update-in [:action-socket :url :port] (constantly action-port)))]
    (comment (println "Overridden World:\n" (util/pretty overridden)))
    (reset! world overridden)
    (is (not (active? @world)) "World created in active state")

    ;; N.B. if this throws an exception, all bets are off.
    ;; Very specifically, if one component binds a socket, then
    ;; a later component throws an exception, you'll probably
    ;; have to restart the JVM.
    ;; This just continues the original lesson from the
    ;; Workflow Reloaded blog post: your System start/stop
    ;; must be bullet-proof.
    (try
      (swap! world component/start)
      (try
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
          (is (not (active? world)) "World stopped")))
      (catch ExceptionInfo ex
        (let [data (.getData ex)
              msg (str "Failed to start system:\n"
                       ex
                       "\nDetails:\n"
                       (util/pretty data)
                       "\nThere's a good chance this is related to either port "
                       auth-port " or " action-port
                       "\nHopefully this doesn't cause serious problems, but it probably will")]
          (println msg))
        (is false "TODO: Just use bind-random-port instead")))))
