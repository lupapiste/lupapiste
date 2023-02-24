(ns lupapalvelu.rest-application-data-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer [give-legacy-verdict]]))

(apply-remote-minimal)

(def rest-address (str (server-address) "/rest/submitted-applications"))

(defn- api-call [params]
  (http-get rest-address (merge params {:throw-exceptions false})))

(defn- sipoo-r-api-call []
  (decode-response
    (api-call {:query-params {:organization "753-R"}
               :basic-auth   ["sipoo-r-backend" "sipoo"]})))

(facts "REST interface for application data -"
  (fact "not available as anonymous user"
    (let [params {:query-params {:organization "753-R"}}
          response (api-call params)]
      response => http401?))
  (fact "Sipoo Backend can access"
    (sipoo-r-api-call) => http200?)
  (fact "Sipoo Backend can not access other municipalities"
    (decode-response
      (api-call {:query-params {:organization "091-R"}
                 :basic-auth   ["sipoo-r-backend" "sipoo"]})) =not=> ok?)
  (let [r-app-id1 (create-app-id pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")
        r-app-id2 (create-app-id pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")
        r-app-id3 (create-app-id pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")

        ;Update rakennusluokka for r-app-1
        _ (do (update-rakennusluokat "753-R" true) (set-krysp-version "753-R" "R" sipoo "2.2.4"))
        r-app-1 (query-application pena r-app-id1)
        r-app1-doc-id (:id (domain/get-document-by-name r-app-1 "uusiRakennus"))
        _ (fact {:midje/description (str "Updates: ")}
            (command pena :update-doc :id r-app-id1 :doc r-app1-doc-id
                     :updates [["kaytto.rakennusluokka", "0110 omakotitalot"]] :collection "documents") => ok?)

        ;Submit and add operation for r-app-id1
        _ (command pena :submit-application :id r-app-id1)
        _ (command pena :add-operation :id r-app-id1 :operation "purkaminen")
        ;;Submit and give verdict for r-app-id3
        _ (command pena :submit-application :id r-app-id3)
        _ (give-legacy-verdict sonja r-app-id3)
        ;;Create and submit change-permit app
        r-app-id4 (:id (command pena :create-change-permit :id r-app-id3))
        _ (command pena :submit-application :id r-app-id4)]
      (let [resp              (sipoo-r-api-call)
            body              (-> resp :body)
            application1-data (first (filter #(= r-app-id1 (get % :asiointitunnus)) (:data body)))
            application4-data (first (filter #(= r-app-id4 (get % :asiointitunnus)) (:data body)))
            toimenpiteet      (get application1-data :toimenpiteet)
            kiinteisto-tunnus "75300000000000"]
        resp => http200?
        body => ok?
        (facts "Application submitted => contained in API call result"
          application1-data => truthy
          application4-data => truthy)

        (fact "Application draft => not contained in API call result"
          (:data body) =not=> (has some (contains {:asiointitunnus r-app-id2})))

        (fact "Application state verdictGiven => not contained in API call result"
          (:data body) =not=> (has some (contains {:asiointitunnus r-app-id3})))

        (facts "Returned data for application1 is correct"
          (fact "kuntakoodi"
            (get application1-data :kuntakoodi) => "753")
          (fact "kiinteistoTunnus"
            (get application1-data :kiinteistoTunnus) => kiinteisto-tunnus)
          (fact "count (toimenpiteet) = 2"
            (count toimenpiteet) => 2)

          (facts "Uusi toimenpide"
            (let [toimenpide1 (first (filter #(get % :uusi) toimenpiteet))]
              (fact "Kuvaus"
                (get-in toimenpide1 [:uusi :kuvaus]) => "Asuinkerrostalon tai rivitalon rakentaminen")
              (fact "kiinttun"
                (get-in toimenpide1 [:rakennuksenTiedot :rakennustunnus :kiinttun]) => kiinteisto-tunnus)
              (fact "rakennusluokka"
                (get-in toimenpide1 [:rakennuksenTiedot :rakennusluokka]) => "0110 omakotitalot")))

          (facts "Purkamis-toimenpide"
            (let [toimenpide1 (first (filter #(get % :purkaminen) toimenpiteet))]
              (fact "Kuvaus"
                (get-in toimenpide1 [:purkaminen :kuvaus]) => "Rakennuksen purkaminen")
              (fact "kiinttun"
                (get-in toimenpide1 [:rakennuksenTiedot :rakennustunnus :kiinttun]) => kiinteisto-tunnus))))

        (facts "Returned data for application4 is correct"
          (fact "kuntakoodi"
            (get application4-data :kuntakoodi) => "753")
          (fact "kiinteistoTunnus"
            (get application4-data :kiinteistoTunnus) => kiinteisto-tunnus)
          (fact "kiinteistoTunnus"
            (get application4-data :asiatyyppi) => "muutoslupa")
          (fact "count (toimenpiteet) = 2"
            (count (get application4-data :toimenpiteet)) => 1)))))
