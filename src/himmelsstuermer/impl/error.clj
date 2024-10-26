(ns himmelsstuermer.impl.error
  (:require
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.user :refer [has-role?]]
    [missionary.core :as m]))


(defn handle-error
  [{:keys [usr] :as state} exc]
  (api/send-message state usr
                    (str "⚠️ Unexpected ERROR! ⚠️"
                         (if (has-role? state :admin usr)
                           (str "\n\n" (ex-message exc)) ""))
                    [[(b/text-btn "To Main Menu" 'delete-and-home)]]
                    :temp))


(defn delete-and-home

  "Handler to delete message. Deletes the message with was called from. Cleanups callbacks. Afterwards run `main` handler"

  [{:keys [usr cbq] :as state}]
  (m/join (constantly (:txs state))
          (api/delete-message state usr (-> cbq :message :message_id))
          ((requiring-resolve (:himmelsstuermer/main-handler state)) state)))
