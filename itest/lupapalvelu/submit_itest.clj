(ns lupapalvelu.submit-itest
  "Testing submit-related validation and related checks."
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(facts "Submit-related validation"
       (let [{application-id :id
              documents      :documents
              :as            application} (create-and-open-application pena
                                                                       :operation "ya-katulupa-vesi-ja-viemarityot"
                                                                       :propertyId sipoo-property-id)
             hakija-doc-id (->> application
                                :documents
                                (util/find-first #(= "hakija-ya"
                                                     (-> % :schema-info :name)))
                                :id)]
         (fact "Document id found"  hakija-doc-id => truthy)

         (fact "Hakija is a person"
               (command pena :update-doc
                        :id application-id
                        :doc hakija-doc-id
                        :collection "documents"
                        :updates [["_selected" "henkilo"]]))
         (fact "Generate documents"
               (generate-documents application pena) => nil
               (provided (tools/dummy-vrk-address) => "Chaoyanggongyuanlu 88"))

         (fact "Update-doc results include :ignore errors"
               (let [{errors :results} (command pena :update-doc
                                                :id application-id
                                                :doc hakija-doc-id
                                                :collection "documents"
                                                :updates [["_selected" "henkilo"]
                                                          ["yritys.liikeJaYhteisoTunnus" "bad-y"]])]
                 (count errors) => 1
                 (first errors) => (contains {:ignore true
                                              :path   ["yritys" "liikeJaYhteisoTunnus"]})))

         (defn application-errors
           "All application validation errors in one list."
           [application]
           (->> application :documents (map :validationErrors) flatten (filter identity)))

         (fact "Application query results do not include :ignore errors"
               (let [{app :application} (query pena :application :id application-id) => ok?]
                 (application-errors app) => empty?))
         (fact "Fetch-validation-errors results do not include :ignore errors"
               (let [{errors :results} (query pena :fetch-validation-errors :id application-id) => ok?]
                 (flatten errors) => empty?))
         (fact "Application is submittable"
               (query pena :application-submittable :id application-id) => ok?)
         (fact "Make hakija-r company"
               (let [{errors :results} (command pena :update-doc
                                                :id application-id
                                                :doc hakija-doc-id
                                                :collection "documents"
                                                :updates [["_selected" "yritys"]])]
                 (count errors) => 1
                 (first errors) => (contains {:path ["yritys" "liikeJaYhteisoTunnus"]})
                 (first errors) =not=> (contains {:ignore true})))
         (fact "Application query now contains one validation error"
               (let [{app :application} (query pena :application :id application-id) => ok?
                     [error] (application-errors app)]
                 error => (contains {:path ["yritys" "liikeJaYhteisoTunnus"]})))
         (fact "Fetch-validation-errors likewise"
               (let [{errors :results} (query pena :fetch-validation-errors :id application-id) => ok?
                     [error] (flatten errors)]
                 error => (contains {:path ["yritys" "liikeJaYhteisoTunnus"]})))
         (fact "Application is still submittable"
               (query pena :application-submittable :id application-id) => ok?)
         (fact "Organization requires fully-formed applications"
               (command sipoo-ya :set-organization-app-required-fields-filling-obligatory :enabled true))
         (fact "Application is no longer submittable"
               (let [{errors :errors} (query pena :application-submittable :id application-id) => fail?]
                 (first errors) => (contains {:text "application.requiredDataDesc"})))
         (fact "Application submit fails"
               (command pena :submit-application :id application-id => fail?))
         (fact "After fixing bad-y there are no longer any validation errors"
               (command pena :update-doc
                        :id application-id
                        :doc hakija-doc-id
                        :collection "documents"
                        :updates [["yritys.liikeJaYhteisoTunnus" "1234567-1"]])
               => {:ok true :results []})
         (fact "Application query results do not include any errors"
               (let [{app :application} (query pena :application :id application-id) => ok?]
                 (application-errors app) => empty?))
         (fact "Fetch-validation-errors results do not include any errors"
               (let [{errors :results} (query pena :fetch-validation-errors :id application-id) => ok?]
                 (flatten errors) => empty?))
         (fact "Application is still not submittable"
               (let [{errors :errors} (query pena :application-submittable :id application-id) => fail?]
                 (first errors) => (contains {:text "application.requiredDataDesc"})))
         (fact "Marking the mandatory attachment not needed makes the application finally submittable"
               (command pena :set-attachment-not-needed :id application-id :notNeeded true
                        :attachmentId (-> application :attachments first :id)) => ok?
               (query pena :application-submittable :id application-id) => ok?)
         (fact "Submit application successfully"
               (command pena :submit-application :id application-id) => ok?)))
