(ns lupapalvelu.change-permit-itest
  (:require [clojure.set :refer [intersection]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer [give-generic-legacy-verdict give-legacy-verdict]]
            [lupapalvelu.publish-dates :as pd]
            [midje.sweet :refer :all]
            [sade.date :as date]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
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
        (give-legacy-verdict sonja application-id :R true))

      (let [application (query-application sonja application-id)]
        (:state application) => "verdictGiven")

      (allowed? sonja :create-change-permit :id application-id) => true

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
          (fact "Source application info"
            (-> (get-by-id :applications change-app-id)
                :body :data :_source-application)
            => {:id application-id :kind "change"})
          (fact "Give verdict to the change permit"
            (command teppo :submit-application :id change-app-id) => ok?
            (let [verdict-id (give-generic-legacy-verdict
                               sonja
                               change-app-id
                               {:fields     {:kuntalupatunnus "888-10-12"
                                             :verdict-code    "1" ;; Granted
                                             :verdict-text    "Lorem ipsum"
                                             :handler         "Decider"
                                             :giver           "lautakunta"
                                             :giver-name      "Board"
                                             :k-verdict-date  (date/timestamp "21.5.2018")
                                             :k-lainvoimainen (date/timestamp "30.5.2018")
                                             :k-kuulutus      (date/timestamp "31.6.2018")}
                                :attachment {:state "ready"}})]
              (fact "No more change permit premises note"
                (query sonja :change-permit-premises-note :id (:id change-app)) => fail?)
              (fact "Change permit state is ready and the verdict can be deleted"
                (:state (query-application sonja change-app-id)) => "ready"
                (command sonja :delete-pate-verdict :id change-app-id
                         :verdict-id verdict-id)
                => ok?
                (let [{:keys [pate-verdicts state]} (query-application sonja change-app-id)]
                  state => "submitted"
                  pate-verdicts => empty?))))))))

  (facts "Change permit can only be applied for an R type of application."
    (let [property-id    sipoo-property-id
          application    (create-and-submit-application pena
                                                        :propertyId property-id
                                                        :address "Paatoskuja 13"
                                                        :operation "ya-katulupa-vesi-ja-viemarityot")
          application-id (:id application)]

      application-id => string?

      (generate-documents! application pena)
      (command sonja :approve-application :id application-id :lang "fi") => ok?
      (give-legacy-verdict sonja application-id :YA false) ;; YA -> no kuulutus or giver
      (let [application (query-application sonja application-id)]
        application => truthy
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

  (facts "Ryhti 2025 link permit flag"
    (let [base-app              (create-and-submit-application teppo :operation "aita" :propertyId sipoo-property-id)
          base-app-id           (:id base-app)
          set-submitted         (fn [app-id date-fn]
                                  (->> (date-fn pd/ryhti-2025-publish-date :day)
                                       date/timestamp
                                       (set-application-submitted app-id)))
          _                     (generate-documents! base-app sonja)
          _                     (give-legacy-verdict sonja base-app-id :R true)
          _                     (set-submitted base-app-id date/minus)
          {pre-2025-cp-id :id}  (command sonja :create-change-permit :id base-app-id)
          _                     (set-submitted base-app-id date/plus)
          {post-2025-cp-id :id} (command sonja :create-change-permit :id base-app-id)]
      (fact "Change permit created for pre-2025 app has flag"
        (:pre-ryhti-2025-link-permit (query-application sonja pre-2025-cp-id))
        => truthy)
      (fact "Change permit created for post-2025 app has no flag"
        (:pre-ryhti-2025-link-permit (query-application sonja post-2025-cp-id))
        => nil)
      (fact "Flag inherited from link-permit to link-permit"
        (let [_            (set-submitted pre-2025-cp-id date/plus) ; just to be sure
              {enc-id :id} (command sonja :create-encumbrance-permit :id pre-2025-cp-id)]
          (:pre-ryhti-2025-link-permit (query-application sonja enc-id))
          => truthy)))))
