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


(defn- get-tempid
  [txs]
  (->> txs
       (map second)
       (filter neg-int?)
       (concat [0])
       (apply min)
       dec))


(defn- add-tx-map
  [txs m]
  (let [eid (or (:db/id m) (get-tempid txs))]
    (into txs (map #(vector :db/add eid (key %) (val %))) (dissoc m :db/id))))


(defn- add-tx-maps
  [txs ms]
  (reduce (fn [acc m] (add-tx-map acc m)) txs ms))


(defrecord Transactions
  [^clojure.lang.Atom txs]

  TransactionsAccumulator

  (transact!
    [this tx-data]
    (let [caller (misc/get-caller)
          old-txs @txs
          ;; TODO: Maybe add validation of args
          maps-with-eids    (not-empty (filter #(and (map? %) (some? (:db/id %))) tx-data))
          maps-without-eids (not-empty (filter #(and (map? %) (nil? (:db/id %))) tx-data))
          vecs (not-empty (filter vector? tx-data))]
      (swap! txs #(cond-> %
                    (some? vecs)              (into vecs)
                    (some? maps-with-eids)    (add-tx-maps maps-with-eids)
                    (some? maps-without-eids) (add-tx-maps maps-without-eids)))
      (let [[_ new-tx-data _] (data/diff old-txs @txs)]
        (tt/event! ::added-transactions {:data {:transactions-accumulator-object this
                                                :caller-info caller
                                                :new-tx-data new-tx-data
                                                :tx-data @txs}}))
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


(tests
  (let [txs (new-transactions-set)]
    (tap (get-txs txs))
    (transact! txs [[:db/add 1 :attr-a :a]])
    (tap (get-txs txs))
    (transact! txs [[:db/add 1 :attr-b :b]])
    (transact! txs [[:db/add 2 :attr-a (keyword "a")]])
    (tap (get-txs txs))
    (transact! txs [{:db/id 2
                     :attr-a :a
                     :attr-b :b
                     :attr-c :c}
                    {:attr-a :a
                     :attr-b :b
                     :attr-c :c}
                    {:db/id -1
                     :attr-a :AAA}
                    {:attr-a :A
                     :attr-b :B
                     :attr-c :C}
                    [:db/add 1 :attr-a :a]])
    (tap (get-txs txs)))

  % := #{}
  % := #{[:db/add 1 :attr-a :a]}
  % := #{[:db/add 1 :attr-a :a]
         [:db/add 1 :attr-b :b]
         [:db/add 2 :attr-a :a]}
  % := #{[:db/add 1 :attr-a :a]
         [:db/add 1 :attr-b :b]
         [:db/add 2 :attr-a :a]
         [:db/add 2 :attr-b :b]
         [:db/add 2 :attr-c :c]
         [:db/add -1 :attr-a :AAA]
         [:db/add -2 :attr-a :a]
         [:db/add -2 :attr-b :b]
         [:db/add -2 :attr-c :c]
         [:db/add -3 :attr-a :A]
         [:db/add -3 :attr-b :B]
         [:db/add -3 :attr-c :C]})
