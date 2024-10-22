(ns himmelsstuermer.e2e
  (:require
    [himmelsstuermer.e2e.scenes :as impl]))


(defmacro defscene
  {:style/indent [1]}
  [& args]
  `(impl/defscene ~@args))


(defmacro situation-run
  {:style/indent [1]
   :clj-kondo/lint-as 'clojure.core/def}
  [& args]
  `(impl/situation-run ~@args))
