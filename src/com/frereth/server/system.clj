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
  (let [default-sock {:url {:port :override-this
                            ;; Must use numeric IP address
                            :address "127.0.0.1"
                            :protocol :tcp}
                      :direction :bind
                      :sock-type :router}]
    {:action-socket (assoc-in default-sock [:url :port] 7841)
     :auth-socket (assoc default-sock [:url :port] 7843)
     ;; Almost definitely want to maximize this thread count
     ;; Although that really depends on the environment.
     ;; It makes sense for a production server.
     ;; For a local one...probably not so much
     :context {:thread-count (-> (util/core-count) dec (max 1))}
     ;; Yes, this is pretty twisty
     ;; TODO: Why isn't there a dissoc-in?
     :control-socket (let [default default-sock
                           url (:url default)]
                       (assoc default :url (dissoc :port)))}))

(defn structure []
  '{:action-socket com.frereth.common.zmq-socket/ctor
    :auth-socket com.frereth.common.zmq-socket/ctor
    :context com.frereth.common.zmq-socket/ctx-ctor
    :control-socket com.frereth.common.zmq-socket/ctor
    :done com.frereth.server.sentinal/ctor
    :logger com.frereth.server.logging/ctor
    ;; For auth
    :principal-manager com.frereth.server.connection-manager/new-directory})

(defn dependencies []
  {:action-socket {:ctx :context}
   :auth-socket {:ctx :context}
   :control-socket {:ctx :context}
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
