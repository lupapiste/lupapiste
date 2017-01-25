(ns lupapalvelu.domain-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.domain :refer :all]
            [lupapalvelu.document.schemas :as schemas]))

(testable-privates lupapalvelu.domain only-authority-sees-drafts filter-targeted-attachment-comments normalize-neighbors)

(facts
  (let [application {:documents [{:id 1 :data "jee"} {:id 2 :data "juu"} {:id 1 :data "hidden"}]}]
    (fact (get-document-by-id application 1) => {:id 1 :data "jee"})
    (fact (get-document-by-id application 2) => {:id 2 :data "juu"})
    (fact (get-document-by-id application -1) => nil)))

(facts
  (let [application {:documents [{:id 1 :data "jee" :schema-info {:name "kukka"}}]}]
    (fact (get-document-by-name application "kukka") => (first (:documents application)))
    (fact (get-document-by-name application "") => nil)))

(facts
  (let [application {:documents [{:id 1 :data "jee" :schema-info {:name "kukka" :type "location"}}]}]
    (fact (get-document-by-type application :location) => {:id 1 :data "jee" :schema-info {:name "kukka" :type "location"}})
    (fact (get-document-by-type application "location") => {:id 1 :data "jee" :schema-info {:name "kukka" :type "location"}})
    (fact (get-document-by-type application :not-gona-found) => nil)))

(facts "get by type works when schema type is keyword (from schemas.clj), LUPA-1801"
  (let [application {:documents [{:id 1 :data "jee" :schema-info {:name "kukka" :type :location}}]}]
    (fact (get-document-by-type application :location) => {:id 1 :data "jee" :schema-info {:name "kukka" :type :location}})
    (fact (get-document-by-type application "location") => {:id 1 :data "jee" :schema-info {:name "kukka" :type :location}})))

(facts "get documents by subtype"
  (let [documents [{:id 1 :data "jee" :schema-info {:name "hakija-ya" :type :location :subtype "hakija"}}
                   {:id 2 :data "jee2" :schema-info {:name "hakija-r" :type :location :subtype "hakija"}}
                   {:id 3 :data "jee3" :schema-info {:name "ilmoittaja" :type :location :subtype "hakija"}}
                   {:id 4 :data "error type" :schema-info {:name "other" :type :location :subtype "error"}}
                   {:id 5 :data "keyword" :schema-info {:name "other" :type :location :subtype :keyword}}]]
    (fact "two hakija docs" (get-documents-by-subtype documents "hakija") => (just [(first documents) (second documents) (nth documents 2)]))
    (fact "one error docs" (get-documents-by-subtype documents "error") => [(nth documents 3)])
    (fact "unknown returns in empty list" (get-documents-by-subtype documents "unknown") => empty?)
    (fact "keyword also works" (get-documents-by-subtype documents :hakija) => (just [(first documents) (second documents) (nth documents 2)]))
    (fact "keyword as subtype value" (get-documents-by-subtype documents :keyword) => (just [(last documents)]))))

(facts "invites"
  (let [invite1 {:email "abba@example.com"}
        invite2 {:email "kiss@example.com"}
        app     {:auth [{:role "writer" :invite invite1}
                        {:role "writer" :invite invite2}
                        {:role "owner"}]}]
    (fact "has two invites" (invites app) => (just invite1 invite2))
    (fact "abba@example.com has one invite" (invite app "abba@example.com") => invite1)
    (fact "jabba@example.com has no invite" (invite app "jabba@example.com") => nil)))

(facts "owner-or-write-access?"
  (let [owner   {:id 1 :role "owner"}
        writer  {:id 2 :role "writer"}
        foreman {:id 3 :role "foreman"}
        reader  {:id 4 :role "reader"}
        app     {:auth [owner writer foreman reader]}]
    (fact "owner has accesss"   (owner-or-write-access? app (:id owner)) => true)
    (fact "writer has accesss" (owner-or-write-access? app (:id writer)) => true)
    (fact "foreman has accesss" (owner-or-write-access? app (:id foreman)) => true)
    (fact "reader doesn't have accesss" (owner-or-write-access? app (:id reader)) => false)
    (fact "someone else doesn't have accesss" (owner-or-write-access? app 5) => false)))

(facts validate-access
  (let [app {:auth [{:id "basic-user"    :role "usering"}
                    {:id "app-authority" :role "authoring"}
                    {:id "some-co"       :role "usering"}
                    {:id "another-user"  :role "stalking"}]}]
    (fact "basic-user has 'usering' access"
      (validate-access [:usering :authoring :hazling] {:application app :user {:id "basic-user"}}) => nil)
    (fact "basic-user has not 'authoring' access"
      (validate-access [:authoring] {:application app :user {:id "basic-user"}}) => {:ok false :text "error.unauthorized"})
    (fact "basic-user has not 'hazling' access"
      (validate-access [:hazling] {:application app :user {:id "basic-user"}}) =>  {:ok false :text "error.unauthorized"})
    (fact "another-user has 'stalking' access"
      (validate-access [:hazling] {:application app :user {:id "basic-user"}}) =>  {:ok false :text "error.unauthorized"})
    (fact "another-user has 'stalking' access"
      (validate-access [:stalking] {:application app :user {:id "another-user"}}) =>  nil)
    (fact "another-user has not any of the 'usering', 'authoring' or 'hazling' accessses"
      (validate-access [:usering :authoring :hazling] {:application app :user {:id "another-user"}}) =>  {:ok false :text "error.unauthorized"})
    (fact "company-user has 'usering' access"
      (validate-access [:usering :authoring :hazling] {:application app :user {:id "co-user" :company {:id "some-co"}}})
      => nil)
    (fact "company-user of anothercp  has no  access"
      (validate-access [:usering :authoring :hazling] {:application app :user {:id "co-user" :company {:id "another-co"}}}) =>  {:ok false :text "error.unauthorized"})))

(facts "only-authority-sees-drafts"
  (only-authority-sees-drafts {:role "authority"} [{:draft true}]) => [{:draft true}]
  (only-authority-sees-drafts {:role "not-authority"} [{:draft true}]) => []
  (only-authority-sees-drafts {:role "authority"} [{:draft false}]) => [{:draft false}]
  (only-authority-sees-drafts {:role "not-authority"} [{:draft false}]) => [{:draft false}]

  (only-authority-sees-drafts nil [{:draft false}]) => [{:draft false}]
  (only-authority-sees-drafts nil [{:draft true}]) => empty?
  (only-authority-sees-drafts {:role "authority"} []) => empty?
  (only-authority-sees-drafts {:role "non-authority"} []) => empty?
  (only-authority-sees-drafts {:role "authority"} nil) => empty?
  (only-authority-sees-drafts {:role "non-authority"} nil) => empty?
  (only-authority-sees-drafts {:role "authority"} [{:draft nil}]) => [{:draft nil}]
  (only-authority-sees-drafts {:role "non-authority"} [{:draft nil}]) => [{:draft nil}]
  (only-authority-sees-drafts {:role "authority"} [{}]) => [{}]
  (only-authority-sees-drafts {:role "nono-authority"} [{}]) => [{}])

(facts "Filtering application data"

  (facts "normalize-neighbors"
    (let [neighbors [{:id "1"
                      :status [{:state "open"}
                               {:state "response-given"
                                :vetuma {:name "foo" :userid "123"}}]}
                     {:id "2"
                      :status [{:state "open"}
                               {:state "response-given"
                                :vetuma {:name "foo" :userid "123"}}
                               {:state "forgotten"}]}]]

      (normalize-neighbors {:role "authority"} neighbors) => [{:id "1"
                                                         :status [{:state "open"}
                                                                  {:state "response-given"
                                                                   :vetuma {:name "foo", :userid "123"}}]}
                                                        {:id "2"
                                                         :status [{:state "open"}
                                                                  {:state "response-given"
                                                                   :vetuma {:name "foo", :userid "123"}}
                                                                  {:state "forgotten"}]}]

      (normalize-neighbors {:role "other"} neighbors) => [{:id "1"
                                                     :status [{:state "open"}
                                                              {:state "response-given"
                                                               :vetuma {:name "foo", :userid nil}}]}
                                                    {:id "2"
                                                     :status [{:state "open"}
                                                              {:state "response-given"
                                                               :vetuma {:name "foo", :userid nil}}
                                                              {:state "forgotten"}]}]))

  (facts "Comments"
    (let [application {:attachments [{:id 1}
                                     {:id 2}]
                       :comments [{:text "nice application"
                                   :target {:type "application"}}
                                  {:text "attachment for you"
                                   :target {:type "attachment"
                                            :id 1}}
                                  {:text "deleted attachment comment"
                                   :target {:type "attachment"
                                            :id 0}}
                                  {:text nil ; 'insertion' comment of attachment
                                   :target {:type "attachment"
                                            :id 0}}]}
          expected-comments [{:text "nice application"
                              :target {:type "application"}}
                             {:text "attachment for you"
                              :target {:type "attachment"
                                       :id 1}}
                             {:text "deleted attachment comment"
                              :target {:type "attachment"
                                       :id 0}}]]
      (fact "even comments for deleted attachments are returned, but not those which are empty"
        (:comments (filter-targeted-attachment-comments application)) => expected-comments))))
