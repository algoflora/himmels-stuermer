(ns himmelsstuermer.e2e.client
  (:require
    [cheshire.core :as json]
    [himmelsstuermer.core]
    [himmelsstuermer.e2e.dummy :as dum]
    [himmelsstuermer.spec.core :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [malli.generator :refer [generate]]
    [taoensso.telemere :as tt]
    [tick.core :as t])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)))


(defonce ^:private update-id (atom 0))


(def mock-lambda-context
  (reify Context
    (getAwsRequestId [_] "mock-request-id")))


(defn mock-record
  ^java.util.HashMap
  []
  (let [java-map (java.util.HashMap.)]
    (doseq [[k v] (generate spec/Record)]
      (.put java-map
            (if (keyword? k) (name k) (str k))
            v))
    java-map))


(malli/=> send-update [:-> spec.tg/Update :any])


(defn send-update
  [data]
  (let [update       (assoc data :update_id (swap! update-id inc))
        mock-record  (mock-record)
        _ (.put mock-record "body" (json/encode update))
        mock-records (java.util.Collections/singletonList mock-record)]
    (tt/event! ::send-update {:data {:update update
                                     :mock-records mock-records
                                     :mock-context mock-lambda-context}})
    (himmelsstuermer.core/run mock-records mock-lambda-context)))


(malli/=> send-action-request [:-> spec/ActionRequest :any])


(defn send-action-request
  [action-request]
  (let [mock-record  (mock-record)
        _ (.put mock-record "body" (json/encode action-request))
        mock-records (java.util.Collections/singletonList mock-record)]
    (tt/event! ::send-action-request {:data {:update update
                                             :mock-records mock-records
                                             :mock-context mock-lambda-context}})
    (himmelsstuermer.core/run mock-records mock-lambda-context)))


(malli/=> dummy-kw?->dummy [:-> [:or spec.tg/User :keyword] spec.tg/User])


(defn- dummy-kw?->dummy
  [dummy]
  (if (keyword? dummy) (-> dummy dum/get-by-key :dummy) dummy))


(malli/=> dummy->base-message [:-> [:or spec.tg/User :keyword] spec.tg/BaseMessage])


(defn- dummy->base-message
  [dummy-kw?]
  (let [dummy (dummy-kw?->dummy dummy-kw?)
        message-id (-> (dum/get-last-messages dummy 1 nil) first :message_id (or 0) inc)]
    {:message_id message-id
     :from dummy
     :chat {:id (:id dummy)
            :type "private"}
     :date (System/currentTimeMillis)}))


(malli/=> call-action [:=> [:cat :keyword :map] :any])


(defn call-action
  [type args-map]
  (let [action-request {:action {:method (name type)
                                 :arguments args-map
                                 :timestamp (t/millis (t/between (t/epoch) (t/inst)))}}]
    (send-action-request action-request)))


(malli/=> send-text [:=>
                     [:cat [:or spec.tg/User :keyword] :string [:vector spec.tg/MessageEntity]]
                     :any])


(defn send-text
  [dummy text entities]
  (let [message (merge (dummy->base-message dummy)
                       {:text text :entities entities})]
    (dum/add-message message)
    (send-update {:message message})))


(malli/=> dummy->base-callback-query [:-> [:or spec.tg/User :keyword] spec.tg/BaseCallbackQuery])


(defn- dummy->base-callback-query
  [dummy-kw?]
  (let [dummy (dummy-kw?->dummy dummy-kw?)]
    {:id (str (java.util.UUID/randomUUID))
     :from dummy}))


(malli/=> click-btn [:=>
                     [:cat
                      [:or spec.tg/User :keyword]
                      spec.tg/Message
                      spec/Regexp]
                     :any])


(defn click-btn
  [dummy msg btn-re]
  (let [base-cbq (dummy->base-callback-query dummy)
        buttons  (->> msg :reply_markup :inline_keyboard flatten
                      (filter #(some? (re-find btn-re (:text %)))))]
    (cond
      (< 1 (count buttons))
      (throw (ex-info "Ambiguous buttons found!"
                      {:event ::ambiguous-buttons-error
                       :message msg :regex btn-re  :buttons buttons :dummy dummy}))

      (zero? (count buttons))
      (throw (ex-info "Button not found!"
                      {:event ::button-not-found-error
                       :message msg :regex btn-re :dummy dummy}))

      :else
      (send-update {:callback_query
                    (merge base-cbq
                           {:message msg :data (-> buttons first :callback_data)})}))))


(defonce ^:private pre-checkout-queries (atom []))


(malli/=> send-pre-checkout-query [:=> [:cat spec.tg/User spec.tg/Invoice] :uuid])


(defn send-pre-checkout-query
  [dummy invoice]
  (let [pre-checkout-query-id (random-uuid)]
    (swap! pre-checkout-queries conj {:id pre-checkout-query-id :dummy dummy :invoice invoice :approved nil})
    (send-update {:pre_checkout_query {:id (str pre-checkout-query-id)
                                       :from dummy
                                       :currency (:currency invoice)
                                       :total_amount (->> (:prices invoice)
                                                          (map :amount)
                                                          (apply +))
                                       :invoice_payload (:payload invoice)}})
    pre-checkout-query-id))


(defn set-pre-checkout-query-status
  [pcq-id ok?]
  (tt/event! ::set-pre-checkout-query-status
             {:data {:pre-checkout-query-id pcq-id :ok? ok?}})
  (swap! pre-checkout-queries (fn [pcqs]
                                (mapv #(if (= (java.util.UUID/fromString pcq-id) (:id %))
                                         (assoc % :approved ok?) %) pcqs))))


(defn send-successful-payment
  [dummy]
  (let [{:keys [invoice]} (->> @pre-checkout-queries
                               (filter #(and (= dummy (:dummy %)) (:approved %)))
                               last)]
    (when (nil? invoice)
      (let [pcqs @pre-checkout-queries]
        (throw (tt/error! {:id ::no-approved-invoice-error
                           :data {:dummy dummy
                                  :pre-checkout-queries pcqs}}
                          (ex-info "No approved invoice!" {})))))
    (let [message (assoc (dummy->base-message dummy)
                         :successful_payment
                         {:currency (:currency invoice)
                          :total_amount (->> invoice :prices (map :amount) (apply +))
                          :invoice_payload (:payload invoice)
                          :telegram_payment_charge_id (str (random-uuid))
                          :provider_payment_charge_id (str (random-uuid))})]
      (dum/add-message message)
      (send-update {:message message}))))
