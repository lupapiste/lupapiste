(ns lupapalvelu.application-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [sade.core :refer [def-]]
            [sade.xml :as xml]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

(fixture/apply-fixture "minimal")

#_(fact (user/change-password "veikko.viranomainen@tampere.fi" "passu") => nil
     (provided (security/get-hash "passu" anything) => "hash"))


#_(defn create-app-with-fn [f apikey & args]
  (let [args (->> args
               (apply hash-map)
               (merge {:operation "asuinrakennus"
                       :propertyId "75312312341234"
                       :x 444444 :y 6666666
                       :address "foo 42, bar"
                       :municipality (or (muni-for-key apikey) sonja-muni)})
               (mapcat seq))]
    (apply f apikey :create-application args)))

;ajax.command("create-application-from-previous-permit", {
;        operation: self.operation(),
;        y: self.y(),
;        x: self.x(),
;        address: self.addressString(),
;        propertyId: util.prop.toDbFormat(self.propertyId()),
;        municipality: self.municipality().id,
;        kuntalupatunnus: self.kuntalupatunnusFromPrevPermit()
;      })

; (provided (cr/get-xml anything anything anything) => (xml/parse (slurp (clojure.java.io/resource "mml/yhteystiedot-KP.xml"))))

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
          (domain/owner-or-writer? anything anything) => true))
      ; 1 b: jos ei oikkia -> (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id (:id app-with-verdict))
      (fact "does not have rights to the found app"
        (create-app-from-prev-permit pena) => (contains {:ok false
                                                         :id "lupis-id"
                                                         :text "error.lupapiste-application-already-exists-but-unauthorized-to-access-it"})
        (provided
          (domain/owner-or-writer? anything anything) => false))
      ;; patee molemmille yllaoleville
      (against-background
        (domain/get-application anything) => {:id "lupis-id"}))

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
    (facts "message includes lupapiste id"
      ; 6 a: jota ei kuitenkaan ole jarjestelmassa -> (fail :error.not-able-to-open-with-lupapiste-id-that-previous-permit-included :id lupapiste-tunnus)
      (fact "the lupapiste id is not found from database though"
        (create-app-from-prev-permit pena
          :x "6707184.319"
          :y "393021.589"
          :address "Kylykuja 3"
          :propertyId "18600303560005") => (contains {:ok false
                                                      :id example-LP-tunnus
                                                      :text "error.not-able-to-open-with-lupapiste-id-that-previous-permit-included"}))

      (facts "app id found in the database"
        ; 6 b: on jarjestelmassa, mutta kayttajalla ei oikkia sille -> (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id lupapiste-tunnus)
        (fact "we do not have permissions to application"
          (create-app-from-prev-permit pena
            :x "6707184.319"
            :y "393021.589"
            :address "Kylykuja 3"
            :propertyId "18600303560005") => (contains {:ok false
                                                        :id example-LP-tunnus
                                                        :text "error.lupapiste-application-already-exists-but-unauthorized-to-access-it"})
          (provided
            (domain/owner-or-writer? anything anything) => false))

        ; 6 c: on jarjestelmassa, ja kayttajalla on oikat sille     -> (ok :id lupapiste-tunnus)
        (fact "got permissions for the application"
          (create-app-from-prev-permit pena
            :x "6707184.319"
            :y "393021.589"
            :address "Kylykuja 3"
            :propertyId "18600303560005") => (contains {:ok true
                                                        :id example-LP-tunnus})
          (provided
            (domain/owner-or-writer? anything anything) => true))

        (against-background
          (mongo/by-id :applications example-LP-tunnus) => {:id example-LP-tunnus} :times 1
          (mongo/by-id :organizations "186-R") => {:krysp {:R {:url "http://localhost:8000/dev/krysp"
                                                               :version "2.1.3"
                                                               :ftpUser "dev_jarvenpaa"}}}))

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

