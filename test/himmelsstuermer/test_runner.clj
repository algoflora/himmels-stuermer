(ns himmelsstuermer.test-runner
  (:gen-class)
  (:require
    [clojure.test :refer [run-tests]]
    [himmelsstuermer.core.db :as db]
    [himmelsstuermer.core.logging :refer [init-logging!]]
    [himmelsstuermer.db-backend :as dbbe]
    [himmelsstuermer.e2e-test]
    [himmelsstuermer.e2e.core :refer [serve]]
    [himmelsstuermer.e2e.serve :refer [set-serve-multimethod!]]
    [kaocha.runner]))


(defn -main
  [& _]
  (init-logging!)
  (db/set-database-backend! (dbbe/get-backend))
  (set-serve-multimethod! serve)
  (let [report (run-tests 'himmelsstuermer.e2e-test)]
    (when (or (pos? (:fail report)) (pos? (:error report)))
      (System/exit 1))))


(defn kaocha
  [& args]
  (init-logging!)
  (db/set-database-backend! (dbbe/get-backend))
  (set-serve-multimethod! serve)
  (apply kaocha.runner/-main args))
