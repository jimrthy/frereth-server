(ns com.frereth.server.comms.detangler
  "Awful name for an alternative comms protocol.

  Set up an experimental netty server and see what happens."
  (:require [aleph.tcp :as tcp]
            ;; Odds are, we also want a UDP section for less-important messages
            ;; Although that's a detail that we should probably hide from most
            ;; clients.
            ;; That's a slippery slope, of course
            ))

(defn gateway
  [strm info]
  ;; TODO: This is the entry point
  (throw (ex-info "Not implemented" {:problem info})))

;; This *must* go into a Component (or the moral equivalent)
(tcp/start-server gateway {:port 22181})
