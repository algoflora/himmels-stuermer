(ns himmelsstuermer.spec.app)


(def HimmelsstuermerConfig
  [:map
   {:closed true}
   [:api/fn :symbol]
   [:db/conn [:maybe :string]]
   [:bot/default-language-code :keyword]
   [:bot/roles [:map-of :keyword [:vector [:or :int :string :keyword]]]]
   [:handler/main :symbol]
   [:handler/actions-namespace :symbol]
   [:handler/payment :symbol]
   [:logging/level :keyword]
   [:project/config :map]])


(def ProjectConfig
  [:map
   {:closed true}
   [:bot/token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
   [:bot/default-language-code {:optional true} :keyword]
   [:bot/roles {:optional true} [:map-of :keyword [:vector [:or :int :string :keyword]]]]
   [:handler/namespaces [:vector :symbol]]
   [:handler/main {:optional true} :symbol]
   [:handler/actions-namespace {:optional true} :symbol]
   [:handler/payment {:optional true} :symbol]
   [:logging/level {:optional true} :keyword]
   [:project/config {:optional true} :map]])


(def Config
  [:map
   {:closed true}
   [:api/fn :symbol]
   [:db/conn [:maybe :string]]
   [:bot/token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
   [:bot/default-language-code :keyword]
   [:bot/roles [:map-of :keyword [:vector [:or :int :string :keyword]]]]
   [:handler/namespaces [:vector :symbol]]
   [:handler/main :symbol]
   [:handler/actions-namespace :symbol]
   [:handler/payment :symbol]
   [:logging/level :keyword]
   [:project/config :map]])
