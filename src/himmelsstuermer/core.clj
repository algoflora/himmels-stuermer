(ns himmelsstuermer.core
  (:gen-class)
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [datomic.client.api :as d]
    [himmelsstuermer.core.dispatcher :as disp]
    [himmelsstuermer.core.init]
    [himmelsstuermer.core.logging :refer [init-logging! reset-nano-timer! throwable->map]]
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
        (if (= "private" (-> message :chat :type))
          (let [state' (m/? (u/load-to-state state (:from message) nil (= "/start" (:text message))))]
            (s/modify-state state' #(update
                                      % :tasks
                                      conj (m/via m/blk (m/?
                                                          (api/delete-message (s/construct-user-state state')
                                                                              (:user state')
                                                                              (:message_id message)))))))
          (let [exc (ex-info "Message from non-private chat!" {:message message})]
            (tt/error! {:id ::non-private-chat-message
                        :data (throwable->map exc)} exc)))))


(defmethod handle-update- :callback-query
  [_ {:keys [callback-query] :as state}]
  (tt/event! ::handle-callback-query {:data {:callback-query callback-query}})
  (u/load-to-state state (:from callback-query) (-> callback-query
                                                    :data
                                                    java.util.UUID/fromString) false))


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


(defn load-database
  [s]
  (let [state (s/modify-state s #(assoc % :database (-> s :system :db-conn d/db)))
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
         (d/transact (-> state :system :db-conn) {:tx-data (seq tx-set)})))


(defn- execute-business-logic
  ([state tasks] (execute-business-logic state tasks false))
  ([state tasks fallback?]
   (m/sp (if fallback?
           (tt/event! ::executing-fallback {:data {:tasks tasks}})
           (tt/event! ::executing-business-logic {:data {:tasks tasks}}))
         (try (let [tx-set    (m/? (perform-tasks state tasks))
                    tx-report (sort (map str (:tx-data (m/? (persist-data state tx-set)))))]
                (tt/event! ::execute-finished {:data {:tx-set tx-set
                                                      :tx-report tx-report}}))
              (catch Exception exc
                (let [exc-map (throwable->map exc)]
                  (if fallback?
                    (throw (tt/error! {:id   ::fatal-error
                                       :data exc-map} exc))
                    (let [_ (tt/error! {:id   ::business-logic-error
                                        :data exc-map} exc)
                          fallback-task (err/handle-error (s/construct-user-state state) exc)]

                      (m/? (execute-business-logic state #{fallback-task} true))))))))))


(malli/=> handle-core [:=> [:cat spec/State spec/Record] spec/MissionaryTask])


(defn handle-core
  [state record]
  (m/sp (tt/event! ::handle-core {:data {:record record}}) ; TODO: check "private" chats, "/start" command, etc...
        (let [state (m/? (handle-record state record))
              tasks (combine-tasks state)]
          (m/? (execute-business-logic state tasks)))))


(defn runtime-api-url
  [path]
  (str "http://" (System/getenv "AWS_LAMBDA_RUNTIME_API") "/2018-06-01/runtime/" path))


;; API says not to use timeout when getting next invocation, so make it a long one
(def timeout-ms (* 1000 60 60 24))


(defn- throwable->error-body
  [^Throwable t]
  {:errorMessage (.getMessage t)
   :errorType    (-> t .getClass .getName)
   :stackTrace   (mapv str (.getStackTrace t))})


(def invocations
  (m/seed (repeatedly
            (fn []
              (m/via m/blk (let [url (runtime-api-url "invocation/next")]
                             (tt/event! ::invocation-next-request {:data {:url url
                                                                          :timeout timeout-ms}})
                             @(http/get url {:timeout timeout-ms})))))))


(def events
  (m/ap (let [initial-state (m/? s/state)
              invoation-task (m/?> invocations)
              {:keys [body headers]} (m/? invoation-task)]
          (tt/set-ctx! (assoc tt/*ctx* :aws-context headers))
          {:state   (s/modify-state initial-state
                                    #(assoc % :aws-context headers))
           :records (:Records (json-decode body))})))


(def app
  (m/ap (let [{:keys [state records]} (m/?> events)
              id (-> state :aws-context :lambda-runtime-aws-request-id)]
          (try (m/? (m/reduce (constantly :processed)
                              (m/ap (let [record (m/?> (m/seed records))]
                                      (reset-nano-timer!)
                                      (m/? (handle-core state record))
                                      (tt/event! ::record-processed)
                                      #_(tt/set-ctx! (assoc tt/*ctx* :state state))))))

               (let [aws-response @(http/post (runtime-api-url (format "invocation/%s/response" id))
                                              {:body "OK"})]
                 (tt/event! ::invocation-response-ok {:data {:invocation-id id
                                                             :aws-response  aws-response}}))


               (catch Exception exc
                 (let [exc-map (throwable->map exc)
                       aws-response (http/post (runtime-api-url (format "invocation/%s/error" id))
                                               {:body (json/encode (throwable->error-body exc))})]
                   (tt/error! {:id   ::unhandled-exception
                               :data {:invocation-id id
                                      :error         exc-map
                                      :aws-response  aws-response}}
                              exc)))
               (finally (tt/set-ctx! (dissoc tt/*ctx* :aws-context)))))))


(defn run
  [& _]
  (init-logging!)
  (tt/event! ::start-main {:let [env (System/getenv)]
                           :data {:main "-main"
                                  :environment env}})
  (m/? (m/reduce conj app)))


(defn -main
  [& args]
  (apply run args))


(comment

  (def t (m/via m/blk (+ 1 2 3)))

  (m/? t)
  
  (def fl (m/seed (repeatedly 3 (fn [] (m/via m/blk
                                         (println "REQUEST")
                                         @(http/get "http://ifconfig.me"))))))

  (println "--------")
  (m/? (m/reduce conj (m/ap (let [resp (m/?> fl)]
                              (m/? (m/sleep 1000))
                              (println "RESPONSE" (subs (:body (m/? resp)) 0 10))))))
  )
