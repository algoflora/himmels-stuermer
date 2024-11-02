(ns himmelsstuermer.blambda.api.terraform
  (:require
    [babashka.fs :as fs]
    [babashka.process :refer [shell]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [himmelsstuermer.blambda.internal :as lib]
    [himmelsstuermer.impl.config :refer [get-config]]
    [selmer.parser :as selmer]))


(defn tf-config-path
  [{:keys [target-dir tf-config-dir]} filename]
  (let [tf-dir (-> (fs/file target-dir tf-config-dir) fs/canonicalize)]
    (fs/file tf-dir filename)))


(defn generate-module
  [opts]
  (selmer/render (slurp (io/resource "blambda/lambda_layer.tf")) opts))


(defn generate-vars
  [opts]
  (let [runtime-zipfile (lib/runtime-zipfile opts)
        lambda-zipfile (lib/lambda-zipfile opts)
        deps-zipfile (lib/deps-zipfile opts)]
    (selmer/render
      (slurp (io/resource "blambda/blambda.tfvars"))
      (merge opts
             {:runtime-layer-compatible-architectures (lib/runtime-layer-architectures opts)
              :runtime-layer-compatible-runtimes (lib/runtime-layer-runtimes opts)
              :runtime-layer-filename runtime-zipfile
              :lambda-filename lambda-zipfile
              :lambda-architecture (first (lib/runtime-layer-architectures opts))
              :deps-layer-compatible-architectures (lib/deps-layer-architectures opts)
              :deps-layer-compatible-runtimes (lib/deps-layer-runtimes opts)
              :deps-layer-filename deps-zipfile
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
           {:type :blambda/missing-file
            :filename (str config-file)})))
     (shell {:dir (str (fs/parent config-file)) :continue continue?} cmd))))


(defn apply!
  [opts]
  (let [cluster-workspace (str "cluster-" (:cluster opts))
        lambda-workspace  (format "lambda-%s-%s" (:cluster opts) (:lambda-name opts))
        auto-approve?     (if (and (coll? *command-line-args*)
                                   (some #{"--auto-approve"} *command-line-args*))
                            " -auto-approve" nil)]
    (run-tf-cmd! opts "terraform init")
    (run-tf-cmd! opts (str "terraform workspace new " cluster-workspace) true)
    (run-tf-cmd! opts (str "terraform workspace select " cluster-workspace))
    (run-tf-cmd! opts (str "terraform apply" auto-approve?))
    (run-tf-cmd! opts (str "terraform workspace new " lambda-workspace) true)
    (run-tf-cmd! opts (str "terraform workspace select " lambda-workspace))
    (run-tf-cmd! opts (str "terraform apply" auto-approve?))
    (run-tf-cmd! opts "terraform workspace select default")))


(defn write-config
  [{:keys [lambda-name tf-module-dir lambda-env-vars cluster tfstate-bucket target-dir]
    :as opts}]
  (let [opts (assoc opts
                    :lambda-filename (format "%s.zip" lambda-name))
        lambda-layer-vars (generate-vars opts)
        lambda-layer-module (generate-module opts)
        vars-file (tf-config-path opts "blambda.auto.tfvars")
        module-dir (tf-config-path opts tf-module-dir)
        module-file (tf-config-path opts (fs/file tf-module-dir "lambda_layer.tf"))
        env-vars (->> lambda-env-vars
                      (map #(let [[k v] (str/split % #"=")]
                              {:key k
                               :val v})))]
    (fs/create-dirs target-dir)
    (fs/create-dirs module-dir)
    (doseq [filename ["cluster.tf" "lambda.tf"]
            :let [target (fs/file target-dir filename)
                  content (selmer/render (slurp (io/resource (str "blambda/" filename)))
                                         (assoc opts :lambda-env-vars env-vars))]]
      (println "Applying Terraform config" (str filename))
      (fs/delete-if-exists target)
      (spit target content))
    (println "Writing lambda layer vars:" (str vars-file))
    (spit vars-file lambda-layer-vars)
    (println "Writing lambda layers module:" (str module-file))
    (spit module-file lambda-layer-module)))


(comment
  (require '[himmelsstuermer.aws :as aws])
  (aws/deploy! {:lambda-name "lambda" :cluster "cluster"}))
