(ns frereth-server.config)

;;;; FIXME: This all needs to go away (yeah, I know, there isn't much).
;;;; Anything that references it needs to get a system parameter instead.
;;;; c.f. Stuart Sierra's workflow revisited. This is an abomination.

;;; DONE: This next needs to move into something like system.clj
(comment (def place-holder {:port 7843
                            :master-port 7842
                            :auth-port 7841}))
(comment
  ;; Honestly, there doesn't seem to be much reason to make this dynamic
  ;; It's the port where all the interesting stuff happens
  (def ^:dynamic *port* 7843))

;; Where you go for root access...
(comment (def master-port 7842))

;; Connect here to log in.
(comment (def auth-port 7841))
