(ns frereth.server.comm
  (require [cljeromq.core :as mq]
           [com.stuartsierra.component :as component]
           [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord ZmqContext [context thread-count :- s/Int]
  component/Lifecycle
  (start
   [this]
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
  component/Lifecycle
  (start [this] this)
  (stop [this] this))
(declare build-url)

(defmulti socket-type
  class)

(s/defrecord ActionSocket [context
                           socket
                           url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [sock (mq/socket! context (socket-type this))]
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

(s/defrecord AuthSocket [context
                         socket
                         url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [sock (mq/socket! context (socket-type this))]
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

(s/defrecord ControlSocket [context
                            socket
                            url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [sock (mq/socket! context (socket-type this))]
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
  []
  :router)

(defmethod socket-type ActionSocket
  []
  :router)

(defn build-global-url
  [config port-key]
  (let [protocol "tcp"
        address "*"
        port (-> config :ports port-key)]
    (strict-map->URI {:protocol protocol
                      :address address
                      :port port})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-context
  [thread-count]
  (map->ZmqContext {:context nil
                    :thread-count thread-count}))

(defn new-action-socket
  []
  (map->ActionSocket {}))

(defn new-action-url
  [config]
  (build-global-url config :action))

(defn new-auth-socket
  []
  (map->AuthSocket {}))

(defn new-auth-url
  [config]
  (build-global-url config :auth))

(defn new-control-socket
  []
  (map->ControlSocket {}))

(defn new-control-url
  []
  (strict-map->URI {:protocol "inproc"
                    :address "local"
                    :port nil}))
