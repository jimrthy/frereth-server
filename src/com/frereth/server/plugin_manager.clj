(ns com.frereth.server.plugin-manager
  "Organize all the 'interesting' pieces that actually do things.

For starters, everything's going to be an App. I'll almost definitely
want to add Daemons (that's really what getty is, right?), but start
  with this approach"
  (:require [com.stuartsierra.component :as cpt]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema/specs

()

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(s/defrecord PluginManager [default-port :- s/Int
                            getty :-
                            root-namespace :- s/Symbol]
  cpt/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [_]
  ())
