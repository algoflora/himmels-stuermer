(ns himmelsstuermer.core.db
  (:require
    [datahike.api :as d]
    [himmelsstuermer.core.state :as s]
    [himmelsstuermer.spec.core :as spec]
    [malli.core :as malli]
    [taoensso.telemere :as tt]))


(malli/=> load-database [:-> spec/State spec/State])


(defn load-database
  [s]
  (let [state (s/modify-state s #(assoc % :database @(-> s :system :db-conn)))
        db    (:database state)]
    (tt/event! ::loaded-database {:data {:database (str db)}})
    state))


(malli/=> load-database [:-> spec/State :map])


(defn transact
  [state tx-set]
  (d/transact (-> state :system :db-conn) (vec tx-set)))
