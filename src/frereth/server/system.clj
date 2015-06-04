(ns frereth-server.system
  "Honestly, this should probably be considered obsolete."
  (:require
   [component-dsl.system :as cpt-dsl]
   #_[com.stuartsierra.component :as component]
   #_[frereth-server.auth-socket :as auth]
   #_[frereth-server.comm :as comm]
   #_[frereth-server.logging :as logging]
   #_[frereth-server.connection-manager :as connection-manager]
   [taoensso.timbre :as log]))

(defn defaults
  []
  {:ports {:action 7843
           :auth 7841}
   ;; Almost definitely want to maximize this thread count
   ;; Although that really depends on the environment.
   ;; It makes sense for a production server.
   ;; For a local one...probably not so much
   :zmq-thread-count 1})

(comment
  (defn init
  "Sets up the system that encapsulates the Components"
  [overrides]
  (let [config (into (defaults) overrides)]
    (-> (component/system-map
         :action-socket (comm/new-action-socket)
         :auth-socket (comm/new-auth-socket)
         :auth-url (comm/new-auth-url config)
         :context (comm/new-context config)
         :control-socket (comm/new-control-socket)
         :control-url (comm/new-control-url)
         :done (promise)
         :logger (logging/new)
         :principal-manager (connection-manager/new-directory))
        (component/system-using
         {:action-socket {:context :context
                          :url :action-url}
          :auth-socket {:context :context
                        :url :auth-url}
          :control-socket {:context :context
                           :url :control-ur}
          :principal-manager [:control-socket]})))))

(defn init
  [overrides]
  (let [description {:structure {:action-socket 'frereth-server.comm/new-action-socket
                                 :auth-socket 'frereth-server.comm/new-auth-socket
                                 :auth-url 'frereth-server.comm/new-auth-url
                                 :context 'frereth-server.comm/new-context
                                 :control-socket 'frereth-server.comm/new-control-socket
                                 :control-url 'frereth-server.comm/new-control-url
                                 :done promise
                                 :logger 'frereth-server.logging/new
                                 :principal-manager 'frereth-server.connection-manager/new-directory}
                     :dependencies {:action-socket {:context :context
                                                    :url :action-url}
                                    :auth-socket {:context :context
                                                  :url :auth-url}
                                    :control-socket {:context :context
                                                     :url :control-ur}
                                    :principal-manager [:control-socket]}}
        options (into defaults overrides)]
    (cpt-dsl/build description options)))
