(ns lupapalvelu.admin.import-export.phrase-category-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [admin command err jarvenpaa local-fixture-factory local-raw pena query
                                            sipoo sonja]]
            [lupapalvelu.mongo :as mongo]
            [muuntaja.core :as m]))

(use-fixtures :each (local-fixture-factory))

(def sipoo-phrase-1 {:fi "Fraasikategoria Sipoo suomeksi"
                     :sv "Fraasikategoria Sipoo ruotsiksi"
                     ;; It's ok for phrase categories to have empty translations
                     :en ""})

(def sipoo-phrase-2 {:fi "Fraasikategoria #2 Sipoo suomeksi"
                     :sv "Fraasikategoria #2 Sipoo ruotsiksi"
                     :en "Fraasikategoria #2 Sipoo englanniksi"})

(defn- get-phrase-categories-from-mongo!
  [org-id]
  (->> (mongo/by-id :organizations org-id [:custom-phrase-categories])
       :custom-phrase-categories))

(deftest import-export-phrase-category-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "phrase-category"}]
    (testing "invalid fetch organization args"
      (doseq [user [pena sonja sipoo]]
        (is ((err :error.unauthorized)
             (query user :import-export-fetch-organizations fetch-orgs-args))))
      (is ((err :error.illegal-value:schema-validation)
           (query admin :import-export-fetch-organizations (assoc fetch-orgs-args :mode "foo"))))
      (is ((err :error.illegal-value:schema-validation)
           (query admin :import-export-fetch-organizations (assoc fetch-orgs-args :target "foo")))))

    (testing "no org choices returned as none have custom phrase categories"
      (is (= {:ok true :organizations {}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (testing "add some custom phrase categories"
      ;; Two for Sipoo
      (is (= {:ok true}
             (command sipoo :save-phrase-category {:organizationId "753-R"
                                                   :category       sipoo-phrase-1})))
      (is (= {:ok true}
             (command sipoo :save-phrase-category {:organizationId "753-R"
                                                   :category       sipoo-phrase-2})))
      ;; .. and one for järvenpää
      (is (= {:ok true}
             (command jarvenpaa :save-phrase-category {:organizationId "186-R"
                                                       :category       {:fi "Fraasikategoria Järvenpää suomeksi"
                                                                        :sv "Fraasikategoria Järvenpää ruotsiksi"
                                                                        :en "Fraasikategoria Järvenpää englanniksi"}}))))
    (testing "sipoo and järvenpää returned as choices"
      (is (= {:ok true :organizations {"186-R" {:fi "Järvenpään rakennusvalvonta"
                                                :sv "Järvenpään rakennusvalvonta"
                                                :en "Järvenpään rakennusvalvonta"}
                                       "753-R" {:fi "Sipoon rakennusvalvonta"
                                                :sv "Sipoon rakennusvalvonta"
                                                :en "Sipoon rakennusvalvonta"}}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (let [fetch-items-args {:target "phrase-category"
                            :org-id "753-R"}]
      (testing "invalid get items args"
        (is ((err :error.illegal-value:schema-validation)
             (query admin :import-export-fetch-items (assoc fetch-items-args :target "foo"))))
        (is ((err :error.organization-not-found)
             (query admin :import-export-fetch-items (assoc fetch-items-args :org-id "foo"))))
        (testing "shouldn't happen, but fetching items from a random org should return nothing"
          (is (= {:ok true :items {}}
                 (query admin :import-export-fetch-items (assoc fetch-items-args :org-id "091-R"))))))

      (testing "get item(s) from sipoo"
        (let [{:keys [ok items]} (query admin :import-export-fetch-items fetch-items-args)
              [_ value]          (first items) ; mongo ids are sequential, so we can assume the order
              [id id-2 :as ids]  (keys items)]
          (is ok)
          (is (= 2 (count items)))
          (is (every? string? ids))
          (is (= sipoo-phrase-1
                 value))

          (testing "export item(s) from sipoo"
            (let [export-args {:target   "phrase-category"
                               :org-id   "753-R"
                               :item-ids id}]

              (testing "invalid export args"
                (is ((err :error.illegal-value:schema-validation)
                     (local-raw admin :import-export-export-items (assoc export-args :target "foo"))))
                (is ((err :error.illegal-value:schema-validation)
                     (local-raw admin :import-export-export-items (assoc export-args :item-ids "foo")))))

              (testing "an empty export is technically ok"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "091-R"))
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_phrase-category_091-R.json\""
                                  Content-Disposition))
                  (is (= {:target :phrase-category
                          :org-id "091-R"
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
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_phrase-category_753-R.json\""
                                  Content-Disposition))
                  (is (= {:target :phrase-category
                          :org-id "753-R"
                          :env    "local"
                          :items  {id sipoo-phrase-1}}
                         edn-body))))

              (testing "multiple items"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :item-ids (str id "," id-2)))
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_phrase-category_753-R.json\""
                                  Content-Disposition))
                  (is (= {:target :phrase-category
                          :org-id "753-R"
                          :env    "local"
                          :items  {id   sipoo-phrase-1
                                   id-2 sipoo-phrase-2}}
                         edn-body))))))

          (testing "import"
            (testing "add some phrase(s)"
              (is (= {:ok true}
                     (command sipoo :upsert-phrase {:organizationId "753-R"
                                                    :category       id
                                                    :tag            "tag"
                                                    :phrase         "Fraasi #1"})))
              (is (= {:ok true}
                     (command sipoo :upsert-phrase {:organizationId "753-R"
                                                    :category       "sopimus"
                                                    :tag            "tag"
                                                    :phrase         "Fraasi #2"}))))

            (let [import-args {:org-id   "753-R"
                               :filename "some-file.json"
                               :document {:target "phrase-category"
                                          :org-id "123-R"
                                          :env    "whatever"
                                          :items  {}}}]
              (testing "invalid import args"
                (is ((err :error.illegal-value:schema-validation)
                     (command admin :import-export-import-items (assoc-in import-args [:document :target] "foo"))))
                ;; It seems schema.corce JSON coercion considers any string to be a valid sc/Bool so test with number
                (is ((err :error.illegal-value:schema-validation)
                     (command admin :import-export-import-items (assoc import-args :replace? 123))))
                (is ((err :error.organization-not-found)
                     (command admin :import-export-import-items (assoc import-args :org-id "foo"))))
                (is ((err :error.import.phrase-category-in-use)
                     (command admin :import-export-import-items (-> import-args
                                                                    ;; trying to replace with an empty import
                                                                    (assoc :replace? true))))
                    "cannot replace all as phrase category `id` is in use")
                (is ((err :error.import.phrase-category-in-use)
                     (command admin :import-export-import-items (-> import-args
                                                                    (assoc :replace? true)
                                                                    (assoc-in [:document :items] {(mongo/create-id) {:fi "A"
                                                                                                                     :sv "B"
                                                                                                                     :en "C"}}))))
                    "cannot replace all as phrase category `id` is in use"))

              (testing "happy path imports"
                (testing "an empty import is technically ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items import-args)))
                  (is (= {(keyword id)   sipoo-phrase-1
                          (keyword id-2) sipoo-phrase-2}
                         (get-phrase-categories-from-mongo! "753-R"))))
                (testing "merging a new custom phrase category is ok"
                  (let [new-id (mongo/create-id)]
                    (is (= {:ok true}
                           (command admin :import-export-import-items (-> import-args
                                                                          (assoc :replace? false)
                                                                          (assoc-in [:document :items] {new-id {:fi "A"
                                                                                                                :sv "B"
                                                                                                                :en "C"}})))))
                    (is (= {(keyword id)     sipoo-phrase-1
                            (keyword id-2)   sipoo-phrase-2
                            (keyword new-id) {:fi "A"
                                              :sv "B"
                                              :en "C"}}
                           (get-phrase-categories-from-mongo! "753-R")))))
                (testing "can replace all, even if one of the matching categories is in use"
                  (is (= {:ok true}
                         (command admin :import-export-import-items (-> import-args
                                                                        (assoc :replace? true)
                                                                        (assoc-in [:document :items] {id {:fi "A"
                                                                                                          :sv "B"
                                                                                                          :en "C"}})))))
                  (is (= {(keyword id) {:fi "A"
                                        :sv "B"
                                        :en "C"}}
                         (get-phrase-categories-from-mongo! "753-R"))))))))))))
