(ns himmelsstuermer.impl.buttons
  (:require
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.api.texts :refer [txt txti]]
    [himmelsstuermer.impl.callbacks :as clb]
    [himmelsstuermer.impl.state :refer [*state*]]))


(defrecord XButton
  [])


(extend-protocol b/KeyboardButton
  clojure.lang.PersistentArrayMap
  (to-map [this _] this)

  clojure.lang.PersistentHashMap
  (to-map [this _] this)

  himmelsstuermer.api.buttons.TextButton
  (to-map [this user]
    {:text (:text this)
     :callback_data
     (str (clb/set-callback user (:func this) (:args this)))})

  himmelsstuermer.api.buttons.TxtButton
  (to-map [this user]
    {:text (txt (:txt this))
     :callback_data
     (str (clb/set-callback user (:func this) (:args this)))})

  himmelsstuermer.api.buttons.TxtiButton
  (to-map [this user]
    {:text (txti (:txt this) (:lang this))
     :callback_data
     (str (clb/set-callback user (:func this) (:args this)))})

  himmelsstuermer.api.buttons.HomeButton
  (to-map [this user]
    (let [text (cond
                 (nil? (:text this))    (txt [:home])
                 (vector? (:text this)) (txt (:text this))
                 :else                  (:text this))]
      {:text text
       :callback_data
       (str (clb/set-callback user (-> *state* :handlers :main) {}))}))

  himmelsstuermer.api.buttons.PayButton
  (to-map [this _]
    {:text (:text this)
     :pay true})

  himmelsstuermer.api.buttons.UrlButton
  (to-map [this _]
    {:text (:text this)
     :url (:url this)})

  XButton
  (to-map [_ user]
    {:text "✖️"
     :callback_data
     (str (clb/set-callback user 'himmelsstuermer.handler/delete-this-message {} true))}))
