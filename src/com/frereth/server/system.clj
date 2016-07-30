(ns com.frereth.server.system
  (:require [cljeromq.curve :as curve]
            [clojure.core.async :as async]
            [com.frereth.common.async-zmq :as async-zmq]
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
    {;; Almost definitely want to maximize this thread count
     ;; Although that really depends on the environment.
     ;; It makes sense for a production server.
     ;; For a local one...probably not so much.
     ;; Q: Why not?
     ;; A: Well, context switches come to mind. I'm not
     ;; sure whether I buy that that would be an issue.
     ;; Whichever approach makes the most sense, we have to have at least 1.
     :context {:thread-count (-> (util/core-count) dec (max 1))}
     :socket-description (-> default-sock
                             (assoc-in [:url :port] 7843)
                             (assoc :server-key (curve/z85-decode "QU]/}50vX=t1mrw.{=<g%c@WCMGX^&?K2$@zzAD:")))}))

(defn structure []
  ;; Note that this is overly simplified.
  ;; It would be crazy to mix the auth and action servers in the same process.
  ;; And the idea for a control socket was never a good idea.
  ;; But...
  ;; it seems to make sense as a starting point.
  ;; Q: Would it make life simpler if I split these all into their
  ;; own processes?
  '{:context com.frereth.common.zmq-socket/ctx-ctor
    :done component-dsl.done-manager/ctor
    :event-interface com.frereth.server.auth-socket/ctor-interface
    :logger com.frereth.server.logging/ctor
    :message-dispatcher com.frereth.server.message-dispatcher/ctor
    :plugin-manager com.frereth.server.plugin-manager/ctor
    :socket-description com.frereth.server.zmq-socket/ctor
    })

(defn dependencies []
  {:event-interface [:socket-description]
   :message-dispatcher [:event-interface :plugin-manager]
   :plugin-manager [:done]
   :socket-description [:context]})

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
