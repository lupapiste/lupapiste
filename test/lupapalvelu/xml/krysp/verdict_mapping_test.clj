(ns lupapalvelu.xml.krysp.verdict-mapping-test
  (:require [clojure.data.xml :as data-xml]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.document.rakennuslupa-canonical-test :refer [application-rakennuslupa]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-canonical-test :refer [verdict]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.verdict-mapping]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.xml :as xml]))

(def app (assoc application-rakennuslupa :attachments [{:id "11"
                                                        :type {:type-id "paatosote" :type-group "paatoksenteko"}
                                                        :latestVersion {:fileId "12" :filename "liite.txt"}
                                                        :target {:type "verdict" :id (:id verdict)}}
                                                       {:id "21"
                                                        :type {:type-id "muu" :type-group "muut"}
                                                        :latestVersion {:fileId "22" :filename "muu_liite.txt"}}
                                                       {:id "31"
                                                        :type {:type-id "paatosote" :type-group "paatoksenteko"}
                                                        :latestVersion {:fileId "32" :filename "eri_paatoksen_liite.txt"}
                                                        :target {:type "verdict" :id (str (:id verdict) "_eri")}}]))

(facts verdict-krysp-mapper
  (against-background
    (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc app :linkPermitData [{:id            "LP-753-2013-90001"
                                                                                          :type          "lupapistetunnus"
                                                                                          :operation     "kerrostalo-rivitalo"
                                                                                          :permitSubtype ""}])
    (org/pate-org? irrelevant) => false)
  (let [result (permit/verdict-krysp-mapper app verdict "fi" "2.2.2" "BEGIN_OF_LINK/")
        attachments (:attachments result)
        xml_s (data-xml/indent-str (:xml result))
        lp-xml (cr/strip-xml-namespaces (xml/parse xml_s))]
    (fact "2.2.2: xml exist" (:xml result) => truthy)

    (fact (validator/validate xml_s :R "2.2.2") => nil)

    (fact "xml contains app id"
      (xml/get-text lp-xml [:luvanTunnisteTiedot :LupaTunnus :MuuTunnus :tunnus])
      => (:id application-rakennuslupa))

    (facts "lupamaaraykset"
      (fact "autopaikkojarakennettava"
        (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaRakennettava]) => "3")

      (fact "autopaikkojarakennettu"
        (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaRakennettu]) => "1")

      (fact "autopaikkojakiinteistolla"
        (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaKiinteistolla]) => "2")

      (facts "katselmus"
        (fact "katselmuksen laji - times two"
          (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaaditutKatselmukset :katselmuksenLaji))
          => [["muu katselmus"]
              ["rakennuksen paikan merkitseminen"]])

        (fact "tarkastuksentaikatselmuksennimi - times two"
          (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaaditutKatselmukset :tarkastuksenTaiKatselmuksenNimi))
          => [["Katselmus"]
              ["Katselmus2"]]))

      (facts "maaraystieto"
        (fact "sisalto - times tow"
          (xml/select lp-xml [:paatostieto :lupamaaraykset :maaraystieto :Maarays :sisalto enlive/content])
          => (just ["muut lupaehdot - teksti" "toinen teksti"] :in-any-order)))

      (facts "vaadittuerityissuunnitelmatieto"
        (fact "vaadittuerityissuunnitelma - times two"
          (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaadittuErityissuunnitelmatieto :vaadittuErityissuunnitelma))
          => [["Suunnitelmat"]
              ["Suunnitelmat2"]]))

      (facts "vaadittutyonjohtajatieto"
        (fact "vaadittutyonjohtaja - times four"
          (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaadittuTyonjohtajatieto :tyonjohtajaRooliKoodi))
          => [["erityisalojen ty\u00f6njohtaja"]
              ["IV-ty\u00f6njohtaja"]
              ["vastaava ty\u00f6njohtaja"]
              ["KVV-ty\u00f6njohtaja"]])))

    (facts "paivamaarat"
      (fact "aloitettavapvm"
        (xml/get-text lp-xml [:paatostieto :paivamaarat :aloitettavaPvm]) => "2022-11-23")

      (fact "lainvoimainenpvm"
        (xml/get-text lp-xml [:paatostieto :paivamaarat :lainvoimainenPvm]) => "2017-11-27")

      (fact "voimassahetkipvm"
        (xml/get-text lp-xml [:paatostieto :paivamaarat :voimassaHetkiPvm]) => "2023-11-23")

      (fact "antopvm"
        (xml/get-text lp-xml [:paatostieto :paivamaarat :antoPvm]) => "2017-11-20")

      (fact "viimeinenvalituspvm"
        (xml/get-text lp-xml [:paatostieto :paivamaarat :viimeinenValitusPvm]) => "2017-12-27")

      (fact "julkipano"
        (xml/get-text lp-xml [:paatostieto :paivamaarat :julkipanoPvm]) => "2017-11-24"))

    (facts "poytakirja"
      (fact "paatos"
        (xml/get-text lp-xml [:paatostieto :poytakirja :paatos]) => "p\u00e4\u00e4t\u00f6s - teksti")

      (fact "paatoskoodi"
        (xml/get-text lp-xml [:paatostieto :poytakirja :paatoskoodi]) => "my\u00f6nnetty")

      (fact "paatoksentekija"
        (xml/get-text lp-xml [:paatostieto :poytakirja :paatoksentekija]) => "Pate Paattaja (Viranhaltija)")

      (fact "paatospvm"
        (xml/get-text lp-xml [:paatostieto :poytakirja :paatospvm]) => "2017-11-23")

      (fact "pykala"
        (xml/get-text lp-xml [:paatostieto :poytakirja :pykala]) => "99"))

    (facts "liitetieto"
      (facts "attachment with verdict target is returned"
        attachments => [{:fileId "12", :filename "12_liite.txt"}])

      (fact "single attachment incuded in xml"
        (count (xml/select lp-xml :liitetieto :Liite)) => 1)

      (fact "kuvaus"
        (xml/get-text lp-xml [:liitetieto :kuvaus]) => "P\u00e4\u00e4t\u00f6sote")

      (fact "linkkiliitteeseen"
        (xml/get-text lp-xml [:liitetieto :linkkiliitteeseen]) => "BEGIN_OF_LINK/12_liite.txt"))

    (facts "viitelupatieto"
      (fact "tunnus"
        (xml/get-text lp-xml [:viitelupatieto :LupaTunnus :muuTunnustieto :tunnus]) => "LP-753-2013-90001")

      (fact "sovellus"
        (xml/get-text lp-xml [:viitelupatieto :LupaTunnus :muuTunnustieto :sovellus]) => "Lupapiste"))

    (facts "paatosliite"
      (fact "kuvaus"
        (xml/get-text lp-xml [:paatostieto :poytakirja :liite :kuvaus]) => "P\u00e4\u00e4t\u00f6s")

      (fact "linkkiliitteeseen"
        (xml/get-text lp-xml [:paatostieto :poytakirja :liite :linkkiliitteeseen]) => "BEGIN_OF_LINK/LP-753-2018-90012 P\u00e4\u00e4t\u00f6s 21.03.2018 12:29.pdf")

      (fact "versio"
        (xml/get-text lp-xml [:paatostieto :poytakirja :liite :versionumero]) => "0.1")

      (fact "muokkaushetki"
        (xml/get-text lp-xml [:paatostieto :poytakirja :liite :muokkausHetki]) => "2018-03-21T10:29:38")

      (fact "tyyppi"
        (xml/get-text lp-xml [:paatostieto :poytakirja :liite :tyyppi]) => "paatos"))))
