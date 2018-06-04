(ns lupapalvelu.pate-legacy-contract-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

-(fact "Create and submit YA application"
   (let [{app-id  :id
          subtype :permitSubtype} (create-and-submit-application pena
                                                                 :operation :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
                                                                 :propertyId sipoo-property-id)]
     (fact "Set Sonja as the application handler"
       (command sonja :upsert-application-handler :id app-id
                :userId sonja-id :roleId sipoo-ya-general-handler-id)
       => ok?)

     (fact "Permit subtype is sijoituslupa"
       subtype => "sijoituslupa")
     (fact "Contracts not visible"
       (query sonja :pate-contract-tab :id app-id)
       => fail?)
     (fact "New verdict draft"
       (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                           :id app-id)]
         (fact "Verdict category is ya"
           (:verdict (open-verdict app-id verdict-id))
           => (contains {:category "ya"}))
         (fact "There is one verdict"
           (count (:verdicts (query sonja :pate-verdicts :id app-id)))
           => 1)
         (fact "Change permit subtype to sijoitussopimus"
           (command sonja :change-permit-sub-type :id app-id
                    :permitSubtype "sijoitussopimus")
           => ok?)
         (fact "Contract tab"
           (query sonja :pate-contract-tab :id app-id) => ok?)
         (fact "No verdicts since the application category has changed"
           (:verdicts (query sonja :pate-verdicts :id app-id))
           => empty?)
         (fact "Due to category change the verdict draft cannot be opened"
           (open-verdict app-id verdict-id)
           => (err :error.invalid-category))))
     (fact "New legacy contract draft"
       (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                           :id app-id)]
         (:verdict (open-verdict app-id verdict-id))
         => (contains {:category "contract"
                       :legacy?  true
                       :data     (contains {:handler "Sonja Sibbo"})})
         (fact "Fill contract"
           (fill-verdict app-id verdict-id
                         :kuntalupatunnus "123456"
                         :verdict-date (timestamp "4.6.2018")
                         :contract-text "This is a binding contract.")
           (fact "Add condition"
             (let [condition-id (-> (edit-legacy-verdict app-id verdict-id
                                                         :add-condition true)
                                    :changes flatten second)]
               (edit-legacy-verdict app-id verdict-id
                                    [:conditions condition-id :name] "Contract condition")
               => (contains {:filled true})))
           (add-review app-id verdict-id "Contract review" "valvonta"))
         (fact "Publish contract"
           (command sonja :publish-legacy-verdict :id app-id
                    :verdict-id verdict-id) => ok?)
         (fact "Contract is published"
           (:published (:verdict (open-verdict app-id verdict-id)))
           => pos?)
         (facts "Application updated"
           (let [{:keys [tasks attachments state]} (query-application sonja app-id)
                 source                            {:type "verdict"
                                                    :id   verdict-id}]
             (fact "State is agreementPrepared"
               state => "agreementPrepared")
             (fact "Two tasks"
               tasks => (just [(contains {:source      source
                                          :taskname    "Contract condition"
                                          :schema-info (contains {:name "task-lupamaarays"})})
                               (contains {:source      source
                                          :taskname    "Contract review"
                                          :schema-info (contains {:name "task-katselmus-ya"})})]
                              :in-any-order))
             (fact "Verdict attachment"
               (let [attachment (last attachments)]
                 attachment => (contains {:source           source
                                          :applicationState "agreementPrepared"
                                          :contents         "Sopimus"
                                          :type             {:type-group "muut"
                                                             :type-id    "paatos"}})
                 (fact "Filename"
                   (-> attachment
                       :latestVersion
                       :filename)
                   => (re-pattern (str app-id " Sopimus .+.pdf")))))))))))
