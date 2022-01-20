(ns msgpack.stream
  (:refer-clojure :exclude [read])
  (:require
    [goog.crypt]
    [goog.math.Long]))

; TODO: make this configurable so if we know we are sending large messages we
; don't incur a cost.

; When a new output-stream is created we need to allocate a buffer to store
; the serialized bytes.  Using a fairly large size is ideal so we don't incur
; lots of memory copies when forming large messages.
(def MSGPACK-STREAM-DEFAULT-SIZE 2048)

(defn bytes->string
  [bs]
  (.utf8ByteArrayToString goog.crypt bs))


(defn string->bytes
  [s]
  (js/Uint8Array. (.stringToUtf8ByteArray goog.crypt s)))


(defprotocol IStream
  (inc-offset! [this new-offset])
  (resize-on-demand! [this n])
  (stream->uint8array [this]))


(defprotocol IInputStream
  (read [this n])
  (read-bytes [this n])
  (read-u8 [this])
  (read-i8 [this])
  (read-u16 [this])
  (read-i16 [this])
  (read-u32 [this])
  (read-i32 [this])
  (read-i64 [this])
  (read-f32 [this])
  (read-f64 [this])
  (read-str [this n]))


(defprotocol IOutputStream
  (write [this buffer])
  (write-u8 [this u8])
  (write-i8 [this i8])
  (write-u16 [this u16])
  (write-i16 [this i16])
  (write-u32 [this u32])
  (write-i32 [this i32])
  (write-i64 [this i64])
  (write-f64 [this f64]))


(deftype MsgpackInputStream
  [bytes ^:unsynchronized-mutable offset]

  IStream
  (inc-offset!  [_ n]
    (set! offset (+ offset n)))

  (resize-on-demand! [_ _]
    nil)

  (stream->uint8array [_]
    (js/Uint8Array. (.-buffer bytes)))

  IInputStream
  (read [this n]
    (let [old-offset offset]
      (inc-offset! this n)
      (.slice (.-buffer bytes) old-offset offset)))

  (read-bytes [this n]
    (js/Uint8Array. (read this n)))

  (read-u8 [this]
    (let [u8 (.getUint8 bytes offset)]
      (inc-offset! this 1)
      u8))

  (read-i8 [this]
    (let [i8 (.getInt8 bytes offset)]
      (inc-offset! this 1)
      i8))

  (read-u16 [this]
    (let [u16 (.getUint16 bytes offset)]
      (inc-offset! this 2)
      u16))

  (read-i16 [this]
    (let [i16 (.getInt16 bytes offset false)]
      (inc-offset! this 2)
      i16))

  (read-u32 [this]
    (let [u32 (.getUint32 bytes offset false)]
      (inc-offset! this 4)
      u32))

  (read-i32 [this]
    (let [i32 (.getInt32 bytes offset false)]
      (inc-offset! this 4)
      i32))

  (read-i64 [this]
    (let [high-bits (.getInt32 bytes offset false)
          low-bits (.getInt32 bytes (+ offset 4) false)]
      (inc-offset! this 8)
      (.toNumber (goog.math.Long. low-bits high-bits))))

  (read-f32 [this]
    (let [f32 (.getFloat32 bytes offset false)]
      (inc-offset! this 4)
      f32))

  (read-f64 [this]
    (let [f64 (.getFloat64 bytes offset false)]
      (inc-offset! this 8)
      f64))

  (read-str
    [stream n]
    (bytes->string (read-bytes stream n))))


(deftype MsgpackOutputStream
  [^:unsynchronized-mutable bytes
   ^:unsynchronized-mutable offset]

  IStream
  (resize-on-demand! [_ n]
    (let [base (+ offset n)]
      (when (> base (.-byteLength bytes))
        (let [new-bytes (js/Uint8Array. (bit-or 0 (* 1.5 base)))
              old-bytes (js/Uint8Array. (.-buffer bytes))]
          (set! bytes (js/DataView. (.-buffer new-bytes)))
          (.set new-bytes old-bytes 0)))))

  (inc-offset! [_ n] (set! offset (+ offset n)))

  (stream->uint8array [_]
    (js/Uint8Array. (.-buffer bytes) 0 offset))

  IOutputStream
  (write [this buffer]
    (resize-on-demand! this (.-byteLength buffer))
    (if (instance? js/ArrayBuffer buffer)
      (.set (js/Uint8Array. (.-buffer bytes)) (js/Uint8Array. buffer) offset)
      (.set (js/Uint8Array. (.-buffer bytes)) buffer offset))
    (inc-offset! this (.-byteLength buffer)))

  (write-u8 [this u8]
    (resize-on-demand! this 1)
    (.setUint8 bytes offset u8 false)
    (inc-offset! this 1))

  (write-i8 [this i8]
    (resize-on-demand! this 1)
    (.setInt8 bytes offset i8 false)
    (inc-offset! this 1))

  (write-u16 [this u16]
    (resize-on-demand! this 2)
    (.setUint16 bytes offset u16 false)
    (inc-offset! this 2))

  (write-i16 [this i16]
    (resize-on-demand! this 2)
    (.setInt16 bytes offset i16 false)
    (inc-offset! this 2))

  (write-u32 [this u32]
    (resize-on-demand! this 4)
    (.setUint32 bytes offset u32 false)
    (inc-offset! this 4))

  (write-i32 [this i32]
    (resize-on-demand! this 4)
    (.setInt32 bytes offset i32 false)
    (inc-offset! this 4))

  ; msgpack stores integers in big-endian
  (write-i64 [this u64]
    (let [glong (goog.math.Long/fromNumber u64)]
      (write-i32 this ^js/Number (.getHighBits glong))
      (write-i32 this ^js/Number (.getLowBits glong))))

  (write-f64 [this f64]
    (resize-on-demand! this 8)
    (.setFloat64 bytes offset f64 false)
    (inc-offset! this 8)))


(defn input-stream
  [buffer]
  (MsgpackInputStream. (js/DataView. buffer) 0))


(defn output-stream
  []
  (MsgpackOutputStream. (js/DataView. (js/ArrayBuffer. MSGPACK-STREAM-DEFAULT-SIZE)) 0))


