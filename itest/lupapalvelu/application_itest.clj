(ns lupapalvelu.application-itest
  (:require [midje.sweet :refer :all]
            [clojure.string :refer [join]]
            [sade.core :refer [unauthorized]]
            [sade.strings :as ss]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application-api :as app]
            [lupapalvelu.document.tools :as tools]))

(mongo/connect!)

(apply-remote-minimal)

#_(fact "can't inject js in 'x' or 'y' params"
   (create-app pena :x ";alert(\"foo\");" :y "what ever") =not=> ok?
   (create-app pena :x "0.1x" :y "1.0")                   =not=> ok?
   (create-app pena :x "1x2" :y "1.0")                    =not=> ok?
   (create-app pena :x "2" :y "1.0")                      =not=> ok?
   (create-app pena :x "410000.1" :y "6610000.1")         => ok?)

(fact "creating application without message"
  (let [id    (create-app-id pena)
        app   (query-application pena id)]
    app => (contains {:id id
                      :state "draft"
                      :location {:x 444444.0 :y 6666666.0}
                      :organization "753-R"})
    (count (:comments app)) => 0
    (first (:auth app)) => (contains
                             {:firstName "Pena"
                              :lastName "Panaani"
                              :type "owner"
                              :role "owner"})
    (fact "has applicant" (:applicant app) => "Panaani Pena")
    (fact "has allowedAttachmentTypes" (:allowedAttachmentTypes app) => seq)

    (fact "Draft is not returned by latest-applications"
      (let [resp (query pena :latest-applications)]
        resp => ok?
        (-> resp :applications count) => 0))))

(fact "creating application with message"
  (let [application-id  (create-app-id pena :messages ["hello"])
        application     (query-application pena application-id)
        hakija          (domain/get-document-by-name application "hakija")]
    (:state application) => "draft"
    (:opened application) => nil
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"))

(fact "application created to Sipoo belongs to organization Sipoon Rakennusvalvonta"
  (let [application-id  (create-app-id pena :propertyId sipoo-property-id)
        application     (query-application pena application-id)
        hakija (domain/get-document-by-name application "hakija")]
    (:organization application) => "753-R"))

(fact "the ready-calculated validation errors about required document fields, included by a newly created application, are updated when those application fields are filled"
  (let [application-id  (create-app-id pena :propertyId sipoo-property-id)
        application     (query-application pena application-id)
        hakija          (domain/get-document-by-name application "hakija")
        errs            (:validationErrors hakija)]
    (count errs) => pos?
    (some #(= "illegal-value:required" (-> % :result second)) errs)

    (generate-documents application pena)

    (let [application     (query-application pena application-id)
          hakija          (domain/get-document-by-name application "hakija")]
      (not-any? #(= "illegal-value:required" (-> % :result second)) errs))))

(fact "application created to Tampere belongs to organization Tampereen Rakennusvalvonta"
  (let [application-id  (create-app-id pena :propertyId tampere-property-id)
        application     (query-application pena application-id)
        hakija (domain/get-document-by-name application "hakija")]
    (:organization application) => "837-R"))

(fact "application created to Reisjarvi belongs to organization Peruspalvelukuntayhtyma Selanne"
  (let [application-id  (create-app-id pena :propertyId "62600000000000")
        application     (query-application pena application-id)
        hakija (domain/get-document-by-name application "hakija")]
    (:organization application) => "069-R"))

(fact* "Assign application to an authority"
       (let [application-id (create-app-id pena :propertyId sipoo-property-id)
             ;; add a comment to change state to open
             _ (comment-application pena application-id true) => ok?
             application (query-application sonja application-id)
             authority-before-assignation (:authority application)
             resp (command sonja :assign-application :id application-id :assigneeId ronja-id)
             assigned-app (query-application sonja application-id)
             authority-after-assignation (:authority assigned-app)]
         application-id => truthy
         application => truthy
         (success resp) => true
         (:id authority-before-assignation) => nil
         authority-after-assignation => (contains {:id ronja-id})
         (fact "Authority is not able to submit"
           sonja =not=> (allowed? sonja :submit-application :id application-id))))

(fact* "Assign application to an authority and then to no-one"
       (let [application-id (create-app-id pena :propertyId sipoo-property-id)
             ;; add a comment change set state to open
             _ (comment-application pena application-id true) => ok?
             application (query-application sonja application-id)
             authority-before-assignation (:authority application)
             resp (command sonja :assign-application :id application-id :assigneeId sonja-id)
             resp (command sonja :assign-application :id application-id :assigneeId nil)
             assigned-app (query-application sonja application-id)
             authority-in-the-end (:authority assigned-app)]
         (:id authority-before-assignation) => nil
         (:id authority-in-the-end) => nil))

(fact "Authority is able to create an application to a municipality in own organization"
  (let [application-id  (create-app-id sonja :propertyId sipoo-property-id)]
    (fact "Application is open"
      (let [application (query-application sonja application-id)]
        application => truthy
        (:state application) => "open"
        (:opened application) => truthy
        (:opened application) => (:created application)))
    (fact "Authority could submit her own application"
      sonja => (allowed? :submit-application :id application-id))
    (fact "Application is submitted"
      (let [resp        (command sonja :submit-application :id application-id)
            application (query-application sonja application-id)]
        resp => ok?
        (:state application) => "submitted"))))

(facts* "Application has opened when submitted from draft"
  (let [{id :id :as app1} (create-application pena) => truthy
        resp (command pena :submit-application :id id) => ok?
        app2 (query-application pena id) => truthy]
    (:opened app1) => nil
    (:opened app2) => number?))

(facts* "cancel application"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Peruutustie 23")
        application-id (:id application)]

    (fact "Mikko sees the application" (query mikko :application :id application-id) => ok?)
    (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)
    (fact "Sonja can cancel Mikko's application"
      (command sonja :cancel-application-authority :id application-id :text nil :lang "fi") => ok?)
    (fact "Sonja sees the canceled application" (query sonja :application :id application-id) => ok?)
    (let [email (last-email)]
      (:to email) => (contains (email-for-key mikko))
      (:subject email) => "Lupapiste.fi: Peruutustie 23 - hakemuksen tila muuttunut"
      (get-in email [:body :plain]) => (contains "Peruutettu")
      email => (partial contains-application-link? application-id "applicant")))

  (fact "Authority can cancel own application"
    (let [application-id  (create-app-id sonja :propertyId sipoo-property-id)]
      (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)
      (fact "Sonja can cancel the application"
        (let [r (command sonja :cancel-application-authority :id application-id :text nil :lang "fi")]
          r => ok?
          (fact "No comments exists from cancel" (-> r :application :comments count) => 0)))))

  (fact "Authority can cancel with reason text, which is added as comment"
    (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Peruutustie 23")
          cancel-reason "Testihakemus"]
      (command sonja :cancel-application-authority :id (:id application) :text cancel-reason :lang "fi") => ok?

      (fact "Mikko sees cancel reason text in comments"
        (let [application (:application (query mikko :application :id (:id application)))]
          (count (:comments application)) => 1
          (-> application :comments (first) :text) => (contains cancel-reason))))))

(fact "Authority in unable to create an application to a municipality in another organization"
  (create-app sonja :propertyId tampere-property-id) => unauthorized?)

(facts "Add operations"
  (let [application-id  (create-app-id mikko :propertyId tampere-property-id)]
    (comment-application mikko application-id true) => ok?
    (command veikko :assign-application :id application-id :assigneeId veikko-id) => ok?

    (fact "Applicant is able to add operation"
      (success (command mikko :add-operation :id application-id :operation "varasto-tms")) => true)

    (fact "Authority is able to add operation"
      (success (command veikko :add-operation :id application-id :operation "muu-uusi-rakentaminen")) => true)))

(facts "link to backend system"
  (let [application    (create-and-submit-application mikko :municipality sonja-muni)
        application-id (:id application)
        redirect-url   "http://www.taustajarjestelma.fi/servlet/kohde?kohde=rakennuslupatunnus&lupatunnus="]

    (command sonja :approve-application :id application-id :lang "fi")

    (facts "no vendor backend id (kuntalupatunnus)"
      (fact* "redirects to LP backend url if configured"
        (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val redirect-url) => ok?
        (let [resp (raw sonja :redirect-to-vendor-backend :id application-id) => http303?]
          (get-in resp [:headers "location"]) => (str redirect-url application-id)))

      (fact "error if no LP backend url configured"
        (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val "") => ok?
        (raw sonja :redirect-to-vendor-backend :id application-id) => http404?

        (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val redirect-url) => ok?
        (raw sonja :redirect-to-vendor-backend :id application-id) => http404?))

    (facts "vendor backend id available (kuntalupatunnus)"
      (command sonja :check-for-verdict :id application-id)
      (let [{verdicts :verdicts} (query-application sonja application-id)
            vendor-backend-id    (app/get-vendor-backend-id verdicts)]
        (fact* "redirect to backend id url if configured"
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val redirect-url) => ok?
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val "http://dontgohere.com") => ok?
          (let [resp (raw sonja :redirect-to-vendor-backend :id application-id) => http303?]
            (get-in resp [:headers "location"]) => (str redirect-url vendor-backend-id)))

        (fact* "redirect to LP backend url if available and backend id url not configured"
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val "") => ok?
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val redirect-url) => ok?
          (let [resp (raw sonja :redirect-to-vendor-backend :id application-id) => http303?]
            (get-in resp [:headers "location"]) => (str redirect-url application-id)))

        (fact "error if no LP backend url or backend id url configured"
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val "") => ok?
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val "") => ok?
          (raw sonja :redirect-to-vendor-backend :id application-id) => http404?)))))

(fact "Pena cannot create app for organization that has new applications disabled"
  (let [resp  (create-app pena :propertyId "99700000000000")]
    resp =not=> ok?
    (:text resp) => "error.new-applications-disabled"))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(defn- set-and-check-person [api-key application-id initial-document path]
  (fact "new person is set"
    (command api-key :set-user-to-document :id application-id :documentId (:id initial-document) :userId mikko-id :path (if (seq path) (join "." path) "")) => ok?
    (let [updated-app (query-application mikko application-id)
          update-doc (domain/get-document-by-id updated-app (:id initial-document))
          schema-name  (get-in update-doc [:schema-info :name])
          person-path  (into [] (concat [:data] (map keyword path) [:henkilotiedot]))
          company-path (into [] (concat [:data] (map keyword path) [:yritys]))
          experience-path (into [] (concat [:data] (map keyword path) [:patevyys]))
          suunnittelija? (in? ["paasuunnittelija" "suunnittelija"] schema-name )]
      (get-in update-doc (into person-path [:etunimi :value])) => "Mikko"
      (get-in update-doc (into person-path [:sukunimi :value])) => "Intonen"
      (get-in update-doc (into person-path [:hetu :value])) => "******-****"
      (get-in update-doc (into company-path [:yritysnimi :value])) => (if suunnittelija? "Yritys Oy" nil)
      (get-in update-doc (into company-path [:liikeJaYhteisoTunnus :value])) => (if suunnittelija? "1234567-1" nil)
      (get-in update-doc (into experience-path [:koulutusvalinta :value])) => (if suunnittelija? "kirvesmies" nil)
      (get-in update-doc (into experience-path [:valmistumisvuosi :value])) => (if suunnittelija? "2000" nil)
      (get-in update-doc (into experience-path [:fise :value])) => (if suunnittelija? "f" nil))))

(defn- check-empty-person
  ([document doc-path args]
   (let [empty-person {:etunimi  {:value ""}
                       :sukunimi {:value ""}
                       :hetu     {:value nil}}
         empty-person (merge empty-person args)]
     document => truthy
     (get-in document doc-path) => empty-person))
  ([document doc-path] (check-empty-person document doc-path {}))
  )

(facts "Set user to document"
  (let [application   (create-and-submit-application mikko :propertyId sipoo-property-id)
        application-id   (:id application)
        paasuunnittelija (domain/get-document-by-name application "paasuunnittelija")
        suunnittelija    (domain/get-document-by-name application "suunnittelija")
        hakija     (domain/get-document-by-name application "hakija")
        maksaja    (domain/get-document-by-name application "maksaja")]


    (fact "initially person data is empty"
      (check-empty-person paasuunnittelija [:data :henkilotiedot])
      (check-empty-person hakija [:data :henkilo :henkilotiedot] {:turvakieltoKytkin {:value false}})
      (check-empty-person maksaja [:data :henkilo :henkilotiedot] {:turvakieltoKytkin {:value false}}))

    (set-and-check-person mikko application-id paasuunnittelija [])
    (set-and-check-person mikko application-id hakija ["henkilo"])
    (set-and-check-person mikko application-id maksaja ["henkilo"])

    (fact "there is no suunnittelija"
      suunnittelija => truthy
      (get-in suunnittelija [:data :henkilotiedot]) => {:etunimi {:value ""}, :hetu {:value nil}, :sukunimi {:value ""}})

    (let [doc-id (:id suunnittelija)
          code "RAK-rakennesuunnittelija"]

      (fact "suunnittelija kuntaroolikoodi is set"
        (command mikko :update-doc :id application-id :doc doc-id :updates [["kuntaRoolikoodi" code]]) => ok?
        (let [updated-app          (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          updated-suunnittelija => truthy
          (get-in updated-suunnittelija [:data :kuntaRoolikoodi :value]) => code))

      (fact "suunnittelija patevyys is set"
        (command mikko :update-doc :id application-id :doc doc-id :updates
                                   [["patevyys.kokemus" "10"]
                                    ["patevyys.patevyysluokka" "AA"]
                                    ["patevyys.patevyys" "Patevyys"]]) => ok?
        (let [updated-app          (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          updated-suunnittelija => truthy
          (get-in updated-suunnittelija [:data :patevyys :patevyys :value]) => "Patevyys"
          (get-in updated-suunnittelija [:data :patevyys :patevyysluokka :value]) => "AA"
          (get-in updated-suunnittelija [:data :patevyys :kokemus :value]) => "10"))

      (fact "new suunnittelija is set"
        (command mikko :set-user-to-document :id application-id :documentId (:id suunnittelija) :userId mikko-id :path "") => ok?
        (let [updated-app           (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          (get-in updated-suunnittelija [:data :henkilotiedot :etunimi :value]) => "Mikko"
          (get-in updated-suunnittelija [:data :henkilotiedot :sukunimi :value]) => "Intonen"
          (get-in updated-suunnittelija [:data :yritys :yritysnimi :value]) => "Yritys Oy"
          (get-in updated-suunnittelija [:data :yritys :liikeJaYhteisoTunnus :value]) => "1234567-1"
          (get-in updated-suunnittelija [:data :patevyys :koulutusvalinta :value]) => "kirvesmies"
          (get-in updated-suunnittelija [:data :patevyys :koulutus :value]) => ""
          (get-in updated-suunnittelija [:data :patevyys :valmistumisvuosi :value]) => "2000"
          (get-in updated-suunnittelija [:data :patevyys :fise :value]) => "f"

          (fact "applicant sees fully masked person id"
            (get-in updated-suunnittelija [:data :henkilotiedot :hetu :value]) => "******-****")

          (fact "suunnittelija kuntaroolikoodi is preserved (LUPA-774)"
            (get-in updated-suunnittelija [:data :kuntaRoolikoodi :value]) => code)))

      (fact "application is unassigned, Sonja does not see the full person IDs"
        (let [app (query-application sonja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (-> app :authority :id) => nil
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-****"))

      (fact "application is unassigned, Ronja does not see the full person IDs"
        (let [app (query-application ronja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-****"))

      (fact "Sonja assigns the application to herself and sees the full person ID"
        (command sonja :assign-application :id application-id :assigneeId sonja-id) => ok?
        (let [app (query-application sonja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (:authority app) => (contains {:id sonja-id})
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-0002"))

      (fact "Ronja still does not see the full person ID"
        (let [app (query-application ronja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (:authority app) => (contains {:id sonja-id})
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-****")))))

(facts "Set company to document"
  (let [{app-id :id :as app} (create-application pena :propertyId sipoo-property-id)
        maksaja              (domain/get-document-by-name app "maksaja")
        get-doc-value        (fn [doc path-prefix path]
                               (-> (get-in doc (into path-prefix path))
                                   tools/unwrapped))]

    (fact "initially maksaja company is empty"
      (let [check (partial get-doc-value maksaja [:data :yritys])]
        (doseq [path [[:yritysnimi]
                      [:liikeJaYhteisoTunnus]
                      [:verkkolaskutustieto :ovtTunnus]
                      [:verkkolaskutustieto :valittajaTunnus]
                      [:yhteyshenkilo :henkilotiedot :etunimi]]]
          (check path) => ss/blank?)))

    (let [resp (invite-company-and-accept-invitation pena app-id "solita")]
      (:status resp) => 200)

    (command pena :set-company-to-document :id app-id :documentId (:id maksaja) :companyId "solita" :path "yritys") => ok?

    (let [application (query-application pena app-id)
          maksaja     (domain/get-document-by-id application (:id maksaja))
          company     (company-from-minimal-by-id "solita")
          check       (partial get-doc-value maksaja [:data :yritys])]
      (check [:yritysnimi :value]) => (:name company)
      (check [:liikeJaYhteisoTunnus :value]) => (:y company)
      (check [:verkkolaskutustieto :ovtTunnus :value]) => (:ovt company)
      (check [:verkkolaskutustieto :valittajaTunnus]) => (:pop company)
      (check [:yhteyshenkilo :henkilotiedot :etunimi :value]) => (-> (find-user-from-minimal-by-apikey pena) :firstName))))

(fact* "Merging building information from KRYSP does not overwrite muutostyolaji or energiatehokkuusluvunYksikko"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "kayttotark-muutos")
        app (query-application pena application-id)
        rakmuu-doc (domain/get-document-by-name app "rakennuksen-muuttaminen")
        resp2 (command pena :update-doc :id application-id :doc (:id rakmuu-doc) :collection "documents" :updates [["muutostyolaji" "muut muutosty\u00f6t"]])
        updated-app (query-application pena application-id)
        building-info (command pena :get-building-info-from-wfs :id application-id) => ok?
        doc-before (domain/get-document-by-name updated-app "rakennuksen-muuttaminen")
        building-id (:buildingId (first (:data building-info)))

        resp3 (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id :path "buildingId" :overwrite true) => ok?
        merged-app (query-application pena application-id)
        doc-after (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

    (fact "muutostyolaji"
      (get-in doc-before [:data :muutostyolaji :value]) => "muut muutosty\u00f6t"
      (get-in doc-after [:data :muutostyolaji :value]) => "muut muutosty\u00f6t")

    (facts "energiatehokkuusluvunYksikko"
      (fact "document has default value"
        (get-in doc-before [:data :luokitus :energiatehokkuusluvunYksikko :value]) => "kWh/m2")
      (fact "was not altered"
        (get-in doc-after [:data :luokitus :energiatehokkuusluvunYksikko :value]) => "kWh/m2"))

    (get-in doc-after [:data :rakennusnro :value]) => "001"
    (get-in doc-after [:data :manuaalinen_rakennusnro :value]) => ss/blank?
    (get-in doc-after [:data :valtakunnallinenNumero :value]) => "481123123R"
    (count (get-in doc-after [:data :huoneistot])) => 21
    (get-in doc-after [:data :kaytto :kayttotarkoitus :value]) => "039 muut asuinkerrostalot"
    (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"

    (fact "KRYSP data is stored in source value field"
      (get-in doc-before [:data :mitat :tilavuus :sourceValue]) => nil
      (get-in doc-after [:data :mitat :tilavuus :sourceValue]) => "8240"
      (get-in doc-after [:data :mitat :tilavuus :value]) => "8240")

    (fact "Merging ID only"
      (let [building-id-2 (:buildingId (second (:data building-info)))
            _ (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id-2 :path "buildingId" :overwrite false) => ok?
            merged-app (query-application pena application-id)
            doc-after-2 (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]

        (fact "kayttotarkoitus remains the same"
          (get-in doc-after-2 [:data :kaytto :kayttotarkoitus :value]) => "039 muut asuinkerrostalot")

        (fact "ID has changed"
          (get-in doc-after-2 [:data :valtakunnallinenNumero :value]) => "478123123J"
          (get-in doc-after-2 [:data :rakennusnro :value]) => "002"
          (get-in doc-after-2 [:data :manuaalinen_rakennusnro :value]) => ss/blank?)))))

(fact* "Merging building information from KRYSP succeeds even if document schema does not have place for all the info"
  (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "purkaminen")
        app (query-application pena application-id)
        doc (domain/get-document-by-name app "purkaminen")
        building-info (command pena :get-building-info-from-wfs :id application-id) => ok?
        building-id (:buildingId (first (:data building-info)))
        resp (command pena :merge-details-from-krysp :id application-id :documentId (:id doc) :collection "documents" :buildingId building-id :path "buildingId" :overwrite true) => ok?
        merged-app (query-application pena application-id)
        doc-after (domain/get-document-by-name merged-app "purkaminen")]
    (get-in doc-after [:data :mitat :kokonaisala :source]) => "krysp"
    (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"))

(facts "Facts about update operation description"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application (query-application pena application-id)
        op (:primaryOperation application)
        test-desc "Testdesc"]
    (fact "operation desc is empty" (-> op :description empty?) => truthy)
    (command pena :update-op-description :id application-id :op-id (:id op) :desc test-desc :collection "operations")
    (let [updated-app (query-application pena application-id)
          updated-op (:primaryOperation updated-app)]
      (fact "description is set" (:description updated-op) => test-desc))))

(facts "Changinging application location"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application    (query-application pena application-id)]

    (fact "applicant should be able to change location when state is draft"
      (:state application) => "draft"
      (command pena :change-location :id application-id
                                     :x (-> application :location :x) - 1
                                     :y (-> application :location :y) + 1
                                     :address (:address application) :propertyId (:propertyId application)) => ok?)

    ; applicant submits and authority gives verdict
    (command pena :submit-application :id application-id)
    (command sonja :check-for-verdict :id application-id)

    (fact "applicant should not be authorized to change location anymore"
      (command pena :change-location :id application-id
                                     :x (-> application :location :x) - 1
                                     :y (-> application :location :y) + 1
                                     :address (:address application) :propertyId (:propertyId application)) => fail?)

    (fact "authority should still be authorized to change location"
      (command sonja :change-location :id application-id
                                      :x (-> application :location :x) - 1
                                      :y (-> application :location :y) + 1
                                      :address (:address application) :propertyId (:propertyId application)) => ok?)))

(fact "Authority can access drafts, but can't use most important commands"
  (let [id (create-app-id pena)
        app (query-application sonja id)
        actions (:actions (query sonja :allowed-actions :id id))
        denied-actions #{:delete-attachment :delete-attachment-version :upload-attachment :change-location
                         :new-verdict-draft :create-attachments :remove-document-data :remove-doc :update-doc
                         :reject-doc :approve-doc :stamp-attachments :create-task :cancel-application-authority
                         :add-link-permit :set-tos-function-for-application :set-tos-function-for-operation
                         :unsubscribe-notifications :subscribe-notifications :assign-application :neighbor-add
                         :change-permit-sub-type :refresh-ktj :merge-details-from-krysp :remove-link-permit-by-app-id
                         :set-attachment-type :move-attachments-to-backing-system :add-operation :remove-auth :create-doc
                         :set-company-to-document :set-user-to-document :set-current-user-to-document :approve-application
                         :submit-application :create-foreman-application}]
    app => map?
    (fact "No denied-actions in allowed-actions"
      (some denied-actions (remove nil? (map
                                         (fn [[action {:keys [ok]}]]
                                           (when ok
                                             action))
                                         actions))) => nil?)))

