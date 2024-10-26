(ns himmelsstuermer.e2e.dummy
  (:require
    [himmelsstuermer.core.init :refer [bot-default-language-code]]
    [himmelsstuermer.misc :refer [remove-nils]]
    [himmelsstuermer.spec.e2e :as spec.e2e]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(defonce ^:private dummies (atom {}))


(malli/=> create [:function
                  [:-> :keyword spec.tg/User]
                  [:=> [:cat :keyword :keyword] spec.tg/User]])


(defn- create
  ([key] (create key (:bot/default-language-code (m/? bot-default-language-code))))
  ([key lang]
   {:id (inc (count @dummies))
    :is_bot false
    :first_name (name key)
    :username (name key)
    :language_code (name lang)}))


(malli/=> new [:-> :keyword spec.e2e/DummyEntry])


(defn new
  [k]
  (k (swap! dummies assoc k {:dummy (create k) :messages []})))


(defn clear-all
  []
  (reset! dummies {}))


(malli/=> dump-all [:=> [:cat] [:map-of :keyword spec.e2e/DummyEntry]])


(defn dump-all
  []
  @dummies)


(malli/=> restore [:-> [:maybe [:map-of :keyword spec.e2e/DummyEntry]] :any])


(defn restore
  [data]
  (reset! dummies (or data {})))


(malli/=> get-by-chat-id [:-> :int spec.e2e/DummyEntry])


(defn- get-by-chat-id
  [chat-id]
  (->> (vals @dummies)
       (filter #(= (get-in % [:dummy :id]) chat-id))
       first))


(malli/=> exists? [:-> :keyword :boolean])


(defn exists?
  [key]
  (contains? @dummies key))


(malli/=> get-by-key [:-> :keyword spec.e2e/DummyEntry])


(defn get-by-key
  [k]
  (k @dummies))


(malli/=> get-last-messages [:=>
                             [:cat spec.tg/User :int [:or :boolean :nil]]
                             [:vector spec.tg/Message]])


(defn get-last-messages
  [dummy n own?]
  (->> dummy :username keyword ((deref dummies)) :messages
       (filter #(case own?
                  true  (= (:id dummy) (-> % :from :id))
                  false (not= (:id dummy) (-> % :from :id))
                  nil   true))
       (take-last n)
       (into [])))


(malli/=> get-first-message [:-> spec.tg/User spec.tg/Message])


(defn get-first-message
  [dummy]
  (-> dummy :username keyword ((deref dummies)) :messages first))


(defn- prepare-request
  [req]
  (cond ; TODO: Think about it...
    (malli/validate spec.tg/SendMessageRequest req) (identity req)
    (malli/validate spec.tg/Message req)            (identity req)
    (malli/validate spec.tg/SendInvoiceRequest req) (let [root-keys [:chat_id :reply_markup]]
                                                      (assoc (select-keys req root-keys)
                                                             :invoice
                                                             (apply dissoc req root-keys)))))


(malli/=> add-message [:=> [:cat [:or
                                  spec.tg/SendMessageRequest
                                  spec.tg/SendInvoiceRequest
                                  spec.tg/Message]]
                       spec.tg/Message])


(defn add-message
  [r]
  (let [req (prepare-request r)
        {:keys [dummy messages]} (get-by-chat-id (or (:chat_id req) (-> req :chat :id)))
        base-message {:message_id (-> messages
                                      last
                                      :message_id
                                      (or 0)
                                      inc)
                      :from {:id 0
                             :is_bot true
                             :first_name "himmelsstuermer"
                             :last_name "e2e"
                             :username "himmelsstuermer.e2e"
                             :language_code "clj"}
                      :chat {:id (:id dummy)
                             :type "private"}
                      :date (System/currentTimeMillis)}
        message (merge base-message (dissoc req :chat_id))]
    (swap! dummies (fn [dms]
                     (update-in dms [(-> dummy :username keyword) :messages]
                                conj message)))
    (let [dummy ((-> dummy :username keyword) @dummies)]
      (tt/event! ::added-message {:data {:message message
                                         :dummy dummy}}))
    (first (get-last-messages dummy 1 nil))))


(malli/=> find-message-index [:=> [:cat [:vector spec.tg/Message] :int] :int])


(defn- find-message-index
  [messages message-id]
  (let [idxs (keep-indexed #(when (= message-id (:message_id %2)) %1) messages)]
    (cond
      (< 1 (count idxs))   (throw (ex-info "Ambiguous messages found!"
                                           {:event ::ambiguous-messages-error
                                            :messages messages :message_id message-id}))
      (zero? (count idxs)) (throw (ex-info "Message not found!"
                                           {:event ::message-not-found-error
                                            :messages messages :message_id message-id}))
      :else (first idxs))))


(malli/=> update-message-text [:-> spec.tg/EditMessageTextRequest spec.tg/Message])


(defn update-message-text
  [req]
  (let [{:keys [dummy messages]} (get-by-chat-id (or (:chat_id req) (-> req :chat :id)))
        msg-idx (find-message-index messages (:message_id req))
        k (-> dummy :username keyword)
        dummies' (swap! dummies
                        (fn [dms]
                          (update-in
                            dms [k :messages]
                            (fn [msgs]
                              (update msgs
                                      msg-idx
                                      #(merge % (remove-nils
                                                  {:text (:text req)
                                                   :entities (:entities req)
                                                   :reply_markup (:reply_markup req)})))))))
        message (get-in dummies' [k :messages msg-idx])
        dummy' (k dummies')]
    (tt/event! ::updated-message-text {:data {:message message
                                              :dummy dummy'}})
    message))


(malli/=> delete-message [:=> [:cat :int :int] :boolean])


(defn delete-message
  [chat-id message-id]
  (let [dummy (:dummy (get-by-chat-id chat-id))
        k     (-> dummy :username keyword)]
    (swap! dummies (fn [dms]
                     (update-in dms [k :messages]
                                (fn [msgs]
                                  (into [] (filter #(not= message-id (:message_id %)) msgs))))))
    (let [dummy' (k @dummies)]
      (tt/event! ::deleted-message {:data {:mesage-id message-id
                                           :dummy dummy'}}))
    (empty? (filter #(= message-id (:message_id %)) (-> @dummies k :messages)))))
