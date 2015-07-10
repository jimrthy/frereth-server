(ns com.frereth.server.test-utils
  "Utilities to cut back on boiler-plate

TODO: Rename this so it doesn't look like it's
tests for a utils namespace."
  (:require [com.stuartsierra.component :as component]
            [com.frereth.server.system :as sys]))

(defn wrap [test]
  "Basic setup/teardown

I'm not sure how I feel about this, but it
seems inappropriate"
  (let [stopped-world (sys/init)
        world (component/start stopped-world)]
    (try
      (test world)
      (finally
        (component/stop world)))))
