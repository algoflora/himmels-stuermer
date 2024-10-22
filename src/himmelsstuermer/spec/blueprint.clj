(ns himmelsstuermer.spec.blueprint
  (:require
    [himmelsstuermer.spec :as spec]
    [himmelsstuermer.spec.telegram :as spec.tg]))


(def SendTextBlueprintEntryArgs
  [:cat
   :any
   [:? [:vector spec.tg/MessageEntity]]])


(def SendTextBlueprintEntry
  [:cat
   [:fn qualified-keyword?]
   SendTextBlueprintEntryArgs])


(def ClickBtnBlueprintEntryArgs
  [:cat
   [:? [:maybe [:int {:min 1}]]]
   [:or :string spec/Regexp]])


(def ClickBtnBlueprintEntry
  [:cat
   [:fn qualified-keyword?]
   ClickBtnBlueprintEntryArgs])


(def CheckMessageBlueprintEntryArgs
  [:cat
   [:? [:maybe [:int {:min 1}]]]
   [:? [:or :string spec/Regexp]]
   [:? [:set spec.tg/MessageEntity]]
   [:? [:vector [:vector [:or :string spec/Regexp]]]]])


(def CheckMessageBlueprintEntry
  [:cat
   [:fn qualified-keyword?]
   CheckMessageBlueprintEntryArgs])


(def CheckInvoiceBlueprintEntryArgs
  [:cat
   :string
   :string
   [:string {:min 3 :max 3}]
   [:vector [:map {:closed true} [:label :string] [:amount :int]]]
   [:? [:vector [:vector [:or :string spec/Regexp]]]]])


(def CheckInvoiceBlueprintEntry
  [:cat
   [:fn qualified-keyword?]
   CheckInvoiceBlueprintEntryArgs])


(def BlueprintEntryArgs
  [:or
   SendTextBlueprintEntryArgs
   ClickBtnBlueprintEntryArgs
   CheckMessageBlueprintEntryArgs
   CheckInvoiceBlueprintEntryArgs])


(def BlueprintEntry
  [:or
   SendTextBlueprintEntry
   ClickBtnBlueprintEntry
   CheckMessageBlueprintEntry
   CheckInvoiceBlueprintEntry])


(def Blueprint
  [:cat
   [:* BlueprintEntry]])
