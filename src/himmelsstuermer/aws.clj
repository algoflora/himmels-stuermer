(ns himmelsstuermer.aws
  (:require
    [himmelsstuermer.aws.build :refer [create-lambda-file]]
    [himmelsstuermer.aws.terraform :refer [write-config apply!]]
    [himmelsstuermer.misc :refer [do-nanos]]))


(def default-opts
  {:arch "arm64"
   :tfstate-bucket "himmelsstuermer"
   :lambda-memory-size 512
   ;; :lambda-name
   ;; :cluster
   ;; :region
   :lambda-timeout 60
   :aux-files #{}
   :aux-packages #{}
   :target-dir "target"
   :tf-config-dir "."
   :work-dir ".work"})


(defn deploy!
  [opts & args]
  (println "Deploy started...\nUser options:" opts)
  (let [nsec (do-nanos (let [opts (-> default-opts
                                      (assoc :args args)
                                      (merge (read-string opts))
                                      create-lambda-file)]
                         (write-config opts)
                         (apply! opts)))]
    (printf "Deploy finished in %.2f seconds." (* nsec 0.000000001))))
