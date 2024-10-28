(ns himmelsstuermer.core
  (:gen-class)
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [datahike.api :as d]
    [himmelsstuermer.core.dispatcher :as disp]
    [himmelsstuermer.core.logging :refer [reset-nano-timer! throwable->map]]
    [himmelsstuermer.core.state :as s]
    [himmelsstuermer.core.user :as u]
    [himmelsstuermer.impl.api :as api]
    [himmelsstuermer.impl.error :as err]
    [himmelsstuermer.impl.transactor :refer [get-txs]]
    [himmelsstuermer.spec.core :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [missionary.core :as m]
    [org.httpkit.client :as http]
    [taoensso.telemere :as tt]))


(defn- json-decode
  [s]
  (postwalk #(if (instance? java.lang.Integer %) (long %) %)
            (json/decode s keyword)))


(defmulti ^:private handle-update- (fn [a & _] a))


(defmethod handle-update- :message
  [_ {:keys [message] :as state}]
  (m/sp (tt/event! ::handle-message {:data {:message message}})
        (let [state' (m/? (u/load-to-state state (:from message)))]
          (s/modify-state state' #(update
                                    % :tasks
                                    conj (m/via m/blk (m/?
                                                        (api/delete-message (s/construct-user-state state')
                                                                            (:user state')
                                                                            (:message_id message)))))))))


(defmethod handle-update- :callback-query
  [_ {:keys [callback-query] :as state}]
  (tt/event! ::handle-callback-query {:data {:callback-query callback-query}})
  (u/load-to-state state (:from callback-query) (-> callback-query
                                                    :data
                                                    java.util.UUID/fromString)))


(defmethod handle-update- :pre-checkout-query ; TODO: Add comprehensive processing of pre-checkout-query
  [_ {:keys [pre-checkout-query] :as state}]
  (m/sp (tt/event! ::handle-pre-checkout-query {:data {:pre-checkout-query pre-checkout-query}})
        (s/modify-state state #(update
                                 % :tasks
                                 conj (m/via m/blk
                                             (m/?
                                               (api/answer-pre-checkout-query (s/construct-user-state state)
                                                                              (:id pre-checkout-query))))))))


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
              function  @(disp/resolve-action! (symbol (:method action)))]
          (s/modify-state state #(assoc % :function function :arguments arguments)))))


(malli/=> load-database [:-> spec/State spec/State])


(defn- load-database
  [s]
  (let [state (s/modify-state s #(assoc % :database @(-> s :system :db-conn)))
        db    (:database state)]
    (tt/event! ::loaded-database {:data {:database (str db)}})
    state))


(malli/=> handle-record [:=> [:cat spec/State spec/Record] spec/MissionaryTask])


(defn- handle-record
  [s record]
  (m/sp (let [body  (-> record :body json-decode)
              state (load-database s)]
          (cond
            (malli/validate spec.tg/Update body)
            (m/? (handle-update (s/modify-state state #(assoc % :update body))))

            (malli/validate spec/ActionRequest body)
            (m/? (handle-action (s/modify-state state #(assoc % :action (:action body)))))))))


(malli/=> combine-tasks [:-> spec/State [:set spec/MissionaryTask]])


(defn- combine-tasks
  [state]
  (let [user-state (s/construct-user-state state)]
    (cond-> (:tasks state)
      (some? (:function state))
      (conj (m/via m/blk (m/? ((:function state) user-state)))))))


(malli/=> perform-tasks [:=> [:cat spec/State [:set spec/MissionaryTask]] spec/MissionaryTask])


(defn- perform-tasks
  [state tasks]
  (m/sp (tt/event! ::performing-tasks {:data {:tasks tasks
                                              :state state}})
        (m/? (apply m/join
                    (fn [& args]
                      (into (:transaction state)
                            (mapcat get-txs) args))
                    tasks))))


(malli/=> persist-data [:=> [:cat spec/State [:set [:or :map [:vector :any]]]] spec/MissionaryTask]) ; TODO: Transaction spec?


(defn- persist-data
  [state tx-set]
  (m/via m/blk
         (tt/event! ::persisting-data {:data {:tx-set tx-set}})
         (d/transact (-> state :system :db-conn) (seq tx-set))))


(defn- execute-business-logic
  ([state tasks] (execute-business-logic state tasks false))
  ([state tasks fallback?]
   (m/sp (if fallback?
           (tt/event! ::executing-fallback {:data {:tasks tasks}})
           (tt/event! ::executing-business-logic {:data {:tasks tasks}}))
         (try (let [tx-set    (m/? (perform-tasks state tasks))
                    tx-report (sort-by first (map seq (:tx-data (m/? (persist-data state tx-set)))))]
                (tt/event! ::execute-finished {:data {:tx-set tx-set
                                                      :tx-report tx-report}}))
              (catch Exception exc
                (let [exc-map (throwable->map exc)]
                  (if fallback?
                    (throw (tt/error! {:id   ::fatal-error
                                       :data exc-map} exc))
                    (let [fallback-task (err/handle-error (s/construct-user-state state)
                                                          exc)]
                      (tt/error! {:id   ::business-logic-error
                                  :data exc-map} exc)
                      (m/? (execute-business-logic state #{fallback-task} true))))))))))


(malli/=> handle-core [:=> [:cat spec/State spec/Record] spec/MissionaryTask])


(defn handle-core
  [state record]
  (m/sp (tt/event! ::handle-core {:data {:record record}}) ; TODO: check "private" chats, "/start" command, etc...
        (let [state (m/? (handle-record state record))
              tasks (combine-tasks state)]
          (m/? (execute-business-logic state tasks)))))


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
                                         :records (:Records (json-decode body))}))
                                 invocations))
               (finally (s/shutdown! initial-state))))))


(def app
  (m/eduction
    (map (fn [{:keys [state records]}]
           (let [id (get-in state [:aws-context "lambda-runtime-aws-request-id"])]
             (try (m/? (m/reduce (constantly :processed)
                                 (m/ap (let [record (m/?> (m/seed records))]
                                         (reset-nano-timer!)
                                         (m/? (handle-core state record))
                                         (tt/event! ::record-processed)
                                         (tt/set-ctx! (assoc tt/*ctx* :state state))))))

                  (tt/event! ::invocation-response-ok {:data {:invocation-id id}})
                  (http/post (str runtime-api-url "invocation/" id "/response")
                             {:body "OK"})

                  (catch Exception exc
                    (let [exc-map (throwable->map exc)]
                      (tt/error! {:id   ::unhandled-exception
                                  :data exc-map}
                                 exc))
                    (http/post (str runtime-api-url "invocation/" id "/error")
                               {:body (json/encode (throwable->error-body exc))}))))))
    requests))


(defn -main
  [& _]
  (m/? (m/reduce conj app)))
