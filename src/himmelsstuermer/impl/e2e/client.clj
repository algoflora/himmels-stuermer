(ns himmelsstuermer.impl.e2e.client
  (:require
    [himmelsstuermer.impl.e2e.dummy :as dum]
    [himmelsstuermer.spec.action :as spec.act]
    [himmelsstuermer.spec.commons :refer [Regexp]]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as m]
    [taoensso.timbre :as log]
    [tick.core :as t]))


(defonce ^:private update-id (atom 0))

(m/=> send-update [:-> spec.tg/UpdateData :any])


(defn- send-update
  [data]
  (let [handler (requiring-resolve 'himmelsstuermer.core/handler)
        update (assoc data :update_id (swap! update-id inc))]
    (log/debug ::send-update {:handler handler :update update})
    (handler update)))


(m/=> send-action-request [:-> spec.act/ActionRequest :any])


(defn- send-action-request
  [action-request]
  (let [handler (requiring-resolve 'himmelsstuermer.core/handler)]
    (handler action-request)))


(m/=> dummy-kw?->dummy [:-> [:or spec.tg/User :keyword] spec.tg/User])


(defn- dummy-kw?->dummy
  [dummy]
  (if (keyword? dummy) (-> dummy dum/get-by-key :dummy) dummy))


(m/=> dummy->base-message [:-> [:or spec.tg/User :keyword] spec.tg/BaseMessage])


(defn- dummy->base-message
  [dummy-kw?]
  (let [dummy (dummy-kw?->dummy dummy-kw?)
        message-id (-> (dum/get-last-messages dummy 1 nil) first :message_id (or 0) inc)]
    {:message_id message-id
     :from dummy
     :chat {:id (:id dummy)
            :type "private"}
     :date (System/currentTimeMillis)}))


(m/=> call-action [:=> [:cat :keyword :map] :any])


(defn call-action
  [type args-map]
  (let [action-request {:action {:method (name type)
                                 :arguments args-map
                                 :timestamp (t/millis (t/between (t/epoch) (t/inst)))}}]
    (send-action-request action-request)))


(m/=> send-text [:=>
                 [:cat [:or spec.tg/User :keyword] :string [:vector spec.tg/MessageEntity]]
                 :any])


(defn send-text
  [dummy text entities]
  (let [message (merge (dummy->base-message dummy)
                       {:text text :entities entities})]
    (log/debug ::send-text-1)
    (dum/add-message message)
    (log/debug ::send-text-2)
    (send-update {:message message})
    (log/debug ::send-text-3)))


(m/=> dummy->base-callback-query [:-> [:or spec.tg/User :keyword] spec.tg/BaseCallbackQuery])


(defn- dummy->base-callback-query
  [dummy-kw?]
  (let [dummy (dummy-kw?->dummy dummy-kw?)]
    {:id (str (java.util.UUID/randomUUID))
     :from dummy}))


(m/=> click-btn [:=>
                 [:cat
                  [:or spec.tg/User :keyword]
                  spec.tg/Message
                  Regexp]
                 :any])


(defn click-btn
  [dummy msg btn-re]
  (let [base-cbq (merge (dummy->base-callback-query dummy))
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


(m/=> send-pre-checkout-query [:=> [:cat spec.tg/User spec.tg/Invoice] :uuid])


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
  (log/debug ::set-pre-checkout-query-status {:pre-checkout-query-id pcq-id :ok? ok?})
  (swap! pre-checkout-queries (fn [pcqs]
                                (mapv #(if (= (java.util.UUID/fromString pcq-id) (:id %))
                                         (assoc % :approved ok?) %) pcqs))))


(defn send-successful-payment
  [dummy]
  (let [{:keys [invoice]} (->> @pre-checkout-queries
                               (filter #(and (= dummy (:dummy %)) (:approved %)))
                               last)]
    (when (nil? invoice)
      (throw (ex-info "No approved invoice!"
                      {:event ::no-approved-invoice-error
                       :dummy dummy
                       :pre-checkout-queries @pre-checkout-queries})))
    (send-update {:message (assoc (dummy->base-message dummy)
                                  :successful_payment
                                  {:currency (:currency invoice)
                                   :total_amount (->> invoice :prices (map :amount) (apply +))
                                   :invoice_payload (:payload invoice)
                                   :telegram_payment_charge_id (str (random-uuid))
                                   :provider_payment_charge_id (str (random-uuid))})})))
