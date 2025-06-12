(ns lupapalvelu.backing-system.krysp.verdict-mapping-test
  (:require [clojure.data.xml :as data-xml]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.krysp.verdict-mapping :as verdict-mapping]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping :as ya-mapping]
            [lupapalvelu.document.poikkeamis-canonical-test :as pct]
            [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :as ya-can-test]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-canonical-test :as vct]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.rakennuslupa-canonical-util :as rakennuslupa-util :refer [application-rakennuslupa
                                                                                   application-rakennuslupa-with-tasks
                                                                                   application-tyonjohtajan-nimeaminen
                                                                                   application-tyonjohtajan-nimeaminen-v2
                                                                                   drop-etvra]]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.backing-system.krysp.verdict-mapping
                   verdict-attachment-pred)

(def verdict (->  vct/verdict
                  (assoc-in [:published :published] (date/timestamp "13.7.2018"))
                  (assoc-in [:published :attachment-id] "531")
                  (assoc-in [:data :selected-attachments] ["good" "bad"])
                  (assoc-in [:data :operation] "Päätökselle kirjattu kuvaus")
                  (assoc-in [:data :deviations] "Päätökselle kirjatut poikkeamat")))

(def created-ts  1531465278000)
(def modified-ts 1531465279000)

(def verdict-attachment
  {:id            "531"
   :modified      1531465278654
   :type          {:type-id "paatos" :type-group "paatoksenteko"}
   :latestVersion {:fileId "15" :filename "Päätös: paatos.txt" :created created-ts}
   :source        {:id   "1a156dd40e40adc8ee064463"
                   :type "verdicts"}})

(def app (assoc application-rakennuslupa-with-tasks
                :attachments [{:id            "11"
                               :type          {:type-id "paatosote" :type-group "paatoksenteko"}
                               :latestVersion {:fileId "12" :filename "Testi: teksti & liite.txt"}
                               :target        {:type "verdict" :id (:id verdict)}}
                              {:id            "21"
                               :type          {:type-id "muu" :type-group "muut"}
                               :latestVersion {:fileId "22" :filename "muu_liite.txt"}}
                              {:id            "31"
                               :type          {:type-id "paatosote" :type-group "paatoksenteko"}
                               :latestVersion {:fileId "32" :filename "eri_paatoksen_liite.txt"}
                               :target        {:type "verdict" :id (str (:id verdict) "_eri")}}
                              {:id            "good"
                               :type          {:type-id "lausunto" :type-group "ennakkoluvat_ja_lausunnot"}
                               :latestVersion {:fileId "99" :filename "valittu.txt"}
                               :target        {:type "statement" :id "s1"}}
                              verdict-attachment]))

(defn verdict-krysp-mapper-test
  [kryspversion]

  (let [app (cond-> app
              (#{"2.2.2"
                 "2.2.3"
                 "2.2.4"} kryspversion) drop-etvra)]
    (facts {:midje/description (str "verdict-krysp-mapper: " kryspversion)}
      (against-background
        (meta-fields/enrich-with-link-permit-data irrelevant)
        => (assoc app :linkPermitData [{:id            "LP-753-2013-90001"
                                        :type          "lupapistetunnus"
                                        :operation     "kerrostalo-rivitalo"
                                        :permitSubtype ""}])
        (org/pate-scope? irrelevant) => false
        (org/get-application-organization anything) => {})
      (let [result      (permit/verdict-krysp-mapper app {} verdict "fi" kryspversion "BEGIN_OF_LINK/")
            attachments (:attachments result)
            xml-s       (data-xml/indent-str (:xml result))
            lp-xml      (cr/strip-xml-namespaces (xml/parse xml-s))]

        (fact "xml exist" (:xml result) => truthy)

        (fact (validator/validate xml-s :R kryspversion) => nil)

        (fact "xml contains app id"
          (xml/get-text lp-xml [:luvanTunnisteTiedot :LupaTunnus :MuuTunnus :tunnus])
          => (:id application-rakennuslupa))

        (fact "menettelyTOS"
          (xml/get-text lp-xml [:menettelyTOS]) => "tos menettely"
          (:tosFunctionName app) => "tos menettely")

        (facts "lupamaaraykset"
          (fact "autopaikkojarakennettava"
            (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaRakennettava]) => "6")

          (fact "autopaikkojarakennettu"
            (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaRakennettu]) => "5")

          (fact "autopaikkojakiinteistolla"
            (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaKiinteistolla]) => "3")

          (fact "autopaikkojaulkopuolella"
            (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaUlkopuolella]) => "4")

          (fact "autopaikkojavahintaan"
            (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaVahintaan]) => "1")

          (fact "autopaikkojaenintaan"
            (xml/get-text lp-xml [:paatostieto :lupamaaraykset :autopaikkojaEnintaan]) => "2")

          (facts "katselmus"
            (fact "katselmuksen laji - times two"
              (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaaditutKatselmukset :katselmuksenLaji))
              => [["muu katselmus"]
                  ["rakennuksen paikan merkitseminen"]])

            (fact "tarkastuksentaikatselmuksennimi - times two"
              (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaaditutKatselmukset :tarkastuksenTaiKatselmuksenNimi))
              => [["Katselmus"]
                  ["Katselmus2"]])

            (fact "sovellus- times two"
              (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaaditutKatselmukset :muuTunnustieto :MuuTunnus :sovellus))
              => [["Lupapiste"]
                  ["Lupapiste"]])

            (fact "tunnus - times two"
              (map :content (xml/select lp-xml :paatostieto :lupamaaraykset :vaaditutKatselmukset :muuTunnustieto :MuuTunnus :tunnus))
              => [["5d19f6247ad0792efa41a555"]
                  ["5d19f6247ad0792efa41a554"]]))

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
              => [["erityisalojen työnjohtaja"]
                  ["IV-työnjohtaja"]
                  ["vastaava työnjohtaja"]
                  ["KVV-työnjohtaja"]]))

          (when (> (parse-long (ss/replace kryspversion #"\." "")) 222)
            (fact "Kokoontumistilan henkilomaara"
              (xml/get-text lp-xml [:paatostieto :lupamaaraykset :kokoontumistilanHenkilomaara])
              => "9")))

        (facts "paivamaarat"
          (fact "aloitettavapvm"
            (xml/get-text lp-xml [:paatostieto :paivamaarat :aloitettavaPvm])
            => (date/xml-date "2022-11-23"))

          (fact "lainvoimainenpvm"
            (xml/get-text lp-xml [:paatostieto :paivamaarat :lainvoimainenPvm])
            => (date/xml-date "2017-11-27"))

          (fact "voimassahetkipvm"
            (xml/get-text lp-xml [:paatostieto :paivamaarat :voimassaHetkiPvm])
            => (date/xml-date "2023-11-23"))

          (fact "antopvm"
            (xml/get-text lp-xml [:paatostieto :paivamaarat :antoPvm])
            => (date/xml-date "2017-11-20"))

          (fact "viimeinenvalituspvm"
            (xml/get-text lp-xml [:paatostieto :paivamaarat :viimeinenValitusPvm])
            => (date/xml-date "2017-12-27"))

          (fact "julkipano"
            (xml/get-text lp-xml [:paatostieto :paivamaarat :julkipanoPvm])
            => (date/xml-date "2017-11-24")))

        (facts "poytakirja"
          (fact "paatos"
            (xml/get-text lp-xml [:paatostieto :poytakirja :paatos]) => "päätös - teksti")

          (fact "paatoskoodi"
            (xml/get-text lp-xml [:paatostieto :poytakirja :paatoskoodi]) => "myönnetty")

          (fact "paatoksentekija"
            (xml/get-text lp-xml [:paatostieto :poytakirja :paatoksentekija]) => "Pate Paattaja")

          (fact "paatospvm"
            (xml/get-text lp-xml [:paatostieto :poytakirja :paatospvm])
            => (date/xml-date "2017-11-23"))

          (fact "pykala"
            (xml/get-text lp-xml [:paatostieto :poytakirja :pykala]) => "99"))

        (facts "liitetieto"
          (facts "verdict attachments are returned"
            attachments => [{:fileId "15" :filename "15_Paatos- paatos.txt"}
                            {:fileId "12", :filename "12_Testi- teksti - liite.txt"}
                            {:fileId "99" :filename "99_valittu.txt"}])

          (fact "two regular attachments are included in xml"
            (count (xml/select lp-xml :liitetieto :Liite)) => 2)

          (fact "kuvaus"
            (xml/get-text lp-xml [:liitetieto :kuvaus]) => "Päätösote")

          (fact "linkkiliitteeseen"
            (xml/get-text lp-xml [:liitetieto :linkkiliitteeseen]) => "BEGIN_OF_LINK/12_Testi- teksti - liite.txt"))

        (facts "viitelupatieto"
          (fact "tunnus"
            (xml/get-text lp-xml [:viitelupatieto :LupaTunnus :muuTunnustieto :tunnus]) => "LP-753-2013-90001")

          (fact "sovellus"
            (xml/get-text lp-xml [:viitelupatieto :LupaTunnus :muuTunnustieto :sovellus]) => "Lupapiste"))

        (facts "paatosliite"
          (fact "kuvaus"
            (xml/get-text lp-xml [:paatostieto :poytakirja :liite :kuvaus]) => "Päätös")

          (fact "linkkiliitteeseen"
            (xml/get-text lp-xml [:paatostieto :poytakirja :liite :linkkiliitteeseen]))

          (fact "versio"
            (xml/get-text lp-xml [:paatostieto :poytakirja :liite :versionumero]) => "0.1")

          (fact "muokkaushetki"
            (xml/get-text lp-xml [:paatostieto :poytakirja :liite :muokkausHetki])
            => (date/xml-datetime created-ts))

          (fact "tyyppi"
            (xml/get-text lp-xml [:paatostieto :poytakirja :liite :tyyppi]) => "paatos"))

        (facts "tiedostonimi-metatieto"
          (->> (xml/select lp-xml [:metatieto])
               (map :content)
               (keep (fn [node]
                       (when (= (xml/get-text node [:metatietoNimi]) "tiedostonimi")
                         (xml/get-text node [:metatietoArvo])))))
          => (just "Paatos- paatos.txt" "Testi- teksti - liite.txt" "valittu.txt"
                   :in-any-order))

        (facts "asianTiedot"
          (fact "rakennusvaltontaasianKuvaus"
            (xml/get-text lp-xml [:asianTiedot :Asiantiedot :rakennusvalvontaasianKuvaus])
            => (-> verdict :data :operation))

          (fact "vahainenPoikkeaminen"
            (xml/get-text lp-xml [:asianTiedot :Asiantiedot :vahainenPoikkeaminen])
            => (-> verdict :data :deviations)))
        ))))

(verdict-krysp-mapper-test "2.2.2")
(verdict-krysp-mapper-test "2.2.3")
(verdict-krysp-mapper-test "2.2.4")
(verdict-krysp-mapper-test "2.2.6")

(def verdict-with-responsibilities-start-date (assoc-in verdict [:data :responsibilities-start-date] 1558774800000)) ;2019-05-25

(defn- check-value-from-verdict-krysp-mapper
  "Parameter path is a vector containing keywords"
  [version application verdict path]
  (let [updated-app (update application
                            :attachments
                            (fn [axs]
                              (conj (map-indexed (fn [i {:keys [latestVersion] :as a}]
                                                   (cond-> a
                                                     latestVersion
                                                     (update :latestVersion
                                                             merge
                                                             {:fileId   (str "file-" i)
                                                              :filename (str "filename_" i ".pdf")})))
                                                 axs)
                                    verdict-attachment)))
        result      (permit/verdict-krysp-mapper updated-app {} verdict "fi" version "BEGIN_OF_LINK/")
        xml-s       (data-xml/indent-str (:xml result))
        lp-xml      (cr/strip-xml-namespaces (xml/parse xml-s))]
    (xml/get-text lp-xml path)))

(facts "permit/verdict-krys-mapper function for application that is tyonjohtajan nimeaminen"
  (against-background
    (org/get-organization "753-R") => {}
    (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc app :linkPermitData [{:id            "LP-753-2013-90001"
                                                                                          :type          "lupapistetunnus"
                                                                                          :operation     "kerrostalo-rivitalo"
                                                                                          :permitSubtype ""}])
    (org/pate-scope? irrelevant) => false)

  ;krysp-mapper-function returns "Uusi päätös" for kayttotapaus if application is tyonjohtajan nimeaminen and
  ;verdict has no value for :usage when passed to krysp-mapper function
  (facts "kayttotapaus"
    (fact "kayttotapaus in application that is tyonjohtajan nimeaminen-v2"
      (check-value-from-verdict-krysp-mapper "2.2.2"
                                             application-tyonjohtajan-nimeaminen-v2
                                             verdict
                                             [:RakennusvalvontaAsia :kayttotapaus])
      => "Uusi päätös")

    (fact "kayttotapaus in application that is tyonjohtajan nimeaminen"
      (check-value-from-verdict-krysp-mapper "2.2.2"
                                             application-tyonjohtajan-nimeaminen
                                             verdict
                                             [:RakennusvalvontaAsia :kayttotapaus])
      => "Uusi päätös")

    (fact "kayttotapaus in application that is not tyojohtaja paatos"
      (check-value-from-verdict-krysp-mapper "2.2.2"
                                             app
                                             verdict-with-responsibilities-start-date
                                             [:RakennusvalvontaAsia :kayttotapaus])
      => "Uusi päätös"))

  ;Vastuiden alkamisPvm is updated if verdict contains value for :responsibilities-start-date
  (facts "Vastuiden alkamisPvm"

    (fact "value of :alkamisPvm is updated when verdict for tyonjohtajan-nimeaminen-v2 has value for :responsibilities-start-date"
      (check-value-from-verdict-krysp-mapper "2.2.2"
                                             application-tyonjohtajan-nimeaminen-v2
                                             verdict-with-responsibilities-start-date
                                             [:tyonjohtajatieto :Tyonjohtaja :alkamisPvm])
      => (date/xml-date "2019-05-25"))

    (fact "value of :alkamisPvm is updated when verdict for tyonjohtajan-nimeaminen has value for :responsibilities-start-date"
      (check-value-from-verdict-krysp-mapper "2.2.2"
                                             application-tyonjohtajan-nimeaminen
                                             verdict-with-responsibilities-start-date
                                             [:tyonjohtajatieto :Tyonjohtaja :alkamisPvm])
      => (date/xml-date "2019-05-25"))

    (fact "Value of :alkamisPvm does not update if verdict does not contain value for :responsibilities-start-date"
      (check-value-from-verdict-krysp-mapper "2.2.2"
                                             app
                                             verdict
                                             [:tyonjohtajatieto :Tyonjohtaja :alkamisPvm])
      => (date/xml-date "2014-02-13"))))

(facts "paloluokka"
  (against-background
    (org/get-organization "753-R") => {}
    (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc app :linkPermitData
                                                                    [{:id            "523"
                                                                      :lupapisteId   "LP-753-2013-00003"
                                                                      :type          "kuntalupatunnus"
                                                                      :operation     "ya-katulupa-vesi-ja-viemarityot"}])
    (org/pate-scope? irrelevant) => false)
  (fact "Paloluokka from primary operation"
    (check-value-from-verdict-krysp-mapper "2.2.2"
                                           app
                                           verdict
                                           [:lisatiedot :Lisatiedot :rakentamisentiedot :paloluokka])
    => (just "P1"))
  (fact "Paloluokka not set"
    (check-value-from-verdict-krysp-mapper "2.2.2"
                                           application-tyonjohtajan-nimeaminen
                                           verdict
                                           [:lisatiedot :Lisatiedot :rakentamisentiedot :paloluokka])
    => nil)
  (fact "Paloluokka from secondary operation"
    (check-value-from-verdict-krysp-mapper "2.2.2"
                                           (-> application-tyonjohtajan-nimeaminen
                                               (assoc :secondaryOperations [(rakennuslupa-util/op-info rakennuslupa-util/rakennuksen-muuttaminen)])
                                               (update :documents #(conj % rakennuslupa-util/rakennuksen-muuttaminen)))
                                           verdict
                                           [:lisatiedot :Lisatiedot :rakentamisentiedot :paloluokka])
    => (just "P1"))
  (fact
    "Empty string is discarded"
    (check-value-from-verdict-krysp-mapper "2.2.2"
                                           (-> application-tyonjohtajan-nimeaminen
                                               (assoc :secondaryOperations [(rakennuslupa-util/op-info rakennuslupa-util/rakennuksen-muuttaminen)])
                                               (update :documents #(conj % (assoc-in rakennuslupa-util/rakennuksen-muuttaminen
                                                                                     [:data :luokitus :paloluokka :value]
                                                                                     ""))))
                                           verdict
                                           [:lisatiedot :Lisatiedot :rakentamisentiedot :paloluokka])
    => nil))


(defn- p-verdict-test [prefix application krysp-version]
  (facts {:midje/description prefix}
    (let  [p-verdict (-> vct/verdict
                         (assoc :category "p")
                         (assoc-in [:published :published] (date/timestamp "13.7.2018"))
                         (assoc-in [:published :attachment-id] "531")
                         (assoc-in [:data :operation] "Päätökselle kirjattu kuvaus")
                         (assoc-in [:data :deviations] "Päätökselle kirjatut poikkeamat"))
           p-app     (assoc application :attachments [{:id            "11"
                                                       :type          {:type-id "paatosote" :type-group "paatoksenteko"}
                                                       :latestVersion {:fileId "12" :filename "liite.txt"}
                                                       :target        {:type "verdict" :id (:id p-verdict)}}
                                                      {:id            "21"
                                                       :type          {:type-id "muu" :type-group "muut"}
                                                       :latestVersion {:fileId "22" :filename "muu_liite.txt"}}
                                                      {:id            "31"
                                                       :type          {:type-id "paatosote" :type-group "paatoksenteko"}
                                                       :latestVersion {:fileId "32" :filename "eri_paatoksen_liite.txt"}
                                                       :target        {:type "verdict" :id (str (:id p-verdict) "_eri")}}
                                                      {:id            "531"
                                                       :modified      1531465278654
                                                       :type          {:type-id "paatos" :type-group "paatoksenteko"}
                                                       :latestVersion {:fileId   "18"
                                                                       :filename "paatos.txt"
                                                                       :created  created-ts
                                                                       :modified modified-ts}
                                                       :source        {:id "1a156dd40e40adc8ee064463"}
                                                       :target        {:type "verdict" :id (:id verdict)}}])

           result      (permit/verdict-krysp-mapper p-app {} p-verdict "fi" krysp-version "BEGIN_OF_LINK/")
           attachments (:attachments result)
           xml-s       (data-xml/indent-str (:xml result))
           lp-xml      (cr/strip-xml-namespaces (xml/parse xml-s))]

      (fact "xml exist" (:xml result) => truthy)

      (fact (validator/validate xml-s :P krysp-version) => nil)

      (fact "xml contains app id"
        (xml/get-text lp-xml [:luvanTunnistetiedot :LupaTunnus :MuuTunnus :tunnus])
        => (:id application))

      (facts "paivamaarat"
        (fact "No aloitettavapvm in P verdicts (any more)"
          (xml/get-text lp-xml [prefix :paatostieto :paivamaarat :aloitettavaPvm]) => nil)

        (fact "lainvoimainenpvm"
          (xml/get-text lp-xml [prefix :paatostieto :paivamaarat :lainvoimainenPvm])
          => (date/xml-date "2017-11-27"))

        (fact "voimassahetkipvm"
          (xml/get-text lp-xml [prefix :paatostieto :paivamaarat :voimassaHetkiPvm])
          => (date/xml-date "2023-11-23"))

        (fact "antopvm"
          (xml/get-text lp-xml [prefix :paatostieto :paivamaarat :antoPvm])
          => (date/xml-date "2017-11-20"))

        (fact "viimeinenvalituspvm"
          (xml/get-text lp-xml [prefix :paatostieto :paivamaarat :viimeinenValitusPvm])
          => (date/xml-date "2017-12-27"))

        (fact "julkipano"
          (xml/get-text lp-xml [prefix :paatostieto :paivamaarat :julkipanoPvm])
          => (date/xml-date "2017-11-24")))

      (facts "poytakirja"
        (fact "paatos"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :paatos]) => "päätös - teksti")

        (fact "paatoskoodi"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :paatoskoodi]) => "myönnetty")

        (fact "paatoksentekija"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :paatoksentekija]) => "Pate Paattaja")

        (fact "paatospvm"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :paatospvm])
          => (date/xml-date "2017-11-23"))

        (fact "pykala"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :pykala]) => "99"))

      (facts "liitetieto"
        (facts "attachment with verdict target is returned"
          attachments => [{:fileId "18" :filename "18_paatos.txt"}
                          {:fileId "12" :filename "12_liite.txt"}])

        (fact "single attachment included in xml"
          (count (xml/select lp-xml :liitetieto :Liite)) => 1)

        (fact "kuvaus"
          (xml/get-text lp-xml [:liitetieto :kuvaus]) => "Päätösote")

        (fact "linkkiliitteeseen"
          (xml/get-text lp-xml [:liitetieto :linkkiliitteeseen]) => "BEGIN_OF_LINK/12_liite.txt"))

      (facts "paatosliite"
        (fact "kuvaus"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :liite :kuvaus]) => "Päätös")

        (fact "linkkiliitteeseen"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :liite :linkkiliitteeseen])
          => "BEGIN_OF_LINK/18_paatos.txt")

        (fact "versio"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :liite :versionumero]) => "0.1")

        (fact "muokkaushetki"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :liite :muokkausHetki])
          => (date/xml-datetime modified-ts))

        (fact "tyyppi"
          (xml/get-text lp-xml [prefix :paatostieto :poytakirja :liite :tyyppi]) => "paatos"))

      (facts "asianTiedot"
        (fact "rakennusvaltontaasianKuvaus"
          (let [description-kw (if (= prefix :suunnittelutarveasiatieto)
                                 :suunnittelutarveasianKuvaus
                                 :poikkeamisasianKuvaus)]
            (xml/get-text lp-xml [prefix :asianTiedot :Asiantiedot description-kw])
            => (-> verdict :data :operation)))

        (fact "vahainenPoikkeaminen"
          (xml/get-text lp-xml [:asianTiedot :Asiantiedot :vahainenPoikkeaminen])
          => (-> verdict :data :deviations)))

      (fact {:midje/description (str "Designer role code is correct for " krysp-version)}
        (let [role   (xml/get-text lp-xml [:suunnittelijaRoolikoodi])
              target (if (= krysp-version "2.2.6")
                       "rakennussuunnittelija"
                       "ARK-rakennussuunnittelija")]
          role => target)))

    (against-background
      (org/get-organization "753-P") => {})))

(def ark-designer (assoc-in rakennuslupa-util/suunnittelija1
                            [:data :kuntaRoolikoodi] "ARK-rakennussuunnittelija"))

(def poikkari-hakemus (update pct/poikkari-hakemus :documents conj ark-designer))
(def suunnittelutarveratkaisu (update pct/suunnittelutarveratkaisu :documents conj ark-designer))

(facts "P verdict tests"
  (doseq [n        [3 4 6]
          :let [version (str "2.2." n)]]
    (fact "Poikkeamis"
      (p-verdict-test :poikkeamisasiatieto poikkari-hakemus version) => true)
    (fact "Suunnittelutarveratkaisu"
      (p-verdict-test :suunnittelutarveasiatieto suunnittelutarveratkaisu version)
      => true)))

(let [att1        {:id "att1" :target {:id "v1"}}
      att2        {:id "att2" :source {:id "v1"}}
      att3        {:id "att3"}
      att4        {:id "att4" :target {:id "v2"}}
      att5        {:id "att5" :target {:id "v2"}}
      attachments [att1 att2 att3 att4 att5]
      application {:attachments attachments}
      check-fn    (fn [verdict]
                    (filter (verdict-attachment-pred application verdict)
                            attachments))]
  (facts verdict-attachment-pred
    (check-fn {:id "foo"}) => empty?
    (check-fn {:id "v1"}) => [att1]
    (check-fn {:id "v1" :data {:selected-attachments []}}) => [att1]
    (check-fn {:id "v1" :data {:selected-attachments ["att3" "att5"]}})
    => [att1 att3 att5]
    (check-fn {:id "v3" :data {:selected-attachments ["att2" "att4" "foo"]}})
    => [att2 att4]
    (check-fn {:id "v2"}) => [att4 att5]))


(defn- ya-verdict-test [application krysp-version]
  (facts "YA verdict"
    (against-background
      (org/get-organization "753-YA") => {}
      (meta-fields/enrich-with-link-permit-data irrelevant) => (assoc application :linkPermitData
                                                                                  [{:id            "523"
                                                                                    :lupapisteId   "LP-753-2013-00003"
                                                                                    :type          "kuntalupatunnus"
                                                                                    :operation     "ya-katulupa-vesi-ja-viemarityot"}])
      (org/pate-scope? irrelevant) => true)

    (let [application (update application :attachments conj {:id "531"
                                                             :modified 1531465278654
                                                             :type {:type-id "paatos" :type-group "paatoksenteko"}
                                                             :latestVersion {:fileId "12" :filename "paatos.txt"}
                                                             :source {:id "5d919ca81bca460f42d495ca"}
                                                             :target {:type "verdict" :id (:id verdict)}})
          lupa-name-key (ya-mapping/resolve-lupa-name-key application)
          result (permit/verdict-krysp-mapper
                   application
                   {}
                   ya-can-test/verdict
                   "fi" krysp-version "BEGIN_OF_LINK/")
          xml-s (data-xml/indent-str (:xml result))
          lp-xml (cr/strip-xml-namespaces (xml/parse xml-s))
          katselmustiedot (->> (xml/select lp-xml :YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :katselmustieto) (map cr/all-of))
          paatostiedot (->> (xml/select lp-xml :YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :paatostieto) (map cr/all-of))
          lupakohtaiset-lisatiedot (->> (xml/select lp-xml :YleisetAlueet :yleinenAlueAsiatieto lupa-name-key :lupakohtainenLisatietotieto :LupakohtainenLisatieto)
                                        (map cr/all-of))]

      (fact "xml exist" (:xml result) => truthy)

      (fact (validator/validate xml-s :YA krysp-version) => nil)

      (fact "xml contains app id"
            (xml/get-text lp-xml [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto :MuuTunnus :tunnus]) => (:id application))

      (facts "paatostiedot"
             (fact "count" (count paatostiedot) => 1)
             (fact "content"
                   (first paatostiedot) => {:Paatos
                                            {:paatoslinkki "BEGIN_OF_LINK/12_paatos.txt",
                                             :paatosdokumentinPvm (date/xml-date "2019-09-29")}})
             (fact "lupaehdotJaMaaraykset" (-> paatostiedot first :Paatos :lupaehdotJaMaaraykset) => nil)
             (fact "takuuaikaPaivat"       (-> paatostiedot first :Paatos :takuuaikaPaivat) => nil)
             (fact "liitetieto"            (-> paatostiedot first :Paatos :liitetieto) => nil))

      (facts "katselmustiedot"
             (fact "count" (count katselmustiedot) => 3)
             (fact "content"
                   katselmustiedot => (contains [{:Katselmus
                                                  {:muuTunnustieto
                                                   {:MuuTunnus
                                                    {:tunnus "5d919dbb1bca460f42d495cb", :sovellus "Lupapiste"}},
                                                   :katselmuksenLaji "Muu valvontakäynti",
                                                   :tarkastuksenTaiKatselmuksenNimi "Muu valvontakäynti suomeksi"}}
                                                 {:Katselmus
                                                  {:muuTunnustieto
                                                   {:MuuTunnus
                                                    {:tunnus "5d919dbb1bca460f42d495cc", :sovellus "Lupapiste"}},
                                                   :katselmuksenLaji "Loppukatselmus",
                                                   :tarkastuksenTaiKatselmuksenNimi "Loppukatselmus suomeksi"}}
                                                 {:Katselmus
                                                  {:muuTunnustieto
                                                   {:MuuTunnus
                                                    {:tunnus "5d919dbb1bca460f42d495cd", :sovellus "Lupapiste"}},
                                                   :katselmuksenLaji "Aloituskatselmus",
                                                   :tarkastuksenTaiKatselmuksenNimi "Aloituskatselmus suomeksi"}}]
                                                :in-any-order)))
      ;; LPK-4859: Creating/sending of agreements (sopimukset) to dmCity is commented out for now. Take into use if decided otherwise.
      #_(facts "lupakohtaiset-lisatiedot"
               lupakohtaiset-lisatiedot => (contains {:selitysteksti "LUVAN_TYYPPI"
                                                      :arvo (if (= "sijoitussopimus" (:permitSubtype application))
                                                              "sopimus"
                                                              "lupa")})))))

(ya-verdict-test ya-can-test/kaivulupa-application-with-review-and-verdict "2.2.4")
  ;; LPK-4859: Creating/sending of agreements (sopimukset) to dmCity is commented out for now. Take into use if decided otherwise.
  #_(ya-verdict-test
      (assoc ya-can-test/kaivulupa-application-with-review-and-verdict :permitSubtype "sijoitussopimus")
      "2.2.4")

(defn- paatos-xml [application verdict]
  (-> (permit/verdict-krysp-mapper application {} verdict "fi" "2.2.4" "BEGIN_OF_LINK/")
      :xml
      data-xml/indent-str
      (xml/parse-string "utf8")
      cr/strip-xml-namespaces))

(defn paatostieto [application verdict]
  (-> (paatos-xml application verdict)
      (xml/select1 :paatostieto :Paatos)))

(defn verdict-avain-arvo-pairs [application verdict]
  (reduce (fn [acc elem]
            (assoc acc
                   (xml/select1 elem :avain enlive/content)
                   (xml/select1 elem :arvo enlive/content)))
          {}
          (-> (paatos-xml application verdict)
              (xml/select :avainArvoTieto :AvainArvoPari))))

(against-background
  [(lupapalvelu.mongo/select :app-links anything) => nil
   (org/pate-scope? irrelevant) => false
   (org/get-application-organization anything) => {}]
  (facts "backing system verdict"
    (let [verdict       vct/backing-system-verdict
          application   application-rakennuslupa
          attachment-id (-> verdict :paatokset first :poytakirjat first :urlHash)]
     (facts "With attachment"
       attachment-id => truthy
       (let [xml (paatostieto (assoc application
                                     :verdicts [verdict]
                                     :attachments [{:id            attachment-id
                                                    :modified      1531465278654
                                                    :type          {:type-id "paatos" :type-group "paatoksenteko"}
                                                    :latestVersion {:fileId   "15"
                                                                    :filename "Päätös: paatos.txt"
                                                                    :created  created-ts}}])
                              verdict)]
         (facts "lupamääräykset"
           (doseq [[k v] {:autopaikkojaEnintaan     10  :autopaikkojaKiinteistolla 7
                          :autopaikkojaRakennettava 2   :autopaikkojaRakennettu    0
                          :autopaikkojaUlkopuolella 3   :autopaikkojaVahintaan     1
                          :kerrosala                100 :kokonaisala               110}]
             (fact {:midje/description (format "%s -> %s" (name k) v)}
               (xml/get-text xml k) => (str v)))
           (fact "Erityissuunnitelmat"
             (xml/select xml :Maarays :sisalto enlive/content)
             => (just "Radontekninen suunnitelma" "Ilmanvaihtosuunnitelma"
                      :in-any-order))
           (fact "Katselmukset"
             (map (fn [kxml]
                    {:name (xml/get-text kxml :tarkastuksenTaiKatselmuksenNimi)
                     :type (xml/get-text kxml :katselmuksenLaji)})
                  (xml/select xml :vaaditutKatselmukset :Katselmus))
             => (just {:name "Aloituskokous" :type "aloituskokous"}
                      {:name "Käyttöönottotarkastus" :type "muu tarkastus"}
                      :in-any-order))
           (fact "Työnjohtajat"
             (xml/select xml :vaadittuTyonjohtajatieto :tyonjohtajaRooliKoodi
                         enlive/content)
             => (just "vastaava työnjohtaja" "ei tiedossa" "työnjohtaja"
                      :in-any-order)))
         (facts "Päivämäärät"
           (doseq [[k v] {:aloitettavaPvm   "23.11.2022"
                          :antoPvm          "20.11.2017"
                          :julkipanoPvm     "24.11.2017"
                          :lainvoimainenPvm "27.11.2017"
                          :raukeamisPvm     "23.11.2024"
                          :voimassaHetkiPvm "23.11.2023"}]
             (fact {:midje/description (format "%s -> %s" (name k) v)}
               (xml/get-text xml k) => (date/xml-date v))))
         (fact "Pöytäkirja"
           (doseq [[k v] {:paatoksentekija "Mölli Keinonen"
                          :paatos          "Mitä muuta on tekeillä?"
                          :paatoskoodi     "ehdollinen"
                          :paatospvm       (date/xml-date "23.11.2017")
                          :pykala          "2"}]
             (fact {:midje/description (format "%s -> %s" (name k) v)}
               (xml/get-text xml k) => v)))
         (fact "Verdict attachment"
           (doseq [[k v] {:kuvaus            "Päätös"
                          :linkkiliitteeseen "BEGIN_OF_LINK/15_Paatos- paatos.txt"
                          :muokkausHetki     (date/xml-datetime created-ts)
                          :versionumero      "0.1"
                          :tyyppi            "paatos"}]
             (fact {:midje/description (format "%s -> %s" (name k) v)}
               (xml/get-text xml :poytakirja :liite k) => v)))))
     (facts "No attachment"
       (let [xml      (paatostieto (assoc application
                                          :verdicts [verdict]
                                          :attachments [{:id            "foobar"
                                                         :modified      1531465278654
                                                         :type          {:type-id "paatos" :type-group "paatoksenteko"}
                                                         :latestVersion {:fileId   "15"
                                                                         :filename "Päätös: paatos.txt"
                                                                         :created  created-ts}}])
                                   verdict)
             verdict2 (update-in verdict [:paatokset 0 :poytakirjat 0] dissoc :urlHash)
             xml2     (paatostieto (assoc application
                                          :verdicts [verdict2]
                                          :attachments [{:id            "foobar"
                                                         :modified      1531465278654
                                                         :type          {:type-id "paatos" :type-group "paatoksenteko"}
                                                         :latestVersion {:fileId   "15"
                                                                         :filename "Päätös: paatos.txt"
                                                                         :created  created-ts}}])
                                   verdict2)]
         (xml/get-text xml :paatoksentekija) => "Mölli Keinonen"
         (xml/get-text xml2 :paatoksentekija) => "Mölli Keinonen"

         (xml/select1 xml :poytakirja :liite) => nil
         (xml/select1 xml2 :poytakirja :liite) => nil))))

  (facts "AvainArvo parit"
    (verdict-avain-arvo-pairs application-rakennuslupa vct/verdict)
    => {"paatosId"                    (:id vct/verdict)
        "paatoksenKasittelija"        "Pate Paattaja"
        "paloluokkaArvo_0"            "8" ; Invalid value on new verdicts but allowed on old ones
        "paloluokkaYksilointitieto_0" "5a1561250e40adc8ee064449"
        "paatoksenRatkaisija"         "Pate Paattaja"}
    (verdict-avain-arvo-pairs application-rakennuslupa
                              (assoc-in vct/verdict [:data :giver] "Hello World"))
    => (contains {"paatoksenKasittelija" "Pate Paattaja"
                  "paatoksenRatkaisija"  "Hello World"})
    (verdict-avain-arvo-pairs application-rakennuslupa
                              (assoc-in vct/verdict [:data :giver] "86"))
    => (contains {"paatoksenKasittelija" "Pate Paattaja"
                  "paatoksenRatkaisija"  "Maxwell Smart"})))
