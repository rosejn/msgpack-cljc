(ns msgpack.pack
  (:require
    [msgpack.interface :refer [Packable pack-bytes Extended unpack-extended]]
    [msgpack.stream :as stream]
    [cljs.reader :refer [parse-timestamp]]))


(defn pack-byte-array
  [stream bytes]
  (let [n (.-byteLength bytes)]
    (cond
      (<= n 0xff) (doto stream (stream/write-u8 0xc4) (stream/write-u8 n) (stream/write bytes))
      (<= n 0xffff) (doto stream (stream/write-u8 0xc5) (stream/write-u16 n) (stream/write bytes))
      (<= n 0xffffffff) (doto stream (stream/write-u8 0xc6) (stream/write-u32 n) (stream/write bytes))
      :else (throw (js/Error. "bytes too large to pack")))))


; we will support doubles only
(defn pack-float
  [stream f]
  (doto stream (stream/write-u8 0xcb) (stream/write-f64 f)))


(defn pack-int
  [stream i]
  (cond
    ; +fixnum
    (<= 0 i 127) (stream/write-u8 stream i)
    ; -fixnum
    (<= -32 i -1) (stream/write-i8 stream i)

    ; uint 8
    (<= 0 i 0xff) (doto stream (stream/write-u8 0xcc) (stream/write-u8 i))
    ; uint 16
    (<= 0 i 0xffff) (doto stream (stream/write-u8 0xcd) (stream/write-u16 i))
    ; uint 32
    (<= 0 i 0xffffffff) (doto stream (stream/write-u8 0xce) (stream/write-u32 i))
    ; uint 64
    (<= 0 i 0xffffffffffffffff) (doto stream (stream/write-u8 0xcf) (stream/write-i64 i))

    ; int 8
    (<= -0x80 i -1) (doto stream (stream/write-u8 0xd0) (stream/write-i8 i))
    ; int 16
    (<= -0x8000 i -1) (doto stream (stream/write-u8 0xd1) (stream/write-i16 i))
    ; int 32
    (<= -0x80000000 i -1) (doto stream (stream/write-u8 0xd2) (stream/write-i32 i))
    ; int 64
    (<= -0x8000000000000000 i -1) (doto stream (stream/write-u8 0xd3) (stream/write-i64 i))

    :else (throw (js/Error. (str "Integer value out of bounds: " i)))))


(defn pack-number
  [stream n]
  (if-not (integer? n)
    (pack-float stream n)
    (pack-int stream n)))


(defn pack-string
  [stream s]
  (let [bytes (stream/string->bytes s)
        len (.-byteLength bytes)]
    (cond
      (<= len 0x1f) (doto stream (stream/write-u8 (bit-or 2r10100000 len)) (stream/write bytes))
      (<= len 0xff) (doto stream (stream/write-u8 0xd9) (stream/write-u8 len) (stream/write bytes))
      (<= len 0xffff) (doto stream (stream/write-u8 0xda) (stream/write-u16 len) (stream/write bytes))
      (<= len 0xffffffff) (doto stream (stream/write-u8 0xdb) (stream/write-u32 len) (stream/write bytes))
      :else (throw (js/Error. "string too large to pack")))))


(declare pack)


(defn pack-coll
  [stream coll]
  (doseq [x coll]
    (pack-bytes x stream)))


(defprotocol IExtendable
  (extension [this]))


(extend-protocol IExtendable
  PersistentHashSet
  (extension [this]
    (Extended. 0x07 (pack (vec this))))

  Keyword
  (extension [this]
    (Extended. 0x03 (pack (.substring (str this) 1) (stream/output-stream))))

  cljs.core.Symbol
  (extension [this]
    (Extended. 0x04 (pack (str this)))))


(defn pack-extended
  [s {:keys [type data]}]
  (let [len (.-byteLength data)]
    (case len
      1 (stream/write-u8 s 0xd4)
      2 (stream/write-u8 s 0xd5)
      4 (stream/write-u8 s 0xd6)
      8 (stream/write-u8 s 0xd7)
      16 (stream/write-u8 s 0xd8)
      (cond
        (<= len 0xff) (doto s (stream/write-u8 0xc7) (stream/write-u8 len))
        (<= len 0xffff) (doto s (stream/write-u8 0xc8) (stream/write-u16 len))
        (<= len 0xffffffff) (doto s (stream/write-u8 0xc9) (stream/write-u32 len))
        :else (throw (js/Error. "extended type too large to pack"))))
    (stream/write-u8 s type)
    (stream/write s data)))


(defn pack-seq
  [s seq]
  (let [len (count seq)]
    (cond
      (<= len 0xf) (doto s (stream/write-u8 (bit-or 2r10010000 len)) (pack-coll seq))
      (<= len 0xffff) (doto s (stream/write-u8 0xdc) (stream/write-u16 len) (pack-coll seq))
      (<= len 0xffffffff) (doto s (stream/write-u8 0xdd) (stream/write-u32 len) (pack-coll seq))
      :else (throw (js/Error. "seq type too large to pack")))))


(defn pack-map
  [s map]
  (let [len (count map)
        pairs (interleave (keys map) (vals map))]
    (cond
      (<= len 0xf) (doto s (stream/write-u8 (bit-or 2r10000000 len)) (pack-coll pairs))
      (<= len 0xffff) (doto s (stream/write-u8 0xde) (stream/write-u16 len) (pack-coll pairs))
      (<= len 0xffffffff) (doto s (stream/write-u8 0xdf) (stream/write-u32 len) (pack-coll pairs))
      :else (throw (js/Error. "map type too large to pack")))))


(extend-protocol Packable
  nil
  (pack-bytes [_ s] (stream/write-u8 s 0xc0))

  boolean
  (pack-bytes [bool s] (stream/write-u8 s (if bool 0xc3 0xc2)))

  number
  (pack-bytes [n s] (pack-number s n))

  string
  (pack-bytes [str s] (pack-string s str))

  Extended
  (pack-bytes [ext s] (pack-extended s ext))

  PersistentVector
  (pack-bytes [seq s] (pack-seq s seq))

  EmptyList
  (pack-bytes [seq s] (pack-seq s seq))

  List
  (pack-bytes [seq s] (pack-seq s seq))

  LazySeq
  (pack-bytes [seq s] (pack-seq s (vec seq)))

  js/Uint8Array
  (pack-bytes [u8 s] (pack-byte-array s u8))

  js/Int32Array
  (pack-bytes [ary s]
    (pack-extended s {:type 101 :data (.-buffer ary)}))

  js/Float32Array
  (pack-bytes [ary s]
    (pack-extended s {:type 102 :data (.-buffer ary)}))

  js/Float64Array
  (pack-bytes [ary s]
    (pack-extended s {:type 103 :data (.-buffer ary)}))

  js/Date
  (pack-bytes [d s]
    (pack-string s (.toISOString d)))

  PersistentArrayMap
  (pack-bytes [array-map s]
    (pack-map s array-map))

  PersistentHashMap
  (pack-bytes [hmap s]
    (pack-map s hmap))

  PersistentHashSet
  (pack-bytes [hset s]
    (pack-bytes (extension hset) s))

  Keyword
  (pack-bytes [kw s]
    (pack-bytes (extension kw) s))

  Symbol
  (pack-bytes [sym s]
    (pack-bytes (extension sym) s)))


(declare unpack-stream)

(defn unpack-n
  [stream n]
  (let [v (transient [])]
    (dotimes [_ n]
      (conj! v (unpack-stream stream)))
    (persistent! v)))


(defn unpack-map
  [stream n]
  (apply hash-map (unpack-n stream (* 2 n))))


(declare unpack-ext)


(defn unpack-stream
  [stream]
  (let [byte (stream/read-u8 stream)]
    (case byte
      0xc0 nil
      0xc2 false
      0xc3 true
      0xc4 (stream/read-bytes stream (stream/read-u8 stream))
      0xc5 (stream/read-bytes stream (stream/read-u16 stream))
      0xc6 (stream/read-bytes stream (stream/read-u32 stream))
      0xc7 (unpack-ext stream (stream/read-u8 stream))
      0xc8 (unpack-ext stream (stream/read-u16 stream))
      0xc9 (unpack-ext stream (stream/read-u32 stream))
      0xca (stream/read-f32 stream)
      0xcb (stream/read-f64 stream)
      0xcc (stream/read-u8 stream)
      0xcd (stream/read-u16 stream)
      0xce (stream/read-u32 stream)
      0xcf (stream/read-i64 stream)
      0xd0 (stream/read-i8 stream)
      0xd1 (stream/read-i16 stream)
      0xd2 (stream/read-i32 stream)
      0xd3 (stream/read-i64 stream)
      0xd4 (unpack-ext stream 1)
      0xd5 (unpack-ext stream 2)
      0xd6 (unpack-ext stream 4)
      0xd7 (unpack-ext stream 8)
      0xd8 (unpack-ext stream 16)
      0xd9 (stream/read-str stream (stream/read-u8 stream))
      0xda (stream/read-str stream (stream/read-u16 stream))
      0xdb (stream/read-str stream (stream/read-u32 stream))
      0xdc (unpack-n stream (stream/read-u16 stream))
      0xdd (unpack-n stream (stream/read-u32 stream))
      0xde (unpack-map stream (stream/read-u16 stream))
      0xdf (unpack-map stream (stream/read-u32 stream))
      (cond
        (= (bit-and 2r11100000 byte) 2r11100000) byte
        (= (bit-and 2r10000000 byte) 0) byte
        (= (bit-and 2r11100000 byte) 2r10100000) (stream/read-str stream (bit-and 2r11111 byte))
        (= (bit-and 2r11110000 byte) 2r10010000) (unpack-n stream (bit-and 2r1111 byte))
        (= (bit-and 2r11110000 byte) 2r10000000) (unpack-map stream (bit-and 2r1111 byte))
        :else (throw (js/Error. "invalid msgpack stream"))))))


(defn keyword-deserializer
	[bytes]
  (keyword
    (unpack-stream
      (stream/input-stream bytes))))


(defn symbol-deserializer
	[bytes]
  (symbol
    (unpack-stream
      (stream/input-stream bytes))))


(defn char-deserializer
	[bytes]
  (unpack-stream
    (stream/input-stream bytes)))


(defn ratio-deserializer
	[bytes]
  (let [[n d] (unpack-stream (stream/input-stream bytes))]
    (/ n d)))


(defn set-deserializer
	[bytes]
  (set (unpack-stream (stream/input-stream bytes))))


(defn date-deserializer
	[bytes]
  (let [date-str (unpack-stream (stream/input-stream bytes))]
    (parse-timestamp date-str)))


(defn int-array-deserializer
  [buffer]
  (js/Int32Array. buffer))


(defn float-array-deserializer
  [buffer]
  (js/Float32Array. buffer))


(defn double-array-deserializer
  [buffer]
  (js/Float64Array. buffer))


(defn byte-array-deserializer
  [buffer]
  (js/Uint8Array. buffer))


(defn unpack-ext
	[stream n]
  (let [type (stream/read-u8 stream)]
    (case type
      3 (keyword-deserializer (stream/read stream n))
      4 (symbol-deserializer (stream/read stream n))
      5 (char-deserializer (stream/read stream n))
      6 (ratio-deserializer (stream/read stream n))
      7 (set-deserializer (stream/read stream n))
      100 (date-deserializer (stream/read stream n))
      101 (int-array-deserializer (stream/read stream n))
      102 (float-array-deserializer (stream/read stream n))
      103 (double-array-deserializer (stream/read stream n))
      104 (byte-array-deserializer (stream/read stream n)))))


(defn unpack
  [buffer]
  (unpack-stream
    (stream/input-stream buffer)))


(defn pack
	[data]
  (let [stream (stream/output-stream)]
    (pack-bytes data stream)
    (stream/stream->uint8array stream)))


(defn pack-stream
  [data stream]
  (pack-bytes data stream))


