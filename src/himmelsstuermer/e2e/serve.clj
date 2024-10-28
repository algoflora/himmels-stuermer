(ns himmelsstuermer.e2e.serve
  (:require
    [himmelsstuermer.spec.telegram :as spec.tg]
    [malli.core :as malli]
    [taoensso.telemere :as tt]))


(defonce ^:private serve-multimethod (atom nil))


(defn set-serve-multimethod
  [mm]
  (tt/event! ::set-serve-multimethod {:data {:multimethod mm}})
  (reset! serve-multimethod mm))


(malli/=> request [:=> [:cat :string :keyword spec.tg/Request] :any])


(defn request
  [_ method body]
  (tt/event! ::request-received
             {:data {:method method
                     :body body}})
  (@serve-multimethod method body))
