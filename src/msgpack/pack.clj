(ns msgpack.pack
  (:require [msgpack.interface :refer [Packable pack-bytes unpack-extended ->Extended]])
  (:import
    [msgpack.interface Extended]
    java.io.ByteArrayInputStream
    java.io.ByteArrayOutputStream
    java.io.DataInput
    java.io.DataInputStream
    java.io.DataOutput
    java.io.DataOutputStream
    java.io.InputStream
    java.io.OutputStream
    java.nio.charset.Charset))

(def ^:private ^Charset MSGPACK-CHARSET (Charset/forName "UTF-8"))

(defmacro cond-let
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & clauses]
  `(let ~bindings (cond ~@clauses)))


(defn- pack-raw
  [^bytes bytes ^DataOutput s]
  (cond-let [len (count bytes)]
            (<= len 0x1f)
            (do (.writeByte s (bit-or 2r10100000 len)) (.write s bytes))

            (<= len 0xffff)
            (do (.writeByte s 0xda) (.writeShort s len) (.write s bytes))

            (<= len 0xffffffff)
            (do (.writeByte s 0xdb) (.writeInt s len) (.write s bytes))))


(defn- pack-str
  [^bytes bytes ^DataOutput s]
  (cond-let [len (count bytes)]
            (<= len 0x1f)
            (do (.writeByte s (bit-or 2r10100000 len)) (.write s bytes))

            (<= len 0xff)
            (do (.writeByte s 0xd9) (.writeByte s len) (.write s bytes))

            (<= len 0xffff)
            (do (.writeByte s 0xda) (.writeShort s len) (.write s bytes))

            (<= len 0xffffffff)
            (do (.writeByte s 0xdb) (.writeInt s len) (.write s bytes))))


(defn- pack-byte-array
  [^bytes bytes ^DataOutput s]
  (cond-let [len (count bytes)]
            (<= len 0xff)
            (do (.writeByte s 0xc4) (.writeByte s len) (.write s bytes))

            (<= len 0xffff)
            (do (.writeByte s 0xc5) (.writeShort s len) (.write s bytes))

            (<= len 0xffffffff)
            (do (.writeByte s 0xc6) (.writeInt s len) (.write s bytes))))


(defn- pack-int
  "Pack integer using the most compact representation"
  [n ^DataOutput s]
  (cond
    ; +fixnum
    (<= 0 n 127)                  (.writeByte s n)
    ; -fixnum
    (<= -32 n -1)                 (.writeByte s n)
    ; uint 8
    (<= 0 n 0xff)                 (do (.writeByte s 0xcc) (.writeByte s n))
    ; uint 16
    (<= 0 n 0xffff)               (do (.writeByte s 0xcd) (.writeShort s n))
    ; uint 32
    (<= 0 n 0xffffffff)           (do (.writeByte s 0xce) (.writeInt s (unchecked-int n)))
    ; uint 64
    (<= 0 n 0xffffffffffffffff)   (do (.writeByte s 0xcf) (.writeLong s (unchecked-long n)))
    ; int 8
    (<= -0x80 n -1)               (do (.writeByte s 0xd0) (.writeByte s n))
    ; int 16
    (<= -0x8000 n -1)             (do (.writeByte s 0xd1) (.writeShort s n))
    ; int 32
    (<= -0x80000000 n -1)         (do (.writeByte s 0xd2) (.writeInt s n))
    ; int 64
    (<= -0x8000000000000000 n -1) (do (.writeByte s 0xd3) (.writeLong s n))
    :else (throw (IllegalArgumentException. (str "Integer value out of bounds: " n)))))


(defn- pack-coll
  [coll ^DataOutput s]
  (doseq [item coll]
    (pack-bytes item s)))


(def ^:private CLASS-OF-BYTE-ARRAY
  (class (java.lang.reflect.Array/newInstance Byte 0)))

(def ^:private CLASS-OF-PRIMITIVE-BYTE-ARRAY
  (Class/forName "[B"))


; Array of java.lang.Byte (boxed)
(extend CLASS-OF-BYTE-ARRAY
  Packable
  {:pack-bytes
   (fn [a ^DataOutput s]
     (pack-bytes (byte-array a) s))})


(extend CLASS-OF-PRIMITIVE-BYTE-ARRAY
  Packable
  {:pack-bytes
   (fn [bytes ^DataOutput s]
     (pack-byte-array bytes s))})


(extend-protocol Packable
  nil
  (pack-bytes
    [_ ^DataOutput s]
    (.writeByte s 0xc0))

  java.lang.Boolean
  (pack-bytes
    [bool ^DataOutput s]
    (if bool
      (.writeByte s 0xc3)
      (.writeByte s 0xc2)))

  java.lang.Byte
  (pack-bytes [n ^DataOutput s] (pack-int n s))

  java.lang.Short
  (pack-bytes [n ^DataOutput s] (pack-int n s))

  java.lang.Integer
  (pack-bytes [n ^DataOutput s] (pack-int n s))

  java.lang.Long
  (pack-bytes [n ^DataOutput s] (pack-int n s))

  java.math.BigInteger
  (pack-bytes [n ^DataOutput s] (pack-int n s))

  clojure.lang.BigInt
  (pack-bytes [n ^DataOutput s] (pack-int n s))

  java.lang.Float
  (pack-bytes [f ^DataOutput s]
    (do (.writeByte s 0xca) (.writeFloat s f)))

  java.lang.Double
  (pack-bytes [d ^DataOutput s]
    (do (.writeByte s 0xcb) (.writeDouble s d)))

  java.math.BigDecimal
  (pack-bytes [d ^DataOutput s]
    (pack-bytes (.doubleValue d) s))

  java.lang.String
  (pack-bytes
    [str ^DataOutput s]
    (let [bytes (.getBytes ^String str MSGPACK-CHARSET)]
      (pack-str bytes s)))

  Extended
  (pack-bytes
    [e ^DataOutput s]
    (let [type (:type e)
          ^bytes data (:data e)
          len (count data)]
      (cond
        (= len 1) (.writeByte s 0xd4)
        (= len 2) (.writeByte s 0xd5)
        (= len 4) (.writeByte s 0xd6)
        (= len 8) (.writeByte s 0xd7)
        (= len 16) (.writeByte s 0xd8)
        (<= len 0xff) (do (.writeByte s 0xc7) (.writeByte s len))
        (<= len 0xffff) (do (.writeByte s 0xc8) (.writeShort s len))
        (<= len 0xffffffff) (do (.writeByte s 0xc9) (.writeInt s len)))
      (.writeByte s type)
      (.write s data)))

  clojure.lang.Sequential
  (pack-bytes [seq ^DataOutput s]
    (cond-let [len (count seq)]
              (<= len 0xf)
              (do (.writeByte s (bit-or 2r10010000 len)) (pack-coll seq s))

              (<= len 0xffff)
              (do (.writeByte s 0xdc) (.writeShort s len) (pack-coll seq s))

              (<= len 0xffffffff)
              (do (.writeByte s 0xdd) (.writeInt s len) (pack-coll seq s))))

  clojure.lang.IPersistentMap
  (pack-bytes [map ^DataOutput s]
    (cond-let [len (count map)
               pairs (interleave (keys map) (vals map))]
              (<= len 0xf)
              (do (.writeByte s (bit-or 2r10000000 len)) (pack-coll pairs s))

              (<= len 0xffff)
              (do (.writeByte s 0xde) (.writeShort s len) (pack-coll pairs s))

              (<= len 0xffffffff)
              (do (.writeByte s 0xdf) (.writeInt s len) (pack-coll pairs s)))))

; Note: the extensions below are not in extend-protocol above because of
; a Clojure bug. See http://dev.clojure.org/jira/browse/CLJ-1381

(defn- read-uint8
  [^DataInput data-input]
  (.readUnsignedByte data-input))

(defn- read-uint16
  [^DataInput data-input]
  (.readUnsignedShort data-input))

(defn- read-uint32
  [^DataInput data-input]
  (bit-and 0xffffffff (.readInt data-input)))

(defn- read-uint64
  [^DataInput data-input]
  (let [n (.readLong data-input)]
    (if (<= 0 n Long/MAX_VALUE)
      n
      (.and (biginteger n) (biginteger 0xffffffffffffffff)))))

(defn- read-bytes
  [n ^DataInput data-input]
  (let [bytes (byte-array n)]
    (do
      (.readFully data-input bytes)
      bytes)))

(defn- read-str
  [n ^DataInput data-input]
  (let [bytes (read-bytes n data-input)]
    (String. ^bytes bytes MSGPACK-CHARSET)))

(declare unpack-stream)

(defn- unpack-ext [n ^DataInput data-input]
  (unpack-extended
   (->Extended (.readByte data-input) (read-bytes n data-input))))

(defn- unpack-n [n ^DataInput data-input]
  (loop [i 0
         v (transient [])]
    (if (< i n)
      (recur
        (unchecked-inc i)
        (conj! v (unpack-stream data-input)))
      (persistent! v))))

(defn- unpack-map [n ^DataInput data-input]
  (loop [i 0
         m (transient {})]
    (if (< i n)
      (recur
        (unchecked-inc i)
        (assoc! m
          (unpack-stream data-input)
          (unpack-stream data-input)))
      (persistent! m))))

(defn unpack-stream
  [^DataInput data-input]
  (cond-let [byte (.readUnsignedByte data-input)]
            ; nil format family
            (= byte 0xc0) nil

            ; bool format family
            (= byte 0xc2) false
            (= byte 0xc3) true

            ; int format family
            (= (bit-and 2r11100000 byte) 2r11100000)
            (unchecked-byte byte)

            (= (bit-and 2r10000000 byte) 0)
            (unchecked-byte byte)

            (= byte 0xcc) (read-uint8 data-input)
            (= byte 0xcd) (read-uint16 data-input)
            (= byte 0xce) (read-uint32 data-input)
            (= byte 0xcf) (read-uint64 data-input)
            (= byte 0xd0) (.readByte data-input)
            (= byte 0xd1) (.readShort data-input)
            (= byte 0xd2) (.readInt data-input)
            (= byte 0xd3) (.readLong data-input)

            ; float format family
            (= byte 0xca) (.readFloat data-input)
            (= byte 0xcb) (.readDouble data-input)

            ; str format family
            (= (bit-and 2r11100000 byte) 2r10100000)
            (let [n (bit-and 2r11111 byte)]
              (read-str n data-input))

            (= byte 0xd9)
            (read-str (read-uint8 data-input) data-input)

            (= byte 0xda)
            (read-str (read-uint16 data-input) data-input)

            (= byte 0xdb)
            (read-str (read-uint32 data-input) data-input)

            ; bin format family
            (= byte 0xc4)
            (read-bytes (read-uint8 data-input) data-input)

            (= byte 0xc5)
            (read-bytes (read-uint16 data-input) data-input)

            (= byte 0xc6)
            (read-bytes (read-uint32 data-input) data-input)

            ; ext format family
            (= byte 0xd4) (unpack-ext 1 data-input)
            (= byte 0xd5) (unpack-ext 2 data-input)
            (= byte 0xd6) (unpack-ext 4 data-input)
            (= byte 0xd7) (unpack-ext 8 data-input)
            (= byte 0xd8) (unpack-ext 16 data-input)

            (= byte 0xc7)
            (unpack-ext (read-uint8 data-input) data-input)

            (= byte 0xc8)
            (unpack-ext (read-uint16 data-input) data-input)

            (= byte 0xc9)
            (unpack-ext (read-uint32 data-input) data-input)

            ; array format family
            (= (bit-and 2r11110000 byte) 2r10010000)
            (unpack-n (bit-and 2r1111 byte) data-input)

            (= byte 0xdc)
            (unpack-n (read-uint16 data-input) data-input)

            (= byte 0xdd)
            (unpack-n (read-uint32 data-input) data-input)

            ; map format family
            (= (bit-and 2r11110000 byte) 2r10000000)
            (unpack-map (bit-and 2r1111 byte) data-input)

            (= byte 0xde)
            (unpack-map (read-uint16 data-input) data-input)

            (= byte 0xdf)
            (unpack-map (read-uint32 data-input) data-input)))


; Array of java.lang.Byte (boxed)
(extend CLASS-OF-BYTE-ARRAY
  Packable
  {:pack-bytes
   (fn [a ^DataOutput s]
     (pack-bytes (byte-array a) s))})


(extend CLASS-OF-PRIMITIVE-BYTE-ARRAY
  Packable
  {:pack-bytes
   (fn [bytes ^DataOutput s]
     (pack-byte-array bytes s))})



(defn pack-stream
  "Serialize application value as a stream of MessagePack-formatted bytes.
  Argument type can be either java.io.DataOutput or java.io.OutputStream. Any
  other type is an error."
  [obj stream]
  (condp instance? stream
    DataOutput (pack-bytes obj stream)
    OutputStream (pack-stream obj (DataOutputStream. stream))))


(defn pack
  "Serialize application value as a MessagePack-formatted byte array"
  [obj]
  (let [stream (ByteArrayOutputStream.)]
    (pack-stream obj stream)
    (.toByteArray stream)))


(defn unpack
  "Deserialize MessagePack-formatted bytes to an application value. Argument
  type can be either java.io.DataInput, java.io.InputStream, or byte array.
  Other types are coerced to a byte array."
  [obj]
  (condp instance? obj
    DataInput (unpack-stream obj)
    InputStream (unpack (DataInputStream. obj))
    CLASS-OF-PRIMITIVE-BYTE-ARRAY (unpack (ByteArrayInputStream. obj))
    (unpack (byte-array obj))))
