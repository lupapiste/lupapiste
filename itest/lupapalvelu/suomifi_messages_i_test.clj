(ns lupapalvelu.suomifi-messages-i-test
  (:require [clj-uuid :as uuid]
            [cljstache.core :as clostache]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.suomifi-messages :as sm]
            [lupapalvelu.suomifi-messages-api :refer [->Recipient]]
            [lupapalvelu.user :as usr]
            [mount.core :as mount]
            [sade.core :refer :all]
            [sade.xml :as sxml])
  (:import [java.util Base64]))

(def ^:dynamic *test-user* nil)

(def test-recipient
  (->Recipient "Percy"
               "Panaani"
               "Tutti Frutin katu 6 T 9"
               "00102"
               "Piippola"
               "FI"
               "310599-955C"))

(def test-verdict
  {:id "5c49cd5a59412f207499aed7"
   :published {:tags "{:body [:div.section \"aha\" [:p \"We are the robots\"]]}"}})

(def test-app
  {:id "LP-000-2019-00000"
   :organization "753-R"
   :state "verdictGiven"
   :verdicts [test-verdict]
   :attachments [{:type {:type-id "ote_kauppa_ja_yhdistysrekisterista" :type-group "hakija"}
                  :latestVersion {:contentType "application/pdf"
                                  :filename "ote.pdf"
                                  :fileId "5cfe4116d2315419cea4dd0b"}}]})

(def test-suomifi-settings
  (merge
    {:authority-id "Viranomaistunnus-uus"
     :service-id "Palvelutunnus6"
     :verdict {:enabled true
               :message "T\u00e4m\u00e4 on p\u00e4\u00e4t\u00f6s rakennuslupa-asiaasi liittyen."
               :attachments []}}
    {:cert-common-name "MegaPalvelu2000"
     :url "http://localhost:8000"
     :sign false
     :dummy false}))

(def test-attachment-selection
  {:type-id "ote_kauppa_ja_yhdistysrekisterista"
   :title "Ote kauppa- ja yhdistysrekisterist\u00e4"
   :type-group "hakija"})

(def authority
  (sm/->viranomainen "753-R" "ville@viranomainen.fi" (uuid/v1) test-suomifi-settings))

(use-fixtures :each
  (fn [test]
    (mount/start #'mongo/connection)
    (let [test-db-name (str "test_" (now) "_suomifi")]
      (mongo/with-db test-db-name
        (fixture/apply-fixture "minimal"))
      (binding [*test-user*     (mongo/with-db test-db-name
                                  (usr/get-user-by-email "sonja.sibbo@sipoo.fi"))
                mongo/*db-name* test-db-name]
        (test)))))


(deftest message-building
  (let [message-map (sm/build-verdict-message test-app
                                              "5c49cd5a59412f207499aed7"
                                              *test-user*
                                              test-recipient
                                              test-suomifi-settings)
        test-message {:message-string (clostache/render-resource "suomifi-messages/VerdictMessage.xml" message-map)
                      :message message-map}]
    (testing "The components going into a verdict message look about right"
      (let [{:keys [sanomaVarmenneNimi sanomaVersio viranomaisTunnus]} authority]
        (is (record? authority))
        (is (string? sanomaVarmenneNimi))
        (is (= "1.0" sanomaVersio))
        (is (= "Viranomaistunnus-uus" viranomaisTunnus)) ;; From minimal
        (is (true? (not-any? (partial = "") (map str (vals authority)))))))
    (testing "A healthy-looking Suomi.fi-verdict message can be built"
      (let [message-map (-> (:message-string test-message) ;; Round-trip from map->string->map
                            (sxml/parse-string "utf-8")
                            sxml/xml->edn)
            data        (get-in message-map [:soapenv:Envelope :soapenv:Body :asi:LahetaViesti
                                             :asi:Kysely :asi:Kohteet :asi:Kohde])
            address     (get-in data [:asi:Asiakas :asi:Osoite])
            file        (get-in data [:asi:Tiedostot :asi:Tiedosto])]
        (is (= "Percy Panaani" (:asi:Nimi address)))
        (is (= "Piippola" (:asi:Postitoimipaikka address)))
        (is (re-find #"^%PDF" (String. (.decode (Base64/getDecoder) (:asi:TiedostoSisalto file)))))
        (is (= "LP-000-2019-00000|5c49cd5a59412f207499aed7" (:asi:ViranomaisTunniste data)))))
    (testing "The attachment specified in settings is included, if available"
      (with-redefs [att/get-attachment-file! (constantly
                                               {:content (fn []
                                                           (io/input-stream "dev-resources/test-pdf.pdf"))})]
        (let [message (sm/build-verdict-message test-app
                                                "5c49cd5a59412f207499aed7"
                                                *test-user*
                                                test-recipient
                                                (assoc-in test-suomifi-settings [:verdict :attachments 0]
                                                          test-attachment-selection))]
          (is (= 2 (count (:Tiedostot message))))
          (is (= "Ote kauppa- ja yhdistysrekisterist\u00e4" (get-in message [:Tiedostot 1 :tiedostonKuvaus]))))))))
