(defproject frereth-server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  ;; TODO: Serve something on this website
  :url "http://frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-transforms "0.1.4"]
                 ;; Q: Does this make any sense in production?
                 ;; A: Well, it makes sense for the general runtime which
                 ;; is the primary goal.
                 [com.cemerick/pomegranate "0.3.0" :exclusions [org.codehaus.plexus/plexus-utils]]
                 [com.postspectacular/rotor "0.1.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [com.taoensso/timbre "3.4.0"]
                 [im.chit/ribol "0.4.0"]
                 ;; For now, this next library needs to be distributed to
                 ;; a local maven repo.
                 ;; It seems like it should really take care of its handler
                 ;; ...except that very likely means native libraries, so
                 ;; it gets more complicated. Still, we shouldn't be worrying
                 ;; about details like jeromq vs jzmq here.
                 ;;[org.clojars.jimrthy/cljeromq "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.7.0-RC1"]
                 [org.zeromq/cljzmq "0.1.4"]
                 [prismatic/schema "0.4.3"]]
  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:" (System/getenv "LD_LIBRARY_PATH"))]
  :main frereth-server.core
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[midje "1.6.3" :exclusions [joda-time]]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [org.clojure/java.classpath "0.2.2"]]}
             :uberjar {:aot :all}}
  :plugins [[lein-midje "3.1.3"]
            [com.palletops/uberimage "0.4.1"]]
  :repl-options {:init-ns user}
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"}
  ;; From lein-uberimage's README
  ;; Can specify:
  ;; :cmd - matches Dockerfile CMD
  ;; :instructions - insert right after the Dockerfile's FROM (which, by
  ;; definition, starts defining a new image)
  ;; :files - map of {docker-image-target lein-project-source}
  ;; :tag - something like "user/repo:tag"
  ;; :base-image - name of the base image (defaults to pallet/java)
  ;; https://github.com/zeromq/jzmq/issues/339 has Dockerfile snippets that might be useful
  ;; for setting up a base image that's ready to be used for this
  ;; Actually, https://github.com/zeromq/jzmq/blob/master/Dockerfile has the whole
  ;; 10 yards. It's just for zmq 3.2 (which happens to be where jzmq is still sitting)

  ;; https://registry.hub.docker.com/u/gsnewmark/jzmq/dockerfile/
  ;; is probably the proper place to start
  :uberimage {})
