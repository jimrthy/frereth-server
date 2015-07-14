(ns com.frereth.server.system
  (:require
   [clojure.core.async :as async]
   [com.frereth.common.async-zmq :as async-zmq]
   [com.frereth.common.util :as util]
   [com.stuartsierra.component :as component]
   [component-dsl.system :as cpt-dsl]
   [schema.core :as s]
   [taoensso.timbre :as log])
  (import [com.frereth.common.async_zmq EventPair EventPairInterface]
          [com.stuartsierra.component SystemMap]))

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
    {;; Almost definitely want to maximize this thread count
     ;; Although that really depends on the environment.
     ;; It makes sense for a production server.
     ;; For a local one...probably not so much.
     ;; Q: Why not?
     ;; A: Well, context switches come to mind. I'm not
     ;; sure whether I buy that that would be an issue.
     :context {:thread-count (-> (util/core-count) dec (max 1))}

     :action-socket (assoc-in default-sock [:url :port] 7841)
     :action-loop {:_name "Action!"}
     :action-loop-interface {:in-chan (async/chan)
                             :external-reader (fn [_]
                                                (throw (ex-info "Action Loop Reader: Not Implemented" {})))
                             :external-writer (fn [_ _2]
                                                (throw (ex-info "Action Loop Writer: Not Implemented" {})))}

     :auth-socket (assoc-in default-sock [:url :port] 7843)
     :auth-loop {:_name "authcz"}

     :control-socket (assoc default-sock
                            :url {:protocol :inproc
                                  :address (name (gensym))})
     :control-loop {:_name "control"}
     :control-loop-interface {:in-chan (async/chan)
                              :external-reader (fn [_]
                                                 (throw (ex-info "Control Loop Reader: Not Implemented" {})))
                              :external-writer (fn [_ _2]
                                                 (throw (ex-info "Control Loop Writer: Not Implemented" {})))}}))

(defn structure []
  '{:action-loop com.frereth.common.async-zmq/ctor
    :action-loop-interface com.frereth.common.async-zmq/ctor-interface
    :action-socket com.frereth.common.zmq-socket/ctor

    :auth-loop com.frereth.common.async-zmq/ctor
    :auth-loop-interface com.frereth.server.auth-socket/ctor-interface
    :auth-socket com.frereth.common.zmq-socket/ctor
    :auth-handler com.frereth.server.comms.registrar/ctor

    :context com.frereth.common.zmq-socket/ctx-ctor

    :control-loop com.frereth.common.async-zmq/ctor
    :control-loop-interface com.frereth.common.async-zmq/ctor-interface
    :control-socket com.frereth.common.zmq-socket/ctor

    :done com.frereth.server.sentinal/ctor
    :logger com.frereth.server.logging/ctor
    ;; For auth
    :principal-manager com.frereth.server.connection-manager/new-directory})

(defn dependencies []
  {:action-loop {:interface :action-loop-interface}
   :action-loop-interface {:ex-sock :action-socket}
   :action-socket {:ctx :context}

   :auth-handler {:event-loop :auth-loop}
   :auth-loop {:ex-sock :auth-socket, :interface :auth-loop-interface}
   :auth-loop-interface {:ex-sock :auth-socket}
   :auth-socket {:ctx :context}

   :control-loop {:ex-sock :action-socket, :interface :action-loop-interface}
   :control-loop-interface {:ex-sock :control-socket}
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
