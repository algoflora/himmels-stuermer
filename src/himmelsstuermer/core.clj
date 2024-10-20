(ns himmelsstuermer.core
  (:gen-class)
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [datalevin.core :as d]
    [himmelsstuermer.api]
    [himmelsstuermer.core.state :as s]
    [himmelsstuermer.core.user :as u]
    [himmelsstuermer.db]
    [himmelsstuermer.impl.api :as api]
    [himmelsstuermer.impl.core :as hs]
    [himmelsstuermer.impl.db]
    [himmelsstuermer.spec :as spec]
    ;; [himmelsstuermer.spec.action :as spec.act]
    [himmelsstuermer.spec.aws :as spec.aws]
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [me.raynes.fs :as fs]
    [missionary.core :as m]
    [org.httpkit.client :as http]
    [taoensso.telemere :as tt]))


;; (malli/=> handle-action [:-> spec.act/ActionRequest :any])


;; (defn- handle-action
;;   [{:keys [action] {:keys [method arguments]} :action}]
;;   (if-let [action-fn (resolve (symbol (str (app/handler-actions-namespace)) method))]
;;     (log/info ::action-success
;;               "Action '%s' completed successfully" type
;;               {:action action
;;                :ok true
;;                :response (action-fn arguments)})
;;     (throw (ex-info "Wrong action type!"
;;                     {:event ::wrong-action-error
;;                      :action-type type}))))


(defmulti handle-update- (fn [type _] type))


(defmethod handle-update- :message
  [_ {:keys [message] :as state}]
  (m/sp (tt/event! ::handle-message {:message message})
        (let [state' (m/? (u/load-to-state state (:from message)))]
          (s/modify-state state' #(update
                                    % :tasks
                                    conj (api/delete-message (:user state') (:message_id message)))))))


(defmethod handle-update- :callback-query
  [_ {:keys [callback-query] :as state}]
  (m/sp (tt/event! ::handle-callback-query {:callback-query callback-query})
        (m/? (u/load-to-state state (:from callback-query) (-> callback-query
                                                               :data
                                                               java.util.UUID/fromString)))))


(defmethod handle-update- :pre-checkout-query ; TODO: Add comprehensive processing of pre-checkout-query
  [_ {:keys [pre-checkout-query] :as state}]
  (m/sp (tt/event! ::handle-pre-checkout-query {:pre-checkout-query pre-checkout-query})
        (s/modify-state state #(update
                                 % :tasks
                                 conj (api/answer-pre-checkout-query (:id pre-checkout-query))))))


(malli/=> hyphenize-kw [:-> [:fn #(and (keyword? %) (not (qualified-keyword? %)))] :keyword])


(defn- hyphenize-kw
  [kw]
  (-> kw name (str/replace #"_" "-") keyword))


(malli/=> handle-update [:-> spec/State spec/MissionaryTask])


(defn- handle-update
  [{:keys [update] :as state}]
  (m/sp
    (tt/event! ::handle-update {:update update})
    (let [type  (some #{:message :callback_query :pre_checkout_query} (keys update))
          type' (hyphenize-kw type)]
      (m/? (handle-update- type' (s/modify-state state #(assoc % type' (type update))))))))


(malli/=> handle-action [:-> spec/State spec/MissionaryTask])


(defn- handle-action
  [{:keys [action] :as state}]
  (m/sp (tt/event! ::handle-action {:action action})
        (let [args (:arguments action)
              task (apply
                     (requiring-resolve (symbol (-> state :actions :namespace str) (:method action)))
                     (if (sequential? args) args [args]))]
          (s/modify-state state #(update % :tasks conj task)))))


(malli/=> handle [:=> [:cat spec/State spec.aws/Record] spec/MissionaryTask])


(defn handle
  [state record]
  (m/sp (tt/event! ::handle-core {:record record}) ; TODO: check "private" chats, "/start" command, etc...
        (let [state'  (s/modify-state state #(assoc % :database (-> state :system :db-conn d/db)))
              body    (-> record :body (json/decode keyword))
              state'' (cond
                        (malli/validate spec.tg/Update body)
                        (m/? (handle-update (s/modify-state state' #(assoc % :update body))))

                        (malli/validate spec/ActionRequest body)
                        (m/? (handle-action (s/modify-state state' #(assoc % :action (:action body))))))
              tx-data (atom (:transaction state''))]
          (tt/event! ::executing-business-logic)
          (binding [himmelsstuermer.db/*db*      (:database state'')
                    himmelsstuermer.api/*user*   (:user state'')
                    himmelsstuermer.impl.db/*tx* tx-data]
            (m/? (apply m/join (constantly nil) (:tasks state''))))
          ;; TODO: Research situation when message sent, button clicked but transaction still not complete
          (tt/event! ::transact-data {:tx-data @tx-data
                                      :tx-report (d/transact! (-> state'' :system :db-conn) @tx-data)})
          (tt/set-ctx! nil))))


(malli/=> shutdown! [:-> spec/State :any])


(def runtime-api-url (str "http://" (System/getenv "AWS_LAMBDA_RUNTIME_API") "/2018-06-01/runtime/"))


;; API says not to use timeout when getting next invocation, so make it a long one
(def timeout-ms (* 1000 60 60 24))


(defn- throwable->error-body
  [t]
  {:errorMessage (.getMessage t)
   :errorType    (-> t .getClass .getName)
   :stackTrace   (mapv str (.getStackTrace t))})


(def invocations
  (m/seed (repeatedly @(http/get (str runtime-api-url "invocation/next")
                                 {:timeout timeout-ms}))))


(def app
  (m/ap (let [initial-state (m/? s/state)]
          (try (let [{:keys [body
                             headers]} (m/?> invocations)
                     id                (get headers "lambda-runtime-aws-request-id")
                     state             (merge initial-state {:aws-context headers})]

                 (tt/set-ctx! (merge tt/*ctx* {:state state}))

                 (try (m/?
                        (m/reduce conj
                                  (m/ap
                                    (let [record (m/?> (m/seed (:Records (json/decode body keyword))))]
                                      (m/? (hs/handle state record))))))
                      (tt/event! ::invocation-response-ok {:invocation-id id})
                      (http/post (str runtime-api-url "invocation/" id "/response")
                                 {:body "OK"})

                      (catch Exception ex
                        (tt/error! ::unhandled-exceprion ex)
                        (http/post (str runtime-api-url "invocation/" id "/error")
                                   {:body (json/encode (throwable->error-body ex))}))))
               (finally (s/shutdown! initial-state))))))


(defn -main
  [& args]
  (m/? (m/reduce conj app)))


(comment
  (require '[datalevin.core :as d]
           '[me.raynes.fs :as fs])

  (let [dir (str (fs/temp-dir ""))
        cn  (d/get-conn dir)]

    (d/transact! cn [[:db/add -1 :a/id 1]
                     [:db/add -2 :a/id 2]
                     [:db/add -3 :a/id 3]])

    (d/q '[:find ?a
           :in $ ?ids
           :where
           [?a :a/id ?id]
           (not-join [?id]
                     [(contains? ?ids ?id)])] (d/db cn) #{1  3}))



  (require '[missionary.core :as m])

  (def t1 (m/sp (println "PLUS")  (+ 1 1)))
  (def t2 (m/sp (println "MINUS") (- 1 1)))

  (m/? (apply m/join vector [t1 t2])))
