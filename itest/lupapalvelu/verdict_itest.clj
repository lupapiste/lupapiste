(ns lupapalvelu.verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [sade.util :as util]))

(fact* "Give verdict"
  (last-email) ; Inbox zero

  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id :address "Paatoskuja 9")
        application-id (:id application)
        email           (last-email) => truthy]
    (:state application) => "submitted"
    (:to email) => (contains (email-for-key pena))
    (:subject email) => "Lupapiste.fi: Paatoskuja 9 - hakemuksen tila muuttunut"
    (get-in email [:body :plain]) => (contains "Hakemus j\u00e4tetty")
    email => (partial contains-application-link? application-id "applicant")

    (let [new-verdict-resp (command sonja :new-verdict-draft :id application-id) => ok?
          verdict-id (:verdictId new-verdict-resp) => truthy
          resp        (command sonja :save-verdict-draft :id application-id :verdictId verdict-id :backendId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124 :text "" :agreement false :section "") => ok?
          application (query-application sonja application-id)
          verdict     (first (:verdicts application))
          paatos      (first (:paatokset verdict))
          poytakirja  (first (:poytakirjat paatos))]
      (count (:verdicts application)) => 1
      (count (:paatokset verdict)) => 1
      (count (:poytakirjat paatos)) => 1

      (:kuntalupatunnus verdict) => "aaa"
      (:status poytakirja) => 42
      (:paatoksentekija poytakirja) => "Paatoksen antaja"
      (get-in paatos [:paivamaarat :anto]) => 123
      (get-in paatos [:paivamaarat :lainvoimainen]) => 124

      (fact "Comment verdict"
        (command sonja :add-comment :id application-id :text "hello" :to nil :target {:type "verdict" :id verdict-id} :openApplication false :roles [:authority]) => ok?
        (fact "Nobody got mail" (last-email) => nil))

      (fact "Upload attachment to draft"
        (upload-attachment-to-target sonja application-id nil true verdict-id "verdict")
        (fact "Nobody got mail" (last-email) => nil))

      (fact "Pena does not see comment or attachment"
        (let [{:keys [comments attachments]} (query-application pena application-id)]
          (count comments) => 0
          (count (keep :latestVersion attachments)) => 0))

      (fact "Sonja sees comment and attachment"
        (let [{:keys [comments attachments]} (query-application sonja application-id)
              attachments-with-versions (filter (comp seq :latestVersion) attachments)]
          (count comments) => 2 ; comment and new attachment auto-comment
          (count attachments-with-versions) => 1

          (fact "Attachment application state"
            (-> attachments-with-versions first :applicationState) => "verdictGiven")))

      (fact "Comment verdict, target is Ronja"
        (command sonja :add-comment :id application-id :text "hello" :to ronja-id :target {:type "verdict" :id verdict-id} :openApplication false :roles [:authority]) => ok?
        (let [email (last-email)]
          (fact "Ronja got mail"
            email => map?
            (:to email) => (contains (email-for "ronja"))
            (let [[href a-id v-id] (re-find #"(?sm)http.+/app/fi/authority#!/verdict/([A-Za-z0-9-]+)/([0-9a-z]+)" (get-in email [:body :plain]))]
              a-id => application-id
              v-id => verdict-id))))

      (fact "Publish verdict" (command sonja :publish-verdict :id application-id :verdictId verdict-id) => ok?)

      (let [application (query-application sonja application-id)
            first-attachment (get-in application [:attachments 0])]

        (fact "verdict is given"
          (:state application) => "verdictGivent"
          (-> application :history last :state) => "verdictGivent")

        (fact "Authority is still able to add an attachment"

          (let [email (last-email)]
            (:to email) => (contains (email-for-key pena))
            (:subject email) => "Lupapiste.fi: Paatoskuja 9 - p\u00e4\u00e4t\u00f6s"
            email => (partial contains-application-link-with-tab? application-id "verdict" "applicant"))

          (upload-attachment sonja (:id application) first-attachment true)
          (upload-attachment pena (:id application) first-attachment false))))))

(fact "Fetch verdict when all antoPvms are in the future"
  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        app-id (:id application)
        future-timestamp (util/get-timestamp-from-now :week 1)]
    (override-krysp-xml sipoo "753-R" :R [{:selector [:yht:antoPvm] :value (util/to-xml-date future-timestamp)}])
    (command sonja :check-for-verdict :id app-id) => (partial expected-failure? "info.paatos-future-date")
    (let [app-with-no-verdicts (query-application mikko app-id)]
      (fact "No verdicts"
        (-> app-with-no-verdicts :verdicts count) => 0)))
  (against-background (after :facts (remove-krysp-xml-overrides sipoo "753-R" :R))))

(facts* "Fetch verdict from KRYSP backend"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        application-id (:id application)
        attachment-count (-> application :attachments count)
        _ (command sonja :check-for-verdict :id application-id) => ok?
        app-with-verdict (query-application mikko application-id)
        verdict-id1 (-> app-with-verdict :verdicts first :id)
        verdict-id2 (-> app-with-verdict :verdicts second :id)
        _ (command sonja :add-comment :id application-id :text "paatosta" :target {:id verdict-id1 :type "verdict"} :roles []) => ok?
        app-with-verdict (query-application mikko application-id)]

    (:organization application) => "753-R"

    (fact "Application states"
      (:state application) => "submitted"
      (:state app-with-verdict) => "verdictGiven")

    (fact "No verdicts in the beginnig"
      (-> application :verdicts count) => 0)

    (fact "Two verdicts from fixture"
      (-> app-with-verdict :verdicts count) => 2)

    (fact "No comments in the beginnig"
      (-> application :comments count) => 0)

    (fact "Has a verdict comment and an attachment comment"
      (-> app-with-verdict :comments count) => 2
      (-> app-with-verdict :comments first :target :type) => "attachment"
      (-> app-with-verdict :comments last :target :type) => "verdict")

    (fact "Applicant receives email about verdict (not about comment)"
      (let [email (last-email)]
       (:to email) => (contains (email-for-key mikko))
       (:subject email) => "Lupapiste.fi: Paatoskuja 17 - p\u00e4\u00e4t\u00f6s"
       email => (partial contains-application-link-with-tab? application-id "verdict" "applicant")))

    (fact "There is one more attachments, see krysp/sample/verdict.xml"
      (-> app-with-verdict :attachments count) => (inc attachment-count))

    (fact "Lupaehdot, see krysp/sample/verdict.xml"
      (-> application :tasks count) => 0
      (-> app-with-verdict :tasks count) => 9)

    (facts "Delete verdicts"
     (command sonja :delete-verdict :id application-id :verdictId verdict-id1) => ok?
     (command sonja :delete-verdict :id application-id :verdictId verdict-id2) => ok?
     (let [app-without-verdict (query-application mikko application-id)]
       (fact "State stepped back"
         (:state app-without-verdict) => "submitted")
       (fact "Tasks have been deleted"
         (-> app-without-verdict :tasks count) => 0)
       (fact "Attachment has been deleted"
         (-> app-without-verdict :attachments count) => attachment-count)
       (fact "Verdicts have been deleted"
         (-> app-without-verdict :verdicts count) => 0)
       (fact "Comments have been deleted"
         (-> app-without-verdict :comments count) => 0)))

    ))

(fact "Rakennus & rakennelma"
  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        application-id (:id application)
        _ (command sonja :check-for-verdict :id application-id) => ok?
        application (query-application mikko application-id)
        buildings   (:buildings application)
        {:as building1}   (first buildings)
        {:as building2}   (second buildings)
        {:as building3}   (last buildings)]

    (count buildings) => 3

    (:buildingId building1) => "123456001M"
    (:propertyId building1) => "18601234567890"
    (:index building1) => "1"
    (:usage building1) => "039 muut asuinkerrostalot"
    (:area building1) => "2000"
    (:created building1) => "2013"

    (:buildingId building2) => "123456002N"
    (:propertyId building2) => "18601234567891"
    (:index building2) => "2"
    (:usage building2) => "719 muut varastorakennukset"
    (:area building2) => "20"
    (:created building2) => "2013"

    (fact "Building 3 does not have permanent ID"
      (:buildingId building3) => "103")

    (:propertyId building3) => "18601234567892"
    (:index building3) => "3"
    (fact "kayttotarkoitus is never nil" (:usage building3) => "")
    (:area building3) => "22"
    (:created building3) => "2013"))
