(ns frereth-server.user
  (:gen-class))

(defn start
  [users]
  ;; TODO: Load up a database connection instead.
  ;; Or, maybe better, use an existing database connection that was
  ;; configured elsewhere.
  ;; Or, maybe, use a connection pool.
  ;; c.f. BoneCP, c3po, and
  ;; https://github.com/kumarshantanu/clj-dbcp
  ;; There's also
  ;; http://tomcat.apache.org/tomcat-7.0-doc/jdbc-pool.html
  ;; Korma should take that worry completely off my plate.
  ;; So don't worry about the connection pool part, anyway.
  ;; As for the actual database connection...I actually want
  ;; to be using Apache Shiro here, so I probably don't need
  ;; that at all.
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
