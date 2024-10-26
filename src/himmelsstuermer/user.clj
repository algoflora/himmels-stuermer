(ns himmelsstuermer.user
  (:require
    [himmelsstuermer.api :as api]
    [himmelsstuermer.api.buttons :as b]))


(defn has-role?
  ([state role] (has-role? state role (:usr state)))
  ([state role user]
   (boolean (some #{(:user/id user) (:user/username user)} (-> state :bot :roles role)))))


(defn with-role
  [{:keys [usr] :as state} role f]
  (if (has-role? state role usr)
    (f)
    (api/send-message state usr "⛔ Forbidden! ⛔" [[(b/home-btn "🏠 To Main Menu")]])))
