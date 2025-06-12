(ns lupapalvelu.elasticsearch.foremen-indexing-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.elasticsearch.foremen :as es-foremen]))

(def base-application {:id           "LP-999-9999-99999"
                       :address      "Test Address 7"
                       :organization "753-R"
                       :municipality "753"
                       :documents    [{:id          "697b621718c53f4923a41983"
                                       :schema-info {:name "tyonjohtaja-v3"}
                                       :tj-document true
                                       :data        {:kuntaRoolikoodi {:value "IV-työnjohtaja"}
                                                     :henkilotiedot   {:etunimi  {:value "Tauno"}
                                                                       :sukunimi {:value "Työnjohtaja"}}}}]
                       :propertyId   "75300000000000"
                       :location     [1 2]})

(deftest application->es-foreman-documents-test
  (testing "Converts application to ES foreman documents"
    (is (= [{:application-id "LP-999-9999-99999"
             :address        "Test Address 7"
             :address-codes  "7"
             :foreman        "Työnjohtaja Tauno"
             :text-search    "LP-999-9999-99999 Työnjohtaja Tauno"
             :municipality   "753"
             :organization   "753-R"
             :id             "697b621718c53f4923a41983"
             :foremanRole    "IV-työnjohtaja"
             :propertyId     "75300000000000"
             :location       {:x 1, :y 2}}]
           (es-foremen/application->es-foreman-documents base-application)))))

(deftest multiple-foreman-documents-test
  (testing "Handles multiple foreman documents in single application"
    (let [application  (assoc base-application
                              :documents [{:id          "doc-1"
                                           :schema-info {:name "tyonjohtaja-v3"}
                                           :tj-document true
                                           :data        {:kuntaRoolikoodi {:value "vastaava työnjohtaja"}
                                                         :henkilotiedot   {:etunimi  {:value "Matti"}
                                                                           :sukunimi {:value "Meikäläinen"}}}}
                                          {:id          "doc-2"
                                           :schema-info {:name "tyonjohtaja-v3"}
                                           :tj-document true
                                           :data        {:kuntaRoolikoodi {:value "KVV-työnjohtaja"}
                                                         :henkilotiedot   {:etunimi  {:value "Liisa"}
                                                                           :sukunimi {:value "Virtanen"}}}}])
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 2 (count es-documents)))
      (is (= "Meikäläinen Matti" (:foreman (first es-documents))))
      (is (= "Virtanen Liisa" (:foreman (second es-documents))))
      (is (= "vastaava työnjohtaja" (:foremanRole (first es-documents))))
      (is (= "KVV-työnjohtaja" (:foremanRole (second es-documents)))))))

(deftest no-foreman-documents-test
  (testing "Returns empty vector when no foreman documents"
    (let [application (assoc base-application :documents [])]
      (is (= [] (es-foremen/application->es-foreman-documents application))))))


(deftest changelog-submitted-timestamp-test
  (testing "Extracts submitted timestamp from changeLog"
    (let [application  (assoc base-application
                              :documents [{:id          "doc-1"
                                           :schema-info {:name "tyonjohtaja-v3"}
                                           :tj-document true
                                           :created     1000
                                           :changeLog   [{:ts 2000 :value "draft"}
                                                         {:ts 3000 :value "submitted"}
                                                         {:ts 4000 :value "approved"}]
                                           :data        {:kuntaRoolikoodi {:value "IV-työnjohtaja"}
                                                         :henkilotiedot   {:etunimi  {:value "Tauno"}
                                                                           :sukunimi {:value "Työnjohtaja"}}}}])
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 1 (count es-documents)))
      (is (= 3000 (:submitted (first es-documents))))
      (is (= 4000 (:modified (first es-documents)))))))

(deftest changelog-declared-timestamp-test
  (testing "Extracts declared timestamp as submitted from changeLog"
    (let [application  (assoc base-application
                              :documents [{:id          "doc-1"
                                           :schema-info {:name "tyonjohtaja-v3"}
                                           :tj-document true
                                           :created     1000
                                           :changeLog   [{:ts 2000 :value "draft"}
                                                         {:ts 3000 :value "declared"}]
                                           :data        {:kuntaRoolikoodi {:value "IV-työnjohtaja"}
                                                         :henkilotiedot   {:etunimi  {:value "Tauno"}
                                                                           :sukunimi {:value "Työnjohtaja"}}}}])
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 1 (count es-documents)))
      (is (= 3000 (:submitted (first es-documents))))
      (is (= 3000 (:modified (first es-documents)))))))

(deftest no-changelog-test
  (testing "Uses created timestamp when no changeLog"
    (let [application  (assoc base-application
                              :documents [{:id          "doc-1"
                                           :schema-info {:name "tyonjohtaja-v3"}
                                           :tj-document true
                                           :created     1000
                                           :data        {:kuntaRoolikoodi {:value "IV-työnjohtaja"}
                                                         :henkilotiedot   {:etunimi  {:value "Tauno"}
                                                                           :sukunimi {:value "Työnjohtaja"}}}}])
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 1 (count es-documents)))
      (is (not (contains? (first es-documents) :submitted)))
      (is (= 1000 (:modified (first es-documents)))))))

(deftest text-search-content-test
  (testing "Builds text-search content from application and document data"
    (let [es-documents (es-foremen/application->es-foreman-documents base-application)]
      (is (= "LP-999-9999-99999 Työnjohtaja Tauno"
             (:text-search (first es-documents)))))))

(deftest search-with-kuntalupatunnus-test
  (testing "Kuntalupatunnus is included in foreman es document"
    (let [application  (assoc base-application :verdicts [{:kuntalupatunnus "2024-001"}])
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 1 (count es-documents)))
      (is (= "2024-001" (:kuntalupatunnus (first es-documents)))))))

(deftest search-with-address-test
  (testing "Address and address-codes are included in foreman es document"
    (let [application  (assoc base-application :address "Test Address 7A B")
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 1 (count es-documents)))
      (is (= "Test Address 7A B" (:address (first es-documents))))
      (is (= "7 A B" (:address-codes (first es-documents)))))))

(deftest document-fields-included-test
  (testing "Includes document-level fields like created, state, type, started"
    (let [application  (assoc base-application
                              :documents [{:id          "doc-1"
                                           :schema-info {:name "tyonjohtaja-v3"}
                                           :tj-document true
                                           :created     5000
                                           :state       "approved"
                                           :type        "application"
                                           :started     6000
                                           :data        {:kuntaRoolikoodi {:value "IV-työnjohtaja"}
                                                         :henkilotiedot   {:etunimi  {:value "Tauno"}
                                                                           :sukunimi {:value "Työnjohtaja"}}}}])
          es-documents (es-foremen/application->es-foreman-documents application)]
      (is (= 1 (count es-documents)))
      (is (= 5000 (:created (first es-documents))))
      (is (= "approved" (:state (first es-documents))))
      (is (= "application" (:type (first es-documents))))
      (is (= 6000 (:started (first es-documents)))))))
