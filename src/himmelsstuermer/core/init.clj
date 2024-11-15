(ns himmelsstuermer.core.init
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.core.storage :refer [get-storage]]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(def ^:private himmelsstuermer-schema
  (-> "himmelsstuermer.schema.edn"
      io/resource
      slurp
      edn/read-string))


(def db-schema
  (m/via m/blk (let [schema (merge himmelsstuermer-schema
                                   (or (some-> "schema.edn" io/resource slurp read-string) {}))]
                 (tt/event! ::init-db-schema {:data {:schema schema}})
                 {:db/schema schema})))


(def db-storage
  ;; TODO: Check why it is loaded multiple times

  (m/via m/blk (let [storage     (get-storage)]
                 (tt/event! ::init-db-storage {:data {:storage storage}})
                 {:db/storage storage})))


(def bot-token
  (m/sp (let [token (:bot/token (m/? conf/config))]
          (tt/event! ::init-bot-token {:data {:token token}})
          {:bot/token token})))


(def bot-default-language-code
  (m/sp (let [code (:bot/default-language-code (m/? conf/config))]
          (tt/event! ::init-bot-default-language {:data {:language-code code}})
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
  (m/via m/blk (let [cfg (:project/config (m/? conf/config))]
                 (tt/event! ::init-project-config {:data {:config cfg}})
                 {:project/config cfg})))
