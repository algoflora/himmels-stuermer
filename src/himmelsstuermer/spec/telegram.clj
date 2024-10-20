(ns himmelsstuermer.spec.telegram)


(def User
  [:map
   {:closed true}
   [:id :int]
   [:is_bot :boolean]
   [:first_name :string]
   [:last_name {:optional true} :string]
   [:username {:optional true} :string]
   [:language_code {:optional true} :string]
   [:is_premium {:optional true} [:= true]]])


(def Chat
  [:map
   {:closed true}
   [:id :int]
   [:type [:enum "private" "group" "supergroup" "channel"]]
   [:title {:optional true} :string]
   [:username {:optional true} :string]
   [:first_name {:optional true} :string]
   [:last_name {:optional true} :string]
   [:is_forum {:optional true} [:= true]]])


(def MessageEntity
  [:map
   {:closed true}
   [:type [:enum
           "mention" "hashtag" "cashtag" "bot_command" "url"
           "email" "phone_number" "bold" "italic" "underline"
           "strikethrough" "spoiler" "blockquote" "expandable_blockquote"
           "code" "pre" "text_link" "text_mension" "custom_emoji"]]
   [:offset :int]
   [:length :int]
   [:url {:optional true} :string]
   [:user {:optional true} User]
   [:language {:optional true} :string]
   [:custom_emoji_id {:optional true} :string]])


(def Button
  [:map {:closed true}
   [:text :string]
   [:url {:optional true} [:re #"^https?://(?:www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&//=]*)$"]]
   [:callback_data {:optional true} [:string {:min 1 :max 64}]]
   [:pay {:optional true} [:= true]]])


(def TextMessage
  [:map {:closed true}
   [:message_id {:optional true} :int]
   [:from  User]
   [:chat Chat]
   [:date :int]
   [:edit_date {:optional true} :int]
   [:text :string]
   [:entities {:optional true} [:maybe [:vector MessageEntity]]]
   [:reply_markup {:optional true} [:map {:closed true}
                                    [:inline_keyboard [:vector
                                                       [:vector
                                                        Button]]]]]])


(def Invoice
  [:map
   {:closed true}
   [:title [:string {:min 1 :max 32}]]
   [:description [:string {:min  1 :max 255}]]
   [:payload [:string {:min 1 :max 128}]]
   [:provider_token :string]
   [:currency [:string {:min 3 :max 3}]]
   [:prices [:vector [:map {:closed true}
                      [:label :string]
                      [:amount :int]]]]])


(def InvoiceMessage
  [:map {:closed true}
   [:message_id {:optional true} :int]
   [:from  User]
   [:chat Chat]
   [:date :int]
   [:edit_date {:optional true} :int]
   [:invoice Invoice]
   [:reply_markup {:optional true} [:map {:closed true}
                                    [:inline_keyboard [:vector
                                                       [:vector
                                                        Button]]]]]])


(def SuccessfulPayment
  [:map {:closed true}
   [:currency [:string {:min 3 :max 3}]]
   [:total_amount :int]
   [:invoice_payload [:string {:min 1 :max 128}]]
   [:telegram_payment_charge_id :string]
   [:provider_payment_charge_id :string]])


(def SuccessfulPaymentMessage
  [:map {:closed true}
   [:message_id {:optional true} :int]
   [:from  User]
   [:chat Chat]
   [:date :int]
   [:edit_date {:optional true} :int]
   [:successful_payment SuccessfulPayment]
   [:reply_markup {:optional true} [:map {:closed true}
                                    [:inline_keyboard [:vector
                                                       [:vector
                                                        Button]]]]]])


(def Message
  [:or
   TextMessage
   InvoiceMessage
   SuccessfulPaymentMessage])


(def MessageUpdate
  [:map {:closed true}
   [:update_id :int]
   [:message Message]])


(def CallbackQuery
  [:map {:closed true}
   [:id :string]
   [:from User]
   [:message {:optional true} [:or
                               Message
                               [:map
                                [:from User]
                                [:message_id :int]
                                [:date [:= 0]]]]]
   [:chat_instance :string]
   [:data {:optional true} [:string {:min 1 :max 64}]]])


(def CallbackQueryUpdate
  [:map {:closed true}
   [:update_id :int]
   [:callbacK_query CallbackQuery]])


(def PreCheckoutQuery
  [:map
   {:closed true}
   [:id :string]
   [:from User]
   [:currency [:string {:min 3 :max 3}]]
   [:total_amount [:int {:min 0}]]
   [:invoice_payload [:string {:min 1 :max 128}]]])


(def PreCheckoutQueryUpdate
  [:map {:closed true}
   [:update_id :int]
   [:pre_checkout_query PreCheckoutQuery]])


(def Update
  [:or
   MessageUpdate
   CallbackQueryUpdate
   PreCheckoutQueryUpdate])
