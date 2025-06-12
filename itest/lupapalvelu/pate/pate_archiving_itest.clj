(ns lupapalvelu.pate.pate-archiving-itest
  (:require [lupapalvelu.archive.archiving]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer [no-errors?]]
            [lupapiste-commons.archive-metadata-schema :as ams]
            [lupapiste-commons.schema-utils :as schema-utils]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :as core]
            [schema.core :as schema])
  (:import [java.util Date]))

(testable-privates lupapalvelu.archive.archiving generate-archive-metadata)

(defn- add-missing-coordinates [{:keys [location] :as metadata}]
 (assoc metadata :location-etrs-tm35fin location))

(defn- change-applicant-to-list
  [{:keys [applicant] :as metadata}]
    (-> (assoc metadata :applicants [applicant])
        (dissoc :applicant)))

(defn- add-mocked-data-from-toj [metadata]
  (assoc metadata :henkilotiedot :ei-sisalla
                  :julkisuusluokka :julkinen
                  :sailytysaika {:arkistointi :ei
                                 :perustelu "<Arvo puuttuu>"}
                  :tosFunction {:code "10030001" :name "Rakennuslupamenettely"}))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Archiving application with PATE verdict"
    (let [app-id (create-app-id pena
                                :propertyId sipoo-property-id
                                :operation  :kerrostalo-rivitalo)]

      (fact "Enable permanent archive"
        (command admin :set-organization-boolean-attribute :attribute "permanent-archive-enabled"
                 :enabled true :organizationId "753-R") => ok?)

      (fact "Submit, approve and publish verdict "
        (command pena :submit-application :id app-id) => ok?
        (command sonja :set-tos-function-for-application :id app-id :functionCode "10 03 00 01")
        (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
        (command sonja :approve-application :id app-id :lang "fi") => ok?

        (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                :id app-id
                                                :template-id (-> pate-fixture/verdict-templates-r
                                                                 first
                                                                 :id))]

          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:automatic-verdict-dates] :value true) => no-errors?
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:verdict-date] :value (core/now)) => no-errors?
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:verdict-code] :value "hyvaksytty") => no-errors?
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:giver-first-name] :value "Pena") => no-errors?
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:giver-last-name] :value "Päättäjä") => no-errors?
          (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?))

      (facts "The one attachment with a document can be archived"
        (let [[attachment :as attachments] (->> (query sonja :attachments :id app-id)
                                                :attachments
                                                (filter :latestVersion))]
          (count attachments) => 3 ; includes bulletin PDF & app-at-time-of-verdict PDF
          (:can-be-archived? attachment) => true
          (->> attachment :id (query sonja :attachment :id app-id :attachmentId)
               :attachment :can-be-archived?) => true))

      (fact "Archiving metadata validates against schema"
        (let [application      (query-application sonja app-id)
              attachment       (first (filter #(= "paatos" (get-in % [:type :type-id])) (:attachments application)))
              user             {:username "sonja" :firstName "Sonja" :lastName "Sipoo"}
              archive-metadata (generate-archive-metadata application user :metadata {} attachment)
              metadata         (->> (assoc archive-metadata :arkistointipvm (Date.) :tila :arkistoitu)
                                    (add-missing-coordinates)
                                    (add-mocked-data-from-toj)
                                    (change-applicant-to-list)
                                    schema-utils/remove-blank-keys
                                    (schema-utils/coerce-metadata-to-schema ams/full-document-metadata))]
          (schema/check ams/full-document-metadata metadata) => nil)))))
