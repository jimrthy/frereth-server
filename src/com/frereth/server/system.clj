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
  "TODO: Pretty much every one of these should
be set by environment variables instead"
  []
  {;; Almost definitely want to maximize this thread count
   ;; Although that really depends on the environment.
   ;; It makes sense for a production server.
   ;; For a local one...probably not so much.
   ;; Q: Why not?
   ;; A: Well, context switches come to mind. I'm not
   ;; sure whether I buy that that would be an issue.
   ;; Whichever approach makes the most sense, we have to have at least 1.
   :event-loop {:ctx-thread-count (-> (util/core-count) dec (max 1))
                :direction :bind
                :event-loop-name "frereth.server/io"
                ;; FIXME: Don't be so sloppy w/ private keys!
                ;; This approach is obviously only useful for something that's
                ;; a total throw-away first draft.
                ;; I just want to get something that resembles security into that
                ;; first draft
                :server-key (curve/z85-decode "8.-(kXzdU0yfLRlj%6wHvSti0{YhYFYJV#%){Hf2")
                :socket-type :router
                :url {:port 7848
                      ;; Must use numeric IP address
                      :address [127 0 0 1]
                      :protocol :tcp}}})

(defn structure []
  ;; Note that this is overly simplified.
  ;; It would be crazy to mix the auth and action servers in the same process.
  ;; And the idea for a control socket was never a good idea.
  ;; But...
  ;; it seems to make sense as a starting point.
  ;; Q: Would it make life simpler if I split these all into their
  ;; own processes?
  '{:done component-dsl.done-manager/ctor
    :event-loop com.frereth.common.system/build-event-loop
    :logger com.frereth.server.logging/ctor
    :plugin-manager com.frereth.server.plugin-manager/ctor})

(defn dependencies []
  {:plugin-manager [:done
                    :event-loop]})

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
