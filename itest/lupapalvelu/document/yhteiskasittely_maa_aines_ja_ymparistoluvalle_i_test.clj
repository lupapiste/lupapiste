(ns lupapalvelu.document.yhteiskasittely-maa-aines-ja-ymparistoluvalle-i-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]))

(def db-name "test_yhteiskasittely")

(use-fixtures :once (local-fixture-factory :db-name db-name))

(defn location-doc
  ([schema-name app-or-id]
   (util/find-first (util/fn-> :schema-info :name
                               (= schema-name))
                    (util/pcond->> app-or-id
                      string? (mongo/by-id :applications)
                      identity :documents)))
  ([app-or-id]
   (location-doc "yhteiskasittelyn-toiminta-alueen-sijainti" app-or-id)))

(defn property-id-and-name [doc]
  (->> doc :data :kiinteisto-tiedot :0 tools/unwrapped
       ((juxt :kiinteistotunnus :tilanNimi))))

(defn update-property-id
  ([path apikey app-id doc-id value]
   (command apikey :update-doc :id app-id
            :doc doc-id
            :collection "documents"
            :updates [[path value]]))
  ([apikey app-id doc-id value]
   (update-property-id "kiinteisto-tiedot.0.kiinteistotunnus"
                       apikey app-id doc-id value)))

(defn update-name [apikey app-id doc-id value]
  (command apikey :update-doc :id app-id
           :doc doc-id
           :collection "documents"
           :updates [["kiinteisto-tiedot.0.tilanNimi" value]]))

(deftest property-list-test
  (testing "Property list is stored correctly"
    (let [{app-id :id :as app} (create-application
                                 pena :operation
                                 "yhteiskasittely-maa-aines-ja-ymparistoluvalle")
          {doc-id :id :as doc} (location-doc app)]
      (testing "Initially empty"
        (is (= ["" ""] (property-id-and-name doc))))
      (testing "Good property-ids are always stored in db format"
        (is (true? (:ok (update-property-id pena app-id doc-id " 123-456-7-89  "))))
        (is (true? (:ok (update-name pena app-id doc-id " Farm "))))
        (is (= ["12345600070089" "Farm"]
               (-> app-id location-doc property-id-and-name)))
        (is (true? (:ok (update-property-id pena app-id doc-id " 99900876504321 "))))
        (is (= ["99900876504321" "Farm"]
               (-> app-id location-doc property-id-and-name))))
      (testing "Bad property-ids are stored as they are"
        (is (true? (:ok (update-property-id pena app-id doc-id " kaboom!  "))))
        (is (= ["kaboom!" "Farm"]
               (-> app-id location-doc property-id-and-name)))))))

(defn property-id [doc]
  (-> doc :data :ylijaamamaan-alkupera :kiinteistotunnus :value))

(deftest property-id-test
  (testing "Property id is stored properly"
    (let [{app-id :id :as app} (create-application
                                 teppo :operation
                                 "ylijaamamaiden-hyodyntaminen")
          get-doc              #(location-doc "ylijaamamaiden-hyodyntaminen"
                                              app-id)
          {doc-id :id :as doc} (get-doc)
          update-property-id   (partial update-property-id
                                        "ylijaamamaan-alkupera.kiinteistotunnus"
                                        teppo app-id doc-id)]
      (testing "Initially empty"
        (is (= "" (property-id doc))))
      (testing "Good property-id is always stored in db format"
        (is (true? (:ok (update-property-id " 123-456-7-89  "))))
        (is (= "12345600070089" (property-id (get-doc))))
        (is (true? (:ok (update-property-id " 99900876504321 "))))
        (is (= "99900876504321" (property-id (get-doc)))))
      (testing "Bad property-ids are stored as they are"
        (is (true? (:ok (update-property-id " kaboom!  "))))
        (is (= "kaboom!" (property-id (get-doc))))
        (is (true? (:ok (update-property-id "12345"))))
        (is (= "12345" (property-id (get-doc))))))))
