(ns himmelsstuermer.test-runner
  (:gen-class)
  (:require
    [clojure.test :refer [run-tests]]
    ;; [db-backend]
    [himmelsstuermer.core.logging :refer [init-logging!]]
    [himmelsstuermer.e2e-test]
    [himmelsstuermer.e2e.core :refer [serve]]
    [himmelsstuermer.e2e.serve :refer [set-serve-multimethod!]]
    [kaocha.runner]))


(defn -main
  [& args]
  (init-logging!)
  (set-serve-multimethod! serve)
  (let [report (apply run-tests (into ['himmelsstuermer.e2e-test] (map symbol) args))]
    (when (or (pos? (:fail report)) (pos? (:error report)))
      (System/exit 1))))


(defn kaocha
  [& args]
  (init-logging!)
  (set-serve-multimethod! serve)
  (apply kaocha.runner/-main args))
