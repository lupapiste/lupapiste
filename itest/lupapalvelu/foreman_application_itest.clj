(ns lupapalvelu.foreman-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Foreman application creation"
  (apply-remote-minimal)

  (let [apikey mikko

        {application-id :id} (create-app apikey
                               :operation "kerrostalo-rivitalo") => truthy

        {foreman-application-id :id} (command apikey :create-foreman-application :id application-id) => truthy

        foreman-application (query-application apikey foreman-application-id)
        foreman-link-permit-data (first (foreman-application :linkPermitData))

        application (query-application apikey application-id)
        link-to-application (first (application :appsLinkingToUs))]

    (fact "Foreman application contains link to application"
      (:id foreman-link-permit-data) => application-id)

    (fact "Original application contains link to foreman application"
      (:id link-to-application) => foreman-application-id)))