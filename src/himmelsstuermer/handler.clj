(ns himmelsstuermer.handler
  (:require
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.vars :refer [*user* *msg*]]))


(defn delete-this-message

  "Handler to delete message. Deletes the message with was called from. Cleanups callbacks"

  {:pre [(number? *msg*)
         (map? *user*)]}

  [_]
  (api/delete-message *user* *msg*))


(defn main

  "Core handler of system. Must be overriden in project."

  [_]
  (api/send-message *user*
                    "Hello from Himmelsstuermer Framework!" []))


(defn payment

  "Payments handler. Must be overriden in project if payments processing is necessary."

  [{payment :successful_payment}]
  (api/send-message *user*
                    (str "Successful payment with payload " (:invoice_payload payment)) [] :temp))
