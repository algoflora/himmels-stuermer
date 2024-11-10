(ns himmelsstuermer.spec.app)


(def HimmelsstuermerConfig
  [:map
   {:closed true}
   [:bot/default-language-code :keyword]
   [:bot/roles [:map-of :keyword [:vector [:or :int :string :keyword]]]]
   [:project/config :map]])


(def ProjectConfig
  [:map
   {:closed true}
   [:bot/token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
   [:bot/default-language-code {:optional true} :keyword]
   [:bot/roles {:optional true} [:map-of :keyword [:vector [:or :int :string :keyword]]]]
   [:project/config {:optional true} :map]])


(def Config
  [:map
   {:closed true}
   [:bot/token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
   [:bot/default-language-code :keyword]
   [:bot/roles [:map-of :keyword [:vector [:or :int :string :keyword]]]]
   [:project/config :map]])
