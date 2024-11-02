(ns himmelsstuermer.aws
  (:require
    [babashka.fs :as fs]
    [himmelsstuermer.blambda.api :refer [build-all]]
    [himmelsstuermer.blambda.api.terraform :refer [write-config apply!]]
    [himmelsstuermer.misc :refer [do-nanos]]
    [himmelsstuermer.spec.core :as spec]
    [malli.core :as m]))


(defn- get-tree
  [dir]
  (->> (fs/glob dir "**")
       (filter #(not (fs/directory? %)))
       (filter #(re-find #".*[^~#]$" (str %)))))


(def default-opts
  {:bb-arch "arm64"
   :bb-version "1.3.186"
   :deps-layer-name "deps-layer"
   :deps-path "./bb.edn"
   :tfstate-bucket "himmelsstuermer"
   :lambda-handler "main/handler"
   :lambda-env-vars []
   :lambda-memory-size 512
   ;; :lambda-name
   ;; :cluster
   :lambda-runtime "provided.al2023"
   :lambda-timeout 15
   :runtime-layer-name "blambda-layer"
   :source-dir "."
   :source-files (into [] (concat (get-tree "./src") (get-tree "./resources")))
   :target-dir "target"
   :tf-config-dir "."
   :tf-module-dir "modules"
   :work-dir ".work"})


(m/=> deploy! [:=> [:cat spec/UserOpts] :nil])


(defn deploy!
  [opts]
  (println "Deploy started...\n")
  (let [opts (merge default-opts opts)
        nsec (do-nanos (build-all opts)
                       (write-config opts)
                       (apply! opts))]
    (printf "Deploy finished in %.2f seconds." (* nsec 0.000000001))))
