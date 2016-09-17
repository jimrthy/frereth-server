(ns com.frereth.server.comms.loader
  "Feeding raw script objects to the browser

I'm very torn about whether this belongs here or in the client

But it *is* pretty clojurescript specific. So going with this approach for now.

Note that specialized scripts (such as anything that doesn't really belong in core)
should come from whichever server told the client to request them.

Defining 'what really belong[s] in core' is worth a lot of consideration"
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.spec :as s]
            [com.frereth.common.schema :as fr-skm])
  (:import [java.net URL]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/fdef find-namespace
        :args (s/cat :module-name string?
                     ;; Note that this needs to be an ordered collection, or it's pointless
                     :extension-search-order (s/coll-of string?))
        :ret (s/nilable (fr-skm/class-predicate URL)))
(defn find-namespace
  [module-name extension-search-order]
  (let [names (string/split module-name #"\.")
        folder-names (butlast names)
        file-name (last names)
        path (str "public/js/compiled/" (string/join "/" folder-names) "/" file-name ".")]
    (when-let [almost-result (seq
                              (take 1
                                    (filter identity
                                            (map (fn [extension]
                                                   (let [actual-name (str path extension)]
                                                     (println "Searching for" actual-name)
                                                     (io/resource actual-name)))
                                                 extension-search-order))))]
      (first almost-result))))

(s/fdef load-cljs-namespace
        :args (s/cat :module-name string?
                     ;; Note that this needs to be an ordered collection, or it's pointless
                     :extension-search-order (s/coll-of string?))
        :ret (s/nilable string?))
(defn load-cljs-namespace
  [module-name extension-search-order]
  (when-let [url (find-namespace module-name extension-search-order)]
    ;; Note that there really isn't any reason to assume this is a file
    (slurp (io/file url))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef load-fn-ns
        :args (s/cat :module-name string?)
        :ret (s/nilable string?))
(defn load-fn-ns
  [module-name]
  (load-cljs-namespace module-name ["cljs" "cljc" "js"]))

(s/fdef load-macros
        :args (s/cat :module-name string?)
        :ret (s/nilable string?))
(defn load-macros
  [module-name]
  (load-cljs-namespace module-name ["clj" "cljc"]))
