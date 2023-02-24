(ns lupapalvelu.backing-system.krysp.rakennuslupa-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :refer [rakennuslupa_to_krysp_212
                                                                           rakennuslupa_to_krysp_213
                                                                           rakennuslupa_to_krysp_214
                                                                           rakennuslupa_to_krysp_215
                                                                           rakennuslupa_to_krysp_216
                                                                           rakennuslupa_to_krysp_218
                                                                           rakennuslupa_to_krysp_220
                                                                           rakennuslupa_to_krysp_222
                                                                           rakennuslupa_to_krysp_223
                                                                           rakennuslupa_to_krysp_224
                                                                           rakennuslupa-element-to-xml
                                                                           add-paattymispvm-in-foreman-application
                                                                           check-operations!]]
            [lupapalvelu.document.rakennuslupa-canonical :refer [rakval-application-to-canonical]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rakennuslupa-canonical-util :refer [asiakirjat-toimitettu-checker
                                                             application-rakennuslupa
                                                             application-aurinkopaneeli
                                                             application-aurinkopaneeli-with-many-applicants
                                                             application-tyonjohtajan-nimeaminen
                                                             application-tyonjohtajan-nimeaminen-v2
                                                             application-suunnittelijan-nimeaminen
                                                             application-suunnittelijan-nimeaminen-muu
                                                             jatkolupa-application
                                                             aloitusoikeus-hakemus
                                                             application-kayttotarkoitus-rakennusluokka
                                                             paperilupa-application]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.validator :refer :all :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system
                   foreman-app-to-foreman-termination-app)

(fact "2.1.2: :tag is set" (has-tag rakennuslupa_to_krysp_212) => true)
(fact "2.1.3: :tag is set" (has-tag rakennuslupa_to_krysp_213) => true)
(fact "2.1.4: :tag is set" (has-tag rakennuslupa_to_krysp_214) => true)
(fact "2.1.5: :tag is set" (has-tag rakennuslupa_to_krysp_215) => true)
(fact "2.1.6: :tag is set" (has-tag rakennuslupa_to_krysp_216) => true)
(fact "2.1.8: :tag is set" (has-tag rakennuslupa_to_krysp_218) => true)
(fact "2.2.0: :tag is set" (has-tag rakennuslupa_to_krysp_220) => true)
(fact "2.2.2: :tag is set" (has-tag rakennuslupa_to_krysp_222) => true)
(fact "2.2.3: :tag is set" (has-tag rakennuslupa_to_krysp_223) => true)
(fact "2.2.4: :tag is set" (has-tag rakennuslupa_to_krysp_224) => true)

(defn- do-test [application & {:keys [validate-tyonjohtaja-type validate-pysyva-tunnus? finnish? validate-operations?]
                               :or   {validate-tyonjohtaja-type nil validate-pysyva-tunnus? false finnish? false validate-operations? false}}]
  (facts "Rakennusvalvonta KRYSP checks"
    (let [canonical (rakval-application-to-canonical application "fi")
          xml_212 (rakennuslupa-element-to-xml canonical "2.1.2")
          xml_213 (rakennuslupa-element-to-xml canonical "2.1.3")
          xml_214 (rakennuslupa-element-to-xml canonical "2.1.4")
          xml_215 (rakennuslupa-element-to-xml canonical "2.1.5")
          xml_216 (rakennuslupa-element-to-xml canonical "2.1.6")
          xml_218 (rakennuslupa-element-to-xml canonical "2.1.8")
          xml_220 (rakennuslupa-element-to-xml canonical "2.2.0")
          xml_222 (rakennuslupa-element-to-xml canonical "2.2.2")
          xml_223 (rakennuslupa-element-to-xml canonical "2.2.3")
          xml_224 (rakennuslupa-element-to-xml canonical "2.2.4")
          xml_212_s (indent-str xml_212)
          xml_213_s (indent-str xml_213)
          xml_214_s (indent-str xml_214)
          xml_215_s (indent-str xml_215)
          xml_216_s (indent-str xml_216)
          xml_218_s (indent-str xml_218)
          xml_220_s (indent-str xml_220)
          xml_222_s (indent-str xml_222)
          xml_223_s (indent-str xml_223)
          xml_224_s (indent-str xml_224)]

      (fact "2.1.2: xml exist" xml_212 => truthy)
      (fact "2.1.3: xml exist" xml_213 => truthy)
      (fact "2.1.4: xml exist" xml_214 => truthy)
      (fact "2.1.5: xml exist" xml_215 => truthy)
      (fact "2.1.6: xml exist" xml_216 => truthy)
      (fact "2.1.8: xml exist" xml_218 => truthy)
      (fact "2.2.0: xml exist" xml_220 => truthy)
      (fact "2.2.2: xml exist" xml_222 => truthy)
      (fact "2.2.3: xml exist" xml_223 => truthy)
      (fact "2.2.4: xml exist" xml_224 => truthy)

      (let [lp-xml_212 (cr/strip-xml-namespaces (xml/parse xml_212_s))
            lp-xml_213 (cr/strip-xml-namespaces (xml/parse xml_213_s))
            lp-xml_216 (cr/strip-xml-namespaces (xml/parse xml_216_s))
            _ (cr/strip-xml-namespaces (xml/parse xml_218_s))
            lp-xml_220 (cr/strip-xml-namespaces (xml/parse xml_220_s))
            _ (cr/strip-xml-namespaces (xml/parse xml_222_s))
            tyonjohtaja_212 (xml/select1 lp-xml_212 [:osapuolettieto :Tyonjohtaja])
            tyonjohtaja_213 (xml/select1 lp-xml_213 [:osapuolettieto :Tyonjohtaja])
            tyonjohtaja_216 (xml/select1 lp-xml_216 [:osapuolettieto :Tyonjohtaja])]

        (when validate-operations?
          (fact "Toimenpiteet"

            (let [app-ops (cons (:primaryOperation application) (:secondaryOperations application))
                  ; maisematyot sisaltyvat hankkeen kuvaukseen
                  operations (remove #(= "puun-kaataminen" (:name %)) app-ops)
                  expected (count operations)
                  xml-operations (xml/select lp-xml_220 [:toimenpidetieto :Toimenpide])]

              (fact "Count" (count xml-operations) => expected)

              (fact "Got buildings"
                (count (xml/select lp-xml_220 [:toimenpidetieto :Toimenpide :Rakennus])) => pos?)

              (doseq [op xml-operations
                      :let [op-tag (-> op :content first :tag)
                            rakennus (xml/select1 op [:Rakennus])]
                      :when rakennus]

                (when (= :uusi op-tag)
                  (fact "Operation description for a new building"
                    (xml/get-text rakennus [:rakennustunnus :rakennuksenSelite]) => "A: kerrostalo-rivitalo-kuvaus"))

                (fact {:midje/description (str "Building ID includes operation ID of " (name op-tag))}
                  (let [ids (xml/select rakennus [:rakennustunnus :MuuTunnus])
                        toimenpide-id (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "toimenpideId"))]))
                        lupapiste-id  (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "Lupapiste"))]))]
                    (fact "with sovellus=toimenpideId"
                      (xml/get-text toimenpide-id [:MuuTunnus :tunnus]) =not=> ss/blank?)

                    (fact "with sovellus=Lupapiste"
                      (xml/get-text lupapiste-id [:MuuTunnus :tunnus]) =not=> ss/blank?)))))))

        (fact "hakija, maksaja and hakijan asiamies parties exist"
          (let [osapuoli-codes (->> (xml/select lp-xml_220 [:osapuolettieto :Osapuoli])
                                 (map (comp :kuntaRooliKoodi cr/all-of)))]
            (->> osapuoli-codes (filter #(= % "Rakennusvalvonta-asian hakija")) count) => pos?
            (if (= (:id application) (:id application-rakennuslupa))
              (->> osapuoli-codes (filter #(= % "Hakijan asiamies")) count) => 1)
            (->> osapuoli-codes (filter #(= % "Rakennusvalvonta-asian laskun maksaja")) count) => pos?))

        (fact "saapumisPvm"
          (let [expected (date/xml-date (:submitted application))]
            (xml/get-text lp-xml_212 [:luvanTunnisteTiedot :LupaTunnus :saapumisPvm]) => expected
            (xml/get-text lp-xml_213 [:luvanTunnisteTiedot :LupaTunnus :saapumisPvm]) => expected))

        (fact "VRKLupatunnus"
          (xml/get-text lp-xml_212 [:luvanTunnisteTiedot :LupaTunnus :VRKLupatunnus]) => (every-checker string? ss/not-blank?))

        (if validate-tyonjohtaja-type
          (do
            (fact "In KRYSP 2.1.2, patevyysvaatimusluokka/vaadittuPatevyysluokka A are mapped to 'ei tiedossa'"
              (xml/get-text tyonjohtaja_212 :vaadittuPatevyysluokka) => "ei tiedossa"
              (xml/get-text tyonjohtaja_212 :patevyysvaatimusluokka) => "ei tiedossa")

            (fact "In KRYSP 2.1.3, patevyysvaatimusluokka/vaadittuPatevyysluokka A is not mapped"
              (xml/get-text tyonjohtaja_213 :patevyysvaatimusluokka) => "A"
              (xml/get-text tyonjohtaja_213 :vaadittuPatevyysluokka) => "A")

            (when (= :v1 validate-tyonjohtaja-type)

              (fact "FISEpatevyyskortti" (->> (xml/select lp-xml_220 [:osapuolettieto :Suunnittelija])
                                           (map cr/all-of)
                                           (every? #(and (-> % :FISEpatevyyskortti string?) (-> % :FISEkelpoisuus string?)))) => true))

            (when (= :v2 validate-tyonjohtaja-type)
              (fact "In KRYSP 2.1.6, :vainTamaHankeKytkin was added (Yhteiset schema was updated to 2.1.5 and tyonjohtaja along with it)"
                (xml/get-text tyonjohtaja_216 :vainTamaHankeKytkin) => "true")))
          (do
            tyonjohtaja_212 => nil
            tyonjohtaja_213 => nil))

        (if validate-pysyva-tunnus?
          (fact "pysyva rakennusnumero" (xml/get-text lp-xml_212 [:rakennustunnus :valtakunnallinenNumero]) => "1234567892")))

      (when (= :v1 validate-tyonjohtaja-type)
        (fact "Rakennusvalvonta KRYSP 2.1.5"
          (let [lp-xml_215 (cr/strip-xml-namespaces (xml/parse xml_215_s))]
            ; Address format has changed in 2.1.5
            (xml/get-text lp-xml_215 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :osoitenimi :teksti]) => "katu"
            (xml/get-text lp-xml_215 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :postitoimipaikannimi]) => "Tuonela"

            ; E-Invoicing fields added in 2.1.5
            (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :ovtTunnus]) => "003712345671"
            (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :verkkolaskuTunnus]) => "laskutunnus-1234"
            (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :valittajaTunnus]) => "BAWCFI22")))

      (fact "Country information"
        (let [lp-xml_220 (cr/strip-xml-namespaces (xml/parse xml_220_s))
              parties (xml/select lp-xml_220 [:osapuolitieto :Osapuoli])
              applicant (some #(and (= "hakija" (xml/get-text % [:VRKrooliKoodi])) %) parties)]
          ;; Foreign addresses were already in 2.1.5 mapping, but implemented in 2.2.0
          (when (= :v1 validate-tyonjohtaja-type)
            (xml/get-text lp-xml_220 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :valtioSuomeksi]) => "Kiina"
            (xml/get-text lp-xml_220 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :valtioKansainvalinen]) => "CHN")

          (if finnish?
            (do (xml/get-text applicant [:valtioSuomeksi]) => "Suomi"
                (xml/get-text applicant [:valtioKansainvalinen]) => "FIN")
            (do (xml/get-text applicant [:valtioSuomeksi]) => "Kiina"
                (xml/get-text applicant [:valtioKansainvalinen]) => "CHN"
                (xml/get-text applicant [:ulkomainenPostitoimipaikka]) => "Tuonela"
                (xml/get-text applicant [:ulkomainenLahiosoite]) => "katu"))))

      (validator/validate xml_212_s (:permitType application) "2.1.2")
      (validator/validate xml_213_s (:permitType application) "2.1.3")
      (validator/validate xml_214_s (:permitType application) "2.1.4")
      (validator/validate xml_215_s (:permitType application) "2.1.5")
      (validator/validate xml_216_s (:permitType application) "2.1.6")
      (validator/validate xml_218_s (:permitType application) "2.1.8")
      (validator/validate xml_220_s (:permitType application) "2.2.0")
      (validator/validate xml_222_s (:permitType application) "2.2.2")
      (validator/validate xml_223_s (:permitType application) "2.2.3")
      (validator/validate xml_224_s (:permitType application) "2.2.4"))))

(facts "Rakennusvalvonta type of permits to canonical and then to xml with schema validation"
  (against-background
    (org/pate-scope? irrelevant) => false
    (org/get-application-organization anything) => {})
  (fact "Rakennuslupa application -> canonical -> xml"
    (do-test application-rakennuslupa :validate-tyonjohtaja-type :v1 :validate-pysyva-tunnus? true :validate-operations? true))

  (fact "Ty\u00f6njohtaja application -> canonical -> xml"
    (do-test application-tyonjohtajan-nimeaminen-v2 :validate-tyonjohtaja-type :v2))

  (fact "Suunnittelija application -> canonical -> xml"
    (do-test application-suunnittelijan-nimeaminen))

  (fact "Aloitusoikeus -> canonical -> xml"
    (do-test aloitusoikeus-hakemus :finnish? true)))


(facts "Rakennusvalvonta tests"
  (against-background
    (org/pate-scope? irrelevant) => false
    (org/get-application-organization anything) => {})
  (let [canonical (rakval-application-to-canonical application-rakennuslupa "fi")
        xml_220 (rakennuslupa-element-to-xml canonical "2.2.0")
        xml_222 (rakennuslupa-element-to-xml canonical "2.2.2")
        xml_223 (rakennuslupa-element-to-xml canonical "2.2.3")
        xml_224 (rakennuslupa-element-to-xml canonical "2.2.4")
        xml_220_s (indent-str xml_220)
        xml_222_s (indent-str xml_222)
        xml_223_s (indent-str xml_223)
        xml_224_s (indent-str xml_224)
        lp-xml_220 (cr/strip-xml-namespaces (xml/parse xml_220_s))
        lp-xml_222 (cr/strip-xml-namespaces (xml/parse xml_222_s))
        lp-xml_223 (cr/strip-xml-namespaces (xml/parse xml_223_s))
        lp-xml_224 (cr/strip-xml-namespaces (xml/parse xml_224_s))
        get-xml-by-version #(-> (rakennuslupa-element-to-xml (rakval-application-to-canonical
                                                               application-kayttotarkoitus-rakennusluokka
                                                               "fi") %)
                                indent-str
                                xml/parse
                                cr/strip-xml-namespaces)]

    (facts "2.2.0"
      (fact "avainsanatieto"
        (xml/select lp-xml_220 [:avainsanaTieto :Avainsana]) => [])
      (fact "Suunnitelijat"
        (count (xml/select lp-xml_220 [:suunnittelijatieto :Suunnittelija])) => 4)
      (fact "Osapuolet"
        (count (xml/select lp-xml_222 [:osapuolettieto :Osapuoli])) => 5)
      (fact "menettelyTOS"
        (xml/get-text lp-xml_220 [:menettelyTOS]) => nil)
      (fact "rakennustietojEiMmutetaKytkin does not exist"
        (xml/get-text lp-xml_220 [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]) => nil)
      (fact "laajennuksen rakennusoikeudellinenKerrosala"
        (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]) => "160")
      (fact "lisatiedot / asiakirjatToimitettuPvm does not exist yet"
        (xml/get-text lp-xml_220 [:lisatiedot :Lisatiedot :asiakirjatToimitettuPvm]) => ss/blank?))

    (facts "2.2.2"
      (fact "avainsanatieto"
        (->> (xml/select lp-xml_222 [:avainsanaTieto :Avainsana])
             (map :content)) => [["avainsana"]
                                 ["toinen avainsana"]])
      (fact "Suunnitelijat"
        (count (xml/select lp-xml_222 [:suunnittelijatieto :Suunnittelija])) => 4)
      (fact "Osapuolet"
        (count (xml/select lp-xml_222 [:osapuolettieto :Osapuoli])) => 5)
      (fact "menettelyTOS"
        (xml/get-text lp-xml_222 [:menettelyTOS]) => "tos menettely")
      (fact "rakennustietojEiMmutetaKytkin"
        (xml/get-text lp-xml_222 [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]) => "true")
      (fact "laajennuksen rakennusoikeudellinenKerrosala"
        (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]) => "160")
      (fact "lisatiedot / asiakirjatToimitettuPvm"
        (xml/get-text lp-xml_222 [:lisatiedot :Lisatiedot :asiakirjatToimitettuPvm]) => asiakirjat-toimitettu-checker)
      (fact "tilapainenRakennuskytkin"
        (xml/get-text lp-xml_222 [:Rakennus :tilapainenRakennusKytkin]) => nil)
      (fact "tilapainenRakennuskytkin"
        (xml/get-text lp-xml_222 [:Rakennus :tilapainenRakennusvoimassaPvm]) => nil)

      (facts "2.2.3"
        (fact "avainsanatieto"
          (->> (xml/select lp-xml_223 [:avainsanaTieto :Avainsana])
               (map :content)) => [["avainsana"]
                                   ["toinen avainsana"]])
        (fact "Suunnitelijat"
          (count (xml/select lp-xml_223 [:suunnittelijatieto :Suunnittelija])) => 4)
        (fact "Osapuolet"
          (count (xml/select lp-xml_223 [:osapuolettieto :Osapuoli])) => 5)
        (fact "menettelyTOS"
          (xml/get-text lp-xml_223 [:menettelyTOS]) => "tos menettely")
        (fact "rakennustietojEiMmutetaKytkin"
          (xml/get-text lp-xml_223 [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]) => "true")
        (fact "laajennuksen rakennusoikeudellinenKerrosala"
          (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]) => "160")
        (fact "lisatiedot / asiakirjatToimitettuPvm"
          (xml/get-text lp-xml_223 [:lisatiedot :Lisatiedot :asiakirjatToimitettuPvm]) => asiakirjat-toimitettu-checker)
        (fact "tilapainenRakennuskytkin"
          (xml/get-text lp-xml_223 [:Rakennus :tilapainenRakennusKytkin]) => "true")
        (fact "tilapainenRakennuskytkin"
          (xml/get-text lp-xml_223 [:Rakennus :tilapainenRakennusvoimassaPvm]) => (date/xml-date "2019-05-05"))
        (fact "Rakennusluokka not enabled"
          (let [new-building (fn [version] (filter #(= ["kerrostalo-rivitalo-id"]
                                                       (-> (xml/select % [:rakennustunnus :muuTunnustieto :MuuTunnus :tunnus])
                                                           first :content))
                                                   (xml/select (get-xml-by-version version) [:rakennustieto :Rakennus])))]

            (xml/get-text (new-building "2.2.4") [:rakennuksenTiedot :rakennusluokka])
            => nil))))))

(facts "Schema flags"
  (against-background
    (org/pate-scope? irrelevant) => false
    (org/get-application-organization anything) => {:rakennusluokat-enabled true
                                                    :krysp                  {:R {:version "2.2.4"}}})
  (let [canonical          (rakval-application-to-canonical application-rakennuslupa "fi")
        xml_220            (rakennuslupa-element-to-xml canonical "2.2.0")
        xml_223            (rakennuslupa-element-to-xml canonical "2.2.3")
        xml_224            (rakennuslupa-element-to-xml canonical "2.2.4")
        xml_220_s          (indent-str xml_220)
        xml_223_s          (indent-str xml_223)
        xml_224_s          (indent-str xml_224)
        lp-xml_220         (cr/strip-xml-namespaces (xml/parse xml_220_s))
        lp-xml_223         (cr/strip-xml-namespaces (xml/parse xml_223_s))
        lp-xml_224         (cr/strip-xml-namespaces (xml/parse xml_224_s))
        get-xml-by-version #(-> (rakennuslupa-element-to-xml (rakval-application-to-canonical
                                                               application-kayttotarkoitus-rakennusluokka
                                                               "fi") %)
                                indent-str
                                xml/parse
                                cr/strip-xml-namespaces)]
    (facts "2.2.3 and 2.2.4"
      (doseq [[lp-xml krysp-version] [[lp-xml_223 "2.2.3"] [lp-xml_224 "2.2.4"]]]
        (fact "avainsanatieto"
          (->> (xml/select lp-xml [:avainsanaTieto :Avainsana])
               (map :content)) => [["avainsana"]
                                   ["toinen avainsana"]])
        (fact "Suunnittelijat"
          (count (xml/select lp-xml [:suunnittelijatieto :Suunnittelija])) => 4)
        (fact "Osapuolet"
          (count (xml/select lp-xml [:osapuolettieto :Osapuoli])) => 5)
        (fact "menettelyTOS"
          (xml/get-text lp-xml [:menettelyTOS]) => "tos menettely")
        (fact "rakennustietojEiMmutetaKytkin"
          (xml/get-text lp-xml [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]) => "true")
        (fact "laajennuksen rakennusoikeudellinenKerrosala"
          (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]) => "160")
        (fact "lisatiedot / asiakirjatToimitettuPvm"
          (xml/get-text lp-xml [:lisatiedot :Lisatiedot :asiakirjatToimitettuPvm]) => asiakirjat-toimitettu-checker)
        (fact "tilapainenRakennuskytkin"
          (xml/get-text lp-xml [:Rakennus :tilapainenRakennusKytkin]) => "true")
        (fact "tilapainenRakennuskytkin"
          (xml/get-text lp-xml [:Rakennus :tilapainenRakennusvoimassaPvm]) => (date/xml-date "2019-05-05"))

        (facts "huoneistot"
          (let [huoneistot (xml/select lp-xml [:Rakennus :asuinhuoneistot])]
            (fact "huoneistotunnus"
              (-> huoneistot first (xml/select1 [:huoneistotunnus]) (xml/get-text [:huoneistonumero]))
              => "001")

            (if (util/compare-version >= krysp-version "2.2.4")
              (fact "valtakunnallinenHuoneistotunnus"
                (-> huoneistot first (xml/get-text [:valtakunnallinenHuoneistotunnus]))
                => (if (util/compare-version >= krysp-version "2.2.4")
                     "123456789"
                     nil)))))

        (let [new-building (fn [version] (filter #(= ["kerrostalo-rivitalo-id"]
                                                     (-> (xml/select % [:rakennustunnus :muuTunnustieto :MuuTunnus :tunnus])
                                                         first :content))
                                                 (xml/select (get-xml-by-version version) [:rakennustieto :Rakennus])))]

          (fact "kayttotarkoitus"
            (xml/get-text (new-building krysp-version) [:rakennuksenTiedot :kayttotarkoitus]) => "032 luhtitalot")

          (fact "Rakennusluokka"
            (if (util/compare-version >= krysp-version "2.2.4")
              (xml/get-text (new-building krysp-version) [:rakennuksenTiedot :rakennusluokka]) => "0110 omakotitalot"
              (xml/get-text (new-building krysp-version) [:rakennuksenTiedot :rakennusluokka]) => nil)))))))

(facts "Rakennelma"
  (let [canonical (rakval-application-to-canonical application-aurinkopaneeli "fi")
        xml_218 (rakennuslupa-element-to-xml canonical "2.1.8")
        xml_220 (rakennuslupa-element-to-xml canonical "2.2.0")
        xml_222 (rakennuslupa-element-to-xml canonical "2.2.2")
        xml_223 (rakennuslupa-element-to-xml canonical "2.2.3")
        xml_224 (rakennuslupa-element-to-xml canonical "2.2.4")
        xml_218_s (indent-str xml_218)
        xml_220_s (indent-str xml_220)
        xml_222_s (indent-str xml_222)
        xml_223_s (indent-str xml_223)
        xml_224_s (indent-str xml_224)]

    (validator/validate xml_218_s :R "2.1.8")
    (validator/validate xml_220_s :R "2.2.0")
    (validator/validate xml_222_s :R "2.2.2")
    (validator/validate xml_223_s :R "2.2.3")
    (validator/validate xml_223_s :R "2.2.4")

    (facts "2.1.8"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_218_s))
            toimenpide (xml/select1 lp-xml [:Toimenpide])
            rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (fact "yksilointitieto" (xml/get-text toimenpide [:yksilointitieto]) => "muu-rakentaminen-id")
        (fact "kuvaus"          (xml/get-text rakennelma [:kuvaus :kuvaus])  => "virtaa maailmaan")
        (fact "kayttotarkoitus" (xml/get-text rakennelma [:kayttotarkoitus]) => nil)
        (fact "rakennusoikeudellinenKerrosala" (xml/get-text rakennelma [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]) => nil)))

    (facts "2.2.0"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_220_s))
            toimenpide (xml/select1 lp-xml [:Toimenpide])
            rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (fact "yksilointitieto" (xml/get-text toimenpide [:yksilointitieto]) => "muu-rakentaminen-id")
        (fact "kuvaus"          (xml/get-text rakennelma [:kuvaus :kuvaus])  => "virtaa maailmaan")
        (fact "kayttotarkoitus" (xml/get-text rakennelma [:kayttotarkoitus]) => "Muu rakennelma")))

    (facts "2.2.2"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_222_s))
            toimenpide (xml/select1 lp-xml [:Toimenpide])
            rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (fact "yksilointitieto"                 (xml/get-text toimenpide [:yksilointitieto]) => "muu-rakentaminen-id")
        (fact "kuvaus"                          (xml/get-text rakennelma [:kuvaus :kuvaus])  => "virtaa maailmaan")
        (fact "kayttotarkoitus"                 (xml/get-text rakennelma [:kayttotarkoitus]) => "Aurinkopaneeli")
        (fact "tilapainenRakennelmaKytkin"      (xml/get-text rakennelma [:tilapainenRakennelmaKytkin]) => nil)
        (fact "tilapainenRakennelmavoimassaPvm" (xml/get-text rakennelma [:tilapainenRakennelmavoimassaPvm]) => nil)
        (fact "kokoontumistilanHenkilomaara" (xml/get-text rakennelma [:kokoontumistilanHenkilomaara]) => nil)))

    (facts "2.2.3 and 2.2.4"
      (doseq [krysp-version [(cr/strip-xml-namespaces (xml/parse xml_223_s)) (cr/strip-xml-namespaces (xml/parse xml_224_s))]]
        (let [lp-xml krysp-version
              toimenpide (xml/select1 lp-xml [:Toimenpide])
              rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (fact "yksilointitieto"            (xml/get-text toimenpide [:yksilointitieto]) => "muu-rakentaminen-id")
        (fact "kuvaus"                     (xml/get-text rakennelma [:kuvaus :kuvaus])  => "virtaa maailmaan")
        (fact "kayttotarkoitus"            (xml/get-text rakennelma [:kayttotarkoitus]) => "Aurinkopaneeli")
        (fact "tilapainenRakennelmaKytkin" (xml/get-text rakennelma [:tilapainenRakennelmaKytkin]) => "true")
        (fact "tilapainenRakennelmaKytkin" (xml/get-text rakennelma [:tilapainenRakennelmavoimassaPvm])
              => (date/xml-date "2019-11-10")))))))

(facts "Tyonjohtajan sijaistus"
  (let [canonical (rakval-application-to-canonical application-tyonjohtajan-nimeaminen-v2 "fi")
        xml_213 (rakennuslupa-element-to-xml canonical "2.1.3")
        xml_215 (rakennuslupa-element-to-xml canonical "2.1.5")
        xml_213_s (indent-str xml_213)
        xml_215_s (indent-str xml_215)]

    (validator/validate xml_213_s (:permitType application-tyonjohtajan-nimeaminen-v2) "2.1.3")
    (validator/validate xml_215_s (:permitType application-tyonjohtajan-nimeaminen-v2) "2.1.5")

    (facts "2.1.3"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_213_s))
            tyonjohtaja (xml/select1 lp-xml [:Tyonjohtaja])
            sijaistus (xml/select1 lp-xml [:Sijaistus])]
        (fact "sijaistettavan nimi" (xml/get-text sijaistus [:sijaistettavaHlo]) => "Jaska Jokunen")
        (fact "sijaistettava rooli" (xml/get-text sijaistus [:sijaistettavaRooli]) => (xml/get-text lp-xml [:tyonjohtajaRooliKoodi]))
        (fact "sijaistettavan alkamisPvm" (xml/get-text sijaistus [:alkamisPvm]) => (date/xml-date "2014-02-13"))
        (fact "sijaistettavan paattymisPvm" (xml/get-text sijaistus [:paattymisPvm]) => (date/xml-date "2014-02-20"))
        (fact "postiosoite"
          (xml/get-text tyonjohtaja [:yritys :postiosoite :osoitenimi :teksti]) => "katu")))

    (facts "2.1.5"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_215_s))
            tyonjohtaja (xml/select1 lp-xml [:Tyonjohtaja])
            vastuu (xml/select tyonjohtaja [:vastattavaTyotieto])]
        (fact "rooli" (xml/get-text tyonjohtaja [:tyonjohtajaRooliKoodi]) => "KVV-ty\u00f6njohtaja")
        (fact "sijaistettavan nimi" (xml/get-text tyonjohtaja [:sijaistettavaHlo]) => "Jaska Jokunen")
        (fact "2 vastuuta" (count vastuu) => 2)
        (fact "vastuun alkamisPvm" (xml/get-text tyonjohtaja [:alkamisPvm]) => (date/xml-date "2014-02-13"))
        (fact "vastuun paattymisPvm" (xml/get-text tyonjohtaja [:paattymisPvm]) => (date/xml-date "2014-02-20"))
        (fact "vastattavaTyo"
          (map #(xml/get-text % [:vastattavaTyo]) vastuu) => (just #{"Sis\u00e4puolinen KVV-ty\u00f6"
                                                                     "Muu tyotehtava"}))
        (fact "postiosoite"
          (xml/get-text tyonjohtaja [:yritys :postiosoitetieto :postiosoite :osoitenimi :teksti]) => "katu")))))


(def suunnittelijan-nimaminen-canonical (rakval-application-to-canonical application-suunnittelijan-nimeaminen "fi"))

(facts "Suunnittelija"

  (facts "2.1.2"
    (let [xml_s (-> suunnittelijan-nimaminen-canonical (rakennuslupa-element-to-xml "2.1.2") indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces (xml/select1 [:Suunnittelija]))]

      (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.1.2")

      (fact "suunnittelijaRoolikoodi" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]) => "ei tiedossa")
      (fact "VRKrooliKoodi"           (xml/get-text suunnittelija [:VRKrooliKoodi])           => "erityissuunnittelija")
      (fact "postitetaanKytkin"       (xml/get-text suunnittelija [:postitetaanKytkin])       => nil)
      (fact "patevyysvaatimusluokka"  (xml/get-text suunnittelija [:patevyysvaatimusluokka])  => "B")
      (fact "koulutus"                (xml/get-text suunnittelija [:koulutus])                => "arkkitehti")
      (fact "valmistumisvuosi"        (xml/get-text suunnittelija [:valmistumisvuosi])        => "2010")
      (fact "vaadittuPatevyysluokka"  (xml/get-text suunnittelija [:vaadittuPatevyysluokka])  => "C")
      (fact "yritys - nimi"           (xml/get-text suunnittelija [:yritys :nimi])            => "Solita Oy")))

  (facts "2.2.0"
    (let [xml_s (-> suunnittelijan-nimaminen-canonical (rakennuslupa-element-to-xml "2.2.0") indent-str)
          lp_xml                               (-> xml_s xml/parse cr/strip-xml-namespaces)
          suunnittelija                        (xml/select1 lp_xml [:Suunnittelija])
          suunnittelija-with-ulkomainen-hetu   (second (xml/select lp_xml [:Suunnittelija]))
          suunnittelija-using-suomalainen-hetu (get (vec (xml/select lp_xml [:Suunnittelija])) 2)]

      (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.2.0")

      (fact "henkilotunnus"           (xml/get-text suunnittelija [:henkilo :henkilotunnus])            => "210281-9988")
      (fact "suunnittelijaRoolikoodi" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi])           => "rakennusfysikaalinen suunnittelija")
      (fact "VRKrooliKoodi"           (xml/get-text suunnittelija [:VRKrooliKoodi])                     => "erityissuunnittelija")
      (fact "postitetaanKytkin"       (xml/get-text suunnittelija [:postitetaanKytkin])                 => nil)
      (fact "patevyysvaatimusluokka"  (xml/get-text suunnittelija [:patevyysvaatimusluokka])            => "B")
      (fact "koulutus"                (xml/get-text suunnittelija [:koulutus])                          => "arkkitehti")
      (fact "valmistumisvuosi"        (xml/get-text suunnittelija [:valmistumisvuosi])                  => "2010")
      (fact "vaadittuPatevyysluokka"  (xml/get-text suunnittelija [:vaadittuPatevyysluokka])            => "C")
      (fact "kokemusvuodet"           (xml/get-text suunnittelija [:kokemusvuodet])                     => "5")
      (fact "FISEpatevyyskortti"      (xml/get-text suunnittelija [:FISEpatevyyskortti])                => "http://www.ym.fi")
      (fact "FISEkelpoisuus"          (xml/get-text suunnittelija [:FISEkelpoisuus])                    => "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)")
      (fact "yritys - nimi"           (xml/get-text suunnittelija [:yritys :nimi])                      => "Solita Oy")

      (facts "hetu/ulkomainen hetu works correctly"
        (facts "suunnittelija with ulkomainen hetu"
          (xml/get-text suunnittelija-with-ulkomainen-hetu [:suunnittelijaRoolikoodi])
          => "rakennusfysikaalinen suunnittelija"
          (xml/get-text suunnittelija-with-ulkomainen-hetu [:henkilo :henkilotunnus])
          => nil
          (xml/get-text suunnittelija-with-ulkomainen-hetu [:henkilo :ulkomainenHenkilotunnus])
          => nil)
        (facts "suunnittelija using finnish hetu"
          (xml/get-text suunnittelija-using-suomalainen-hetu [:suunnittelijaRoolikoodi])
          => "rakennusfysikaalinen suunnittelija"
          (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :henkilotunnus])
          => "210281-9988"
          (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :ulkomainenHenkilotunnus])
          => nil))))

  (letfn [(suunnittelija>222 [krysp-version]
            (let [xml_s                                (-> suunnittelijan-nimaminen-canonical (rakennuslupa-element-to-xml krysp-version) indent-str)
                  lp_xml                               (-> xml_s xml/parse cr/strip-xml-namespaces)
                  suunnittelija                        (xml/select1 lp_xml [:Suunnittelija])
                  suunnittelija-using-ulkomainen-hetu  (second (xml/select lp_xml [:Suunnittelija]))
                  suunnittelija-using-suomalainen-hetu (get (vec (xml/select lp_xml [:Suunnittelija])) 2)]

              (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) krysp-version)

              (fact "henkilotunnus"           (xml/get-text suunnittelija [:henkilo :henkilotunnus])            => "210281-9988") ;;ONE CASE FOR HETU IS TESTED HERE
              (fact "suunnittelijaRoolikoodi" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi])           => "rakennusfysikaalinen suunnittelija")
              (fact "VRKrooliKoodi"           (xml/get-text suunnittelija [:VRKrooliKoodi])                     => "erityissuunnittelija")
              (fact "postitetaanKytkin"       (xml/get-text suunnittelija [:postitetaanKytkin])                 => nil)
              (fact "patevyysvaatimusluokka"  (xml/get-text suunnittelija [:patevyysvaatimusluokka])            => "B")
              (fact "koulutus"                (xml/get-text suunnittelija [:koulutus])                          => "arkkitehti")
              (fact "valmistumisvuosi"        (xml/get-text suunnittelija [:valmistumisvuosi])                  => "2010")
              (fact "vaadittuPatevyysluokka"  (xml/get-text suunnittelija [:vaadittuPatevyysluokka])            => "C")
              (fact "kokemusvuodet"           (xml/get-text suunnittelija [:kokemusvuodet])                     => "5")
              (fact "FISEpatevyyskortti"      (xml/get-text suunnittelija [:FISEpatevyyskortti])                => "http://www.ym.fi")
              (fact "FISEkelpoisuus"          (xml/get-text suunnittelija [:FISEkelpoisuus])                    => "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)")
              (fact "yritys - nimi"           (xml/get-text suunnittelija [:yritys :nimi])                      => "Solita Oy")

              (facts "hetu/ulkomainen hetu works correctly"
                (facts "suunnittelija with ulkomainen hetu"
                  (xml/get-text suunnittelija-using-ulkomainen-hetu [:suunnittelijaRoolikoodi])
                  => "rakennusfysikaalinen suunnittelija"
                  (xml/get-text suunnittelija-using-ulkomainen-hetu [:henkilo :henkilotunnus])
                  => nil
                  (xml/get-text suunnittelija-using-ulkomainen-hetu [:henkilo :ulkomainenHenkilotunnus])
                  => "123456")
                (facts "suunnittelija using finnish hetu"
                  (xml/get-text suunnittelija-using-suomalainen-hetu [:suunnittelijaRoolikoodi])
                  => "rakennusfysikaalinen suunnittelija"
                  (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :henkilotunnus])
                  => "210281-9988"
                  (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :ulkomainenHenkilotunnus])
                  => nil))))]
    (suunnittelija>222 "2.2.3")
    (suunnittelija>222 "2.2.4")))

(def suunnittelijan-nimaminen-muu-canonical (rakval-application-to-canonical application-suunnittelijan-nimeaminen-muu "fi"))

(facts "Muu suunnittelija"

  (facts "2.1.2"
    (let [xml_s (-> suunnittelijan-nimaminen-muu-canonical (rakennuslupa-element-to-xml "2.1.2") indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces (xml/select1 [:Suunnittelija]))]

      (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.1.2")

      (fact "suunnittelijaRoolikoodi" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]) => "ei tiedossa")
      (fact "muuSuunnittelijaRooli"   (xml/get-text suunnittelija [:muuSuunnittelijaRooli])   => nil)
      (fact "VRKrooliKoodi"           (xml/get-text suunnittelija [:VRKrooliKoodi])           => "erityissuunnittelija")))

  (facts "2.2.0"
    (let [xml_s (-> suunnittelijan-nimaminen-muu-canonical (rakennuslupa-element-to-xml "2.2.0") indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces (xml/select1 [:Suunnittelija]))]

      (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.2.0")

      (fact "suunnittelijaRoolikoodi" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]) => "ei tiedossa")
      (fact "muuSuunnittelijaRooli"   (xml/get-text suunnittelija [:muuSuunnittelijaRooli])   => nil)
      (fact "VRKrooliKoodi"           (xml/get-text suunnittelija [:VRKrooliKoodi])           => "erityissuunnittelija")))

  (facts "2.2.2"
    (let [xml_s (-> suunnittelijan-nimaminen-muu-canonical (rakennuslupa-element-to-xml "2.2.2") indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces (xml/select1 [:Suunnittelija]))]

      (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.2.2")

      (fact "suunnittelijaRoolikoodi" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]) => "muu")
      (fact "muuSuunnittelijaRooli"   (xml/get-text suunnittelija [:muuSuunnittelijaRooli])   => "ei listassa -rooli")
      (fact "VRKrooliKoodi"           (xml/get-text suunnittelija [:VRKrooliKoodi])           => "erityissuunnittelija"))))

(def rakval-jatkolupa-canonical (rakval-application-to-canonical jatkolupa-application "fi"))

(facts "rakval jatkolupa"

  (facts "2.2.2"
    (let [xml_s (-> rakval-jatkolupa-canonical (rakennuslupa-element-to-xml "2.2.2") indent-str)
          app-info (-> xml_s xml/parse cr/strip-xml-namespaces)]

      (validator/validate xml_s (:permitType jatkolupa-application) "2.2.2")
      (fact "asiankuvaus" (xml/get-text app-info [:rakennusvalvontaasianKuvaus]) => "Pari vuotta jatko-aikaa, ett\u00e4 saadaan rakennettua loppuun."))))

(defn get-hakijat [app krysp-version]
  (let [osapuolet (-> (rakval-application-to-canonical app "fi")
                      (rakennuslupa-element-to-xml krysp-version)
                      (indent-str)
                      (xml/parse)
                      (cr/strip-xml-namespaces)
                      (xml/select [:osapuolitieto :Osapuoli]))]
    (filter #(= "hakija" (-> % xml/select cr/all-of :VRKrooliKoodi)) osapuolet)))

(facts "hetu/ulkomainen hetu is working correctly for hakija"
  (facts "2.2.2"
    (let [hakijat                               (get-hakijat application-aurinkopaneeli-with-many-applicants "2.2.2")
          hakija                                (xml/select (first hakijat)) ;;Test case where only finnish hetu has value
          hakija-using-ulkomainen-henkilotunnus (xml/select (second hakijat))
          hakija-using-finnish-hetu             (-> hakijat vec (get 2) xml/select)]
      (xml/get-text hakija [:henkilo :henkilotunnus]) => "210281-9988"
      (xml/get-text hakija [:henkilo :ulkomainenHenkilotunnus]) => nil
      (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :henkilotunnus]) => nil
      (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :ulkomainenHenkilotunnus]) => nil
      (xml/get-text hakija-using-finnish-hetu [:henkilo :henkilotunnus]) => "210281-9988"
      (xml/get-text hakija-using-finnish-hetu [:henkilo :ulkomainenHenkilotunnus]) => nil))
  (facts "2.2.3"
    (let [hakijat                               (get-hakijat application-aurinkopaneeli-with-many-applicants "2.2.3")
          hakija                                (xml/select (first hakijat)) ;;Test case where only finnish hetu has value
          hakija-using-ulkomainen-henkilotunnus (xml/select (second hakijat))
          hakija-using-finnish-hetu             (-> hakijat vec (get 2) xml/select)]
      (xml/get-text hakija [:henkilo :henkilotunnus]) => "210281-9988"
      (xml/get-text hakija [:henkilo :ulkomainenHenkilotunnus]) => nil
      (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :henkilotunnus]) => nil
      (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :ulkomainenHenkilotunnus]) => "123456"
      (xml/get-text hakija-using-finnish-hetu [:henkilo :henkilotunnus]) => "210281-9988"
      (xml/get-text hakija-using-finnish-hetu [:henkilo :ulkomainenHenkilotunnus]) => nil)))

(facts "foreman termination date is working correctly"
  (facts "2.2.2"
    (let [transformed (->> application-tyonjohtajan-nimeaminen-v2
                           (foreman-app-to-foreman-termination-app true 1583331881657))
          tyonjohtaja (-> transformed
                          (rakval-application-to-canonical "fi")
                          (add-paattymispvm-in-foreman-application transformed)
                          (rakennuslupa-element-to-xml "2.2.2")
                          indent-str
                          xml/parse
                          cr/strip-xml-namespaces
                          (xml/select [:tyonjohtajatieto :Tyonjohtaja]))]
      (xml/get-text tyonjohtaja [:paattymisPvm]) => (date/xml-date "2020-03-04"))))

(defn- change-value-in-canonical
  [canonical path value]
  (if canonical
    (map #(assoc-in % path value) canonical)
    canonical))

(defn- helper-function-for-updating-omistaja
  [canonical first-path second-path value]
  (map #(update-in % first-path change-value-in-canonical second-path value) canonical))

(defn- get-element-xml-value
  [canonical krysp-version path]
  (-> (rakennuslupa-element-to-xml canonical krysp-version)
      indent-str
      xml/parse
      cr/strip-xml-namespaces
      (xml/get-text path)))

(facts "new enum values in 2.2.3"
    (against-background
      (org/pate-scope? irrelevant) => false
      (org/get-application-organization anything) => {})
    (let [path-to-rakennuspaikkatieto                 [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :rakennuspaikkatieto]
          inner-path-to-hallintaperuste               [:Rakennuspaikka :rakennuspaikanKiinteistotieto 0 :RakennuspaikanKiinteisto :hallintaperuste]
          path-to-toimenpidetieto                     [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto]
          inner-path-to-paloluokka                    [:Toimenpide :rakennustieto :Rakennus :rakennuksenTiedot :paloluokka]
          path-to-osapuolitieto                       [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :osapuolitieto]
          inner-path-to-kuntaRooliKoodi-in-osapuoli   [:Osapuoli :kuntaRooliKoodi]
          path-between-Toimenpide-omistajatieto       [:Toimenpide :rakennustieto :Rakennus :omistajatieto]
          inner-path-to-kuntaRooliKoodi-in-omistaja   [:Omistaja :kuntaRooliKoodi]
          canonical-application-with-new-enum-values  (-> (rakval-application-to-canonical application-rakennuslupa "fi")
                                                         (update-in path-to-rakennuspaikkatieto change-value-in-canonical inner-path-to-hallintaperuste "muu oikeus")
                                                         (update-in path-to-toimenpidetieto change-value-in-canonical inner-path-to-paloluokka "P0")
                                                         (update-in path-to-osapuolitieto change-value-in-canonical inner-path-to-kuntaRooliKoodi-in-osapuoli "Rakennuksen laajarunkoisen osan arvioija")
                                                         (update-in path-to-toimenpidetieto helper-function-for-updating-omistaja path-between-Toimenpide-omistajatieto inner-path-to-kuntaRooliKoodi-in-omistaja "Rakennuksen laajarunkoisen osan arvioija"))
          canonical-application-with-old-enum-values  (rakval-application-to-canonical application-rakennuslupa "fi")
          hallintaperuste                             [:RakennuspaikanKiinteisto :hallintaperuste]
          paloluokka                                  [:paloluokka]
          osapuoli-kuntaroolikoodi                    [:Osapuoli :kuntaRooliKoodi]
          omistaja-kuntaroolikoodi                    [:Omistaja :kuntaRooliKoodi]]


      (facts "hallintaperuste"

        (fact "new enum value 'muu oikeus' does not change in 2.2.3"
          (get-element-xml-value canonical-application-with-new-enum-values "2.2.3" hallintaperuste)
          => "muu oikeus")

        (fact "new enum value 'muu oikeus' changes to 'ei tiedossa' pre 2.2.3"
          (get-element-xml-value canonical-application-with-new-enum-values "2.2.2" hallintaperuste)
          => "ei tiedossa")

        (fact "old enum value 'oma' does not change  in 2.2.3"
          (get-element-xml-value canonical-application-with-old-enum-values "2.2.3" hallintaperuste)
          => "oma")

        (fact "old enum value 'oma' does not change pre 2.2.3"
          (get-element-xml-value canonical-application-with-old-enum-values "2.2.0" hallintaperuste)
          => "oma"))

      (facts "paloluokka"

        (fact "new enum value 'P0' does not change in 2.2.3"
          (get-element-xml-value canonical-application-with-new-enum-values "2.2.3" paloluokka)
          => "P0")

        (fact "krysp < 2.2.3 does not contain paloluokka when canonical contains paloluokka with P0 value"
          (get-element-xml-value canonical-application-with-new-enum-values "2.2.0" paloluokka)
          => nil)

        (fact "old enum value 'P1' does not change  in 2.2.3"
          (get-element-xml-value canonical-application-with-old-enum-values "2.2.3" paloluokka)
          => "P1")

        (fact "old enum value 'P1' does not change pre 2.2.3"
          (get-element-xml-value canonical-application-with-old-enum-values "2.2.2" paloluokka)
          => "P1"))

      (facts "osapuoli-kuntaroolikoodi"

        (fact "new enum value 'Rakennuksen laajarunkoisen osan arvioija' doesn't change in 2.2.3"
          (get-element-xml-value canonical-application-with-new-enum-values "2.2.3" osapuoli-kuntaroolikoodi)
          => "Rakennuksen laajarunkoisen osan arvioija")

        (fact "new enum value 'Rakennuksen laajarunkoisen osan arvioija' changes to 'ei tiedossa' pre 2.2.3"
          (get-element-xml-value canonical-application-with-new-enum-values "2.2.2" osapuoli-kuntaroolikoodi)
          => "ei tiedossa")

        (fact "old enum value 'Rakennusvalvonta-asian laskun maksaja' doesn't change in 2.2.3"
          (get-element-xml-value canonical-application-with-old-enum-values "2.2.3" osapuoli-kuntaroolikoodi)
          => "Rakennusvalvonta-asian laskun maksaja")

        (fact "old enum value 'Rakennusvalvonta-asian laskun maksaja' doesn't change pre 2.2.3"
          (get-element-xml-value canonical-application-with-old-enum-values "2.2.0" osapuoli-kuntaroolikoodi)
          => "Rakennusvalvonta-asian laskun maksaja")

        (facts "omistaja-kuntaroolikoodi"

          (fact "new enum value 'Rakennuksen laajarunkoisen osan arvioija' doesn't change in 2.2.3"
            (get-element-xml-value canonical-application-with-new-enum-values "2.2.3" omistaja-kuntaroolikoodi)
            => "Rakennuksen laajarunkoisen osan arvioija")

          (fact "new enum value 'Rakennuksen laajarunkoisen osan arvioija' changes to 'Rakennuksen omistaja' pre 2.2.3"
            (get-element-xml-value canonical-application-with-new-enum-values "2.2.2" omistaja-kuntaroolikoodi)
            => "Rakennuksen omistaja")

          (fact "old enum value 'Rakennuksen omistaja' doesn't change in 2.2.3"
            (get-element-xml-value canonical-application-with-old-enum-values "2.2.3" omistaja-kuntaroolikoodi)
            => "Rakennuksen omistaja")

          (fact "old enum value 'Rakennuksen omistaja' doesn't change pre 2.2.3"
            (get-element-xml-value canonical-application-with-old-enum-values "2.2.2" omistaja-kuntaroolikoodi)
            => "Rakennuksen omistaja")))))

(facts "Aiemmalla luvalla hakeminen"
  (against-background
    (org/pate-scope? irrelevant) => false
    (org/get-application-organization anything) => {})
  (letfn [(make-xml ([application version]
                     (-> application
                         (rakval-application-to-canonical "fi")
                         (rakennuslupa-element-to-xml version)
                         indent-str
                         xml/parse
                         cr/strip-xml-namespaces))
            ([application]
             (make-xml application "2.2.2")))
          (entry [v]
            {:modified 11223344 :value v})
          (only-one [xml one]
            (doseq [a    [:uusi :purkaminen :kaupunkikuvaToimenpide :laajennus
                       :uudelleenrakentaminen :muuMuutosTyo]
                    :let [expected (if (= a one) truthy nil)]]
              (fact {:midje/description (str "Only one: " (name a))}
                (xml/select1 xml [:Toimenpide a]) => expected)))]

    (let [xml (make-xml paperilupa-application)]
      (facts "Asian tiedot"
        (xml/get-text xml [:Asiantiedot :rakennusvalvontaasianKuvaus])
        => "Paper description"
        (xml/get-text xml [:Asiantiedot :vahainenPoikkeaminen])
        => "One more thing")

      (facts "Muu muutostyö"
        (only-one xml :muuMuutosTyo)
        (xml/get-text xml [:Toimenpide :muuMuutosTyo :muutostyonLaji])
       => "rakennuksen pääasiallinen käyttötarkoitusmuutos"
       (xml/get-text xml [:Toimenpide :muuMuutosTyo :kuvaus]) => "Paper description"
       (xml/get-text xml [:Toimenpide :muuMuutosTyo :perusparannusKytkin]) => "true"
       (xml/get-text xml [:Toimenpide :muuMuutosTyo :rakennustietojaEimuutetaKytkin]) => "true"))

   (let [xml (make-xml paperilupa-application "2.1.6")]
     (facts "Muu muutostyö: older KuntaGML version"
        (only-one xml :muuMuutosTyo)
        (xml/get-text xml [:Toimenpide :muuMuutosTyo :muutostyonLaji])
       => "rakennuksen pääasiallinen käyttötarkoitusmuutos"
       (xml/get-text xml [:Toimenpide :muuMuutosTyo :kuvaus]) => "Paper description"
       (xml/get-text xml [:Toimenpide :muuMuutosTyo :perusparannusKytkin]) => "true"
       (xml/select1 xml [:Toimenpide :muuMuutosTyo :rakennustietojaEimuutetaKytkin]) => nil))

    (let [xml (make-xml (assoc-in paperilupa-application
                                  [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                  (entry "uusi")))]
      (facts "Uusi"
        (only-one xml :uusi)
        (xml/select1 xml [:Toimenpide :uusi :muutostyonLaji]) => nil
        (xml/get-text xml [:Toimenpide :uusi :kuvaus]) => "Paper description"
        (xml/select1 xml [:Toimenpide :uusi :perusparannusKytkin]) => nil
        (xml/select1 xml [:Toimenpide :uusi :rakennustietojaEimuutetaKytkin]) => nil))

    (let [xml (make-xml (assoc-in paperilupa-application
                                  [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                  (entry "purkaminen")))]
      (facts "Purkaminen"
        (only-one xml :purkaminen)
        (xml/select1 xml [:Toimenpide :purkaminen :muutostyonLaji]) => nil
        (xml/get-text xml [:Toimenpide :purkaminen :kuvaus]) => "Paper description"
        (xml/select1 xml [:Toimenpide :purkaminen :perusparannusKytkin]) => nil
        (xml/select1 xml [:Toimenpide :purkaminen :rakennustietojaEimuutetaKytkin]) => nil))

    (let [xml (make-xml (assoc-in paperilupa-application
                                  [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                  (entry "kaupunkikuvaToimenpide")))]
      (facts "Kaupunkikuvatoimenpide"
        (only-one xml :kaupunkikuvaToimenpide)
        (xml/select1 xml [:Toimenpide :kaupunkikuvaToimenpide :muutostyonLaji]) => nil
        (xml/get-text xml [:Toimenpide :kaupunkikuvaToimenpide :kuvaus]) => "Paper description"
        (xml/select1 xml [:Toimenpide :kaupunkikuvaToimenpide :perusparannusKytkin]) => nil
        (xml/select1 xml [:Toimenpide :kaupunkikuvaToimenpide :rakennustietojaEimuutetaKytkin]) => nil))

    (let [xml (make-xml (assoc-in paperilupa-application
                                  [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                  (entry "laajennus")))]
      (facts "Laajennus"
        (only-one xml :laajennus)
        (xml/select1 xml [:Toimenpide :laajennus :muutostyonLaji]) => nil
        (xml/get-text xml [:Toimenpide :laajennus :kuvaus]) => "Paper description"
        (xml/get-text xml [:Toimenpide :laajennus :perusparannusKytkin]) => "true"
        (xml/select1 xml [:Toimenpide :laajennus :rakennustietojaEimuutetaKytkin]) => nil
        (xml/get-text xml [:Toimenpide :laajennus :laajennuksentiedot :huoneistoala
                           :kayttotarkoitusKoodi]) => "ei tiedossa"))

    (let [xml (make-xml (update-in paperilupa-application
                                   [:documents 1 :data :kuntagml-toimenpide]
                                   #(assoc %
                                           :toimenpide (entry "uudelleenrakentaminen")
                                           :muutostyolaji (entry "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"))))]
      (facts "Uudelleenrakentaminen"
        (only-one xml :uudelleenrakentaminen)
        (xml/get-text xml [:Toimenpide :uudelleenrakentaminen :muutostyonLaji])
        => "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"
        (xml/get-text xml [:Toimenpide :uudelleenrakentaminen :kuvaus]) => "Paper description"
        (xml/get-text xml [:Toimenpide :uudelleenrakentaminen :perusparannusKytkin]) => "true"
        (xml/select1 xml [:Toimenpide :uudelleenrakentaminen :rakennustietojaEimuutetaKytkin]) => nil))

    (facts "check-operations!"
      (fact "All good"
        (doseq [application [application-rakennuslupa
                             application-aurinkopaneeli
                             application-aurinkopaneeli-with-many-applicants
                             application-tyonjohtajan-nimeaminen
                             application-tyonjohtajan-nimeaminen-v2
                             application-suunnittelijan-nimeaminen
                             application-suunnittelijan-nimeaminen-muu
                             jatkolupa-application
                             aloitusoikeus-hakemus
                             application-kayttotarkoitus-rakennusluokka
                             paperilupa-application]]
          (check-operations! application "fi") => nil))
      (fact "Bad toimenpide for paperilupa"
        (check-operations! (assoc-in paperilupa-application
                                     [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                     (entry "bad"))
                           :fi)
        => (throws #"Toimenpiteen tyyppiä ei ole valittu."))
      (fact "Bad toimenpide for regular application is ignored"
        (check-operations! (assoc-in application-aurinkopaneeli
                                     [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                     (entry "bad"))
                           :fi)
        => nil)
      (fact "Missing toimenpide for paperilupa"
        (check-operations! (assoc-in paperilupa-application
                                     [:documents 1 :data :kuntagml-toimenpide]
                                     nil)
                           :sv)
        => (throws #"Typ av åtgärd har inte valts."))
      (fact "Missing toimenpide for regular application is ignored"
        (check-operations! (assoc-in application-rakennuslupa
                                     [:documents 1 :data :kuntagml-toimenpide]
                                     nil)
                           :sv)
        => nil))))
