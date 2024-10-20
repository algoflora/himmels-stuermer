(ns himmelsstuermer.impl.errors
  (:require
    [clojure.stacktrace :as st]
    [clojure.string :as str]
    [himmelsstuermer.api :as api]
    [himmelsstuermer.button :as b]
    [himmelsstuermer.dynamic :refer [*user*]]
    [himmelsstuermer.user :refer [has-role?]]
    [malli.core :as m]
    [taoensso.timbre :as log]))


(defn handle-error
  ([ex] (handle-error (Thread/currentThread) ex))
  ([thr ex]
   (let [data (ex-data ex)
         data (cond-> data
                (= :malli.core/invalid-input (:type data))
                (assoc :explain (m/explain (-> data :data :input) (-> data :data :args)))

                (= :malli.core/invalid-output (:type data))
                (assoc :explain (m/explain (-> data :data :output) (-> data :data :value))))
         ekw  (or (:event data) :error-event)
         msg  (ex-message ex)
         st   (str/split (with-out-str (st/print-stack-trace ex)) #"\n") #_(take 5 (.getStackTrace ex))
         thrn (.getName thr)]
     (log/error ekw msg (merge data {:stacktrace st :thread thrn :cause (ex-cause ex) :is-error? true})))
   (when (some? *user*)
     (api/send-message *user*
                       (str "⚠️ Unexpected ERROR! ⚠️"
                            (if (has-role? :admin *user*)
                              (str "\n\n" (ex-message ex)) ""))
                       [[(b/text-btn "To Main Menu" 'himmelsstuermer.handler/delete-and-home)]]
                       :temp))
   #_(when (= :test conf/profile)
     (throw ex))))
