(ns com.frereth.server.comm
  "All of my socket handling

TODO: These should be split up into a sub-namespace
instead of jammed all together"
  (:require [cljeromq.core :as mq]
            [com.frereth.common.communication :as comm]
            [com.frereth.common.util :as util]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema
;;; Big swatch of this has already moved to frereth.common
;;; TODO: Get with the program and eliminate the duplication

(s/defrecord ZmqContext [context :- mq/Context
                         thread-count :- s/Int]
  component/Lifecycle
  (start
   [this]
   (log/debug "Starting a 0mq Context with" thread-count "threads")
   (let [ctx (mq/context thread-count)]
     (assoc this :context ctx)))
  (stop
   [this]
   (when context
     (mq/terminate! context))
   (assoc this :context nil)))

(defmulti socket-type
  ;; Q: Why didn't I just use class here?
  #(class %))

;;; TODO: These next 3 records needs to collapse into one.
;;; Or two, at the most. Since the ControlSocket should
;;; probably be inproc.
(s/defrecord ActionSocket [context :- ZmqContext
                           socket :- mq/Socket
                           url :- comm/URI]
  component/Lifecycle
  (start
   [this]
   (let [type (socket-type this)
         sock (mq/socket! (:context context) type)]
     ;; TODO: Make this another option. It's really only
     ;; for debugging.
     (mq/set-router-mandatory! sock true)
     (mq/bind! sock (comm/build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (when socket
     (mq/set-linger! socket 0)
     (mq/unbind! socket (comm/build-url url))
     (mq/close! socket))
   (assoc this :socket nil)))

(s/defrecord AuthSocket [context :- ZmqContext
                         socket :- mq/Socket
                         url :- comm/URI]
  component/Lifecycle
  (start
   [this]
   (let [type (socket-type this)
         sock (mq/socket! (:context context) type)]
     ;; TODO: Make this another option. It's really only
     ;; for debugging.
     (mq/set-router-mandatory! sock true)
     (let [address (comm/build-url url)]
       (log/debug "Trying to bind" sock "to" address "based on" url)
       (try
         (mq/bind! sock address)
         (catch ExceptionInfo ex
           (comment (log/warn "Yes, I'm handling the failed binding"))
           (raise {:problem ex
                   :details (.getData ex)
                   :url-attempted url
                   :url-representation address}))))
     (assoc this :socket sock)))

  (stop
   [this]
   (when socket
     (mq/set-linger! socket 0)
     (mq/unbind! socket (comm/build-url url))
     (mq/close! socket))
   (assoc this :socket nil)))

(s/defrecord ControlSocket [context :- ZmqContext
                            socket :- mq/Socket
                            url :- comm/URI]
  component/Lifecycle
  (start
   [this]
   (let [ctx (:context context)
         sock-type (socket-type this)
         _ (log/debug "Creating a" sock-type "socket for" ctx)
         sock (mq/socket! ctx sock-type)]
     (mq/bind! sock (comm/build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (when socket
     (mq/set-linger! socket 0)
     ;; Can't unbind an inproc socket
     (comment
       (let [addr (comm/build-url url)]
         (log/debug "Trying to unbind the Control Socket at" addr)
         (mq/unbind! socket addr)))
     (mq/close! socket))
   (assoc this :socket nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(defmethod socket-type ActionSocket
  [_]
  :router)

(defmethod socket-type AuthSocket
  [_]
  :router)

(defmethod socket-type ControlSocket
  [_]
  :router)

(s/defn build-global-url :- comm/URI
  "Q: Why did I name it this way?"
  [{:keys [port protocol address]
    :as config
    :or {protocol "tcp"
         address "localhost"}} :- {:ports {s/Keyword s/Int}
                                   s/Any s/Any}]
  (log/debug "Trying to set up a URL based on" (util/pretty config))
  {:protocol protocol
   :address address
   :port port})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn new-context :- ZmqContext
  [{:keys [thread-count] :as cfg
    :or {thread-count 1}}]
  (map->ZmqContext {:context nil
                    :thread-count thread-count}))

(s/defn new-action-socket :- ActionSocket
  [_]
  (map->ActionSocket {}))

(s/defn new-action-url :- comm/URI
  [config]
  (build-global-url config))

(s/defn new-auth-socket :- AuthSocket
  [_]
  (log/warn "FIXME: Move to auth_socket")
  (map->AuthSocket {}))

(s/defn new-auth-url :- s/Str
  [config]
  (log/debug "Setting up the Auth Socket URL based on" config)
  (build-global-url config))

(s/defn new-control-socket :- ControlSocket
  [_]
  (map->ControlSocket {}))

(s/defn new-control-url :- comm/URI
  [_]
  {:protocol "inproc"
   :address "local"
   :port nil})
