(ns himmelsstuermer.aws.terraform
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [himmelsstuermer.aws.common :refer [stream-to-out]]
    [himmelsstuermer.core.init :refer [bot-token]]
    [me.raynes.fs :as fs]
    [missionary.core :as m]
    [selmer.parser :as selmer])
  (:import
    (java.lang
      ProcessBuilder)))


(defn tf-config-path
  [{:keys [target-dir tf-config-dir] :as opts} filename]
  (let [tf-dir (-> (fs/file target-dir tf-config-dir) .getCanonicalPath)]
    (fs/file tf-dir filename)))


(defn generate-vars
  [opts]
  (selmer/render
    (slurp (io/resource "himmelsstuermer.aws/aws.tfvars"))
    (merge opts
           {:lambda-architecture (:arch opts)
            :bot-token (:bot/token (m/? bot-token))})))


(defn run-tf-cmd!
  ([opts command] (run-tf-cmd! opts command false))
  ([opts command continue?]
   (let [config-file (tf-config-path opts "lambda.tf")]
     (when-not (fs/exists? config-file)
       (throw
         (ex-info
           (format "Missing Terraform config file %s; run `write-config`"
                   (str config-file))
           {:type :aws/missing-file
            :filename (str config-file)})))
     (let [dir     (fs/parent config-file)
           builder (ProcessBuilder. command)
           _ (.directory builder dir)
           process (.start builder)
           _ (stream-to-out (.getInputStream process))
           _ (stream-to-out (.getErrorStream process))]
       (when (not= 0 (.waitFor process))
         (let [exc (ex-info (str "Command failed: " command) {:type :aws/shell-command-failed
                                                              :command command
                                                              :dir dir})]
           (if continue?
             (println (ex-message exc) "\nContinue...")
             (throw exc))))))))


(defn apply!
  [opts]
  (let [cluster-workspace (format "himmelsstuermer-cluster-%s" (:cluster opts))
        lambda-workspace  (format "himmelsstuermer-lambda-%s-%s" (:cluster opts) (:lambda-name opts))]
    (run-tf-cmd! opts ["terraform" "init"])
    (run-tf-cmd! opts ["terraform" "workspace" "new" cluster-workspace] true)
    (run-tf-cmd! opts ["terraform" "workspace" "select" cluster-workspace])
    (run-tf-cmd! opts ["terraform" "apply" "--auto-approve"])
    (run-tf-cmd! opts ["terraform" "workspace" "new" lambda-workspace] true)
    (run-tf-cmd! opts ["terraform" "workspace" "select" lambda-workspace])
    (run-tf-cmd! opts ["terraform" "apply" "--auto-approve"])
    (run-tf-cmd! opts ["terraform" "workspace" "select" "default"])))


(defn write-config
  [{:keys [lambda-env-vars target-dir] :as opts}]
  (let [lambda-vars (generate-vars opts)
        vars-file (tf-config-path opts "aws.auto.tfvars")
        env-vars (->> lambda-env-vars
                      (map #(let [[k v] (str/split % #"=")]
                              {:key k
                               :val v})))]
    (fs/mkdirs target-dir)
    (doseq [filename ["cluster.tf" "lambda.tf"]
            :let [target (fs/file target-dir filename)
                  content (selmer/render (slurp (io/resource (str "himmelsstuermer.aws/" filename)))
                                         (assoc opts :lambda-env-vars env-vars))]]
      (println "Applying Terraform config" (str filename))
      (when (fs/exists? target)
        (fs/delete target))
      (spit target content))
    (println "Writing lambda vars:" (str vars-file))
    (spit vars-file lambda-vars)))
