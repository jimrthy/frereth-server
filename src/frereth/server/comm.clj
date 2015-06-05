(ns frereth.server.comm
  "All of my socket handling

TODO: These should be split up into a sub-namespace
instead of jammed all together"
  (:require [cljeromq.core :as mq]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

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

(s/defrecord URI [protocol :- s/Str
                  address :- s/Str
                  port :- s/Int]
  ;; TODO: This could really just as easily
  ;; be a plain dictionary.
  ;; More importantly, it conflicts with native
  ;; Java's URI. This will be confusing
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(declare build-url)
(defmulti socket-type
  #(class %))

(s/defrecord ActionSocket [context :- mq/Context
                           socket :- mq/Socket
                           url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [type (socket-type this)
         sock (mq/socket! context type)]
     ;; TODO: Make this another option. It's really only
     ;; for debugging.
     (mq/set-router-mandatory! sock 1)
     (mq/bind! sock (build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (mq/set-linger! socket 0)
   (mq/unbind! socket (build-url url))
   (mq/close! socket)
   (assoc this :socket nil)))

(s/defrecord AuthSocket [context :- ZmqContext
                         socket :- mq/Socket
                         url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [type (socket-type this)
         sock (mq/socket! (:context context) type)]
     ;; TODO: Make this another option. It's really only
     ;; for debugging.
     (mq/set-router-mandatory! sock true)
     (let [url (build-url url)]
       (log/debug "Trying to bind" sock "to" url)
       (mq/bind! sock url))
     (assoc this :socket sock)))

  (stop
   [this]
   (mq/set-linger! socket 0)
   (mq/unbind! socket (build-url url))
   (mq/close! socket)
   (assoc this :socket nil)))

(s/defrecord ControlSocket [context :- ZmqContext
                            socket :- mq/Socket
                            url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [sock (mq/socket! (:context context) (socket-type this))]
     (mq/bind! sock (build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (mq/set-linger! socket 0)
   (mq/unbind! socket (build-url url))
   (mq/close! socket)
   (assoc this :socket nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(s/defn build-url :- s/Str
  [url :- URI]
  (let [port (:port url)]
    (str (:protocol url) "://"
         (:address url)
         ;; port is meaningless for inproc
         (when port
           (str ":" port)))))

(defmethod socket-type AuthSocket
  [_]
  :router)

(defmethod socket-type ActionSocket
  [_]
  :router)

(s/defn build-global-url :- URI
  [config :- {:ports {s/Keyword s/Int}
              s/Any s/Any}
   port-key :- s/Any]
  (let [protocol "tcp"
        address "*"
        port (-> config :ports port-key)]
    (strict-map->URI {:protocol protocol
                      :address address
                      :port port})))

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

(s/defn new-action-url :- URI
  [config]
  (build-global-url config :action))

(s/defn new-auth-socket :- AuthSocket
  [_]
  (map->AuthSocket {}))

(s/defn new-auth-url :- s/Str
  [config]
  (build-global-url config :auth))

(s/defn new-control-socket :- ControlSocket
  [_]
  (map->ControlSocket {}))

(s/defn new-control-url :- URI
  [_]
  (strict-map->URI {:protocol "inproc"
                    :address "local"
                    :port nil}))