(ns himmelsstuermer.aws.build
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [me.raynes.fs :as fs])
  (:import
    (java.lang
      ProcessBuilder)))


(def ^:private files-dir "./.himmelsstuermer.build")


(defn collect-files
  [paths]
  (fs/delete-dir files-dir)
  (fs/mkdir files-dir)
  (let [hs-dir (fs/file files-dir)]
    (doseq [path paths]
      (let [[src-path dst-path] (if (seqable? path)
                                  [(first path) (second path)]
                                  [path (if (fs/absolute? path) (fs/name path) path)])]
        (fs/copy src-path (fs/file hs-dir dst-path))))))


(defn- current-datetime
  []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-ss") (java.util.Date.)))


(defn stream-to-out
  [input-stream]
  (let [reader (java.io.BufferedReader. (java.io.InputStreamReader. input-stream))]
    (future
      (loop []
        (when-let [line (.readLine reader)]
          (println line)
          (recur))))))


(defn dockerfile-to-temp
  []
  (let [dockerfile-content (slurp (io/resource "Dockerfile.himmelsstuermer.build"))
        temp-file (fs/temp-file "Dockerfile" ".tmp")]
    (spit temp-file dockerfile-content)
    (str temp-file)))


(defn build-image
  [opts]
  (collect-files (:aux-files opts))
  (let [image-name (format "himmelsstuermer-%s-%s"
                           (:cluster opts)
                           (:lambda-name opts))
        image-tag  (current-datetime)
        tag        (format "%s:%s" image-name image-tag)
        packages   (format "AUX_PACKAGES=%s" (str/join " " (map str (:aux-packages opts))))
        arch       (format "TARGET_ARCH=%s" (:arch opts))
        arch-2     (format "TARGET_ARCH_2=%s" (if (= "aarch64" (:arch opts)) "arm64" (:arch opts)))
        command ["docker" "build"
                 "-t" tag
                 "-f" (dockerfile-to-temp)
                 "--build-arg" packages
                 "--build-arg" arch
                 "--build-arg" arch-2
                 "--progress=plain"
                 "."]
        builder (ProcessBuilder. command)
        _ (.put (.environment builder) "DOCKER_BUILDKIT" "1")
        process (.start builder)]
    (stream-to-out (.getInputStream process))
    (stream-to-out (.getErrorStream process))
    {:exit-code  (.waitFor process)
     :image-name tag}))


(defn clean
  "Deletes target and work directories"
  [{:keys [target-dir work-dir]}]
  (doseq [dir [target-dir work-dir]]
    (println "Removing directory:" dir)
    (fs/delete-dir dir)))