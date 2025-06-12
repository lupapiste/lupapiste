(ns lupapalvelu.backing-system.krysp.rasite-tai-yhteisjarjestely-test
  "Tests the path from app to canonical to KuntaGML for the rasite-tai-yhteisjarjestely operation;
  both rakennusrasite and yhteisjarjestely 'subtypes'."
  (:require [clojure.data.xml :as cxml]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.backing-system.krysp.verdict-mapping]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.data-schema :as doc-schema]
            [lupapalvelu.document.rakennuslupa-canonical :as rl-canonical]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.rakennuslupa-canonical-util :as rc-util]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]))

(def rasite-op
  (get op/operations :rasite-tai-yhteisjarjestely))

(def varasto-op
  (get op/operations :varasto-tms))

(def description-attachment
  "The KRYSP XML schema requires that the description be provided as an attachment. This is it"
  (-> att/Attachment
      (dissoc (sc/optional-key :metadata))
      (ssg/generate)
      (assoc :type {:type-group "muut"
                    :type-id    "kuvaus"})
      (assoc-in [:latestVersion :user :id] "batchrun-user")
      (assoc-in [:latestVersion :filename] "description-filename.pdf")
      (assoc-in [:latestVersion :fileId] "description-file-id")))

(defn mock-doc-data [app-type]
  {:tyyppi           {:value app-type}
   :kuvaus           {:value "Tämä on testikuvaus"}
   :rakennusrasite   {:oikeutetutTontit {:1 {:kiinteistotunnus {:value "75341600250050"}
                                             :tilanNimi        {:value "Kuusela"}
                                             :pintaAla         {:value "10"}
                                             :rekisterointiPvm {:value "4.11.2021"}}}
                      :rasitetutTontit  {:0 {:kiinteistotunnus {:value "75341600360066"}
                                             :tilanNimi        {:value "Mikkola"}
                                             :pintaAla         {:value "50"}
                                             :rekisterointiPvm {:value "9.11.2021"}}
                                         :1 {:kiinteistotunnus {:value "75341600180003"}
                                             :tilanNimi        {:value "Rakkola"}
                                             :pintaAla         {:value "100"}
                                             :rekisterointiPvm {:value "5.11.2021"}}}}
   :yhteisjarjestely {:kohdeTontit {:1 {:kiinteistotunnus {:value "75341600180003"}
                                        :tilanNimi        {:value "Sipoon Kartano"}
                                        :pintaAla         {:value "1"}
                                        :rekisterointiPvm {:value "15.11.2021"}
                                        ;; Owners not included in the XML yet but may be in the future
                                        :omistajat        {:1 {:etunimi     {:value "Jaakko Jaakkima Jorma"}
                                                               :henkilolaji {:value "luonnollinen"}
                                                               :sukunimi    {:value "Pakkanen"}}
                                                           :0 {:etunimi     {:value "Ahma Kyösti Jaakkima"}
                                                               :henkilolaji {:value "luonnollinen"}
                                                               :sukunimi    {:value "Voller"}}}}}}})

(defn make-application [app-type]
  (let [operation {:id   (ssg/generate ssc/ObjectIdStr)
                   :name "rasite-tai-yhteisjarjestely"}]
    (assoc rc-util/application-rakennuslupa
           :primaryOperation operation
           :secondaryOperations []
           :documents [rc-util/hakija-henkilo
                       rc-util/paasuunnittelija
                       rc-util/suunnittelija1
                       rc-util/maksaja-henkilo
                       rc-util/rakennuspaikka
                       (-> (ssg/generate (doc-schema/doc-data-schema (:schema rasite-op) true))
                           (assoc-in [:schema-info :op] operation)
                           (assoc :data (mock-doc-data app-type)))]
           :attachments [description-attachment])))

(defn canonical-to-xml-str
  "All versions up to 2.2.4 have not had the types we are interested in change so no need to check them"
  [mock-app]
  (let [canonical (-> mock-app
                      (rl-canonical/rakval-application-to-canonical "fi")
                      (rl-mapping/add-non-attachment-links mock-app "test:/"))
        mapping   (rl-mapping/get-rakennuslupa-mapping "2.2.4")]
    (-> (emit/element-to-xml canonical mapping)
        (cxml/indent-str))))

(defn parse-xml-rakennusvalvonta-asia [xml-str]
  (->> xml-str
       (xml/parse)
       (cr/strip-xml-namespaces)
       (cr/as-is)
       :Rakennusvalvonta
       :rakennusvalvontaAsiatieto
       :RakennusvalvontaAsia))

(defn get-kiinteistotieto [rakval-asia]
  (->> rakval-asia
       :rakennuspaikkatieto
       :Rakennuspaikka
       :rakennuspaikanKiinteistotieto
       (map :RakennuspaikanKiinteisto)))

(defn check-rasite-application [application]
  (against-background
    [(domain/get-application-no-access-checking "LP-753-2013-00001" [:attachments]) => application
     (mongo/by-id :organizations "753-R") => {}
     (mongo/by-id :organizations "753-R" anything) => {}]
    (facts "canonical to XML"
      (let [primary-xml-str               (canonical-to-xml-str application)
            primary-rakennusvalvonta-asia (parse-xml-rakennusvalvonta-asia primary-xml-str)
            primary-kiinteistotieto       (get-kiinteistotieto primary-rakennusvalvonta-asia)]

        (fact "primary operation XML is valid"
          (validator/validate primary-xml-str "R" "2.2.4") => nil)

        (facts "rasite as primary operation"

          (fact "has correct primary property information"
            primary-kiinteistotieto
            => (contains (contains {:kiinteistotieto
                                    {:Kiinteisto
                                     {:tilannimi        "Hiekkametsa"
                                      :kiinteistotunnus "21111111111111"}}})))

          (fact "has rakennusrasite"
            primary-kiinteistotieto
            => (contains (contains {:rakennusrasite
                                    (contains {:rasitettuKiinteisto
                                               [{:kiinteisto
                                                 {:tilannimi        "Mikkola"
                                                  :kiinteistotunnus "75341600360066"}}
                                                {:kiinteisto
                                                 {:tilannimi        "Rakkola"
                                                  :kiinteistotunnus "75341600180003"}}]
                                               :rasitteenSisalto
                                               {:kuvaus            "Tämä on testikuvaus"
                                                :linkkiliitteeseen "test:/description-file-id_description-filename.pdf"}})})))

          (fact "has oikeutetut tontit"
            primary-kiinteistotieto
            => (contains (contains {:kiinteistotieto
                                    {:Kiinteisto
                                     {:tilannimi        "Kuusela"
                                      :kiinteistotunnus "75341600250050"}}})))

          (fact "has no yhteisjarjestely"
            (some #(-> % keys set :yhteisjarjestely) primary-kiinteistotieto) => nil)

          (fact "has correct description"
            primary-rakennusvalvonta-asia
            => (contains {:asianTiedot {:Asiantiedot {:rakennusvalvontaasianKuvaus "Tämä on testikuvaus"}}}) )

          (fact "has correct käyttötapaus when rasite is the primary operation"
            (:kayttotapaus primary-rakennusvalvonta-asia) => "Uusi rakennusrasitehakemus")

          (fact "has correct avain-arvo-parit"
            (map (comp (juxt :avain :arvo) :AvainArvoPari)
                 (:avainArvoTieto primary-rakennusvalvonta-asia))
            =>  (just ["rasitetyyppi" "rakennusrasite"]
                      ["rasite_laji_0" "oikeutettu"]
                      ["rasite_tarkenne_0" "rakennusrasite"]
                      ["rasite_kiinteistotunnus_0" "75341600250050"]
                      ["rasite_laji_1" "rasitettu"]
                      ["rasite_tarkenne_1" "rakennusrasite"]
                      ["rasite_kiinteistotunnus_1" "75341600360066"]
                      ["rasite_laji_2" "rasitettu"]
                      ["rasite_tarkenne_2" "rakennusrasite"]
                      ["rasite_kiinteistotunnus_2" "75341600180003"]
                      :in-any-order)))))))

(defn check-yhteisjarjestely-application [application]
  (against-background
    [(domain/get-application-no-access-checking "LP-753-2013-00001" [:attachments]) => application
     (mongo/by-id :organizations "753-R") => {}
     (mongo/by-id :organizations "753-R" anything) => {}]
    (facts "canonical to XML"
      (let [primary-xml-str               (canonical-to-xml-str application)
            primary-rakennusvalvonta-asia (parse-xml-rakennusvalvonta-asia primary-xml-str)
            primary-kiinteistotieto       (get-kiinteistotieto primary-rakennusvalvonta-asia)]

        (fact "primary operation XML is valid"
          (validator/validate primary-xml-str "R" "2.2.4") => nil)

        (facts "yhteisjarjestely as primary operation"
          (fact "property information"
            primary-kiinteistotieto
            => (contains (contains {:kiinteistotieto
                                    {:Kiinteisto
                                     {:tilannimi        "Hiekkametsa"
                                      :kiinteistotunnus "21111111111111"}}}))
            primary-kiinteistotieto
            => (contains (contains
                           {:yhteisjarjestely
                            (contains {:muutkiinteistot         {:kiinteisto
                                                                 {:tilannimi        "Sipoon Kartano"
                                                                  :kiinteistotunnus "75341600180003"}}
                                       :yhteisjarjestelynKuvaus {:kuvaus            "Tämä on testikuvaus"
                                                                 :linkkiliitteeseen "test:/description-file-id_description-filename.pdf"}})})))

          (fact "has no rakennusrasite"
            (some #(-> % keys set :rakennusrasite) primary-kiinteistotieto) => nil)

          (fact "has no oikeutetut tontit"
            (util/find-first #(-> % :kiinteistotieto :Kiinteisto :tilannimi #{"Kuusela"})
                             primary-kiinteistotieto) => nil)

          (fact "has correct description"
            primary-rakennusvalvonta-asia
            => (contains {:asianTiedot {:Asiantiedot {:rakennusvalvontaasianKuvaus "Tämä on testikuvaus"}}}) )

          (fact "has correct käyttötapaus"
            (:kayttotapaus primary-rakennusvalvonta-asia) => "Uusi rakennusrasitehakemus")

          (fact "has correct avain-arvo-parit"
            (map (comp (juxt :avain :arvo) :AvainArvoPari)
                 (:avainArvoTieto primary-rakennusvalvonta-asia))
            =>  (just ["rasitetyyppi" "yhteisjarjestely"]
                      ["rasite_laji_0" "sopimus"]
                      ["rasite_tarkenne_0" "yhteisjarjestely"]
                      ["rasite_kiinteistotunnus_0" "75341600180003"]
                      :in-any-order)))))))

(facts "Rakennusrasite applications"
  (doseq [app-type ["rakennusrasite" "" nil]]
    (fact {:midje/description (str "Application type:" app-type)})
    (check-rasite-application (make-application app-type))))

(fact "Yhteisjärjestely application"
  (check-yhteisjarjestely-application (make-application "yhteisjarjestely")))

(fact "No avain-arvo pairs for a regular application"
  (let [app (-> (make-application "rakennusrasite")
                (update :documents butlast)
                tools/unwrapped)]
    (common/get-avain-arvo-parit app) => nil))

(facts "rakennusrasite verdict"
  (let [app               (make-application "rakennusrasite")
        org               {:id "753-R"}
        verdict           {:id              "637262a05d2b4812838a1125"
                           :kuntalupatunnus "13-0185-R"
                           :paatokset       [{:id             "637262a05d2b4812838a1127"
                                              :lupamaaraykset {:autopaikkojaEnintaan      10
                                                               :autopaikkojaKiinteistolla 7
                                                               :autopaikkojaRakennettava  2
                                                               :autopaikkojaRakennettu    0
                                                               :autopaikkojaUlkopuolella  3
                                                               :autopaikkojaVahintaan     1
                                                               :kerrosala                 "100"
                                                               :kokonaisala               "110"
                                                               :maaraykset                [{:maaraysaika   1377637200000
                                                                                            :sisalto       "Radontekninen suunnitelma"
                                                                                            :toteutusHetki 1377723600000}
                                                                                           {:maaraysaika   1377810000000
                                                                                            :sisalto       "Ilmanvaihtosuunnitelma"
                                                                                            :toteutusHetki 1377896400000}]
                                                               :vaaditutKatselmukset      [{:katselmuksenLaji                "aloituskokous"
                                                                                            :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
                                                                                           {:katselmuksenLaji                "muu tarkastus"
                                                                                            :tarkastuksenTaiKatselmuksenNimi "Käyttöönottotarkastus"}]
                                                               :vaaditutTyonjohtajat      "Vastaava työnjohtaja, Vastaava IV-työnjohtaja, Työnjohtaja"}
                                              :paivamaarat    {:aloitettava      (date/timestamp "23.11.2022")
                                                               :anto             (date/timestamp "20.11.2017")
                                                               :julkipano        (date/timestamp "24.11.2017")
                                                               :lainvoimainen    (date/timestamp "27.11.2017")
                                                               :raukeamis        (date/timestamp "23.11.2024")
                                                               :viimeinenValitus (date/timestamp "27.12.2017")
                                                               :voimassaHetki    (date/timestamp "23.11.2023")}
                                              :poytakirjat    [{:paatoksentekija "Mölli Keinonen"
                                                                :paatos          "Mitä muuta on tekeillä?"
                                                                :paatoskoodi     "ehdollinen"
                                                                :paatospvm       (date/timestamp "23.11.2017")
                                                                :pykala          "2"
                                                                :status          "6"
                                                                :urlHash         "b55ae9c30533428bd9965a84106fb163611c1a7d"}]}]
                           :timestamp       12345}
        lang              "fi"
        krysp-version     "2.2.4"
        beginning-of-link "sftp://localhost/"]
    (facts "rakennusrasite verdict generates a valid XML"
      (against-background
        (meta-fields/enrich-with-link-permit-data app) => app
        (domain/get-application-no-access-checking irrelevant irrelevant) => app
        (org/get-organization irrelevant) => org
        (org/pate-scope? irrelevant) => true)
      (let [{:keys [xml]} (permit/verdict-krysp-mapper app org verdict lang krysp-version beginning-of-link)
            xml-str       (cxml/indent-str xml)]
        (fact "verdict XML is valid"
          (validator/validate xml-str "R" krysp-version) => nil)))))
