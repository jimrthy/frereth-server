(ns frereth-server.comm
  (require [com.stuartsierra.component :as component]
           [schema.core :as s]
           [zeromq.zmq :as zmq]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord ZmqContext [context thread-count :- s/Int]
  component/Lifecycle
  (start 
   [this]
   (let [ctx (zmq/context thread-count)]
     (assoc this :context ctx)))
  (stop
   [this]
   (when context
     (zmq/close context))
   (assoc this :context nil)))

(s/defrecord URI [protocol :- s/Str
                  address :- s/Str
                  port :- s/Int]
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
   (let [sock (zmq/socket context (socket-type this))]
     (zmq/bind sock (build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (zmq/set-linger socket 0)
   (zmq/unbind socket (build-url url))
   (zmq/close socket)
   (assoc this :socket nil)))

(s/defrecord AuthSocket [context
                         socket
                         url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [sock (zmq/socket context (socket-type this))]
     (zmq/bind sock (build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (zmq/set-linger socket 0)
   (zmq/unbind socket (build-url url))
   (zmq/close socket)
   (assoc this :socket nil)))

(s/defrecord ControlSocket [context
                            socket
                            url :- URI]
  component/Lifecycle
  (start
   [this]
   (let [sock (zmq/socket context (socket-type this))]
     (zmq/bind sock (build-url url))
     (assoc this :socket sock)))

  (stop
   [this]
   (zmq/set-linger socket 0)
   (zmq/unbind socket (build-url url))
   (zmq/close socket)
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
  (->ActionSocket))

(defn new-action-url
  [config]
  (build-global-url config :action))

(defn new-auth-socket
  []
  (->AuthSocket))

(defn new-auth-url
  [config]
  (build-global-url config :auth))

(defn new-control-socket
  []
  (->ControlSocket))

(defn new-control-url
  []
  (strict-map->URI {:protocol "inproc"
                    :address "local"
                    :port nil}))
