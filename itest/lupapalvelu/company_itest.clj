(ns lupapalvelu.company-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [sade.http :as http]))

(facts* "Company is added to application"

  (let [application-id (create-app-id mikko :municipality sonja-muni :address "Kustukatu 13")
        auth (:auth (query-application mikko application-id))
        params {:follow-redirects false
                :throw-exceptions false}]
    (count auth) => 1

    (fact "Applicant invites company"
      (command mikko :company-invite :id application-id :company-id "solita") => ok?)

    (fact "Company is only invited to the application"
      (let [auth (:auth (query-application mikko application-id))
            auth-ids (flatten (map (juxt :id) auth))]
        (count auth) => 2
        (some #(= "solita" %) auth-ids) => nil?))

    (fact "Invitation is accepted"
      (let [email (last-email)
            auth (:auth (query-application mikko application-id))
            [uri token] (re-find #"http.+/app/fi/welcome#!/accept-company-invitation/([A-Za-z0-9-]+)" (:plain (:body email)))
            resp (http/post (str (server-address) "/api/token/" token) params)]
        (:status resp) => 200))

    (fact "Company is fully authored to the application after accept"
      (let [auth (:auth (query-application mikko application-id))
            auth-ids (flatten (map (juxt :id) auth))]
        (some #(= "solita" %) auth-ids) => true))))

