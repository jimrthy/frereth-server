(defproject frereth-server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  ;; TODO: Serve something on this website
  :url "http://frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-transforms "0.1.0"]
                 [com.postspectacular/rotor "0.1.0"]
                 [com.taoensso/timbre "2.7.1"]
                 ;; For now, this next library needs to be distributed to
                 ;; a local maven repo.
                 ;; It seems like it should really take care of its handler
                 ;; ...except that very likely means native libraries, so
                 ;; it gets more complicated. Still, we shouldn't be worrying
                 ;; about details like jeromq vs jzmq here.
                 [org.clojars.jimrthy/cljeromq "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.5.1"]
                 ;; See if swapping to jeromq makes life easier.
                 ;; This seems like I'll be missing an important
                 ;; point when the security/encryption pieces fall
                 ;; into place.
                 ;; Run with this for now...jeromq is advertised
                 ;; as a drop-in replacement.
                 ;; Aside from the fact that 2.2 is ancient and mostly obsolete.
                 ;;[org.zeromq/jzmq "2.2.1"]
                 [org.jeromq/jeromq "0.3.0-SNAPSHOT"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]]
  :main frereth-server.core
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[clj-ns-browser "1.3.1"]
                                  [midje "1.6.0"]
                                  [night-vision "0.1.0-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [ritz/ritz-debugger "0.7.0"]]
                   ;; night-vision.goggles/introspect-ns! 'user
                   :injections [(require 'night-vision.goggles)
                                (require 'clojure.pprint)]}}
  :plugins [[lein-midje "3.0.0"]]
  :repl-options {:init-ns user}
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"})
