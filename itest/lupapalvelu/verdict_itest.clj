(ns lupapalvelu.verdict-itest
  (:require [clj-time.coerce :as coerce]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libreclient]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.util :as util]))

(apply-remote-minimal)

;; Old verdict itest use cases converted to Pate legacy itests.

(fact* "Give verdict"
  (last-email) ; Inbox zero

  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id :address "Paatoskuja 9")
        application-id (:id application)
        email          (last-email) => truthy]
    (:state application) => "submitted"
    (:to email) => (contains (email-for-key pena))
    (:subject email) => "Lupapiste: Paatoskuja 9, Sipoo - hankkeen tila on nyt Hakemus j\u00e4tetty"
    (get-in email [:body :plain]) => (contains "Hakemus j\u00e4tetty")
    email => (partial contains-application-link? application-id "applicant")

    (let [new-verdict-resp (command sonja :new-legacy-verdict-draft :id application-id) => ok?
          verdict-id       (:verdict-id new-verdict-resp)                               => truthy
          _                (fill-verdict sonja application-id verdict-id
                                         :kuntalupatunnus "aaa"
                                         :verdict-code "42"
                                         :handler "Paatoksen antaja"
                                         :anto 123
                                         :lainvoimainen 124
                                         :verdict-text ""
                                         :verdict-section "")
          application      (query-application sonja application-id)
          verdict          (query sonja :pate-verdict :id application-id
                                  :verdict-id verdict-id)]
      (:permitSubtype application) => falsey
      (count (:pate-verdicts application)) => 1

      (-> verdict :verdict :data) => {:kuntalupatunnus "aaa"
                                      :verdict-code    "42"
                                      :handler         "Paatoksen antaja"
                                      :anto            123
                                      :lainvoimainen   124
                                      :verdict-text    ""
                                      :verdict-section ""}
      (:filled verdict) => true

      ;; TODO: Comments are not yet visible for Pate verdicts.
      (fact "Comment verdict"
        (command sonja :add-comment :id application-id :text "hello" :to nil :target {:type "verdict" :id verdict-id} :openApplication false :roles [:authority]) => ok?
        (fact "Nobody got mail" (last-email) => nil))

      (fact "Upload attachment to draft"
        (add-verdict-attachment sonja application-id verdict-id "Draft" )
        (fact "Nobody got mail" (last-email) => nil))

      (fact "Pena does not see comment or attachment"
        (let [{:keys [comments attachments]} (query-application pena application-id)]
          (count comments) => 0
          (count (keep :latestVersion attachments)) => 0))

      (fact "Sonja sees comment and attachment"
        (let [{:keys [comments attachments]} (query-application sonja application-id)
              attachments-with-versions      (filter (comp seq :latestVersion) attachments)]
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
            (let [[_ a-id v-id] (re-find #"(?sm)http.+/app/fi/authority#!/verdict/([A-Za-z0-9-]+)/([0-9a-z]+)"
                                         (get-in email [:body :plain]))]
              a-id => application-id
              v-id => verdict-id))))

      (fact "Publish verdict"
        (command sonja :publish-legacy-verdict
                 :id application-id :verdict-id verdict-id) => ok?)
      (verdict-pdf-queue-test sonja {:app-id     application-id
                                     :verdict-id verdict-id})
      (check-verdict-date sonja application-id 123)
      (let [application      (query-application sonja application-id)
            {:keys [verdict]} (query sonja :pate-verdict :id application-id
                                     :verdict-id (-> application :pate-verdicts
                                                     first :id))
            first-attachment (get-in application [:attachments 0])]
        (fact "verdict data"
          (-> verdict :state) => "published")
        (fact "verdict is given"
          (:state application) => "verdictGiven"
          (-> application :history last :state) => "verdictGiven")

        (fact "Email was sent"
          (let [email (last-email)
                body  (get-in email [:body :plain])]
            (:to email) => (contains (email-for-key pena))
            (:subject email) => "Lupapiste: Paatoskuja 9, Sipoo - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"
            email => (partial contains-application-link-with-tab? application-id "verdict" "applicant")
            body => (contains "Hei Pena,")))

        (fact "Authority is still able to add an attachment"
          (upload-attachment sonja (:id application) first-attachment true)
          (upload-attachment pena (:id application) first-attachment false))

        (fact "Applicant can not alter the attachment"
          (command pena :set-attachment-type :id (:id application)
                                             :attachmentId (:id first-attachment)
                                             :attachmentType "muut.aitapiirustus") => fail?
          (command pena :delete-attachment :id (:id application)
                                           :attachmentId (:id first-attachment)
                                           :attachmentType "muut.aitapiirustus") => fail?)))))

(fact "Fetch verdict when all antoPvms are in the future"
  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        app-id (:id application)
        future-timestamp (util/get-timestamp-from-now :week 1)]
    (override-krysp-xml sipoo "753-R" :R [{:selector [:yht:antoPvm] :value (util/to-xml-date future-timestamp)}])

    (command sonja :check-for-verdict :id app-id) => (partial expected-failure? "info.paatos-future-date")
    (fact "No verdicts"
      (-> (query-application mikko app-id) :verdicts count) => 0)

    (fact "Disable antoPvm check"
      (command sipoo :set-organization-validate-verdict-given-date :enabled false) => ok?

      (fact "Verdict is now read"
        (command sonja :check-for-verdict :id app-id) => ok?
        (-> (query-application mikko app-id) :verdicts count) => pos?))))

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
      (-> app-with-verdict :comments count) => 3
      (-> app-with-verdict :comments first :target :type) => "attachment"
      (-> app-with-verdict :comments last :target :type) => "verdict")

    (fact "Applicant receives email about verdict (not about comment)"
      (let [email (last-email)]
       (:to email) => (contains (email-for-key mikko))
       (:subject email) => "Lupapiste: Paatoskuja 17, Sipoo - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"
       email => (partial contains-application-link-with-tab? application-id "verdict" "applicant")))

    (fact "There are two new attachments, see krysp/dev/verdict.xml"
      (-> app-with-verdict :attachments count) => (+ attachment-count 2))

    (when (libreclient/enabled?)
      (facts "RTF attachment gets converted to PDF with the original file stored as well"
        (let [{:keys [latestVersion] :as attachment} (->> (:attachments app-with-verdict)
                                                          (filter (fn [{:keys [latestVersion]}] (= (:filename latestVersion) "sample-rtf-verdict.pdf")))
                                                          (first))]
          (fact "attachment exists"
            attachment => truthy)

          (fact "latest version file is a PDF"
            (let [{:keys [headers body]} (raw sonja
                                              "download-attachment"
                                              :file-id (:fileId latestVersion)
                                              :id application-id)]
              (get headers "Content-Type") => "application/pdf"
              (.contains body "PDF") => true)

          (fact "original file exists and is a RTF"
            (:originalFileId latestVersion) => truthy
            (let [{:keys [headers body]} (raw sonja
                                              "download-attachment"
                                              :file-id (:originalFileId latestVersion)
                                              :id application-id)]
              (get headers "Content-Type") => "application/rtf"
              (.contains body "\\rtf1\\ansi") => true))))))

    (fact "Lupaehdot, see krysp/dev/verdict.xml"
      (-> application :tasks count) => 0
      (-> app-with-verdict :tasks count) => 9)

    (facts "Delete verdicts"
      (fact "Delete first backing system verdict"
        (command sonja :delete-verdict :id application-id :verdict-id verdict-id1) => ok?)
      (fact "No step back since there still is a verdict"
        (:state (query-application mikko application-id)) => "verdictGiven")
      (let [{draft-id :verdict-id} (command sonja :new-legacy-verdict-draft :id application-id)]
        (fact "No step back: draft deleted"
          (command sonja :delete-legacy-verdict :id application-id :verdict-id draft-id) => ok?
          (:state (query-application mikko application-id)) => "verdictGiven"))
      (fact "Create one more draft"
        (command sonja :new-legacy-verdict-draft :id application-id) => ok?)
      (fact "Delete second backing system verdict"
        (command sonja :delete-verdict :id application-id :verdict-id verdict-id2)
        => ok?)
      (let [app-without-verdict (query-application mikko application-id)]
        (fact "State stepped back"
          (:state app-without-verdict) => "submitted")
        (fact "History is updated"
          (-> app-without-verdict :history last :state) => "submitted")
        (fact "Tasks have been deleted"
          (-> app-without-verdict :tasks count) => 0)
        (fact "Attachment has been deleted"
          (-> app-without-verdict :attachments count) => attachment-count)
        (fact "Verdicts have been deleted"
          (-> app-without-verdict :verdicts count) => 0)
        (fact "Comments have been deleted"
          (-> app-without-verdict :comments count) => 0)))))

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

(facts "Kiinteistotoimitus verdicts"
       (let [app-id (create-app-id pena :propertyId sipoo-property-id :operation "kiinteistonmuodostus")
             app          (query-application pena app-id)
             {doc-id :id} (domain/get-document-by-name app "kiinteistonmuodostus")
             _            (command pena :update-doc
                                   {:doc doc-id
                                    :id app-id
                                    :updates [["kiinteistonmuodostus.kiinteistonmuodostusTyyppi","tilusvaihto"]]
                                    :collection "documents"})
             _            (command pena :submit-application :id app-id)
             _            (command sonja :check-for-verdict :id app-id)
             app          (tools/unwrapped (query-application pena app-id))
             [logo sample logo2 lupapiste calendar] (:attachments app)]
         (fact "Five attachments" (count (:attachments app)) => 5)
         (fact "Three verdicts" (count (:verdicts app)) => 3)
         (facts "First verdict"
                (let [verdict-id (-> app :verdicts first :id)]
                  (fact "Two decisions (paatos)" (count (get-in app [:verdicts 0 :paatokset])) => 2)
                  (facts "First decision"
                         (let [d (get-in app [:verdicts 0 :paatokset 0])
                               pk (-> d :poytakirjat first)]
                           (fact "Date is 10.10.2015" (-> d :paivamaarat :paatosdokumentinPvm)
                                 => (coerce/to-long "2015-10-10"))
                           (fact "Poytakirja details"
                                 (select-keys pk [:paatoksentekija :paatoskoodi :status])
                                 => {:paatoksentekija "Tiina Tilusvaihto"
                                     :paatoskoodi "Kiinteist\u00f6toimitus"
                                     :status "43"})
                           (fact "Since there are multiple attachments the poytakirja urlHash is the verdict id"
                                 (:urlHash pk) => verdict-id)
                           (facts "Logo and sample attachments have the poytakirja as target"
                                  (fact "Logo target"
                                        (:target logo) => {:type "verdict"
                                                           :id verdict-id
                                                           :urlHash verdict-id})
                                  (fact "Sample target"
                                        (:target sample) => {:type "verdict"
                                                             :id verdict-id
                                                             :urlHash verdict-id}))))
                  (facts "Second decision"
                         (let [d (get-in app [:verdicts 0 :paatokset 1])
                               pk (-> d :poytakirjat first)]
                           (fact "Date is 11.11.2015" (-> d :paivamaarat :paatosdokumentinPvm)
                                 => (coerce/to-long "2015-11-11"))
                           (fact "Poytakirja details"
                                 (select-keys pk [:paatoksentekija :paatoskoodi :status])
                                 => {:paatoksentekija "Timo Tilusvaihto"
                                     :paatoskoodi "ei tiedossa"
                                     :status "42"})
                           (fact "Since there is only one attachment the poytakirja urlHash is the attachment logo2 id"
                                 (:urlHash pk) => (-> (:id logo2)))
                           (fact "Attachment logo2 target"
                                 (:target logo2) => {:type "verdict"
                                                     :id verdict-id
                                                     :urlHash (:id logo2)})))))
         (facts "Second verdict"
                (fact "One decision (paatos)" (count (get-in app [:verdicts 1 :paatokset])) => 1)
                (let [verdict-id (-> app :verdicts second :id)
                      d (get-in app [:verdicts 1 :paatokset 0])
                      pk (-> d :poytakirjat first)]
                  (fact "Date is 1.11.2015" (-> d :paivamaarat :paatosdokumentinPvm) => (coerce/to-long "2015-11-01"))
                  (fact "Poytakirja details"
                        (select-keys pk [:paatoksentekija :paatoskoodi :status])
                        => {:paatoksentekija "Riku Rasitetoimitus"
                            :paatoskoodi "Kiinteist\u00f6rekisterin pit\u00e4j\u00e4n p\u00e4\u00e4t\u00f6s"
                            :status "44"})
                  (fact "Since there are multiple attachments the poytakirja urlHash is the verdict id"
                        (:urlHash pk) => verdict-id)
                  (facts "Lupapiste and calendar attachments have the poytakirja as target"
                         (fact "Logo target"
                               (:target lupapiste) => {:type "verdict"
                                                       :id verdict-id
                                                       :urlHash verdict-id})
                         (fact "Calendar target"
                               (:target calendar) => {:type "verdict"
                                                   :id verdict-id
                                                      :urlHash verdict-id}))))
         (facts "Third verdict"
                (fact "One decision (paatos)" (count (get-in app [:verdicts 2 :paatokset])) => 1)
                (let [d (get-in app [:verdicts 2 :paatokset 0])
                      pk (-> d :poytakirjat first)]
                  (fact "Date is 8.12.2015" (-> d :paivamaarat :paatosdokumentinPvm) => (coerce/to-long "2015-12-08"))
                  (fact "Poytakirja details"
                        (select-keys pk [:paatoksentekija :paatoskoodi :status])
                        => {:paatoksentekija "Liisa Lohkominen"
                            :paatoskoodi "Kiinteist\u00f6toimitus"
                            :status "43"})))))

(fact "When checking verdict, if no valid verdict exits, buildingIds and backendIds are still saved to application"
  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        app-id (:id application)
        op1    (:primaryOperation application)]
    (override-krysp-xml sipoo "753-R" :R [{:selector [:rakval:paatostieto] :value nil}
                                          {:selector [:rakval:rakennuksenTiedot :rakval:rakennustunnus :rakval:muuTunnustieto :rakval:MuuTunnus :yht:tunnus] :value (:id op1)}])

    (let [response (command sonja :check-for-verdict :id app-id)
          application (query-application sonja app-id)
          op-document (some #(when (= (:id op1) (-> % :schema-info :op :id)) %) (:documents application))]
      (fact "No verdicts found"
        (:ok response) => false
        (:text response) => "info.no-verdicts-found-from-backend")
      (fact "Buildings were still read"
        (:buildings application) => sequential?)
      (fact "building id from KRYSP is saved to document"
        (get-in op-document [:data :valtakunnallinenNumero :value]) => "123456001M")
      (fact "backend id from KRYSP is saved to verdict"
        (get-in application [:verdicts 0 :kuntalupatunnus]) => "2013-01"
        (get-in application [:verdicts 1 :kuntalupatunnus]) => "13-0185-R")
      (fact "saved verdicts are drafts"
        (get-in application [:verdicts 0 :draft]) => true
        (get-in application [:verdicts 1 :draft]) => true
      (fact "paatokset arrays are empty"
        (get-in application [:verdicts 0 :paatokset]) => []
        (get-in application [:verdicts 1 :paatokset]) => [])))))

(fact "In verdicts-extra-reader, buildingIds are updated to documents also"
  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        app-id (:id application)
        op1    (:primaryOperation application)
        doc-count (count (:documents application))
        expected-docs ["uusiRakennus" "hankkeen-kuvaus"
                       "paatoksen-toimitus-rakval" "rakennuspaikka"
                       "rakennusjatesuunnitelma"
                       "hakija-r" "maksaja" "paasuunnittelija" "suunnittelija"]]

    (map (comp :name :schema-info ) (:documents application)) => (contains expected-docs :in-any-order)

    (override-krysp-xml sipoo "753-R" :R [{:selector [:rakval:rakennuksenTiedot :rakval:rakennustunnus :rakval:muuTunnustieto :rakval:MuuTunnus :yht:tunnus] :value (:id op1)}])

    (let [response (command sonja :check-for-verdict :id app-id)
          application (query-application mikko app-id)
          op-document (some #(when (= (:id op1) (-> % :schema-info :op :id)) %) (:documents application))
          new-docs (:documents application)]
      (fact "Verdicts are ok"
        (:ok response) => true
        (count (:verdicts application)) => 2
        (count (:tasks application)) => 9)
      (fact "Buildings were saved"
        (:buildings application) => (every-checker not-empty sequential?))
      (fact "building id from KRYSP is saved to document"
        (get-in op-document [:data :valtakunnallinenNumero :value]) => "123456001M")

      (fact "Rakennusjateselvitys is a new document"
        (count new-docs) => (inc doc-count)
        (-> (last new-docs)
            :schema-info
            :name) => "rakennusjateselvitys")))

  (against-background (after :facts (remove-krysp-xml-overrides sipoo "753-R" :R))))

(facts "Section requirements for verdict"
       (let [r-id         (create-app-id pena :operation "aita" :propertyId sipoo-property-id)
             kt-id        (create-app-id pena :operation "kiinteistonmuodostus" :propertyId sipoo-property-id)
             p-id         (create-app-id pena :operation "poikkeamis" :propertyId sipoo-property-id)
             section-fail (partial expected-failure? :info.section-required-in-verdict)]
         (fact "Submit applications"
               (command pena :submit-application :id r-id) => ok?
               (command pena :submit-application :id kt-id) => ok?
               (command pena :submit-application :id p-id) => ok?)
         (fact "Verdicts for all"
               (command sonja :check-for-verdict :id r-id) => ok?
               (command sonja :check-for-verdict :id kt-id) => ok?
               (command sonja :check-for-verdict :id p-id) => ok?)
         (fact "Enable section requirement for Sipoo"
               (command sipoo :section-toggle-enabled :flag true) => ok?)
         (fact "Section requirement for aita does not prevent verdict, since verdict XML contains section."
               (command sipoo :section-toggle-operation :operationId "aita" :flag true) => ok?
               (command sonja :check-for-verdict :id r-id) => ok?)
         (fact "Aita setting does not affect kiinteistonmuodostus"
               (command sonja :check-for-verdict :id kt-id) => ok?)
         (fact "Section requirement for kiinteistonmuodostus prevents verdict"
               (command sipoo :section-toggle-operation :operationId "kiinteistonmuodostus" :flag true) => ok?
               (command sonja :check-for-verdict :id kt-id) => section-fail)
         (fact "Section requirement for poikkeamis does not prevent verdict, since verdict XML contains section."
               (command sipoo :section-toggle-operation :operationId "poikkeamis" :flag true) => ok?
               (command sonja :check-for-verdict :id p-id) => ok?)
         (fact "Disable section requirement for aita"
               (command sipoo :section-toggle-operation :operationId "aita" :flag false) => ok?
               (command sonja :check-for-verdict :id r-id) => ok?)
         (fact "Disable section requirement for Sipoo"
               (command sipoo :section-toggle-enabled :flag false) => ok?
               (command sonja :check-for-verdict :id kt-id) => ok?)))

(defn verdict-files [app-id]
  (->> (query-application sonja app-id)
       :attachments
       (filter #(-> % :target :type (= "verdict")))
       (map #(-> % :latestVersion :filename))))

(facts "Delete deprecated verdict attachments"
  (let [{app-id :id} (create-and-submit-application pena
                                                    :operation "poikkeamis"
                                                    :propertyId sipoo-property-id)]
    (fact "Sonja fetches verdict"
      (command sonja :check-for-verdict :id app-id) => ok?)
    (let [files (verdict-files app-id)]
      (fact "There is only one verdict attachment"
        files => ["sample-attachment.pdf"]))
    (fact "Sonja fetches verdict with the attachment changed"
      (override-krysp-xml sipoo "753-R" :P [{:selector [:yht:poytakirja :yht:linkkiliitteeseen ]
                                             :value    "http://localhost:8000/dev/sample-verdict.pdf"}])
      (command sonja :check-for-verdict :id app-id) => ok?)
    (let [files (verdict-files app-id)]
      (fact "There is still only one verdict attachment"
        files => ["sample-verdict.pdf"])))
  (against-background (after :facts (remove-krysp-xml-overrides sipoo "753-R" :P))))
