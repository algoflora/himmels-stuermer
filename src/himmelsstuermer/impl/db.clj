(ns himmelsstuermer.impl.db)


(def ^:dynamic *tx* nil)


(defn transact
  [tx-data]
  (swap! *tx* into tx-data))
