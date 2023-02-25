(ns lupapalvelu.application-from-prev-permit-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.application-api] ; require local endpoints
            [lupapalvelu.application-utils :refer [person-id-masker-for-user]]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.krysp-test-util :as krysp-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.prev-permit-api :refer :all]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [net.cgrand.enlive-html :as enlive]
            [ring.util.codec :as codec]
            [sade.core :refer [def- now]]
            [sade.util :as util]
            [sade.xml :as xml]))

(def local-db-name (str "test_app-from-prev-permit-itest_" (now)))

(mount/start #'mongo/connection)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

(def example-kuntalupatunnus "14-0241-R 3")
(def example-LP-tunnus "LP-186-2014-00290")


(def example-xml (xml/parse (io/input-stream (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))))
(def example-223-xml (xml/parse (io/input-stream (io/resource "krysp/dev/verdict-rakval-223-from-kuntalupatunnus-query.xml"))))
(def example-app-info (krysp-reader/get-app-info-from-message example-xml example-kuntalupatunnus))

(def example-xml-with-jakokirjain (xml/parse (io/input-stream (io/resource "krysp/dev/verdict-rakval-with-jakokirjain.xml"))))
(def example-app-info-with-jakokirjain (krysp-reader/get-app-info-from-message example-xml-with-jakokirjain example-kuntalupatunnus))

(defn- create-app-from-prev-permit [apikey & args]
  (let [args (->> args
                  (apply hash-map)
                  (merge {:lang "fi"
                          :organizationId "186-R"  ;; Jarvenpaan rakennusvalvonta
                          :kuntalupatunnus example-kuntalupatunnus
                          :y 0
                          :x 0
                          :address ""
                          :propertyId nil
                          :authorizeApplicants true})
                  (mapcat seq))]
    (apply local-command apikey :create-application-from-previous-permit args)))

(defn- get-personal-ids-by-email
  [application email subtype]
  (let [hakija? (= subtype :hakija)
        path (if hakija?
               [:data :henkilo :yhteystiedot :email :value]
               [:data :yhteystiedot :email :value])
        document-by-email (->> (domain/get-documents-by-subtype (:documents application) subtype)
                               (filter #(= (get-in % path) email))
                               first)
        henkilotiedot     (get-in document-by-email (if hakija?
                                                      [:data :henkilo :henkilotiedot]
                                                      [:data :henkilotiedot]))]
    {:hetu (-> henkilotiedot :hetu :value)
     :ulkomainenHenkilotunnus (-> henkilotiedot :ulkomainenHenkilotunnus :value)
     :not-finnish-hetu (-> henkilotiedot :not-finnish-hetu :value)}))


(mongo/with-db local-db-name
  (facts "Creating new application based on a prev permit"
    ;; This applies to all tests in this namespace
    (against-background
     (krysp-fetch/get-application-xml-by-backend-id anything anything) => example-xml
     (krysp-fetch/get-application-xml-by-application-id anything) => example-xml)

    (fact "missing parameters"
      (create-app-from-prev-permit raktark-jarvenpaa :organizationId "")
      => (partial expected-failure? "error.missing-parameters"))

    ;; 1: hakijalla ei ole oiketta noutaa aiempaa lupaa

    (fact "applicant cannot create application"
      (create-app-from-prev-permit pena
                                   :x "6707184.319"
                                   :y "393021.589"
                                   :address "Kylykuja 3"
                                   :propertyId "18600303560003") => unauthorized?)

    ;; 2: Kannassa on ei-peruutettu hakemus, jonka organization ja verdictin kuntalupatunnus matchaa haettuihin. Palautuu lupapiste-tunnus, jolloin hakemus avataan.

    (fact "db has app that has the kuntalupatunnus in its verdict and its organization matches"
      (create-app-from-prev-permit raktark-jarvenpaa) => (contains {:ok true :id "lupis-id"})
      (provided
        (domain/get-application-as anything anything :include-canceled-apps? false) => {:id "lupis-id" :state "verdictGiven"}))

    ;; 3: jos taustajarjestelmasta ei saada xml-sisaltoa -> fail

    (fact "no xml content received from backend with the kuntalupatunnus"
      (create-app-from-prev-permit raktark-jarvenpaa) => (partial expected-failure? "error.no-previous-permit-found-from-backend")
      (provided
       (krysp-fetch/get-application-xml-by-backend-id anything anything) => nil))

    ;; 4: jos (krysp-reader/get-app-info-from-message xml kuntalupatunnus) palauttaa nillin -> (fail :error.no-previous-permit-found-from-backend)

    (fact "no application info could be parsed"
      (create-app-from-prev-permit raktark-jarvenpaa) => (partial expected-failure? "error.no-previous-permit-found-from-backend")
      (provided
       (krysp-reader/get-app-info-from-message anything anything anything) => nil))

    ;; 5: jos parametrina annettu organisaatio ja app-infosta ratkaistu organisaatio ei matchaa -> (fail :error.previous-permit-found-from-backend-is-of-different-organization)

    (fact "ids of the given and resolved organizations do not match"
      (create-app-from-prev-permit raktark-jarvenpaa) => (partial expected-failure? "error.previous-permit-found-from-backend-is-of-different-organization")
      (provided
        (krysp-reader/get-app-info-from-message anything anything anything)
        => {:municipality   "753"
            :rakennuspaikka {:propertyId "75312312341234"
                             :x          444444 :y 6666666
                             :address    "Virhekuja 12"}}))

    ;; 6: jos sanomassa ei ollut rakennuspaikkaa/kiinteistotunnusta, ja ei alunperin annettu tarpeeksi parametreja -> (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)

    (fact "no 'rakennuspaikkatieto' element in the received xml, need more info"
      (create-app-from-prev-permit raktark-jarvenpaa) => {:ok                     false
                                                          :needMorePrevPermitInfo true
                                                          :text                   "error.more-prev-app-info-needed"}
      (provided
        (krysp-reader/get-app-info-from-message anything anything anything)
        => (dissoc example-app-info :rakennuspaikka)))

    (fact "propertyId is missing"
      (create-app-from-prev-permit raktark-jarvenpaa) => {:ok   false
                                                          :text "error.previous-permit-no-propertyid"}
      (provided
        (krysp-reader/get-app-info-from-message anything anything anything)
        => (util/dissoc-in example-app-info [:rakennuspaikka :propertyId])))

    ;; 7: testaa Sonjalla, etta ei ole oikeuksia luoda hakemusta, mutta jarvenpaan viranomaisella on

    (facts "authority tests"
      (let [property-id "18600303560005"]

        (fact "authority of different municipality cannot create application"
          (create-app-from-prev-permit sonja
                                       :x "6707184.319"
                                       :y "393021.589"
                                       :address "Kylykuja 3"
                                       :propertyId property-id) => unauthorized?)

        (facts "authority of same municipality can create application"
          (mongo/with-db local-db-name
            (let [resp1       (create-app-from-prev-permit raktark-jarvenpaa
                                                           :x "6707184.319"
                                                           :y "393021.589"
                                                           :address "Kylykuja 3"
                                                           :propertyId property-id)
                  app-id      (:id resp1)
                  application (query-application local-query raktark-jarvenpaa app-id)
                  invites     (filter #(= raktark-jarvenpaa-id (get-in % [:invite :inviter :id])) (:auth application))
                  find-party  (fn [schema-name firstname]
                                (util/find-first (util/fn->> :data :henkilo :henkilotiedot :etunimi :value (= firstname))
                                                 (domain/get-documents-by-name application schema-name)))
                  owner       (->> (:documents application)
                                   (util/find-first #(some-> % :data :valtakunnallinenNumero
                                                             :value (= "100222397J")))
                                   :data :rakennuksenOmistajat :0
                                   tools/unwrapped)]

              resp1  => ok?

              (fact "Owner's personal info is included"
                owner => (contains
                           {:_selected    "henkilo"
                            :henkilo
                            (contains {:henkilotiedot (contains {:etunimi  "Matti"
                                                                 :sukunimi "Mainio"})
                                       :osoite        (contains {:katu                 "Mäennyppylänkatu 2 D 37"
                                                                 :maa                  "FIN"
                                                                 :postinumero          "09990"
                                                                 :postitoimipaikannimi "Testikaupunki"})})
                            :omistajalaji "muu yksityinen henkilö tai perikunta"}))

              (fact "verdictDate exists"
                (:verdictDate application) => pos?)

              ;; Test count of the invited emails, because invalid emails are ignored
              (fact "invites count"
                (count invites) => 3
                (count (:invites (local-query pena  :invites))) => 1
                (count (:invites (local-query mikko :invites))) => 1
                (count (:invites (local-query teppo :invites))) => 1)

              (facts "Party documents"
                (fact "hakija document count"
                  (count (domain/get-documents-by-name application "hakija-r")) => 5)
                (fact "Elmeri's invalid email address is cleared"
                  (-> (find-party "hakija-r" "Elmeri") :data :henkilo :yhteystiedot :email :value) => "")
                (fact "Maksaja Pena's invalid hetu is cleared"
                  (-> (find-party "maksaja" "Pena Jouko Tapani") :data :henkilo :henkilotiedot :hetu :value) => "")
                (fact "Mikko's invalid postal code is cleared"
                  (-> (find-party "hakija-r" "Mikko Ilmari") :data :henkilo :osoite :postinumero :value) => ""))

              ;; Cancel the application and re-call 'create-app-from-prev-permit' -> should open application with different ID
              (fact "fetching prev-permit again after canceling the previously fetched one"
                (local-command raktark-jarvenpaa :change-application-state
                               :id (:id application)
                               :state "appealed") => ok?
                (local-command raktark-jarvenpaa :cancel-application
                               :id (:id application)
                               :text "Se on peruutus ny!"
                               :lang "fi")
                (let [resp2 (create-app-from-prev-permit raktark-jarvenpaa
                                                         :x "6707184.319"
                                                         :y "393021.589"
                                                         :address "Kylykuja 3"
                                                         :propertyId property-id)
                      =>    ok?]
                  (:id resp2) =not=> app-id)))))))


    ;; 9: Sanoman kaikilta hakijoilta puuttuu henkilo- ja yritystiedot
    ;; Pitaisi onnistua, ks. LPK-543.
    ;; Viimeinen testi omalla kuntalupatunnuksella.

    (fact "no proper applicants in the xml message"
      (create-app-from-prev-permit raktark-jarvenpaa
                                   :x "6707184.319"
                                   :y "393021.589"
                                   :address "Kylykuja 3"
                                   :kuntalupatunnus "14-0241-R 2"
                                   :propertyId "18600303560004") => ok?
      (provided
        (krysp-reader/get-app-info-from-message anything anything anything)
        => (-> example-app-info
               (assoc :kuntalupatunnus "14-0241-R 2")
               (update-in [:hakijat] (fn [hakijat] (map #(dissoc % :henkilo :yritys) hakijat)))))))


    (facts "Applicants invitation should be selectable"
      (against-background
        (krysp-fetch/get-application-xml-by-backend-id anything anything) => example-xml
        (krysp-fetch/get-application-xml-by-application-id anything) => example-xml)
      (fact "When not authorise applicants - there should be only one authorised"
        (->> (:id (create-app-from-prev-permit raktark-jarvenpaa
                                              :kuntalupatunnus "14-0241-R 10"
                                              :authorizeApplicants false))
            (query-application local-query raktark-jarvenpaa)
            (:auth)
            (count)) => 1 ;; owner of the application
        (provided
          (krysp-reader/get-app-info-from-message anything anything anything)
          => example-app-info))

      (fact "When not authorise applicants - there should be five applicant documents"
        (let [application (->> (:id (create-app-from-prev-permit raktark-jarvenpaa
                                                                 :kuntalupatunnus "14-0241-R 11"
                                                                 :authorizeApplicants false))
                               (query-application local-query raktark-jarvenpaa))]
            (count (domain/get-documents-by-subtype (:documents application) :hakija))) => 5
        (provided
          (krysp-reader/get-app-info-from-message anything anything anything)
          => example-app-info))

      (fact "When authorise applicants"
       (->> (:id (create-app-from-prev-permit raktark-jarvenpaa
                                              :kuntalupatunnus "14-0241-R 12"
                                              :authorizeApplicants true))
            (query-application local-query raktark-jarvenpaa)
            (:auth)
            (count)) => 4
       (provided
         (krysp-reader/get-app-info-from-message anything anything anything)
         => example-app-info)))

    (facts "Application from previous permit contains correct data"
      (fact "reviews are fetched"
        (->> (:id (create-app-from-prev-permit raktark-jarvenpaa))
             (query-application local-query raktark-jarvenpaa)
             (:tasks)
             (count)) => 14)
      (fact "state is fetched"
        (->> (:id (create-app-from-prev-permit raktark-jarvenpaa))
             (query-application local-query raktark-jarvenpaa)
             (:state)) => "constructionStarted"))
  (let [app-id   (:id (create-and-submit-local-application pena
                                                           :propertyId jarvenpaa-property-id
                                                           :operation "pientalo"))
        xml      (enlive/at example-xml
                            [:rakval:luvanTunnisteTiedot :yht:kuntalupatunnus]
                            (enlive/after (krysp-util/build-lp-tunnus-xml app-id)))
        app-info (krysp-reader/get-app-info-from-message xml example-kuntalupatunnus)]
    (against-background [(krysp-reader/get-app-info-from-message anything anything anything)
                         => app-info
                         (krysp-fetch/get-application-xml-by-backend-id anything anything) => xml
                         (domain/get-application-as {:organization "186-R" :_id app-id}
                                                    anything
                                                    :include-canceled-apps? true) => {:id app-id :state "sent"}
                         (domain/get-application-as anything
                                                    anything
                                                    :include-canceled-apps? false) => nil]
                        (fact "Already existing application with the same app-id as in the xml"
                          (create-app-from-prev-permit raktark-jarvenpaa
                                                       :kuntalupatunnus example-kuntalupatunnus
                                                       :authorizeApplicants false)
                          => (contains {:id app-id})))))

(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))
(mongo/with-db local-db-name
               (facts "hetu and ulkomainen hetu are mapped to documents correctly. "
                 ;not-finnish-hetu is set to true iff hetu does not exists in xml but ulkomainenHenkilotunnus does
                 (against-background
                   [(krysp-fetch/get-application-xml-by-backend-id anything anything) => example-223-xml
                    (person-id-masker-for-user anything anything) => identity]
                   (fact ""
                     (let [application (->> (:id (create-app-from-prev-permit raktark-jarvenpaa))
                                            (query-application local-query raktark-jarvenpaa))]
                       (get-personal-ids-by-email application "elmeri@example.com" :hakija)
                       => {:hetu "040664-140E", :ulkomainenHenkilotunnus "567", :not-finnish-hetu false}
                       (get-personal-ids-by-email application "mikko@example.com" :hakija)
                       => {:hetu "" :not-finnish-hetu true :ulkomainenHenkilotunnus "01234599"}
                       (get-personal-ids-by-email application "esa@example.com" :hakija)
                       => {:hetu "130227-262D" :not-finnish-hetu false :ulkomainenHenkilotunnus ""}
                       (get-personal-ids-by-email application "pena@example.com" :hakija)
                       => {:hetu "" :not-finnish-hetu false :ulkomainenHenkilotunnus ""}
                       (get-personal-ids-by-email application "louhi@example.com" :suunnittelija)
                       => {:hetu "151139-935S" :not-finnish-hetu false :ulkomainenHenkilotunnus "99"}
                       (get-personal-ids-by-email application "jippo@example.com" :suunnittelija)
                       => {:hetu "300129-692Y" :not-finnish-hetu false :ulkomainenHenkilotunnus nil}
                       (get-personal-ids-by-email application "jamppa@example.com" :suunnittelija)
                       => {:hetu nil :not-finnish-hetu true :ulkomainenHenkilotunnus "1995"})))))

(facts "Parse street addresses correctly"
  (fact "should not add whitespace between osoitenumero and jakokirjain"
    (-> example-app-info-with-jakokirjain :rakennuspaikka :address) => "Vuohikatu 5b"))

(facts "Application from kuntalupatunnus via rest API"      ; DOES NOT USE LOCAL QUERIES
  (let [rest-address (str (server-address) "/rest/get-lp-id-from-previous-permit")
        params       {:query-params {"kuntalupatunnus" example-kuntalupatunnus}
                      :basic-auth   ["jarvenpaa-backend" "jarvenpaa"]}]
    (against-background [(before :facts (apply-remote-minimal))]
                        (fact "should create new LP application if kuntalupatunnus doesn't match existing app"
                          (let [response  (http-get rest-address params)
                                resp-body (:body (decode-response response))]
                            response => http200?
                            resp-body => ok?
                            (keyword (:text resp-body)) => :created-new-application
                            (let [application (query-application raktark-jarvenpaa (:id resp-body))]
                              (:opened application) => truthy
                              (:verdictDate application) => pos?)))

                        (facts "should return the LP application if the kuntalupatunnus matches an existing app"
                          (fact "Pate verdict"
                            (let [{app-id :id} (create-and-submit-application pena :propertyId jarvenpaa-property-id)
                                  _            (give-legacy-verdict raktark-jarvenpaa app-id
                                                                    :kuntalupatunnus example-kuntalupatunnus)
                                  response     (http-get rest-address params)
                                  resp-body    (:body (decode-response response))]
                              response => http200?
                              resp-body => ok?
                              (keyword (:text resp-body)) => :already-existing-application))
                          (fact "Pate verdict draft, draft verdict is not regarded"
                            (let [{app-id :id}      (create-and-submit-application pena :propertyId jarvenpaa-property-id)
                                  {vid :verdict-id} (command raktark-jarvenpaa :new-legacy-verdict-draft
                                                             :id app-id)
                                  _                 (fill-verdict raktark-jarvenpaa app-id vid
                                                                  :kuntalupatunnus example-kuntalupatunnus)
                                  response          (http-get rest-address params)
                                  resp-body         (:body (decode-response response))]
                              response => http200?
                              resp-body => ok?
                              (keyword (:text resp-body)) => :created-new-application))
                          (fact "Check for verdict"
                            (let [{app-id :id} (create-and-submit-application pena :propertyId jarvenpaa-property-id)
                                  _            (override-krysp-xml jarvenpaa "186-R" :R
                                                                   [{:selector [:yht:kuntalupatunnus]
                                                                     :value    (codec/url-encode example-kuntalupatunnus)}])
                                  _            (command raktark-jarvenpaa :check-for-verdict :id app-id)
                                  response     (http-get rest-address params)
                                  resp-body    (:body (decode-response response))]
                              response => http200?
                              resp-body => ok?
                              (keyword (:text resp-body)) => :already-existing-application)))

                        (fact "create new LP app if kuntalupatunnus matches existing app in another organization"
                          (let [{app-id :id} (create-and-submit-application pena :propertyId sipoo-property-id)
                                _            (give-legacy-verdict sonja app-id
                                                                  :kuntalupatunnus example-kuntalupatunnus)
                                response     (http-get rest-address params)
                                resp-body    (:body (decode-response response))]
                            response => http200?
                            resp-body => ok?
                            (keyword (:text resp-body)) => :created-new-application)))))
