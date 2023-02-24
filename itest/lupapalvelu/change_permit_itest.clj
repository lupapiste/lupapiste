(ns lupapalvelu.change-permit-itest
  (:require [clojure.set :refer [intersection]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(facts "Change permit"
  (let [application       (create-and-submit-application
                            teppo
                            :operation "kerrostalo-rivitalo"
                            :propertyId sipoo-property-id
                            :address "Paatoskuja 12")
        application-id    (:id application)
        [doc-id1 doc-id2] (map :id (:documents application))]

    (fact "Request statement from Pena"
      (command sonja :request-for-statement :id application-id
               :functionCode ""
               :selectedPersons [{:email "pena@example.com"
                                  :name  "Pena"
                                  :text  "Pena"}]) => ok?)
    (fact "Approve the first document"
      (command sonja :approve-doc :id application-id
               :doc doc-id1
               :collection "documents"
               :path "") => ok?)
    (fact "Reject the second document"
      (command sonja :reject-doc :id application-id
               :doc doc-id2
               :collection "documents"
               :path "") => ok?)

    (generate-documents! application sonja)

    (fact "No change permit premises note"
      (query teppo :change-permit-premises-note :id application-id) => fail?
      (query sonja :change-permit-premises-note :id application-id) => fail?)

    (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
    (command sonja :approve-application :id application-id :lang "fi") => ok?

    (fact "can not be created based on current R application before verdict has been given"
      (command sonja :create-change-permit :id application-id) => (partial expected-failure? "error.command-illegal-state"))

    (fact "Give verdict to original application"
      (give-legacy-verdict sonja application-id))

    (let [application (query-application sonja application-id)]
      (:state application) => "verdictGiven")

    sonja => (allowed? :create-change-permit :id application-id)

    (let [resp            (command teppo :create-change-permit :id application-id)
          {change-id :id} resp]
      (fact "was created" resp => ok?)

      (let [{change-app-id :id
             :as           change-app} (query-application sonja change-id)
            old-doc                    (domain/get-document-by-name application "uusiRakennus")
            change-doc                 (domain/get-document-by-name change-app "uusiRakennus")
            change-op                  (:primaryOperation change-app)]
        (fact "Change permit premises note"
          (query teppo :change-permit-premises-note :id change-app-id) => ok?
          (query sonja :change-permit-premises-note :id change-app-id) => fail?)
       (fact "Operation id is not the same in new application"
         (:id change-op) =not=> (-> application :primaryOperation :id))
       (fact "Document has new operation id"
         (-> change-doc :schema-info :op :id) =not=> (-> old-doc :schema-info :op :id)
         (-> change-doc :schema-info :op :id) => (:id change-op))

       (fact "All documents have new ids"
         (let [old-ids (set (map :id (:documents application)))
               new-ids (set (map :id (:documents change-app)))]
           (intersection new-ids old-ids) => empty?))
       (fact "No statement givers"
         (map :role (:auth change-app)) => ["writer"])
       (fact "No approval information in documents"
         (->> (:documents change-app)
              (map :meta)
              (map :_approved)
              (filter identity)) => empty?)
       (fact "Fetch verdict for the change permit"
         (command teppo :submit-application :id change-app-id) => ok?
         (command sonja :check-for-verdict :id change-app-id) => ok?)
       (fact "No more change permit premises note"
         (query sonja :change-permit-premises-note :id (:id change-app)) => fail?)))))

(facts "Change permit can only be applied for an R type of application."
  (let [property-id            sipoo-property-id
        application            (create-and-submit-application pena
                                 :propertyId property-id
                                 :address "Paatoskuja 13"
                                 :operation "ya-katulupa-vesi-ja-viemarityot")
        application-id         (:id application)]

    application-id => string?

    (generate-documents! application pena)
    (command sonja :approve-application :id application-id :lang "fi") => ok?
    (give-legacy-verdict sonja application-id)
    (let [application (query-application sonja application-id) => truthy]
      (:state application) => "verdictGiven")
    (command pena :create-change-permit :id application-id) => (partial expected-failure? "error.invalid-permit-type")))

(facts "Change permit note vs. no premises"
  (let [{base-id :id} (create-and-submit-application teppo
                                                     :operation "aita"
                                                     :propertyId sipoo-property-id)]
    (fact "No change permit premises note"
      (query teppo :change-permit-premises-note :id base-id) => fail?
      (query sonja :change-permit-premises-note :id base-id) => fail?)
    (fact "Fetch verdict"
      (command sonja :check-for-verdict :id base-id) => ok?)
    (fact "Create change permit"
      (let [{change-id :id} (command teppo :create-change-permit :id base-id)]
        (fact "Submit the change permit"
          (command teppo :submit-application :id change-id) => ok?)
        (fact "Still no change permit premises note"
          (query teppo :change-permit-premises-note :id change-id) => fail?
          (query sonja :change-permit-premises-note :id change-id) => fail?)))))
