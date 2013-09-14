(ns frereth-server.user
  (:gen-class))

(defn start
  [users]
  ;; TODO: Load up a database connection instead.
  ;; Or, maybe better, use an existing database connection that was
  ;; configured elsewhere.
  {})

(defn stop
  [users]
  {})

(defn existing-user-ids
  "What users does the system know about?
TODO: Add the ability to create new users and look them up."
  [system]
  (keys @(:users system)))

(defn existing-user?
  "Does the system know about this user?
This approach sucks."
  [user-id system]
  (contains? (existing-user-ids system) user-id))

(defn add-user
  [credentials system]
  (swap! (:users system) (fn [current]
                           (into current credentials))))
