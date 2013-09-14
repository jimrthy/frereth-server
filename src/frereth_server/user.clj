(ns frereth-server.user
  (:genclass))

(defn start
  "Generate a new system based on the old, but with user definitions added.
This approach is lame and stupid, but I have to start somewhere.
TODO: Persist users"
  [system]
  (assert (not-any? :users system))
  (into system {:users (atom {})}))

(defn existing-user-ids
  "What users does the system know about?
TODO: Add the ability to create new users and look them up."
  [system]
  (keys @(:users system)))

(defn existing-user?
  "Does the system know about this user?
This approach sucks."
  [user-id system]
  (contains (existing-user-ids system) user-id))

(defn add-user
  [credentials system]
  (swap! (:users system) (fn [current]
                           (into current credentials))))
