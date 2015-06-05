(ns frereth.server.system
  "Honestly, this should probably be considered obsolete."
  (:require
   [com.stuartsierra.component :as component]
   [component-dsl.system :as cpt-dsl]
   [schema.core :as s]
   [taoensso.timbre :as log])
  (import [com.stuartsierra.component SystemMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn defaults
  []
  {:ports {:action 7843
           :auth 7841}
   ;; Almost definitely want to maximize this thread count
   ;; Although that really depends on the environment.
   ;; It makes sense for a production server.
   ;; For a local one...probably not so much
   :zmq-thread-count 1})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn init :- SystemMap
  [overrides]
  (let [description {:structure {:action-socket 'frereth.server.comm/new-action-socket
                                 :auth-socket 'frereth.server.comm/new-auth-socket
                                 :auth-url 'frereth.server.comm/new-auth-url
                                 :context 'frereth.server.comm/new-context
                                 :control-socket 'frereth.server.comm/new-control-socket
                                 :control-url 'frereth.server.comm/new-control-url
                                 :done 'frereth.server.sentinal/ctor
                                 :logger 'frereth.server.logging/new
                                 :principal-manager 'frereth.server.connection-manager/new-directory}
                     :dependencies {:action-socket {:context :context
                                                    :url :action-url}
                                    :auth-socket {:context :context
                                                  :url :auth-url}
                                    :control-socket {:context :context
                                                     :url :control-ur}
                                    :principal-manager [:control-socket]}}
        options (into (defaults) overrides)]
    (cpt-dsl/build description options)))
