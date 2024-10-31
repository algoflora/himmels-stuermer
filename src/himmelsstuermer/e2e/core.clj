(ns himmelsstuermer.e2e.core
  (:require
    [himmelsstuermer.e2e.client :as cl]
    [himmelsstuermer.e2e.dummy :as dum]
    [taoensso.telemere :as tt]))


(defmulti serve (fn [method _] method))


(defmethod serve :sendMessage
  [_ req]
  (tt/event! ::serve-sendMessage {:data {:request req}})
  (dum/add-message req))


(defmethod serve :editMessageText
  [_ req]
  (tt/event! ::serve-editMessageText {:data {:request req}})
  (dum/update-message-text req))


(defmethod serve :deleteMessage
  [_ {:keys [chat_id message_id] :as req}]
  (tt/event! ::serve-deleteMessage {:data {:request req}})
  (dum/delete-message chat_id message_id))


(defmethod serve :sendInvoice
  [_ req]
  (tt/event! ::serve-sendInvoice {:data {:request req}})
  (dum/add-message req))


(defmethod serve :answerPrecheckoutQuery
  [_ req]
  (tt/event! ::serve-answerPrecheckoutQuery {:data {:request req}})
  (cl/set-pre-checkout-query-status (:pre_checkout_query_id req) (:ok req)))
