(ns himmelsstuermer.core.init
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [datomic.client.api :as d]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.misc :as misc]
    [me.raynes.fs :as fs]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(def ^:private himmelsstuermer-schema
  (-> "himmelsstuermer.schema.edn"
      io/resource
      slurp
      edn/read-string))


(defn- database-exists?
  "Checks if a database with the given name exists in Datomic Cloud."
  [client db-name]
  (let [dbs (d/list-databases client {})]
    (some #(= db-name %) dbs)))


(def db-conn
  ;; TODO: Check why it is loaded multiple times

  (m/sp (let [client-config (case @conf/profile
                              :test
                              {:server-type :datomic-local
                               :system (System/getProperty "himmelsstuermer.test.database.id"
                                                           (str (random-uuid)))
                               :storage-dir :mem}

                              :aws
                              {:server-type :cloud
                               :region (System/getenv "AWS_REGION")
                               :system (System/getenv "DATOMIC_SYSTEM")
                               :endpoint (System/getenv "DATOMIC_ENDPOINT")
                               :proxy-port 8182})

              db-name (:name (misc/project-info))

              schema (into himmelsstuermer-schema
                           (or (some->> "schema.edn" io/resource slurp read-string) []))

              client (d/client client-config)

              conn   (do (when-not (database-exists? client db-name)
                           (d/create-database client {:db-name db-name})
                           (tt/event! ::database-created {:data {:db-name db-name}}))
                         (d/connect client {:db-name db-name}))]
          (d/transact conn {:tx-data schema})
          (tt/event! ::init-db-conn {:data {:client-config client-config
                                            :schema schema
                                            :db-name db-name
                                            :connection conn}})
          {:db/conn conn})))


(def bot-token
  (m/sp (let [token (:bot/token (m/? conf/config))]
          (tt/event! ::init-bot-token {:data {:token token}})
          {:bot/token token})))


(def bot-default-language-code
  (m/sp (let [code (:bot/default-language-code (m/? conf/config))]
          (tt/event! ::init-bot-default-language {:language-code code})
          {:bot/default-language-code code})))


(defn- load-role
  [roles rs]
  (let [entries ((first rs) roles)]
    (-> (mapv (fn [x]
                (cond
                  (and (keyword? x) (some #{x} (set rs)))
                  (throw (ex-info "Circular roles dependencies!"
                                  {:event ::circular-roles-error
                                   :role x
                                   :roles roles}))

                  (keyword? x) (load-role roles (conj rs x))
                  :else x))
              entries)
        flatten
        set)))


(def bot-roles
  (m/sp (let [roles-data (:bot/roles (m/? conf/config))
              roles (into {}
                          (map (fn [[k _]] [k (load-role roles-data (list k))]))
                          roles-data)]
          (tt/event! ::init-bot-roles {:data {:roles roles}})
          {:bot/roles roles})))


(def project-config
  (m/sp (let [cfg (:project/config (m/? conf/config))]
          (tt/event! ::init-project-config {:data {:config cfg}})
          {:project/config cfg})))


;; [{:db/ident :user/uuid
;;   :db/valueType :db.type/uuid
;;   :db/cardinality :db.cardinality/one
;;   :db/unique :db.unique/identity
;;   :db/doc "UUID of User"}

;;  {:db/ident :user/username
;;   :db/valueType :db.type/string
;;   :db/cardinality :db.cardinality/one
;;   :db/unique :db.unique/identity
;;   :db/doc "User's Telegram username"}

;;  {:db/ident :user/id
;;   :db/valueType :db.type/long
;;   :db/cardinality :db.cardinality/one
;;   :db/unique :db.unique/identity
;;   :db/doc "User's Telegram ID (and chat_id in private chats)"}

;;  {:db/ident :user/first-name
;;   :db/valueType :db.type/string
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "User's first name in Telegram profile"}

;;  {:db/ident :user/last-name
;;   :db/valueType :db.type/string
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "User's last name in Telegram profile"}

;;  {:db/ident :user/language-code
;;   :db/valueType :db.type/string
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "User's language code in Telegram profile"}

;;  {:db/ident :user/msg-id
;;   :db/valueType :db.type/long
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "ID of 'main' chat message for this User"}

;;  {:db/ident :callback/uuid
;;   :db/valueType :db.type/uuid
;;   :db/cardinality :db.cardinality/one
;;   :db/unique :db.unique/identity
;;   :db/doc "UUID of Callback"}

;;  {:db/ident :callback/function
;;   :db/valueType :db.type/symbol
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "Qualified symbol of function of Callback"}

;;  {:db/ident :callback/arguments
;;   :db/valueType :db.type/string
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "EDN-serialized arguments of Callback"}

;;  {:db/ident :callback/user
;;   :db/valueType :db.type/ref
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "The User for whom this Callbak is intended"}

;;  {:db/ident :callback/service?
;;   :db/valueType :db.type/boolean
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "Indicates is this callback a service one. If `true` then User's Callback will not reset."}

;;  {:db/ident :callback/message-id
;;   :db/valueType :db.type/long
;;   :db/cardinality :db.cardinality/one
;;   :db/doc "ID of Message this Callback is associated with"}]
