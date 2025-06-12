(ns lupapalvelu.ryhti.openapi-parser-test
  (:require [clojure.test :refer [deftest is]]
            [ryhti-openapi-parser :as sut]))

(deftest parse-ref-test
  (is (= "BuildingSiteBuildingProduct"
         (sut/parse-ref "#/components/schemas/BuildingSiteBuildingProduct"))))

(deftest update-properties-test
  (is (= [:string {:description "foo"}]
         (sut/update-properties :string assoc :description "foo")))

  (is (= [:string {:description "foo"}]
         (sut/update-properties [:string] assoc :description "foo")))

  (is (= [:string {:description "foo"}]
         (sut/update-properties [:string {}] assoc :description "foo")))

  (is (= (list 'into [:enum {:description "foo"}] (list 'vals))
         (sut/update-properties (list 'into [:enum] (list 'vals)) assoc :description "foo"))))

(deftest get-codeset-uri-test
  (is (= "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine"
         (sut/get-codeset-uri "Julkisivun rakennusaine. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine\">http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine</a>")))
  (is (= "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine"
         (sut/get-codeset-uri "Julkisivun rakennusaine. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine\">http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine</a>")))
  (is (= "http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine"
         (sut/get-codeset-uri "Julkisivun rakennusaine. Käytetään koodistoa http://uri.suomi.fi/codelist/rytj/julkisivunrakennusaine")))
  (is (= "http://uri.suomi.fi/codelist/rytj/hulevedenkasittely"
         (sut/get-codeset-uri "Huleveden käsittelyn laji. Käytetään koodiston URI arvoa http://uri.suomi.fi/codelist/rytj/hulevedenkasittely")))
  (is (= "https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607"
         (sut/get-codeset-uri "Postinumero. Käytetään koodiston code arvoa <a href=\"https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607\">https://uri.suomi.fi/codelist/statbr/postitoimipaik_1_20160607</a>"))))

(deftest ->malli-test
  (is (= [:string {:description "hello"}]
         (sut/->malli {:type "string"
                       :description "hello"}))))

(deftest ->malli-schema-codeset-test
  (is (= [:vector
          {:description nil}
          '(into [:enum {:description "Asiakirjan kieli tai sisältämät kielet. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/ryhtikielet\">http://uri.suomi.fi/codelist/rytj/ryhtikielet</a>"}]
                 (sort (vals mappings/ryhtikielet)))]
         (sut/->malli-schema {:type "array"
                              :description "Asiakirjan kieli tai sisältämät kielet. Käytetään koodistoa <a href=\"http://uri.suomi.fi/codelist/rytj/ryhtikielet\">http://uri.suomi.fi/codelist/rytj/ryhtikielet</a>"
                              :items {:type "string"}}))))
