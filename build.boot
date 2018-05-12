(def project 'frereth-server)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"src"}
          :dependencies '[[adzerk/boot-test "RELEASE" :scope "test"]
                          [byte-transforms "0.1.4"]
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
                          [org.clojure/clojure "1.9.0"]
                          [org.clojure/spec.alpha "0.1.143"]
                          ;; FIXME: Move this to the testing task.
                          ;; Don't want to depend on it in general.
                          [org.clojure/test.check "0.10.0-alpha2" :scope "test" :exclusions [org.clojure/clojure]]
                          ;; TODO: Move this into the dev task
                          ;; (sadly, it isn't a straight copy/paste)
                          [samestep/boot-refresh "0.1.0" :scope "test" :exclusions [org.clojure/clojure]]
                          [tolitius/boot-check "0.1.9" :scope "test" :exclusions [org.clojure/clojure]]])

;; Q: What's the equivalent of lein's :jvm-opts ?
;; Then again, that's for the sake of native 0mq interop, and I've mostly
;; moved past it...I think.

(task-options!
 aot {:namespace   #{'frereth-cp.server 'frereth-cp.client}}
 pom {:project     project
      :version     version
      :description "Implement CurveCP in clojure"
      ;; TODO: Add a real website
      :url         "https://github.com/jimrthy/frereth-cp"
      :scm         {:url "https://github.com/jimrthy/frereth-cp"}
      ;; Q: Should this go into public domain like the rest
      ;; of the pieces?
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 jar {:main        'com.frereth.server.core
      :file        (str "frereth-cp-server-" version "-standalone.jar")})

(require '[samestep.boot-refresh :refer [refresh]])
(require '[tolitius.boot-check :as check])
(require '[adzerk.boot-test :refer [test]])
(require '[boot.pod :as pod])

(deftask build
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  ;; Note that this approach passes the raw command-line parameters
  ;; to -main, as opposed to what happens with `boot run`
  ;; TODO: Eliminate this discrepancy
  (let [dir (if (seq dir) dir #{"target"})]
    (comp (javac) (aot) (pom) (uber) (jar) (target :dir dir))))

(deftask check-conflicts
  "Verify there are no dependency conflicts."
  []
  (with-pass-thru fs
    (require '[boot.pedantic :as pedant])
    (let [dep-conflicts (resolve 'pedant/dep-conflicts)]
      (if-let [conflicts (not-empty (dep-conflicts pod/env))]
        (throw (ex-info (str "Unresolved dependency conflicts. "
                             "Use :exclusions to resolve them!")
                        conflicts))
        (println "\nVerified there are no dependency conflicts.")))))

(deftask dev
  "Add the dev resources to the mix"
  []
  (merge-env! :source-paths #{"dev" "dev-resources"}
              ;; FIXME: Need access to the org.clojure/tools.namespace plugin
              ;; to maintain equivalency w/ original lein version.
              ;; Then again, that's supposed to be the point behind boot-refresh,
              ;; at least as I'm using it.
              :dependencies [[org.clojure/java.classpath "0.2.3"
                              :exclusions [org.clojure/clojure]]])
  identity)

;; Original lein version includes a reference to the com.palletops/uberimage plugin
;; Q: What was/is it for?
;; Q: Do I want to use it?

(deftask testing
  "Add pieces for testing"
  []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask cider-repl
  "Set up a REPL for connecting from CIDER"
  []
  ;; Just because I'm prone to forget one of the vital helper steps
  ;; Note that this would probably make more sense under profile.boot.
  ;; Except that doesn't have access to the defined in here, such
  ;; as...well, almost any of what it actually uses.
  ;; Q: Should they move to there also?
  (comp (dev) (testing) (check-conflicts) (cider) (javac) (repl)))

(deftask run
  "Run the project."
  [f file FILENAME #{str} "the arguments for the application."]
  ;; This is a leftover template from another project that I
  ;; really just copy/pasted over.
  ;; Q: Does it make any sense to keep it around?
  (require '[com.frereth.server :as app])
  (apply (resolve 'app/-main) file))
