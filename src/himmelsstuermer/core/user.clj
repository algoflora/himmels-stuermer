(ns himmelsstuermer.core.user
  (:require
    [datascript.core :as d]
    [himmelsstuermer.core.dispatcher :as disp]
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
  (let [user' (assoc user ; TODO: Attemp to minimize transaction data
                     :user/username (:username udata)
                     :user/first-name (:first_name udata)
                     :user/last-name (:last_name udata)
                     :user/language-code (:language_code udata))]
    (tt/event! ::user-renew {:data {:old-user user
                                    :new-user user'}})
    [user' (into {} (filter #(-> % second some?)) user')]))


(malli/=> create [:=> [:cat :symbol spec.tg/User] [:cat spec/User [:vector [:or [:vector :any] :map]]]])


(defn- create
  [handler-main udata]
  (let [uuid (random-uuid)
        user (into {}
                   (filter #(-> % second some?))
                   {:db/id -1
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
            :callback/user -1
            :callback/service? false}]]))


;; (defn set-msg-id
;;   [user msg-id]
;;   (d/transact! *dtlv* [{:user/id (:user/id user)
;;                         :user/msg-id msg-id}])
;;   (log/debug ::set-msg-id
;;              "User's msg-id was set to %d" msg-id
;;              {:user user :msg-id msg-id}))


(malli/=> load-to-state [:=> [:cat spec/State spec.tg/User [:maybe :uuid] :boolean] spec/MissionaryTask])


(defn load-to-state
  ([{:keys [database] :as state} from uuid reset?]
   (m/sp (let [query (if (some? uuid)

                       '[:find (pull ?u [*]) (pull ?cbu [*]) (pull ?cb [*])
                         :in $ ?uid ?uuid
                         :where
                         [?u :user/id ?uid]
                         [?u :user/uuid ?uuuid]
                         [?cbu :callback/uuid ?uuuid]
                         [?cb :callback/uuid ?uuid]]

                       '[:find (pull ?u [*]) (pull ?cbu [*]) (pull ?cb [*])
                         :in $ ?uid
                         :where
                         [?u :user/id ?uid]
                         [?u :user/uuid ?uuuid]
                         [?cbu :callback/uuid ?uuuid]
                         [?cb :callback/uuid ?uuuid]])

               [user? user-callback? callback?]
               (first (apply d/q query database (cond-> [(:id from)]
                                                  (some? uuid) (conj uuid))))

               ;; data (d/q query database (:id from) uuid)
               ;; datoms (mapv str (d/datoms database :eav))
               ;; id (:id from)
               ;; _ (tt/event! ::query-test {:data {:?uid id
               ;;                                   :?uuid uuid
               ;;                                   :datoms datoms
               ;;                                   :result data}})

               [_user tx-data] (cond
                                 (nil? user?)
                                 (create (symbol disp/main-handler) from)

                                 (is-new-udata? user? from)
                                 (renew user? from)

                                 :else [user? []])
               user            (if reset? (assoc _user :user/msg-id 0) _user)
               is-payment?     (contains? (:message state) :successful_payment)
               function        @(if is-payment? disp/payment-handler
                                    (or (disp/resolve-symbol! (:callback/function callback?))
                                        disp/main-handler))
               arguments       (or (:callback/arguments callback?) {})]
           (tt/event! ::user-loaded {:data {:user user}})
           (s/modify-state state #(cond-> %
                                    (and (or (not=   (symbol disp/main-handler)
                                                     (:callback/function user-callback?))
                                             (seq (:callback/arguments user-callback?)))
                                         (false? (:calllback/service? callback?)))
                                    (update :transaction conj {:callback/uuid (:user/uuid user)
                                                               :callback/function (symbol disp/main-handler)
                                                               :callback/arguments {}})

                                    :always (->
                                              (assoc :user user)
                                              (assoc :function function)
                                              (assoc :arguments arguments)
                                              (update :transaction into tx-data))))))))
