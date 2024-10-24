(ns himmelsstuermer.impl.db
  (:require
    [taoensso.telemere :as tt]))


(def ^:dynamic *tx* nil)


(defn transact
  [tx-data]
  (when (nil? *tx*)
    (let [st (into [] (map str) (.getStackTrace (Thread/currentThread)))]
      (tt/event! ::nil-tx {:data {:st st}})))
  (let [tx (swap! *tx* into tx-data)]
    (tt/event! ::added-transact-data {:data {:new-tx-data tx-data
                                             :tx-data tx}})))
