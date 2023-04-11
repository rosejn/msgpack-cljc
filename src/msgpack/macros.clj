(ns msgpack.macros
  "Macros for extending MessagePack with Extended types.
  See msgpack.extensions for examples."
  (:require
    [msgpack.interface :refer [Packable pack-bytes ->Extended unpack-extended]]
    [msgpack.core :refer :all]))


(defmacro extend-msgpack
  [class type-num pack-form unpack-form]
  (let [[pack-fn pack-args pack] pack-form
        [unpack-fn unpack-args unpack] unpack-form]
  `(let [type# ~type-num]
     (assert (<= 0 type# 127)
             "[-1, -128]: reserved for future pre-defined extensions.")
     (do
       (extend-protocol Packable ~class
                        (pack-bytes [~@pack-args s#]
                          (pack-bytes (->Extended type# ~pack) s#)))

       (defmethod unpack-extended type# [ext#]
         (let [~@unpack-args (:data ext#)]
           ~unpack))))))


