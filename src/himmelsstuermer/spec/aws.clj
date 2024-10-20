(ns himmelsstuermer.spec.aws)


(def Record
  [:map
   [:attributes
    [:map-of :keyword :string]
    #_[:map
     [:AWSTraceHeader {:optional true} :string]
     [:ApproximateFirstReceiveTimestamp {:optional true} :string]
     [:ApproximateReceiveCount {:optional true} :string]
     [:MessageDeduplicationId {:optional true} :string]
     [:MessageGroupId {:optional true} :string]
     [:SenderId {:optional true} :string]
     [:SentTimestamp {:optional true} :string]
     [:SequenceNumber {:optional true} :string]
     [:SqsManagedSseEnabled {:optional true} :string]]]
   [:awsRegion :string]
   [:body :string]
   [:eventSource :string]
   [:eventSourceARN :string]
   [:md5OfBody :string]
   [:messageAttributes [:map]]
   [:messageId :string]
   [:receiptHandle :string]])
