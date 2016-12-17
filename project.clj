(defproject com.frereth/server "0.1.0-SNAPSHOT"
  :description "Serve Frereth worlds to client(s)"
  ;; TODO: Serve something on this website
  :url "http://frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [#_[byte-transforms "0.1.4"]  ; get this from aleph now
                 ;; This gets its reference to guava from component-dsl's version of clojurescript.
                 ;; TODO: Fix that (I don't want/need access to clojurescript here)
                 [com.frereth/common "0.0.1-SNAPSHOT" :exclusions [com.google.guava/guava]]
                 [com.jimrthy.substratum "0.1.0-SNAPSHOT" :exclusions [com.taoensso/encore
                                                                       com.taoensso/timbre
                                                                       im.chit/hara.event
                                                                       io.aviso/pretty
                                                                       org.clojure/core.async
                                                                       org.clojure/tools.cli]]
                 [com.postspectacular/rotor "0.1.0"]
                 ;; For now, this next library needs to be distributed to
                 ;; a local maven repo.
                 ;; It seems like it should really take care of its handler
                 ;; ...except that very likely means native libraries, so
                 ;; it gets more complicated. Still, we shouldn't be worrying
                 ;; about details like jeromq vs jzmq here.
                 ;;[org.clojars.jimrthy/cljeromq "0.1.0-SNAPSHOT"]

                 ;; Q: Why aren't I inheriting this from frereth-common?
                 ;; A: It's really a bug in CIDER that hasn't been released yet.
                 ;; TODO: Need to switch to either boot or use :managed-dependencies
                 [org.clojure/clojure "1.9.0-alpha14"]]
  ;; Q: Is there a good way to move the extra library path up into common?
  ;; Better Q: Now that I've copied it into common, do I still need this here and in client?
  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:" (System/getenv "LD_LIBRARY_PATH"))
             "-Djava.awt.headless=true"]
  :main frereth.server.core
  :profiles {:dev {:dependencies [[org.clojure/java.classpath "0.2.3"
                                   :exclusions [org.clojure/clojure]]]
                   :plugins [[org.clojure/tools.namespace "0.2.11" :exclusions [org.clojure/clojure]]
                             #_[org.clojure/java.classpath "0.2.2"]]
                   :source-paths ["dev"]}
             :uberjar {:aot :all}}
  ;; If I'm going to be using this, it makes a lot more sense to move it
  ;; into one of my personal profiles
  :plugins [[com.palletops/uberimage "0.4.1" :exclusions [clj-http
                                                          clj-time
                                                          org.apache.httpcomponents/httpclient
                                                          org.apache.httpcomponents/httpcore
                                                          org.clojure/clojure]]]
  :repl-options {:init-ns user
                 :timeout 120000}
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
  ;; 10 yards. It's just for zmq 3.2

  ;; TODO: Verify this
  :test-paths ["test" "src/test/clojure"]  ; default, but lein-test-refresh isn't finding them
  :test-refresh {:notify-on-success false
                 ;; Suppress some of the clojure.test cruft messages
                 :quiet false}

  ;; https://registry.hub.docker.com/u/gsnewmark/jzmq/dockerfile/
  ;; is probably the proper place to start
  :uberimage {})
