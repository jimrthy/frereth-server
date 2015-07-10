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
    {:action-socket nil
     :auth-socket nil
     :auth-url (assoc default-sock :port 7843)
     :action-url (assoc default-sock :port 7841)
     ;; Almost definitely want to maximize this thread count
     ;; Although that really depends on the environment.
     ;; It makes sense for a production server.
     ;; For a local one...probably not so much
     :context {:thread-count 2}
     :control-socket nil
     :done nil
     :logger nil
     :principal-manager nil}))

(defn structure []
  {:action-socket 'frereth.server.comm/new-action-socket
   :auth-socket 'frereth.server.comm/new-auth-socket
   :action-url 'frereth.server.comm/new-action-url
   :auth-url 'frereth.server.comm/new-auth-url
   :context 'frereth.server.comm/new-context
   :control-socket 'frereth.server.comm/new-control-socket
   :control-url 'frereth.server.comm/new-control-url
   :done 'frereth.server.sentinal/ctor
   :logger 'frereth.server.logging/ctor
   :principal-manager 'frereth.server.connection-manager/new-directory})

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
