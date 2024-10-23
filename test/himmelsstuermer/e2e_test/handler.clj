(ns himmelsstuermer.e2e-test.handler
  (:require
    [clojure.string :as str]
    [datalevin.core :as d]
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.api.db :refer [*db* transact]]
    [himmelsstuermer.api.vars :refer [*user*]]
    [himmelsstuermer.user :as u]
    [missionary.core :as m]
    [taoensso.timbre :as log]))


(defn main
  [{msg :message}]
  (let [text (or (:text msg) "stranger")]
    (api/send-message *user*
                      (format "Hi, %s!" text)
                      [[(b/text-btn "Go!" 'go {:text text})]
                       [(b/text-btn "Temp" 'temp)]])))


(defn go
  [{:keys [text]}]
  (api/send-message *user* (format "Go, %s!" text) [[(b/home-btn "Home")]]))


(defn temp
  [_]
  (api/send-message *user*
                    (format "Temp message of %s" (-> *user* :user/first-name str/capitalize))
                    [] :temp))


(defn store
  [_]
  (api/send-message *user* "Hello" [[(b/text-btn "Save" 'save)]]))


(defn save
  [_]
  (transact [{:test-entity/user [:user/id (:user/id *user*)]
              :test-entity/data (str/upper-case (:user/first-name *user*))}])
  (api/send-message *user* "Name saved" [[(b/text-btn "Reveal" 'himmelsstuermer.e2e-test.handler/reveal)]]))


(defn reveal
  [_]
  (let [name (ffirst (d/q '[:find ?n
                            :in $ ?uid
                            :where
                            [?e :test-entity/user [:user/id ?uid]]
                            [?e :test-entity/data ?n]] *db* (:user/id *user*)))]
    (log/debug ::reveal-2 {})
    (api/send-message *user* name [])
    (log/debug ::reveal-3 {})))


(defn roled
  [_]
  (let [text (if (u/has-role? :admin) "Hello, sir" "Hi")]
    (api/send-message *user* text [])))


(defn error
  [_]
  (api/send-message *user* "Hello World!" [[(b/text-btn "Button" 'himmelsstuermer.e2e-test.handler/error-button)]]))


(defn error-button
  [_]
  (api/send-message *user* "Click Error" [[(b/text-btn "Error" 'himmelsstuermer.e2e-test.handler/error-expression)]]))


(defn error-expression
  [_]
  (/ 1 0))


(defn payment
  [_]
  (api/send-message *user* "Give me all your money!" [[(b/text-btn "Invoice" 'invoice)]]))


(defn invoice
  [_]
  (api/send-invoice *user* {:title "Invoice"
                            :description "All your money!"
                            :payload "all-your-money"
                            :provider_token "" ; Like XTR
                            :currency "XTR"
                            :prices [{:label "Price" :amount 15000}
                                     {:label "Discount" :amount -5000}]}
                    "Pay 100 XTR" [[(b/text-btn "Dummy button" 'fake)]]))
