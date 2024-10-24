(ns himmelsstuermer.impl.errors
  (:require
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.api.vars :refer [*user*]]
    [himmelsstuermer.handler :as h]
    [himmelsstuermer.impl.state :refer [*state*]]
    [himmelsstuermer.user :refer [has-role?]]
    [missionary.core :as m]
    [taoensso.telemere :as tt]))


(defn handle-error
  [ex]
  (tt/error! ::unhandled-error ex)
  (when (some? *user*)
    (m/? (api/send-message *user*
                           (str "⚠️ Unexpected ERROR! ⚠️"
                                (if (has-role? :admin *user*)
                                  (str "\n\n" (ex-message ex)) ""))
                           [[(b/text-btn "To Main Menu" 'himmelsstuermer.impl.errors/handler)]]
                           :temp)))
  #_(when (= :test conf/profile)
       (throw ex)))


(defn handler
  [_]
  (m/join (constantly nil)
          (h/delete-this-message {})
          ((requiring-resolve (-> *state* :handlers :main)) {})))
