;; Really intended as a higher-level wrapper layer over
;; cljzmq.
;;
;; Really need to add the license...this project is LGPL.

(ns zguide.zhelpers
  (:refer-clojure :exclude [send])
  (:require [zeromq.zmq :as mq])
  (:import [org.zeromq ZMQ$Context ZMQ$Socket ZMQQueue])
  (:import (java.util Random)
           (java.nio ByteBuffer)))

(defmacro with-context
  [[id threads] & body]
  `(let [~id (mq/context ~threads)]
     (try ~@body
          (finally (.term ~id)))))


(defmacro with-socket [[name context type] & body]
  `(let [~name (mq/socket ~context ~type)]
     (try ~@body
          (finally (.close ~name)))))

(defmacro with-poller [poller-name context socket & body]
  "Cut down on some of the boilerplate around pollers.
What's left still seems pretty annoying."
  ;; I don't think I actually need this sort of gensym
  ;; magic with clojure, do I?
  (let [name# poller-name
        ctx# context
        s# socket]
    `(let [~name# (mq/poller ~ctx#)]
       (mq/register ~name# ~s# :pollin :pollerr)
       (try
         ~@body
         (finally
           (mq/unregister ~name# ~s#))))))

(comment (defn queue
  "Forwarding device for request-reply messaging.
cljzmq doesn't seem to have an equivalent.
It almost definitely needs one.
FIXME: Fork that repo, add this, send a Pull Request."
  [#^ZMQ$Context context #^ZMQ$Socket frontend #^ZMQ$Socket backend]
  (ZMQQueue. context frontend backend)))

(defmulti send (fn [#^ZMQ$Socket socket message & flags]
                 (class message)))

(defmethod send String
  ([#^ZMQ$Socket socket #^String message flags]
     ;; If the received flags are a keyword, convert from clzmq.
     ;; Otherwise, assume they're a string.
     (let [actual-flags
           (if (keyword? flags)
             (do (println (format "Sending '%s' with flags %s" message (name flags)))
                 (mq/socket-options flags))
             flags)]
       (println (format "Sending '%s' with flags %d" message actual-flags))
       (mq/send socket (.getBytes message) actual-flags)))
  ([#^ZMQ$Socket socket #^String message]
     (send socket message :no-block)))

(defn send-partial [#^ZMQ$Socket socket message]
  "I'm seeing this as a way to send all the messages in an envelope, except 
the last.
Yes, it seems dumb, but it was convenient at one point.
Honestly, that's probably a clue that this basic idea is just wrong."
  (send socket message :send-more))

(defn send-all [#^ZMQ$Socket socket messages]
  "At this point, I'm basically envisioning the usage here as something like HTTP.
Where the headers back and forth carry more data than the messages.
This approach is a total cop-out.
There's no way it's appropriate here.
I just need to get something written for my
\"get the rope thrown across the bridge\" approach.
It totally falls apart when I'm just trying to send a string."
  (doseq [m messages]
    (send-partial socket m))
  (send socket ""))

(defn recv-all-str
  "How much overhead gets added by just converting the received primitive
Byte[] to strings?"
  ([#^ZMQ$Socket socket]
     (let [packets (mq/receive-all socket)]
       (map #(String. %) packets))))

(defn recv-obj
  "This function is horribly dangerous and really should not be used.
It's also quite convenient:
read a string from a socket and convert it to a clojure object.
That's how this is really meant to be used, if you can trust your peers.
Could it possibly be used safely through EDN?"
  ([#^ZMQ$Socket socket]
     (-> socket mq/receive-str read)))

(defn dump
  "Cheeseball first draft at just logging incoming messages.
This approach is pretty awful...at the very least it should build
a string and return that.
Then again, it's fairly lispy...callers can always rediret STDOUT."
  [#^ZMQ$Socket socket]
  (println (->> "-" repeat (take 38) (apply str)))
  (doseq [msg (mq/receive-all socket)]
    (print (format "[%03d] " (count msg)))
    (if (and (= 17 (count msg)) (= 0 (first msg)))
      (println (format "UUID %s" (-> msg ByteBuffer/wrap .getLong)))
      (println (-> msg String. .trim)))))

(comment (defn set-id
  "what on earth was I doing with this?"
  ([#^ZMQ$Socket socket #^long n]
    (let [rdn (Random. (System/currentTimeMillis))]
      (identify socket (str (.nextLong rdn) "-" (.nextLong rdn) n))))
  ([#^ZMQ$Socket socket]
     (set-id socket 0))))
