(ns lupapalvelu.vtj
  (:require [ring.util.codec :refer [form-decode]]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.xml :refer :all]))

(def encoding "ISO-8859-1")

(defn parse-vtj [s]
  (-> s
    (form-decode encoding)
    (parse :encoding encoding)
    strip-xml-namespaces))

(defn extract-vtj [s]
  (let [xml (parse-vtj s)]
    {:firstName   (get-text xml :NykyisetEtunimet :Etunimet)
     :lastName    (get-text xml :NykyinenSukunimi :Sukunimi)
     :street      (get-text xml :VakinainenKotimainenLahiosoite :LahiosoiteS)
     :zip         (get-text xml :VakinainenKotimainenLahiosoite :Postinumero)
     :city        (get-text xml :VakinainenKotimainenLahiosoite :PostitoimipaikkaS)}))

(comment "Osuuuspankki test dude"
         {:VTJHenkiloVastaussanoma
          {:Henkilo
           {:Kuolintiedot {:Kuolinpvm nil},
            :NykyisetEtunimet {:Etunimet "Sylvi Sofie"},
            :VakinainenUlkomainenLahiosoite
            {:AsuminenAlkupvm nil,
             :UlkomainenPaikkakuntaJaValtioS nil,
             :Valtiokoodi3 nil,
             :UlkomainenLahiosoite nil,
             :AsuminenLoppupvm nil,
             :UlkomainenPaikkakuntaJaValtioR nil,
             :UlkomainenPaikkakuntaJaValtioSelvakielinen nil},
            :Kotikunta
            {:KuntasuhdeAlkupvm "20050525",
             :Kuntanumero "297",
             :KuntaS "Kuopio",
             :KuntaR "Kuopio"},
            :Henkilotunnus "081181-9984",
            :Aidinkieli
            {:KieliS "suomi",
             :KieliR "finska",
             :Kielikoodi "fi",
             :KieliSelvakielinen nil},
            :VakinainenKotimainenLahiosoite
            {:AsuminenAlkupvm "20050525",
             :Postinumero "70100",
             :LahiosoiteS "Sep\u00e4nkatu 11 A 5",
             :LahiosoiteR nil,
             :AsuminenLoppupvm nil,
             :PostitoimipaikkaR "KUOPIO",
             :PostitoimipaikkaS "KUOPIO"},
            :SuomenKansalaisuusTietokoodi "1",
            :NykyinenSukunimi {:Sukunimi "Marttila"}},
           :Paluukoodi "Haku onnistui",
           :Asiakasinfo
           {:InfoE "08.02.2013 00:36",
            :InfoS "08.02.2013 00:36",
            :InfoR "08.02.2013 00:36"},
           :Hakuperusteet
           {:Henkilotunnus "081181-9984", :SahkoinenAsiointitunnus nil}}})
