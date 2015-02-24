(ns lupapalvelu.application-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [sade.core :refer [def-]]
            [sade.xml :as xml]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

(fixture/apply-fixture "minimal")

(def- example-kuntalupatunnus "14-0241-R 3")
(def- example-LP-tunnus "LP-186-2014-00290")

(defn- create-app-from-prev-permit [apikey & args]
  (let [args (->> args
               (apply hash-map)
               (merge {:operation "aiemmalla-luvalla-hakeminen"
                       :municipality "186"  ;; Jarvenpaa
                       :kuntalupatunnus example-kuntalupatunnus
                       :y 0
                       :x 0
                       :address ""
                       :propertyId nil})
               (mapcat seq))]
    (apply local-command apikey :create-application-from-previous-permit args)))

(fact* "Creating new application based on a prev permit"

  (let [example-xml (xml/parse (slurp (io/resource "../resources/krysp/sample/verdict-rakval-from-kuntalupatunnus-query.xml")))
        example-app-info (krysp-reader/get-app-info-from-message example-xml example-kuntalupatunnus)]


    (fact "missing parameters"
      (create-app-from-prev-permit pena :municipality "") => (partial expected-failure? "error.missing-parameters")
      (create-app-from-prev-permit pena :operation "") => (partial expected-failure? "error.missing-parameters"))


    ; 1: Kannassa on app, jonka organization ja verdictin kuntalupatunnus matchaa haettuihin
    (facts "db has app that has the kuntalupatunnus in its verdict and its organization matches"
      ; 1 a: avaa hakemus, jos on oikat -> (ok :id lupapiste-tunnus)
      (fact "has rights to the found app"
        (create-app-from-prev-permit pena) => (contains {:ok true
                                                         :id "lupis-id"})
        (provided
          (domain/get-application-as anything anything) => {:id "lupis-id"}))
      ; 1 b: jos ei oikkia -> (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id (:id app-with-verdict))
      (fact "does not have rights to the found app"
        (create-app-from-prev-permit pena) => (contains {:ok false
                                                         :id "lupis-id"
                                                         :text "error.lupapiste-application-already-exists-but-unauthorized-to-access-it"})
        (provided
          (domain/get-application-as anything anything) => nil))
      ;; patee molemmille yllaoleville
      (against-background
        (domain/get-application-no-access-checking anything) => {:id "lupis-id"}))

    ; 2: jos taustajarjestelmasta ei saada xml-sisaltoa -> (fail :error.no-previous-permit-found-from-backend)
    (fact "no xml content received from backend with the kuntalupatunnus"
      (create-app-from-prev-permit pena) => (partial expected-failure? "error.no-previous-permit-found-from-backend")
      (provided
        (krysp-fetch-api/get-application-xml anything false true) => nil))

    ; 3: jos (krysp-reader/get-app-info-from-message xml kuntalupatunnus) palauttaa nillin -> (fail :error.no-previous-permit-found-from-backend)
    (fact "no application info could be parsed"
      (create-app-from-prev-permit pena) => (partial expected-failure? "error.no-previous-permit-found-from-backend")
      (provided
        (krysp-reader/get-app-info-from-message anything anything) => nil))

    ; 4: jos parametrina annettu organisaatio ja app-infosta ratkaistu organisaatio ei matchaa -> (fail :error.previous-permit-found-from-backend-is-of-different-organization)
    (fact "ids of the given and resolved organizations do not match"
      (create-app-from-prev-permit pena) => (partial expected-failure? "error.previous-permit-found-from-backend-is-of-different-organization")
      (provided
        (krysp-reader/get-app-info-from-message anything anything) => {:municipality "753"}))

    ; 5: jos sanomassa ei ollut rakennuspaikkaa, ja ei alunperin annettu tarpeeksi parametreja -> (fail :error.more-prev-app-info-needed :needMorePrevPermitInfo true)
    (fact "no 'rakennuspaikkatieto' element in the received xml, need more info"
      (create-app-from-prev-permit pena) => (contains {:ok false
                                                       :needMorePrevPermitInfo true
                                                       :text "error.more-prev-app-info-needed"})
      (provided
        (krysp-reader/get-app-info-from-message anything anything) => (dissoc example-app-info :rakennuspaikka)))

    ; 6) sanomassa tulee lupapiste-id
    (facts "message includes lupapiste id and app id found in the database"

      ; 6 a: on jarjestelmassa, mutta kayttajalla ei oikkia sille -> (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id lupapiste-tunnus)
      (fact "we do not have permissions to application"
        (create-app-from-prev-permit pena
          :x "6707184.319"
          :y "393021.589"
          :address "Kylykuja 3"
          :propertyId "18600303560005") => (contains {:ok false
                                                      :id example-LP-tunnus
                                                      :text "error.lupapiste-application-already-exists-but-unauthorized-to-access-it"})
        (provided
          (domain/get-application-as anything anything) => nil))

      ; 6 b: on jarjestelmassa, ja kayttajalla on oikat sille     -> (ok :id lupapiste-tunnus)
      (fact "got permissions for the application"
        (create-app-from-prev-permit pena
          :x "6707184.319"
          :y "393021.589"
          :address "Kylykuja 3"
          :propertyId "18600303560005") => (contains {:ok true
                                                      :id example-LP-tunnus})
        (provided
          (domain/get-application-as anything anything) => {:id example-LP-tunnus}))

      (against-background
        (krysp-reader/get-app-info-from-message anything anything) => (assoc example-app-info :id example-LP-tunnus)))

    ; 7: do-create-application heittaa jonkin poikkeuksen -> (fail :error.no-previous-permit-found-from-backend)
    (fact "do-create-application fails with exception"
      (create-app-from-prev-permit pena
        :x "6707184.319"
        :y "393021.589"
        :address "Kylykuja 3"
        :propertyId "18600303560005") => (contains {:ok false})
      (provided
        (#'lupapalvelu.application/do-create-application anything anything) =throws=> (Exception. "This was expected.")))

    ; 8: testaa Sonjalla, etta ei ole oikkia luoda hakemusta, mutta jarvenpaan viranomaisella on
    (facts "authority tests"

      (fact "authority of different municipality cannot create application"
        (create-app-from-prev-permit sonja
          :x "6707184.319"
          :y "393021.589"
          :address "Kylykuja 3"
          :propertyId "18600303560005") => (contains {:ok false :text "error.unauthorized"}))

      (fact "authority of same municipality can create application"
        (create-app-from-prev-permit raktark-jarvenpaa
          :x "6707184.319"
          :y "393021.589"
          :address "Kylykuja 3"
          :propertyId "18600303560005") => (contains {:ok true :id "LP-186-2014-00001"})))

    (fixture/apply-fixture "minimal")

    ; 9: hakijalla on oikeus luoda hakemus
    (fact "applicant can create application"
      (fact "authority of different municipality cannot create app with prev permit"
        (create-app-from-prev-permit pena
          :x "6707184.319"
          :y "393021.589"
          :address "Kylykuja 3"
          :propertyId "18600303560005") => (contains {:ok true :id "LP-186-2014-00001"})))

  ))

