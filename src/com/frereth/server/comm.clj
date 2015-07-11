(ns com.frereth.server.comm
  "All of my socket handling

TODO: These should be split up into a sub-namespace
instead of jammed all together"
  (:require [cljeromq.core :as mq]
            [com.frereth.common.communication :as comm]
            [com.frereth.common.util :as util]
            [com.frereth.common.zmq-socket :as zmq-sock]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]
           [com.frereth.common.zmq_socket ContextWrapper SocketDescription]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema
;;; Big swatch of this has already moved to frereth.common
;;; TODO: Get with the program and eliminate the duplication

(comment
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
     (assoc this :context nil))))

(comment
  (defmulti socket-type
    ;; Q: Why didn't I just use class here?
    #(class %)))

(comment
  ;;; TODO: These next 3 records needs to collapse into one.
;;; Or two, at the most. Since the ControlSocket should
;;; probably be inproc.
  (s/defrecord ActionSocket [context :- ContextWrapper
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

  (s/defrecord AuthSocket [context :- ContextWrapper
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

  (s/defrecord ControlSocket [context :- ContextWrapper
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
     (assoc this :socket nil))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(comment
  (defmethod socket-type ActionSocket
    [_]
    :router)

  (defmethod socket-type AuthSocket
    [_]
    :router)

  (defmethod socket-type ControlSocket
    [_]
    :router))

(s/defn build-global-url :- comm/URI
  "Q: Why did I name it this way?"
  [{:keys [port protocol address]
    :as config
    :or {protocol "tcp"
         ;; Q: Shouldn't this address be *?
         address "localhost"}} :- {:ports {s/Keyword s/Int}
                                   s/Any s/Any}]
  (log/debug "Trying to set up a URL based on" (util/pretty config))
  {:protocol protocol
   :address address
   :port port})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn new-context :- ContextWrapper
  [{:keys [thread-count] :as cfg
    :or {thread-count 1}}]
  (log/warn "Obsolete. Just call the common library directly instead")
  (zmq-sock/ctx-ctor cfg))

(s/defn new-socket :- SocketDescription
  [url :- mq/zmq-url]
  ;; Honestly, this is also obsolete
  (log/warn "Getting ready to construct a SocketDescription based on:\n" url)
  (zmq-sock/ctor {:url url
                  :direction :bind
                  :sock-type :router}))
