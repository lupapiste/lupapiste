(ns lupapalvelu.bim.bim-extracted-data-test
  (:require [clojure.edn :as edn]
            [clojure.test :as t]
            [lupapalvelu.bim.bim-extracted-data :as sut]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [matcher-combinators.test :refer [match?]]
            [schema.core :as sc]))

(def example-bimmer-response
  (-> (slurp "dev-resources/bim/example-bimmer-parsed-ifc-response.edn")
      edn/read-string))

(def test-schema
  (schemas/get-schema (schemas/get-latest-schema-version) "uusiRakennuskohde"))

(t/deftest save-ifc-data-snapshot!-test
  (let [fixed-now  12345678
        fixed-id   "000000000000000000000001"
        properties (:properties example-bimmer-response)
        snapshot   {:linked-metadata-file-version-id "version-abc"
                    :query                           {:IfcBuilding [{:name "FI_Kohde"}]}
                    :properties                      properties}]
    (with-redefs [mongo/create-id (constantly fixed-id)]
      (let [doc (sut/ifc-data-document! test-schema fixed-now snapshot)]
        (t/is (match? {:linked-metadata-file-version-id "version-abc"
                       :modified                        fixed-now
                       :ifc-query                       (:query snapshot)
                       :ifc-properties                  properties
                       :ifc-doc                         {:id          fixed-id
                                                         :created     fixed-now
                                                         :schema-info (:info test-schema)
                                                         :data        any?}}
                      doc))))))

(t/deftest ifc-data-document-schema-validation-test
  (let [fixed-id  "000000000000000000000001"
        snapshot  {:linked-metadata-file-version-id "version-abc"
                   :query                           {:IfcBuilding [{:name "FI_Kohde"}]}
                   :properties                      {}}]
    (with-redefs [mongo/create-id (constantly fixed-id)]
      (t/testing "rejects snapshot with blank file-version-id"
        (t/is (thrown? Exception
                       (sut/ifc-data-document! test-schema 12345678
                                               (assoc snapshot :linked-metadata-file-version-id "")))))
      (t/testing "rejects snapshot with missing file-version-id"
        (t/is (thrown? Exception
                       (sut/ifc-data-document! test-schema 12345678
                                               (dissoc snapshot :linked-metadata-file-version-id)))))
      (t/testing "accepts valid ETRS-TM35FIN location from IfcMapConversion"
        (let [doc (sut/ifc-data-document! test-schema 12345678
                                          (assoc snapshot :properties
                                                 {"IfcMapConversion" [{"Eastings"  500.0
                                                                       "Northings" 6700.0
                                                                       "Scale"     0.001}]}))]
          (t/is (= {:x 500000.0 :y 6700000.0} (-> doc :ifc-doc :location)))))
      (t/testing "omits :location when IfcMapConversion coordinates are out of ETRS-TM35FIN bounds"
        (let [doc (sut/ifc-data-document! test-schema 12345678
                                          (assoc snapshot :properties
                                                 {"IfcMapConversion" [{"Eastings"  1.0
                                                                       "Northings" 2.0
                                                                       "Scale"     0.001}]}))]
          (t/is (not (contains? (:ifc-doc doc) :location)))))
      (t/testing "IfcDoc schema rejects out-of-bounds location coordinates"
        (t/is (some? (sc/check sut/IfcDoc
                               {:id                  fixed-id
                                :created             12345678
                                :schema-info         {:name "uusiRakennuskohde" :version 1}
                                :data                {}
                                :rakennuspaikka-data {}
                                :location            {:x 1.0 :y 2.0}})))))))
