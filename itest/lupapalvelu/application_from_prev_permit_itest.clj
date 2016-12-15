(ns lupapalvelu.application-from-prev-permit-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [sade.core :refer [def- now]]
            [sade.xml :as xml]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application-api] ; require local endpoints
            [lupapalvelu.prev-permit-api :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.itest-util :as util]))

(def local-db-name (str "test_app-from-prev-permit-itest_" (now)))

(mongo/connect!)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

(def example-kuntalupatunnus "14-0241-R 3")
(def example-LP-tunnus "LP-186-2014-00290")


(def example-xml (xml/parse (slurp (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))))
(def example-app-info (krysp-reader/get-app-info-from-message example-xml example-kuntalupatunnus))

(defn- create-app-from-prev-permit [apikey & args]
  (let [args (->> args
                  (apply hash-map)
                  (merge {:lang "fi"
                          :organizationId "186-R"  ;; Jarvenpaan rakennusvalvonta
                          :kuntalupatunnus example-kuntalupatunnus
                          :y 0
                          :x 0
                          :address ""
                          :propertyId nil})
                  (mapcat seq))]
    (apply local-command apikey :create-application-from-previous-permit args)))


(mongo/with-db local-db-name
  (facts "Creating new application based on a prev permit"
    ;; This applies to all tests in this namespace
    (against-background
     (krysp-fetch/get-application-xml-by-backend-id anything anything) => example-xml
     (krysp-fetch/get-application-xml-by-application-id anything) => example-xml)

    (fact "missing parameters"
      (create-app-from-prev-permit raktark-jarvenpaa :organizationId "") => (partial expected-failure? "error.missing-parameters"))

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
       (krysp-reader/get-app-info-from-message anything anything) => nil))

    ;; 5: kiinteistotunnusta ei tule sanomassa

    (fact "propertyId is missing"
      (create-app-from-prev-permit raktark-jarvenpaa) => (partial expected-failure? "error.previous-permit-no-propertyid")
      (provided
       (krysp-reader/get-app-info-from-message anything anything) => (assoc example-app-info :municipality nil)))

    ;; 6: jos parametrina annettu organisaatio ja app-infosta ratkaistu organisaatio ei matchaa -> (fail :error.previous-permit-found-from-backend-is-of-different-organization)

    (fact "ids of the given and resolved organizations do not match"
      (create-app-from-prev-permit raktark-jarvenpaa) => (partial expected-failure? "error.previous-permit-found-from-backend-is-of-different-organization")
      (provided
       (krysp-reader/get-app-info-from-message anything anything) => {:municipality "753"}))

    ;; 7: jos sanomassa ei ollut rakennuspaikkaa, ja ei alunperin annettu tarpeeksi parametreja -> (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)

    (fact "no 'rakennuspaikkatieto' element in the received xml, need more info"
      (create-app-from-prev-permit raktark-jarvenpaa) => (contains {:ok false
                                                                    :needMorePrevPermitInfo true
                                                                    :text "error.more-prev-app-info-needed"})
      (provided
       (krysp-reader/get-app-info-from-message anything anything) => (dissoc example-app-info :rakennuspaikka)))

    ;; 8: testaa Sonjalla, etta ei ole oikeuksia luoda hakemusta, mutta jarvenpaan viranomaisella on

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
            (let [resp1 (create-app-from-prev-permit raktark-jarvenpaa
                                                     :x "6707184.319"
                                                     :y "393021.589"
                                                     :address "Kylykuja 3"
                                                     :propertyId property-id)
                  app-id (:id resp1)
                  application (query-application local-query raktark-jarvenpaa app-id)
                  invites (filter #(= raktark-jarvenpaa-id (get-in % [:invite :inviter :id])) (:auth application))]

              resp1  => ok?

              ;; Test count of the invited emails, because invalid emails are ignored
              (fact "invites count"
                (count invites) => 3
                (count (:invites (local-query pena  :invites))) => 1
                (count (:invites (local-query mikko :invites))) => 1
                (count (:invites (local-query teppo :invites))) => 1)

              (fact "hakija document count"
                (count (domain/get-documents-by-name application "hakija-r")) => 5)

              ;; Cancel the application and re-call 'create-app-from-prev-permit' -> should open application with different ID
              (fact "fetching prev-permit again after canceling the previously fetched one"
                (local-command raktark-jarvenpaa :cancel-application-authority
                               :id (:id application)
                               :text "Se on peruutus ny!"
                               :lang "fi")
                (let [resp2 (create-app-from-prev-permit raktark-jarvenpaa
                                                         :x "6707184.319"
                                                         :y "393021.589"
                                                         :address "Kylykuja 3"
                                                         :propertyId property-id) => ok?]
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
       (krysp-reader/get-app-info-from-message anything anything) => (-> example-app-info
                                                                         (assoc :kuntalupatunnus "14-0241-R 2")
                                                                         (update-in [:hakijat] (fn [hakijat] (map #(dissoc % :henkilo :yritys) hakijat))))))

    (facts "Application from kuntalupatunnus via rest API"
      (let [rest-address (str (server-address) "/rest/get-lp-id-from-previous-permit")
            params  {:query-params {"kuntalupatunnus" example-kuntalupatunnus}
                     :basic-auth   ["jarvenpaa-backend" "jarvenpaa"]}]
        (against-background [(before :facts (apply-remote-minimal))]
                            (mongo/with-db local-db-name
                              (fact "should create new LP application if kuntalupatunnus doesn't match existing app"
                                (let [response (http-get rest-address params)
                                      resp-body (:body (util/decode-response response))]
                                  response => http200?
                                  resp-body => ok?
                                  (keyword (:text resp-body)) => :created-new-application
                                  (let [application (query-application local-query raktark-jarvenpaa (:id resp-body))]
                                    (:opened application) => truthy))))

                            (fact "should return the LP application if the kuntalupatunnus matches an existing app"
                              (let [{app-id :id} (create-and-submit-application pena :propertyId jarvenpaa-property-id)
                                    verdict-resp (give-verdict raktark-jarvenpaa app-id :verdictId example-kuntalupatunnus)
                                    response     (http-get rest-address params)
                                    resp-body    (:body (util/decode-response response))]
                                verdict-resp => ok?
                                response => http200?
                                resp-body => ok?
                                (keyword (:text resp-body)) => :already-existing-application))

                            (fact "create new LP app if kuntalupatunnus matches existing app in another organization"
                              (let [{app-id :id} (create-and-submit-application pena :propertyId sipoo-property-id)
                                    verdict-resp (give-verdict sonja app-id :verdictId example-kuntalupatunnus)
                                    response     (http-get rest-address params)
                                    resp-body    (:body (util/decode-response response))]
                                verdict-resp => ok?
                                response => http200?
                                resp-body => ok?
                                (keyword (:text resp-body)) => :created-new-application)))))))
