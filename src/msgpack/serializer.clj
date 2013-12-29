(ns msgpack.serializer
  (:require [msgpack.utils :refer :all]))

(defmulti serialize class)

(defmethod serialize nil
  [_] (byte-literals [0xc0]))

(defmethod serialize Boolean
  [bool]
  (if bool (byte-literals [0xc3])
    (byte-literals [0xc2])))

(defn- int-bytes
  [x n]
  (if (= 0 n) []
  (conj (int-bytes (bit-shift-right x 8) (dec n))
        (byte-literal (bit-and 0xff x)))))

; TODO: 64-bit int/uint
(defmethod serialize Long
  [x]
  (cond
    (<= 0 x 127) (byte-literals [x])
    (<= -32 x -1) (byte-literals [x])
    (<= 0 x 0xff) (byte-literals [0xcc x])
    (<= 0 x 0xffff) (cons (byte-literal 0xcd) (int-bytes x 2))
    (<= 0 x 0xffffffff) (cons (byte-literal 0xce) (int-bytes x 4))
    (<= -0x80 x 0x7f) (byte-literals [0xd0 x])
    (<= -0x8000 x 0x7fff) (cons (byte-literal 0xd1) (int-bytes x 2))
    (<= -0x80000000 x 0x7fffffff) (cons (byte-literal 0xd2) (int-bytes x 4))))
