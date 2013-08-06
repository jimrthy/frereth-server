(ns frereth-server.system
  (:gen-class)
  (:require [frereth-server.auth-socket :as auth]
            [zguide.zhelpers :as mqh]
            [zeromq.zmq :as mq])
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

   ;; It seems pretty likely that this is important.
   ;; At least for remote servers that might very well
   ;; be load-balanced, or some such.
   ;; Of course, that's really a bad reason for it to 
   ;; exist now.
   :broker (atom nil)

   ;; Signal everything that we're finished.
   ;; Not elegant...but it's already enough of a PITA to qualify
   ;; as YAGNI.
   :done (atom nil)

   ;; These seem like lower-level configuration details that don't 
   ;; particularly belong here...oh, well. If nothing else, they
   ;; work as something like fairly reasonable defaults
   ;; FIXME: Seriously, though. These don't belong here.
   :ports {:client 7843
           :master 7842
           :auth 7841}})

(defn start
  "Performs side effects to initialize the system, acquire resources,
and start it running. Returns an updated instance of the system."
  [universe]
  ;; Some combination of doto and -> (->> ?) seems appropriate here
  (assert (not @(:network-context universe)))
  (assert (not @(:master-connection universe)))
  ;; Q: What on earth did I have planned for this?
  ;; A: My best guess is something along the lines of
  ;; majordomo.
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
        ;; That strongly goes against 0mq's grain, but it's doable.

        ;;context (mq/context (- (.availableProcessors (Runtime/getRuntime)) 1))
        context (mq/context 1)

        ;; Which ports should I be listening to?
        ports (:ports universe)

        ;; Where should the master server be listening?
        ;; This is the part that deals with really controlling the server.
        ;; For now, at least, think of it as a back door.
        ;; I'm torn about whether it's a good idea or not...
        ;; honestly, its existence should be configurable
        ;; (and default to off!)
        ;;master-port config/master-port
        master-port (:master ports)
        
        ;; Connection to localhost for Ultimate Power...
        ;; this should, honestly, be something like NREPL
        ;; REP is absolutely the wrong type of socket, though.
        ;; Check RRR patterns. At the very least, change
        ;; it to a Dealer.
        ;; FIXME: Make it so.
        master-socket (mq/socket context :rep)

        ;; Where does the actual action happen?
        ;; Note that this is the connection that, say, sys-ops should use
        ;; I think that comment has rotted more than a bit.
        ;; I'm obviously still making this up as I go, but
        ;; I *do* intend for this to be the actual client
        ;; communication ports.
        ;; But, honestly, now we're getting into territory
        ;; that has to be decided on a case-by-case basis.
        ;; I'm not trying to solve the problem in general
        ;; yet. Just my specific problem.
        client-port (:client ports)

        ;; Clients connect here to do things like logging in and
        ;; find out where to access the most appropriate resources
        ;; (whatever that means...different resource sets for
        ;; different capabilities. Different URLs as new versions
        ;; are released. Etc.
        ;; Then again, for the simplest scenarios, just get the
        ;; resources here directly, more or less the way HTTP works.
        auth-port (:auth ports)

        ;; These probably need to be something different
        ;; The 'actual client' sockets should probably be a
        ;; dealer/router pair. c.f. rrbroker.
        ;;client-sockets (mq/socket context mq/dealer)
        auth-thread (auth/runner context done auth-port)

        ;; Umm...No. This should not be a publisher.
        ;; Except that it makes sense.
        ;; Stream out state updates over this.
        ;; Deal with "real" client requests over RRR.
        ;; Except that it totally does not make sense:
        ;; different clients will receive different messages
        ;; based on their state (e.g. location).
        ;; Here's a major issue with the one-size-fits-all
        ;; approach:
        ;; It doesn't.
        ;; Worry about it some other time.
        client-socket (mq/socket context :pub)]
    ;; Using JNI, I can use shared memory sockets, can't I?
    ;;(mq/bind master-socket (format "ipc://127.0.0.1:%d" master-port))
    ;; Doesn't work on windows.
    ;; Note that, on unix, we have to set up the named pipe.
    ;; FIXME: Should probably plan on using that, if it's available.
    ;; For now, it qualifies as premature optimization.
    (let [master-address (format "tcp://127.0.0.1:%d" master-port)]
      (try
        (mq/bind master-socket master-address)
        (catch Exception e
          ;; FIXME: Switch to something that resembles real logging.
          (do (println "Failed to bind " master-socket " to " master-address)
              (throw)))))

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
     :master-connection (atom master-socket)
     :clients (atom client-socket)
     :authentication-thread (atom auth-thread)
     ;; Strictly so I have a reference to what's happening where.
     ;; Note that this is just an ordinary map.
     :ports ports}))

(defn kill-authenticator
  "Tell the authenticator socket to kill itself.
I originally tried to TCO with loop/recur, but that just does not
play nicely with killing off the client socket and retrying on
failure.
It doesn't recurse deeply, so don't worry about that.
I'm waffling about whether this belongs in this namespace
or auth-socket."
  ([universe]
     ;; Just go with what seems like a reasonable default
     ;; for the retry count
     (kill-authenticator universe 5))

  ([universe retries]
     ;; FIXME: Add an atom to universe to indicate that we expect
     ;; authenticator to be around in the first place. If it's the
     ;; first time through, don't waste time on this.
     (when-let [ports (:ports universe)]
       (if-let [ctx @(:network-context universe)]
         (do
           (when (< 0 retries)
             (mqh/with-socket [auth-killer ctx :req]
               (println "There are " retries 
                        " attempts left to be rid of ports")
               
               ;; Now we can start killing off the interesting shit
               (mq/connect auth-killer (format "tcp://127.0.0.1:%d"
                                               (ports :auth)))
               ;; send is async, right? There isn't any point
               ;; to specifying NOBLOCK here,
               ;; is there?
               (mqh/send auth-killer "dieDieDIE!!")
               (println "Death sentence sent")

               ;; If the other side's alive, it really should 
               ;; ACK pretty much immediately.
               ;; This is the second time I've needed
               ;; this sort of boiler plate.
               ;; FIXME: Use with-poller instead
               (let [poller (mq/poller ctx)]
                 (mq/register poller auth-killer :pollin :pollerr)
                 ;; The 0 indicates that we're checking the first 
                 ;; [and only]
                 ;; poller. Really need some sort of timeout option. 
                 ;; I think.
                 ;; Compare with auth-socket.
                 (if (mq/check-poller poller 0 :pollerr)
                   (throw (RuntimeException.
                           "AUTH socket error! Be smarter"))
                   (when-not (mq/check-poller poller 0 :pollin)
                     ;; This is lame...but I have to start somewhere.
                     ;; If the sockets are all tied up doing something
                     ;; else, this much delay
                     ;; really ought to be enough to let them unstick.
                     ;; Of course, magic numbers like this are evil!
                     (Thread/sleep 75)
                     (kill-authenticator universe (dec retries))))))))
         (println "No networking context...WTF?")))
     (println "Authentication thread gone")))

(defn stop
  "Performs side-effects to shut down the system and release its
resources. Returns an updated instance of the system, ready to re-start."
  [universe]
  (when universe
    (println "Have a universe to kill")
    ;; Signal to other threads that pieces are finished
    (swap! (:done universe) (constantly true))

    ;; Realistically, need to wait for them to finish up.
    (when-let [ctx @(:network-context universe)]
      (println "Have a context to shut down")
      (try
        (kill-authenticator universe)
        (println "Authenticator killer didn't throw")
        (finally
          ;; Or whatever the clojure equivalent is.
          (when-let [sock @(:clients universe)]
            (.close sock))
          (println "Client thread closed")
          (when-let [sock @(:master-connection universe)]
            (.close sock))
          (println "Master connection closed")
          ;; ...and we're hanging here.
          ;; This is built-in 0mq behavior. Especially
          ;; at startup, when I'm sending several "kill"
          ;; messages to a nonexistent listener.
          ;; The context is trying to obey me and deliver
          ;; those messages before exiting.
          ;; This does not work.
          ;; FIXME: How do I force it to accept failure?
          (throw (RuntimeException. "Start here"))
          (.term ctx)))))
  (println "Universe destroyed.")
  ;; Just return a completely fresh instance.
  (init))
