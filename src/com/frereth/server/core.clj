(ns com.frereth.server.core
  (:require
   [com.stuartsierra.component :as component]
   [com.frereth.server.system :as sys]
   [taoensso.timbre :as log :as log]))

(defn -main
  [& args]
  ;; The basic gist:
  (let [;; Basic system first
        dead-system (sys/init)
        system (component/start dead-system)

        ;; FIXME: Should load up some sort of info about which and how many
        ;; thread are actually running now.
        ]
    (log/trace "Have " (Thread/activeCount) " threads")
    ;; TODO: system should have some sort of promise that I can deref
    ;; Or maybe I don't care...as long as there are event loop threads
    ;; running, it isn't going to exit.
    ))
