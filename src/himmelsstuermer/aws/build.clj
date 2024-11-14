(ns himmelsstuermer.aws.build
  (:require
    [himmelsstuermer.aws.common :refer [stream-to-out]]
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
      (let [[src-path dst-path] (if (sequential? path)
                                  [(first path) (second path)]
                                  [path (if (fs/absolute? path) (fs/name path) path)])]
        (println "FILES" src-path hs-dir dst-path)
        (fs/copy src-path (fs/file hs-dir dst-path))))))


(defn- current-datetime
  []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-ss") (java.util.Date.)))


(defn- build-uberjar
  []
  (let [command ["lein" "uberjar"]
        builder (ProcessBuilder. ^java.util.List command)
        process (.start builder)]
    (stream-to-out (.getInputStream process))
    (stream-to-out (.getErrorStream process))
    (.waitFor process)))


(defn create-lambda-file
  [opts]
  (collect-files (:aux-files opts))
  (build-uberjar)
  (assoc opts :lambda-jar-file "./uberjar/lambda.jar"))


(defn clean
  "Deletes target and work directories"
  [{:keys [target-dir work-dir]}]
  (doseq [dir [target-dir work-dir]]
    (println "Removing directory:" dir)
    (fs/delete-dir dir)))
