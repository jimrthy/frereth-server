(ns frereth-server.system
  (:use expectations)
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

;; Seems more than a little wrong to be using an atom here. Oh well.
(let [world (atom nil)]
  (reset! world (sys/init))
  (expect false? (active? world))

  (swap! world sys/start)
  (try
    (expect true? (active? world))
    (finally
      ;; sys/stop is totally broken. That breaks everything else.
      ;; In all fairness, it should. I just really didn't want to
      ;; deal with that tonight.
      (swap! world sys/stop)
      (expect false? (active? world)))))
