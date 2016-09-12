(ns lupapalvelu.info-links-itest
  (:require [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.info-links :as info-links]
            [sade.util :as util]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(apply-remote-minimal)

(facts "info links"
 
  (let [{application-id :id :as app} 
        (create-and-open-application pena :propertyId sipoo-property-id)]
   
    application-id => truthy
   
    (let [response (query pena :info-links :id application-id)]
      response => ok?
      (fact "Initially Pena sees no links" (:links response) => []))

    (let [response (command sonja :info-link-upsert :id application-id :text "link text" :url "http://example.org/1")]
      response => ok?
      (fact "Sonja can add an info-link"
        (:linkId response) => 1))

    (let [response (query sonja :info-links :id application-id)]
      response => ok?
      (fact "Sonja sees the infolink, it is not new and is editable"
        (:text (first (:links response))) => "link text"
        (:url  (first (:links response))) => "http://example.org/1"
        ;(:isNew (first (:links response))) => false
        (:canEdit (first (:links response))) => true))
     
    (let [response (command sonja :info-link-upsert :id application-id :text "second" :url "http://example.org/2")]
      response => ok?
      (fact "Sonja adds another info-link"
         (:linkId response) => 2))

    (let [response (command sonja :info-link-upsert :id application-id :text "third url" :url "http://example.org/3")]
      response => ok?
      (fact "Sonja adds a third info-link"
        (:linkId response) => 3))
       
    (let [response (command sonja :info-link-reorder :id application-id :linkIds [3 2 1])]
      response => ok?
      (fact "Sonja reorders the links"
        (:res response) => true))

    (let [response (query sonja :info-links :id application-id)]
      response => ok?
      (fact "Sonja sees ordered links"
        (map :linkId (:links response)) => [3 2 1]))
         
    (let [response (command sonja :info-link-reorder :id application-id :linkIds [1 1 2 5])]
      response => ok?
      (fact "Sonja reorders the links badly"
        (:res response) => false))

    (let [response (command sonja :info-link-delete :id application-id :linkId 2)]
      (fact "Sonja can delete infolinks" 
        response => ok?))
    
    (let [response (command pena :info-link-delete :id application-id :linkId 1)]
      (fact "Pena can't delete infolinks" 
        response =not=> ok?))
    
    (let [response (query sonja :info-links :id application-id)]
      response => ok?
      (fact "Sonja no longer sees the deleted link"
        (map :linkId (:links response)) => [3 1]))
     
    (let [response (query pena :info-links :id application-id)]
      response => ok?
      (fact "Pena hasn't seen the links yet and sees he cant' modify them"
        (map :isNew (:links response)) => [true true]
        (map :canEdit (:links response)) => [false false]))

    (let [resp (command pena :mark-seen :id application-id :type "info-links")
          response (query pena :info-links :id application-id)]
      response => ok?
      (fact "Pena has seen the links after calling mark-seen" (map :isNew (:links response)) => [false false]))
  
    (let [response (command sonja :info-link-upsert :id application-id :text "new text" :url "http://example.org/1-new" :linkId "one")]
      (fact "Sonja fails to update link with bad id"
         response =not=> ok?))
  
    (let [response (command sonja :info-link-upsert :id application-id :text "new text" :url "http://example.org/1-new" :linkId 1)]
      response => ok?
      (fact "Sonja updates an infolink"
        (:linkId response) => 1))
  
    (let [response (command pena :info-link-upsert :id application-id :text "new text bad" :url "http://example.org/1-bad" :linkId 1)]
      (fact "Pena fails to update an infolink"
        response =not=> ok?))
  
    (let [response (query pena :info-links :id application-id)]
      response => ok?
      (fact "Pena sees the new updated link by Sonja as a new one at right position"
        (:isNew (nth (:links response) 1)) => true
        (:url (nth (:links response) 1)) => "http://example.org/1-new"
        (:text (nth (:links response) 1)) => "new text"))

    ))

(fact "Organization links"
      (let [app-id (create-app-id pena :propertyId sipoo-property-id :operation "pientalo")]
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
                       :url "http://example.com"
                       :nameFi "Esimerkki" :nameSv "Exempel"
                       :index 0)
              => ok?)
        (fact "Now the first link is new again"
              (-> (query pena :organization-links :id app-id :lang "fi")
                  :links first) => {:url "http://example.com" :text "Esimerkki" :isNew true})
        (fact "Add new link"
              (command sipoo :add-organization-link
                       :url "http://www.lupapiste.fi"
                       :nameFi "Lupapiste" :nameSv "Lupapiste"))
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
                          :links (map :isNew))=> '(false false false))))
      (fact "Organization without links"
            (let [app-id (create-app-id veikko
                                        :propertyId tampere-property-id
                                        :operation "ya-katulupa-vesi-ja-viemarityot")]
              (query veikko :organization-links :id app-id :lang "fi") => (contains {:links []}))))
