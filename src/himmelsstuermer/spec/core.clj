(ns himmelsstuermer.spec.core
  (:require
    [datahike.spec :as spec.dh]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.impl.transactor]
    [himmelsstuermer.spec.telegram :as spec.tg]))


(def MissionaryTask
  :any
  #_[:fn #(instance? clojure.lang.RestFn %)]) ; TODO: Wait for Missionary update about meta or smth...


(def Regexp
  [:fn #(instance? java.util.regex.Pattern %)])


(def Buttons
  [:vector
   [:maybe [:vector
            [:maybe [:fn #(satisfies? b/KeyboardButton %)]]]]])


(def Record
  [:map
   [:attributes
    [:map-of :keyword :string]]
   [:awsRegion :string]
   [:body :string]
   [:eventSource :string]
   [:eventSourceARN :string]
   [:md5OfBody :string]
   [:messageAttributes [:map]]
   [:messageId :string]
   [:receiptHandle :string]])


(def Action
  [:map {:closed true}
   [:method :string]
   [:arguments :map]
   [:timestamp :int]])


(def ActionRequest
  [:map {:closed true}
   [:action Action]])


(def User
  [:map {:closed true}
   [:db/id :int]
   [:user/uuid :uuid]
   [:user/username {:optional true} :string]
   [:user/id :int]
   [:user/first-name :string]
   [:user/last-name {:optional true} [:maybe :string]]
   [:user/language-code {:optional true} :string]
   [:user/msg-id {:optional true} :int]])


(def Callback
  [:map {:closed true}
   [:db/id :int]
   [:callback/uuid :uuid]
   [:callback/function :symbol]
   [:callback/arguments :map]
   [:callback/user [:or [:map [:db/id :int]] User]]
   [:callback/service? :boolean]
   [:callback/message-id {:optional true} :int]])


(def State
  [:map {:closed true}
   [:profile :keyword]
   [:system [:map {:closed true}
             [:db-conn [:fn spec.dh/SConnection]]
             [:api-fn fn?]]]
   [:bot [:map {:closed true}
          [:token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
          [:roles [:map-of :keyword [:set [:or :int :string]]]]
          [:default-language-code :keyword]]]
   [:actions [:map {:closed true}
              [:namespace :symbol]]]
   [:handlers [:map {:closed true}
               [:main :symbol]
               [:payment :symbol]]]
   [:project [:map {:closed true}
              [:group :string]
              [:name :string]
              [:version :string]
              [:config :map]]]
   [:database [:maybe [:fn spec.dh/SDB]]]
   [:action [:maybe Action]]
   [:update [:maybe spec.tg/Update]]
   [:message [:maybe spec.tg/Message]]
   [:callback-query [:maybe spec.tg/CallbackQuery]]
   [:pre-checkout-query [:maybe spec.tg/PreCheckoutQuery]]
   [:user [:maybe User]]
   [:function [:maybe fn?]]
   [:arguments :map]
   [:transaction [:set [:or :map [:vector :any]]]] ; TODO: Transaction vector spec?
   [:tasks [:set MissionaryTask]]
   [:aws-context [:maybe [:map-of :string :string]]]])


(def UserState
  [:map ; Open because it will be merged with arguments
   [:himmelsstuermer/api-fn fn?]
   [:himmelsstuermer/main-handler :symbol]
   [:bot [:map {:closed true}
          [:token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
          [:roles [:map-of :keyword [:set [:or :int :string]]]]
          [:default-language-code :keyword]]]
   [:prf :keyword]
   [:cfg :map]
   [:idb [:fn spec.dh/SDB]]
   [:txs [:fn #(satisfies? himmelsstuermer.impl.transactor/TransactionsAccumulator %)]]
   [:msg [:maybe spec.tg/Message]]
   [:cbq [:maybe spec.tg/CallbackQuery]]
   [:pcq [:maybe spec.tg/PreCheckoutQuery]]
   [:usr [:maybe User]]])
