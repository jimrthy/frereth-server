(ns com.frereth.server.system
  (:require
   [com.frereth.common.util :as util]
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
  "Mostly pulled out of thin air

TODO: Pretty much every one of these should
be set by environment variables instead"
  []
  (let [default-sock {:port :override-this
                      ;; Must use numeric IP address
                      :address #_"localhost" "127.0.0.1"
                      :protocol "tcp"}]
    ;; nil isn't legal. Should really be sending an empty map
    ;; Q: Does leaving the entry out completely work?
    (comment
      {:action-socket nil
       :auth-socket nil
       :control-socket nil
       :done nil
       :logger nil
       :principal-manager nil})

    {:auth-url (assoc default-sock :port 7843)
     :action-url (assoc default-sock :port 7841)
     ;; Almost definitely want to maximize this thread count
     ;; Although that really depends on the environment.
     ;; It makes sense for a production server.
     ;; For a local one...probably not so much
     :context {:thread-count 2}}))

(defn structure []
  '{:action-socket com.frereth.server.comm/new-action-socket
    :auth-socket com.frereth.server.comm/new-auth-socket
    :action-url com.frereth.server.comm/new-action-url
    :auth-url com.frereth.server.comm/new-auth-url
    :context com.frereth.server.comm/new-context
    :control-socket com.frereth.server.comm/new-control-socket
    :control-url com.frereth.server.comm/new-control-url
    :done com.frereth.server.sentinal/ctor
    :logger com.frereth.server.logging/ctor
    :principal-manager com.frereth.server.connection-manager/new-directory})

(defn dependencies []
  {:action-socket {:context :context
                   :url :action-url}
   :auth-socket {:context :context
                 :url :auth-url}
   :control-socket {:context :context
                    :url :control-url}
   :principal-manager [:control-socket]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn init :- SystemMap
  [overrides]
  (let [description {:structure (structure)
                     :dependencies (dependencies)}
        options (into (defaults) overrides)]
    ;; No logger available yet
    (println "Trying to build" (util/pretty (:structure description))
             "\nWith configuration" (util/pretty options))
    (cpt-dsl/build description options)))
