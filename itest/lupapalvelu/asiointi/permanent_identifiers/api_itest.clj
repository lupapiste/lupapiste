(ns lupapalvelu.asiointi.permanent-identifiers.api-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.asiointi.test-data :as test-data]
            [lupapalvelu.asiointi.test-utils :as asiointi-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest :refer [admin command query with-test-system]]
            [matcher-combinators.test :refer [match?]]))

(use-fixtures :each (fn [run-tests]
                      (with-test-system (-> {:fixture "pate-verdict"}
                                            test-system/with-remote-components
                                            test-system/with-mock-laundry)
                        (run-tests))))

(def applicant itest/pena)

(def test-PRT "123456789A")
(def test-PHT "123")

(def ^:private query-application (asiointi-test/query-application :building-permit))

(def ^:private assign-permanent-ids! (asiointi-test/assign-permanent-ids! :building-permit))

(defn- assign-permanent-ids-raw
  [{:keys [username password organization-id permit-id body]}]
  (-> (asiointi-test/submit! {:method   :post
                              :url      (format "/asiointi/api/%s/building-permit/%s/assign-permanent-ids"
                                                organization-id permit-id)
                              :username username
                              :password password
                              :body     body})
      (select-keys #{:status :body})))

(def ^:private pre-verdict-documents
  {"hakija-r"                           test-data/hakija
   "hankkeen-kuvaus"                    test-data/hankkeen-kuvaus
   "laajennettuRakennusjateselvitys-v2" test-data/laajennettuRakennusjateselvitys-v2
   "maksaja"                            test-data/maksaja
   "paasuunnittelija"                   test-data/paasuunnittelija
   "paatoksen-toimitus-rakval"          test-data/paatoksen-toimitus-rakval
   "rakennuspaikka"                     test-data/rakennuspaikka
   "suunnittelija"                      test-data/suunnittelija
   "uusiRakennuskohde"                  test-data/uusi-rakennuskohde-rakennus-full})

(deftest assign-permanent-identifiers-test
  (let [organization-id "753-R"]
    (testing "configure target organization"
      ;; Make sure target organization generates Ryhti-applications
      (is (:ok (command admin :set-organization-ryhti-data-2026-start-ts
                        {:organizationId           organization-id
                         :ryhti-data-2026-start-ts 0})))
      ;; Make sure the target organization uses fresh KuntaGML for schemas
      (is (:ok (command admin :set-organization-permit-type-krysp-version
                        {:org-id     organization-id
                         :permitType "R"
                         :version    "2.2.6"})))
      ;; Make sure the target organization is configured to use the Ryhti API
      (is (:ok (command admin :set-ryhti-api-configuration
                        {:organizationId organization-id
                         :ryhtiConfig    [{:municipality  "753"
                                           :client-id     "42"
                                           :client-secret "hunter2"}]}))))
    (let [application-id (:id (itest/create-application applicant))
          app            (let [resp (query applicant :application :id application-id)]
                           (is (:ok resp))
                           (:application resp))
          permit-id      (-> app :ryhti :permanentPermitIdentifier)
          context        {:username        "sipoo_asiointiapi"
                          :password        "sipoo"
                          :organization-id organization-id
                          :permit-id       permit-id}]
      (is (string? permit-id))

      (testing "fill-in documents before submitting"
        (let [documents          (:documents app)
              document->name     (fn [doc] (-> doc :schema-info :name))
              actual-by-name     (group-by document->name documents)
              expected-doc-names (->> pre-verdict-documents keys set)
              actual-doc-names   (->> actual-by-name keys set)]
          (is (= actual-doc-names expected-doc-names))
          (doseq [[doc-name doc-data] pre-verdict-documents]
            (let [{doc-id :id} (first (get actual-by-name doc-name))]
              (when (seq doc-data)
                (is (:ok (command applicant :update-doc
                                  {:id      application-id
                                   :doc     doc-id
                                   :updates (vec doc-data)}))))))))

      (testing "application should have a single building with a single apartment, no permanent IDs"
        (let [application  (query-application context)
              buildings    (asiointi-test/application->buildings application)
              with-ids     (assoc context
                                  :body
                                  {:buildings (-> buildings
                                                  (assoc-in [0 :permanentBuildingIdentifier] test-PRT)
                                                  (assoc-in [0 :apartments 0 :apartmentIdentifier] test-PHT))})
              buildingKey  (get-in buildings [0 :buildingKey])
              apartmentKey (get-in buildings [0 :apartments 0 :apartmentKey])]
          (is (match? [{:apartments  [{:apartmentKey asiointi-test/uuid-string?}]
                        :buildingKey asiointi-test/uuid-string?}]
                      buildings))

          (testing "invalid assignments"
            (testing "unknown buildingKey"
              (let [with-ids (assoc-in with-ids [:body :buildings 0 :buildingKey] "foobar")]
                (is (= {:status 400
                        :body   {:errors [;; the implementation also notices that foobar
                                          ;; is not the correct parent building for the appartment
                                          {:detail  "Apartment assigned to the wrong building"
                                           :pointer (str "/apartmentKey/" apartmentKey)}
                                          {:detail  "Assign request references an unknown field!"
                                           :pointer "/buildingKey/foobar"}]
                                 :status 400
                                 :title  "Bad request"}}
                       (assign-permanent-ids-raw with-ids)))))
            (testing "unknown apartmentKey"
              (let [with-ids (assoc-in with-ids [:body :buildings 0 :apartments 0 :apartmentKey] "foobar")]
                (is (= {:status 400
                        :body   {:errors [{:pointer "/apartmentKey/foobar"
                                           :detail  "Assign request references an unknown field!"}]
                                 :title  "Bad request"
                                 :status 400}}
                       (assign-permanent-ids-raw with-ids))))))

          (testing "assign permanent IDs"
            (is (nil? (assign-permanent-ids! with-ids)))

            (testing "assigning IDs is idempotent"
              (is (nil? (assign-permanent-ids! with-ids)))))

          (testing "but trying to overwrite with different values fails"
            (let [with-ids (assoc-in with-ids [:body :buildings 0 :permanentBuildingIdentifier] "foobar")]
              (is (= {:status 400
                      :body   {:errors [{:pointer (str "/buildingKey/" buildingKey)
                                         :detail  "Cannot overwrite an existing permanent ID!"}]
                               :title  "Bad request"
                               :status 400}}
                     (assign-permanent-ids-raw with-ids))))
            (let [with-ids (assoc-in with-ids [:body :buildings 0 :apartments 0 :apartmentIdentifier] "foobar")]
              (is (= {:status 400
                      :body   {:errors [{:pointer (str "/apartmentKey/" apartmentKey)
                                         :detail  "Cannot overwrite an existing permanent ID!"}]
                               :title  "Bad request"
                               :status 400}}
                     (assign-permanent-ids-raw with-ids))))))))))
