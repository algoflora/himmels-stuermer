(ns himmelsstuermer.core
  (:gen-class)
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [datalevin.core :as d]
    [himmelsstuermer.core.logging :refer [reset-nano-timer!]]
    [himmelsstuermer.core.state :as s]
    [himmelsstuermer.core.user :as u]
    [himmelsstuermer.impl.api :as api]
    [himmelsstuermer.impl.transactor :refer [get-txs]]
    [himmelsstuermer.spec.core :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [me.raynes.fs :as fs]
    [missionary.core :as m]
    [org.httpkit.client :as http]
    [taoensso.telemere :as tt]))


(defmulti ^:private handle-update- (fn [a & _] a))


(defmethod handle-update- :message
  [_ {:keys [message] :as state}]
  (m/sp (tt/event! ::handle-message {:data {:message message}})
        (let [state' (m/? (u/load-to-state state (:from message)))]
          (s/modify-state state' #(update
                                    % :tasks
                                    conj (api/delete-message (s/construct-user-state state')
                                                             (:user state')
                                                             (:message_id message)))))))


(defmethod handle-update- :callback-query
  [_ {:keys [callback-query] :as state}]
  (tt/event! ::handle-callback-query {:callback-query callback-query})
  (u/load-to-state state (:from callback-query) (-> callback-query
                                                    :data
                                                    java.util.UUID/fromString)))


(defmethod handle-update- :pre-checkout-query ; TODO: Add comprehensive processing of pre-checkout-query
  [_ {:keys [pre-checkout-query] :as state}]
  (m/sp (tt/event! ::handle-pre-checkout-query {:pre-checkout-query pre-checkout-query})
        (s/modify-state state #(update
                                 % :tasks
                                 conj (api/answer-pre-checkout-query state (:id pre-checkout-query))))))


(malli/=> hyphenize-kw [:-> [:fn #(and (keyword? %) (not (qualified-keyword? %)))] :keyword])


(defn- hyphenize-kw
  [kw]
  (-> kw name (str/replace #"_" "-") keyword))


(malli/=> handle-update [:-> spec/State spec/MissionaryTask])


(defn- handle-update
  [{:keys [update] :as state}]
  (m/sp
    (tt/event! ::handle-update {:data {:update update}})
    (let [type  (some #{:message :callback_query :pre_checkout_query} (keys update))
          type' (hyphenize-kw type)]
      (m/? (handle-update- type' (s/modify-state state #(assoc % type' (type update))))))))


(malli/=> handle-action [:-> spec/State spec/MissionaryTask])


(defn- handle-action
  [{:keys [action] :as state}]
  (m/sp (tt/event! ::handle-action {:data {:action action}})
        (let [arguments (:arguments action)
              function  (requiring-resolve (symbol (-> state :actions :namespace str) (:method action)))]
          (s/modify-state state #(assoc % :function function :arguments arguments)))))


(malli/=> handle [:=> [:cat spec/State spec/Record] spec/MissionaryTask])


(defn handle
  [state record]
  (m/sp (tt/event! ::handle-core {:data {:record record}}) ; TODO: check "private" chats, "/start" command, etc...
        (let [state'  (s/modify-state state #(assoc % :database (-> state :system :db-conn d/db)))
              _ (tt/event! ::loaded-database {:data {:database (-> state' :database)
                                                     :conn (-> state' :system :db-conn)}})
              body    (-> record :body (json/decode keyword))
              state'' (cond
                        (malli/validate spec.tg/Update body)
                        (m/? (handle-update (s/modify-state state' #(assoc % :update body))))

                        (malli/validate spec/ActionRequest body)
                        (m/? (handle-action (s/modify-state state' #(assoc % :action (:action body))))))
              user-state (s/construct-user-state state'')
              tasks (conj (:tasks state'') ((:function state'') user-state))]
          (tt/event! ::executing-business-logic {:tasks tasks
                                                 :user-state user-state})
          ;; TODO: Research situation when message sent, button clicked but transaction still not complete
          (let [txd (m/? (apply m/join (fn [& args]
                                         (into (:transaction state'')
                                               (mapcat get-txs)
                                               args))
                                tasks))
                _ (tt/event! ::transacting-data {:data {:tx-data txd}})
                tx-data (into []
                              (map (comp vec seq))
                              (:tx-data (d/transact! (-> state'' :system :db-conn) (seq txd))))]
            (tt/event! ::transacted-data {:data {:tx-data tx-data}})))))


(def runtime-api-url (str "http://" (System/getenv "AWS_LAMBDA_RUNTIME_API") "/2018-06-01/runtime/"))


;; API says not to use timeout when getting next invocation, so make it a long one
(def timeout-ms (* 1000 60 60 24))


(defn- throwable->error-body
  [^Throwable t]
  {:errorMessage (.getMessage t)
   :errorType    (-> t .getClass .getName)
   :stackTrace   (mapv str (.getStackTrace t))})


(def invocations
  (m/seed (repeatedly (fn []
                        @(http/get (str runtime-api-url "invocation/next")
                                   {:timeout timeout-ms})))))


(def requests
  (m/ap (let [initial-state (m/? s/state)]
          (tt/set-ctx! (merge tt/*ctx* {:state initial-state}))
          (try (m/?> (m/eduction (map (fn [{:keys [body headers]}]
                                        {:state   (s/modify-state initial-state
                                                                  #(assoc % :aws-context headers))
                                         :records (:Records (json/decode body keyword))}))
                                 invocations))
               (finally (s/shutdown! initial-state))))))


(def app
  (m/eduction
    (map (fn [{:keys [state records]}]
           (let [id (get-in state [:aws-context "lambda-runtime-aws-request-id"])]
             (try (m/? (m/reduce (constantly :processed)
                                 (m/ap (let [record (m/?> (m/seed records))]
                                         (reset-nano-timer!)
                                         (m/? (handle state record))
                                         (tt/event! ::record-processed)
                                         (tt/set-ctx! (assoc tt/*ctx* :state state))))))

                  (tt/event! ::invocation-response-ok {:invocation-id id})
                  (http/post (str runtime-api-url "invocation/" id "/response")
                             {:body "OK"})

                  (catch Exception ex
                    (tt/error! {:id ::unhandled-exception
                                :data ex}
                               ex)
                    (http/post (str runtime-api-url "invocation/" id "/error")
                               {:body (json/encode (throwable->error-body ex))}))))))
    requests))


(defn -main
  [& _]
  (m/? (m/reduce conj app)))


(comment
  (-main)

  (def t (m/ap (let [x 10]
                 (m/?> (m/seed [1 2 3 x])))))

  (m/? (m/reduce conj t))

  

  (m/? (m/reduce conj (try (m/eduction (map #(/ 100 %)) (m/seed [1 2 3])) (catch Exception ex (println ex)))))
  

  (def fl (m/seed [:a 1 2 3 :b [1 2] :c :q repeat 3 [:x 1] :q :d 1 2 3]))

  (defn construct-mapper
    []
    (let [counter (atom 0)]
      (fn [i]
        (when (keyword? i)
          (swap! counter inc))
        [@counter i])))
  
  (def f (m/eduction (map (construct-mapper))
                     (partition-by first)
                     (map #(map second %))
                     (map (fn [els]
                            (if (= :q (first els))
                              (when (fn? (second els))
                                (apply (second els) (drop 2 els)))
                              [els])))
                     cat fl))

  (m/? (m/reduce conj f))



  (do
    (require '[me.raynes.fs :as fs]
             '[datalevin.core :as d])

    (let [tmp-dir (str (fs/temp-dir "dtlv-sandbox"))
          cn (d/get-conn tmp-dir {:a/id  {:db/valueType :db.type/long}
                                  :a/ref {:db/valueType :db.type/ref}
                                  :b/id  {:db/valueType :db.type/keyword}})]
      (try (d/transact cn [{:a/id 1
                            :a/ref :zxc}
                           {:db/id :zxc
                            :b/id :A}])
           (finally
             (d/close cn)
             (fs/delete-dir tmp-dir)))))
  )
