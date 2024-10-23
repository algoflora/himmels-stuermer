(ns himmelsstuermer.impl.callbacks
  (:require
    [datalevin.core :as d]
    [himmelsstuermer.api.db :as db]
    [himmelsstuermer.spec :as spec]
    [malli.core :as malli]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(malli/=> set-callback
          [:function
           [:=> [:cat spec/User :symbol [:maybe :map]] spec/MissionaryTask]
           [:=> [:cat spec/User :symbol [:maybe :map] :boolean] spec/MissionaryTask]
           [:=> [:cat
                 spec/User
                 :symbol
                 [:maybe :map]
                 :boolean
                 :uuid]
            spec/MissionaryTask]])


(defn set-callback
  ([user f args]
   (set-callback user f args false))
  ([user f args is-service]
   (set-callback user f args is-service (java.util.UUID/randomUUID)))
  ([user f args is-service uuid]
   (m/sp (let [args (or args {})
               tx-data [{:callback/uuid uuid
                         :callback/function f
                         :callback/arguments args
                         :callback/service? is-service
                         :callback/user [:user/uuid (:user/uuid user)]}]]
           (db/transact tx-data)
           (tt/event! ::callback-create {:data {:user user
                                                :function f
                                                :arguments args}})
           uuid))))


(malli/=> delete [:=> [:cat spec/User :int] spec/MissionaryTask])


(defn delete
  [user mid]
  (m/sp (let [eids-to-retract (d/q '[:find ?cb
                                     :in $ ?uid ?mid
                                     :where
                                     [?cb :callback/message-id ?mid]
                                     [?cb :callback/user [:user/id ?uid]]]
                                   db/*db* (:user/id user) mid)
              tx-data (into [] #(vector :db/retractEntity (first %)) eids-to-retract)]
          (db/transact tx-data)
          (tt/event! ::callbacks-delete {:data {:user user
                                                :message-id mid
                                                :tx-data tx-data}}))))


(malli/=> set-new-message-ids [:=> [:cat spec/User [:or :int :nil] [:vector :uuid]] spec/MissionaryTask])


(defn set-new-message-ids
  [user mid uuids]
  (m/sp (let [uuids-to-retract (d/q '[:find [?uuid ...]
                                      :in $ ?uid ?mid ?uuids
                                      :where
                                      [?cb :callback/user [:user/id ?uid]]
                                      [?cb :callback/message-id ?mid]
                                      [?cb :callback/uuid ?uuid]
                                      (not-join [?uuid]
                                                [(contains? ?uuids ?uuid)])]
                                    db/*db* (:user/id user) mid (set uuids))
              tx-data (-> []
                          (into (map #(vector :db/retractEntity [:callback/uuid %]) uuids-to-retract))
                          (into (map #(array-map :callback/uuid % :callback/message-id mid) uuids)))]
          (db/transact tx-data)
          (tt/event! ::callbacks-set-new-message-ids
                     {:data {:user user
                             :message-id mid
                             :unspoil-callback-uuids uuids
                             :tx-data tx-data}}))))
