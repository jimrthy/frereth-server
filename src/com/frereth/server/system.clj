(ns com.frereth.server.system
  (:require [cljeromq.curve :as curve]
            [clojure.core.async :as async]
            [clojure.spec :as s]
            [com.frereth.common.async-zmq :as async-zmq]
            [com.frereth.common.schema]
            [com.frereth.common.util :as util]
            [com.stuartsierra.component :as component]
            [component-dsl.system :as cpt-dsl]
            [taoensso.timbre :as log])
  (:import [com.stuartsierra.component SystemMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn defaults
  "Mostly pulled out of thin air

TODO: Pretty much every one of these should
be set by environment variables instead.

Well, the ones that shouldn't just go away completely"
  []
  {:event-loop {:direction :bind
                :event-loop-name "Server"
                ;; This is just a magic key I generated once and hard-coded into
                ;; the client.
                ;; TODO: Need to switch to real keys and certs!
                :server-key (curve/z85-decode "8C))+8}}<P[p8%c<j)bpj2aJO5:VCU>DvB@@#LqW")
                :socket-type :router
                ;; Almost definitely want to maximize this thread count
                ;; Although that really depends on the environment.
                ;; It makes sense for a production server.
                ;; For a local one...probably not so much.
                ;; Q: Why not?
                ;; A: Well, context switches come to mind. I'm not
                ;; sure whether I buy that that would be an issue.
                ;; Whichever approach makes the most sense, we have to have at least 1.
                :thread-count (-> (util/core-count) dec (max 1))
                :url {:port 7841
                      ;; Must use numeric IP address
                      :address "127.0.0.1"
                      :protocol :tcp}}})

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

    :event-loop com.frereth.common.system/build-event-loop-description

    :logger com.frereth.server.logging/ctor
    ;; For auth
    :principal-manager com.frereth.server.connection-manager/new-directory})

(defn dependencies []
  {:event-loop [:context]
   :principal-manager {:control-socket :event-loop}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef init
        ;; TODO: Spec the overrides better
        :args (s/cat :overrides any?)
        :ret :com.frereth.common.schema/system-map)
(defn init
  [overrides]
  (let [struct (structure)
        description #:component-dsl.system{:structure struct
                                           :dependencies (dependencies)}
        options (into (defaults) overrides)]
    ;; No logger available yet
    (println "Trying to build" (util/pretty struct)
             "\nWith configuration" (util/pretty options))
    (cpt-dsl/build description options)))
