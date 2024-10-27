(ns himmelsstuermer.impl.transactor
  (:require
    [clojure.data :as data]
    [himmelsstuermer.misc :as misc]
    [hyperfiddle.rcf :as rcf :refer [tests tap %]]
    [taoensso.telemere :as tt]))


(defprotocol TransactionsStorage

  (get-txs   [this]))


(defprotocol TransactionsAccumulator

  (transact! [this tx-data]))


(defrecord Transactions
  [^clojure.lang.Atom txs]

  TransactionsAccumulator

  (transact!
    [this tx-data]
    (let [caller (misc/get-caller)
          old-txs @txs]
      (swap! txs into tx-data)
      (let [[_ new-tx-data _] (data/diff old-txs @txs)]
        (tt/event! ::added-transactions {:data {:transactions-accumulator-object this
                                                :caller-info caller
                                                :new-tx-data new-tx-data}}))
      this))


  TransactionsStorage

  (get-txs
    [this]
    (let [caller (misc/get-caller)]
      (tt/event! ::flushed-transactions {:data {:transaction-storage-object this
                                                :caller-info caller}})
      @txs)))


(defn new-transactions-set
  []
  (->Transactions (atom #{})))
