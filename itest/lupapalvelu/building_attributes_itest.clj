(ns lupapalvelu.building-attributes-itest
  (:require [lupapalvelu.building-attributes :as attr]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core]))


(mount/start #'mongo/connection)

(defn has-keys [& ks]
  (fn [m]
    (fact {:midje/description (str "has-keys " ks)}
          (keys m) => (contains ks :in-any-order))))

(defn building-with-id [building-id buildings-result]
  (some (fn [{:keys [id] :as building}] (if (= id building-id) building)) (:data buildings-result)))

(defn clear-buildings! []
  (mongo/drop-collection :building-info))

(def ts 1234)

(mongo/with-db test-db-name
  (lupapalvelu.fixture.core/apply-fixture "minimal")

  (with-redefs [sade.core/now (fn [] ts)]
    (facts
     (facts "buildings"
            (clear-buildings!)
            (fact "fails with unauthorized when"
                  (fact "user is not org admin"
                        (local-query sonja :buildings :organizationId "753-R") => unauthorized?)

                  (fact "user belongs to a different organization"
                        (local-query sipoo :buildings :organizationId "991-R") => unauthorized?))

            (fact "fails with wrong scope when requested not in R organization"
                  (local-query sipoo-ya :buildings :organizationId "753-YA") => (partial expected-failure? :error.unsupported-permit-type))

            (fact "returns zero building attributes when none inserted"
                  (-> (local-query sipoo :buildings :organizationId "753-R")
                      :data
                      count) => 0)

            (fact "returns attributes when some found"
                  (let [{building-id-1 :building-id} (attr/upsert-building-attribute! "753-R" {:field "vtjprt" :value "1234567892"})
                        {building-id-2 :building-id} (attr/upsert-building-attribute! "753-R" {:field "vtjprt" :value "1030462707"})
                        result (local-query sipoo :buildings :organizationId "753-R")
                        buildings (:data result)]
                    result => ok?
                    (count buildings) => 2
                    (set buildings) => #{{:id building-id-1 :vtjprt "1234567892" :modified 1234 :sent-to-archive nil}
                                         {:id building-id-2 :vtjprt "1030462707" :modified 1234 :sent-to-archive nil}})))

     (facts "update-building"
            (clear-buildings!)
            (facts "fails"
                          (fact "with unauthorized when"
                                (let [some-attribute {:field "ratu" :value "fooval"}]

                                  (fact "user is not org admin"
                                        (local-command sonja :update-building :organizationId "753-R" :building-update some-attribute) => unauthorized?)

                                  (fact "user belongs to a different organization"
                                        (local-command sipoo :update-building :organizationId "991-R" :building-update some-attribute) => unauthorized?)))

                          (fact "when"
                                (fact "updating unknown building"
                                      (local-command sipoo :update-building :organizationId "753-R" :building-update {:id "foo" :field "ratu" :value "ratu-1"})
                                      => (partial expected-failure? :unknown-building-id))

                                (fact "field is not an allowed field"
                                      (local-command sipoo :update-building :organizationId "753-R" :building-update {:field "invalid-field-yo" :value "ratu-1"})
                                      => (partial expected-failure? :error.building.invalid-attribute))

                                (fact "trying to set VTJ-PRT field to an invalid value"
                                      (local-command sipoo :update-building :organizationId "753-R" :building-update {:field "vtjprt" :value "en-ole-oikea-vtj-prt"})

                                      => (partial expected-failure? :error.building.invalid-attribute))

                                (fact "trying to set VTJ-PRT field to empty value"
                                      (local-command sipoo :update-building :organizationId "753-R" :building-update {:field "vtjprt" :value ""})
                                      => (partial expected-failure? :error.building.invalid-attribute)


                                      (local-command sipoo :update-building :organizationId "753-R" :building-update {:field "vtjprt" :value nil})
                                      => (partial expected-failure? :error.building.invalid-attribute))

                                (fact "trying to create a new building without vjt-prt with only"
                                      (clear-buildings!)
                                      (fact "ratu"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "ratu" :value "ratu"})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))
                                      (fact "kiinteistotunnus"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "kiinteistotunnus" :value "11122233334444"})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))
                                      (fact "visibility"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "visibility" :value "viranomainen"})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))
                                      (fact "publicity"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "publicity" :value "julkinen"})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))
                                      (fact "myyntipalvelussa"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "myyntipalvelussa" :value true})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))
                                      (fact "comment"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "comment" :value "some comment"})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))
                                      (fact "address"
                                            (let [result (local-command sipoo :update-building
                                                                        :organizationId "753-R"
                                                                        :building-update {:field "address" :value "Some address"})]
                                              result => (partial expected-failure? :error.building.external-building-id-required)))))

                          (fact "when trying to set"
                                (clear-buildings!)
                                (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building
                                                                                              :organizationId "753-R"
                                                                                              :building-update {:field "ratu" :value "ratu-1"})]
                                  (fact "an invalid visibility value"                              (local-command sipoo :update-building :organizationId "753-R"
                                                                                                                  :building-update {:id building-id
                                                                                                                                    :field "visibility"
                                                                                                                                    :value "en-ole-visibility"})
                                        => (partial expected-failure? :error.building.invalid-attribute))

                                  (fact "an invalid publicity value"
                                        (local-command sipoo :update-building :organizationId "753-R"
                                                       :building-update {:id building-id
                                                                         :field "publicity"
                                                                         :value "en-ole-publicity"})
                                        => (partial expected-failure? :error.building.invalid-attribute))
                                  (fact "an invalid publicity value"
                                        (local-command sipoo :update-building :organizationId "753-R"
                                                       :building-update {:id building-id
                                                                         :field "publicity"
                                                                         :value "en-ole-publicity"})
                                        => (partial expected-failure? :error.building.invalid-attribute))))

                          (fact "when adding a building with duplicate vtjprt"
                                (clear-buildings!)
                                (let [{{:keys [building-id]} :data :as first-result} (local-command sipoo :update-building
                                                                                                    :organizationId "753-R"
                                                                                                    :building-update {:field "vtjprt" :value "1234567892"})
                                      duplicate-result (local-command sipoo :update-building
                                                                      :organizationId "753-R"
                                                                      :building-update {:field "vtjprt" :value "1234567892"})]

                                  duplicate-result => (partial expected-failure? :error.building.duplicate-identifier)
                                  (:error-data duplicate-result) => {:duplicates [{:id building-id :vtjprt "1234567892"}]}))

                          (fact "when updating a building with an already existing identifier (vtjprt)"
                                (clear-buildings!)
                                (let [{{building-id-a :building-id} :data :as first-result} (local-command sipoo :update-building
                                                                                                           :organizationId "753-R"
                                                                                                           :building-update {:field "vtjprt" :value "1234567892"})
                                      {{building-id-b :building-id} :data :as second-result} (local-command sipoo :update-building
                                                                                                            :organizationId "753-R"
                                                                                                            :building-update {:field "vtjprt" :value "100012345N"})
                                      duplicate-update (local-command sipoo :update-building
                                                                      :organizationId "753-R"
                                                                      :building-update {:id building-id-b :field "vtjprt" :value "1234567892"})]

                                  duplicate-update => (partial expected-failure? :error.building.duplicate-identifier)
                                  (:error-data duplicate-update) => {:duplicates [{:id building-id-a :vtjprt "1234567892"}]})))

            (facts "creates"

                   (fact "a new building with vtj-prt"
                         (clear-buildings!)
                         (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building
                                                                                       :organizationId "753-R"
                                                                                       :building-update {:field "vtjprt" :value "1234567892"})]
                           result => ok?
                           (:data result) => (has-keys :building-id :building)

                           (fact "and the new building can be fetched"
                                 (building-with-id building-id (local-query sipoo :buildings :organizationId "753-R"))
                                 => {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil})))


                   (fact "a building and sets all possible fields"
                         (clear-buildings!)
                         (let [{{building-id-a :building-id} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                              :building-update  {:field "vtjprt" :value "1234567892"})]
                           (local-command sipoo :update-building :organizationId "753-R"
                                          :building-update {:id building-id-a
                                                            :field "ratu"
                                                            :value "ratu-1"}) => ok?
                           (local-command sipoo :update-building :organizationId "753-R"
                                          :building-update {:id building-id-a
                                                            :field "kiinteistotunnus"
                                                            :value "11122233335555"}) => ok?
                           (local-command sipoo :update-building :organizationId "753-R"
                                          :building-update {:id building-id-a
                                                            :field "visibility"
                                                            :value "viranomainen"}) => ok?
                           (local-command sipoo :update-building :organizationId "753-R"
                                          :building-update {:id building-id-a
                                                            :field "publicity"
                                                            :value "julkinen"}) => ok?
                           (local-command sipoo :update-building :organizationId "753-R"
                                          :building-update {:id building-id-a
                                                            :field "myyntipalvelussa"
                                                            :value true}) => ok?

                           (building-with-id building-id-a (local-query sipoo :buildings :organizationId "753-R"))
                           => {:id building-id-a
                               :ratu "ratu-1"
                               :vtjprt "1234567892"
                               :kiinteistotunnus "11122233335555"
                               :visibility "viranomainen"
                               :publicity "julkinen"
                               :myyntipalvelussa true
                               :modified ts
                               :sent-to-archive nil})

                         ;;Ensures that duplicate check only works on identifiers and not other fields
                         (fact "and allows setting duplicate values for another other buildings for fields that are not identifiers"
                               (let [{{building-id-b :building-id} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                                    :building-update {:field "vtjprt" :value "1030462707"})]

                                 (local-command sipoo :update-building :organizationId "753-R"
                                                :building-update {:id building-id-b
                                                                  :field "visibility"
                                                                  :value "viranomainen"}) => ok?

                                 (local-command sipoo :update-building :organizationId "753-R"
                                                :building-update {:id building-id-b
                                                                  :field "publicity"
                                                                  :value "julkinen"}) => ok?

                                 (local-command sipoo :update-building :organizationId "753-R"
                                                :building-update {:id building-id-b
                                                                  :field "myyntipalvelussa"
                                                                  :value true}) => ok?

                                 (building-with-id building-id-b (local-query sipoo :buildings :organizationId "753-R"))
                                 => {:id building-id-b
                                     :vtjprt "1030462707"
                                     :visibility "viranomainen"
                                     :publicity "julkinen"
                                     :myyntipalvelussa true
                                     :modified ts
                                     :sent-to-archive nil})))

                   (fact "a new building and"
                         (fact "removes ratu by setting it to null"
                               (clear-buildings!)
                               ;;Adds an initial centry so that we have more than one entry in the data to make this more realistic.
                               ;;We want to check that the duplicate check does not interpret null identifiers as duplicates
                               (local-command sipoo :update-building :organizationId "753-R"
                                              :building-update {:field "vtjprt" :value "1030462707"})
                               (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                             :building-update {:field "vtjprt"
                                                                                                               :value "1234567892"})]

                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "ratu"
                                                                                                                 :value "ratu-3"}) => ok?
                                 (let [remove-attr-resp (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                                        :field "ratu"
                                                                                                                                        :value nil})]
                                   remove-attr-resp => {:ok true :data {:building-id building-id
                                                                        :building {:id building-id
                                                                                   :modified ts
                                                                                   :vtjprt "1234567892"
                                                                                   :sent-to-archive nil}}})

                                 (building-with-id building-id (local-query sipoo :buildings :organizationId "753-R"))
                                 => {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}))

                         (fact "sets kiinteistotunnus to null"
                               (clear-buildings!)
                               ;;Adds an initial centry so that we have more than one entry in the data to make this more realistic.
                               ;;We want to check that the duplicate check does not interpret null identifiers as duplicates

                               (local-command sipoo :update-building :organizationId "753-R"
                                              :building-update {:field "vtjprt"
                                                                :value "1030462707"})

                               (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                             :building-update {:field "vtjprt"
                                                                                                               :value "1234567892"}) ]

                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "kiinteistotunnus"
                                                                                                                 :value "11122233335555"}) => ok?

                                 (let [remove-attr-resp (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                                        :field "kiinteistotunnus"
                                                                                                                                        :value nil})]
                                   remove-attr-resp => {:ok true :data {:building-id building-id
                                                                        :building {:id building-id
                                                                                   :modified ts
                                                                                   :vtjprt "1234567892"
                                                                                   :sent-to-archive nil}}})

                                 (building-with-id building-id (local-query sipoo :buildings :organizationId "753-R"))
                                 => {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}))

                         (fact "sets visibility to null"
                               (clear-buildings!)
                               (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                             :building-update {:field "vtjprt"
                                                                                                               :value "1234567892"}) ]

                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "visibility"
                                                                                                                 :value "viranomainen"}) => ok?
                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "visibility"
                                                                                                                 :value nil}) => ok?
                                 (building-with-id building-id (local-query sipoo :buildings :organizationId "753-R"))
                                 => {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}))

                         (fact "sets publicity to null"
                               (clear-buildings!)
                               (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                             :building-update {:field "vtjprt"
                                                                                                               :value "1234567892"})]

                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "publicity"
                                                                                                                 :value "salainen"}) => ok?
                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "publicity"
                                                                                                                 :value nil}) => ok?
                                 (building-with-id building-id (local-query sipoo :buildings :organizationId "753-R"))
                                 => {:id building-id :vtjprt "1234567892" :modified ts :sent-to-archive nil}))

                         (fact "Sets its identifier field to the same value as before. The building itself should not be counted "
                               (clear-buildings!)
                               (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                             :building-update {:field "vtjprt"
                                                                                                               :value "1234567892"}) ]

                                 (local-command sipoo :update-building :organizationId "753-R" :building-update {:id building-id
                                                                                                                 :field "vtjprt"
                                                                                                                 :value "1234567892"}) => ok?))))

            (facts "remove-building"
                          (clear-buildings!)

                          (let [{{:keys [building-id]} :data :as result} (local-command sipoo :update-building :organizationId "753-R"
                                                                                        :building-update {:field "vtjprt" :value "1234567892"})]
                            (fact "fails"
                                  (fact "with unauthorized when"
                                        (fact "user is not org admin"
                                              (local-command sonja :remove-building :organizationId "753-R" :building-id building-id) => unauthorized?)

                                        (fact "user belongs to a different organization"
                                              (local-command sipoo :remove-building :organizationId "991-R" :building-id building-id) => unauthorized?))

                                  (fact "when building id does not exist in the organization"
                                        (local-command sipoo :remove-building :organizationId "753-R" :building-id "en-ole-building-id")
                                        => (partial expected-failure? :unknown-building-id))

                                  (fact "when building id is not a string"
                                        (local-command sipoo :remove-building :organizationId "753-R" :building-id false)
                                        => (partial expected-failure? :error.building.invalid-remove-request)))

                            (fact "removes building given an existing building id in the organization"
                                  (local-command sipoo :remove-building :organizationId "753-R" :building-id building-id) => ok?
                                  (building-with-id building-id (local-query sipoo :buildings :organizationId "753-R")) => nil


                                  (fact  "and new building can be added afterwards (ensures document remains valid)"
                                         (local-command sipoo :update-building :organizationId "753-R"
                                                        :building-update  {:field "vtjprt" :value "1234567892"}) => ok?))))))))
