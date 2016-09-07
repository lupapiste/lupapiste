(ns lupapalvelu.info-links-itest
  (:require [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.info-links :as info-links]
            [sade.util :as util]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(apply-remote-minimal)

(facts "info links"
   
    (let [{application-id :id :as response} 
            (create-app pena :propertyId tampere-property-id :operation "kerrostalo-rivitalo")
          application (query-application pena application-id)]
         
       response => ok?
     
       (let [response (query pena :info-links :id application-id)]
          response => ok?
          (fact "initially pena sees no links" (:links response) => []))

       (let [response (command pena :info-link-upsert :id application-id :text "link text" :url "http://www.host.tla")]
          response => ok?
          (fact "Pena can add an info-link"
              (:linkId response) => 1))

       (let [response (query pena :info-links :id application-id)]
          response => ok?
          (fact "Pena sees the infolink"
              (:text (first (:links response))) => "link text"
              (:url  (first (:links response))) => "http://www.host.tla"))
        
       (let [response (command pena :info-link-upsert :id application-id :text "second" :url "http://second.org")]
          response => ok?
          (fact "Pena adds another info-link"
              (:linkId response) => 2))

       (let [response (command pena :info-link-upsert :id application-id :text "third url" :url "http://third.org")]
          response => ok?
          (fact "Pena a third info-link"
              (:linkId response) => 3))
           
       (let [response (command pena :info-link-reorder :id application-id :linkIds ["3" "2" "1"])]
          response => ok?
          (fact "Pena reorders the links"
              (:res response) => true))
 
       (let [response (query pena :info-links :id application-id)]
          response => ok?
          (fact "Pena sees ordered links"
              (map :linkId (:links response)) => [3 2 1]))
              
       (let [response (command pena :info-link-reorder :id application-id :linkIds ["1" "1" "2" "5"])]
          response => ok?
          (fact "Pena reorders the links badly"
              (:res response) => false))
 
       (let [response (command pena :info-link-delete :id application-id :linkId "2")]
          (fact "Pena can delete infolinks" 
             response => ok?))
       
       (let [response (query pena :info-links :id application-id)]
          response => ok?
          (fact "Pena no longer sees the deleted link"
              (map :linkId (:links response)) => [3 1]))
          
       ;; näkyvyys muille ja luetun trackays puuttuu
))
