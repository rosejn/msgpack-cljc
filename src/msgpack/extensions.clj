(ns msgpack.extensions
  "Extended types for JVM Clojure-specific types"
  (:require
    [msgpack.core :as msg]
    [msgpack.macros :refer [extend-msgpack]])
  (:import
    [java.nio ByteBuffer ByteOrder]))


(defn- keyword->str
  "Convert keyword to string with namespace preserved.
  Example: :A/A => \"A/A\""
  [k]
  (subs (str k) 1))

(extend-msgpack
  clojure.lang.Keyword 3

  (pack
    [k]
    (msg/pack (keyword->str k)))

  (unpack
    [bytes]
    (keyword (msg/unpack bytes))))


(extend-msgpack
  clojure.lang.Symbol 4
  (pack
    [s]
    (msg/pack (str s)))

  (unpack
    [bytes]
    (symbol (msg/unpack bytes))))


(extend-msgpack
 java.lang.Character 5

 (pack
   [c]
   (msg/pack (str c)))

 (unpack
   [bytes]
   (first (char-array (msg/unpack bytes)))))


(extend-msgpack
  clojure.lang.Ratio 6

  (pack
    [r]
    (msg/pack [(numerator r) (denominator r)]))

  (unpack
    [bytes]
    (let [[n d] (msg/unpack bytes)]
      (/ n d))))


(extend-msgpack
  clojure.lang.IPersistentSet 7

  (pack
    [s]
    (msg/pack (seq s)))

  (unpack
    [bytes]
    (set (msg/unpack bytes))))


(extend-msgpack
  (class (int-array 0)) 101

  (pack
    [ary]
    (let [buf (ByteBuffer/allocate (* 4 (count ary)))]
      (.order buf (ByteOrder/nativeOrder))
      (doseq [v ary]
        (.putInt buf v))
      (.array buf)))

  (unpack
    [bytes]
    (let [buf (ByteBuffer/wrap bytes)
          _ (.order buf (ByteOrder/nativeOrder))
          int-buf (.asIntBuffer buf)
          int-ary (int-array (.limit int-buf))]
      (.get int-buf int-ary)
      int-ary)))


; Add support for float arrays
(extend-msgpack
  (class (float-array 0)) 102

  (pack
    [ary]
    (let [buf (ByteBuffer/allocate (* 4 (count ary)))]
      (.order buf (ByteOrder/nativeOrder))
      (doseq [f ary]
        (.putFloat buf f))
      (.array buf)))

  (unpack
    [bytes]
    (let [buf (ByteBuffer/wrap bytes)
          _ (.order buf (ByteOrder/nativeOrder))
          float-buf (.asFloatBuffer buf)
          float-ary (float-array (.limit float-buf))]
      (.get float-buf float-ary)
      float-ary)))


(extend-msgpack
  (class (double-array 0)) 103

  (pack
    [ary]
    (let [buf (ByteBuffer/allocate (* 8 (count ary)))]
      (.order buf (ByteOrder/nativeOrder))
      (doseq [v ary]
        (.putDouble buf v))
      (.array buf)))

  (unpack
    [bytes]
    (let [buf (ByteBuffer/wrap bytes)
          _ (.order buf (ByteOrder/nativeOrder))
          double-buf (.asDoubleBuffer buf)
          double-ary (double-array (.limit double-buf))]
      (.get double-buf double-ary)
      double-ary)))

