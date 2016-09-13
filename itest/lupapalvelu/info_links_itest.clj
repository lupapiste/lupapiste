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

(facts "info links"
 
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
     
    (let [response (command teppo :info-link-upsert :id application-id :text "second" :url "http://example.org/2")]
      response => ok?
      (fact "Statement giver Teppo adds another info-link"
         (:linkId response) => string?))

    (let [response (command sonja :info-link-upsert :id application-id :text "third url" :url "http://example.org/3")]
      response => ok?
      (fact "Sonja adds a third info-link"
        (:linkId response) => string?))
       
    (let [links (:links (query sonja :info-links :id application-id))
          l1-id (:linkId (nth links 0))
          l2-id (:linkId (nth links 1))
          l3-id (:linkId (nth links 2))]
          
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
          (fact "Statement giver Teppo sees the links"
             (map :linkId (:links response)) => [l3-id l2-id l1-id]))
          
       (let [response (command sonja :info-link-delete :id application-id :linkId l2-id)]
         (fact "Sonja can delete infolinks" 
           response => ok?))
    
       (let [response (command pena :info-link-delete :id application-id :linkId (:linkId l1-id))]
         (fact "Pena can't delete infolinks" 
           response =not=> ok?))
    
       (let [response (query sonja :info-links :id application-id)]
         response => ok?
         (fact "Sonja no longer sees the deleted link"
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
           (:text (nth (:links response) 1)) => "new text"))

)))  

