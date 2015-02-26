(ns lupapalvelu.xml.asianhallinta.asianhallinta-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.organization :as organization]))

(apply-remote-minimal)

(fl/facts* "Asianhallinta itest"
  (facts "UusiAsia from poikkeamis application"
    (let [app-id (create-app-id
                    pena
                    :municipality velho-muni
                    :operation "poikkeamis"
                    :propertyId "29703401070010"
                    :y 6965051.2333374 :x 535179.5
                    :address "Suusaarenkierto 44") => truthy
          application (query-application pena app-id) => truthy
          organization (organization/resolve-organization velho-muni (:permitType application)) => truthy
          scope  (organization/resolve-organization-scope velho-muni (:permitType application) organization) => truthy
          config (:caseManagement scope) => truthy]
      (generate-documents application pena)
      (upload-attachment-to-all-placeholders pena application)

      (command pena :submit-application :id app-id) => ok?

      (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

      (let [updated-application (query-application velho app-id)])
      ))


  (fact "Can't create asianhallinta with non-asianhallinta operation"))
