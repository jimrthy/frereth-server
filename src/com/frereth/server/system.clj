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
                :url {:cljeromq.common/port 7841
                      ;; Must use numeric IP address
                      :cljeromq.common/zmq-address "127.0.0.1"
                      :cljeromq.common/zmq-protocol :tcp}}})

(defn structure []
  ;; Note that this is overly simplified.
  ;; It would be crazy to mix the auth and action servers in the same process.
  ;; Q: Would it?
  ;; A: It would for an enterprise architecture.
  ;; Q: Is it less crazy for a game server that some kid's going to be running
  ;; with no sysadmin skills?
  ;; And the idea for a control socket was never a good idea.
  ;; But...
  ;; it seems to make sense as a starting point.
  ;; Q: Would it make life simpler if I split these all into their
  ;; own processes?
  '{:context com.frereth.common.zmq-socket/ctx-ctor

    :event-loop com.frereth.common.system/build-event-loop-description

    ;; Q: Does either of these still make sense?
    ;; A: This gives (main) something to wait on, if you want to run as a CLI app
    ;; :done component-dsl.done-manager/ctor
    ;; This is probably pretty important, if only to get logging configured
    ;; :logger com.frereth.server.logging/ctor
    ;; TODO: Restore those when I'm done debugging this
    ;; For auth
    :principal-manager com.frereth.server.connection-manager/new-directory})

(defn dependencies []
  ;; One thing that might make this more interesting than my existing cpt-dsl tests:
  ;; I have two direct top-level components, the context and the principal-manager.
  ;; Then nested event-loop component depends on one and is depended upon by the other.
  ;; TODO: Add this kind of test to that
  {:event-loop [:context]
   :principal-manager {:control-socket :event-loop}})

(defn initialize-description []
  (let [struct (structure)]
    #:component-dsl.system{:structure struct
                           :dependencies (dependencies)}))

(comment
  ;; This isn't working predictably.
  ;; Q: Why not?
  ;; A: Well, this part seems fine
  (comment (cpt-dsl/split-nested-definers (structure)))
  ;; As does this
  (comment (cpt-dsl/build (initialize-description) (defaults)))
  ;; At first glance, so does this.
  ;; Except that it looks like it isn't finding the nested component constructors
  (comment (cpt-dsl/pre-process (assoc (initialize-description)
                                       :component-dsl.system/options (defaults)))
           )
  (comment (let [true-tops (->> (initialize-description)
                                :component-dsl.system/structure
                                (filter (comp symbol? second))
                                (into {}))]
             true-tops))
  (comment (let [nested-ctord (->> (initialize-description)
                                   :component-dsl.system/structure
                                   (filter (comp symbol? second))
                                   (into {})
                                   cpt-dsl/split-nested-definers
                                   :component-dsl.system/definers
                                   (cpt-dsl/call-nested-ctors (defaults)))]
             nested-ctord))
  (let [de-nested (->> (initialize-description)
                       :component-dsl.system/structure
                       (filter (comp symbol? second))
                       (into {})
                       cpt-dsl/split-nested-definers
                       :component-dsl.system/definers
                       (cpt-dsl/call-nested-ctors (defaults))
                       (reduce cpt-dsl/de-nest-component-ctors
                               #:component-dsl.system{:structure tops
                                                      :dependencies dependencies}))
        ]
             nested-ctord))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef init
        ;; TODO: Spec the overrides better
        :args (s/cat :overrides any?)
        :ret :com.frereth.common.schema/system-map)
(defn init
  [overrides]
  (let [description (initialize-description)
        options (into (defaults) overrides)]
    ;; No logger available yet
    (println "Trying to build" (util/pretty struct)
             "\nWith configuration" (util/pretty options))
    (cpt-dsl/build description options)))
