(ns himmelsstuermer.handler
  (:require
    [himmelsstuermer.api :as api]))


(defn delete-this-message

  "Handler to delete message. Deletes the message with was called from. Cleanups callbacks"

  [{:keys [usr cbq] :as state}]
  (api/delete-message state usr (-> cbq :message :message_id)))


(defn main

  "Core handler of system. Must be overriden in project."

  [{:keys [usr] :as state}]
  (api/send-message state usr
                    "Hello from Himmelsstuermer Framework!" []))


(defn payment

  "Payments handler. Must be overriden in project if payments processing is necessary."

  [{usr :usr {payment :successful_payment} :msg :as state}]
  (api/send-message state usr
                    (str "Successful payment with payload "
                         (:invoice_payload payment))
                    [] :modal))
