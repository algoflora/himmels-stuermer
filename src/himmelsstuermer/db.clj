(ns himmelsstuermer.db
  (:require
    [himmelsstuermer.impl.db :as impl]))


(def ^:dynamic *db* nil)


(defn transact
  [tx-data]
  (impl/transact tx-data))
