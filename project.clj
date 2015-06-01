(defproject com.frereth/server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  ;; TODO: Serve something on this website
  :url "http://frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-transforms "0.1.4"]
                 [com.frereth/common "0.0.1-SNAPSHOT"]
                 [com.postspectacular/rotor "0.1.0"]
                 [com.stuartsierra/component "0.2.3"]]
  ;; Q: Is there a good way to move this up into common?
  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:" (System/getenv "LD_LIBRARY_PATH"))]
  :main frereth-server.core
  :profiles {:dev {:source-paths ["dev"]
                   :plugins [[org.clojure/tools.namespace "0.2.10"]
                             [org.clojure/java.classpath "0.2.2"]]}
             :uberjar {:aot :all}}
  ;; If I'm going to be using this, it makes a lot more sense to move it
  ;; into one of my personal profiles
  :plugins [[com.palletops/uberimage "0.4.1" :exclusions [clj-time]]]
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
