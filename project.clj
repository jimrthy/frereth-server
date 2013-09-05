(defproject frereth-server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  :url "http://frereth.com"
  ;; TODO: Just break down and switch to EPL.
  ;; It'll make life so much easier in the long run.
  :license {:name "Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 ;; See if swapping to jeromq makes life easier.
                 ;; This seems like I'll be missing an important
                 ;; point when the security/encryption pieces fall
                 ;; into place.
                 ;; Run with this for now...jeromq is advertised
                 ;; as a drop-in replacement.
                 ;;[org.zeromq/jzmq "2.2.1"]
                 [org.jeromq/jeromq "0.3.0-SNAPSHOT"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]
                 [byte-transforms "0.1.0"]]
  ;; TODO: Will I ned the sonatype repositories reference(s)?
  :repositories {;"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 }
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [midje "1.5.1"]
                                  ]}}
  :plugins [[lein-midje "3.0.0"]]
  :main frereth-server.core)
