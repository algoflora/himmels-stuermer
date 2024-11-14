(ns himmelsstuermer.core.storage
  (:require
    [clojure.data.codec.base64 :as b64]
    [datascript.storage]
    [dynamodb.api :as api]
    [dynamodb.constant :as const]
    [himmelsstuermer.core.config :refer [profile]]
    [himmelsstuermer.misc :as misc]
    [taoensso.nippy :as nippy]
    [taoensso.telemere :as tt]))


(defn get-storage
  [table-name {:keys [public-key secret-key endpoint region] :as opts}]
  (tt/event! ::get-storage {:data opts})
  (let [{client :result nanos :nanos}
        (misc/do-nanos* (api/make-client public-key secret-key endpoint region))
        _ (tt/event! ::client-received {:data {:client client
                                               :time-millis (* 0.000001 nanos)}})

        {tables :result nanos :nanos}
        (misc/do-nanos* (api/list-tables client {:limit 100}))
        _ (tt/event! ::tables-received {:data {:response tables
                                               :time-millis (* 0.000001 nanos)}})] ; TODO: Implement getting all tables

    (when (nil? ((set (:TableNames tables)) table-name))
      (let [{:keys [result nanos]}
            (misc/do-nanos* (api/create-table client table-name
                                              {:Addr :N}
                                              {:Addr const/key-type-hash}
                                              {:tags {:project (:name (misc/project-info))
                                                      :profile @profile}
                                               :table-class const/table-class-standard
                                               :billing-mode const/billing-mode-pay-per-request}))]
        (tt/event! ::table-created {:data {:table-name table-name
                                           :response result
                                           :time-millis (* 0.000001 nanos)}})))

    (reify datascript.storage/IStorage
      (-store
        [_ addr+data-seq]
        (let [caller (misc/get-caller)

              {items :result serialization-nanos :nanos}
              (misc/do-nanos* (into []
                                    (map (fn [[addr data]]
                                           {:Put {:Item {:Addr {:N (str addr)}
                                                         :Payload {:B (nippy/freeze data)}}
                                                  :TableName table-name}}))
                                    addr+data-seq))

              {:keys [result nanos]}
              (misc/do-nanos* (api/api-call client "TransactWriteItems"
                                            {:ClientRequestToken (str (random-uuid))
                                             :ReturnConsumedCapacity const/return-consumed-capacity-total
                                             :ReturnItemCollectionMetrics const/return-item-collection-metrics-size
                                             :TransactItems items}))]
          (tt/event! ::storage-store {:data {:response result
                                             :serialization-millis (* 0.000001 serialization-nanos)
                                             :request-millis (* 0.000001 nanos)
                                             :caller caller}})))

      (-restore
        [_ addr]
        (let [caller (misc/get-caller)

              {:keys [result nanos]}
              (misc/do-nanos* (api/api-call client "GetItem"
                                            {:ClientRequestToken (str (random-uuid))
                                             :ReturnConsumedCapacity const/return-consumed-capacity-total
                                             :ConsistentRead true
                                             :TableName table-name
                                             :Key {:Addr {:N (str addr)}}
                                             :ProjectionExpression "Payload"}))
              {data :result deserialization-nanos :nanos}
              (misc/do-nanos* (some-> result :Item :Payload :B str .getBytes b64/decode nippy/thaw))]
          (tt/event! ::storage-restore {:data {:Addr addr
                                               :response (update-in result [:Item :Payload :B] count)
                                               :deserialization-millis (* 0.000001 deserialization-nanos)
                                               :request-millis (* 0.000001 nanos)
                                               :caller caller}})
          data))

      (-list-addresses
        [_]
        (let [{:keys [result nanos]}
              (misc/do-nanos* (api/scan client table-name
                                        {:attrs-get [:Addr]}))]
          (tt/event! ::storage-list-addresses {:data {:response result
                                                      :time-millis (* 0.000001 nanos)}})
          (->> result
               :Items
               (map :Addr))))

      (-delete
        [_ addr-seq]
        (let [items (into []
                          (map (fn [addr]
                                 {:Delete {:Key {:Addr {:N (str addr)}}
                                           :TableName table-name}}))
                          addr-seq)

              {:keys [result nanos]}
              (misc/do-nanos* (api/api-call client "TransactWriteItems"
                                            {:ClientRequestToken (str (random-uuid))
                                             :ReturnConsumedCapacity const/return-consumed-capacity-total
                                             :ReturnItemCollectionMetrics const/return-item-collection-metrics-size
                                             :TransactItems items}))]
          (tt/event! ::storage-delete {:Addrs addr-seq
                                       :response result
                                       :time-millis (* 0.000001 nanos)}))))))


(def test-client-opts
  {:public-key "awsPublicKey"
   :secret-key "awsSecretKey"
   :endpoint   "http://localhost:8000"
   :region     "aws-region"})


(get nippy/public-types-spec -67)
