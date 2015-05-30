(ns frereth-server.system
  (:require
   ;;[cljeromq.core :as mq]
   [com.stuartsierra.component :as component]
   [frereth-server.auth-socket :as auth]
   [frereth-server.comm :as comm]
   [frereth-server.logging :as logging]
   [frereth-server.connection-manager :as connection-manager]
   [taoensso.timbre :as log]   
   [zeromq.zmq :as mq])
  #_(:gen-class))

(defn defaults
  []
  {:ports {:action 7843
           :auth 7841}
   ;; TODO: Almost definitely want to maximize this thread count
   :zmq-thread-count 1})

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
          :principal-manager [:control-socket]}))))
