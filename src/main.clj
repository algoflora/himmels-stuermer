(ns main
  (:require
    [himmelsstuermer.core :as bbot]))


(defn handler
  [& args]
  (println "Hello from Himmelsstuermer!")
  (apply bbot/sqs-receiver args))
