(ns himmelsstuermer.core.init
  (:require
    [clojure.data :refer [diff]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [datahike-dynamodb.core]
    [datahike.api :as d]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.misc :refer [do-nanos do-nanos*]]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(def ^:private himmelsstuermer-schema
  (-> "himmelsstuermer.schema.edn"
      io/resource
      slurp
      edn/read-string))


(def db-conn
  ;; TODO: Check why it is loaded multiple times

  (m/sp (let [store  (case @conf/profile
                       :aws
                       {:backend          :dynamodb
                        :consistent-read? true
                        :table            (System/getenv "DYNAMODB_TABLE_NAME")
                        :region           (System/getenv "AWS_REGION")
                        :access-key       (System/getenv "DYNAMODB_PUBLIC_KEY")
                        :secret           (System/getenv "DYNAMODB_SECRET_KEY")}

                       :test
                       (if (System/getenv "HIMMELSSTUERMER_USE_LOCAL_DYNAMODB")
                         {:backend    :dynamodb
                          :endpoint   "http://localhost:8000"
                          :table      (System/getProperty "himmelsstuermer.test.database.id"
                                                          (str (random-uuid)))
                          :region     "region"
                          :access-key "accessKey"
                          :secret     "secretKey"}

                         {:backend :mem
                          :id      (System/getProperty "himmelsstuermer.test.database.id"
                                                       (str (random-uuid)))}))

              schema (into himmelsstuermer-schema
                           (or (some->> "schema.edn" io/resource slurp read-string) []))

              opts   {:store               store
                      :schema-flexibility  :write
                      :index               :datahike.index/persistent-set
                      :keep-history?       true
                      :allow-unsafe-config true
                      :attribute-refs?     false
                      :initial-tx          schema}

              ;; {exists? :result
              ;;  nanos :nanos}   (do-nanos* (d/database-exists? {:store store}))

              ;; _ (tt/event! ::checked-database-exists? {:data {:exists? exists?
              ;;                                                 :time-millis (* 0.000001 nanos)}})

              {conn :result
               nanos :nanos}   (do-nanos* (try (d/connect opts)
                                               (catch clojure.lang.ExceptionInfo exc
                                                 (if (or (= :test @conf/profile)
                                                         (= :db-does-not-exist (-> exc ex-data :type)))
                                                   (let [db (d/create-database {:store store} schema)]
                                                     (tt/event! ::database-created {:data {:database db}})
                                                     (d/connect opts))
                                                   (throw exc)))))
              _ (tt/event! ::got-connected {:data {:time-millis (* 0.000001 nanos)}})
              db     @conn

              [new-schema removed-schema untoched-schema]
              (diff (set schema) (into #{} (map (fn [[_ v]] (dissoc v :db/id))) (d/schema db)))]
          (when (some? new-schema)
            (tt/event! ::schema-update {:data {:added new-schema
                                               :removed removed-schema
                                               :untoched untoched-schema}})
            (d/transact conn schema))
          (tt/event! ::init-db-conn {:data {:options opts
                                            :schema schema
                                            :connection conn}})
          {:db/conn conn
           :db/db   db})))


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


(comment
  {:type :config-does-not-match-stored-db
   :config {:keep-history? true
            :search-cache-size 10000
            :index :datahike.index/persistent-set
            :store [:dynamodb "ap-southeast-1" "devel-laniakea-stars-bot"]
            :store-cache-size 1000
            :attribute-refs? false
            :crypto-hash? false
            :schema-flexibility :write
            :branch :db}
   :stored-config {:keep-history? true
                   :search-cache-size 10000
                   :index :datahike.index/persistent-set
                   :store [:dynamodb "ap-southeast-1" "devel-laniakea-stars-bot"]
                   :store-cache-size 1000
                   :attribute-refs? false
                   :crypto-hash? false
                   :schema-flexibility :write
                   :allow-unsafe-config true
                   :branch :db}
   :diff (nil
          {:allow-unsafe-config true}
          {:keep-history? true :search-cache-size 10000 :index :datahike.index/persistent-set :store [:dynamodb "ap-southeast-1" "devel-laniakea-stars-bot"] :store-cache-size 1000 :attribute-refs? false :crypto-hash? false :schema-flexibility :write :branch :db})}

  )
