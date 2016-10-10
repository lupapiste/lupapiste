(ns lupapalvelu.vetuma-test
  (:require [lupapalvelu.vetuma :refer :all]
            [sade.env :as env]
            [midje.sweet :refer :all])
  (:import [org.apache.commons.io.output NullWriter]))

(def test-parameters (str (env/value :vetuma :rcvid)
                          "&VETUMA-APP2&20061218151424309&6&0,1,2,3,6&LOGIN&EXTAUTH&fi&https://localhost/Show.asp&https://localhost/ShowCancel.asp&https://localhost/ShowError.asp&"
                          (env/value :vetuma :ap)
                          "&trid1234567890&"
                          (env/value :vetuma :rcvid) "-" (env/value :vetuma :key) "&"))
(def valid-response  {"RCVID" (env/value :vetuma :rcvid),
                      "USERID" "210281-9988",
                      "ERRURL" "https://localhost:8443/vetuma/error",
                      "RETURL" "https://localhost:8443/vetuma/return",
                      "MAC" "FD60C02F8DEDEA748677AF84856C1E0400D6A809BA44A97AB3788A2E1D087204",
                      "TIMESTMP" "20121008144528336",
                      "STATUS" "SUCCESSFUL",
                      "SUBJECTDATA" "ETUNIMI=PORTAALIA, SUKUNIMI=TESTAA",
                      "VTJDATA" "%3C%3Fxml+version%3D%221.0%22+encoding%3D%22ISO-8859-1%22+standalone%3D%22yes%22%3F%3E%3Cns2%3AVTJHenkiloVastaussanoma+versio%3D%221.0%22+sanomatunnus%3D%22PERUSJHHS2%22+tietojenPoimintaaika%3D%2220121008144529%22+xmlns%3Ans2%3D%22http%3A%2F%2Fxml.vrk.fi%2Fschema%2Fvtjkysely%22+xmlns%3D%22http%3A%2F%2Ftempuri.org%2F%22%3E%3Cns2%3AAsiakasinfo%3E%3Cns2%3AInfoS%3E08.10.2012+14%3A45%3C%2Fns2%3AInfoS%3E%3Cns2%3AInfoR%3E08.10.2012+14%3A45%3C%2Fns2%3AInfoR%3E%3Cns2%3AInfoE%3E08.10.2012+14%3A45%3C%2Fns2%3AInfoE%3E%3C%2Fns2%3AAsiakasinfo%3E%3Cns2%3APaluukoodi+koodi%3D%220000%22%3EHaku+onnistui%3C%2Fns2%3APaluukoodi%3E%3Cns2%3AHakuperusteet%3E%3Cns2%3AHenkilotunnus+hakuperusteTekstiE%3D%22Found%22+hakuperusteTekstiR%3D%22Hittades%22+hakuperusteTekstiS%3D%22L%F6ytyi%22+hakuperustePaluukoodi%3D%221%22%3E210281-9988%3C%2Fns2%3AHenkilotunnus%3E%3Cns2%3ASahkoinenAsiointitunnus+hakuperusteTekstiE%3D%22Not+used%22+hakuperusteTekstiR%3D%22Beteckningen+har+inte+anv%E4ndat%22+hakuperusteTekstiS%3D%22Tunnushakuperustetta+ei+ole+kaytetty%22+hakuperustePaluukoodi%3D%224%22%3E%3C%2Fns2%3ASahkoinenAsiointitunnus%3E%3C%2Fns2%3AHakuperusteet%3E%3Cns2%3AHenkilo%3E%3Cns2%3AHenkilotunnus+voimassaolokoodi%3D%221%22%3E210281-9988%3C%2Fns2%3AHenkilotunnus%3E%3Cns2%3ANykyinenSukunimi%3E%3Cns2%3ASukunimi%3EDemo%3C%2Fns2%3ASukunimi%3E%3C%2Fns2%3ANykyinenSukunimi%3E%3Cns2%3ANykyisetEtunimet%3E%3Cns2%3AEtunimet%3ENordea%3C%2Fns2%3AEtunimet%3E%3C%2Fns2%3ANykyisetEtunimet%3E%3Cns2%3AVakinainenKotimainenLahiosoite%3E%3Cns2%3ALahiosoiteS%3E%3C%2Fns2%3ALahiosoiteS%3E%3Cns2%3ALahiosoiteR%3E%3C%2Fns2%3ALahiosoiteR%3E%3Cns2%3APostinumero%3E%3C%2Fns2%3APostinumero%3E%3Cns2%3APostitoimipaikkaS%3E%3C%2Fns2%3APostitoimipaikkaS%3E%3Cns2%3APostitoimipaikkaR%3E%3C%2Fns2%3APostitoimipaikkaR%3E%3Cns2%3AAsuminenAlkupvm%3E%3C%2Fns2%3AAsuminenAlkupvm%3E%3Cns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AVakinainenKotimainenLahiosoite%3E%3Cns2%3AVakinainenUlkomainenLahiosoite%3E%3Cns2%3AUlkomainenLahiosoite%3E%3C%2Fns2%3AUlkomainenLahiosoite%3E%3Cns2%3AUlkomainenPaikkakuntaJaValtioS%3E%3C%2Fns2%3AUlkomainenPaikkakuntaJaValtioS%3E%3Cns2%3AUlkomainenPaikkakuntaJaValtioR%3E%3C%2Fns2%3AUlkomainenPaikkakuntaJaValtioR%3E%3Cns2%3AUlkomainenPaikkakuntaJaValtioSelvakielinen%3E%3C%2Fns2%3AUlkomainenPaikkakuntaJaValtioSelvakielinen%3E%3Cns2%3AValtiokoodi3%3E%3C%2Fns2%3AValtiokoodi3%3E%3Cns2%3AAsuminenAlkupvm%3E%3C%2Fns2%3AAsuminenAlkupvm%3E%3Cns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AVakinainenUlkomainenLahiosoite%3E%3Cns2%3AKotikunta%3E%3Cns2%3AKuntanumero%3E%3C%2Fns2%3AKuntanumero%3E%3Cns2%3AKuntaS%3E%3C%2Fns2%3AKuntaS%3E%3Cns2%3AKuntaR%3E%3C%2Fns2%3AKuntaR%3E%3Cns2%3AKuntasuhdeAlkupvm%3E%3C%2Fns2%3AKuntasuhdeAlkupvm%3E%3C%2Fns2%3AKotikunta%3E%3Cns2%3AKuolintiedot%3E%3Cns2%3AKuolinpvm%3E%3C%2Fns2%3AKuolinpvm%3E%3C%2Fns2%3AKuolintiedot%3E%3Cns2%3AAidinkieli%3E%3Cns2%3AKielikoodi%3E%3C%2Fns2%3AKielikoodi%3E%3Cns2%3AKieliS%3E%3C%2Fns2%3AKieliS%3E%3Cns2%3AKieliR%3E%3C%2Fns2%3AKieliR%3E%3Cns2%3AKieliSelvakielinen%3E%3C%2Fns2%3AKieliSelvakielinen%3E%3C%2Fns2%3AAidinkieli%3E%3Cns2%3ASuomenKansalaisuusTietokoodi%3E0%3C%2Fns2%3ASuomenKansalaisuusTietokoodi%3E%3C%2Fns2%3AHenkilo%3E%3C%2Fns2%3AVTJHenkiloVastaussanoma%3E",
                      "TRID" "68242365637727173837",
                      "EXTRADATA" "HETU=210281-9988",
                      "LG" "fi",
                      "SO" "62",
                      "CANURL" "https://localhost:8443/vetuma/cancel"})
(def invalid-response  {"RCVID" "<<HACKED>>"})
(def url "https://localhost:8443")
(def config-fixture {:rcvid (env/value :vetuma :rcvid)
                     :key (env/value :vetuma :key)})
(if (and (env/value :vetuma :key) (not (env/feature? :dummy-ident)) (not (env/feature? :suomifi-ident)))
  (facts
    (fact "digest calculation based on vetuma docs"
      (mac test-parameters) => "72A72A046BD5561BD1C47F3B77FC9456AD58C9C428CACF44D502834C9F8C02A3")

    (fact "request-data can be generated"
      (request-data url "fi") => truthy)

    (fact "request-data does not contain (secret) key"
      (contains? (request-data url "fi") "KEY") => falsey)

    (fact "response-data can be parsed"
      (parsed valid-response) => (contains {:userid "210281-9988"})
      (provided (config) => config-fixture))

    (fact "extracting :subjectdata works"
      (extract-subjectdata {:subjectdata "ETUNIMI=PORTAALIA, SUKUNIMI=TESTAA"}) => {:firstname "PORTAALIA" :lastname "TESTAA"})

    (fact "extracting invalid :subjectdata does not fail"
      ; This was seen in production
      (extract-subjectdata {:subjectdata "CEN"}) => empty?)

    (fact "parsing response just works"
      (-> valid-response parsed user-extracted) => (contains {:userid "210281-9988" :firstname "PORTAALIA" :lastname "TESTAA"})
      (provided (config) => config-fixture))

    (fact "with invalid data, empty map is returned"
      (binding [*out* (NullWriter.)
                *err* (NullWriter.)]
        (parsed invalid-response) => (throws RuntimeException))))

  (println "Vetuma disabled or key not defined, skipped test"))

(facts "template parsing"
  (fact "without placeholders input is returned"
    (apply-templates {:a "eka" :b "toka"}) => {:a "eka" :b "toka"})
  (fact "placeholders are changed"
    (apply-templates {:a "eka{b}" :b "toka"}) => {:a "ekatoka" :b "toka"})
  (fact "missing placeholders are ignored"
    (apply-templates {:a "eka{c}" :b "toka"}) => {:a "eka" :b "toka"}))
