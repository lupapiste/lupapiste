(ns lupapalvelu.organization-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :as local-org-api]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-util :refer :all]
            [sade.util :refer [fn->]]))

(apply-remote-minimal)

(fact* "Organization details query works"
 (let [resp  (query pena "organization-details" :municipality "753" :operation "asuinrakennus" :lang "fi") => ok?]
   (count (:attachmentsForOp resp )) => pos?
   (count (:links resp)) => pos?))

(fact* "The query /organizations"
  (let [resp (query admin :organizations) => ok?]
    (count (:organizations resp)) => pos?))

(fact "Update organization"
  (let [organization         (first (:organizations (query admin :organizations)))
        orig-scope           (first (:scope organization))
        organization-id      (:id organization)
        resp                 (command admin :update-organization
                               :permitType (:permitType orig-scope)
                               :municipality (:municipality orig-scope)
                               :inforequestEnabled (not (:inforequest-enabled orig-scope))
                               :applicationEnabled (not (:new-application-enabled orig-scope))
                               :openInforequestEnabled (not (:open-inforequest orig-scope))
                               :openInforequestEmail "someone@localhost")
        updated-organization (query admin :organization-by-id :organizationId organization-id)
        updated-scope        (local-org-api/resolve-organization-scope updated-organization (:municipality orig-scope) (:permitType orig-scope))]

    (fact "inforequest-enabled" (:inforequest-enabled updated-scope) => (not (:inforequest-enabled orig-scope)))
    (fact "new-application-enabled" (:new-application-enabled updated-scope) => (not (:new-application-enabled orig-scope)))
    (fact "open-inforequest" (:open-inforequest updated-scope) => (not (:open-inforequest orig-scope)))
    (fact "open-inforequest-email" (:open-inforequest-email updated-scope) => "someone@localhost")))

(fact* "Tampere-ya sees (only) YA operations and attachments (LUPA-917, LUPA-1006)"
  (let [resp (query tampere-ya :organization-by-user) => ok?
        tre  (:organization resp)]
    (keys (:operationsAttachments tre)) => [:YA]
    (-> tre :operationsAttachments :YA) => truthy
    (keys (:attachmentTypes resp)) => [:YA]
    (-> resp :attachmentTypes :YA) => truthy))


(facts "Selected operations"

  (fact* "For an organization which has no selected operations, all operations are returned"
    (:selected-operations (query admin "organization-by-id" :organizationId "753-YA")) => nil?
    (let [resp (query sipoo "all-operations-for-organization" :organizationId "753-YA") => ok?
          operations (:operations resp)]
      ;; All the YA operations (and only those) are received here.
      (count operations) => 1
      (-> operations first first) => "yleisten-alueiden-luvat"))

  (fact* "Set selected operations"
    (command pena "set-organization-selected-operations" :operations ["asuinrakennus" "jatkoaika"]) => unauthorized?
    (command sipoo "set-organization-selected-operations" :operations ["asuinrakennus" "jatkoaika"]) => ok?)

  (fact* "Query selected operations"
    (query pena "selected-operations-for-municipality" :municipality "753") => ok?
    (let [resp (query sipoo "selected-operations-for-municipality" :municipality "753")]
      resp => ok?
      ;; Received the two selected R operations plus all the YA operations.
      (:operations resp) => [["Rakentaminen ja purkaminen"
                              [["Uuden rakennuksen rakentaminen"
                                [["Asuinrakennus" "asuinrakennus"]]]
                               ["Jatkoaika" "jatkoaika"]]]
                             ["yleisten-alueiden-luvat"
                              [["sijoituslupa"
                                [["pysyvien-maanalaisten-rakenteiden-sijoittaminen"
                                  [["vesi-ja-viemarijohtojen-sijoittaminen"
                                    "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"]
                                   ["maalampoputkien-sijoittaminen"
                                    "ya-sijoituslupa-maalampoputkien-sijoittaminen"]
                                   ["kaukolampoputkien-sijoittaminen"
                                    "ya-sijoituslupa-kaukolampoputkien-sijoittaminen"]
                                   ["sahko-data-ja-muiden-kaapelien-sijoittaminen"
                                    "ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen"]
                                   ["rakennuksen-tai-sen-osan-sijoittaminen"
                                    "ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen"]]]
                                 ["pysyvien-maanpaallisten-rakenteiden-sijoittaminen"
                                  [["ilmajohtojen-sijoittaminen"
                                    "ya-sijoituslupa-ilmajohtojen-sijoittaminen"]
                                   ["muuntamoiden-sijoittaminen"
                                    "ya-sijoituslupa-muuntamoiden-sijoittaminen"]
                                   ["jatekatoksien-sijoittaminen"
                                    "ya-sijoituslupa-jatekatoksien-sijoittaminen"]
                                   ["leikkipaikan-tai-koiratarhan-sijoittaminen"
                                    "ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen"]
                                   ["rakennuksen-pelastuspaikan-sijoittaminen"
                                    "ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen"]]]
                                 ["muu-sijoituslupa" "ya-sijoituslupa-muu-sijoituslupa"]]]
                               ["katulupa"
                                [["kaivaminen-yleisilla-alueilla"
                                  [["vesi-ja-viemarityot" "ya-katulupa-vesi-ja-viemarityot"]
                                   ["maalampotyot" "ya-katulupa-maalampotyot"]
                                   ["kaukolampotyot" "ya-katulupa-kaukolampotyot"]
                                   ["kaapelityot" "ya-katulupa-kaapelityot"]
                                   ["kiinteiston-johto-kaapeli-ja-putkiliitynnat"
                                    "ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat"]]]
                                 ["liikennealueen-rajaaminen-tyokayttoon"
                                  [["nostotyot" "ya-kayttolupa-nostotyot"]
                                   ["vaihtolavat" "ya-kayttolupa-vaihtolavat"]
                                   ["kattolumien-pudotustyot"
                                    "ya-kayttolupa-kattolumien-pudotustyot"]
                                   ["muu-liikennealuetyo" "ya-kayttolupa-muu-liikennealuetyo"]]]
                                 ["yleisen-alueen-rajaaminen-tyomaakayttoon"
                                  [["talon-julkisivutyot" "ya-kayttolupa-talon-julkisivutyot"]
                                   ["talon-rakennustyot" "ya-kayttolupa-talon-rakennustyot"]
                                   ["muu-tyomaakaytto" "ya-kayttolupa-muu-tyomaakaytto"]]]]]
                               ["kayttolupa"
                                [["tapahtumat" "ya-kayttolupa-tapahtumat"]
                                 ["harrastustoiminnan-jarjestaminen"
                                  "ya-kayttolupa-harrastustoiminnan-jarjestaminen"]
                                 ["mainokset" "ya-kayttolupa-mainostus-ja-viitoitus"]
                                 ["metsastys" "ya-kayttolupa-metsastys"]
                                 ["vesistoluvat" "ya-kayttolupa-vesistoluvat"]
                                 ["terassit" "ya-kayttolupa-terassit"]
                                 ["kioskit" "ya-kayttolupa-kioskit"]
                                 ["muu-kayttolupa" "ya-kayttolupa-muu-kayttolupa"]]]
                               ["jatkoaika" "ya-jatkoaika"]]]]))

  (fact* "Query selected operations"
    (let [id   (create-app-id pena :operation "asuinrakennus" :municipality sonja-muni)
          resp (query pena "addable-operations" :id id) => ok?]
      (:operations resp) => [["Rakentaminen ja purkaminen" [["Uuden rakennuksen rakentaminen" [["Asuinrakennus" "asuinrakennus"]]]]]]))

  (fact* "The query 'organization-by-user' correctly returns the selected operations of the organization"
    (let [resp (query pena "organization-by-user") => unauthorized?
          resp (query sipoo "organization-by-user") => ok?]
      (get-in resp [:organization :selectedOperations]) => {:R ["asuinrakennus" "jatkoaika"]}))

  (fact "An application query correctly returns the 'required fields filling obligatory' info in the organization meta data"
    (let [app-id (create-app-id pena :operation "asuinrakennus" :municipality sonja-muni)
          app    (query-application pena app-id)
          org    (query admin "organization-by-id" :organizationId  (:organization app))]

      (fact "when the 'app-required-fields-filling-obligatory' flag has not yet been set for organization in db"
        (:app-required-fields-filling-obligatory org) => nil
        (-> app :organizationMeta :requiredFieldsFillingObligatory) => false)

      (command sipoo "set-organization-app-required-fields-filling-obligatory" :isObligatory false) => ok?
      (let [app    (query-application pena app-id)
            org    (query admin "organization-by-id" :organizationId  (:organization app))]
        (fact "when the 'app-required-fields-filling-obligatory' flag set to False"
          (:app-required-fields-filling-obligatory org) => false
          (-> app :organizationMeta :requiredFieldsFillingObligatory) => false))

      (command sipoo "set-organization-app-required-fields-filling-obligatory" :isObligatory true) => ok?
      (let [app    (query-application pena app-id)
            org    (query admin "organization-by-id" :organizationId  (:organization app))]
        (fact "when the 'app-required-fields-filling-obligatory' flag set to True"
          (:app-required-fields-filling-obligatory org) => true
          (-> app :organizationMeta :requiredFieldsFillingObligatory) => true)))))
