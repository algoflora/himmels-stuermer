(ns himmelsstuermer.aws.terraform
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [himmelsstuermer.blambda.internal :as lib]
    [himmelsstuermer.impl.config :refer [get-config]]
    [me.raynes.fs :as fs]
    [selmer.parser :as selmer]))


(defn tf-config-path
  [{:keys [target-dir tf-config-dir]} filename]
  (let [tf-dir (-> (fs/file target-dir tf-config-dir) fs/canonicalize)]
    (fs/file tf-dir filename)))


(defn generate-vars
  [opts]
  (let [lambda-zipfile (lib/lambda-zipfile opts)]
    (selmer/render
      (slurp (io/resource "himmelsstuermer.aws/aws.tfvars"))
      (merge opts
             {:lambda-filename lambda-zipfile
              :lambda-architecture (first (lib/runtime-layer-architectures opts))
              :bot-token (:bot/token (get-config))}))))


(defn run-tf-cmd!
  ([opts cmd] (run-tf-cmd! opts cmd false))
  ([opts cmd continue?]
   (let [config-file (tf-config-path opts "lambda.tf")]
     (when-not (fs/exists? config-file)
       (throw
         (ex-info
           (format "Missing Terraform config file %s; run `write-config`"
                   (str config-file))
           {:type :aws/missing-file
            :filename (str config-file)})))
     (let [dir    (str (fs/parent config-file))
           result (sh "sh" "-c" cmd :dir dir)]
       (if (and (not continue?) (not= 0 (:exit result)))
         (throw (ex-info (str "Command failed: " cmd) {:type :aws/shell-command-failed
                                                       :exit (:exit result)
                                                       :output (:out result)
                                                       :error (:err result)}))
         result)))))


(defn apply!
  [opts]
  (let [cluster-workspace (format "cluster-%s" (:cluster opts))
        lambda-workspace  (format "lambda-%s-%s" (:cluster opts) (:lambda-name opts))]
    (run-tf-cmd! opts "terraform init")
    (run-tf-cmd! opts (str "terraform workspace new " cluster-workspace) true)
    (run-tf-cmd! opts (str "terraform workspace select " cluster-workspace))
    (run-tf-cmd! opts (str "terraform apply --auto-approve"))
    (run-tf-cmd! opts (str "terraform workspace new " lambda-workspace) true)
    (run-tf-cmd! opts (str "terraform workspace select " lambda-workspace))
    (run-tf-cmd! opts (str "terraform apply --auto-approve"))
    (run-tf-cmd! opts "terraform workspace select default")))


(defn write-config
  [{:keys [lambda-env-vars target-dir] :as opts}]
  (let [lambda-vars (generate-vars opts)
        vars-file (tf-config-path opts "aws.auto.tfvars")
        env-vars (->> lambda-env-vars
                      (map #(let [[k v] (str/split % #"=")]
                              {:key k
                               :val v})))]
    (fs/create-dirs target-dir)
    (doseq [filename ["cluster.tf" "lambda.tf"]
            :let [target (fs/file target-dir filename)
                  content (selmer/render (slurp (io/resource (str "himmelsstuermer.aws/" filename)))
                                         (assoc opts :lambda-env-vars env-vars))]]
      (println "Applying Terraform config" (str filename))
      (fs/delete-if-exists target)
      (spit target content))
    (println "Writing lambda vars:" (str vars-file))
    (spit vars-file lambda-vars)))
