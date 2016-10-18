(ns com.frereth.server.connection-manager
  "Poorly chosen name. principal-wrangler seems like it would have been more appropriate.

This namespace is really a prototype version of managing users.
Which really doesn't belong in here at all.

It might make sense to have a shiro/friend layer as part of a demo/proof of concept.
And it should tie in with a database.

Those are really more tiers, in a real system.

Then again, I still need to handle the basics for plain ol' humble localhost."
  (:require [clojure.spec :as s]
            [com.frereth.common.async-zmq]
            [com.stuartsierra.component :as component]
            [com.frereth.common.schema :as fr-skm]
            [hara.event :refer (raise)])
  (:import [clojure.lang IDeref]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::id uuid?)
(s/def ::nick-name string?)
(s/def ::socket :com.frereth.common.async-zmq/event-pair)
(s/def ::user (s/keys :req [::id ::nick-name ::socket]))

(s/def ::user-directory (s/map-of uuid? ::user))

(s/def ::users (s/and #(instance? IDeref %)
                      #(s/valid? ::user-directory (deref %))))
(s/def ::directory (s/keys :req-un [::control-socket ::users]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Component

(defrecord Directory [control-socket
                      users]
  component/Lifecycle
  (start
   [this]
   ;; TODO: Pull this out of a database instead
   (let [id (java.util.UUID/randomUUID)
         nick-name "admin"
         socket control-socket
         base {::id id
               ::nick-name nick-name
               ::socket socket}
         initial-user base
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
    (when-let [users (:users this)]
      (reset! users {}))
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef existing-user-ids
        :args (s/cat :this ::directory)
        :ret (s/coll-of ::id))
(defn existing-user-ids
  "What users does the system know about?
TODO: Add the ability to create new users and look them up."
  [system]
  (if-let [users-atom (:users system)]
    (keys @users-atom)
    (do
      (raise {:problem "Missing users atom"
            :details system
            :specifics (keys system)}))))

(s/fdef existing-user?
        :args (s/cat :this ::directory
                     :user-id ::id)
        :ret boolean?)
(defn existing-user?
  "Does the system know about this user?"
  [this user-id]
  (contains? (existing-user-ids this) user-id))

(s/fdef add-user
        :args (s/cat :this ::directory
                     :credentials ::user)
        :ret ::directory)
(defn add-user
  [this credentials]
  (swap! (:users this) (fn [current]
                         (assoc current (::id credentials) credentials)))
  this)

(defn new-directory
  [_]
  (map->Directory {}))
