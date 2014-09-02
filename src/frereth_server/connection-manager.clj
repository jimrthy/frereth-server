(ns frereth-server.connection-manager
  (:require [com.stuartsierra.component :as component]
            [frereth-server.comm :as comm]
            [schema.core :as s])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord User [id :- s/Uuid
                   nick-name :- s/Str
                   socket])

(s/defrecord Directory [control-socket
                        users :- {:s/Uuid User}]
  component/Lifecycle
  (start
   [this]
   ;; TODO: Pull this out of a database instead
   (let [id (java.util.UUID/randomUUID)
         nick-name "admin"
         socket control-socket
         base {:id id
               :nick-name nick-name
               :socket socket}
         initial-user (strict-map->User base)]
     ;; TODO: This is strictly for the sake of getting started.
     (assoc this users {id initial-user})))

  (stop
   [this]
   (assoc this :users {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn existing-user-ids
  "What users does the system know about?
TODO: Add the ability to create new users and look them up."
  [system]
  (keys @(:users system)))

(defn existing-user?
  "Does the system know about this user?"
  [user-id system]
  (contains? (existing-user-ids system) user-id))

(defn add-user
  [credentials system]
  (swap! (:users system) (fn [current]
                           (into current credentials))))

(defn new-directory
  []
  (->Directory))
