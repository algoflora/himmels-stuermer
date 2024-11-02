(ns himmelsstuermer.core.db
  (:require
    [malli.core :as malli]))


(defprotocol DBBackendProtocol

  (run-query [this query db args])

  (is-database? [this obj])

  (is-connection? [this obj])

  (database-exists? [this cfg])

  (create-database [this cfg schema])

  (get-connection [this cfg schema])

  (transaction [this cn tx-data]))


(defrecord DBBackendDummy
  []

  DBBackendProtocol

  (run-query
    [_ _ _ _]
    (throw (Exception. "No database backend!")))


  (is-database?
    [_ _]
    (throw (Exception. "No database backend!")))


  (is-connection?
    [_ _]
    (throw (Exception. "No database backend!")))


  (database-exists?
    [_ _]
    (throw (Exception. "No database backend!")))


  (create-database
    [_ _ _]
    (throw (Exception. "No database backend!")))


  (get-connection
    [_ _ _]
    (throw (Exception. "No database backend!")))


  (transaction
    [_ _ _]
    (throw (Exception. "No database backend!"))))


(defonce ^:private db-backend (atom (->DBBackendDummy)))


(malli/=> set-database-backend! [:-> [:fn #(satisfies? DBBackendProtocol %)] :nil])


(defn set-database-backend!
  [backend]
  (println "\nDatabase backend set: " (type backend) "\n")
  (reset! db-backend backend))


(defn q
  [query db & args]
  (run-query @db-backend query db args))


(defn is-db?
  [obj]
  (is-database? @db-backend obj))


(defn is-conn?
  [obj]
  (is-connection? @db-backend obj))


(defn db-exists?
  [cfg]
  (database-exists? @db-backend cfg))


(defn create-db
  [cfg schema]
  (create-database @db-backend cfg schema))


(defn connect
  [cfg schema]
  (get-connection @db-backend cfg schema))


(defn transact
  [state tx-set]
  (transaction @db-backend (-> state :system :db-conn) (vec tx-set)))
