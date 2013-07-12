(ns frereth-server.system
  (:gen-class)
  (:require [frereth-server.auth-socket :as auth]
            [frereth-server.config :as config]
            [zguide.zhelpers :as mq])
  (:gen-class))

(defn init
  "Returns a new instance of the whole application.
I really need to figure out what I want to do here."
  []
  {;; Process-wide context for zeromq. It doesn't seem
   ;; likely to change at runtime, but it's possible.
   ;; e.g. If we want to tweak its threads.
   :network-context (atom nil)
   ;; Actual controller socket.
   ;; If the context changes, then all the sockets that use it must also.
   :master-connection (atom nil)
   :broker (atom nil)
   ;; Signal everything that we're finished.
   :done (atom nil)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
and start it running. Returns an updated instance of the system."
  [universe]
  ;; Some combination of doto and -> (->> ?) seems appropriate here
  (assert (not @(:network-context universe)))
  (assert (not @(:master-connection universe)))
  ;; What on earth did I have planned for this?
  (assert (not @(:broker universe)))
  (assert (not @(:done universe)))

  ;; FIXME: Be paranoid and protect everything here!
  (let [;; Let's be explicit about this:
        done (atom false)

        ;; Actual networking context
        ;; Q: How many threads should I dedicate to the networking context?
        ;; A: For now, run with "available - 1."
        ;; This value will really vary according to need and is something
        ;; that is ripe for tweaking. In all honesty, it should probably
        ;; be 1 for now.
        ;; FIXME: Load this from configuration.
        ;; For that matter, really should be able to tweak it at runtime.

        ;;context (mq/context (- (.availableProcessors (Runtime/getRuntime)) 1))
        context (mq/context 1)

        ;; Where should the master server be listening?
        ;; This is the part that deals with really controlling the server.
        ;; For now, at least, think of it as a back door.
        ;; I'm torn about whether it's a good idea or not...
        ;; honestly, its existence should be configurable
        ;; (and default to off!)
        master-port config/master-port

        ;; Connection to localhost for Ultimate Power...
        ;; this should, honestly, be something like NREPL
        master-socket (mq/socket context mq/rep)

        ;; Where does the actual action happen?
        ;; Note that this is the connection that, say, sys-ops should use
        client-port config/*port*

        ;; These probably need to be something different
        ;; The 'actual client' sockets should probably be a
        ;; dealer/router pair. c.f. rrbroker.
        ;;client-sockets (mq/socket context mq/dealer)
        auth-thread (auth/runner context done)
        
        client-socket (mq/socket context mq/pub)]
    ;; Using JNI, I can use shared memory sockets, can't I?
    ;;(mq/bind master-socket (format "ipc://127.0.0.1:%d" master-port))
    ;; Doesn't work on windows.
    ;; Note that, on unix, we have to set up the named pipe.
    ;; FIXME: Should probably plan on using that, if it's available.
    ;; For now, it qualifies as premature optimization.
    (mq/bind master-socket (format "tcp://127.0.0.1:%d" master-port))

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
    ;; Plan on publishing worlds to FreeNet. Duh.

    ;; Approach to try:
    ;; 1) Add listener threads. Create the sockets and
    ;; loop in there as appropriate.
    ;; 2) Have a set of auth sockets (no reason to use more
    ;; than one thread) that will redirect to things like
    ;; a source for downloading the current worldset and
    ;; the "real" client sockets.
    ;; 3) Can send a "KILL" message to the client sockets
    ;; when it's time for them to stop...but better be positive
    ;; that the command came from here. Maybe have them
    ;; polling on localhost, specifically for that?
    ;; Basic skeleton is done. c.f. authentication.clj.

    (mq/bind client-socket (format "tcp://*:%d" client-port))

    ;; Return the updated system.
    {:done done
     :network-context (atom context)
     ;; It's tempting to store these in atoms.
     ;; I'm not sure why I feel that temptation.
     :master-connection (atom master-socket)
     :clients (atom client-socket)
     :authentication-thread (atom auth-thread)}))

(defn stop
  "Performs side-effects to shut down the system and release its
resources. Returns an updated instance of the system, ready to re-start."
  [universe]
  (when universe
    ;; Signal to other threads that pieces are finished
    (swap! (:done universe) (constantly true))
    ;; Realistically, need to wait for them to finish up.
    ;; FIXME: Send a KILL request to the authentication thread

    (when-let [ctx @(:network-context universe)]
      (try
        (let [auth-killer (mq/socket ctx mq/req)]
          (mq/connect auth-killer (format "tcp://127.0.0.1:%d" config/auth-port))
          (mq/send auth-killer "dieDieDIE!!")
          ;; Wait for a response.
          (mq/recv-str auth-killer))
        (when-let [sock (:clients universe)]
          (.close sock))
        (when-let [sock (:master-connection universe)]
          (.close sock))
        (finally
          (.term ctx)))))
  ;; Just return a completely fresh instance.
  (init))
