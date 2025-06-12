(ns lupapalvelu.mml.property.client-test
  "Although the tests invoke the [[lupapalvelu.mml.property.api]] API,
  what we're really testing is the OGC client ([[lupapalvelu.mml.property.client]])."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.mml.property.api :as api]
            [lupapalvelu.mml.property.client :as client]
            [schema.core :as sc]))

(def ^:dynamic ^:private check-fn
  "Verify the outgoing request"
  (fn [_request]
    (throw (ex-info "check-fn not defined" {}))))

(def ^:dynamic ^:private response-fn
  "Produces a mock response"
  (constantly {:features []}))

(use-fixtures :once (fn [test-fn]
                      (sc/with-fn-validation (test-fn))))

(use-fixtures :once (fn [test-fn]
                      (binding [api/*instance* (client/->ApiClient
                                               (fn [request]
                                                 (check-fn request)
                                                 (response-fn)))]
                        (test-fn))))

(deftest property-info-by-id-test
  (let [property-id "12345678901234"]
    (binding [check-fn (fn [{:keys [path query]}]
                         (is (str/includes? path "RekisteriyksikonAvoimetOminaisuustiedot"))
                         (is (= property-id (-> query :kiinteistotunnus))))]
      (testing "empty response"
        (is (nil? (api/property-info-by-id! property-id))))
      (testing "multiple matches"
        (binding [response-fn (constantly
                                {:meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 2
                                 :type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T11:55:35.142053Z"
                                 :features       [{:properties {:kiinteistotunnus property-id
                                                                :kunta            [{:kuntatunnus   "001"
                                                                                    :nimiSuomeksi  "nimi"
                                                                                    :nimiRuotsiksi "namn"}
                                                                                   {:kuntatunnus   "002"
                                                                                    :nimiSuomeksi  "ignored"
                                                                                    :nimiRuotsiksi "ignored"}]}}
                                                  {:properties {:kiinteistotunnus "00005678901234"
                                                                :kunta            [{:kuntatunnus   "003"
                                                                                    :nimiSuomeksi  "ignored"
                                                                                    :nimiRuotsiksi "ignored"}
                                                                                   {:kuntatunnus   "004"
                                                                                    :nimiSuomeksi  "ignored"
                                                                                    :nimiRuotsiksi "ignored"}]}}]})]
          (is (= {:propertyId   property-id
                  :municipality "001"
                  :name         {:fi "nimi"
                                 :sv "namn"}}
                 (api/property-info-by-id! property-id))))))))

(deftest property-infos-by-point-test
  (let [x 500000
        y 7000000]
    (binding [check-fn (fn [{:keys [path query]}]
                         (is (str/includes? path "RekisteriyksikonAvoimetOminaisuustiedot"))
                         (is (= "S_INTERSECTS(geometry,POINT (500000.000 7000000.000))"
                                (-> query :filter))))]
      (testing "empty response"
        (is (= [] (api/property-infos-by-point! x y))))
      (testing "multiple matches"
        (binding [response-fn (constantly
                                {:meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 2
                                 :type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T11:56:16.920781Z"
                                 :features       [{:properties {:kiinteistotunnus "11111111111111"
                                                                :kunta            [{:kuntatunnus   "001"
                                                                                    :nimiSuomeksi  "nimi 1"
                                                                                    :nimiRuotsiksi "namn 1"}
                                                                                   {:kuntatunnus   "002"
                                                                                    :nimiSuomeksi  "ignored"
                                                                                    :nimiRuotsiksi "ignored"}]}}
                                                  {:properties {:kiinteistotunnus "22222222222222"
                                                                :kunta            [{:kuntatunnus   "003"
                                                                                    :nimiSuomeksi  "nimi 3"
                                                                                    :nimiRuotsiksi "namn 3"}
                                                                                   {:kuntatunnus   "004"
                                                                                    :nimiSuomeksi  "ignored"
                                                                                    :nimiRuotsiksi "ignored"}]}}]})]
          (is (= [{:propertyId   "11111111111111"
                   :municipality "001"
                   :name         {:fi "nimi 1"
                                  :sv "namn 1"}}
                  {:propertyId   "22222222222222"
                   :municipality "003"
                   :name         {:fi "nimi 3"
                                  :sv "namn 3"}}]
                 (api/property-infos-by-point! x y))))))))

(deftest property-details-by-id-test
  (let [property-id "12345678901234"]
    (binding [check-fn (fn [{:keys [path query]}]
                         (is (str/includes? path "RekisteriyksikonPerustiedot"))
                         (is (= property-id (-> query :kiinteistotunnus))))]
      (testing "empty response"
        (is (nil? (api/property-details-by-id! property-id))))

      (testing "single response"
        (binding [response-fn (constantly
                                {:meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 1
                                 :type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T11:56:16.920781Z"
                                 :features       [{:properties {:kiinteistotunnus     property-id
                                                                :maapintaala          123456
                                                                :nimi                 "NIMI"
                                                                :olotila              1
                                                                :rekisteriyksikkolaji 2
                                                                :rekisterointipvm     "2023-03-01"
                                                                :vesipintaala         3}}]})]
          (is (= {:kiinteistotunnus     property-id
                  :maapintaala          "12.3456"
                  :nimi                 "NIMI"
                  :olotila              "1"
                  :rekisteriyksikkolaji "2"
                  :rekisterointipvm     "20230301"
                  :vesipintaala         "0.0003"}
                 (api/property-details-by-id! property-id)))))
      (testing "partial data"
        (binding [response-fn (constantly
                                {:meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 1
                                 :type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T11:56:16.920781Z"
                                 :features       [{:properties {:kiinteistotunnus     property-id
                                                                :nimi                 ""
                                                                :olotila              1
                                                                :rekisteriyksikkolaji 2
                                                                :rekisterointipvm     "2023-03-01"}}]})]
          (is (= {:kiinteistotunnus     property-id
                  :olotila              "1"
                  :rekisteriyksikkolaji "2"
                  :rekisterointipvm     "20230301"}
                 (api/property-details-by-id! property-id)))))
      (testing "kiinteistotunnus is the only mandatory field"
        (binding [response-fn (constantly
                                {:meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 1
                                 :type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T11:56:16.920781Z"
                                 :features       [{:properties {:kiinteistotunnus property-id
                                                                :nimi             ""
                                                                :foo              "bar"}}]})]
          (is (= {:kiinteistotunnus property-id}
                 (api/property-details-by-id! property-id)))))
      (testing "Maapintaala fallbacks to kokonaispintaala"
        (binding [response-fn (constantly
                                {:meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 1
                                 :type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T11:56:16.920781Z"
                                 :features       [{:properties {:kiinteistotunnus property-id
                                                                :nimi             "Hello"
                                                                :kokonaispintaala 12345}}]})]
          (is (= {:kiinteistotunnus property-id
                  :nimi             "Hello"
                  :maapintaala      "1.2345"}
                 (api/property-details-by-id! property-id))))))))

(deftest property-infos-by-wkt-test
  (testing "point query"
    (let [wkt           "POINT (100 0)"
          *req-counter  (atom 0)
          *resp-counter (atom 0)]
      (binding [check-fn (fn [{:keys [path query]}]
                           (is (= "S_INTERSECTS(geometry,POINT (100 0))"
                                  (-> query :filter)))
                           (swap! *req-counter inc)
                           (case (deref *req-counter)
                             1 (is (str/includes? path "RekisteriyksikonPerustiedot"))
                             2 (is (str/includes? path "RekisteriyksikonLaajatSijaintitiedot"))))]
        (testing "empty response"
          (is (= [] (api/property-infos-by-wkt! wkt)))
          (reset! *req-counter 0)
          (reset! *resp-counter 0))
        (testing "single response"
          (binding [response-fn (fn []
                                  (swap! *resp-counter inc)
                                  (case (deref *resp-counter)
                                    1 {:meta           {:rekisteritilanneAjantasalla "true"
                                                        :rekisteritilannepvm         "2023-09-18"}
                                       :numberReturned 1
                                       :type           "FeatureCollection"
                                       :timeStamp      "2023-09-18T11:56:16.920781Z"
                                       :features       [{:id         123
                                                         :properties {:kiinteistotunnus     "11111111111111"
                                                                      :maapintaala          123456
                                                                      :nimi                 "NIMI"
                                                                      :olotila              1
                                                                      :rekisteriyksikkolaji 5
                                                                      :rekisterointipvm     "2023-03-01"
                                                                      :vesipintaala         3
                                                                      :kunta                [{:kuntatunnus               "042"
                                                                                              :loppupvm                  nil
                                                                                              :kuntatunnuksenEsitysmuoto "042"
                                                                                              :olotila                   1
                                                                                              :alkupvm                   nil
                                                                                              :nimiSuomeksi              "KuntaFin"
                                                                                              :nimiRuotsiksi             "KuntaSv"}]}}]}
                                    2 {:meta           {:rekisteritilanneAjantasalla "true"
                                                        :rekisteritilannepvm         "2023-09-18"}
                                       :numberReturned 1
                                       :type           "FeatureCollection"
                                       :timeStamp      "2023-09-18T11:56:16.920781Z"
                                       :features       [{:id         123
                                                         :properties {:kiinteistotunnus                           "11111111111111"
                                                                      :olotila                                    1
                                                                      :rekisteriyksikonLaajatSijaintitiedotPalsta [{:geometry                    {:coordinates [[[100.0 0.0]
                                                                                                                                                                 [101.0 0.0]
                                                                                                                                                                 [101.0 1.0]
                                                                                                                                                                 [100.0 1.0]
                                                                                                                                                                 [100.0 0.0]]]
                                                                                                                                                  :type        "Polygon"}
                                                                                                                    :kiinteistotunnuksenSijainti {:coordinates [400000.123 6700000.432]
                                                                                                                                                  :type        "Point"}
                                                                                                                    :kiinteistotunnus            "11111111111111"}]}}]}))]
            (is (= [{:kiinttunnus          "11111111111111"
                     :kunta                "042"
                     :nimi                 {:fi "KuntaFin"
                                            :sv "KuntaSv"}
                     :rekisteriyksikkolaji {:id     "5"
                                            :selite {:fi "Kruununkalastus"
                                                     :sv "Kronofiske"}}
                     :wkt                  "POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0))"
                     :x                    "400000.123"
                     :y                    "6700000.432"}]
                   (api/property-infos-by-wkt! wkt))))
          (reset! *req-counter 0)
          (reset! *resp-counter 0))
        (testing "response with projected lot"
          (binding [response-fn (fn []
                                  (swap! *resp-counter inc)
                                  (case (deref *resp-counter)
                                    1 {:meta           {:rekisteritilanneAjantasalla "true"
                                                        :rekisteritilannepvm         "2023-09-18"}
                                       :numberReturned 1
                                       :type           "FeatureCollection"
                                       :timeStamp      "2023-09-18T11:56:16.920781Z"
                                       :features       [{:id         123
                                                         :properties {:kiinteistotunnus     "11111111111111"
                                                                      :maapintaala          123456
                                                                      :nimi                 "NIMI"
                                                                      :olotila              1
                                                                      :rekisteriyksikkolaji 5
                                                                      :rekisterointipvm     "2023-03-01"
                                                                      :vesipintaala         3
                                                                      :kunta                [{:kuntatunnus               "042"
                                                                                              :loppupvm                  nil
                                                                                              :kuntatunnuksenEsitysmuoto "042"
                                                                                              :olotila                   1
                                                                                              :alkupvm                   nil
                                                                                              :nimiSuomeksi              "KuntaFin"
                                                                                              :nimiRuotsiksi             "KuntaSv"}]}}]}
                                    2 {:meta           {:rekisteritilanneAjantasalla "true"
                                                        :rekisteritilannepvm         "2023-09-18"}
                                       :numberReturned 1
                                       :type           "FeatureCollection"
                                       :timeStamp      "2023-09-18T11:56:16.920781Z"
                                       :features       [{:id         123
                                                         :properties {:kiinteistotunnus                                     "11111111111111"
                                                                      :olotila                                              1
                                                                      :rekisteriyksikonLaajatSijaintitiedotProjisoituPalsta [{:geometry                    {:coordinates [[[100.0 0.0]
                                                                                                                                                                           [101.0 0.0]
                                                                                                                                                                           [101.0 1.0]
                                                                                                                                                                           [100.0 1.0]
                                                                                                                                                                           [100.0 0.0]]]
                                                                                                                                                            :type        "Polygon"}
                                                                                                                              :kiinteistotunnuksenSijainti {:coordinates [400000.123 6700000.432]
                                                                                                                                                            :type        "Point"}
                                                                                                                              :kiinteistotunnus            "11111111111111"}]}}]}))]
            (is (= [{:kiinttunnus          "11111111111111"
                     :kunta                "042"
                     :nimi                 {:fi "KuntaFin"
                                            :sv "KuntaSv"}
                     :rekisteriyksikkolaji {:id     "5"
                                            :selite {:fi "Kruununkalastus"
                                                     :sv "Kronofiske"}}
                     :wkt                  "POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0))"
                     :x                    "400000.123"
                     :y                    "6700000.432"}]
                   (api/property-infos-by-wkt! wkt))))
          (reset! *req-counter 0)
          (reset! *resp-counter 0)))))
  (testing "point query with buffer"
    (let [wkt    "POINT (100 0)"
          radius "50"]
      (binding [check-fn (fn [{:keys [query]}]
                           (is (= "S_WITHIN(geometry,BUFFER(POINT (100 0), 50))"
                                  (-> query :filter))))]
        (testing "empty response"
          (is (= [] (api/property-infos-by-wkt! wkt radius)))))))
  (testing "non-point queries ignores radius"
    (let [wkt    "LINESTRING (100 0, 101 1)"
          radius "50"]
      (binding [check-fn (fn [{:keys [query]}]
                           (is (= "S_INTERSECTS(geometry,LINESTRING (100 0, 101 1))"
                                  (-> query :filter))))]
        (testing "empty response"
          (is (= [] (api/property-infos-by-wkt! wkt radius))))))))

(deftest lots-by-id-test
  (let [property-id "12345678901234"]
    (binding [check-fn (fn [{:keys [path query]}]
                         (is (str/includes? path "PalstanSijaintitiedot"))
                         (is (= property-id (-> query :kiinteistotunnus))))]
      (testing "empty response"
        (is (= [] (api/lots-by-property-id! property-id))))
      (testing "multiple matches"
        (binding [response-fn (constantly
                                {:type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T10:45:55.742941Z"
                                 :meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 1
                                 :features       [{:id         1
                                                   :type       "Feature"
                                                   :geometry   {:coordinates [[[100.0 0.0]
                                                                               [101.0 0.0]
                                                                               [101.0 1.0]
                                                                               [100.0 1.0]
                                                                               [100.0 0.0]]]
                                                                :type        "Polygon"}
                                                   :properties {:kiinteistotunnus               property-id
                                                                :kiinteistotunnuksenEsitysmuoto "564-421-6-84"
                                                                :kiinteistotunnuksenSijainti    {:coordinates [500000
                                                                                                               7000000]}}}]})]
          (is (= [{:kiinttunnus "12345678901234"
                   :lot-id      "1"
                   :x           "500000.000"
                   :y           "7000000.000"
                   :wkt         "POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0))"}]
                 (api/lots-by-property-id! property-id))))))))

(deftest lot-property-id-by-point-test
  (let [x 500000
        y 7000000]
    (binding [check-fn (fn [{:keys [path query]}]
                         (is (str/includes? path "PalstanSijaintitiedot"))
                         (is (= "S_INTERSECTS(geometry,POINT (500000.000 7000000.000))"
                                (-> query :filter))))]
      (testing "empty response"
        (is (nil? (api/lot-property-id-by-point! x y))))
      (testing "multiple matches"
        (binding [response-fn (constantly
                                {:type           "FeatureCollection"
                                 :timeStamp      "2023-09-18T10:45:55.742941Z"
                                 :meta           {:rekisteritilanneAjantasalla "true"
                                                  :rekisteritilannepvm         "2023-09-18"}
                                 :numberReturned 1
                                 :features       [{:id         1
                                                   :type       "Feature"
                                                   :geometry   {:coordinates [[[100.0 0.0]
                                                                               [101.0 0.0]
                                                                               [101.0 1.0]
                                                                               [100.0 1.0]
                                                                               [100.0 0.0]]]
                                                                :type        "Polygon"}
                                                   :properties {:kiinteistotunnus               "12345678901234"
                                                                :kiinteistotunnuksenEsitysmuoto "564-421-6-84"
                                                                :kiinteistotunnuksenSijainti    {:coordinates [500000
                                                                                                               7000000]}}}]})]
          (is (= "12345678901234"
                 (api/lot-property-id-by-point! x y))))))))
