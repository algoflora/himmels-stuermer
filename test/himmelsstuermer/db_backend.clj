(ns himmelsstuermer.db-backend
  (:require
    [datahike.api :as d]
    [datahike.spec :as spec.dh]
    [himmelsstuermer.core.db :as db]))


(defn- create-datahike-opts
  [cfg schema]
  {:store              cfg
   :schema-flexibility :write
   :index              :datahike.index/persistent-set
   :keep-history?      true
   :attribute-refs?    false
   :initial-tx         schema})


(defrecord DBBackendDatahike
  []

  db/DBBackendProtocol

  (run-query
    [_ query db args]
    (apply d/q query db args))


  (is-database?
    [_ obj]
    (spec.dh/SDB obj))


  (is-connection?
    [_ obj]
    (spec.dh/SConnection obj))


  (database-exists?
    [_ cfg]
    (d/database-exists? {:store cfg}))


  (create-database
    [_ cfg schema]
    (d/create-database (create-datahike-opts cfg schema)))


  (get-connection
    [_ cfg schema]
    (d/connect (create-datahike-opts cfg schema)))


  (transaction
    [_ cn tx-data]
    (d/transact cn tx-data)))


(defn get-backend
  [& _]
  (->DBBackendDatahike))
