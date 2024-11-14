(ns himmelsstuermer.core.storage
  (:require
    [clojure.edn :as edn]
    [datascript.storage]
    [dynamodb.api :as api]
    [dynamodb.constant :as const]
    [himmelsstuermer.core.config :refer [profile]]
    [himmelsstuermer.misc :as misc]
    [taoensso.telemere :as tt])
  (:import
    (java.io
      BufferedReader
      InputStreamReader)
    (java.lang
      ProcessBuilder)))


;; (defn test-storage-fixture
;;   [f]
;;   (let [command     ["docker"
;;                      "run" "-d"
;;                      "-p" "8000:8000"
;;                      "amazon/dynamodb-local:latest"]
;;         builder      (ProcessBuilder. command)
;;         process      (.start builder)
;;         reader       (BufferedReader. (InputStreamReader. (.getInputStream process)))
;;         container-id (.readLine reader)]
;;     (.waitFor process)
;;     (Thread/sleep 1000)
;;     (f)
;;     (-> ["docker" "stop" container-id]
;;         ProcessBuilder.
;;         .start .waitFor)
;;     (-> ["docker" "rm" container-id]
;;         ProcessBuilder.
;;         .start .waitFor)))


(defn get-storage
  [table-name {:keys [public-key secret-key endpoint region] :as opts}]
  (tt/event! ::get-storage {:data opts})
  (let [client (api/make-client public-key secret-key endpoint region)
        tables (api/list-tables client {:limit 100})] ; TODO: Implement getting all tables

    ;; TODO: Serialization?
    (when (nil? ((set (:TableNames tables)) table-name))
      (let [resp (api/create-table client table-name
                                   {:addr :N}
                                   {:addr const/key-type-hash}
                                   {:tags {:project (:name (misc/project-info))
                                           :profile @profile}
                                    :table-class const/table-class-standard
                                    :billing-mode const/billing-mode-pay-per-request})]
        (tt/event! ::table-created {:data {:table-name table-name
                                           :response resp}})))

    (reify datascript.storage/IStorage
      (-store
        [_ addr+data-seq]
        (let [caller (misc/get-caller)
              addr+data-map (into {} addr+data-seq)
              items (into []
                          (map (fn [[addr data]]
                                 {:Put {:Item {:addr {:N (str addr)}
                                               :data {:S (pr-str data)}}
                                        :TableName table-name}}))
                          addr+data-seq)
              resp (api/api-call client "TransactWriteItems"
                                 {:ClientRequestToken (str (random-uuid))
                                  :ReturnConsumedCapacity const/return-consumed-capacity-total
                                  :ReturnItemCollectionMetrics const/return-item-collection-metrics-size
                                  :TransactItems items})]
          (tt/event! ::storage-store {:data {:data addr+data-map
                                             :response resp
                                             :caller caller}})))

      (-restore
        [_ addr]
        (let [caller (misc/get-caller)
              resp (api/get-item client table-name
                                 {:addr addr})]
          (tt/event! ::storage-restore {:data {:address addr
                                               :response resp
                                               :caller caller}})
          (some-> resp
                  :Item
                  :data
                  edn/read-string)))

      (-list-addresses
        [_]
        (let [resp (api/scan client table-name
                             {:attrs-get [:addr]})]
          (tt/event! ::storage-list-addresses {:data {:response resp}})
          (->> resp
               :Items
               (map :addr))))

      (-delete
        [_ addr-seq]
        (let [items (into []
                          (map (fn [addr]
                                 {:Delete {:Key {:addr {:N (str addr)}}
                                           :TableName table-name}}))
                          addr-seq)
              resp (api/api-call client "TransactWriteItems"
                                 {:ClientRequestToken (str (random-uuid))
                                  :ReturnConsumedCapacity const/return-consumed-capacity-total
                                  :ReturnItemCollectionMetrics const/return-item-collection-metrics-size
                                  :TransactItems items})]
          (tt/event! ::storage-delete {:addresses addr-seq
                                       :response resp}))))))


(def test-client-opts
  {:public-key "awsPublicKey"
   :secret-key "awsSecretKey"
   :endpoint   "http://localhost:8000"
   :region     "aws-region"})
