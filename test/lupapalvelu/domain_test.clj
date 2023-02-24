(ns lupapalvelu.domain-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.domain :refer :all]))

(testable-privates lupapalvelu.domain only-authority-sees-drafts normalize-neighbors)

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

(facts "get document(s) by subtype"
  (let [documents [{:id 1 :data "jee" :schema-info {:name "hakija-ya" :type :location :subtype "hakija"}}
                   {:id 2 :data "jee2" :schema-info {:name "hakija-r" :type :location :subtype "hakija"}}
                   {:id 3 :data "jee3" :schema-info {:name "ilmoittaja" :type :location :subtype "hakija"}}
                   {:id 4 :data "error type" :schema-info {:name "other" :type :location :subtype "error"}}
                   {:id 5 :data "keyword" :schema-info {:name "other" :type :location :subtype :keyword}}]]
    (fact "two hakija docs"
      (get-documents-by-subtype documents "hakija") => (just [(first documents) (second documents) (nth documents 2)])
      (get-document-by-subtype documents "hakija") => (first documents)
      (get-document-by-subtype {:documents documents} :hakija) => (first documents))
    (fact "one error docs"
      (get-documents-by-subtype documents "error") => [(nth documents 3)]
      (get-document-by-subtype documents "error") => (nth documents 3))
    (fact "unknown returns in empty list"
      (get-documents-by-subtype documents "unknown") => empty?
      (get-document-by-subtype documents "unknown") => nil)
    (fact "keyword also works" (get-documents-by-subtype documents :hakija) => (just [(first documents) (second documents) (nth documents 2)]))
    (fact "keyword as subtype value"
      (get-documents-by-subtype documents :keyword) => (just [(last documents)])
      (get-document-by-subtype documents :keyword) => (last documents))))

(facts "invites"
  (let [invite1 {:email "abba@example.com" :inviter "inviter1"}
        invite2 {:email "kiss@example.com"}
        app     {:auth [{:role "writer" :invite invite1}
                        {:role "writer" :invite invite2 :type :company :inviter "inviter2"}
                        {:role "writer"}]}]
    (fact "has two invites" (invites app) => (just (assoc invite1 :type nil :inviter "inviter1")
                                                   (assoc invite2 :type :company :inviter "inviter2")))))

(facts "write-access?"
  (let [writer  {:id 2 :role "writer"}
        foreman {:id 3 :role "foreman"}
        reader  {:id 4 :role "reader"}
        app     {:auth [writer foreman reader]}]
    (fact "writer has accesss" (write-access? app (:id writer)) => true)
    (fact "foreman has accesss" (write-access? app (:id foreman)) => true)
    (fact "reader doesn't have accesss" (write-access? app (:id reader)) => false)
    (fact "someone else doesn't have accesss" (write-access? app 5) => false)))

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
    (let [app {:handlers [{:userId "foo"}]}
          neighbors [{:id "1"
                      :status [{:state "open"}
                               {:state "response-given"
                                :vetuma {:name "foo" :userid "123"}}]}
                     {:id "2"
                      :status [{:state "open"}
                               {:state "response-given"
                                :vetuma {:name "foo" :userid "123"}}
                               {:state "forgotten"}]}]]

      (fact "Handler sees hetus"
        (normalize-neighbors app {:id "foo"} neighbors)
        => [{:id "1"
             :status [{:state "open"}
                      {:state "response-given"
                       :vetuma {:name "foo", :userid "123"}}]}
            {:id "2"
             :status [{:state "open"}
                      {:state "response-given"
                       :vetuma {:name "foo", :userid "123"}}
                      {:state "forgotten"}]}])

      (fact "Role does not matter for hetu visibility"
        (normalize-neighbors app {:role "other"} neighbors)
        => [{:id "1"
             :status [{:state "open"}
                      {:state "response-given"
                       :vetuma {:name "foo"}}]}
            {:id "2"
             :status [{:state "open"}
                      {:state "response-given"
                       :vetuma {:name "foo"}}
                      {:state "forgotten"}]}]
        (normalize-neighbors app {:role "other"} neighbors)
        => [{:id "1"
             :status [{:state "open"}
                      {:state "response-given"
                       :vetuma {:name "foo"}}]}
            {:id "2"
             :status [{:state "open"}
                      {:state "response-given"
                       :vetuma {:name "foo"}}
                      {:state "forgotten"}]}])))

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
                              :removed true
                              :target {:type "attachment"
                                       :id 0}}]]
      (fact "even comments for deleted attachments are returned, but not those which are empty"
        (-> application
            flag-removed-attachment-comments
            cleanup-attachment-comments
            :comments) => expected-comments))))

(facts enrich-application-handlers
  (fact "handlers not used -> not enriched -> no mongo calls"
    (:organization (enrich-application-handlers {:organization ..org-id..
                                                 :handlers [{:id ..id..
                                                             :userId ..user-id..
                                                             :roleId ..role-id..
                                                             :firstName ..first-name..
                                                             :lastName ..last-name..}]})) => ..org-id..
    (provided (lupapalvelu.mongo/select-one :organizations {:_id ..org-id..} [:handler-roles]) => irrelevant :times 0))

  (fact "one handler in org - one in app"
    (:handlers (enrich-application-handlers {:organization ..org-id..
                                             :handlers [{:id ..id..
                                                         :userId ..user-id..
                                                         :roleId ..role-id..
                                                         :firstName ..first-name..
                                                         :lastName ..last-name..}]})) => [{:id ..id..
                                                                                           :userId ..user-id..
                                                                                           :roleId ..role-id..
                                                                                           :firstName ..first-name..
                                                                                           :lastName ..last-name..
                                                                                           :name ..name..}]
    (provided (lupapalvelu.mongo/select-one :organizations {:_id ..org-id..} [:handler-roles]) => {:handler-roles [{:id ..role-id.. :name ..name..}]}))

  (fact "no handlers in org - one in app - handler not enriched"
    (enrich-application-handlers {:organization ..org-id.. :data ..data.. :handlers [{:id ..handler-id.. :roleId ..role-id..}]}) =>
    {:organization ..org-id.. :data ..data.. :handlers [{:id ..handler-id.. :roleId ..role-id..}]}
    (provided (lupapalvelu.mongo/select-one :organizations {:_id ..org-id..} [:handler-roles]) => {:handler-roles []}))

  (fact "one handler in org - none in app"
    (enrich-application-handlers {:organization ..org-id.. :data ..data.. :handlers []}) =>
    {:organization ..org-id.. :data ..data.. :handlers []}
    (provided (lupapalvelu.mongo/select-one :organizations {:_id ..org-id..} [:handler-roles]) => irrelevant :times 0))

  (fact "organization is given as argument"
    (enrich-application-handlers {:organization ..org-id.. :data ..data.. :handlers [{:id ..handler-id.. :roleId ..role-id..}]}
                                 {:handler-roles [{:id ..role-id.. :name ..name..}]}) =>
    {:organization ..org-id.. :data ..data.. :handlers [{:id ..handler-id.. :roleId ..role-id.. :name ..name..}]}
    (provided (lupapalvelu.mongo/select-one :organizations {:_id ..org-id..} [:handler-roles]) => irrelevant :times 0))

  (fact "multiple handlers in org - two in app"
    (enrich-application-handlers {:organization ..org-id.. :data ..data.. :handlers [{:id ..handler-id-2.. :roleId ..role-id-2..} {:id ..handler-id-0.. :roleId ..role-id-0..}]}) =>
    {:organization ..org-id.. :data ..data.. :handlers [{:id ..handler-id-2.. :roleId ..role-id-2.. :name ..name-2..} {:id ..handler-id-0.. :roleId ..role-id-0.. :name ..name-0..}]}
    (provided (lupapalvelu.mongo/select-one :organizations {:_id ..org-id..} [:handler-roles]) => {:handler-roles [{:id ..role-id-0.. :name ..name-0..} {:id ..role-id-1.. :name ..name-1..} {:id ..role-id-2.. :name ..name-2..}  {:id ..role-id-3.. :name ..name-3..}]})))


(facts enrich-application-tags
  (fact "empty application tags"
    (enrich-application-tags {:id ..app-id.. :tags []} {:tags [{:id ..tag-1.. :label ..label-1..}]})
    => {:id ..app-id.. :tags []})

  (fact "no application tags"
    (enrich-application-tags {:id ..app-id..} {:tags [{:id ..tag-1.. :label ..label-1..}]})
    => {:id ..app-id.. :tags []})

  (fact "empty organization tags (invalid case)"
    (enrich-application-tags {:id ..app-id.. :tags [..tag-1..]} {:tags []})
    => {:id ..app-id.. :tags []})

  (fact "no organization tags (invalid case)"
    (enrich-application-tags {:id ..app-id.. :tags [..tag-1..]} {})
    => {:id ..app-id.. :tags []})

  (fact "one organization tag and one application tag"
    (enrich-application-tags {:id ..app-id.. :tags [..tag-1..]} {:tags [{:id ..tag-1.. :label ..label-1..}]})
    => {:id ..app-id.. :tags [{:id ..tag-1.. :label ..label-1..}]})

  (fact "one organization tag and one non matching application tag (invalid case)"
    (enrich-application-tags {:id ..app-id.. :tags [..tag-2..]} {:tags [{:id ..tag-1.. :label ..label-1..}]})
    => {:id ..app-id.. :tags []})

  (fact "multiple organization tags and one application tag"
    (enrich-application-tags {:id ..app-id.. :tags [..tag-2..]}
                             {:tags [{:id ..tag-1.. :label ..label-1..}
                                     {:id ..tag-2.. :label ..label-2..}
                                     {:id ..tag-3.. :label ..label-3..}]})
    => {:id ..app-id.. :tags [{:id ..tag-2.. :label ..label-2..}]})

  (fact "multiple organization tags and multiple application tags"
    (enrich-application-tags {:id ..app-id.. :tags [..tag-2.. ..tag-3.. ..tag-5.. ..tag-6..]}
                             {:tags [{:id ..tag-1.. :label ..label-1..}
                                     {:id ..tag-2.. :label ..label-2..}
                                     {:id ..tag-3.. :label ..label-3..}
                                     {:id ..tag-4.. :label ..label-4..}
                                     {:id ..tag-5.. :label ..label-5..}]})
    => {:id ..app-id.. :tags [{:id ..tag-2.. :label ..label-2..}
                              {:id ..tag-3.. :label ..label-3..}
                              {:id ..tag-5.. :label ..label-5..}]}))
