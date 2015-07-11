(ns com.frereth.server.connection-manager
  (:require [com.stuartsierra.component :as component]
            [com.frereth.common.schema :as fr-skm]
            [com.frereth.server.comm :as comm]
            [ribol.core :refer (raise)]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord User
    [id :- s/Uuid
     nick-name :- s/Str
     socket]
  ; Q: Is there a good reason this isn't a hashmap?
  )
(def user
  {:id s/Uuid
   :nick-name s/Str
   :socket s/Any})

(def user-directory {:s/Uuid User})

(s/defrecord Directory [control-socket
                        users :- fr-skm/atom-type]
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
         initial-user #_(strict-map->User base) base
         users (or users (atom {}))]
     ;; This is strictly for the sake of getting started.
     ;; If there's already an admin in users, shouldn't add another
     ;; Actually, the nick-name should probably be the key in the
     ;; first place.
     ;; Then again, this is really just a placeholder until I
     ;; can switch to some sort of sane user management that someone
     ;; else has written.
     ;; TODO: Make this go away
     (swap!  users assoc id initial-user)
     (assoc this :users users)))

  (stop
   [this]
   (assoc this :users {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn existing-user-ids
  "What users does the system know about?
TODO: Add the ability to create new users and look them up."
  [system :- Directory]
  (if-let [users-atom (:users system)]
    (keys @users-atom)
    (do
      (raise {:problem "Missing users atom"
            :details system
            :specifics (keys system)}))))

(s/defn existing-user? :- s/Bool
  "Does the system know about this user?"
  [self :- Directory
   user-id :- s/Uuid]
  (contains? (existing-user-ids self) user-id))

(defn add-user
  [credentials system]
  (swap! (:users system) (fn [current]
                           (into current credentials))))

(defn new-directory
  [_]
  (map->Directory {}))
