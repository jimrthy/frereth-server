(ns dev
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.frereth.common.util :as util]
            [com.frereth.server.system :as system]
            [com.stuartsierra.component :as component]
            [schema.core :as s]))

(def system nil)
;; Can't do this here.
;; https://groups.google.com/forum/#!topic/clojure/S__jYV_g0GE
(comment (set! *print-length* 50))

(defn init
  "Constructs the current development system"
  []
  (alter-var-root #'system
                  (constantly (system/init {}))))

(defn start
  "Starts the current development system"
  []
  (alter-var-root #'system component/start))

(defn stop
  "Shuts down and destroys the current development system"
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the curent development system and starts it running"
  []
  (init)
  (start))

(defn reset
  "Heart of development workflow.
1) Stops current application instance
2) Reloads any source files that have changed
3) Creates and starts a new application instance"
  []
  (stop)
  (refresh :after 'dev/go))
