(ns himmelsstuermer.user
  (:require
    [himmelsstuermer.api.vars :refer [*user*]]
    [himmelsstuermer.impl.callbacks :as clb]
    [himmelsstuermer.impl.state :refer [*state*]]
    [missionary.core :as m]))


(defn has-role?
  ([role] (has-role? role *user*))
  ([role user]
   (boolean (some #{(:user/id user) (:user/username user)} (set (role (-> *state* :bot :roles)))))))


(defmacro with-role
  {:style/indent [1]
   :clj-kondo/lint-as 'clojure.core/when}
  [role & body]
  `(do
     (require '[himmelsstuermer.api]
              '[himmelsstuermer.button]
              '[himmelsstuermer.api.vars])
     (if (himmelsstuermer.user/has-role? ~role ~'himmelsstuermer.api.vars/*user*)
       (do ~@body)
       (himmelsstuermer.api/send-message ~'*user* "⛔ Forbidden! ⛔" [[(himmelsstuermer.button/home-btn "🏠 To Main Menu")]]))))


(defn set-handler
  ([func] (set-handler func {}))
  ([func args] (set-handler func args *user*))
  ([func args user]
   (m/sp (clb/set-callback user func args false (:user/uuid user)))))
