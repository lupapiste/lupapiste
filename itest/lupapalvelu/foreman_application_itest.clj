(ns lupapalvelu.foreman-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

; TODO test that document data is copied to foreman application

(facts* "Foreman application"
  (apply-remote-minimal)

  (let [apikey mikko

        {application-id :id} (create-app apikey
                               :operation "kerrostalo-rivitalo") => truthy

        {foreman-application-id :id} (command apikey :create-foreman-application :id application-id :taskId "" :foremanRole "") => truthy

        foreman-application (query-application apikey foreman-application-id)
        foreman-link-permit-data (first (foreman-application :linkPermitData))

        foreman-doc (lupapalvelu.domain/get-document-by-name foreman-application "tyonjohtaja-v2")

        application (query-application apikey application-id)
        link-to-application (first (application :appsLinkingToUs))

        foreman-applications (query apikey :foreman-applications :id application-id) => truthy]

    (fact "Update ilmoitusHakemusValitsin to 'ilmoitus'"
      (command apikey :update-doc :id foreman-application-id :doc (:id foreman-doc) :updates [["ilmoitusHakemusValitsin" "ilmoitus"]]) => ok?)

    (fact "Foreman application contains link to application"
      (:id foreman-link-permit-data) => application-id)

    (fact "Original application contains link to foreman application"
      (:id link-to-application) => foreman-application-id)

    (fact "All linked Foreman applications are returned in query"
      (let [applications (:applications foreman-applications)]
        (count applications) => 1
        (:id (first applications)) => foreman-application-id))))