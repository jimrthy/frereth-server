(ns frereth-server.system
  (:gen-class)
  (:require [frereth-server.config :as config]
            [zguide.zhelpers :as mq]))

(defn init
  "Returns a new instance of the whole application.
I really need to figure out what I want to do here."
  []
  {:network-context (atom nil)
   :master-connection (atom nil)
   :client-connections (atom nil)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
and start it running. Returns an updated instance of the system."
  [universe]
  ;; Some combination of doto and -> (->> ?) seems appropriate here
  (assert (not @(:network-context universe)))
  (assert (not @(:master-connection universe)))
  (assert (not @(:client-connections universe)))

  ;; Q: How many threads should I dedicate to the networking context?
  ;; A: For now, run with "available - 1.
  ;; This value will really vary according to need and is something
  ;; that is ripe for tweaking. In all honesty, it should probably
  ;; be 1 for now.
  (let [;; Actual networking context
        context (mq/context (- (.availableProcessors (Runtime/getRuntime)) 1))
        ;; Where should the master server be listening?
        master-port config/master-port
        ;; Connection to localhost for Ultimate Power...
        ;; this should, honestly, be something like NREPL
        master-socket (mq/socket context mq/rep)
        ;; Where should regular clients connect?
        client-port config/*port*
        ;; These probably need to be something different
        client-sockets (mq/socket context mq/router)]
    ;; Using JNI, I can use shared memory sockets, can't I?
    (mq/bind master-socket (format "tcp://localhost:%d" master-port))
    ;; Want to be pickier about who can connect. Or, at least,
    ;; have the option to do so.
    ;; Honestly, this probably shouldn't be part of the overall
    ;; system state for general user systems...it just opens up
    ;; hacking possibilities.
    ;; Besides, the name seems to imply a range of sockets. 
    ;; Which is pretty stupid.
    ;; Honestly, should have HTTPS connections for downloading
    ;; the "html/css/js" sort of pieces. That sort of thing
    ;; is quite a bit different than the actual game interface.
    ;; Esp. if I can use something smarter like bittorrent.
    ;; Seems more than a little silly to reinvent the wheel
    ;; there.
    ;; Need to invest more hammock time.
    (mq/bind client-sockets (format "tcp://*:%d" client-port))
    {:network-context context
     ;; It's tempting to store these in atoms.
     ;; I'm not sure why I feel that temptation.
     :master-connection master-socket
     :client-connections client-sockets}))

(defn stop
  "Performs side-effects to shut down the system and release its
resources. Returns an updated instance of the system, ready to re-start."
  [universe]
  (when universe
    (when-let [context (:network-context universe)]
      (try
        (when-let [sock (:client-connections universe)]
          (.close sock))
        (when-let [sock (:master-connection universe)]
          (.close sock))
        (finally
          (.term context)))))
  (init))
