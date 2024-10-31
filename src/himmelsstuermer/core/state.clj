(ns himmelsstuermer.core.state
  (:require
    [clojure.data :as data]
    [clojure.set :as set]
    [himmelsstuermer.core.config :as conf]
    [himmelsstuermer.core.dispatcher :as disp]
    [himmelsstuermer.core.init :as init]
    [himmelsstuermer.impl.transactor :refer [new-transactions-set]]
    [himmelsstuermer.misc :as misc]
    [himmelsstuermer.spec.core :as spec]
    [malli.core :as malli]
    [malli.instrument :refer [instrument!]]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(malli/=> create-state [:=> [:cat :keyword [:* :map]] spec/State])


(defn- create-state
  [profile & args]
  (let [data (apply merge args)]
    {:profile profile
     :system {:db-conn (:db/conn data)}
     :bot {:token (:bot/token data)
           :roles (:bot/roles data)
           :default-language-code (:bot/default-language-code data)}
     :project (assoc (misc/project-info)
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
     :function nil
     :arguments {}
     :tasks #{}
     :aws-context nil}))


(malli/=> create-user-state [:-> spec/State spec/UserState])


(defn construct-user-state
  [state]
  (let [base-map {:himmelsstuermer/main-handler (symbol disp/main-handler)
                  :bot (:bot state)
                  :prf (:profile state)
                  :cfg (-> state :project :config)
                  :idb (:database state)
                  :txs (new-transactions-set)
                  :msg (:message state)
                  :cbq (:callback-query state)
                  :pcq (:pre-checkout-query state)
                  :usr (:user state)}
        arguments (:arguments state)]
    (when (some (-> base-map keys set) (keys arguments))
      (throw (ex-info "Forbidden key in arguments!" {:forbidden-keys (set/intersection
                                                                       (-> arguments keys set)
                                                                       (-> base-map keys set))})))
    (merge base-map (:arguments state))))


(defn state
  []
  (m/sp (let [profile @conf/profile]
          (when (Boolean/parseBoolean (System/getProperty "himmelsstuermer.malli.instrument"))
            (tt/event! ::malli-instrument-run)
            (instrument! {:report
                          (fn [type data]
                            (let [[s v] (case type
                                          :malli.core/invalid-input  [(:input data)  (:args data)]
                                          :malli.core/invalid-output [(:output data) (:value data)]
                                          nil)
                                  explanation (when (malli/schema? s)
                                                (malli/explain s v))
                                  data'     (assoc data :explain explanation)
                                  exception (malli/-exception type data')]
                              (throw (tt/error! {:id type
                                                 :data data'} exception))))}))
          (let [state (m/? (m/join (partial create-state profile)
                                   init/db-conn
                                   init/bot-token
                                   init/bot-default-language-code
                                   init/bot-roles
                                   init/project-config))]
            (tt/event! ::state-created {:data state})
            state))))


(malli/=> modify-state [:=> [:cat spec/State fn?] spec/State])


(defn modify-state
  [state modify-fn]
  (let [caller          (misc/get-caller)
        state'          (modify-fn state)
        [removed added] (data/diff state state')]
    (tt/set-ctx! (assoc tt/*ctx* :state state'))
    (tt/event! ::state-modified
               {:data {:removed removed
                       :added added
                       :caller-info caller}})
    state'))


(malli/=> shutdown! [:-> spec/State :any])


(defn shutdown!
  [state]
  #_(d/close (-> state :system :db-conn))) ; TODO: Check this behaviour twice!
