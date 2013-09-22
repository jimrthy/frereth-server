(defproject frereth-server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  ;; TODO: Serve something on this website
  :url "http://frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-transforms "0.1.0"]
                 [org.clojure/clojure "1.5.1"]
                 ;; FIXME: Log rotation!!
                 [org.clojure/tools.logging "0.2.6"]
                 ;; See if swapping to jeromq makes life easier.
                 ;; This seems like I'll be missing an important
                 ;; point when the security/encryption pieces fall
                 ;; into place.
                 ;; Run with this for now...jeromq is advertised
                 ;; as a drop-in replacement.
                 ;; Aside from the fact that 2.2 is ancient and mostly obsolete.
                 ;;[org.zeromq/jzmq "2.2.1"]
                 [org.jeromq/jeromq "0.3.0-SNAPSHOT"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]
                 ]
  :git-dependencies [["git@github.com:jimrthy/cljeromq.git"]]
  :main frereth-server.core
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[midje "1.5.1"]
                                  [org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]]}}
  :plugins [[lein-midje "3.0.0"]
            ;; lein-git-deps only until I can get my zeromq wrapper into clojars
            [lein-git-deps "0.0.1-SNAPSHOT"]]
  ;; TODO: Will I need the sonatype repositories reference(s)?
  :repositories {;"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 }
  :source-paths ["src" ".lein-git-deps/cljeromq/src"])
