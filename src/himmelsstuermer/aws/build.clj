(ns himmelsstuermer.aws.build
  (:require
    ;; [clojure.java.io :as io]
    ;; [clojure.string :as str]
    [himmelsstuermer.aws.common :refer [stream-to-out]]
    ;; [himmelsstuermer.misc :refer [project-info]]
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
        builder (ProcessBuilder. command)
        process (.start builder)]
    (stream-to-out (.getInputStream process))
    (stream-to-out (.getErrorStream process))
    (.waitFor process)))


(defn- zip-files
  []
  (let [lambda-zip-file (format "./lambda-%s.zip" (current-datetime))
        command ["zip" "-r" (str "../target/" lambda-zip-file) "." "-i" "*"]
        builder (ProcessBuilder. command)
        _ (.directory builder (fs/file files-dir))
        process (.start builder)]
    (stream-to-out (.getInputStream process))
    (stream-to-out (.getErrorStream process))
    (.waitFor process)
    lambda-zip-file))


(defn create-lambda-file
  [opts]
  (collect-files (:aux-files opts))
  (build-uberjar)
  ;; (fs/copy "./target/uberjar/lambda.jar" (fs/file (fs/file files-dir) "lambda.jar"))
  (assoc opts :lambda-jar-file "./uberjar/lambda.jar" #_(zip-files)))


;; (defn dockerfile-to-temp
;;   []
;;   (let [dockerfile-content (slurp (io/resource "Dockerfile.himmelsstuermer.build"))
;;         temp-file (fs/temp-file "Dockerfile" ".tmp")]
;;     (spit temp-file dockerfile-content)
;;     (str temp-file)))


;; (defn build-image
;;   [opts]
;;   (collect-files (:aux-files opts))
;;   (let [image-name (format "himmelsstuermer-%s-%s"
;;                            (:cluster opts)
;;                            (:lambda-name opts))
;;         image-tag  (current-datetime)
;;         packages   (format "AUX_PACKAGES=%s" (str/join " " (map str (:aux-packages opts))))
;;         name       (format "PROJECT_NAME=%s" (:name (project-info)))
;;         arch       (format "TARGET_ARCH=%s" (:arch opts))
;;         arch-2     (format "TARGET_ARCH_2=%s" (if (= "arm64" (:arch opts)) "aarch64" (:arch opts)))
;;         command (-> ["docker" "build"
;;                      "-t" (format "%s:%s" image-name image-tag)
;;                      "-f" (dockerfile-to-temp)
;;                      "--build-arg" name
;;                      "--build-arg" packages
;;                      "--build-arg" arch
;;                      "--build-arg" arch-2]
;;                     (into (map str) (:args opts))
;;                     (conj "."))
;;         builder (ProcessBuilder. command)
;;         _ (.put (.environment builder) "DOCKER_BUILDKIT" "1")
;;         process (.start builder)]
;;     (stream-to-out (.getInputStream process))
;;     (stream-to-out (.getErrorStream process))
;;     {:exit-code  (.waitFor process)
;;      :image-name image-name
;;      :image-tag  image-tag}))


(defn clean
  "Deletes target and work directories"
  [{:keys [target-dir work-dir]}]
  (doseq [dir [target-dir work-dir]]
    (println "Removing directory:" dir)
    (fs/delete-dir dir)))
