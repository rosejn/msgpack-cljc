(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))


(def lib 'com.github.rosejn/msgpack-cljc)
(def version (format "2.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))


(defn clean [_]
  (b/delete {:path "target"}))


(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :scm {:url "https://github.com/rosejn/msgpack-cljc"}
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))


(defn install [_]
  (b/install {:basis      basis
              :lib        lib
              :version    version
              :jar-file   jar-file
              :class-dir  class-dir}))


(defn deploy [_]
  (dd/deploy {:installer :remote
              :sign-releases? false
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib
                                     :class-dir class-dir})}))
