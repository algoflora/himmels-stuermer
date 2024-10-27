(ns himmelsstuermer.core.init
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [datahike.api :as d]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.misc :refer [read-resource-dir]]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(def api-fn
  (m/sp (let [sym  (:api/fn (m/? conf/config))
              func (requiring-resolve sym)]
          (tt/event! ::init-api-fn {:data {:symbol sym
                                           :function func}})
          {:api/fn @func})))


(def ^:private himmelsstuermer-schema
  (m/sp (-> "himmelsstuermer-resources/schema.edn"
            io/resource
            slurp
            edn/read-string)))


(def db-conn
  (m/sp (let [store-cfg (or (:db/conf (m/? conf/config))
                            {:backend  :mem
                             :id       (str (random-uuid))})
              schema    (m/? (m/join (fn [init & more]
                                       (into init cat more))
                                     himmelsstuermer-schema
                                     (read-resource-dir "schema")))
              opts      {:store              store-cfg
                         :schema-flexibility :write
                         :index              ({:mem :datahike.index/persistent-set}
                                              (:backend store-cfg)
                                              :datahike.index/hitchhiker-tree)
                         :keep-history?      true
                         :attribute-refs?    false
                         :initial-tx         schema}

              conn      (do (when-not (d/database-exists? {:store store-cfg})
                              (let [db (d/create-database opts)]
                                (tt/event! ::database-created {:data {:database db}})))
                            (d/connect {:store store-cfg}))]
          (tt/event! ::init-db-conn {:data {:store-config store-cfg
                                            :options opts
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


(def handler-main
  (m/sp (let [sym (:handler/main (m/? conf/config))]
          (tt/event! ::init-handler-main {:data {:symbol sym}})
          {:handler/main sym})))


(def handler-payment
  (m/sp (let [sym  (:handler/payment (m/? conf/config))]
          (tt/event! ::init-handler-payment {:data {:symbol sym}})
          {:handler/payment sym})))


(def actions-namespace
  (m/sp (let [sym (:actions/namespace (m/? conf/config))]
          (tt/event! ::init-actions-namespace {:data {:symbol sym}})
          {:actions/namespace sym})))


(def project-config
  (m/sp (let [cfg (:project/config (m/? conf/config))]
          (tt/event! ::init-project-config {:data {:config cfg}})
          {:project/config cfg})))


(comment

  (def cfg {:store #_{:backend  :file
                    :path     (str "/tmp/datahike-sandbox-" (random-uuid))}

            {:backend  :mem
             :id       (str (random-uuid))}
            :index              :datahike.index/hitchhiker-tree
            :schema-flexibility :write
            :keep-history?      true
            :attribute-refs?    false})

  (d/database-exists? cfg)
  (def db (d/create-database cfg))

  (def cn (d/connect cfg))

  (println cn)
  
  (def dbt (m/sp @cn))

  (m/? dbt)
 )
