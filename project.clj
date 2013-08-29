(defproject frereth-server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  :url "http://frereth.com"
  :license {:name "Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.zeromq/jzmq "2.2.1"]
                 [org.zeromq/cljzmq "0.1.1"]
                 [byte-transforms "0.1.0"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  ;; How does it work to use an EPL dependency in
                                  ;; an AGPL project, esp. if it's only part of the
                                  ;; development profile?
                                  [expectations "1.4.49"]]}}
  :plugins [[lein-expectations "0.0.7"]]
  :main frereth-server.core)
