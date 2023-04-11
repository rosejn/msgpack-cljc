(ns msgpack.interface)

(defprotocol Packable
  "Pack this application data type into bytes on an output stream."
  (pack-bytes [obj out-stream]))

;; MessagePack allows applications to define application-specific types using
;; the Extended type. Extended type consists of an integer and a byte array
;; where the integer identifies the type and the byte array host serialized
;; data.
(defrecord Extended [type data])

(defmulti unpack-extended
  "Refine Extended type to an application-specific type."
  :type)

; By default we dispatch on the extension byte
(defmethod unpack-extended :default [ext] ext)

