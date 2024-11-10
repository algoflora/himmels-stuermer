(ns himmelsstuermer.aws
  (:require
    [himmelsstuermer.aws.build :refer [build-image]]
    [himmelsstuermer.aws.terraform :refer [write-config apply!]]
    [himmelsstuermer.misc :refer [do-nanos]]))


(def default-opts
  {:arch "arm64"
   :tfstate-bucket "himmelsstuermer"
   :lambda-memory-size 2048
   ;; :lambda-name
   ;; :cluster
   ;; :region
   :lambda-timeout 15
   :aux-files #{}
   :aux-packages #{}
   :target-dir "target"
   :tf-config-dir "."
   :work-dir ".work"})


(defn deploy!
  [opts & args]
  (println "Deploy started...\nUser options:" opts)
  (let [opts (merge (assoc default-opts :args args) (read-string opts))
        _ (println "Full opts: " opts)
        nsec (do-nanos (let [{:keys [exit-code image-name image-tag]} (build-image opts)
                             _ (when (not (zero? exit-code))
                                 (throw (ex-info "Container image build failed!" {:type :aws-image-build-failed
                                                                                  :exit-code exit-code})))
                             opts (assoc opts :image-name image-name :image-tag image-tag)]
                         (write-config opts)
                         (apply! opts)))]
    (printf "Deploy finished in %.2f seconds." (* nsec 0.000000001))))
