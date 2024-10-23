(ns himmelsstuermer.core.state
  (:require
    [clojure.pprint :refer [pprint]]
    [datalevin.core :as d]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.core.init :as init]
    [himmelsstuermer.spec :as spec]
    [malli.core :as malli]
    [malli.instrument :refer [instrument!]]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(defmacro project-info

  "This macro expands in map with keys `group`, `name` and `version` of current project by information from project.clj"

  []
  (let [[_ ga version] (read-string (try (slurp "project.clj") (catch Exception _ "[]")))
        [ns name version] (try [(namespace ga) (name ga) version] (catch Exception _ []))]
    {:group ns
     :name name
     :version version}))


(malli/=> create-state [:=> [:cat :keyword [:* :map]] spec/State])


(defn- create-state
  [profile & args]
  (let [data (apply merge args)]
    {:profile profile
     :system {:db-conn (:db/conn data)
              :api-fn (:api/fn data)}
     :bot {:token (:bot/token data)
           :roles (:bot/roles data)
           :default-language-code (:bot/default-language-code data)}
     :actions {:namespace (:actions/namespace data)}
     :handlers {:main (:handler/main data)
                :payment (:handler/payment data)}
     :project (assoc (project-info)
                     :config
                     (:project/config data))
     :database nil
     :transaction #{}
     :action nil
     :update nil
     :message nil
     :callback-query nil
     :pre-checkout-query nil
     :user nil
     :tasks []
     :aws-context nil}))


(def state
  (m/sp (let [profile @conf/profile]
          (when (System/getProperty  "himmelsstuermer.malli.instrument" "false")
            (tt/event! ::malli-instrument-run)
            (instrument! {:report (fn [type data]
                                    (let [[s v] (case type
                                                  :malli.core/invalid-input  [(:input data)  (:args data)]
                                                  :malli.core/invalid-output [(:output data) (:value data)]
                                                  nil)]
                                      (tt/error! (malli/-exception
                                                   type
                                                   (merge (if (some? s) (malli/explain s v) {}) data)))))}))
          (let [state (m/? (m/join (partial create-state profile)
                                   init/api-fn
                                   init/db-conn
                                   init/bot-token
                                   init/bot-default-language-code
                                   init/bot-roles
                                   init/handler-main
                                   init/handler-payment
                                   init/actions-namespace
                                   init/project-config))]
            (tt/event! ::state-created {:data state})
            state))))


(malli/=> modify-state [:-> spec/State spec/State])


(defn modify-state
  [state modify-fn]
  (let [state' (modify-fn state)]
    (tt/set-ctx! (assoc tt/*ctx* :state state'))
    (tt/event! ::state-modified {:data {:old-state state
                                        :new-state state'}})
    state'))


(defn shutdown!
  [state]
  (d/close (-> state :system :db-conn))
  (tt/stop-handlers!))


(comment
  (def x (atom 0))
  (def ^:dynamic *x* nil)

  (binding [*x* x]
    (swap! *x* inc))

  @x

  )
