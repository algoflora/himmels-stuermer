(ns himmelsstuermer.user
  (:require
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.impl.callbacks :as clb]))


(defn has-role?
  ([state role] (has-role? state role (:usr state)))
  ([state role user]
   (and (some? user) (some #{(:user/id user) (:user/username user)} (-> state :bot :roles role)))))


(defn with-role
  [{:keys [usr] :as state} role f]
  (if (has-role? state role usr)
    (f)
    (api/send-message state usr "⛔ Forbidden! ⛔" [[(b/home-btn "🏠 To Main Menu")]])))


(defn set-handler
  ([{:keys [usr] :as state} f args] (set-handler state usr f args))
  ([state usr f args]
   (clb/set-callback state usr f args false (:user/uuid usr))))
