(ns himmelsstuermer.e2e
  (:require
    [himmelsstuermer.impl.e2e.flow :as impl]))


(defmacro defflow
  {:style/indent [1]}
  [& args]
  `(impl/defflow ~@args))


(defmacro flows-out
  {:style/indent [1]
   :clj-kondo/lint-as 'clojure.core/def}
  [& args]
  `(impl/flows-out ~@args))
