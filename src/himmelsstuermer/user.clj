(ns himmelsstuermer.user
  (:require
    [himmelsstuermer.dynamic :refer [*user*]]
    [himmelsstuermer.impl.callbacks :as clb]
    [himmelsstuermer.impl.system.app :as app]))


(defn has-role?
  ([role] (has-role? role *user*))
  ([role user]
   (boolean (some #{(:user/id user) (:user/username user)} (set (role @app/bot-roles))))))


(defmacro with-role
  {:style/indent [1]
   :clj-kondo/lint-as 'clojure.core/when}
  [role & body]
  `(do
     (require '[himmelsstuermer.api]
              '[himmelsstuermer.button]
              '[himmelsstuermer.dynamic])
     (if (himmelsstuermer.user/has-role? ~role ~'himmelsstuermer.dynamic/*user*)
       (do ~@body)
       (himmelsstuermer.api/send-message ~'*user* "⛔ Forbidden! ⛔" [[(himmelsstuermer.button/home-btn "🏠 To Main Menu")]]))))


(defn set-handler
  ([func] (set-handler func {}))
  ([func args] (set-handler func args *user*))
  ([func args user]
   (clb/set-callback user func args false (:user/uuid user))))
