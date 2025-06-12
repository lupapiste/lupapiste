(ns lupapalvelu.building-attributes-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.building-attributes :as attr]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.core :as sc]
            [sade.util :as util]))

(def ts 1234)

(use-fixtures :once
  (local-fixture-factory :db-name "test_building-attributes-test")
  (fn [f]
    (with-redefs [sc/now (constantly ts)]
      (f))))

(defn has-keys? [ks m]
  (every? #(contains? (set ks) %) (keys m)))

(defn building-with-id [building-id buildings-result]
  (util/find-by-id building-id (:data buildings-result)))

(defn clear-buildings! []
  (mongo/drop-collection :building-info))

(use-fixtures :each (fn [f]
                      (clear-buildings!)
                      (f)))

(def not-found? (partial expected-failure? :error.not-found))
(def bad-attribute? (partial expected-failure? :error.building.invalid-attribute))

(deftest buildings-test
  (testing "fails with unauthorized when"
    (testing "user is not org admin"
      (is (unauthorized? (query sonja :buildings :organizationId "753-R"))))

    (testing "user belongs to a different organization"
      (is (unauthorized? (query sipoo :buildings :organizationId "991-R")))))

  (testing "fails with wrong scope when requested not in R organization"
    (expected-failure? :error.unsupported-permit-type
                       (query sipoo-ya :buildings :organizationId "753-YA")))

  (testing "returns zero building attributes when none inserted"
    (is (zero? (-> (local-query sipoo :buildings :organizationId "753-R")
                   :data
                   count))))

  (testing "returns attributes when some found"
    (let [{building-id-1 :building-id} (attr/upsert-building-attribute! "753-R" {:field "vtjprt" :value "1234567892"})
          {building-id-2 :building-id} (attr/upsert-building-attribute! "753-R" {:field "vtjprt" :value "1030462707"})
          {buildings :data
           :as       result}           (query sipoo :buildings :organizationId "753-R")]
      (is (ok? result))
      (is (= 2 (count buildings)))
      (is (= #{{:id building-id-1 :vtjprt "1234567892" :modified 1234 :sent-to-archive nil}
               {:id building-id-2 :vtjprt "1030462707" :modified 1234 :sent-to-archive nil}}
             (set buildings))))))

(deftest update-building1-test
  (testing "fails"
    (testing "with unauthorized when"
      (let [some-attribute {:field "ratu" :value "fooval"}]

        (testing "user is not org admin"
          (is (unauthorized? (command sonja :update-building :organizationId "753-R"
                                      :building-update some-attribute))))

        (testing "user belongs to a different organization"
          (is (unauthorized? (command sipoo :update-building :organizationId "991-R"
                                      :building-update some-attribute))))))

    (testing "when"
      (testing "updating unknown building"
        (is (expected-failure? :unknown-building-id
                               (command sipoo :update-building :organizationId "753-R"
                                        :building-update {:id "foo" :field "ratu" :value "ratu-1"}))))

      (testing "field is not an allowed field"
        (is (bad-attribute? (command sipoo :update-building :organizationId "753-R"
                                     :building-update {:field "invalid-field-yo" :value "ratu-1"}))))

      (testing "trying to set VTJ-PRT field to an invalid value"
        (is (bad-attribute? (command sipoo :update-building :organizationId "753-R"
                                     :building-update {:field "vtjprt" :value "en-ole-oikea-vtj-prt"}))))

      (testing "trying to set VTJ-PRT field to empty value"
        (is (bad-attribute? (command sipoo :update-building :organizationId "753-R"
                                     :building-update {:field "vtjprt" :value ""})))

        (is (bad-attribute? (command sipoo :update-building :organizationId "753-R"
                                     :building-update {:field "vtjprt" :value nil})))))))

(def no-vtj-prt? (partial expected-failure? :error.building.external-building-id-required))

(deftest no-vtj-prt-test
  (testing "trying to create a new building without vjt-prt with only"
    (testing "ratu"
      (is  (no-vtj-prt? (command sipoo :update-building
                                 :organizationId "753-R"
                                 :building-update {:field "ratu" :value "ratu"}))))
    (testing "kiinteistotunnus"
      (is (no-vtj-prt? (command sipoo :update-building
                                :organizationId "753-R"
                                :building-update {:field "kiinteistotunnus" :value "11122233334444"}))))
    (testing "visibility"
      (is (no-vtj-prt? (command sipoo :update-building
                                :organizationId "753-R"
                                :building-update {:field "visibility" :value "viranomainen"}))))
    (testing "publicity"
      (is (no-vtj-prt? (command sipoo :update-building
                                :organizationId "753-R"
                                :building-update {:field "publicity" :value "julkinen"}))))
    (testing "myyntipalvelussa"
      (is (no-vtj-prt? (command sipoo :update-building
                                :organizationId "753-R"
                                :building-update {:field "myyntipalvelussa" :value true}))))
    (testing "comment"
      (is (no-vtj-prt? (command sipoo :update-building
                                :organizationId "753-R"
                                :building-update {:field "comment" :value "some comment"}))))
    (testing "address"
      (is (no-vtj-prt? (command sipoo :update-building
                                :organizationId "753-R"
                                :building-update {:field "address" :value "Some address"}))))))

(deftest update-building2-test
  (testing "when trying to set"
    (let [{{:keys [building-id]} :data
           :as response} (command sipoo :update-building
                                  :organizationId "753-R"
                                  :building-update {:field "vtjprt" :value "1234567892"})]
      (is (ok? response))
      (is (boolean building-id))
      (testing "an invalid visibility value"
        (is (bad-attribute? (command sipoo :update-building :organizationId "753-R"
                                     :building-update {:id    building-id
                                                       :field "visibility"
                                                       :value "en-ole-visibility"}))))

      (testing "an invalid publicity value"
        (is (bad-attribute? (command sipoo :update-building :organizationId "753-R"
                                     :building-update {:id    building-id
                                                       :field "publicity"
                                                       :value "en-ole-publicity"}))))
      (testing "wrong organization"
        (is (not-found? (command tampere :update-building :organizationId "837-R"
                                 :building-update {:id    building-id
                                                   :field "publicity"
                                                   :value "salainen"})))))))

(deftest update-building3-test
  (testing "when adding a building with duplicate vtjprt"
    (let [{{:keys [building-id]} :data} (command sipoo :update-building
                                                 :organizationId "753-R"
                                                 :building-update {:field "vtjprt" :value "1234567892"})
          duplicate-result              (command sipoo :update-building
                                                 :organizationId "753-R"
                                                 :building-update {:field "vtjprt" :value "1234567892"})]
      (is (expected-failure? :error.building.duplicate-identifier duplicate-result))
      (is (= {:duplicates [{:id building-id :vtjprt "1234567892"}]}
             (:error-data duplicate-result))))))

(deftest update-building4-test
  (testing "when updating a building with an already existing identifier (vtjprt)"
    (let [{{building-id-a :building-id} :data} (command sipoo :update-building
                                                        :organizationId "753-R"
                                                        :building-update {:field "vtjprt"
                                                                          :value "1234567892"})
          {{building-id-b :building-id} :data} (command sipoo :update-building
                                                        :organizationId "753-R"
                                                        :building-update {:field "vtjprt"
                                                                          :value "100012345N"})
          duplicate-update                     (command sipoo :update-building
                                                        :organizationId "753-R"
                                                        :building-update {:id    building-id-b
                                                                          :field "vtjprt"
                                                                          :value "1234567892"})]

      (is (expected-failure? :error.building.duplicate-identifier duplicate-update))
      (is (= {:duplicates [{:id building-id-a :vtjprt "1234567892"}]}
             (:error-data duplicate-update))))))

(deftest create-building-test
  (testing "creates"
    (testing "a new building with vtj-prt"
      (let [{{:keys [building-id]} :data
             :as                   result} (command sipoo :update-building
                                                    :organizationId "753-R"
                                                    :building-update {:field "vtjprt" :value "1234567892"})]
        (is (ok? result))
        (is (has-keys? [:building-id :building] (:data result)))

        (testing "and the new building can be fetched"
          (is (= {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}
                 (building-with-id building-id (query sipoo :buildings :organizationId "753-R")))))))))


(deftest create-building2-test
  (testing "a building and sets all possible fields"
    (let [{{building-id-a :building-id} :data} (command sipoo :update-building :organizationId "753-R"
                                                        :building-update  {:field "vtjprt" :value "1234567892"})]
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id-a
                                          :field "ratu"
                                          :value "ratu-1"})))
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id-a
                                          :field "kiinteistotunnus"
                                          :value "11122233335555"})))
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id-a
                                          :field "visibility"
                                          :value "viranomainen"})))
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id-a
                                          :field "publicity"
                                          :value "julkinen"})))
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id-a
                                          :field "myyntipalvelussa"
                                          :value true})))

      (is (= {:id               building-id-a
              :ratu             "ratu-1"
              :vtjprt           "1234567892"
              :kiinteistotunnus "11122233335555"
              :visibility       "viranomainen"
              :publicity        "julkinen"
              :myyntipalvelussa true
              :modified         ts
              :sent-to-archive  nil}
             (building-with-id building-id-a (query sipoo :buildings :organizationId "753-R")))))

    ;;Ensures that duplicate check only works on identifiers and not other fields
    (testing "and allows setting duplicate values for another other buildings for fields that are not identifiers"
      (let [{{building-id-b :building-id} :data} (command sipoo :update-building :organizationId "753-R"
                                                          :building-update {:field "vtjprt" :value "1030462707"})]

        (is (ok? (command sipoo :update-building :organizationId "753-R"
                          :building-update {:id    building-id-b
                                            :field "visibility"
                                            :value "viranomainen"})))

        (is (ok? (command sipoo :update-building :organizationId "753-R"
                          :building-update {:id    building-id-b
                                            :field "publicity"
                                            :value "julkinen"})))

        (is (ok? (command sipoo :update-building :organizationId "753-R"
                          :building-update {:id    building-id-b
                                            :field "myyntipalvelussa"
                                            :value true})))

        (is (= {:id               building-id-b
                :vtjprt           "1030462707"
                :visibility       "viranomainen"
                :publicity        "julkinen"
                :myyntipalvelussa true
                :modified         ts
                :sent-to-archive  nil}
               (building-with-id building-id-b (query sipoo :buildings :organizationId "753-R"))))))))

(deftest create-building3-test
  (testing "a new building and"
    (testing "removes ratu by setting it to null"
      ;; Adds an initial centry so that we have more than one entry in the data to make
      ;; this more realistic.  We want to check that the duplicate check does not
      ;; interpret null identifiers as duplicates
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:field "vtjprt" :value "1030462707"})))
      (let [{{:keys [building-id]} :data} (command sipoo :update-building :organizationId "753-R"
                                                   :building-update {:field "vtjprt"
                                                                     :value "1234567892"})]

        (is (ok? (command sipoo :update-building :organizationId "753-R"
                          :building-update {:id    building-id
                                            :field "ratu"
                                            :value "ratu-3"})))
        (is (= {:ok true :data {:building-id building-id
                                :building    {:id              building-id
                                              :modified        ts
                                              :vtjprt          "1234567892"
                                              :sent-to-archive nil}}}
               (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "ratu"
                                          :value nil})))

        (is (= {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}
               (building-with-id building-id (query sipoo :buildings :organizationId "753-R"))))))))

(deftest create-building4-test
  (testing "sets kiinteistotunnus to null"
    ;; Adds an initial centry so that we have more than one entry in the data to make this
    ;; more realistic.  We want to check that the duplicate check does not interpret null
    ;; identifiers as duplicates

    (is (ok? (command sipoo :update-building :organizationId "753-R"
                      :building-update {:field "vtjprt"
                                        :value "1030462707"})))

    (let [{{:keys [building-id]} :data} (command sipoo :update-building :organizationId "753-R"
                                                 :building-update {:field "vtjprt"
                                                                   :value "1234567892"}) ]

      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "kiinteistotunnus"
                                          :value "11122233335555"})))

      (is (= {:ok true :data {:building-id building-id
                              :building    {:id              building-id
                                            :modified        ts
                                            :vtjprt          "1234567892"
                                            :sent-to-archive nil}}}
             (command sipoo :update-building :organizationId "753-R"
                      :building-update {:id    building-id
                                        :field "kiinteistotunnus"
                                        :value nil})))

      (is (= {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}
             (building-with-id building-id (query sipoo :buildings :organizationId "753-R")))))))

(deftest create-building5-test
  (testing "sets visibility to null"
    (let [{{:keys [building-id]} :data} (command sipoo :update-building :organizationId "753-R"
                                                 :building-update {:field "vtjprt"
                                                                   :value "1234567892"}) ]

      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "visibility"
                                          :value "viranomainen"})))
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "visibility"
                                          :value nil})))
      (is (= {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}
             (building-with-id building-id (query sipoo :buildings :organizationId "753-R")))))))

(deftest create-building6-test
  (testing "sets publicity to null"
    (let [{{:keys [building-id]} :data} (command sipoo :update-building :organizationId "753-R"
                                                 :building-update {:field "vtjprt"
                                                                   :value "1234567892"})]

      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "publicity"
                                          :value "salainen"})))
      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "publicity"
                                          :value nil})))
      (is (= {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}
             (building-with-id building-id (query sipoo :buildings :organizationId "753-R")))))))

(deftest create-building7-test
  (testing "Sets its identifier field to the same value as before. The building itself should not be counted "
    (let [{{:keys [building-id]} :data} (command sipoo :update-building :organizationId "753-R"
                                                 :building-update {:field "vtjprt"
                                                                   :value "1234567892"}) ]

      (is (ok? (command sipoo :update-building :organizationId "753-R"
                        :building-update {:id    building-id
                                          :field "vtjprt"
                                          :value "1234567892"}))))))

(deftest remove-building-test
  (testing "remove-building"
    (let [{{:keys [building-id]} :data} (command sipoo :update-building :organizationId "753-R"
                                                 :building-update {:field "vtjprt" :value "1234567892"})]
      (testing "fails"
        (testing "with unauthorized when"
          (testing "user is not org admin"
            (is (unauthorized? (command sonja :remove-building :organizationId "753-R"
                                        :building-id building-id))))

          (testing "no auths for the organization"
            (is (unauthorized? (command sipoo :remove-building :organizationId "991-R"
                                        :building-id building-id))))

          (testing "user belongs to a different organization"
            (is (not-found? (command tampere :remove-building :organizationId "837-R"
                                     :building-id building-id)))))

        (testing "when building id does not exist in the organization"
          (is (not-found? (command sipoo :remove-building :organizationId "753-R"
                                   :building-id "en-ole-building-id"))))

        (testing "when building id is not a string"
          (is (schema-error? (command sipoo :remove-building :organizationId "753-R"
                                      :building-id false)))))

      (testing "removes building given an existing building id in the organization"
        (is (ok? (command sipoo :remove-building :organizationId "753-R" :building-id building-id)))
        (is (nil? (building-with-id building-id (query sipoo :buildings :organizationId "753-R"))))

        (testing  "and new building can be added afterwards (ensures document remains valid)"
          (is (ok? (command sipoo :update-building :organizationId "753-R"
                            :building-update  {:field "vtjprt" :value "1234567892"}))))))))
