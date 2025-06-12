(ns lupapalvelu.application-utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.application :refer [make-application-id]]
            [lupapalvelu.application-utils :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as o]
            [sade.date :as date]
            [sade.env :as env]))

(deftest operation-description-test
  (let [app        {:primaryOperation {:name "kerrostalo-rivitalo"}}
        legacy-app {:primaryOperation nil}]
    (testing "normal application"
      (is (= "Asuinkerrostalon tai rivitalon rakentaminen"
             (operation-description app :fi)))
      (is (= "Byggande av flervåningshus eller radhus"
             (operation-description app :sv))))
    (testing "legacy application"
      (is (= "" (operation-description legacy-app :fi)))
      (is (= "" (operation-description legacy-app :sv))))))

(deftest person-id-masker-for-user-test
  (testing "handler authority - no masking"
    (is (= {:schema-info {:name "maksaja"}
            :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}}
           ((person-id-masker-for-user {:id "id" :role :authority}
                                       {:handlers [{:userId "id"}]})
            {:schema-info {:name "maksaja"}
             :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}}))))

  (testing "non handler authority"
    (is (= {:schema-info {:name "maksaja"}
            :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-****"}}}}}
           ((person-id-masker-for-user {:id       "id"
                                        :role     :authority
                                        :orgAuthz {:org-id #{:authority}}}
                                       {:organization "org-id"
                                        :handlers     [{:userId "other-id"}]})
            {:schema-info {:name "maksaja"}
             :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}}))))

  (testing "authority in different organization"
    (is (= {:schema-info {:name "maksaja"}
            :data        {:henkilo {:henkilotiedot {:hetu {:value "******-****"}}}}}
           ((person-id-masker-for-user {:id       "id"
                                        :role     :authority
                                        :orgAuthz {:another-org-id #{:authority}}}
                                       {:organization "org-id"
                                        :handlers     [{:userId "other-id"}]})
            {:schema-info {:name "maksaja"}
             :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}}))))

  (testing "non authority user"
    (is (= {:schema-info {:name "maksaja"}
            :data        {:henkilo {:henkilotiedot {:hetu {:value "******-****"}}}}}
           ((person-id-masker-for-user {:id "id" :role :authority}
                                       {:handlers [{:userId "other-id"}]})
            {:schema-info {:name "maksaja"}
             :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-5522"}}}}})))))

(deftest get-sorted-operation-documents-test
  (testing "one operation - two docs"
    (is (= [{:schema-info {:name :doc1 :op {:id "op1"}}}]
           (get-sorted-operation-documents {:documents           [{:schema-info {:name :doc1 :op {:id "op1"}}}
                                                                  {:schema-info {:name :doc2}}]
                                            :primaryOperation    {:id "op1" :name "paatoimenpide" :description "" :created 0}
                                            :secondaryOperations []}))))

  (testing "one operation - missing doc"
    (is (= []
           (get-sorted-operation-documents {:documents           [{:schema-info {:name :doc2 :op {:id "op2"}}}]
                                            :primaryOperation    {:id "op1" :name "paatoimenpide" :description "" :created 0}
                                            :secondaryOperations []}))))

  (testing "multiple operations"
    (is (= [{:schema-info {:name :doc5 :op {:id "op5"}}}
            {:schema-info {:name :doc1 :op {:id "op1"}}}
            {:schema-info {:name :doc6 :op {:id "op2"}}}
            {:schema-info {:name :doc3 :op {:id "op3"}}}
            {:schema-info {:name :doc4 :op {:id "op4"}}}]
           (get-sorted-operation-documents {:documents           [{:schema-info {:name :doc1 :op {:id "op1"}}}
                                                                  {:schema-info {:name :doc2}}
                                                                  {:schema-info {:name :doc3 :op {:id "op3"}}}
                                                                  {:schema-info {:name :doc4 :op {:id "op4"}}}
                                                                  {:schema-info {:name :doc5 :op {:id "op5"}}}
                                                                  {:schema-info {:name :doc6 :op {:id "op2"}}}
                                                                  {:schema-info {:name :doc7}}]
                                            :primaryOperation    {:id "op5" :name "paatoimenpide" :description "" :created 4}
                                            :secondaryOperations [{:id "op4" :name "muutoimenpide" :description "" :created 3}
                                                                  {:id "op2" :name "eritoimenpide" :description "" :created 1}
                                                                  {:id "op3" :name "jokutoimenpide" :description "" :created 2}
                                                                  {:id "op1" :name "sivutoimenpide" :description "" :created 0}]})))))

(deftest vrk-lupatunnus-test
  (env/with-feature-value :prefixed-id false
    (is (nil? (vrk-lupatunnus {:municipality "" :created nil})))
    (is (= "12300016-0321"
           (vrk-lupatunnus {:municipality "123"
                            :created      (date/timestamp "11.10.2016")
                            :id           "LP-123-2016-00321"})))
    (is (= "12300017-0321"
           (vrk-lupatunnus {:municipality "123"
                            :created      (date/timestamp "11.10.2016")
                            :submitted    (date/timestamp "1.1.2017")
                            :id           "LP-123-2016-00321"})))

    (testing "special '0000'"
      (is (= "12300017-1000"
             (vrk-lupatunnus {:municipality "123"
                              :created      (date/timestamp "11.10.2016")
                              :submitted    (date/timestamp "1.1.2017")
                              :id           "LP-123-2016-10000"}))))

    (testing "from a verdict"
      (is (= "hello-world"
             (vrk-lupatunnus {:verdicts [{:vrk-lupatunnus "hello-world"}]})))
      (is (= "hello-world"
             (vrk-lupatunnus {:verdicts [{}
                                         {:vrk-lupatunnus "  "}
                                         {:vrk-lupatunnus "hello-world"}]})))
      (is (= "hello-world"
             (vrk-lupatunnus {:municipality "123"
                              :created      (date/timestamp "11.10.2016")
                              :submitted    (date/timestamp "1.1.2017")
                              :id           "LP-123-2016-10000"
                              :verdicts     [{:vrk-lupatunnus "hello-world"}]}))))

    (testing "with sequence"
      (with-redefs [mongo/get-next-sequence-value (constantly 12)]
        (is (= "12300017-0012"
               (vrk-lupatunnus {:municipality "123"
                                :created      (date/timestamp "11.10.2016")
                                :submitted    (date/timestamp "1.1.2017")
                                :id           (make-application-id "123")})))))

    (testing "with sequence 9999"
      (with-redefs [mongo/get-next-sequence-value (constantly 9999)]
        (is (= "12300017-9999"
               (vrk-lupatunnus {:municipality "123"
                                :created      (date/timestamp "11.10.2016")
                                :submitted    (date/timestamp "1.1.2017")
                                :id           (make-application-id "123")})))))

    (testing "with sequence 10000"
      (with-redefs [mongo/get-next-sequence-value (constantly 10000)]
        (is (= "12300017-1000"
               (vrk-lupatunnus {:municipality "123"
                                :created      (date/timestamp "11.10.2016")
                                :submitted    (date/timestamp "1.1.2017")
                                :id           (make-application-id "123")})))))))

(deftest operation-metadata-value-test
  (is (true? (operation-metadata-value? {:primaryOperation {:name "pientalo"}}
                                        :building)))
  (is (false? (operation-metadata-value? {:primaryOperation {:name "pientalo"}}
                                         :kuntagml-226)))
  (is (false? (operation-metadata-value? {:primaryOperation {:name "pientalo"}}
                                         "foobar")))
  (is (true? (operation-metadata-value? {:primaryOperation    {:name "hiihoo"}
                                         :secondaryOperations [{:name "aita"}
                                                               {:name "pientalo-laaj"}]}
                                        "building")))
  (is (true? (operation-metadata-value? {:primaryOperation    {:name "hiihoo"}
                                         :secondaryOperations [{:name "erityista-toimintaa-alue"}
                                                               {:name "pientalo-laaj"}]}
                                        :kuntagml-226))))

(defn make-org [version]
  (assoc-in {} [:krysp :R :version] version))

(deftest check-kuntagml-226-requirement-test
  (is (nil? (check-kuntagml-226-requirement {})))
  (is (nil? (check-kuntagml-226-requirement {:application {:primaryOperation {:name "sijoittamislupa"}}})))
  (is (nil? (check-kuntagml-226-requirement {:application  {:primaryOperation {:name "sijoittamislupa"}}
                                             :organization (delay (make-org "2.2.4"))})))

  (with-redefs [o/krysp-write-integration? (fn [_ _] true)]
    (is (= {:ok false :text "error.kuntagml-226-required"}
           (check-kuntagml-226-requirement {:application  {:primaryOperation {:name "sijoittamislupa"}
                                                           :permitType       "R"}
                                            :organization (delay (make-org "2.2.4"))})))
    (is (= {:ok false :text "error.kuntagml-226-required"}
           (check-kuntagml-226-requirement {:application  {:primaryOperation    {:name "maalampo"}
                                                           :secondaryOperations [{:name "erityista-toimintaa-alue"}]
                                                           :permitType          "R"}
                                            :organization (delay (make-org "2.2.4"))}))))

  (is (nil? (check-kuntagml-226-requirement {:application  {:primaryOperation {:name "aita"}
                                                            :permitType       "R"}
                                             :organization (delay (make-org "2.2.4"))})))

  (with-redefs [o/krysp-write-integration? (fn [_ _] false)]
    (is (nil? (check-kuntagml-226-requirement {:application  {:primaryOperation {:name "sijoittamislupa"}
                                                              :permitType       "R"}
                                               :organization (delay (make-org "2.2.4"))}))))

  (with-redefs [o/krysp-write-integration? (fn [_ _] true)]
    (is (nil? (check-kuntagml-226-requirement {:application  {:primaryOperation {:name "sijoittamislupa"}
                                                              :permitType       "R"}
                                               :organization (make-org "2.2.6")})))))

(deftest source-application-info-test
  (testing "source-application-info"
    (testing "returns normalized source info"
      (is (= {:id "LP-123-2020-00001" :kind :copy}
             (source-application-info {:_source-application {:id "LP-123-2020-00001"
                                                             :kind :copy}})))
      (is (= {:id "LP-123-2020-00001" :kind :copy}
             (source-application-info {:_source-application {:id "LP-123-2020-00001"
                                                             :kind "copy"}}))))

    (testing "filters by kind"
      (is (nil? (source-application-info {:_source-application {:id "LP-123-2020-00001"
                                                                :kind :copy}}
                                         :change)))
      (is (= {:id "LP-123-2020-00001" :kind :copy}
             (source-application-info {:_source-application {:id "LP-123-2020-00001"
                                                             :kind :copy}}
                                      "copy"))))

    (testing "throws for invalid source info"
      (is (thrown? clojure.lang.ExceptionInfo
                   (source-application-info {:_source-application {:kind :copy}})))))

  (testing "->source-application-info"
    (testing "builds source info from application"
      (is (= {:id "LP-123-2020-00002" :kind :change}
             (->source-application-info {:id "LP-123-2020-00002"} :change))))

    (testing "builds source info directly from id"
      (is (= {:id "LP-123-2020-00003" :kind :continuation}
             (->source-application-info "LP-123-2020-00003" :continuation)))))

  (testing "source-application"
    (testing "loads source application by id"
      (with-redefs [mongo/select-one (fn [_ {id :_id} & _]
                                       (when (= id "LP-123-2020-00004")
                                         {:id "LP-123-2020-00004" :name "Source app"}))]
        (is (= {:id "LP-123-2020-00004" :name "Source app"}
               (source-application {:_source-application {:id "LP-123-2020-00004"
                                                          :kind :copy}})))))

    (testing "returns nil when kind does not match"
      (is (nil? (source-application {:_source-application {:id "LP-123-2020-00004"
                                                           :kind :copy}}
                                    :kind :change))))))
