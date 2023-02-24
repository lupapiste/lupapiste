(ns lupapalvelu.backing-system.krysp.rasite-tai-yhteisjarjestely-test
  "Tests the path from app to canonical to KRYSP XML for the rasite-tai-yhteisjarjestely operation;
  both rakennusrasite and yhteisjarjestely 'subtypes' and as a primary or secondary operation."
  (:require [clojure.data.xml :as cxml]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.document.data-schema :as doc-schema]
            [lupapalvelu.document.rakennuslupa-canonical :as rl-canonical]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.rakennuslupa-canonical-util :as rc-util]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.shared-util :as util]
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

(def mock-doc-data
  {:tyyppi           {:value "rakennusrasite"}
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

(def primary-operation-app
  "Mock application where the rasite-tai-yhteisjarjestely is the primary operation
  The type is rakennusrasite (unlike secondary below) to check both types"
  (let [operation {:id   (ssg/generate ssc/ObjectIdStr)
                   :name "rasite-tai-yhteisjarjestely"}]
    (-> rc-util/application-rakennuslupa
        (assoc :primaryOperation operation)
        (update :documents conj (-> (ssg/generate (doc-schema/doc-data-schema (:schema rasite-op) true))
                                    (assoc-in [:schema-info :op] operation)
                                    (assoc :data mock-doc-data)))
        (assoc :attachments [description-attachment]))))

(def secondary-operation-app
  "Mock application where the rasite-tai-yhteisjarjestely is a secondary operation.
  Also the type is yhteisjarjestely so we can test the logic for types"
  (let [operation {:id   (ssg/generate ssc/ObjectIdStr)
                   :name "varasto-tms"}]
    (-> primary-operation-app
        (assoc :id "LP-753-2013-00002")
        (assoc :secondaryOperations [(:primaryOperation primary-operation-app)])
        (assoc :primaryOperation operation)
        (update :documents (fn [documents]
                             (mapv #(cond-> %
                                      (-> % :schema-info :op :name (= "rasite-tai-yhteisjarjestely"))
                                      (assoc-in [:data :tyyppi :value] "yhteisjarjestely"))
                                   documents))))))

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

(facts "canonical to XML"
  (background
    (domain/get-application-no-access-checking "LP-753-2013-00001" [:attachments]) => primary-operation-app
    (domain/get-application-no-access-checking "LP-753-2013-00002" [:attachments]) => secondary-operation-app
    (mongo/by-id :organizations "753-R") => {}
    (mongo/by-id :organizations "753-R" anything) => {})

  (let [primary-xml-str                 (canonical-to-xml-str primary-operation-app)
        primary-rakennusvalvonta-asia   (parse-xml-rakennusvalvonta-asia primary-xml-str)
        primary-kiinteistotieto         (get-kiinteistotieto primary-rakennusvalvonta-asia)

        secondary-xml-str               (canonical-to-xml-str secondary-operation-app)
        secondary-rakennusvalvonta-asia (parse-xml-rakennusvalvonta-asia secondary-xml-str)
        secondary-kiinteistotieto       (get-kiinteistotieto secondary-rakennusvalvonta-asia)]

    (fact "primary operation XML is valid"
      (validator/validate primary-xml-str "R" "2.2.4") => nil)

    (fact "secondary operation XML is valid"
      (validator/validate secondary-xml-str "R" "2.2.4") => nil)

    (facts "rasite as primary operation"

      (fact "has correct secondary operation information"
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
                                           {:kuvaus            "Rakennusrasitteen tai yhteisjärjestelyn kuvaus"
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
        => (contains {:asianTiedot {:Asiantiedot {:rakennusvalvontaasianKuvaus "Uuden rakennuksen rakentaminen tontille.\n\nPuiden kaataminen:Puun kaataminen\n\nRakennusrasite tai yhteisjärjestely:Tämä on testikuvaus"
                                                  :vahainenPoikkeaminen        "Ei poikkeamisia"}}}) )

      (fact "has correct käyttötapaus when rasite is the primary operation"
        (:kayttotapaus primary-rakennusvalvonta-asia) => "Uusi rakennusrasitehakemus")

      (fact "has correct avain-arvo-parit"
        primary-rakennusvalvonta-asia
        => (contains {:avainArvoTieto
                      (contains [{:AvainArvoPari {:avain "rasite_kiinteistotunnus_0" :arvo "75341600250050"}}
                                 {:AvainArvoPari {:avain "rasite_laji_0" :arvo "oikeutettu"}}
                                 {:AvainArvoPari {:avain "rasite_tarkenne_0" :arvo "rakennusrasite"}}
                                 {:AvainArvoPari {:avain "rasite_kiinteistotunnus_1" :arvo "75341600360066"}}
                                 {:AvainArvoPari {:avain "rasite_laji_1" :arvo "rasitettu"}}
                                 {:AvainArvoPari {:avain "rasite_tarkenne_1" :arvo "rakennusrasite"}}
                                 {:AvainArvoPari {:avain "rasite_kiinteistotunnus_2" :arvo "75341600180003"}}
                                 {:AvainArvoPari {:avain "rasite_laji_2" :arvo "rasitettu"}}
                                 {:AvainArvoPari {:avain "rasite_tarkenne_2" :arvo "rakennusrasite"}}])}))

    (facts "rasite as secondary operation"

      (fact "secondary operation XML has correct primary operation information"
        secondary-kiinteistotieto
        => (contains (contains {:kiinteistotieto
                                {:Kiinteisto
                                 {:tilannimi        "Hiekkametsa"
                                  :kiinteistotunnus "21111111111111"}}})))

      (fact "secondary operation XML has no rakennusrasite"
        (some #(-> % keys set :rakennusrasite) secondary-kiinteistotieto) => nil)

      (fact "primary operation XML has no oikeutetut tontit"
        (util/find-first #(-> % :kiinteistotieto :Kiinteisto :tilannimi #{"Kuusela"})
                         secondary-kiinteistotieto) => nil)

      (fact "secondary operation XML has yhteisjarjestely"
        secondary-kiinteistotieto
        => (contains (contains
                       {:yhteisjarjestely
                        (contains {:muutkiinteistot         {:kiinteisto
                                                             {:tilannimi        "Sipoon Kartano"
                                                              :kiinteistotunnus "75341600180003"}}
                                   :yhteisjarjestelynKuvaus {:kuvaus            "Rakennusrasitteen tai yhteisjärjestelyn kuvaus"
                                                             :linkkiliitteeseen "test:/description-file-id_description-filename.pdf"}})})))
      (fact "has correct description"
        secondary-rakennusvalvonta-asia
        => (contains {:asianTiedot {:Asiantiedot {:rakennusvalvontaasianKuvaus "Uuden rakennuksen rakentaminen tontille.\n\nPuiden kaataminen:Puun kaataminen\n\nRakennusrasite tai yhteisjärjestely:Tämä on testikuvaus"
                                                  :vahainenPoikkeaminen        "Ei poikkeamisia"}}}))

      (fact "has correct käyttötapaus when rasite-tai-yhteisjarjestely is not the primary operation"
        (:kayttotapaus secondary-rakennusvalvonta-asia) => "Uusi hakemus")

      (fact "has correct avain-arvo-parit"
        secondary-rakennusvalvonta-asia
        => (contains {:avainArvoTieto
                      (contains [{:AvainArvoPari {:avain "rasite_kiinteistotunnus_0" :arvo "75341600180003"}}
                                 {:AvainArvoPari {:avain "rasite_laji_0" :arvo "sopimus"}}
                                 {:AvainArvoPari {:avain "rasite_tarkenne_0" :arvo "yhteisjarjestely"}}])}))))))
