(ns lupapalvelu.info-links-itest
  (:require [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.info-links :as info-links]
            [sade.util :as util]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(apply-remote-minimal)

(defn link-id [links url]
   (:id (first (filter #(= url (:url %)) links))))

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

    (let [response (command sonja :info-link-upsert :id application-id :text "link text" :url "http://example.org/1")]
      response => ok?
      (fact "Sonja can add an info-link"
        (:linkId response) => string?))

    (let [response (query sonja :info-links :id application-id)]
      response => ok?
      (fact "Sonja sees the infolink, it is new and is editable"
        (:text (first (:links response))) => "link text"
        (:url  (first (:links response))) => "http://example.org/1"
        (:isNew (first (:links response))) => true
        (:canEdit (first (:links response))) => true))
     
    (let [response (query teppo :info-links :id application-id)]
      response => ok?
      (fact "Statementgiver Teppo sees the infolink and it is not editable"
        (:text (first (:links response))) => "link text"
        (:canEdit (first (:links response))) => false))

    (let [response (command teppo :info-link-upsert :id application-id :text "second" :url "http://example.org/2")]
      response => ok?
      (fact "Statement giver Teppo adds another info-link"
         (:linkId response) => string?))
   
    (let [response (query teppo :info-links :id application-id)]
      response => ok?
      (fact "Statementgiver Teppo sees his own infolink and it is editable"
        (:text (second (:links response))) => "second"
        (:canEdit (second (:links response))) => true))

    (let [response (command sonja :info-link-upsert :id application-id :text "third url" :url "http://example.org/3")]
      response => ok?
      (fact "Sonja adds a third info-link"
        (:linkId response) => string?))
       
    (let [links (:links (query sonja :info-links :id application-id))
          l1-id (:linkId (nth links 0))   ;; by sonja
          l2-id (:linkId (nth links 1))   ;; by teppo
          l3-id (:linkId (nth links 2))]  ;; by sonja
          
       (fact "Last link is last in list"
          (:url (last links)) = "http://example.org/3")
       
       (fact "Sonja can reorder links"
         (:res (command sonja :info-link-reorder :id application-id :linkIds [l3-id l2-id l1-id])) => true)

       (let [response (query sonja :info-links :id application-id)]
         response => ok?
         (fact "Sonja sees ordered links"
           (map :url (:links response)) => ["http://example.org/3" "http://example.org/2" "http://example.org/1"]))
  
       (let [response (query teppo :info-links :id application-id)]
          response => ok?
          (fact "Statement giver Teppo sees the links and the middle one is editable"
             (map :linkId (:links response)) => [l3-id l2-id l1-id]
             (map :canEdit (:links response)) => [false true false]))
          
       (let [response (command sonja :info-link-delete :id application-id :linkId l2-id)]
         (fact "Sonja can delete all infolinks" 
           response => ok?))
    
       (let [response (command pena :info-link-delete :id application-id :linkId (:linkId l1-id))]
         (fact "Pena can't delete Sonja's infolinks" 
           response =not=> ok?))
  
       (fact "Pena can't update Sonja's infolink"
         (command pena :info-link-upsert :id application-id :text "bad text" :url "http://example.org/1-bad" :linkId l1-id) =not=> ok?)
      
       (fact "Teppo can't update Sonja's infolink"
          (command teppo :info-link-upsert :id application-id :text "bad text 2" :url "http://example.org/1-bad-2" :linkId l1-id) =not=> ok?)
   
       (let [response (command teppo :info-link-delete :id application-id :linkId (:linkId l1-id))]
         (fact "Teppo can't delete Sonja's infolinks either" 
           response =not=> ok?))
    
       (let [response (query sonja :info-links :id application-id)]
         response => ok?
         (fact "Sonja no longer sees the link deleted by her"
           (map :linkId (:links response)) => [l3-id l1-id]))
     
       (let [response (query pena :info-links :id application-id)]
         response => ok?
         (fact "Pena hasn't seen the links yet and sees he cant' modify them"
           (map :isNew (:links response)) => [true true]
           (map :canEdit (:links response)) => [false false]))

       (let [resp (command pena :mark-seen :id application-id :type "info-links")
             response (query pena :info-links :id application-id)]
         response => ok?
         (fact "Pena has seen the links after calling mark-seen" (map :isNew (:links response)) => [false false]))
  
       (let [response (command sonja :info-link-upsert :id application-id :text "new text" :url "http://example.org/one-new" :linkId "one")]
         (fact "Sonja fails to update link with bad id"
            (:ok response) => false))
  
       (let [response (command sonja :info-link-upsert :id application-id :text "new text" :url "http://example.org/1-new" :linkId l1-id)]
         (fact "Sonja can update an infolink"
            response => ok?))
  
       (let [response (command pena :info-link-upsert :id application-id :text "new text bad" :url "http://example.org/1-bad" :linkId l1-id)]
         (fact "Pena fails to update an infolink"
           response =not=> ok?))
  
       (let [response (query pena :info-links :id application-id)]
         response => ok?
         (fact "Pena sees the new updated link by Sonja as a new one at right position"
           (:isNew (nth (:links response) 1)) => true
           (:url (nth (:links response) 1)) => "http://example.org/1-new"
           (:text (nth (:links response) 1)) => "new text")))))

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
