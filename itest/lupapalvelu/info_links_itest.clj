(ns lupapalvelu.info-links-itest
  (:require [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.info-links :as info-links]
            [sade.util :as util]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(defn- localized [text-map]
  (i18n/with-default-localization text-map (:fi text-map)))

(apply-remote-minimal)

(facts "Application info links"

  (let [{application-id :id :as app}
        (create-and-open-application pena :propertyId sipoo-property-id)]

   application-id => truthy

   (fact "Invite statement giver"
      (command sonja :request-for-statement :id application-id :functionCode nil :selectedPersons
         [{:email "teppo@example.com" :text "Hello" :name "Tepanderi"}]) => ok?)

    (let [response (query pena :info-links :id application-id)]
      response => ok?
      (fact "Initially Pena sees no links" (:links response) => []))

    (let [response (command sonja :info-link-upsert :id application-id :text "sonja-1" :url "http://example.org/1")]
      response => ok?
      (fact "Sonja can add an info-link"
        (:linkId response) => string?))

    (let [response (query sonja :info-links :id application-id)]
      response => ok?
      (fact "Sonja sees the infolink, it is new and is editable"
        (:text (first (:links response))) => "sonja-1"
        (:url  (first (:links response))) => "http://example.org/1"
        (:isNew (first (:links response))) => true
        (:canEdit (first (:links response))) => true))

    (let [response (query teppo :info-links :id application-id)]
      response => ok?
      (fact "Statementgiver Teppo sees the infolink and it is not editable"
        (:text (first (:links response))) => "sonja-1"
        (:canEdit (first (:links response))) => false))

    (let [response (command teppo :info-link-upsert :id application-id :text "teppo-2" :url "http://example.org/2")]
      response => ok?
      (fact "Statement giver Teppo adds another info-link"
         (:linkId response) => string?))

    (let [response (query teppo :info-links :id application-id)]
      response => ok?
      (fact "Statementgiver Teppo sees his own infolink and it is editable"
        (:text (second (:links response))) => "teppo-2"
        (:canEdit (second (:links response))) => true))

    (let [response (command sonja :info-link-upsert :id application-id :text "sonja-3" :url "http://example.org/3")]
      response => ok?
      (fact "Sonja adds a third info-link"
        (:linkId response) => string?))

    (let [links (:links (query sonja :info-links :id application-id))
          l1-id (:linkId (nth links 0))   ;; by sonja
          l2-id (:linkId (nth links 1))   ;; by teppo
          l3-id (:linkId (nth links 2))]  ;; by sonja

       (fact "Sonja sees the correct links in correct order"
          (map :text links) = ["sonja-1" "teppo-2" "sonja-3"])

       (fact "Sonja can reorder links"
         (command sonja :info-link-reorder :id application-id :linkIds [l3-id l2-id l1-id]) => ok?)

       (fact "Sonja sees the reordered links in order"
          (map :text (:links (query sonja :info-links :id application-id))) = ["sonja-3" "teppo-2" "sonja-1"])

       (fact "Pena sees no links as editable"
          (map :canEdit (:links (query pena :info-links :id application-id))) = [false false false])

       (fact "Teppo sees only his link as editable"
          (map :canEdit (:links (query teppo :info-links :id application-id))) = [false true false])

       (fact "Sonja sees all links as editable"
          (map :canEdit (:links (query sonja :info-links :id application-id))) = [true true true])

       (fact "Pena can't update Sonja's infolink"
         (command pena :info-link-upsert :id application-id :text "bad text" :url "http://example.org/1-bad" :linkId l1-id) =not=> ok?)

       (fact "Pena can't delete Sonja's infolink"
         (command pena :info-link-delete :id application-id :linkId (:linkId l3-id)) =not=> ok?)

       (fact "Teppo can't update Sonja's infolink"
         (command teppo :info-link-upsert :id application-id :text "bad text" :url "http://example.org/1-bad" :linkId l1-id) =not=> ok?)

       (fact "Teppo can't delete Sonja's infolink"
         (command teppo :info-link-delete :id application-id :linkId (:linkId l3-id)) =not=> ok?)

       (fact "Sonja still sees the correct links"
          (map :text (:links (query sonja :info-links :id application-id))) = ["sonja-3" "teppo-2" "sonja-1"])

       (fact "Sonja can delete Teppo's link"
         (command sonja :info-link-delete :id application-id :linkId l2-id) => ok?)

       (fact "Sonja sees the link was removed"
          (map :text (:links (query sonja :info-links :id application-id))) = ["sonja-3" "sonja-1"])

       (fact "Pena thinks the links are new before calling mark-seen"
          (map :isNew (:links (query pena :info-links :id application-id))) = [true true])

       (fact "Pena can mark info links as seen"
           (command pena :mark-seen :id application-id :type "info-links") => ok?)

       (fact "Pena has now seen the links"
          (map :isNew (:links (query pena :info-links :id application-id))) = [false false])

       (fact "Sonja can't update a non-existent link"
          (command sonja :info-link-upsert :id application-id :text "new text" :url "http://example.org/one-new" :linkId "one") =not=> ok?)

       (fact "Sonja can update an existing link"
          (command sonja :info-link-upsert :id application-id :text "sonja-1-new" :url "http://example.org/1-new" :linkId l1-id) => ok?)

       (fact "Pena now hasn't seen the updated link"
          (map :isNew (:links (query pena :info-links :id application-id))) = [false true])

       (fact "Pena sees the change made by Sonja"
          (map :text (:links (query pena :info-links :id application-id))) = ["sonja-3" "sonja-1-new"]))))

(fact "Organization links"
      (let [app-id (create-app-id pena :propertyId sipoo-property-id :operation "pientalo")
            example-url    (localized {:fi "http://example.com"})
            example-name   (localized {:fi "Esimerkki", :sv "Exempel"})
            lupapiste-url  (localized {:fi "http://lupapiste.fi"})
            lupapiste-name (localized {:fi "Lupapiste"})]
        (fact "Unseen default minimal links (no timestamps)"
              (query pena :organization-links :id app-id :lang "fi")
              => {:ok true
                  :links [{:url "http://sipoo.fi"
                           :text "Sipoo"
                           :isNew true}
                          {:url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"
                           :text "Rakennusvalvonta"
                           :isNew true}]})
        (fact "Swedish links"
              (-> (query pena :organization-links :id app-id :lang "sv")
                  :links first :text)=> "Sibbo")
        (fact "No language falls back to Finnish"
              (let [result (query pena :organization-links :id app-id :lang "")]
                (-> result
                    :links first :text) => "Sipoo"))
        (fact "Mark seen"
              (command pena :mark-seen-organization-links :id app-id) => ok?)
        (fact "Links are no longer new"
              (->> (query pena :organization-links :id app-id :lang "fi")
                   :links (map :isNew))=> '(false false))
        (fact "Update the first link"
              (command sipoo :update-organization-link
                       :url example-url
                       :name example-name
                       :index 0)
              => ok?)
        (fact "Now the first link is new again"
              (-> (query pena :organization-links :id app-id :lang "fi")
                  :links first) => {:url (:fi example-url) :text "Esimerkki" :isNew true})
        (fact "Add new link"
              (command sipoo :add-organization-link
                       :url lupapiste-url
                       :name lupapiste-name))
        (fact "There are two new links"
              (->> (query pena :organization-links :id app-id :lang "fi")
                   :links (map :isNew))=> '(true false true))
        (fact "Mark seen again"
              (command pena :mark-seen-organization-links :id app-id) => ok?)
        (fact "Links are no longer new  again"
              (->> (query pena :organization-links :id app-id :lang "fi")
                   :links (map :isNew))=> '(false false false))
        (fact "Authorities can see the organization links in draft state"
              (query sonja :organization-links :id app-id :lang "fi") => ok?
              (query luukas :organization-links :id app-id :lang "fi") => ok?)
        (fact "Authorities can see the application info links in draft state"
              (query sonja :info-links :id app-id) => ok?
              (query luukas :info-links :id app-id) => ok?)
        (facts "Statement giver"
               (fact "Submit application"
                     (command pena :submit-application :id app-id) => ok?)
               (fact "Invite statement giver"
                     (command sonja :request-for-statement
                              :id app-id
                              :functionCode nil
                              :selectedPersons [{:email "teppo@example.com"
                                                 :text "Hello"
                                                 :name "Tepanderi"}]) => ok?)
               (fact "Statement giver sees all new links"
                     (->> (query teppo :organization-links :id app-id :lang "fi")
                          :links (map :isNew))=> '(true true true))
               (fact "Mark seen"
                     (command teppo :mark-seen-organization-links :id app-id) => ok?
                     (->> (query teppo :organization-links :id app-id :lang "fi")
                          :links (map :isNew))=> '(false false false)))
        (facts "Organization link urls must be valid"
               (fact "Add link"
                     (command sipoo :add-organization-link
                              :url (assoc lupapiste-url
                                          :fi "not-a-valid-url")
                              :name lupapiste-name)
                     => {:ok false, :text "error.invalid.url"})
               (fact "Update link"
                     (command sipoo :update-organization-link
                              :url (assoc lupapiste-url
                                          :sv "not-a-valid-url")
                              :name lupapiste-name
                              :index 0)
                     => {:ok false, :text "error.invalid.url"})
               (fact "Remove link"
                     (command sipoo :remove-organization-link
                              :url (assoc lupapiste-url
                                          :fi "not-a-valid-url")
                              :name lupapiste-name)
                     => {:ok false, :text "error.invalid.url"})))
      (fact "Organization without links"
            (let [app-id (create-app-id veikko
                                        :propertyId tampere-property-id
                                        :operation "ya-katulupa-vesi-ja-viemarityot")]
              (query veikko :organization-links :id app-id :lang "fi") => (contains {:links []}))))
