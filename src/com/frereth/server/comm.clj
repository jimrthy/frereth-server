(ns com.frereth.server.comm
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

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

(comment
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
                    :sock-type :router})))
