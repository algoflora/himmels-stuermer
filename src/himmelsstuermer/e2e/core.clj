(ns himmelsstuermer.e2e.core
  (:require
    [himmelsstuermer.e2e.client :as cl]
    [himmelsstuermer.e2e.dummy :as dum]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as m]
    [taoensso.telemere :as tt]))


(defmulti ^:private serve (fn [method _] method))

(m/=> request [:=> [:cat :keyword spec.tg/Request] :any])


(defn request
  [method body]
  (tt/event! ::e2e-request-received
             {:method method
              :body body})
  (serve method body))


(defmethod serve :sendMessage
  [_ req]
  (dum/add-message req))


(defmethod serve :editMessageText
  [_ req]
  (dum/update-message-text req))


(defmethod serve :deleteMessage
  [_ {:keys [chat_id message_id]}]
  (dum/delete-message chat_id message_id))


(defmethod serve :sendInvoice
  [_ req]
  (dum/add-message req))


(defmethod serve :answerPrecheckoutQuery
  [_ req]
  (cl/set-pre-checkout-query-status (:pre_checkout_query_id req) (:ok req)))
