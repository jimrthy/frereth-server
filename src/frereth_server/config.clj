(ns frereth-server.config)

;; Honestly, there doesn't seem to be much reason to make this dynamic
;; It's the port where all the interesting stuff happens
(def ^:dynamic *port* 7843)

;; Where you go for root access...
(def master-port 7842)

;; Connect here to log in.
(def auth-port 7841)
