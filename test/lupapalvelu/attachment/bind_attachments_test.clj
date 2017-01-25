(ns lupapalvelu.attachment.tags-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.attachment.bind :refer :all]
            [lupapalvelu.attachment.bind-attachments-api :refer :all]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.operations :as op]
            [schema.core :as sc]))

(testable-privates lupapalvelu.attachment.bind-attachments-api validate-attachment-groups)

(facts validate-attachment-groups
  (fact "one file"
    (let [filedatas [{:group {:groupType "operation" :operations [{:id ..op-id.. :name ..op-name..}]}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => nil
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => nil)
    (provided (sc/check [att/Operation] [{:id ..op-id.. :name ..op-name..}]) => nil))

  (fact "multiple files"
    (let [filedatas [{:group {:groupType "operation" :operations [{:id ..op-id.. :name ..op-name..}]}}
                     {:group {:groupType "parties"}}
                     {:group {:groupType "building-site" :operations nil}}
                     {:group nil}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => nil
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => nil)
    (provided (sc/check [att/Operation] [{:id ..op-id.. :name ..op-name..}]) => nil))

  (fact "attachment op selection not allowed"
    (let [filedatas [{:group nil}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => nil)

  (fact "attachment op selection not allowed - trying to set"
    (let [filedatas [{:group {:groupType "building-site"}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => (partial expected-failure? :error.illegal-meta-type)
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => false))

  (fact "illegal group type"
    (let [filedatas [{:group {:groupType "invalid group type"}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => (partial expected-failure? :error.illegal-attachment-group-type))

  (fact "illegal operation"
    (let [filedatas [{:group {:groupType "parties"}}
                     {:group {:groupType "operation" :operations [{:id ..op-id.. :name ..op-name..} {}]}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => (partial expected-failure? :error.illegal-attachment-operation)
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => nil)
    (provided (sc/check [att/Operation] [{:id ..op-id.. :name ..op-name..} {}]) => :failure)))
