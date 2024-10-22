(ns himmelsstuermer.spec.e2e
  (:require
    [himmelsstuermer.spec.telegram :as spec.tg]))


(def DummyEntry
  [:map
   [:dummy spec.tg/User]
   [:messages [:vector spec.tg/Message]]])
