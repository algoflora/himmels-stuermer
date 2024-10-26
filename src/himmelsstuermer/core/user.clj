(ns himmelsstuermer.core.user
  (:require
    [datalevin.core :as d]
    [himmelsstuermer.core.state :as s]
    [himmelsstuermer.spec.core :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(malli/=> is-new-data? [:=> [:cat spec/User spec.tg/User] :boolean])


(defn- is-new-udata?
  [user udata]
  (or (not= (:username udata) (:user/username user))
      (not= (:first_name udata) (:user/first-name user))
      (not= (:last_name udata) (:user/last-name user))
      (not= (:language_code udata) (:user/language-code user))))


(defn- renew
  [user udata]
  (let [user' (assoc user ; TODO: Attempt to minimize transaction data
                     :user/username (:usernam udata)
                     :user/first-name (:first_name udata)
                     :user/last-name (:last_name udata)
                     :user/language-code (:language_code udata))]
    (tt/event! ::user-renew {:old-user user :new-user user'})
    [user' (into {} (filter #(-> % second some?)) user')]))


(malli/=> create [:=> [:cat :symbol spec.tg/User] [:cat spec/User [:vector [:or [:vector :any] :map]]]])


(defn- create
  [handler-main udata]
  (let [uuid (random-uuid)
        user (into {}
                   (filter #(-> % second some?))
                   {:db/id -999
                    :user/uuid uuid
                    :user/id (:id udata)
                    :user/username (:username udata)
                    :user/first-name (:first_name udata)
                    :user/last-name (:last_name udata)
                    :user/language-code (:language_code udata)})]
    (tt/event! ::user-create {:data {:user user}})
    [user [user
           {:callback/uuid uuid
            :callback/function handler-main
            :callback/arguments {}
            :callback/user -999
            :callback/service? false}]]))


;; (defn set-msg-id
;;   [user msg-id]
;;   (d/transact! *dtlv* [{:user/id (:user/id user)
;;                         :user/msg-id msg-id}])
;;   (log/debug ::set-msg-id
;;              "User's msg-id was set to %d" msg-id
;;              {:user user :msg-id msg-id}))


(malli/=> load-to-state [:=> [:cat spec/State spec.tg/User [:? [:maybe :uuid]]] spec/MissionaryTask])


(defn load-to-state
  ([state from] (load-to-state state from nil))
  ([{:keys [database] :as state} from uuid]
   (m/sp (let [query  (conj '[:find (pull ?u [*]) (pull ?cbu [*]) (pull ?cb [*])
                              :in $ ?uid ?uuid
                              :where
                              [?u :user/id ?uid]
                              [?u :user/uuid ?uuuid]
                              [?cbu :callback/uuid ?uuuid]]
                            (if (some? uuid)
                              '[?cb :callback/uuid ?uuid]
                              '[?cb :callback/uuid ?uuuid]))

               [user? user-callback? callback?]
               (first (d/q query database (:id from) uuid))

               ;; data (d/q query database (:id from) uuid)
               ;; datoms (mapv str (d/datoms database :eav))
               ;; id (:id from)
               ;; _ (tt/event! ::query-test {:data {:?uid id
               ;;                                   :?uuid uuid
               ;;                                   :datoms datoms
               ;;                                   :result data}})

               [user tx-data]   (cond
                                  (nil? user?)
                                  (create (-> state :handlers :main) from)

                                  (is-new-udata? user? from)
                                  (renew user? from)

                                  :else [user? []])
               is-payment?      (contains? (:message state) :successful_payment)
               function         @(requiring-resolve ; TODO: rewrite more elegant
                                  (if is-payment?
                                    (-> state :handlers :payment)
                                    (or (:callback/function callback?)
                                        (-> state :handlers :main))))
               arguments        (or (:callback/arguments callback?) {})]
           (tt/event! ::user-loaded {:data {:user user}})
           (s/modify-state state #(cond-> %
                                    (and (not=   (-> state :handlers :main)
                                                 (:callback/function user-callback?))
                                         (false? (:calllback/service? callback?)))
                                    (update :transaction conj {:callback/uuid (:user/uuid user)
                                                               :callback/function (-> state :handlers :main)
                                                               :callback/arguments {}})

                                    :always (->
                                              (assoc :user user)
                                              (assoc :function function)
                                              (assoc :arguments arguments)
                                              (update :transaction into tx-data))))))))
