(ns lupapalvelu.document.document-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.document :refer [create-doc-validator]]))

(testable-privates lupapalvelu.document.document deny-remove-of-last-document)

(facts create-doc-validator
  ; type is "YA" and at least one doc is "party" -> fail
  (create-doc-validator {:application {:permitType "YA"
                                       :documents [{:schema-info {:type "festivity"}}
                                                   {:schema-info {:type "party"}}
                                                   {:schema-info {:type "celebration"}}]}}) => {:ok false, :text "error.create-doc-not-allowed"}
  ; none of the docs are "party"
  (create-doc-validator {:application {:permitType "YA"
                                       :documents [{:schema-info {:type "festivity"}}
                                                   {:schema-info {:type "celebration"}}]}}) => nil
  ; type is not "YA"
  (create-doc-validator {:application {:permitType "R"
                                       :documents [{:schema-info {:type "festivity"}}
                                                   {:schema-info {:type "party"}}
                                                   {:schema-info {:type "celebration"}}]}}) => nil)

(facts "deny-remove-of-last-document"
  (deny-remove-of-last-document nil nil) => falsey
  (deny-remove-of-last-document {} nil) => falsey
  (deny-remove-of-last-document nil []) => falsey

  (fact "poikkeamis rakennushanke"
    (let [rakennushanke {:schema-info {:name "rakennushanke"}}]
      (deny-remove-of-last-document rakennushanke {:documents []}) => true
      (deny-remove-of-last-document rakennushanke {:documents [rakennushanke]}) => true
      (deny-remove-of-last-document rakennushanke {:documents [rakennushanke rakennushanke]}) => falsey))

  (fact "suunnittelija"
    (let [suunnittelija {:schema-info {:name "suunnittelija"}}]
     (deny-remove-of-last-document suunnittelija {:documents [suunnittelija]}) => falsey
     (deny-remove-of-last-document suunnittelija {:documents [suunnittelija suunnittelija]}) => falsey)))
