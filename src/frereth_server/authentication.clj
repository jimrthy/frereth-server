(ns frereth-server.authentication
  (:require [frereth-server.config :as config]
            [zguide.zhelpers :as mq])
  (:gen-class))

(defn- authenticator
  "Deal with authentication requests."
  [ctx done-reference]
  ;; This looks like an ugly weakness in my scheme:
  ;; The client needs to connect to a dealer socket
  ;; to auth. Then, realistically, it needs to switch to
  ;; another dealer socket for actual command/control and
  ;; a sub socket for game state updates.
  ;; It's very tempting to just re-use this auth socket
  ;; for game data.
  ;; Not doing so may qualify as premature optimization,
  ;; but it seems pretty blindingly obvious.
  ;; The alternative simplifies the client ("I only need 1 socket!")
  ;; but not by much. Certainly not enough to qualify as an upside.
  (let [listener (mq/socket ctx mq/dealer)]
    (mq/bind listener (format "tcp://*:%d" config/auth-port))
    (while (not @done-reference)
      ;; Well, this is where the handshake and such actually goes.
      ;; Doesn't it?
      (throw (RuntimeException. "What should happen here?"))
      ;; Well...how much, if any, of this should happen here?
      ;; It seems like this probably *should* be pretty transparent,
      ;; so I can implement something blindly stupid here, then
      ;; not need to rewrite a bunch of crap when it's time to be less
      ;; stupid.
      ;; I strongly suspect that I'm actually looking for the
      ;; majordomo pattern.
      (intentional-syntax-error "Start here")

      ;; poll for a request.

      ;; That means that system/stop needs to send a "die" message
      ;; to this port.

      ;; FIXME: Bind a specific socket to a specific port just for
      ;; that message?

      ;; What's the memory overhead involved in that?
      ;; It seems like it can't possibly be worth worrying about.

      ;; For other message types, work through a login sequence.
      ;; Possibly present a potential client with details about
      ;; what to do/where to go next.
      )))


(defn runner
  "Set up the authenticator.
ctx is the zmq context for the authenticator to listen on.
done-reference is some sort of deref-able instance that will tell the thread to quit.
This feels like an odd approach, but nothing more obvious springs to mind."
  [ctx done-reference]
  (.start (Thread. (fn []
                     (authenticator ctx done-reference)))))
