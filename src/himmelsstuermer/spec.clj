(ns himmelsstuermer.spec
  (:require
    [datalevin.core :as d]
    [datalevin.db]
    [himmelsstuermer.api.buttons :as b]
    [himmelsstuermer.spec.telegram :as spec.tg]))


(def MissionaryTask
  [:fn #(instance? clojure.lang.RestFn %)]) ; TODO: Wait for Missionary update about meta or smth...


(def Regexp
  [:fn #(instance? java.util.regex.Pattern %)])


(def Buttons
  [:vector
   [:maybe [:vector
            [:maybe [:fn #(instance? b/KeyboardButton %)]]]]])


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
   [:user/last-name {:optional true} :string]
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
             [:db-conn [:fn #(d/conn? %)]]
             [:api-fn fn?]]]
   [:bot [:map {:closed true}
          [:token [:re #"^\d{10}:[a-zA-Z0-9_-]{35}$"]]
          [:roles [:map-of :keyword [:vector [:or :int :string :keyword]]]]
          [:default-language-code :keyword]]]
   [:actions [:map {:closed true}
              [:namespace :symbol]]]
   [:handlers [:map {:closed true}
               [:main :symbol]
               [:payment fn?]]]
   [:project [:map {:closed true}
              [:group :string]
              [:name :string]
              [:version :string]
              [:config :map]]]
   [:database [:maybe [:fn #(instance? datalevin.db.DB %)]]]
   [:transaction [:set [:or [:vector :any] :map]]]
   [:action [:maybe Action]]
   [:update [:maybe spec.tg/Update]]
   [:message [:maybe spec.tg/Message]]
   [:callback-query [:maybe spec.tg/CallbackQuery]]
   [:pre-checkout-query [:maybe spec.tg/PreCheckoutQuery]]
   [:user [:maybe User]]
   [:tasks [:vector MissionaryTask]]
   [:aws-context [:maybe [:map-of :string :string]]]])
