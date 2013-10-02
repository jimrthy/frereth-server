(ns frereth-server.authorization
  (:require [cljeromq.core :as mq])
  (:gen-class))

(defn runner
  "Deal with authorization requests."
  [ctx]
  ;; TODO:
  ;; Kick off a thread that polls a socket in ctx to validate that a user
  ;; has appropriate access rights to do...something.
  ;; This is amorphous enough that I probably don't want to do anything
  ;; with it until it's received a lot more thought.
  (throw (RuntimeException. "FIXME: Get this written")))
