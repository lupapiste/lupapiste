(ns lupapalvelu.pate-contract-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(fact "Create and submit YA application"
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
     (fact "New legacy verdict draft"
       (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                           :id app-id)]
         (let [{:keys [verdict]} (open-verdict sonja app-id verdict-id)]
           (fact "Verdict category is ya"
             (:category verdict) => "ya")
           (fact "Verdict giver is the handler"
             (:data verdict) => (contains {:handler "Sonja Sibbo"})))
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
           (open-verdict sonja app-id verdict-id)
           => (err :error.invalid-category))))
     (fact "New legacy contract draft"
       (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                           :id app-id)]
         (:verdict (open-verdict sonja app-id verdict-id))
         => (contains {:category "contract"
                       :legacy?  true
                       :data     (contains {:handler "Sonja Sibbo"})})
         (fact "Fill contract"
           (fill-verdict sonja app-id verdict-id
                         :kuntalupatunnus "123456"
                         :handler "Random Authority"
                         :verdict-date (timestamp "4.6.2018")
                         :contract-text "This is a binding contract.")
           (fact "Add condition"
             (let [condition-id (-> (edit-verdict sonja app-id verdict-id
                                                  :add-condition true)
                                    :changes flatten second)]
               (edit-verdict sonja app-id verdict-id
                             [:conditions condition-id :name] "Contract condition")
               => (contains {:filled true})))
           (add-legacy-review sonja app-id verdict-id "Contract review" "valvonta"))
         (fact "Publish contract"
           (command sonja :publish-legacy-verdict :id app-id
                    :verdict-id verdict-id) => ok?)
         (fact "Contract is published"
           (:published (:verdict (open-verdict sonja app-id verdict-id)))
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
                 attachment => (contains {:source           (update source :type str "s")
                                          :applicationState "agreementPrepared"
                                          :contents         "Sopimus"
                                          :type             {:type-group "muut"
                                                             :type-id    "paatos"}})
                 (fact "Filename"
                   (-> attachment
                       :latestVersion
                       :filename)
                   => (re-pattern (str app-id " Sopimus .+.pdf")))))))
         (fact "The published contract has one signature"
           (let [{:keys [verdict]} (open-verdict sonja app-id verdict-id)]
             (-> verdict :data :signatures vals)
             => (just [(contains {:name "Random Authority"
                                  :date (-> verdict :data :verdict-date)})])))

         (fact "Sonja signs the contract: bad password"
           (command sonja :sign-pate-contract :id app-id
                    :verdict-id verdict-id
                    :password "bad") => (err :error.password))

         (fact "Sonja signs the contract: correct password"
           (command sonja :sign-pate-contract :id app-id
                    :verdict-id verdict-id
                    :password "sonja") => ok?)

         (let [{:keys [attachments state]} (query-application sonja app-id)
               attachment                  (last attachments)]
           (fact "Application state is agreementSigned"
             state => "agreementSigned")
           (fact "There are two verdict attachment versions"
             ;; Attachment application state is still
             ;; agreementPrepared, since it does not change
             ;; automatically by version.
             attachment => (contains {:applicationState "agreementPrepared"
                                      :source           {:type "verdicts"
                                                         :id   verdict-id}})

             (count (:versions attachment)) => 2))
         (fact "The contract has two signatures"
           (let [{:keys [verdict]} (open-verdict sonja app-id verdict-id)]
             (-> verdict :data :signatures vals)
             => (just [{:name "Random Authority"
                        :date (-> verdict :data :verdict-date)}
                       {:name    "Sonja Sibbo"
                        :user-id sonja-id
                        :date    (:modified verdict)}]
                      :in-any-order)))
         (fact "Sonja cannot resign"
           (command sonja :sign-pate-contract :id app-id
                    :verdict-id verdict-id
                    :password "sonja")
           => (err :error.already-signed))
         (invite-company-and-accept-invitation sonja app-id "esimerkki" erkki)

         (fact "Erkki adds Pena to company"
           (command erkki :company-invite-user
                    :email "pena@example.com"
                    :admin false
                    :firstName "Pena"
                    :lastName "Panaani"
                    :submit false) => ok?)
         (http-token-call (token-from-email "pena@example.com"))
         (fact "Pena can now sign the contract"
           (command pena :sign-pate-contract :id app-id
                    :verdict-id verdict-id
                    :password "pena") => ok?)
         (fact "Erkki can no longer sign"
           (command erkki :sign-pate-contract :id app-id
                    :verdict-id verdict-id
                    :password "esimerkki")
           => (err :error.already-signed))
         (fact "There are now three signatures"
           (let [{:keys [verdict]} (open-verdict sonja app-id verdict-id)]
             (-> verdict :data :signatures vals)
             => (just [{:name "Random Authority"
                        :date (-> verdict :data :verdict-date)}
                       (just {:name    "Sonja Sibbo"
                              :user-id sonja-id
                              :date    pos?})
                       {:name       "Pena Panaani, Esimerkki Oy"
                        :user-id    pena-id
                        :company-id "esimerkki"
                        :date       (:modified verdict)}]
                      :in-any-order)))))))

(facts "Modern contracts"
  (toggle-pate "753-YA" true)

  (facts "Create new YA application"
    (let [{app-id :id} (create-and-submit-application teppo
                                                      :operation :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
                                                      :propertyId sipoo-property-id)]
      (fact "Change permit subtype to sijoitussopimus"
        (command sonja :change-permit-sub-type :id app-id
                 :permitSubtype "sijoitussopimus")
        => ok?)

      (fact "Set Sonja as the application handler"
       (command sonja :upsert-application-handler :id app-id
                :userId sonja-id :roleId sipoo-ya-general-handler-id)
       => ok?)

      (fact "Legacy contracts no longer supported"
        (command sonja :new-legacy-verdict-draft :id app-id)
        => fail?)
      (fact "Contract settings consist only of reviews"
        (let [review-id (add-repeating-setting sipoo-ya :contract :reviews :add-review
                                               :fi "Foo suomeksi"
                                               :sv "Foo p\u00e5 svenska"
                                               :en "Foo in English"
                                               :type "aloituskatselmus")]
          (query sipoo-ya :verdict-template-settings :category "contract")
          => (contains {:filled true})
          (fact "Create contract template"
            (let [{template-id :id} (init-verdict-template sipoo-ya :contract)]
              (fact "Fill template"
                (set-template-draft-values sipoo-ya
                                           template-id
                                           :language "fi"
                                           :contract-text "This is a test contract."
                                           [:reviews review-id :included] true
                                           [:reviews review-id :selected] true))
              (fact "Add condition"
                (add-template-condition sipoo-ya template-id
                                        "Some random condition."))
              (fact "Publish template"
                (publish-verdict-template sipoo-ya template-id) => ok?)
              (fact "New contract"
                (let [{:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id
                                                    :template-id template-id)
                      {:keys [verdict references
                              filled]}     (open-verdict sonja app-id verdict-id)]
                  verdict => (contains {:category "contract"
                                        :data     (contains {:language         "fi"
                                                             :contract-text    "This is a test contract."
                                                             :handler          "Sonja Sibbo"
                                                             :reviews-included true})})
                  filled => false
                  (fact "Review"
                    (let [review-id (-> references :reviews first :id)]
                      (-> references :reviews last)
                      => {:fi   "Foo suomeksi"
                          :sv   "Foo p\u00e5 svenska"
                          :en   "Foo in English"
                          :type "aloituskatselmus"
                          :id   review-id}
                      (-> verdict :data :reviews) => [review-id]))
                  (fact "Condition"
                    (-> verdict :data :conditions vals)
                    => (just [{:condition "Some random condition."}]))
                  (fact "Fill contract"
                    (fill-verdict sonja app-id verdict-id
                                  :verdict-date (timestamp "11.6.2018")))
                  (fact "Application attachments cannot be selected for contract"
                    (first (:errors (command sonja :edit-pate-verdict :id app-id
                                             :verdict-id verdict-id
                                             :path [:attachments]
                                             :value ["foobar"])))
                    => [["attachments"] "error.read-only"])
                  (fact "Publishing contract is possible in submitted state"
                    (command sonja :publish-pate-verdict :id app-id
                             :verdict-id verdict-id)
                    => ok?)
                  (let [{:keys [tasks attachments]} (query-application sonja app-id)]
                    (fact "Two tasks have been created"
                      tasks => (contains [(contains {:taskname    "Foo suomeksi"
                                                     :source      {:type "verdict"
                                                                   :id   verdict-id}
                                                     :schema-info (contains {:name "task-katselmus-ya"})})
                                          (contains {:taskname    "Some random condition."
                                                     :source      {:type "verdict"
                                                                   :id   verdict-id}
                                                     :schema-info (contains {:name "task-lupamaarays"})})]
                                         :in-any-order
                                         :gaps-ok))
                    (fact "One attachment has been created"
                      attachments => (contains [(contains {:source   {:type "verdicts"
                                                                      :id   verdict-id}
                                                           :type     {:type-group "muut"
                                                                      :type-id    "paatos"}
                                                           :contents "Sopimus"})])
                      (fact "Filename"
                        (-> attachments
                            last
                            :latestVersion
                            :filename)
                        => (re-pattern (str app-id " Sopimus .+.pdf"))))))))))))))
