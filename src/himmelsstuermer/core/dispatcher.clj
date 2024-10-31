(ns himmelsstuermer.core.dispatcher
  (:require
    [clojure.core.server]
    [clojure.java.io :as io]
    ;; [himmelsstuermer.core.logging]
    [himmelsstuermer.handler]))


;; TODO: Move dispatcher to missionary tasks? Then we can do logging here...
(def ^:private dispatcher-config
  (some-> "dispatcher.edn"
          io/resource
          slurp
          read-string))


(def ^:private handlers-namespaces
  (or (:handlers-namespaces dispatcher-config) ['himmelsstuermer.handler]))


(doseq [ns handlers-namespaces]
  ;; (tt/event! ::require-namespace {:data {:namespace ns}})
  (require (symbol ns)))


(def main-handler
  (let [sym (or (:main-handler dispatcher-config) 'himmelsstuermer.handler/main)]
    ;; (tt/event! ::init-main-handler {:data {:symbol sym}})
    (resolve sym)))


(def payment-handler
  (let [sym (or (:payment-handler dispatcher-config) 'himmelsstuermer.handler/payment)]
    ;; (tt/event! ::init-payment-handler {:data {:symbol sym}})
    (resolve sym)))


(def ^:private actions-namespace
  (or (:actions-namespace dispatcher-config) 'himmelsstuermer.actions))


;; (tt/event! ::require-actions-namespace {:data {:namespace actions-namespace}})
(require actions-namespace)


(defn resolve-symbol!
  [symbol]
  (when symbol
    (resolve symbol)))


(defn resolve-action!
  [symbol]
  (when symbol
    (resolve actions-namespace symbol)))
