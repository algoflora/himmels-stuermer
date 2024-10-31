(ns build
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.build.api :as b]))


(def lib 'io.github.algoflora/himmelsstuermer)
(def version (format "0.1.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s.jar" (name lib)))


;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))


(defn clean
  [_]
  (b/delete {:path "target"}))


(defn find-namespaces
  [src-dir]
  (let [nss (->> (file-seq (io/file src-dir))
                 (filter #(and (.isFile %) (.endsWith (.getName %) ".clj")))
                 (map #(-> (.getPath %)
                           (str/replace (str src-dir "/") "")
                           (str/replace #"\.clj$" "")
                           (str/replace "/" ".")
                           symbol)))]
    (println "Namespaces found: " nss) nss))


(defn jar
  [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :ns-compile (find-namespaces "./src")
          :jar-file jar-file}))


#_(defn uberjar
  [_]
  (aot-compile nil)
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'himmelsstuermer.core}))


(defn uberjar
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile (find-namespaces "./src")
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'himmelsstuermer.core}))


(defn install
  [_]
  ;; (jar nil)
  (uberjar nil)
  (b/install {:basis @basis
              :class-dir class-dir
              :lib lib
              :version version
              :jar-file uber-file})
  (println (str lib ":" version)))
