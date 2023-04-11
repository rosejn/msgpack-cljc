# msgpack-cljc

msgpack-cljc is a lightweight and simple library for converting
between native Clojure(script) data structures and MessagePack byte formats.
msgpack-cljc only depends on Clojure(script) itself; it has no third-party
dependencies.


### History

This library is the result of integrating and extending two previous msgpack libraries:

* [clojure-msgpack](https://github.com/edma2/clojure-msgpack)
* [msgpack-cljs](https://github.com/pkcsecurity/msgpack-cljs)


Their differing interfaces and approaches for extending msgpack with
additional types have been unified into a single cljc library, and support
for transmitting typed arrays has been added. The library is also tested
to be compatible with itself sending data in both directions between
Clojure and ClojureScript, and it has been used in production for
several years. After trying to coordinate with the original authors (no replies)
I decided to fork the projects and continue development here.

* https://github.com/edma2/clojure-msgpack/issues/29
* https://github.com/pkcsecurity/msgpack-cljs/issues/4

Thanks for the original work!


## Installation


[![Clojars Project](http://clojars.org/msgpack-cljc/latest-version.svg)](https://clojars.org/msgpack-cljc)
[![Build Status](https://travis-ci.org/edma2/msgpack-cljc.svg?branch=master)](https://travis-ci.org/edma2/msgpack-cljc)

## Usage


### Sente

This library works great with [Sente](https://github.com/ptaoussanis/sente) for sending data between Clojure and ClojureScript over ajax/websockets.

Use the `IPacker` protocol to connect msgpack to sente:

```clojure

(ns example.server
  (:require
    [msgpack.core :as msgpack]
    [msgpack.extensions]
    [msgpack.macros :refer [extend-msgpack]]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
    [taoensso.sente.interfaces :refer [IPacker]])
  (:import
    [java.time Instant]))

; Example of adding packing support for java.time.Instant
(extend-msgpack
  java.time.Instant
  100

  (pack
    [instant]
    (msgpack/pack (.toString instant)))

  (unpack
    [bytes]
    (java.time.Instant/parse (String. bytes))))

; Setup the sente packer
(deftype MsgPacker
  []
  IPacker
  (pack   [_ x]
    (msgpack/pack x))

  (unpack [_ s]
    (let [msg (msgpack/unpack s)]
      msg)))

(defn msgpack-packer
  []
  (MsgPacker.))


(let [ws-http-kit-adapter (get-sch-adapter)
      opts {:csrf-token-fn (fn [_] "special-csrf-token")
            :packer (msgpack-packer)
            :user-id-fn (fn [_] (util/uuid))}
      ws-server (sente/make-channel-socket-server! ws-http-kit-adapter opts)]
     ...)
```


And on the client side you do basically the same thing:


```clojure
(ns example.client
   [taoensso.sente :as sente]
   [taoensso.sente.interfaces :refer [IPacker]])

(deftype MsgPacker []
  IPacker
  (pack   [_ x] (msgpack/pack x))
  (unpack [_ s] (msgpack/unpack s)))


(defn msg-packer
  []
  (MsgPacker.))


(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/my-ws-endpoint" "special-csrf-token"
        {:type :ws
         :port 4444
         :packer (msg-packer)
         :ws-opts {:binary-type "arraybuffer"}})

```


### Basic
* `pack`: Serialize object as a sequence of java.lang.Bytes.
* `unpack` Deserialize bytes as a Clojure object.

```clojure
(require '[msgpack.core :as msg])
(require 'msgpack.clojure-extensions)

(msg/pack {:compact true :schema 0})
; => #<byte[] [B@60280b2e>

(msg/unpack (msg/pack {:compact true :schema 0}))
; => {:schema 0, :compact true}
```

### Streaming
`msgpack-cljc` provides a streaming API for situations where it is more
convenient or efficient to work with byte streams instead of fixed byte arrays
(e.g. size of object is not known ahead of time).

The streaming counterpart to `msgpack.core/pack` is `msgpack.core/pack-stream`
which returns nil and accepts either
[java.io.OutputStream](http://docs.oracle.com/javase/7/docs/api/java/io/OutputStream.html)
or
[java.io.DataOutput](http://docs.oracle.com/javase/7/docs/api/java/io/DataOutput.html)
as an additional argument.

`msgpack.core/unpack` is in "streaming mode" when the argument is of type
[java.io.DataInput](http://docs.oracle.com/javase/7/docs/api/java/io/DataInput.html)
or
[java.io.InputStream](http://docs.oracle.com/javase/7/docs/api/java/io/InputStream.html).

```clojure
(use 'clojure.java.io)

(with-open [s (output-stream "test.dat")]
  (msg/pack-stream {:compact true :schema 0} s))

(with-open [s (input-stream "test.dat")] (msg/unpack s))
; => {:schema 0, :compact true}
```

### Core types

Clojure			                | MessagePack
----------------------------|------------
nil			                    | Nil
java.lang.Boolean	          | Boolean
java.lang.Byte	            | Integer
java.lang.Short	            | Integer
java.lang.Integer	          | Integer
java.lang.Long	            | Integer
java.lang.BigInteger	      | Integer
clojure.lang.BigInt	        | Integer
java.lang.Float		          | Float
java.lang.Double	          | Float
java.math.BigDecimal	      | Float
java.lang.String	          | String
clojure.lang.Sequential	    | Array
clojure.lang.IPersistentMap | Map
msgpack.core.Ext	          | Extended

Serializing a value of unrecognized type will fail with `IllegalArgumentException`.  See [Application types](#application-types) if you want to register your own types.

### Clojure types
Some native Clojure types don't have an obvious MessagePack counterpart. We can
serialize them as Extended types. To enable automatic conversion of these
types, load the `clojure-extensions` library.

Clojure			    | MessagePack
----------------------------|------------
clojure.lang.Keyword	    | Extended (type = 3)
clojure.lang.Symbol	    | Extended (type = 4)
java.lang.Character	    | Extended (type = 5)
clojure.lang.Ratio	    | Extended (type = 6)
clojure.lang.IPersistentSet | Extended (type = 7)

With `msgpack.clojure-extensions`:
```clojure
(require 'msgpack.clojure-extensions)
(msg/pack :hello)
; => #<byte[] [B@a8c55bf>
```

Without `msgpack.clojure-extensions`:
```clojure
(msg/pack :hello)
; => IllegalArgumentException No implementation of method: :pack-stream of
; protocol: #'msgpack.core/Packable found for class: clojure.lang.Keyword
; clojure.core/-cache-protocol-fn (core _deftype.clj:544)
```

### <a name="application-types">Application types</a>
You can also define your own Extended types with `extend-msgpack`.

```clojure
(require '[msgpack.macros :refer [extend-msgpack]])

(defrecord Person [name])

(extend-msgpack
  Person
  100
  [p] (.getBytes (:name p))
  [bytes] (->Person (String. bytes)))

(msg/unpack (msg/pack [(->Person "bob") 5 "test"]))
; => (#user.Person{:name "bob"} 5 "test")
```

### Options
All pack and unpack functions take an optional map of options:
* `:compatibility-mode`
  Serialize/deserialize strings and bytes using the raw-type defined here:
  https://github.com/msgpack/msgpack/blob/master/spec-old.md

  Note: No error is thrown if an unpacked value is reserved under the old spec
  but defined under the new spec. We always deserialize something if we can
  regardless of `compatibility-mode`.

```clojure
(msg/pack (byte-array (byte 9)) {:compatibility-mode true})
```

## License
msgpack-cljc is MIT licensed. See the included LICENSE file for more details.


