(ns lupapalvelu.admin.import-export.price-catalogue-itest
  "lupapalvelu.admin.import-export.phrase-category-itest contains more general assertions"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.invoices.test-util :as invoice-util]
            [lupapalvelu.itest-util :refer [admin command err jarvenpaa local-fixture-factory local-raw query]]
            [lupapalvelu.mongo :as mongo]
            [muuntaja.core :as m]))

(use-fixtures :each (local-fixture-factory))

(deftest import-export-price-catalogues-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "price-catalogue"}]

    (testing "no org choices returned as none have price-catalogues"
      (is (= {:ok true :organizations {}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (testing "enable invoicing and add some price catalogues"
      ;; Enable invoicing for a couple of orgs
      (invoice-util/toggle-invoicing-ok true "R" "753")
      (invoice-util/toggle-invoicing-ok true "R" "186")
      (invoice-util/toggle-invoicing-ok true "YM" "564")
      ;; Three price catalogues fro 753-R
      (let [id-0  (invoice-util/new-catalogue 10 false) ; draft
            id-1  (invoice-util/new-catalogue 1 true)
            id-2  (invoice-util/new-catalogue 5 true)
            ;; järvenpää only has draft(s)
            _     (invoice-util/new-catalogue 5 false {:org-id "186-R"
                                                       :user   jarvenpaa})
            cat-1 (mongo/by-id :price-catalogues id-1)
            cat-2 (mongo/by-id :price-catalogues id-2)]

        (testing "sipoo returned as the only choice"
          (is (= {:ok true :organizations {"753-R" {:fi "Sipoon rakennusvalvonta"
                                                    :sv "Sipoon rakennusvalvonta"
                                                    :en "Sipoon rakennusvalvonta"}}}
                 (query admin :import-export-fetch-organizations fetch-orgs-args))))

        (let [fetch-items-args {:target "price-catalogue"
                                :org-id "753-R"}]

          (testing "get item(s) from sipoo"
            (let [{:keys [ok items]} (query admin :import-export-fetch-items fetch-items-args)]
              (is ok)
              ;; only published price catalogues are returned
              (is (= {id-1 cat-1
                      id-2 cat-2}
                     items))

              (testing "export item(s) from sipoo"
                (let [export-args {:target   "price-catalogue"
                                   :org-id   "753-R"
                                   :item-ids id-1}]

                  (testing "an empty export is technically ok"
                    (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "091-R"))
                          {:strs [Content-Type
                                  Content-Disposition]}     headers
                          edn-body                          (m/decode-response-body resp)]
                      (is (= 200 status))
                      (is (= "application/transit+json" Content-Type))
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_price-catalogue_091-R.json\""
                                      Content-Disposition))
                      (is (= {:target :price-catalogue
                              :org-id "091-R"
                              :env    "local"
                              :items  {}}
                             edn-body))))

                  (testing "can't export a draft price catalogue even if we know the id"
                    (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :item-ids id-0))
                          {:strs [Content-Type
                                  Content-Disposition]}     headers
                          edn-body                          (m/decode-response-body resp)]
                      (is (= 200 status))
                      (is (= "application/transit+json" Content-Type))
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_price-catalogue_753-R.json\""
                                      Content-Disposition))
                      (is (= {:target :price-catalogue
                              :org-id "753-R"
                              :env    "local"
                              :items  {}}
                             edn-body))))

                  (testing "single item"
                    (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items export-args)
                          {:strs [Content-Type
                                  Content-Disposition]}     headers
                          edn-body                          (m/decode-response-body resp)]
                      (is (= 200 status))
                      (is (= "application/transit+json" Content-Type))
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_price-catalogue_753-R.json\""
                                      Content-Disposition))
                      (is (= {:target :price-catalogue
                              :org-id "753-R"
                              :env    "local"
                              :items  {id-1 cat-1}}
                             edn-body))))

                  (testing "multiple items"
                    (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :item-ids (str id-1 "," id-2)))
                          {:strs [Content-Type
                                  Content-Disposition]}     headers
                          edn-body                          (m/decode-response-body resp)]
                      (is (= 200 status))
                      (is (= "application/transit+json" Content-Type))
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_price-catalogue_753-R.json\""
                                      Content-Disposition))
                      (is (= {:target :price-catalogue
                              :org-id "753-R"
                              :env    "local"
                              :items  {id-1 cat-1
                                       id-2 cat-2}}
                             edn-body))))))

              (testing "import"
                (let [import-args {:org-id   "753-R"
                                   :filename "some-file"
                                   :document {:target "price-catalogue"
                                              :org-id "123-R"
                                              :env    "whatever"
                                              :items  {}}}]
                  (testing "invalid import args"
                    (testing "importing into an org with no invoicing"
                      (is ((err :error.organization-not-found)
                           (command admin :import-export-import-items (assoc import-args :org-id "091-R")))))

                    (testing "importing into an org with invoicing but no support for catalogues"
                      (is ((err :error.price-catalogue.unsupported-permit-type)
                           (command admin :import-export-import-items (assoc import-args :org-id "564-YMP")))))

                    (testing "importing with an incorrect type"
                      (is ((err :error.price-catalogue.incorrect-type)
                           (command admin :import-export-import-items (assoc-in import-args [:document :items] {id-1 (assoc cat-1 :type "YA")})))))

                    (testing "cannot replace all catalogues if one of them is published"
                      (is ((err :error.price-catalogue.cannot-replace-published)
                           (command admin :import-export-import-items (-> import-args
                                                                          (assoc :replace? true)
                                                                          (assoc-in [:document :items] {id-1 cat-1})))))))

                  (testing "happy path imports"
                    (testing "an empty import is technically ok"
                      (is (= {:ok true}
                             (command admin :import-export-import-items import-args))))

                    (let [new-id   (mongo/create-id)
                          new-name (str (random-uuid))
                          new-cat  {:meta            {:modified    1724838105310
                                                      :modified-by {:id        "50ac77ecc2e6c2ea6e73f83e"
                                                                    :username  "sipoo"
                                                                    :firstName "Simo"
                                                                    :lastName  "Suurvisiiri"
                                                                    :role      "authority"}}
                                    :valid-from      1724878800000
                                    :name            new-name
                                    :type            "R"
                                    :state           "published"
                                    :organization-id "123-R"
                                    :rows            []
                                    :id              new-id
                                    :valid-until     1724965199999}]

                      (testing "merging a new catalogue is ok even if target org has published catalogues"
                        (is (= {:ok true}
                               (command admin :import-export-import-items (-> import-args
                                                                              (assoc :replace? false)
                                                                              (assoc-in  [:document :items] {new-id new-cat})))))
                        (let [cat-from-db (mongo/select-one :price-catalogues {:name new-name})]
                          (is (= (-> new-cat
                                     ;; New id generated, valid-from/until removed
                                     (dissoc :id :valid-from :valid-until)
                                     ;; Assigned to a new org and marked as draft
                                     (assoc :organization-id "753-R"
                                            :state "draft"))
                                 (-> cat-from-db
                                     (dissoc :id))))))
                      (testing "can replace all if target or has no published catalogues"
                        (is (= {:ok true}
                               (command admin :import-export-import-items (-> import-args
                                                                              (assoc :replace? true :org-id "186-R")
                                                                              (assoc-in  [:document :items] {new-id new-cat})))))
                        (let [[cat-from-db :as all] (mongo/select :price-catalogues {:organization-id "186-R"})]
                          (is (= 1 (count all)))
                          (is (= (-> new-cat
                                     ;; New id generated, valid-from/until removed
                                     (dissoc :id :valid-from :valid-until)
                                     ;; Assigned to a new org and marked as draft
                                     (assoc :organization-id "186-R"
                                            :state "draft"))
                                 (-> cat-from-db
                                     (dissoc :id)))))))))))))))))
