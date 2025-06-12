(ns lupapalvelu.building-construction-started-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

;;
;; TODO: Fix this
;;
#_(fact* "Construction can be started even if the municipality does not have sftp account"
   (let [application-id    (create-app-id sonja :municipality "998" :address "Rakentamisen aloituspolku 2")
         _ (command sonja :submit-application :id application-id) => ok?
         _ (command sonja :check-for-verdict :id application-id) => ok?
         application     (query-application sonja application-id)
         building-1     (-> application :buildings first) => truthy

         resp (command sonja :inform-building-construction-started
                :id application-id
                :buildingIndex (:index building-1)
                :startedDate "1.1.2015"
                :lang "fi")]

     (fact "Meta: organization has no ftpUser"
       (let [org (query admin :organization-by-id :organizationId (:organization application))
             krysp-conf (get-in org [:krysp (keyword (lupapalvelu.permit/permit-type application))])]
         org => truthy
         krysp-conf => truthy
         (:ftpUser krysp-conf) => nil))

     (fact "Application state before :inform-building-construction-started was verdictGiven"
       (:state application) => "verdictGiven")

     (fact "inform-building-construction-started command succeeded"
       resp => ok?)

     (fact "XML file was not actually sent"
       (:integrationAvailable resp) => false)

     (fact "State has changed to construction started"
       (:state (query-application sonja application-id)) => "constructionStarted")))
