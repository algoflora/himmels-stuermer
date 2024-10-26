(ns himmelsstuermer.api.transactor
  (:require
    [himmelsstuermer.impl.transactor :as impl]))


(defn transact!
  [txs tx-data]
  (impl/transact! txs tx-data))
