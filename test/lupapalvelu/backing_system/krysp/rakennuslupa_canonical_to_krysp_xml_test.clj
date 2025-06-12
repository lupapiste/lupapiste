(ns lupapalvelu.backing-system.krysp.rakennuslupa-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :refer [add-paattymispvm-in-foreman-application
                                                                           check-operations!
                                                                           rakennuslupa-element-to-xml
                                                                           rakennuslupa_to_krysp_212
                                                                           rakennuslupa_to_krysp_213
                                                                           rakennuslupa_to_krysp_214
                                                                           rakennuslupa_to_krysp_215
                                                                           rakennuslupa_to_krysp_216
                                                                           rakennuslupa_to_krysp_218
                                                                           rakennuslupa_to_krysp_220
                                                                           rakennuslupa_to_krysp_222
                                                                           rakennuslupa_to_krysp_223
                                                                           rakennuslupa_to_krysp_224
                                                                           rakennuslupa_to_krysp_226]]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.rakennuslupa-canonical :refer [rakval-application-to-canonical]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rakennuslupa-canonical-util :refer [aloitusoikeus-hakemus application-aurinkopaneeli
                                                             application-aurinkopaneeli-with-many-applicants
                                                             application-kayttotarkoitus-rakennusluokka
                                                             application-puhtaan-siirtyman-sijoittamislupa
                                                             application-rakennuslupa
                                                             application-rakennusoikeus-harjakorkeus
                                                             application-rakennusoikeus-tilavuus
                                                             application-sijoittamislupa-v2
                                                             application-sijoittamislupa-v3
                                                             application-suunnittelijan-nimeaminen
                                                             application-suunnittelijan-nimeaminen-muu
                                                             application-tyonjohtajan-nimeaminen
                                                             application-tyonjohtajan-nimeaminen-v2
                                                             application-tyonjohtajan-nimeaminen-v3
                                                             asiakirjat-toimitettu-checker demolition-notice
                                                             drop-etvra jatkolupa-application paperilupa-application
                                                             tyonjohtaja-v3 with-mitat]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.validator :as validator]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(def foreman-app-to-foreman-termination-app
  #'lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system/foreman-app-to-foreman-termination-app)

(def main-app-to-foreman-v3-app
  #'lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system/main-app-to-foreman-v3-app)

(deftest tag-is-set-test
  (testing "2.1.2: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_212))))
  (testing "2.1.3: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_213))))
  (testing "2.1.4: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_214))))
  (testing "2.1.5: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_215))))
  (testing "2.1.6: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_216))))
  (testing "2.1.8: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_218))))
  (testing "2.2.0: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_220))))
  (testing "2.2.2: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_222))))
  (testing "2.2.3: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_223))))
  (testing "2.2.4: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_224))))
  (testing "2.2.6: :tag is set" (is (true? (has-tag rakennuslupa_to_krysp_226)))))

(defn- do-test [application & {:keys [validate-tyonjohtaja-type validate-pysyva-tunnus?
                                      finnish? validate-operations?]
                               :or   {validate-tyonjohtaja-type nil   validate-pysyva-tunnus? false
                                      finnish?                  false validate-operations?    false}}]
  (testing "Rakennusvalvonta KRYSP checks"
    (let [canonical (rakval-application-to-canonical application "fi")
          xml_212   (rakennuslupa-element-to-xml canonical "2.1.2")
          xml_213   (rakennuslupa-element-to-xml canonical "2.1.3")
          xml_214   (rakennuslupa-element-to-xml canonical "2.1.4")
          xml_215   (rakennuslupa-element-to-xml canonical "2.1.5")
          xml_216   (rakennuslupa-element-to-xml canonical "2.1.6")
          xml_218   (rakennuslupa-element-to-xml canonical "2.1.8")
          xml_220   (rakennuslupa-element-to-xml canonical "2.2.0")
          xml_222   (rakennuslupa-element-to-xml canonical "2.2.2")
          xml_223   (rakennuslupa-element-to-xml canonical "2.2.3")
          xml_224   (rakennuslupa-element-to-xml canonical "2.2.4")
          xml_226   (rakennuslupa-element-to-xml canonical "2.2.6")
          xml_212_s (indent-str xml_212)
          xml_213_s (indent-str xml_213)
          xml_214_s (indent-str xml_214)
          xml_215_s (indent-str xml_215)
          xml_216_s (indent-str xml_216)
          xml_218_s (indent-str xml_218)
          xml_220_s (indent-str xml_220)
          xml_222_s (indent-str xml_222)
          xml_223_s (indent-str xml_223)
          xml_224_s (indent-str xml_224)
          xml_226_s (indent-str xml_226)]

      (testing "2.1.2: xml exist" (is (some? xml_212)))
      (testing "2.1.3: xml exist" (is (some? xml_213)))
      (testing "2.1.4: xml exist" (is (some? xml_214)))
      (testing "2.1.5: xml exist" (is (some? xml_215)))
      (testing "2.1.6: xml exist" (is (some? xml_216)))
      (testing "2.1.8: xml exist" (is (some? xml_218)))
      (testing "2.2.0: xml exist" (is (some? xml_220)))
      (testing "2.2.2: xml exist" (is (some? xml_222)))
      (testing "2.2.3: xml exist" (is (some? xml_223)))
      (testing "2.2.4: xml exist" (is (some? xml_224)))
      (testing "2.2.6: xml exist" (is (some? xml_226)))

      (let [lp-xml_212      (cr/strip-xml-namespaces (xml/parse xml_212_s))
            lp-xml_213      (cr/strip-xml-namespaces (xml/parse xml_213_s))
            lp-xml_216      (cr/strip-xml-namespaces (xml/parse xml_216_s))
            _               (cr/strip-xml-namespaces (xml/parse xml_218_s))
            lp-xml_220      (cr/strip-xml-namespaces (xml/parse xml_220_s))
            _               (cr/strip-xml-namespaces (xml/parse xml_222_s))
            tyonjohtaja_212 (xml/select1 lp-xml_212 [:osapuolettieto :Tyonjohtaja])
            tyonjohtaja_213 (xml/select1 lp-xml_213 [:osapuolettieto :Tyonjohtaja])
            tyonjohtaja_216 (xml/select1 lp-xml_216 [:osapuolettieto :Tyonjohtaja])]

        (when validate-operations?
          (testing "Toimenpiteet"
            (let [app-ops        (cons (:primaryOperation application) (:secondaryOperations application))
                                        ; maisematyot sisaltyvat hankkeen kuvaukseen
                  operations     (remove #(= "puun-kaataminen" (:name %)) app-ops)
                  expected       (count operations)
                  xml-operations (xml/select lp-xml_220 [:toimenpidetieto :Toimenpide])]

              (testing "Count" (is (= expected (count xml-operations))))

              (testing "Got buildings"
                (is (pos? (count (xml/select lp-xml_220 [:toimenpidetieto :Toimenpide :Rakennus])))))

              (doseq [op    xml-operations
                      :let  [op-tag (-> op :content first :tag)
                             rakennus (xml/select1 op [:Rakennus])]
                      :when rakennus]

                (when (= :uusi op-tag)
                  (testing "Operation description for a new building"
                    (is (= "A: kerrostalo-rivitalo-kuvaus" (xml/get-text rakennus [:rakennustunnus :rakennuksenSelite])))))

                (testing (str "Building ID includes operation ID of " (name op-tag))
                  (let [ids           (xml/select rakennus [:rakennustunnus :MuuTunnus])
                        toimenpide-id (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "toimenpideId"))]))
                        lupapiste-id  (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "Lupapiste"))]))]
                    (testing "with sovellus=toimenpideId"
                      (is (not (ss/blank? (xml/get-text toimenpide-id [:MuuTunnus :tunnus])))))

                    (testing "with sovellus=Lupapiste"
                      (is (not (ss/blank? (xml/get-text lupapiste-id [:MuuTunnus :tunnus])))))))))))

        (testing "hakija, maksaja and hakijan asiamies parties exist"
          (let [osapuoli-codes (->> (xml/select lp-xml_220 [:osapuolettieto :Osapuoli])
                                    (map (comp :kuntaRooliKoodi cr/all-of)))]
            (is (pos? (->> osapuoli-codes (filter #(= % "Rakennusvalvonta-asian hakija")) count)))
            (when (= (:id application) (:id application-rakennuslupa))
              (testing "has one asiamies"
                (is (= 1 (->> osapuoli-codes (filter #(= % "Hakijan asiamies")) count)))))
            (is (pos? (->> osapuoli-codes (filter #(= % "Rakennusvalvonta-asian laskun maksaja")) count)))))

        (testing "saapumisPvm"
          (let [expected (date/xml-date (:submitted application))]
            (is (= expected (xml/get-text lp-xml_212 [:luvanTunnisteTiedot :LupaTunnus :saapumisPvm])))
            (is (= expected (xml/get-text lp-xml_213 [:luvanTunnisteTiedot :LupaTunnus :saapumisPvm])))))

        (testing "VRKLupatunnus"
          (let [v (xml/get-text lp-xml_212 [:luvanTunnisteTiedot :LupaTunnus :VRKLupatunnus])]
            (is (string? v))
            (is (ss/not-blank? v))))

        (if validate-tyonjohtaja-type
          (do
            (testing "In KRYSP 2.1.2, patevyysvaatimusluokka/vaadittuPatevyysluokka A are mapped to 'ei tiedossa'"
              (is (= "ei tiedossa" (xml/get-text tyonjohtaja_212 :vaadittuPatevyysluokka)))
              (is (= "ei tiedossa" (xml/get-text tyonjohtaja_212 :patevyysvaatimusluokka))))

            (testing "In KRYSP 2.1.3, patevyysvaatimusluokka/vaadittuPatevyysluokka A is not mapped"
              (is (= "A" (xml/get-text tyonjohtaja_213 :patevyysvaatimusluokka)))
              (is (= "A" (xml/get-text tyonjohtaja_213 :vaadittuPatevyysluokka))))

            (when (= :v1 validate-tyonjohtaja-type)
              (testing "FISEpatevyyskortti"
                (is (true? (->> (xml/select lp-xml_220 [:osapuolettieto :Suunnittelija])
                                (map cr/all-of)
                                (every? #(and (-> % :FISEpatevyyskortti string?) (-> % :FISEkelpoisuus string?))))))))

            (when (= :v2 validate-tyonjohtaja-type)
              (testing "In KRYSP 2.1.6, :vainTamaHankeKytkin was added (Yhteiset schema was updated to 2.1.5 and tyonjohtaja along with it)"
                (is (= "true" (xml/get-text tyonjohtaja_216 :vainTamaHankeKytkin))))))
          (do
            (is (nil? tyonjohtaja_212))
            (is (nil? tyonjohtaja_213))))

        (when validate-pysyva-tunnus?
          (testing "pysyva rakennusnumero"
            (is (= "1234567892" (xml/get-text lp-xml_212 [:rakennustunnus :valtakunnallinenNumero]))))))

      (when (= :v1 validate-tyonjohtaja-type)
        (testing "Rakennusvalvonta KRYSP 2.1.5"
          (let [lp-xml_215 (cr/strip-xml-namespaces (xml/parse xml_215_s))]
            ; Address format has changed in 2.1.5
            (is (= "katu" (xml/get-text lp-xml_215 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :osoitenimi :teksti])))
            (is (= "Tuonela" (xml/get-text lp-xml_215 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :postitoimipaikannimi])))

            ; E-Invoicing fields added in 2.1.5
            (is (= "003712345671" (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :ovtTunnus])))
            (is (= "laskutunnus-1234" (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :verkkolaskuTunnus])))
            (is (= "BAWCFI22" (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :valittajaTunnus]))))))

      (testing "Country information"
        (let [lp-xml_220 (cr/strip-xml-namespaces (xml/parse xml_220_s))
              parties    (xml/select lp-xml_220 [:osapuolitieto :Osapuoli])
              applicant  (some #(and (= "hakija" (xml/get-text % [:VRKrooliKoodi])) %) parties)]
          ;; Foreign addresses were already in 2.1.5 mapping, but implemented in 2.2.0
          (when (= :v1 validate-tyonjohtaja-type)
            (is (= "Kiina" (xml/get-text lp-xml_220 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :valtioSuomeksi])))
            (is (= "CHN" (xml/get-text lp-xml_220 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :valtioKansainvalinen]))))

          (if finnish?
            (do (is (= "Suomi" (xml/get-text applicant [:valtioSuomeksi])))
                (is (= "FIN" (xml/get-text applicant [:valtioKansainvalinen]))))
            (do (is (= "Kiina" (xml/get-text applicant [:valtioSuomeksi])))
                (is (= "CHN" (xml/get-text applicant [:valtioKansainvalinen])))
                (is (= "Tuonela" (xml/get-text applicant [:ulkomainenPostitoimipaikka])))
                (is (= "katu" (xml/get-text applicant [:ulkomainenLahiosoite])))))))

      (is (nil? (validator/validate xml_212_s (:permitType application) "2.1.2")))
      (is (nil? (validator/validate xml_213_s (:permitType application) "2.1.3")))
      (is (nil? (validator/validate xml_214_s (:permitType application) "2.1.4")))
      (is (nil? (validator/validate xml_215_s (:permitType application) "2.1.5")))
      (is (nil? (validator/validate xml_216_s (:permitType application) "2.1.6")))
      (is (nil? (validator/validate xml_218_s (:permitType application) "2.1.8")))
      (is (nil? (validator/validate xml_220_s (:permitType application) "2.2.0")))
      (is (nil? (validator/validate xml_222_s (:permitType application) "2.2.2")))
      (is (nil? (validator/validate xml_223_s (:permitType application) "2.2.3")))
      (is (nil? (validator/validate xml_224_s (:permitType application) "2.2.4")))
      (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6"))))))

(deftest permits-to-canonical-and-xml-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (testing "Rakennuslupa application -> canonical -> xml"
      (do-test (drop-etvra application-rakennuslupa) :validate-tyonjohtaja-type :v1
               :validate-pysyva-tunnus? true :validate-operations? true))

    (testing "Työnjohtaja application -> canonical -> xml"
      (do-test application-tyonjohtajan-nimeaminen-v2 :validate-tyonjohtaja-type :v2))

    (testing "Suunnittelija application -> canonical -> xml"
      (do-test application-suunnittelijan-nimeaminen))

    (testing "Aloitusoikeus -> canonical -> xml"
      (do-test aloitusoikeus-hakemus :finnish? true))))

(deftest etvra-226-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (testing "Rakennusvalvonta KuntaGML checks"
      (let [application application-rakennuslupa
            canonical   (rakval-application-to-canonical application "fi")
            xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
            xml_226_s   (indent-str xml_226)
            lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]

        (testing "2.2.6: xml exist" (is (some? xml_226)))

        (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))

        (testing "yksilointitieto present"
          (is (= "etvra-id"
                 (xml/get-text lp-xml_226 [:toimenpidetieto :Toimenpide
                                           :erityistaToimintaavartenRakennettavaAlueTieto :yksilointitieto]))))
        (testing "document description present"
          (is (= "Torille!"
                 (xml/get-text lp-xml_226 [:toimenpidetieto :Toimenpide
                                           :erityistaToimintaavartenRakennettavaAlueTieto :kuvaus :kuvaus]))))
        (testing "alueenTyyppi present"
          (is (= "Tori"
                 (xml/get-text lp-xml_226 [:toimenpidetieto :Toimenpide
                                           :erityistaToimintaavartenRakennettavaAlueTieto :alueenTyyppi]))))
        (testing "alueenPintaAla present"
          (is (= "123"
                 (xml/get-text lp-xml_226 [:toimenpidetieto :Toimenpide
                                           :erityistaToimintaavartenRakennettavaAlueTieto :alueenPintaAla]))))))))

(deftest rakennusoikeus-valinta-226-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (testing "rakennusoikeudellinenKerrosala"
      (testing "Rakennusvalvonta KuntaGML checks"
        (let [application application-rakennuslupa
              canonical   (rakval-application-to-canonical application "fi")
              xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
              xml_226_s   (indent-str xml_226)
              lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]

          (testing "2.2.6: xml exist" (is (some? xml_226)))

          (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))

          (let [building (first (xml/select lp-xml_226 [:toimenpidetieto :Toimenpide :rakennustieto :Rakennus]))]
            (testing "rakennusoikeudellinenKerrosala given"
              (is (= "160" (xml/get-text building [:rakennuksenTiedot :rakennusoikeudellinenKerrosala]))))
            (testing "rakennusoikeusKuutiometreina not given"
              (is (nil? (xml/get-text building [:rakennuksenTiedot :rakennusoikeusKuutiometreina]))))
            (testing "harjanKorkeusrakennusalalla not given"
              (is (nil? (xml/get-text building [:rakennuksenTiedot :harjanKorkeusrakennusalalla]))))))))

    (testing "rakennusoikeusKuutiometreina"
      (testing "Rakennusvalvonta KuntaGML checks"
        (let [application application-rakennusoikeus-tilavuus
              canonical   (rakval-application-to-canonical application "fi")
              xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
              xml_226_s   (indent-str xml_226)
              lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]

          (testing "2.2.6: xml exist" (is (some? xml_226)))

          (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))

          (let [building (first (xml/select lp-xml_226 [:toimenpidetieto :Toimenpide :rakennustieto :Rakennus]))]
            (testing "rakennusoikeudellinenKerrosala not given"
              (is (nil? (xml/get-text building [:rakennuksenTiedot :rakennusoikeudellinenKerrosala]))))
            (testing "rakennusoikeusKuutiometreina given"
              (is (= "123" (xml/get-text building [:rakennuksenTiedot :rakennusoikeusKuutiometreina]))))
            (testing "harjanKorkeusrakennusalalla not given"
              (is (nil? (xml/get-text building [:rakennuksenTiedot :harjanKorkeusrakennusalalla]))))))))

    (testing "harjanKorkeusrakennusalalla"
      (testing "Rakennusvalvonta KuntaGML checks"
        (let [application application-rakennusoikeus-harjakorkeus
              canonical   (rakval-application-to-canonical application "fi")
              xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
              xml_226_s   (indent-str xml_226)
              lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]

          (testing "2.2.6: xml exist" (is (some? xml_226)))

          (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))

          (let [building (first (xml/select lp-xml_226 [:toimenpidetieto :Toimenpide :rakennustieto :Rakennus]))]
            (testing "rakennusoikeudellinenKerrosala not given"
              (is (nil? (xml/get-text building [:rakennuksenTiedot :rakennusoikeudellinenKerrosala]))))
            (testing "rakennusoikeusKuutiometreina given"
              (is (= "123" (xml/get-text building [:rakennuksenTiedot :rakennusoikeusKuutiometreina]))))
            (testing "harjanKorkeusrakennusalalla given"
              (is (= "4.57" (xml/get-text building [:rakennuksenTiedot :harjanKorkeusrakennusalalla])))))))

      (testing "invalid or missing values"
        (testing "rakennusoikeusKuutiometreina missing"
          (let [application (-> application-rakennusoikeus-harjakorkeus
                                (with-mitat {:rakennusoikeusValinta        {:value "harjakorkeus"}
                                             :rakennusoikeusKuutiometreina {:value ""}}))
                canonical   (rakval-application-to-canonical application "fi")]
            (testing "rakennusoikeusKuutiometreina must be present"
              (is (thrown-with-msg? Exception
                                    #"rakennusoikeusKuutiometreina and harjanKorkeusrakennusalalla must be both set!"
                                    (rakennuslupa-element-to-xml canonical "2.2.6"))))))
        (testing "harjanKorkeusrakennusalalla missing"
          (let [application (-> application-rakennusoikeus-harjakorkeus
                                (with-mitat {:rakennusoikeusValinta       {:value "harjakorkeus"}
                                             :harjanKorkeusrakennusalalla {:value ""}}))
                canonical   (rakval-application-to-canonical application "fi")]
            (testing "rakennusoikeusKuutiometreina must be present"
              (is (thrown-with-msg? Exception
                                    #"rakennusoikeusKuutiometreina and harjanKorkeusrakennusalalla must be both set!"
                                    (rakennuslupa-element-to-xml canonical "2.2.6"))))))
        (testing "both are missing? -> fine"
          (let [application (-> application-rakennusoikeus-harjakorkeus
                                (with-mitat {:rakennusoikeusValinta        {:value "harjakorkeus"}
                                             :harjanKorkeusrakennusalalla  {:value ""}
                                             :rakennusoikeusKuutiometreina {:value ""}}))
                canonical   (rakval-application-to-canonical application "fi")
                xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
                xml_226_s   (indent-str xml_226)
                lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]
            (testing "2.2.6: xml exist" (is (some? xml_226)))
            (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))
            (let [building (first (xml/select lp-xml_226 [:toimenpidetieto :Toimenpide :rakennustieto :Rakennus]))]
              (testing "rakennusoikeudellinenKerrosala not given"
                (is (nil? (xml/get-text building [:rakennuksenTiedot :rakennusoikeudellinenKerrosala]))))
              (testing "rakennusoikeusKuutiometreina not given"
                (is (nil? (xml/get-text building [:rakennuksenTiedot :rakennusoikeusKuutiometreina]))))
              (testing "harjanKorkeusrakennusalalla not given"
                (is (nil? (xml/get-text building [:rakennuksenTiedot :harjanKorkeusrakennusalalla])))))))))))

(defn- sijoittamislupa-test-base [test-name application]
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (testing test-name
      (testing "Rakennusvalvonta KuntaGML checks"
        (let [canonical   (rakval-application-to-canonical application "fi")
              xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
              xml_226_s   (indent-str xml_226)
              lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]

          (testing "2.2.6: xml exist" (is (some? xml_226)))

          (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))

          (testing "sijoittamislupa present"
            (is (= "Sijoittamislupa"
                   (xml/get-text lp-xml_226 [:toimenpidetieto :Toimenpide :sijoittamislupa :kuvaus])))))))))

(deftest sijoittamislupa-226-test
  (sijoittamislupa-test-base "2.2.6 with sijoittamislupa"
                             application-sijoittamislupa-v2))

(deftest sijoittamislupa-v3-226-test
  (sijoittamislupa-test-base "2.2.6 with sijoittamislupa-v3"
                             application-sijoittamislupa-v3))

(deftest puhtaan-siirtyman-sijoittamislupa-226-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (testing "Rakennusvalvonta KuntaGML checks"
      (let [application application-puhtaan-siirtyman-sijoittamislupa
            canonical   (rakval-application-to-canonical application "fi")
            xml_226     (rakennuslupa-element-to-xml canonical "2.2.6")
            xml_226_s   (indent-str xml_226)
            lp-xml_226  (cr/strip-xml-namespaces (xml/parse xml_226_s))]

        (testing "2.2.6: xml exist" (is (some? xml_226)))

        (is (nil? (validator/validate xml_226_s (:permitType application) "2.2.6")))

        (testing "sijoittamislupa present"
          (is (= "Puhtaan siirtymän sijoittamislupa"
                 (xml/get-text lp-xml_226 [:toimenpidetieto :Toimenpide :puhtaanSiirtymanSijoittamislupa :kuvaus]))))))))

(defn get-xml-by-version [version]
  (-> (rakennuslupa-element-to-xml (rakval-application-to-canonical
                                     application-kayttotarkoitus-rakennusluokka
                                     "fi")
                                   version)
      indent-str
      xml/parse
      cr/strip-xml-namespaces))

(deftest rakennusvalvonta-tests
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (let [canonical  (rakval-application-to-canonical application-rakennuslupa "fi")
          xml_220    (rakennuslupa-element-to-xml canonical "2.2.0")
          xml_222    (rakennuslupa-element-to-xml canonical "2.2.2")
          xml_223    (rakennuslupa-element-to-xml canonical "2.2.3")
          xml_224    (rakennuslupa-element-to-xml canonical "2.2.4")
          xml_226    (rakennuslupa-element-to-xml canonical "2.2.6")
          xml_220_s  (indent-str xml_220)
          xml_222_s  (indent-str xml_222)
          xml_223_s  (indent-str xml_223)
          xml_224_s  (indent-str xml_224)
          xml_226_s  (indent-str xml_226)
          lp-xml_220 (cr/strip-xml-namespaces (xml/parse xml_220_s))
          lp-xml_222 (cr/strip-xml-namespaces (xml/parse xml_222_s))
          lp-xml_223 (cr/strip-xml-namespaces (xml/parse xml_223_s))
          lp-xml_224 (cr/strip-xml-namespaces (xml/parse xml_224_s))
          lp-xml_226 (cr/strip-xml-namespaces (xml/parse xml_226_s))]

      (is (nil? (validator/validate xml_226_s (:permitType application-rakennuslupa) "2.2.6")))

      (testing "2.2.0"
        (testing "avainsanatieto"
          (is (= [] (xml/select lp-xml_220 [:avainsanaTieto :Avainsana]))))
        (testing "Suunnitelijat"
          (is (= 4 (count (xml/select lp-xml_220 [:suunnittelijatieto :Suunnittelija])))))
        (testing "Osapuolet"
          (is (= 5 (count (xml/select lp-xml_222 [:osapuolettieto :Osapuoli])))))
        (testing "menettelyTOS"
          (is (nil? (xml/get-text lp-xml_220 [:menettelyTOS]))))
        (testing "rakennustietojEiMmutetaKytkin does not exist"
          (is (nil? (xml/get-text lp-xml_220 [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]))))
        (testing "laajennuksen rakennusoikeudellinenKerrosala"
          (is (= "160" (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]))))
        (testing "lisatiedot / asiakirjatToimitettuPvm does not exist yet"
          (is (ss/blank? (xml/get-text lp-xml_220 [:lisatiedot :Lisatiedot :asiakirjatToimitettuPvm])))))

      (testing "2.2.2"
        (testing "avainsanatieto"
          (is (= [["avainsana"] ["toinen avainsana"]]
                 (->> (xml/select lp-xml_222 [:avainsanaTieto :Avainsana])
                      (map :content)))))
        (testing "Suunnitelijat"
          (is (= 4 (count (xml/select lp-xml_222 [:suunnittelijatieto :Suunnittelija])))))
        (testing "Osapuolet"
          (is (= 5 (count (xml/select lp-xml_222 [:osapuolettieto :Osapuoli])))))
        (testing "menettelyTOS"
          (is (= "tos menettely" (xml/get-text lp-xml_222 [:menettelyTOS]))))
        (testing "rakennustietojEiMmutetaKytkin"
          (is (= "true" (xml/get-text lp-xml_222 [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]))))
        (testing "laajennuksen rakennusoikeudellinenKerrosala"
          (is (= "160" (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]))))
        (testing "lisatiedot / asiakirjatToimitettuPvm"
          (is (asiakirjat-toimitettu-checker (xml/get-text lp-xml_222 [:lisatiedot :Lisatiedot
                                                                       :asiakirjatToimitettuPvm]))))
        (testing "tilapainenRakennuskytkin"
          (is (nil? (xml/get-text lp-xml_222 [:Rakennus :tilapainenRakennusKytkin]))))
        (testing "tilapainenRakennusvoimassaPvm"
          (is (nil? (xml/get-text lp-xml_222 [:Rakennus :tilapainenRakennusvoimassaPvm]))))

        (testing "2.2.3"
          (testing "avainsanatieto"
            (is (= [["avainsana"] ["toinen avainsana"]]
                   (->> (xml/select lp-xml_223 [:avainsanaTieto :Avainsana])
                        (map :content)))))
          (testing "Suunnitelijat"
            (is (= 4 (count (xml/select lp-xml_223 [:suunnittelijatieto :Suunnittelija])))))
          (testing "Osapuolet"
            (is (= 5 (count (xml/select lp-xml_223 [:osapuolettieto :Osapuoli])))))
          (testing "menettelyTOS"
            (is (= "tos menettely" (xml/get-text lp-xml_223 [:menettelyTOS]))))
          (testing "rakennustietojEiMmutetaKytkin"
            (is (= "true" (xml/get-text lp-xml_223 [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]))))
          (testing "laajennuksen rakennusoikeudellinenKerrosala"
            (is (= "160" (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot :rakennusoikeudellinenKerrosala]))))
          (testing "lisatiedot / asiakirjatToimitettuPvm"
            (is (asiakirjat-toimitettu-checker (xml/get-text lp-xml_223 [:lisatiedot :Lisatiedot
                                                                         :asiakirjatToimitettuPvm]))))
          (testing "tilapainenRakennuskytkin"
            (is (= "true" (xml/get-text lp-xml_223 [:Rakennus :tilapainenRakennusKytkin]))))
          (testing "tilapainenRakennusvoimassaPvm"
            (is (= (date/xml-date "2019-05-05") (xml/get-text lp-xml_223 [:Rakennus :tilapainenRakennusvoimassaPvm]))))
          (testing "Rakennusluokka not enabled"
            (let [new-building (fn [version] (filter #(= ["kerrostalo-rivitalo-id"]
                                                         (-> (xml/select % [:rakennustunnus :muuTunnustieto
                                                                            :MuuTunnus :tunnus])
                                                             first :content))
                                                     (xml/select (get-xml-by-version version)
                                                                 [:rakennustieto :Rakennus])))]
              (is (nil? (xml/get-text (new-building "2.2.4") [:rakennuksenTiedot :rakennusluokka]))))))

        (testing "esteetonKytkin"
          (let [path [:Rakennus :esteetonKytkin]]
            (testing "< 2.2.6"
              (doseq [lp-xml [lp-xml_220 lp-xml_222 lp-xml_223 lp-xml_224]]
                (testing "no esteetonKytkin"
                  (is (nil? (xml/get-text lp-xml path))))))
            (testing ">= 2.2.6"
              (is (= "true" (xml/get-text lp-xml_226 path))))))))

    (testing "Blank fields are omitted"
      (let [->xml (fn [application]
                    (-> application
                        (rakval-application-to-canonical "fi")
                        (rakennuslupa-element-to-xml "2.2.4")
                        indent-str
                        xml/parse
                        cr/strip-xml-namespaces))
            xml1  (-> application-rakennuslupa
                      (update :documents
                              (partial util/update-by-pred
                                       #(some-> % :data :huoneistot)
                                       (util/fn-> (assoc-in [:data :huoneistot :0 :huoneistoala]
                                                            {:value " " :modified 123456})
                                                  (assoc-in [:data :kokonaisala :value] ""))))
                      ->xml)
            xml2  (-> application-aurinkopaneeli
                      (update :documents
                              (partial util/update-by-pred
                                       #(some-> % :data :kokonaisala)
                                       #(assoc-in % [:data :kokonaisala :value] " ")))
                      ->xml)]
        (testing "No huoneistoala value regression"
          (is (true? (not-any? #(ss/starts-with? (xml/get-text %) "{")
                               (xml/select xml1 [:Rakennus :huoneistoala])))))
        (testing "Half of the apartments are not in the xml"
          (is (= 6 (->> (:documents application-rakennuslupa)
                        (map (util/fn-> :data :huoneistot count))
                        (apply +))))
          (is (= 3 (count (xml/select xml1 [:huoneistonTyyppi])))))
        (testing "No structure kokonaisala"
          (is (empty? (xml/select xml2 [:kokonaisala]))))))))

(deftest schema-flags-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {:rakennusluokat-enabled true
                                                              :krysp                  {:R {:version "2.2.4"}}})]
    (let [canonical          (rakval-application-to-canonical application-rakennuslupa "fi")
          xml_220            (rakennuslupa-element-to-xml canonical "2.2.0")
          xml_223            (rakennuslupa-element-to-xml canonical "2.2.3")
          xml_224            (rakennuslupa-element-to-xml canonical "2.2.4")
          xml_226            (rakennuslupa-element-to-xml canonical "2.2.6")
          xml_220_s          (indent-str xml_220)
          xml_223_s          (indent-str xml_223)
          xml_224_s          (indent-str xml_224)
          xml_226_s          (indent-str xml_226)
          lp-xml_220         (cr/strip-xml-namespaces (xml/parse xml_220_s))
          lp-xml_223         (cr/strip-xml-namespaces (xml/parse xml_223_s))
          lp-xml_224         (cr/strip-xml-namespaces (xml/parse xml_224_s))
          _                  (cr/strip-xml-namespaces (xml/parse xml_226_s))]
      (testing "2.2.3 and 2.2.4"
        (doseq [[lp-xml krysp-version] [[lp-xml_223 "2.2.3"] [lp-xml_224 "2.2.4"]]]
          (testing "avainsanatieto"
            (is (= [["avainsana"] ["toinen avainsana"]]
                   (->> (xml/select lp-xml [:avainsanaTieto :Avainsana])
                        (map :content)))))
          (testing "Suunnittelijat"
            (is (= 4 (count (xml/select lp-xml [:suunnittelijatieto :Suunnittelija])))))
          (testing "Osapuolet"
            (is (= 5 (count (xml/select lp-xml [:osapuolettieto :Osapuoli])))))
          (testing "menettelyTOS"
            (is (= "tos menettely" (xml/get-text lp-xml [:menettelyTOS]))))
          (testing "rakennustietojEiMmutetaKytkin"
            (is (= "true" (xml/get-text lp-xml [:muuMuutosTyo :rakennustietojaEimuutetaKytkin]))))
          (testing "laajennuksen rakennusoikeudellinenKerrosala"
            (is (= "160" (xml/get-text lp-xml_220 [:laajennus :laajennuksentiedot
                                                   :rakennusoikeudellinenKerrosala]))))
          (testing "lisatiedot / asiakirjatToimitettuPvm"
            (is (asiakirjat-toimitettu-checker (xml/get-text lp-xml [:lisatiedot :Lisatiedot
                                                                     :asiakirjatToimitettuPvm]))))
          (testing "tilapainenRakennuskytkin"
            (is (= "true" (xml/get-text lp-xml [:Rakennus :tilapainenRakennusKytkin]))))
          (testing "tilapainenRakennusvoimassaPvm"
            (is (= (date/xml-date "2019-05-05") (xml/get-text lp-xml [:Rakennus :tilapainenRakennusvoimassaPvm]))))

          (testing "huoneistot"
            (let [huoneistot (xml/select lp-xml [:Rakennus :asuinhuoneistot])]
              (testing "huoneistotunnus"
                (is (= "001" (-> huoneistot first (xml/select1 [:huoneistotunnus])
                                 (xml/get-text [:huoneistonumero])))))

              (when (util/compare-version >= krysp-version "2.2.4")
                (testing "valtakunnallinenHuoneistotunnus"
                  (is (= "123456789" (-> huoneistot first (xml/get-text [:valtakunnallinenHuoneistotunnus]))))))))

          (let [new-building (fn [version] (filter #(= ["kerrostalo-rivitalo-id"]
                                                       (-> (xml/select % [:rakennustunnus :muuTunnustieto
                                                                          :MuuTunnus :tunnus])
                                                           first :content))
                                                   (xml/select (get-xml-by-version version)
                                                               [:rakennustieto :Rakennus])))]

            (testing "kayttotarkoitus"
              (is (= "032 luhtitalot" (xml/get-text (new-building krysp-version)
                                                    [:rakennuksenTiedot :kayttotarkoitus]))))

            (testing "Rakennusluokka"
              (if (util/compare-version >= krysp-version "2.2.4")
                (testing "omakotitalot"
                  (is (= "0110 omakotitalot" (xml/get-text (new-building krysp-version)
                                                           [:rakennuksenTiedot :rakennusluokka]))))
                (testing "missing"
                  (is (nil? (xml/get-text (new-building krysp-version)
                                          [:rakennuksenTiedot :rakennusluokka]))))))))))))

(deftest rakennelma-test
  (let [canonical (rakval-application-to-canonical application-aurinkopaneeli "fi")
        xml_218   (rakennuslupa-element-to-xml canonical "2.1.8")
        xml_220   (rakennuslupa-element-to-xml canonical "2.2.0")
        xml_222   (rakennuslupa-element-to-xml canonical "2.2.2")
        xml_223   (rakennuslupa-element-to-xml canonical "2.2.3")
        xml_224   (rakennuslupa-element-to-xml canonical "2.2.4")
        xml_226   (rakennuslupa-element-to-xml canonical "2.2.6")
        xml_218_s (indent-str xml_218)
        xml_220_s (indent-str xml_220)
        xml_222_s (indent-str xml_222)
        xml_223_s (indent-str xml_223)
        xml_224_s (indent-str xml_224)
        xml_226_s (indent-str xml_226)]

    (is (nil? (validator/validate xml_218_s :R "2.1.8")))
    (is (nil? (validator/validate xml_220_s :R "2.2.0")))
    (is (nil? (validator/validate xml_222_s :R "2.2.2")))
    (is (nil? (validator/validate xml_223_s :R "2.2.3")))
    (is (nil? (validator/validate xml_223_s :R "2.2.4")))
    (is (nil? (validator/validate xml_226_s :R "2.2.6")))

    (testing "2.1.8"
      (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_218_s))
            toimenpide (xml/select1 lp-xml [:Toimenpide])
            rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (testing "yksilointitieto" (is (= "muu-rakentaminen-id" (xml/get-text toimenpide [:yksilointitieto]))))
        (testing "kuvaus"          (is (= "virtaa maailmaan" (xml/get-text rakennelma [:kuvaus :kuvaus]))))
        (testing "kayttotarkoitus" (is (nil? (xml/get-text rakennelma [:kayttotarkoitus]))))
        (testing "rakennusoikeudellinenKerrosala" (is (nil? (xml/get-text rakennelma
                                                                          [:laajennus :laajennuksentiedot
                                                                           :rakennusoikeudellinenKerrosala]))))))

    (testing "2.2.0"
      (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_220_s))
            toimenpide (xml/select1 lp-xml [:Toimenpide])
            rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (testing "yksilointitieto" (is (= "muu-rakentaminen-id" (xml/get-text toimenpide [:yksilointitieto]))))
        (testing "kuvaus"          (is (= "virtaa maailmaan" (xml/get-text rakennelma [:kuvaus :kuvaus]))))
        (testing "kayttotarkoitus" (is (= "Muu rakennelma" (xml/get-text rakennelma [:kayttotarkoitus]))))))

    (testing "2.2.2"
      (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_222_s))
            toimenpide (xml/select1 lp-xml [:Toimenpide])
            rakennelma (xml/select1 toimenpide [:Rakennelma])]

        (testing "yksilointitieto"                 (is (= "muu-rakentaminen-id"
                                                          (xml/get-text toimenpide [:yksilointitieto]))))
        (testing "kuvaus"                          (is (= "virtaa maailmaan"
                                                          (xml/get-text rakennelma [:kuvaus :kuvaus]))))
        (testing "kayttotarkoitus"                 (is (= "Aurinkopaneeli"
                                                          (xml/get-text rakennelma [:kayttotarkoitus]))))
        (testing "tilapainenRakennelmaKytkin"      (is (nil? (xml/get-text rakennelma [:tilapainenRakennelmaKytkin]))))
        (testing "tilapainenRakennelmavoimassaPvm" (is (nil? (xml/get-text rakennelma [:tilapainenRakennelmavoimassaPvm]))))
        (testing "kokoontumistilanHenkilomaara"    (is (nil? (xml/get-text rakennelma [:kokoontumistilanHenkilomaara]))))))

    (testing "2.2.3 and 2.2.4"
      (doseq [krysp-version [(cr/strip-xml-namespaces (xml/parse xml_223_s))
                             (cr/strip-xml-namespaces (xml/parse xml_224_s))]]
        (let [lp-xml     krysp-version
              toimenpide (xml/select1 lp-xml [:Toimenpide])
              rakennelma (xml/select1 toimenpide [:Rakennelma])]

          (testing "yksilointitieto"            (is (= "muu-rakentaminen-id"
                                                       (xml/get-text toimenpide [:yksilointitieto]))))
          (testing "kuvaus"                     (is (= "virtaa maailmaan"
                                                       (xml/get-text rakennelma [:kuvaus :kuvaus]))))
          (testing "kayttotarkoitus"            (is (= "Aurinkopaneeli"
                                                       (xml/get-text rakennelma [:kayttotarkoitus]))))
          (testing "tilapainenRakennelmaKytkin" (is (= "true"
                                                       (xml/get-text rakennelma [:tilapainenRakennelmaKytkin]))))
          (testing "tilapainenRakennelmavoimassaPvm"
            (is (= (date/xml-date "2019-11-10") (xml/get-text rakennelma [:tilapainenRakennelmavoimassaPvm])))))))))

(deftest tyonjohtajan-sijaistus-test
  (let [canonical (rakval-application-to-canonical application-tyonjohtajan-nimeaminen-v2 "fi")
        xml_213   (rakennuslupa-element-to-xml canonical "2.1.3")
        xml_215   (rakennuslupa-element-to-xml canonical "2.1.5")
        xml_226   (rakennuslupa-element-to-xml canonical "2.2.6")
        xml_213_s (indent-str xml_213)
        xml_215_s (indent-str xml_215)
        xml_226_s (indent-str xml_226)]

    (is (nil? (validator/validate xml_213_s (:permitType application-tyonjohtajan-nimeaminen-v2) "2.1.3")))
    (is (nil? (validator/validate xml_215_s (:permitType application-tyonjohtajan-nimeaminen-v2) "2.1.5")))
    (is (nil? (validator/validate xml_226_s (:permitType application-tyonjohtajan-nimeaminen-v2) "2.2.6")))

    (testing "2.1.3"
      (let [lp-xml      (cr/strip-xml-namespaces (xml/parse xml_213_s))
            tyonjohtaja (xml/select1 lp-xml [:Tyonjohtaja])
            sijaistus   (xml/select1 lp-xml [:Sijaistus])]
        (testing "sijaistettavan nimi"
          (is (= "Jaska Jokunen" (xml/get-text sijaistus [:sijaistettavaHlo]))))
        (testing "sijaistettava rooli"
          (is (= (xml/get-text lp-xml [:tyonjohtajaRooliKoodi])
                 (xml/get-text sijaistus [:sijaistettavaRooli]))))
        (testing "sijaistettavan alkamisPvm"
          (is (= (date/xml-date "2014-02-13")
                 (xml/get-text sijaistus [:alkamisPvm]))))
        (testing "sijaistettavan paattymisPvm"
          (is (= (date/xml-date "2014-02-20")
                 (xml/get-text sijaistus [:paattymisPvm]))))
        (testing "postiosoite"
          (is (= "katu" (xml/get-text tyonjohtaja [:yritys :postiosoite :osoitenimi :teksti]))))))

    (testing "2.1.5"
      (let [lp-xml      (cr/strip-xml-namespaces (xml/parse xml_215_s))
            tyonjohtaja (xml/select1 lp-xml [:Tyonjohtaja])
            vastuu      (xml/select tyonjohtaja [:vastattavaTyotieto])]
        (testing "rooli"
          (is (= "KVV-työnjohtaja" (xml/get-text tyonjohtaja [:tyonjohtajaRooliKoodi]))))
        (testing "sijaistettavan nimi"
          (is (= "Jaska Jokunen" (xml/get-text tyonjohtaja [:sijaistettavaHlo]))))
        (testing "2 vastuuta"
          (is (= 2 (count vastuu))))
        (testing "vastuun alkamisPvm"
          (is (= (date/xml-date "2014-02-13") (xml/get-text tyonjohtaja [:alkamisPvm]))))
        (testing "vastuun paattymisPvm"
          (is (= (date/xml-date "2014-02-20") (xml/get-text tyonjohtaja [:paattymisPvm]))))
        (testing "vastattavaTyo"
          (is (= #{"Sisäpuolinen KVV-työ" "Muu tyotehtava"}
                 (set (map #(xml/get-text % [:vastattavaTyo]) vastuu)))))
        (testing "postiosoite"
          (is (= "katu" (xml/get-text tyonjohtaja [:yritys :postiosoitetieto
                                                   :postiosoite :osoitenimi :teksti]))))))))

(def suunnittelijan-nimaminen-canonical
  (rakval-application-to-canonical application-suunnittelijan-nimeaminen "fi"))

(def suunnittelijan-nimaminen-muu-canonical
  (rakval-application-to-canonical application-suunnittelijan-nimeaminen-muu "fi"))

(deftest suunnittelija-test
  (testing "2.1.2"
    (let [xml_s (-> suunnittelijan-nimaminen-canonical (rakennuslupa-element-to-xml "2.1.2") indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces (xml/select1 [:Suunnittelija]))]

      (is (nil? (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.1.2")))

      (testing "suunnittelijaRoolikoodi"
        (is (= "ei tiedossa" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]))))
      (testing "VRKrooliKoodi"
        (is (= "erityissuunnittelija" (xml/get-text suunnittelija [:VRKrooliKoodi]))))
      (testing "postitetaanKytkin"
        (is (nil? (xml/get-text suunnittelija [:postitetaanKytkin]))))
      (testing "patevyysvaatimusluokka"
        (is (= "B" (xml/get-text suunnittelija [:patevyysvaatimusluokka]))))
      (testing "koulutus"
        (is (= "arkkitehti" (xml/get-text suunnittelija [:koulutus]))))
      (testing "valmistumisvuosi"
        (is (= "2010" (xml/get-text suunnittelija [:valmistumisvuosi]))))
      (testing "vaadittuPatevyysluokka"
        (is (= "C" (xml/get-text suunnittelija [:vaadittuPatevyysluokka]))))
      (testing "yritys - nimi"
        (is (= "Solita Oy" (xml/get-text suunnittelija [:yritys :nimi]))))))

  (testing "2.2.0"
    (let [xml_s (-> suunnittelijan-nimaminen-canonical (rakennuslupa-element-to-xml "2.2.0") indent-str)
          lp_xml                               (-> xml_s xml/parse cr/strip-xml-namespaces)
          suunnittelija                        (xml/select1 lp_xml [:Suunnittelija])
          suunnittelija-with-ulkomainen-hetu   (second (xml/select lp_xml [:Suunnittelija]))
          suunnittelija-using-suomalainen-hetu (get (vec (xml/select lp_xml [:Suunnittelija])) 2)]

      (is (nil? (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.2.0")))

      (testing "henkilotunnus"
        (is (= "210281-9988" (xml/get-text suunnittelija [:henkilo :henkilotunnus]))))
      (testing "suunnittelijaRoolikoodi"
        (is (= "rakennusfysikaalinen suunnittelija" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]))))
      (testing "VRKrooliKoodi"
        (is (= "erityissuunnittelija" (xml/get-text suunnittelija [:VRKrooliKoodi]))))
      (testing "postitetaanKytkin"
        (is (nil? (xml/get-text suunnittelija [:postitetaanKytkin]))))
      (testing "patevyysvaatimusluokka"
        (is (= "B" (xml/get-text suunnittelija [:patevyysvaatimusluokka]))))
      (testing "koulutus"
        (is (= "arkkitehti" (xml/get-text suunnittelija [:koulutus]))))
      (testing "valmistumisvuosi"
        (is (= "2010" (xml/get-text suunnittelija [:valmistumisvuosi]))))
      (testing "vaadittuPatevyysluokka"
        (is (= "C" (xml/get-text suunnittelija [:vaadittuPatevyysluokka]))))
      (testing "kokemusvuodet"
        (is (= "5" (xml/get-text suunnittelija [:kokemusvuodet]))))
      (testing "FISEpatevyyskortti"
        (is (= "http://www.ym.fi" (xml/get-text suunnittelija [:FISEpatevyyskortti]))))
      (testing "FISEkelpoisuus"
        (is (= "tavanomainen pääsuunnittelu (uudisrakentaminen)" (xml/get-text suunnittelija [:FISEkelpoisuus]))))
      (testing "yritys - nimi"
        (is (= "Solita Oy" (xml/get-text suunnittelija [:yritys :nimi]))))

      (testing "hetu/ulkomainen hetu works correctly"
        (testing "suunnittelija with ulkomainen hetu"
          (is (= "rakennusfysikaalinen suunnittelija"
                 (xml/get-text suunnittelija-with-ulkomainen-hetu [:suunnittelijaRoolikoodi])))
          (is (nil? (xml/get-text suunnittelija-with-ulkomainen-hetu [:henkilo :henkilotunnus])))
          (is (nil? (xml/get-text suunnittelija-with-ulkomainen-hetu [:henkilo :ulkomainenHenkilotunnus]))))
        (testing "suunnittelija using finnish hetu"
          (is (= "rakennusfysikaalinen suunnittelija"
                 (xml/get-text suunnittelija-using-suomalainen-hetu [:suunnittelijaRoolikoodi])))
          (is (= "210281-9988"
                 (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :henkilotunnus])))
          (is (nil? (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :ulkomainenHenkilotunnus])))))))

  (letfn [(suunnittelija>222 [krysp-version]
            (let [xml_s                                (-> suunnittelijan-nimaminen-canonical
                                                           (rakennuslupa-element-to-xml krysp-version)
                                                           indent-str)
                  lp_xml                               (-> xml_s xml/parse cr/strip-xml-namespaces)
                  suunnittelija                        (xml/select1 lp_xml [:Suunnittelija])
                  suunnittelija-using-ulkomainen-hetu  (second (xml/select lp_xml [:Suunnittelija]))
                  suunnittelija-using-suomalainen-hetu (get (vec (xml/select lp_xml [:Suunnittelija])) 2)]

              (is (nil? (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) krysp-version)))

              (testing "henkilotunnus"
                (is (= "210281-9988" (xml/get-text suunnittelija [:henkilo :henkilotunnus]))))
              (testing "suunnittelijaRoolikoodi"
                (is (= "rakennusfysikaalinen suunnittelija" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]))))
              (testing "VRKrooliKoodi"
                (is (= "erityissuunnittelija" (xml/get-text suunnittelija [:VRKrooliKoodi]))))
              (testing "postitetaanKytkin"
                (is (nil? (xml/get-text suunnittelija [:postitetaanKytkin]))))
              (testing "patevyysvaatimusluokka"
                (is (= "B" (xml/get-text suunnittelija [:patevyysvaatimusluokka]))))
              (testing "koulutus"
                (is (= "arkkitehti" (xml/get-text suunnittelija [:koulutus]))))
              (testing "valmistumisvuosi"
                (is (= "2010" (xml/get-text suunnittelija [:valmistumisvuosi]))))
              (testing "vaadittuPatevyysluokka"
                (is (= "C" (xml/get-text suunnittelija [:vaadittuPatevyysluokka]))))
              (testing "kokemusvuodet"
                (is (= "5" (xml/get-text suunnittelija [:kokemusvuodet]))))
              (testing "FISEpatevyyskortti"
                (is (= "http://www.ym.fi" (xml/get-text suunnittelija [:FISEpatevyyskortti]))))
              (testing "FISEkelpoisuus"
                (is (= "tavanomainen pääsuunnittelu (uudisrakentaminen)" (xml/get-text suunnittelija [:FISEkelpoisuus]))))
              (testing "yritys - nimi"
                (is (= "Solita Oy" (xml/get-text suunnittelija [:yritys :nimi]))))

              (testing "hetu/ulkomainen hetu works correctly"
                (testing "suunnittelija with ulkomainen hetu"
                  (is (= "rakennusfysikaalinen suunnittelija"
                         (xml/get-text suunnittelija-using-ulkomainen-hetu [:suunnittelijaRoolikoodi])))
                  (is (nil? (xml/get-text suunnittelija-using-ulkomainen-hetu [:henkilo :henkilotunnus])))
                  (is (= "123456"
                         (xml/get-text suunnittelija-using-ulkomainen-hetu [:henkilo :ulkomainenHenkilotunnus]))))
                (testing "suunnittelija using finnish hetu"
                  (is (= "rakennusfysikaalinen suunnittelija"
                         (xml/get-text suunnittelija-using-suomalainen-hetu [:suunnittelijaRoolikoodi])))
                  (is (= "210281-9988"
                         (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :henkilotunnus])))
                  (is (nil? (xml/get-text suunnittelija-using-suomalainen-hetu [:henkilo :ulkomainenHenkilotunnus])))))))]
    (testing "2.2.3" (suunnittelija>222 "2.2.3"))
    (testing "2.2.4" (suunnittelija>222 "2.2.4"))))

(deftest muu-suunnittelija-test
  (testing "2.1.2"
    (let [xml_s (-> suunnittelijan-nimaminen-muu-canonical
                    (rakennuslupa-element-to-xml "2.1.2")
                    indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces
                            (xml/select1 [:Suunnittelija]))]

      (is (nil? (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.1.2")))

      (testing "suunnittelijaRoolikoodi"
        (is (= "ei tiedossa" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]))))
      (testing "muuSuunnittelijaRooli"
        (is (nil? (xml/get-text suunnittelija [:muuSuunnittelijaRooli]))))
      (testing "VRKrooliKoodi"
        (is (= "erityissuunnittelija" (xml/get-text suunnittelija [:VRKrooliKoodi]))))))

  (testing "2.2.0"
    (let [xml_s (-> suunnittelijan-nimaminen-muu-canonical
                    (rakennuslupa-element-to-xml "2.2.0")
                    indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces
                            (xml/select1 [:Suunnittelija]))]

      (is (nil? (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.2.0")))

      (testing "suunnittelijaRoolikoodi"
        (is (= "ei tiedossa" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]))))
      (testing "muuSuunnittelijaRooli"
        (is (nil? (xml/get-text suunnittelija [:muuSuunnittelijaRooli]))))
      (testing "VRKrooliKoodi"
        (is (= "erityissuunnittelija" (xml/get-text suunnittelija [:VRKrooliKoodi]))))))

  (testing "2.2.2"
    (let [xml_s (-> suunnittelijan-nimaminen-muu-canonical
                    (rakennuslupa-element-to-xml "2.2.2")
                    indent-str)
          suunnittelija (-> xml_s xml/parse cr/strip-xml-namespaces (xml/select1 [:Suunnittelija]))]

      (is (nil? (validator/validate xml_s (:permitType application-tyonjohtajan-nimeaminen) "2.2.2")))

      (testing "suunnittelijaRoolikoodi"
        (is (= "muu" (xml/get-text suunnittelija [:suunnittelijaRoolikoodi]))))
      (testing "muuSuunnittelijaRooli"
        (is (= "ei listassa -rooli" (xml/get-text suunnittelija [:muuSuunnittelijaRooli]))))
      (testing "VRKrooliKoodi"
        (is (= "erityissuunnittelija" (xml/get-text suunnittelija [:VRKrooliKoodi])))))))

(def rakval-jatkolupa-canonical (rakval-application-to-canonical jatkolupa-application "fi"))

(deftest rakval-jatkolupa-test
  (testing "2.2.2"
    (let [xml_s (-> rakval-jatkolupa-canonical (rakennuslupa-element-to-xml "2.2.2") indent-str)
          app-info (-> xml_s xml/parse cr/strip-xml-namespaces)]

      (is (nil? (validator/validate xml_s (:permitType jatkolupa-application) "2.2.2")))
      (testing "asiankuvaus"
        (is (= "Pari vuotta jatko-aikaa, että saadaan rakennettua loppuun."
               (xml/get-text app-info [:rakennusvalvontaasianKuvaus])))))))

(defn get-hakijat [app krysp-version]
  (let [osapuolet (-> (rakval-application-to-canonical app "fi")
                      (rakennuslupa-element-to-xml krysp-version)
                      (indent-str)
                      (xml/parse)
                      (cr/strip-xml-namespaces)
                      (xml/select [:osapuolitieto :Osapuoli]))]
    (filter #(= "hakija" (-> % xml/select cr/all-of :VRKrooliKoodi)) osapuolet)))

(deftest hakija-hetu-test
  (testing "2.2.2"
    (let [hakijat                               (get-hakijat application-aurinkopaneeli-with-many-applicants "2.2.2")
          hakija                                (xml/select (first hakijat))
          hakija-using-ulkomainen-henkilotunnus (xml/select (second hakijat))
          hakija-using-finnish-hetu             (-> hakijat vec (get 2) xml/select)]
      (is (= "210281-9988" (xml/get-text hakija [:henkilo :henkilotunnus])))
      (is (nil? (xml/get-text hakija [:henkilo :ulkomainenHenkilotunnus])))
      (is (nil? (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :henkilotunnus])))
      (is (nil? (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :ulkomainenHenkilotunnus])))
      (is (= "210281-9988" (xml/get-text hakija-using-finnish-hetu [:henkilo :henkilotunnus])))
      (is (nil? (xml/get-text hakija-using-finnish-hetu [:henkilo :ulkomainenHenkilotunnus])))))
  (testing "2.2.3"
    (let [hakijat                               (get-hakijat application-aurinkopaneeli-with-many-applicants "2.2.3")
          hakija                                (xml/select (first hakijat))
          hakija-using-ulkomainen-henkilotunnus (xml/select (second hakijat))
          hakija-using-finnish-hetu             (-> hakijat vec (get 2) xml/select)]
      (is (= "210281-9988" (xml/get-text hakija [:henkilo :henkilotunnus])))
      (is (nil? (xml/get-text hakija [:henkilo :ulkomainenHenkilotunnus])))
      (is (nil? (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :henkilotunnus])))
      (is (= "123456" (xml/get-text hakija-using-ulkomainen-henkilotunnus [:henkilo :ulkomainenHenkilotunnus])))
      (is (= "210281-9988" (xml/get-text hakija-using-finnish-hetu [:henkilo :henkilotunnus])))
      (is (nil? (xml/get-text hakija-using-finnish-hetu [:henkilo :ulkomainenHenkilotunnus]))))))

(deftest foreman-termination-date-test
  (testing "2.2.2"
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
      (is (= (date/xml-date "2020-03-04") (xml/get-text tyonjohtaja [:paattymisPvm]))))))

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

(deftest new-enum-values-223-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (let [path-to-rakennuspaikkatieto                 [:Rakennusvalvonta :rakennusvalvontaAsiatieto
                                                       :RakennusvalvontaAsia :rakennuspaikkatieto]
          inner-path-to-hallintaperuste               [:Rakennuspaikka :rakennuspaikanKiinteistotieto
                                                       0 :RakennuspaikanKiinteisto :hallintaperuste]
          path-to-toimenpidetieto                     [:Rakennusvalvonta :rakennusvalvontaAsiatieto
                                                       :RakennusvalvontaAsia :toimenpidetieto]
          inner-path-to-paloluokka                    [:Toimenpide :rakennustieto :Rakennus
                                                       :rakennuksenTiedot :paloluokka]
          path-to-osapuolitieto                       [:Rakennusvalvonta :rakennusvalvontaAsiatieto
                                                       :RakennusvalvontaAsia :osapuolettieto
                                                       :Osapuolet :osapuolitieto]
          inner-path-to-kuntaRooliKoodi-in-osapuoli   [:Osapuoli :kuntaRooliKoodi]
          path-between-Toimenpide-omistajatieto       [:Toimenpide :rakennustieto :Rakennus :omistajatieto]
          inner-path-to-kuntaRooliKoodi-in-omistaja   [:Omistaja :kuntaRooliKoodi]
          canonical-application-with-new-enum-values  (-> (rakval-application-to-canonical
                                                            application-rakennuslupa "fi")
                                                          (update-in path-to-rakennuspaikkatieto
                                                                     change-value-in-canonical
                                                                     inner-path-to-hallintaperuste "muu oikeus")
                                                          (update-in path-to-toimenpidetieto
                                                                     change-value-in-canonical
                                                                     inner-path-to-paloluokka "P0")
                                                          (update-in path-to-osapuolitieto
                                                                     change-value-in-canonical
                                                                     inner-path-to-kuntaRooliKoodi-in-osapuoli
                                                                     "Rakennuksen laajarunkoisen osan arvioija")
                                                          (update-in path-to-toimenpidetieto
                                                                     helper-function-for-updating-omistaja
                                                                     path-between-Toimenpide-omistajatieto
                                                                     inner-path-to-kuntaRooliKoodi-in-omistaja
                                                                     "Rakennuksen laajarunkoisen osan arvioija"))
          canonical-application-with-old-enum-values  (rakval-application-to-canonical application-rakennuslupa "fi")
          hallintaperuste                             [:RakennuspaikanKiinteisto :hallintaperuste]
          paloluokka                                  [:paloluokka]
          osapuoli-kuntaroolikoodi                    [:Osapuoli :kuntaRooliKoodi]
          omistaja-kuntaroolikoodi                    [:Omistaja :kuntaRooliKoodi]]

      (testing "hallintaperuste"
        (testing "new enum value 'muu oikeus' does not change in 2.2.3"
          (is (= "muu oikeus"
                 (get-element-xml-value canonical-application-with-new-enum-values
                                        "2.2.3" hallintaperuste))))
        (testing "new enum value 'muu oikeus' changes to 'ei tiedossa' pre 2.2.3"
          (is (= "ei tiedossa"
                 (get-element-xml-value canonical-application-with-new-enum-values
                                        "2.2.2" hallintaperuste))))
        (testing "old enum value 'oma' does not change in 2.2.3"
          (is (= "oma"
                 (get-element-xml-value canonical-application-with-old-enum-values
                                        "2.2.3" hallintaperuste))))
        (testing "old enum value 'oma' does not change pre 2.2.3"
          (is (= "oma"
                 (get-element-xml-value canonical-application-with-old-enum-values
                                        "2.2.0" hallintaperuste)))))

      (testing "paloluokka"
        (testing "new enum value 'P0' does not change in 2.2.3"
          (is (= "P0" (get-element-xml-value canonical-application-with-new-enum-values "2.2.3" paloluokka))))
        (testing "krysp < 2.2.3 does not contain paloluokka when canonical contains paloluokka with P0 value"
          (is (nil? (get-element-xml-value canonical-application-with-new-enum-values "2.2.0" paloluokka))))
        (testing "old enum value 'P1' does not change in 2.2.3"
          (is (= "P1" (get-element-xml-value canonical-application-with-old-enum-values "2.2.3" paloluokka))))
        (testing "old enum value 'P1' does not change pre 2.2.3"
          (is (= "P1" (get-element-xml-value canonical-application-with-old-enum-values "2.2.2" paloluokka)))))

      (testing "osapuoli-kuntaroolikoodi"
        (testing "new enum value 'Rakennuksen laajarunkoisen osan arvioija' doesn't change in 2.2.3"
          (is (= "Rakennuksen laajarunkoisen osan arvioija"
                 (get-element-xml-value canonical-application-with-new-enum-values
                                        "2.2.3" osapuoli-kuntaroolikoodi))))
        (testing "new enum value 'Rakennuksen laajarunkoisen osan arvioija' changes to 'ei tiedossa' pre 2.2.3"
          (is (= "ei tiedossa"
                 (get-element-xml-value canonical-application-with-new-enum-values
                                        "2.2.2" osapuoli-kuntaroolikoodi))))
        (testing "old enum value 'Rakennusvalvonta-asian laskun maksaja' doesn't change in 2.2.3"
          (is (= "Rakennusvalvonta-asian laskun maksaja"
                 (get-element-xml-value canonical-application-with-old-enum-values
                                        "2.2.3" osapuoli-kuntaroolikoodi))))
        (testing "old enum value 'Rakennusvalvonta-asian laskun maksaja' doesn't change pre 2.2.3"
          (is (= "Rakennusvalvonta-asian laskun maksaja"
                 (get-element-xml-value canonical-application-with-old-enum-values
                                        "2.2.0" osapuoli-kuntaroolikoodi)))))

      (testing "omistaja-kuntaroolikoodi"
        (testing "new enum value 'Rakennuksen laajarunkoisen osan arvioija' doesn't change in 2.2.3"
          (is (= "Rakennuksen laajarunkoisen osan arvioija"
                 (get-element-xml-value canonical-application-with-new-enum-values
                                        "2.2.3" omistaja-kuntaroolikoodi))))
        (testing "new enum value 'Rakennuksen laajarunkoisen osan arvioija' changes to 'Rakennuksen omistaja' pre 2.2.3"
          (is (= "Rakennuksen omistaja"
                 (get-element-xml-value canonical-application-with-new-enum-values
                                        "2.2.2" omistaja-kuntaroolikoodi))))
        (testing "old enum value 'Rakennuksen omistaja' doesn't change in 2.2.3"
          (is (= "Rakennuksen omistaja"
                 (get-element-xml-value canonical-application-with-old-enum-values
                                        "2.2.3" omistaja-kuntaroolikoodi))))
        (testing "old enum value 'Rakennuksen omistaja' doesn't change pre 2.2.3"
          (is (= "Rakennuksen omistaja"
                 (get-element-xml-value canonical-application-with-old-enum-values
                                        "2.2.2" omistaja-kuntaroolikoodi))))))))

(deftest patevyystodistus-fields-226-test
  (with-redefs [org/pate-scope?                  (constantly false)
                org/get-application-organization (constantly {})]
    (testing "Regular message"
      (let [application      (drop-etvra application-rakennuslupa)
            canonical        (rakval-application-to-canonical application "fi")
            xml_226          (rakennuslupa-element-to-xml canonical "2.2.6")
            xml_226_s        (indent-str xml_226)
            lp-xml_226       (cr/strip-xml-namespaces (xml/parse xml_226_s))
            suunnittelijat   (xml/select lp-xml_226 [:osapuolettieto :Suunnittelija])
            paasuunnittelija (xml/select suunnittelijat
                                         (enlive/has [:suunnittelijaRoolikoodi
                                                      (enlive/text-pred (partial = "pääsuunnittelija"))]))
            suunnittelija3   (xml/select suunnittelijat
                                         (enlive/has [:suunnittelijaRoolikoodi
                                                      (enlive/text-pred (partial = "muu"))]))]
        (testing "Suunnittelijat"
          (is (= 4 (count suunnittelijat)))

          (let [todistus (xml/select paasuunnittelija [:virallinenTodistustieto :VirallinenTodistus])]
            (testing "patevyydentoteaja enum is mapped to full name"
              (is (= "Suomen Arkkitehtiliitto - Finlands Arkitektförbund ry"
                     (xml/get-text todistus :virallisenTodistuksenantaja))))
            (is (= "2045-01-01+02:00" (xml/get-text todistus :virallisenTodistuksenvoimassoloPvm)))
            (is (= "http://example.com/1" (xml/get-text todistus :linkkiPatevyysrekisteriin))))

          (let [todistus (xml/select suunnittelija3 [:virallinenTodistustieto :VirallinenTodistus])]
            (testing "patevyydentoteaja, siirtymäsäännös, fields not included in the KuntaGML"
              (is (= "ei listassa -rooli" (xml/get-text suunnittelija3 :muuSuunnittelijaRooli)))
              (is (= '() todistus)))))))

    (testing "TJ-V3 message"
      (let [{:keys [documents]
             :as   application}    (-> application-tyonjohtajan-nimeaminen-v3
                                       (update :documents conj (assoc tyonjohtaja-v3 :id (str (random-uuid))))
                                       (main-app-to-foreman-v3-app tyonjohtaja-v3))
            canonical              (rakval-application-to-canonical application "fi")
            xml_226                (rakennuslupa-element-to-xml canonical "2.2.6")
            xml_226_s              (indent-str xml_226)
            lp-xml_226             (cr/strip-xml-namespaces (xml/parse xml_226_s))
            tj-virallinen-todistus (xml/select1 lp-xml_226 [:osapuolettieto :Tyonjohtaja
                                                            :virallinenTodistustieto :VirallinenTodistus])]

        (testing "Documents"
          (is (= [(:id tyonjohtaja-v3)]
                 (->> documents (filter :tj-document) (mapv :id)))
              "Only one foreman")
          (is (empty? (filter doc/designer-document? documents))
              "No designers"))

        (testing "only TJ party"
          (is (= 1 (count (xml/select lp-xml_226 [:osapuolettieto])))))

        (testing "Tyonjohtaja-v3 has patevyystodistus fields"
          (testing "patevyydentoteaja enum is mapped to full name"
            (is (= "Rakennus-, LVI- ja kiinteistöalan henkilöpätevyydet FISE Oy"
                   (xml/get-text tj-virallinen-todistus :virallisenTodistuksenantaja))))
          (is (= "2049-01-01+02:00"
                 (xml/get-text tj-virallinen-todistus :virallisenTodistuksenvoimassoloPvm)))
          (is (= "http://example.com/todistus"
                 (xml/get-text tj-virallinen-todistus :linkkiPatevyysrekisteriin))))))))

(deftest aiemmalla-luvalla-hakeminen-test
  (with-redefs [org/pate-scope?                 (constantly false)
                org/get-application-organization (constantly {})]
    (letfn [(make-xml
              ([application version]
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
              (doseq [a [:uusi :purkaminen :purkamisilmoitus :kaupunkikuvaToimenpide :laajennus
                         :uudelleenrakentaminen :muuMuutosTyo]]
                (testing (str "Only one: " (name a))
                  (if (= a one)
                    (is (some? (xml/select1 xml [:Toimenpide a])))
                    (is (nil? (xml/select1 xml [:Toimenpide a])))))))]

      (let [xml (make-xml paperilupa-application)]
        (testing "Asian tiedot"
          (is (= "Paper description"
                 (xml/get-text xml [:Asiantiedot :rakennusvalvontaasianKuvaus])))
          (is (= "One more thing"
                 (xml/get-text xml [:Asiantiedot :vahainenPoikkeaminen]))))

        (testing "Muu muutostyö"
          (only-one xml :muuMuutosTyo)
          (is (= "rakennuksen pääasiallinen käyttötarkoitusmuutos"
                 (xml/get-text xml [:Toimenpide :muuMuutosTyo :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :muuMuutosTyo :kuvaus])))
          (is (= "true" (xml/get-text xml [:Toimenpide :muuMuutosTyo :perusparannusKytkin])))
          (is (= "true" (xml/get-text xml [:Toimenpide :muuMuutosTyo :rakennustietojaEimuutetaKytkin])))))

      (let [xml (make-xml paperilupa-application "2.1.6")]
        (testing "Muu muutostyö: older KuntaGML version"
          (only-one xml :muuMuutosTyo)
          (is (= "rakennuksen pääasiallinen käyttötarkoitusmuutos"
                 (xml/get-text xml [:Toimenpide :muuMuutosTyo :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :muuMuutosTyo :kuvaus])))
          (is (= "true" (xml/get-text xml [:Toimenpide :muuMuutosTyo :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :muuMuutosTyo :rakennustietojaEimuutetaKytkin])))))

      (let [xml (make-xml (assoc-in paperilupa-application
                                    [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                    (entry "uusi")))]
        (testing "Uusi"
          (only-one xml :uusi)
          (is (nil? (xml/select1 xml [:Toimenpide :uusi :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :uusi :kuvaus])))
          (is (nil? (xml/select1 xml [:Toimenpide :uusi :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :uusi :rakennustietojaEimuutetaKytkin])))))

      (let [xml (make-xml (assoc-in paperilupa-application
                                    [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                    (entry "purkaminen")))]
        (testing "Purkaminen"
          (only-one xml :purkaminen)
          (is (nil? (xml/select1 xml [:Toimenpide :purkaminen :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :purkaminen :kuvaus])))
          (is (nil? (xml/select1 xml [:Toimenpide :purkaminen :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :purkaminen :rakennustietojaEimuutetaKytkin])))))

      (let [xml (make-xml (assoc-in demolition-notice
                                    [:documents 1 :data :kuntagml-toimenpide]
                                    (entry "purkamisilmoitus"))
                          "2.2.6")]
        (testing "Purkamisilmoitus"
          (only-one xml :purkaminen)
          (is (= {:tag     :avainArvoTieto
                  :attrs   nil
                  :content [{:tag     :AvainArvoPari
                             :attrs   nil
                             :content [{:tag :avain :attrs nil :content ["asia"]}
                                       {:tag :arvo  :attrs nil :content ["purkamisilmoitus"]}]}]}
                 (xml/select1 xml [:avainArvoTieto])))
          (is (nil? (xml/select1 xml [:Toimenpide :purkaminen :muutostyonLaji])))
          (is (= "Purkamisilmoitus (Rakentamislaki 55§)" (xml/get-text xml [:Toimenpide :purkaminen :kuvaus])))
          (is (nil? (xml/select1 xml [:Toimenpide :purkaminen :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :purkaminen :rakennustietojaEimuutetaKytkin])))))

      (let [xml (make-xml (assoc-in paperilupa-application
                                    [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                    (entry "kaupunkikuvaToimenpide")))]
        (testing "Kaupunkikuvatoimenpide"
          (only-one xml :kaupunkikuvaToimenpide)
          (is (nil? (xml/select1 xml [:Toimenpide :kaupunkikuvaToimenpide :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :kaupunkikuvaToimenpide :kuvaus])))
          (is (nil? (xml/select1 xml [:Toimenpide :kaupunkikuvaToimenpide :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :kaupunkikuvaToimenpide :rakennustietojaEimuutetaKytkin])))))

      (let [xml (make-xml (assoc-in paperilupa-application
                                    [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                    (entry "laajennus")))]
        (testing "Laajennus"
          (only-one xml :laajennus)
          (is (nil? (xml/select1 xml [:Toimenpide :laajennus :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :laajennus :kuvaus])))
          (is (= "true" (xml/get-text xml [:Toimenpide :laajennus :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :laajennus :rakennustietojaEimuutetaKytkin])))
          (is (= "ei tiedossa" (xml/get-text xml [:Toimenpide :laajennus :laajennuksentiedot :huoneistoala
                                                  :kayttotarkoitusKoodi])))))

      (let [xml (make-xml (update-in paperilupa-application
                                     [:documents 1 :data :kuntagml-toimenpide]
                                     #(assoc %
                                             :toimenpide (entry "uudelleenrakentaminen")
                                             :muutostyolaji (entry "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"))))]
        (testing "Uudelleenrakentaminen"
          (only-one xml :uudelleenrakentaminen)
          (is (= "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"
                 (xml/get-text xml [:Toimenpide :uudelleenrakentaminen :muutostyonLaji])))
          (is (= "Paper description" (xml/get-text xml [:Toimenpide :uudelleenrakentaminen :kuvaus])))
          (is (= "true" (xml/get-text xml [:Toimenpide :uudelleenrakentaminen :perusparannusKytkin])))
          (is (nil? (xml/select1 xml [:Toimenpide :uudelleenrakentaminen :rakennustietojaEimuutetaKytkin])))))

      (testing "check-operations!"
        (testing "All good"
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
            (is (nil? (check-operations! application)))))
        (testing "Bad toimenpide for paperilupa"
          (is (thrown-with-msg? Exception #"kuntagml-toimenpide.error.type"
                                (check-operations! (assoc-in paperilupa-application
                                                             [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                                             (entry "bad"))))))
        (testing "Bad toimenpide for regular application is ignored"
          (is (nil? (check-operations! (assoc-in application-aurinkopaneeli
                                                 [:documents 1 :data :kuntagml-toimenpide :toimenpide]
                                                 (entry "bad"))))))
        (testing "Missing toimenpide for paperilupa"
          (is (thrown-with-msg? Exception #"kuntagml-toimenpide.error.type"
                                (check-operations! (assoc-in paperilupa-application
                                                             [:documents 1 :data :kuntagml-toimenpide]
                                                             nil)))))
        (testing "Missing toimenpide for regular application is ignored"
          (is (nil? (check-operations! (assoc-in application-rakennuslupa
                                                 [:documents 1 :data :kuntagml-toimenpide]
                                                 nil)))))))))
