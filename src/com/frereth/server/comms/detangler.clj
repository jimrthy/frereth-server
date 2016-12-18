(ns com.frereth.server.comms.detangler
  "Awful name for an alternative comms protocol.

  Set up an experimental netty server and see what happens."
  (:require [aleph.tcp :as tcp]
            ;; Odds are, we also want a UDP section for less-important messages
            ;; Although that's a detail that we should probably hide from most
            ;; clients.
            ;; That's a slippery slope, of course
            [com.frereth.common.aleph :as aleph]
            [com.frereth.common.zmq-socket :as zmq-socket]
            [manifold.stream :as stream]))

(defn fast-echo-handler
  "Set up an arbitrary handler that will call f on the input
before parroting...whatever it sends back.

TODO: Experiment and figure out how this whole thing works"
  [f]
  (fn [s info]
    ;; Notice that this really just connects s to itself.
    ;; This is OK because it's a duplex stream.
    (stream/connect
     (stream/map f s)
     s)))

;; This *must* go into a Component (or the moral equivalent)
(start-server (fast-echo-handler inc) {:port 22181})
