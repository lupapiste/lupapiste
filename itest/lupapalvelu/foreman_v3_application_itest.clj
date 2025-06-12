(ns lupapalvelu.foreman-v3-application-itest
  (:require [lupapalvelu.application.foreman :as foreman]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.foreman-itest-util :as foreman-util]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set]]
            [sade.core]
            [sade.date :as date :refer [timestamp]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn- create-and-invite-foreman-command [apikey application-id role email user created-date]
  (with-redefs [sade.core/now (constantly created-date)]
    (facts "create-and-invite-foreman"
      (let [invite-resp (foreman-util/create-and-invite-foreman-command apikey application-id role email user)
            app         (query-application apikey application-id)
            docs        (domain/get-documents-by-name app :tyonjohtaja-v3)
            foreman-doc (util/find-first #(-> % :data :kuntaRoolikoodi :value (= role)) docs)]
        invite-resp => ok?
        (command apikey :update-doc :id application-id :doc (:id foreman-doc)
                 :updates [["patevyysvaatimusluokka" "A"]
                           ["vastattavatTyotehtavat.sisapuolinenKvvTyo" true]
                           ["patevyys-tyonjohtaja.valmistumisvuosi" "1942"]
                           ["patevyys-tyonjohtaja.valvottavienKohteidenMaara" "10"]
                           ["patevyys-tyonjohtaja.kokemusvuodet" "10"]
                           ["patevyys-tyonjohtaja.koulutusvalinta" "insinööri"]]) => ok?))))

(defn- create-verdict
  [authority app-id category]
  (let [template-id (->> pate-fixture/verdict-templates-r
                         (filter #(= category (:category %)))
                         (first)
                         :id)
        resp        (command authority :new-pate-verdict-draft :id app-id :template-id template-id)]
    (fact "verdict created"
      resp => ok?)
    (:verdict-id resp)))

(defn- fill-verdict-fields
  [authority app-id verdict-id verdict-date]
  (facts "can fill verdict"
    (command authority :edit-pate-verdict :id app-id :verdict-id verdict-id
             :path [:automatic-verdict-dates] :value true) => ok?
    (command authority :edit-pate-verdict :id app-id :verdict-id verdict-id
             :path [:verdict-date] :value verdict-date) => ok?
    (command authority :edit-pate-verdict :id app-id :verdict-id verdict-id
             :path [:verdict-code] :value "hyvaksytty") => ok?
    (command authority :edit-pate-verdict :id app-id :verdict-id verdict-id
             :path [:giver-first-name] :value "Pena") => ok?
    (command authority :edit-pate-verdict :id app-id :verdict-id verdict-id
             :path [:giver-last-name] :value "Päättäjä") => ok?))

(defn- publish-verdict
  [authority app-id verdict-id]
  (fact "verdict published"
    (command authority :publish-pate-verdict :id app-id :verdict-id verdict-id) => ok?))

(defn create-and-publish-verdict
  [authority app-id verdict-date category]
  (let [verdict-id (create-verdict authority app-id category)]
    (fill-verdict-fields authority app-id verdict-id verdict-date)
    (publish-verdict authority app-id verdict-id)))

(defn get-foreman-v3-xml
  [app doc-id timestamp]
  (with-redefs [lupapalvelu.itest-util/get-files-from-sftp-server? false]
    (get-valid-krysp-xml app
                         (fn [^File file]
                           (let [filename (.toString file)]
                             (ss/ends-with? filename (format "%s_%s_%s.xml"
                                                              (:id app)
                                                              doc-id
                                                              timestamp)))))))

(defn fix-remote-url
  "Make sure the URLs point to us, in all environments (local vs CI)"
  []
  (let [krysp-url (str (server-address) "/dev/krysp")]
    (mongo/update :organizations {} {$set {:krysp.R.url krysp-url}})))

(defn terminate-foreman-v3
  [authority app-id foreman-doc-id request-date ended]
  (fact "Terminate foreman"
    (command authority :terminate-foreman-v3 :id app-id
             :foreman-doc-id foreman-doc-id
             :lang "fi"
             :reason "Terminated!"
             :ended ended) => ok?)
  (fact "Termination data updated after terminating"
    (let [foreman-app (query-application authority app-id)
          doc         (domain/get-document-by-id foreman-app foreman-doc-id)]
      (:foremanTermination doc)
      => (just {:state      "confirmed"
                :reason     "Terminated!"
                :request-ts request-date
                :requester  (contains {:id (min-util/id-for-key authority)})
                :ended      ended
                :started    pos?
                :confirmer  (contains {:id (min-util/id-for-key authority)})}))))

(defn fill-applicant-doc [apikey app-id doc-id]
  (command apikey :set-user-to-document :id app-id :documentId doc-id :userId pena-id :path "henkilo") => ok?
  (command apikey :set-current-user-to-document :id app-id :documentId doc-id :path "henkilo") => ok?
  (command apikey :set-company-to-document :id app-id :documentId doc-id :companyId "esimerkki" :path "yritys") => ok?
  (let [user (usr/get-user-by-id pena-id)]
    (command apikey :update-doc :id app-id :doc doc-id
             :updates [["yritys.yhteyshenkilo.henkilotiedot.etunimi" (:firstName user)]
                       ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" (:lastName user)]
                       ["yritys.yhteyshenkilo.yhteystiedot.email" (:email user)]
                       ["yritys.yhteyshenkilo.yhteystiedot.puhelin" (:phone user)]]) => ok?))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-mock-laundry)
  (facts "New foreman KuntaGML generation"
    (env/with-feature-value :new-foreman-invite true
      (fix-remote-url)
      (with-local-actions
        (fact "Enable pate sftp"
          (command admin :update-organization :permitType "R" :municipality "753" :pateEnabled true :pateSftp true) => ok?)
        (let [applicant                    pena
              authority                    sonja
              {foreman-app-id :id
               :as            foreman-app} (create-and-submit-application applicant
                                                                          :propertyId sipoo-property-id
                                                                          :address "Worker Way"
                                                                          :operation "pientalo")
              _                            (fill-applicant-doc applicant foreman-app-id (:id (domain/get-applicant-document (:documents foreman-app))))
              submit-date                  (timestamp "2024-01-07")
              _                            (create-and-invite-foreman-command authority foreman-app-id "KVV-työnjohtaja" "" pena-id submit-date)
              _                            (create-and-invite-foreman-command authority foreman-app-id "IV-työnjohtaja" "" pena-id submit-date)
              approve-date                 (timestamp "2024-01-08")
              verdict-date                 (timestamp "2024-01-09")
              termination-date             (timestamp "2024-01-10")
              ended-date                   (timestamp "2024-01-11")
              resend-approved-date         (timestamp "2025-01-08")
              resend-termination-date      (timestamp "2025-01-01")
              foreman-app                  (query-application applicant foreman-app-id)
              docs                         (domain/get-documents-by-name foreman-app :tyonjohtaja-v3)
              doc                          (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "KVV-työnjohtaja")) docs)
              foreman-doc-id               (:id doc)]

          (with-redefs [sade.core/now (constantly verdict-date)]
            (create-and-publish-verdict authority foreman-app-id verdict-date "r"))

          (fact "check tasks after verdict"
            (let [{:keys [tasks] :as app} (query-application applicant foreman-app-id)
                  tj-task                 (util/find-by-key :taskname "Vastaava työnjohtaja" tasks)
                  docs                    (domain/get-documents-by-name app :tyonjohtaja-v3)
                  tj-doc-id               (:id (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "vastaava työnjohtaja")) docs))]
              (fact "task for vastaava työnjohtaja exists"
                (some? tj-task) => true)
              (fact "doc for vastaava työnjohtaja exists"
                (some? tj-doc-id) => true)
              (fact "applicant may not remove foreman docs"
                (allowed? applicant :remove-doc :id foreman-app-id :docId tj-doc-id) => :error.not-allowed-to-remove-document)
              (fact "authority may remove an empty foreman doc, even if it is pre-verdict"
                (allowed? authority :remove-doc :id foreman-app-id :docId tj-doc-id) => true)

              (fact "assign pena as vastaava työnjohtaja"
                (command pena :set-user-to-document {:id         foreman-app-id
                                                     :documentId tj-doc-id
                                                     :userId     pena-id
                                                     :path       ""}) => ok?

                (fact "authority may not remove a filled pre-verdict foreman doc"
                  (allowed? authority :remove-doc :id foreman-app-id :docId tj-doc-id) => :error.document.post-verdict-deletion))))

          (fact "Submit foreman"
            (with-redefs [sade.core/now (constantly submit-date)]
              (command applicant :foreman-consent :id foreman-app-id :foreman-doc-id foreman-doc-id :consent true :with-lang "fi") => ok?
              (command applicant :declare-foreman :id foreman-app-id :foreman-doc-id foreman-doc-id :with-lang "fi")) => ok?)

          (fact "Acknowledge foreman"
            (with-redefs [sade.core/now (constantly approve-date)]
              (command authority :acknowledge-foreman :id foreman-app-id :foreman-doc-id foreman-doc-id :with-lang "fi")) => ok?)

          (fact "Extract email from foreman v3 document"
            (let [foreman-app (query-application applicant foreman-app-id)
                  doc         (domain/get-document-by-id foreman-app foreman-doc-id)]
              (foreman/tj-document->email doc) => "pena@example.com"))

          (fact "When an 'acknowledged' state change is sent, a 'vireillä' state change with the declaration/submission date should also be created"
            (-> (query-application applicant foreman-app-id)
                (get-foreman-v3-xml foreman-doc-id approve-date)
                xml/xml->edn
                :Rakennusvalvonta
                :rakennusvalvontaAsiatieto
                :RakennusvalvontaAsia
                :kasittelynTilatieto)
            => [{:Tilamuutos {:pvm         (date/xml-date submit-date)
                              :tila        "vireillä"
                              :kasittelija {:henkilo {:nimi {:etunimi  "Sonja"
                                                             :sukunimi "Sibbo"}}}}}
                {:Tilamuutos {:pvm         (date/xml-date approve-date)
                              :tila        "lupa hyväksytty"
                              :kasittelija {:henkilo {:nimi {:etunimi  "Sonja"
                                                             :sukunimi "Sibbo"}}}}}])
          (fact "Correct KuntaGML message generated when re-sending a foreman message"
            (with-redefs [sade.core/now (constantly resend-approved-date)]
              (command sonja :send-foreman-message :id foreman-app-id :foreman-doc-id foreman-doc-id) => ok?
              (-> (query-application applicant foreman-app-id)
                  (get-foreman-v3-xml foreman-doc-id resend-approved-date)
                  xml/xml->edn
                  :Rakennusvalvonta
                  :rakennusvalvontaAsiatieto
                  :RakennusvalvontaAsia
                  :kayttotapaus) => "Uuden työnjohtajan nimeäminen"))

          (fact "Enable generation of KuntaGML foreman termination messages"
            (command admin :set-organization-boolean-attribute
                     :attribute "foreman-termination-krysp-enabled"
                     :enabled true
                     :organizationId "753-R") => ok?)

          (fact "Terminate foreman v3"
            (with-redefs [sade.core/now (constantly termination-date)]
              (terminate-foreman-v3 authority foreman-app-id foreman-doc-id termination-date ended-date)))

          (fact "Terminating foreman v3 creates the correct KuntaGML message"
            (let [foreman-app (query-application applicant foreman-app-id)
                  xml         (get-foreman-v3-xml foreman-app foreman-doc-id termination-date)
                  as-edn      (xml/xml->edn xml)]
              (fact "kasittelynTilatieto"
                (-> as-edn
                    :Rakennusvalvonta
                    :rakennusvalvontaAsiatieto
                    :RakennusvalvontaAsia
                    :kasittelynTilatieto)
                => [{:Tilamuutos {:pvm         (date/xml-date submit-date)
                                  :tila        "vireillä"
                                  :kasittelija {:henkilo {:nimi {:etunimi  "Sonja"
                                                                 :sukunimi "Sibbo"}}}}}
                    {:Tilamuutos {:pvm         (date/xml-date approve-date)
                                  :tila        "lupa hyväksytty"
                                  :kasittelija {:henkilo {:nimi {:etunimi  "Sonja"
                                                                 :sukunimi "Sibbo"}}}}}
                    {:Tilamuutos {:pvm         (date/xml-date termination-date)
                                  :tila        "vireillä"
                                  :kasittelija {:henkilo {:nimi {:etunimi  "Sonja"
                                                                 :sukunimi "Sibbo"}}}}}])
              (fact "luvanTunnisteTiedot"
                (-> as-edn
                    :Rakennusvalvonta
                    :rakennusvalvontaAsiatieto
                    :RakennusvalvontaAsia
                    :luvanTunnisteTiedot)
                => {:LupaTunnus {:muuTunnustieto {:MuuTunnus {:tunnus   (str foreman-doc-id "-TJ")
                                                              :sovellus "Lupapiste"}}
                                 :saapumisPvm    (date/xml-date submit-date)}})
              (fact "viitelupatieto"
                (-> as-edn
                    :Rakennusvalvonta
                    :rakennusvalvontaAsiatieto
                    :RakennusvalvontaAsia
                    :viitelupatieto)
                => {:LupaTunnus {:muuTunnustieto {:MuuTunnus {:tunnus   foreman-app-id
                                                              :sovellus "Lupapiste"}}
                                 :viittaus       "edellinen rakennusvalvonta-asia"}})
              (fact "osapuolettieto"
                (-> as-edn
                    :Rakennusvalvonta
                    :rakennusvalvontaAsiatieto
                    :RakennusvalvontaAsia
                    :osapuolettieto)
                => {:Osapuolet {:osapuolitieto    {:Osapuoli {:VRKrooliKoodi                 "hakija"
                                                              :henkilo                       {:henkilotunnus    "010203-040A"
                                                                                              :nimi             {:etunimi  "Pena"
                                                                                                                 :sukunimi "Panaani"}
                                                                                              :osoite           {:osoitenimi           {:teksti "Paapankuja 12"}
                                                                                                                 :postinumero          "92620"
                                                                                                                 :postitoimipaikannimi "Piippola"
                                                                                                                 :valtioKansainvalinen "FIN"
                                                                                                                 :valtioSuomeksi       "Suomi"}
                                                                                              :puhelin          "0102030405"
                                                                                              :sahkopostiosoite "pena@example.com"}
                                                              :kuntaRooliKoodi               "Rakennusvalvonta-asian hakija"
                                                              :suoramarkkinointikieltoKytkin "false"
                                                              :turvakieltoKytkin             "false"}}
                                :tyonjohtajatieto {:Tyonjohtaja {:tyonjohtajaRooliKoodi    "KVV-työnjohtaja"
                                                                 ;; see mapping kuntaRoolikoodi-to-vrkRooliKoodi
                                                                 :VRKrooliKoodi            "työnjohtaja"
                                                                 :henkilo                  {:henkilotunnus    "010203-040A"
                                                                                            :nimi             {:etunimi  "Pena"
                                                                                                               :sukunimi "Panaani"}
                                                                                            :osoite           {:osoitenimi           {:teksti "Paapankuja 12"}
                                                                                                               :postinumero          "92620"
                                                                                                               :postitoimipaikannimi "Piippola"
                                                                                                               :valtioKansainvalinen "FIN"
                                                                                                               :valtioSuomeksi       "Suomi"}
                                                                                            :puhelin          "0102030405"
                                                                                            :sahkopostiosoite "pena@example.com"}
                                                                 ;; Note that we don't report the start date..
                                                                 :paattymisPvm             (date/xml-date ended-date)
                                                                 :tyonjohtajaHakemusKytkin "false"
                                                                 :vainTamaHankeKytkin      "false"
                                                                 :kokemusvuodet            "10"
                                                                 :koulutus                 "insinööri"
                                                                 :patevyysvaatimusluokka   "A"
                                                                 :vaadittuPatevyysluokka   "A"
                                                                 :valmistumisvuosi         "1942"
                                                                 :vastattavaTyotieto       {:VastattavaTyo {:vastattavaTyo "Sisäpuolinen KVV-työ"}}}}}})

              (fact "lisatiedot"
                (-> as-edn
                    :Rakennusvalvonta
                    :rakennusvalvontaAsiatieto
                    :RakennusvalvontaAsia
                    :lisatiedot)
                => {:Lisatiedot {:asioimiskieli "suomi"}})

              (fact "kayttotapaus"
                (-> as-edn
                    :Rakennusvalvonta
                    :rakennusvalvontaAsiatieto
                    :RakennusvalvontaAsia
                    :kayttotapaus) => "Työnjohtajan irtisanominen")))

          (fact "Foreman v3 document state changes to acknowledged when terminated from declared state"
            (let [foreman-doc (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]

              (with-redefs [sade.core/now (constantly approve-date)]
                (command applicant :foreman-consent :id foreman-app-id :foreman-doc-id (:id foreman-doc) :consent true :with-lang "fi") => ok?
                (command authority :declare-foreman :id foreman-app-id :foreman-doc-id (:id foreman-doc) :with-lang "fi") => ok?)

              (with-redefs [sade.core/now (constantly termination-date)]
                (terminate-foreman-v3 authority foreman-app-id (:id foreman-doc) termination-date ended-date)

                (let [app-after-termination (query-application applicant foreman-app-id)
                      terminated-foreman    (domain/get-document-by-id app-after-termination (:id foreman-doc))
                      state-change-log      (->> (:changeLog terminated-foreman)
                                                 last)]
                  (:state terminated-foreman) => "acknowledged"
                  (:field state-change-log)   => "state"
                  (:value state-change-log)   => "acknowledged"
                  (:user state-change-log)    => truthy
                  (:ts state-change-log)      => termination-date))

              (fact "Correct KuntaGML message generated when re-sending a terminated foreman message"
                (with-redefs [sade.core/now (constantly resend-termination-date)]
                  (command sonja :send-foreman-message :id foreman-app-id :foreman-doc-id foreman-doc-id) => ok?
                  (-> (query-application applicant foreman-app-id)
                      (get-foreman-v3-xml foreman-doc-id resend-termination-date)
                      xml/xml->edn
                      :Rakennusvalvonta
                      :rakennusvalvontaAsiatieto
                      :RakennusvalvontaAsia
                      :kayttotapaus) => "Työnjohtajan irtisanominen"))

              (fact "Disable generation of KuntaGML message"
                (command admin :set-organization-boolean-attribute
                         :attribute "foreman-termination-krysp-enabled"
                         :enabled false
                         :organizationId "753-R") => ok?))))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "foreman-submit-settings"
    (let [app (create-and-submit-application mikko :operation "kerrostalo-rivitalo")]
      (fact "Default settings are returned by default"
        (query sonja :get-foreman-submit-settings :id (:id app))
        => {:ok           true
            :application  {:approve false :give-verdict true}
            :notification {:acknowledge true :approve false :give-verdict true}})
      (fact "Organization admin can change settings"
        (command sipoo :set-foreman-submit-setting :path {:application {:approve true}} :enabled true :organizationId "753-R") => ok?
        (query sonja :get-foreman-submit-settings :id (:id app)) =>
        {:ok           true
         :application  {:approve true :give-verdict true}
         :notification {:acknowledge true :approve false :give-verdict true}}))))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-mock-laundry)
  (facts "Foreman type change from notification to application"
    (env/with-feature-value :new-foreman-invite true
      (fix-remote-url)
      (with-local-actions
        (fact "Enable pate sftp"
          (command admin :update-organization :permitType "R" :municipality "753" :pateEnabled true :pateSftp true) => ok?)
        (let [applicant                    pena
              authority                    sonja
              {foreman-app-id :id
               :as            foreman-app} (create-and-submit-application applicant
                                                                          :propertyId sipoo-property-id
                                                                          :address "Worker Way"
                                                                          :operation "pientalo")
              _                            (fill-applicant-doc applicant foreman-app-id (:id (domain/get-applicant-document (:documents foreman-app))))
              submit-date                  (timestamp "2024-01-07")
              _                            (create-and-invite-foreman-command authority foreman-app-id "KVV-työnjohtaja" "" pena-id submit-date)
              approve-date                 (timestamp "2024-01-08")
              verdict-date                 (timestamp "2024-01-09")
              foreman-app                  (query-application applicant foreman-app-id)
              docs                         (domain/get-documents-by-name foreman-app :tyonjohtaja-v3)
              doc                          (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "KVV-työnjohtaja")) docs)
              foreman-doc-id               (:id doc)]

          (with-redefs [sade.core/now (constantly verdict-date)]
            (create-and-publish-verdict authority foreman-app-id verdict-date "r"))

          (with-redefs [sade.core/now (constantly approve-date)]
            (fact "Change declared foreman documents type"
              (command applicant :foreman-consent :id foreman-app-id :foreman-doc-id foreman-doc-id :consent true :with-lang "fi")
              (command applicant :declare-foreman :id foreman-app-id :foreman-doc-id foreman-doc-id :with-lang "fi")
              (command authority :change-foreman-doc-type :id foreman-app-id :foreman-doc-id foreman-doc-id :foreman-type "application" :with-lang "fi") => ok?))

          (fact "Foreman type has changed to application"
            (let [foreman-app (query-application applicant foreman-app-id)
                  doc         (domain/get-document-by-id foreman-app foreman-doc-id)]
              (:type doc) => "application"
              (:state doc) => "submitted"
              (:started doc) => nil?))

          (with-redefs [sade.core/now (constantly approve-date)]
            (fact "Foreman type has changed back to notification"
              (command authority :change-foreman-doc-type :id foreman-app-id :foreman-doc-id foreman-doc-id :foreman-type "notification" :with-lang "fi") => ok?

              (let [foreman-app (query-application applicant foreman-app-id)
                    doc         (domain/get-document-by-id foreman-app foreman-doc-id)]
                (:type doc) => "notification"
                (:state doc) => "declared"
                (:started doc) => approve-date)))

          (with-redefs [sade.core/now (constantly approve-date)]
            (fact "Can not change foreman type after it has been approved"
              (command sipoo :set-foreman-submit-setting :enabled true :organizationId "753-R" :path {:application {:approve true}}) => ok?
              (command authority :change-foreman-doc-type :id foreman-app-id :foreman-doc-id foreman-doc-id :foreman-type "application" :with-lang "fi")
              (command authority :approve-foreman :id foreman-app-id :foreman-doc-id foreman-doc-id :with-lang "fi")
              (command authority :change-foreman-doc-type :id foreman-app-id :foreman-doc-id foreman-doc-id :foreman-type "notification" :with-lang "fi") => (partial expected-failure? "error.foreman.wrong-state"))))))))
