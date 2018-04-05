(ns lupapalvelu.document.approval-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.approval :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.model-test :refer [schema]]))

(def approvable-schema {:info {:name "approval-model" :version 1 :approvable true}
                        :body [{:name "s" :type :string}]})

(facts "approvable schema"
  (approvable? (model/new-document approvable-schema ..now..) []))

(facts "non-approvable schema"
  (approvable? nil) => false
  (approvable? {}) => false
  (approvable? (model/new-document schema ..now..)) => false)

(def schema-without-approvals {:info {:name "approval-model-without-approvals"
                                      :version 1
                                      :approvable false}
                               :body [{:name "single" :type :string :approvable true}
                                      {:name "single2" :type :string}
                                      {:name "repeats" :type :group :repeating true :approvable true
                                       :body [{:name "single3" :type :string}]}]})

(def schema-with-approvals {:info {:name "approval-model-with-approvals"
                                   :version 1
                                   :approvable true}
                            :body [{:name "single" :type :string :approvable true}
                                   {:name "single2" :type :string}
                                   {:name "repeats" :type :group :repeating true :approvable true
                                    :body [{:name "single3" :type :string}]}]})

(schemas/defschema 1 schema-without-approvals)
(schemas/defschema 1 schema-with-approvals)

(facts "approve document part"
  (let [document (model/new-document schema-with-approvals ..now..)]
    (approvable? document schema-with-approvals [:single]) => true
    (approvable? document schema-with-approvals [:single2]) => false
    (approvable? document schema-with-approvals [:repeats :1]) => true
    (approvable? document schema-with-approvals [:repeats :0 :single3]) => false))

