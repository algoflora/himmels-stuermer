(ns himmelsstuermer.e2e-test.handler
  (:require
    [clojure.string :as str]
    [datomic.client.api :as d]
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.api.transactor :refer [transact!]]
    [himmelsstuermer.user :as u]))


(defn main
  [{:keys [msg usr] :as state}]
  (let [text (or (:text msg) "stranger")]
    (api/send-message state usr
                      (format "Hi, %s!" text)
                      [[(b/text-btn "Go!" 'go {:text text})]
                       [(b/text-btn "Temp" 'modal)]])))


(defn go
  [{:keys [text usr] :as state}]
  (api/send-message state usr (format "Go, %s!" text) [[(b/home-btn "Home")]]))


(defn modal
  [{:keys [usr] :as state}]
  (api/send-message state usr
                    (format "Temp message of %s" (-> usr :user/first-name str/capitalize))
                    [] :modal))


(defn store
  [{:keys [usr] :as state}]
  (api/send-message state usr "Hello" [[(b/text-btn "Save" 'save)]]))


(defn save
  [{:keys [usr txs] :as state}]
  (transact! txs [{:test-entity/user [:user/id (:user/id usr)]
                   :test-entity/data (str/upper-case (:user/first-name usr))}])
  (api/send-message state usr
                    "Name saved"
                    [[(b/text-btn "Reveal" 'himmelsstuermer.e2e-test.handler/reveal)]]))


(defn reveal
  [{:keys [usr idb] :as state}]
  (let [name (ffirst (d/q '[:find ?n
                            :in $ ?uid
                            :where
                            [?u :user/id ?uid]
                            [?e :test-entity/user ?u]
                            [?e :test-entity/data ?n]] idb (:user/id usr)))]
    (api/send-message state usr name [])))


(defn roled
  [{:keys [usr] :as state}]
  (let [text (if (u/has-role? state :admin) "Hello, sir" "Hi")]
    (api/send-message state usr text [])))


(defn fail
  [{:keys [usr] :as state}]
  (api/send-message state usr "Hello World!" [[(b/text-btn "Button" 'himmelsstuermer.e2e-test.handler/fail-button)]]))


(defn fail-button
  [{:keys [usr] :as state}]
  (api/send-message state usr "Click Error" [[(b/text-btn "Error" 'himmelsstuermer.e2e-test.handler/fail-expression)]]))


(defn fail-expression
  [_]
  (/ 1 0))


(defn payment
  [{:keys [usr] :as state}]
  (api/send-message state usr "Give me all your money!" [[(b/text-btn "Invoice" 'invoice)]]))


(defn invoice
  [{:keys [usr] :as state}]
  (api/send-invoice state usr {:title "Invoice"
                               :description "All your money!"
                               :payload "all-your-money"
                               :provider_token "" ; Like XTR
                               :currency "XTR"
                               :prices [{:label "Price" :amount 15000}
                                        {:label "Discount" :amount -5000}]}
                    "Pay 100 XTR" [[(b/text-btn "Dummy button" 'fake)]]))
