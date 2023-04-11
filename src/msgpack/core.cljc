(ns msgpack.core
  (:require [msgpack.pack :as pack]))


(defn pack
  [obj]
  (pack/pack obj))


(defn pack-stream
  [obj stream]
  (pack/pack-stream obj stream))


(defn unpack
  [bytes]
  (pack/unpack bytes))


(defn unpack-stream
  [in-stream]
   (pack/unpack-stream in-stream))


(defn hex
  "Convert byte sequence to hex string"
  [coll]
  (let [hex [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \a \b \c \d \e \f]]
      (letfn [(hexify-byte [b]
        (let [v (bit-and b 0xFF)]
          [(hex (bit-shift-right v 4)) (hex (bit-and v 0x0F))]))]
        (apply str (interleave
                     (mapcat hexify-byte coll)
                     (repeat " "))))))


