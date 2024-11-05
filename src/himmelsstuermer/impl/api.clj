(ns ^:no-doc himmelsstuermer.impl.api
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.walk :refer [postwalk]]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.core.logging :refer [throwable->map]]
    [himmelsstuermer.e2e.serve]
    [himmelsstuermer.impl.buttons :as ib]
    [himmelsstuermer.impl.callbacks :as clb]
    [himmelsstuermer.impl.transactor :refer [transact! get-txs TransactionsStorage]]
    [himmelsstuermer.misc :as misc]
    [himmelsstuermer.spec.core :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [me.raynes.fs :as fs]
    [missionary.core :as m]
    [org.httpkit.client :as http]
    [taoensso.telemere :as tt]))


(defn request
  [token method data]
  (let [url  (format "https://api.telegram.org/bot%s/%s" token (name method))

        {:keys [result nanos]}
        (misc/do-nanos* @(http/post url {:headers {"Content-Type" "application/json"}
                                         :body (generate-string data)}))

        resp (update result :body #(try (parse-string % true)
                                        (catch Throwable _ %)))]
    (tt/event! ::telegram-api-response
               {:data {:method method
                       :data data
                       :response resp
                       :time-millis (* 0.000001 nanos)}})
    (if (-> resp :body :ok)
      (postwalk #(if (instance? java.lang.Integer %) (long %) %)
                (-> resp :body :result))
      (let [exc (ex-info "Bad Telegram API response!"
                         {:method method
                          :status (:status resp)
                          :data data
                          :response resp})]
        (throw (tt/error! {:id ::bad-telegram-api-response
                           :data (ex-data exc)} exc))))))


(malli/=> api-task [:=> [:cat spec/UserState :keyword :map] spec/MissionaryTask])


(defn api-task
  [state method data]
  (m/sp (let [api-fn (case @conf/profile
                       :test (resolve 'himmelsstuermer.e2e.serve/request)
                       :aws  (resolve 'himmelsstuermer.impl.api/request))
              token  (-> state :bot :token)]
          (tt/event! ::calling-api-fn
                     {:data {:function api-fn
                             :profile @conf/profile
                             :method method
                             :data data}})
          (api-fn token method data))))


(malli/=> prepare-keyboard [:=>
                            [:cat
                             spec/UserState
                             [:maybe spec/Buttons]
                             spec/User
                             :boolean]
                            [:map
                             [:inline_keyboard [:vector
                                                [:vector
                                                 spec.tg/Button]]]]])


(defn prepare-keyboard
  [state kbd user modal?]
  (when kbd
    {:inline_keyboard
     (cond-> (mapv (fn [btns] (mapv #(b/to-map % state user) btns)) kbd)
       modal? (conj [(b/to-map (ib/->XButton) state user)]))}))


(defn- set-callbacks-message-id
  [state user msg]
  (clb/set-new-message-ids
    state
    user
    (:message_id msg)
    (->> msg
         :reply_markup :inline_keyboard flatten
         (map #(some-> % :callback_data java.util.UUID/fromString))
         (filterv some?))))


(defn- to-edit?
  [options user]
  (when (and (some? (:user/msg-id user)) (= (:message-id options) (:user/msg-id user)))
    (throw (ex-info "Manual editing of Main Message is forbidden!"
                    {:event ::manual-main-message-edit-error})))
  (if (:modal options)
    (some? (:message-id options))
    (pos-int? (:user/msg-id user))))


(defn- prepare-body
  [body options user]
  (cond-> body
    true                    (assoc :chat_id (:user/id user))
    (:markdown options)     (assoc :parse_mode "Markdown")
    (to-edit? options user) (assoc :message_id (or (:message-id options)
                                                   (:user/msg-id user)))))


(defmulti ^:private send-to-chat (fn [& args] (identity (first args))))


;; Media

;; (defn- create-media
;;   [type data]
;;   {:type (name type)
;;    :caption (:caption data)
;;    :media (:file data)})


;; (defn- prepare-to-multipart
;;   [args-map]
;;   (let [files     (atom {})
;;         args-map# (walk/postwalk
;;                     (fn [x]
;;                       (cond (instance? java.io.File x)
;;                             (let [file-id (keyword (str "fid" (java.util.UUID/randomUUID)))]
;;                               (swap! files assoc file-id x)
;;                               (str "attach://" (name file-id)))
;;                             (number? x) (str x)
;;                             :else x))
;;                     (assoc args-map :content-type :multipart))]
;;     (cond-> args-map#
;;       (contains? args-map# :media)        (update :media generate-string)
;;       (contains? args-map# :reply_markup) (update :reply_markup generate-string)
;;       true (merge @files))))


;; (defn- send-new-media-to-chat
;;   [type args-map media]
;;   (let [media-is-file? (instance? java.io.File (:media media))
;;         method         (keyword (str "send" (-> type name str/capitalize)))
;;         args-map#      (cond-> (merge args-map media)
;;                          true (dissoc :media)
;;                          true (assoc type (:media media))
;;                          media-is-file? (prepare-to-multipart))]
;;     (api-wrap method args-map#)))


;; (defn- edit-media-in-chat
;;   [type args-map media]
;;   (let [media-is-file? (instance? java.io.File (:media media))
;;         args-map#      (cond-> args-map
;;                          true (assoc :media media)
;;                          media-is-file? (prepare-to-multipart))]
;;     (try (api-wrap :editMessageMedia args-map#)
;;          (catch clojure.lang.ExceptionInfo ex
;;            (if (= 400 (-> ex ex-data :status))
;;              (send-new-media-to-chat type args-map media)
;;              (throw (ex-info "Request to :editMessageMedia failed!"
;;                              {:event ::edit-message-media-error
;;                               :args args-map#
;;                               :error ex})))))))


;; (defn- send-media-to-chat
;;   [type user data kbd optm]
;;   (let [media    (create-media type data)
;;         args-map (prepare-body {:reply_markup kbd} optm user)
;;         new-msg  (if (to-edit? optm user)
;;                    (edit-media-in-chat type args-map media)
;;                    (send-new-media-to-chat type args-map media))]
;;     (set-callbacks-message-id user new-msg)
;;     new-msg))


;; (defmethod send-to-chat :photo
;;   [& args]
;;   (apply send-media-to-chat args))


;; (defmethod send-to-chat :document
;;   [& args]
;;   (apply send-media-to-chat args))


(defmethod send-to-chat :invoice
  [_ state user b options]
  (m/sp (let [body (prepare-body b options user)
              new-msg (m/? (api-task state :sendInvoice body))]
          (m/? (set-callbacks-message-id state user new-msg)))))


(malli/=> send-message- [:=> [:cat spec/UserState :map] spec/MissionaryTask])


(defn- send-message-
  [state body]
  (m/sp (m/? (api-task state :sendMessage body))))


(malli/=> edit-message-text- [:=> [:cat spec/UserState :map] spec/MissionaryTask])


(defn- edit-message-text-
  [state body]
  (m/sp (try (m/? (api-task state :editMessageText body))
             (catch clojure.lang.ExceptionInfo ex
               (when (not= 400 (-> ex ex-data :status))
                 (throw (ex-info "Request to :editMessageText failed!"
                                 {:event ::edit-message-text-error
                                  :args body
                                  :error ex})))
               (tt/event! ::edit-message-failed
                          {:data {:request body
                                  :error ex}})
               (m/? (send-message- state body))))))


(defmethod send-to-chat :text
  [_ {:keys [txs] :as state} user b options]
  (m/sp (let [body       (prepare-body b options user)
              msg-task   (if (to-edit? options user)
                           (edit-message-text- state body)
                           (send-message- state body))
              new-msg    (m/? msg-task)
              new-msg-id (:message_id new-msg)]
          (tt/event! ::text-sent-to-chat {:data {:user user
                                                 :body body
                                                 :new-msg new-msg
                                                 :new-message-id new-msg-id}})
          (when (and (not (:modal options)) (not= new-msg-id (:msg-id user)))
            (tt/event! ::set-user-msg-id {:data {:message new-msg
                                                 :message-id new-msg-id}})
            (transact! txs [[:db/add (:db/id user) :user/msg-id new-msg-id]]))
          (m/? (set-callbacks-message-id state user new-msg))
          new-msg-id)))


(defmulti process-args (fn [a & _] a))


(defmethod process-args :text
  [_ state user & args]
  (let [opts (->> args (filter keyword?) set)]
    {:body    {:text         (->> args (filter string?) first)
               :reply_markup (prepare-keyboard
                               state
                               (->> args (filter vector?) first misc/remove-nils)
                               user (boolean (some #{:modal} opts)))
               :entities     (->> args (filter set?) first vec)}
     :options (into {:message-id (->> args (filter int?) first)}
                    (map #(vector % true) opts))}))


(defmethod process-args :invoice
  ([_ state user text data] (process-args :invoice state user text data []))
  ([_ state user text data kbd]
   {:body    (merge data {:reply_markup (prepare-keyboard
                                          state
                                          (into [[(b/pay-btn text)]] kbd)
                                          user true)})
    :options {:modal true}}))


(malli/=> send! [:=> [:cat :keyword spec/UserState spec/User [:* :any]] spec/MissionaryTask])


(defn send!
  [type state user & args]
  (m/sp (let [{:keys [body options]} (apply process-args type state user args)
              response (m/? (send-to-chat type state user body options))]
          (reify
            TransactionsStorage

            (get-txs [_] (-> state :txs get-txs))


            clojure.lang.IDeref

            (deref [_] response)))))


(defn- download-file
  [token file-path]
  (let [uri (format "https://api.telegram.org/file/bot%s/%s"
                    token file-path)
        ^java.io.ByteArrayInputStream bis  (-> uri http/get deref :body)
        file (fs/file fs/temp-dir (java.util.UUID/randomUUID))
        fos  (java.io.FileOutputStream. file)]
    (try
      (.transferTo bis fos)
      (finally
        (.close fos)))
    file))


(defn get-file
  [state file-id]
  (m/via m/blk (let [file-path (m/? (api-task state :getFile file-id))]
                 (if (fs/exists? file-path)
                   (fs/file file-path)
                   (download-file (-> state :bot :token) file-path)))))


(defn delete-message
  [state user mid]
  (m/join (constantly (:txs state))
          (clb/delete state user mid)
          (try (api-task state :deleteMessage {:chat_id (:user/id user)
                                               :message_id mid})
               (catch Exception exc
                 (when (not= 400 (-> exc ex-data :status))
                   (throw (ex-info "Request to :deleteMessage failed!"
                                   {:event ::delete-message-error
                                    :user user
                                    :message-id mid
                                    :error exc})))
                 (let [exc-map (throwable->map exc)]
                   (tt/event! ::delete-message-failed {:data {:user user
                                                              :message-id mid
                                                              :error exc-map}}))))))


(defn answer-pre-checkout-query
  ([state pcq-id] (answer-pre-checkout-query state pcq-id nil))
  ([state pcq-id error]
   (m/sp (m/? (api-task state :answerPrecheckoutQuery (into {:pre_checkout_query_id pcq-id
                                                             :ok (nil? error)}
                                                            (when (some? error)
                                                              [:error_message error]))))
         (:txs state))))
