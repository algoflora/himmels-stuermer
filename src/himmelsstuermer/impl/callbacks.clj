(ns himmelsstuermer.impl.callbacks
  (:gen-class)
  (:require
    [datomic.client.api :as d]
    [himmelsstuermer.impl.transactor :refer [transact!]]
    [himmelsstuermer.spec.core :as spec]
    [malli.core :as malli]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(malli/=> set-callback
          [:function
           [:=> [:cat spec/UserState spec/User :symbol [:maybe :map]] :uuid]
           [:=> [:cat spec/UserState spec/User :symbol [:maybe :map] :boolean] :uuid]
           [:=> [:cat
                 spec/UserState
                 spec/User
                 :symbol
                 [:maybe :map]
                 :boolean
                 :uuid]
            :uuid]])


(defn set-callback
  ([state user f args]
   (set-callback state user f args false))
  ([state user f args is-service]
   (set-callback state user f args is-service (java.util.UUID/randomUUID)))
  ([{:keys [txs]} user f args is-service uuid]
   (let [args (or args {})
         tx-data [{:callback/uuid uuid
                   :callback/function f
                   :callback/arguments (prn-str args)
                   :callback/service? is-service
                   :callback/user (:db/id user)}]]
     (transact! txs tx-data)
     (tt/event! ::callback-create {:data {:user user
                                          :function f
                                          :arguments args}})
     uuid)))


(malli/=> delete [:=> [:cat spec/UserState spec/User :int] spec/MissionaryTask])


(defn delete
  [{:keys [idb txs]} user mid]
  (tt/event! ::clb-delete {:data {:user user :mid mid}})
  (m/sp (let [ueid (:db/id user)
              eids-to-retract (if (pos-int? ueid)
                                (d/q '[:find ?cb
                                       :in $ ?uid ?mid
                                       :where
                                       [?u :user/id ?uid]
                                       [?cb :callback/message-id ?mid]
                                       [?cb :callback/user ?u]]
                                     idb (:user/id user) mid)
                                #{})
              tx-data (into [] (map #(vector :db/retractEntity (first %))) eids-to-retract)]
          (transact! txs tx-data))))


(malli/=> set-new-message-ids [:=> [:cat
                                    spec/UserState
                                    spec/User
                                    [:or :int :nil]
                                    [:vector :uuid]] spec/MissionaryTask])


(defn set-new-message-ids
  [{:keys [idb txs]} user mid uuids]

  (m/sp (let [ueid (:db/id user)
              eids-to-retract (if (pos-int? ueid)
                                (d/q '[:find ?cb
                                       :in $ ?ueid ?mid ?uuids
                                       :where
                                       [?cb :callback/user ?ueid]
                                       [?cb :callback/message-id ?mid]
                                       [?cb :callback/uuid ?uuid]
                                       (not-join [?uuid ?uuids]
                                                 [(contains? ?uuids ?uuid)])]
                                     idb ueid mid (set uuids))
                                #{})
              tx-data (-> #{}
                          (into (map #(vector :db/retractEntity (first %)) eids-to-retract))
                          (into (map #(array-map :callback/uuid % :callback/message-id mid) uuids)))]
          (transact! txs tx-data)
          (tt/event! ::callbacks-set-new-message-ids
                     {:data {:user user
                             :message-id mid
                             :unspoil-callback-uuids uuids
                             :tx-data tx-data}}))))
