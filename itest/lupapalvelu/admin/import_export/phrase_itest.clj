(ns lupapalvelu.admin.import-export.phrase-itest
  "lupapalvelu.admin.import-export.phrase-category-itest contains more general assertions"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [admin command jarvenpaa local-fixture-factory local-raw query sipoo]]
            [lupapalvelu.mongo :as mongo]
            [muuntaja.core :as m]
            [sade.util :as util]))

(use-fixtures :each (local-fixture-factory))

(def sipoo-phrase-1 {:fi "Fraasikategoria Sipoo suomeksi"
                     :sv "Fraasikategoria Sipoo ruotsiksi"
                     :en "Fraasikategoria Sipoo englanniksi"})

(defn- get-phrase-categories-from-mongo!
  [org-id]
  (->> (mongo/by-id :organizations org-id [:custom-phrase-categories])
       :custom-phrase-categories))

(defn- get-phrases-from-mongo!
  [org-id]
  (->> (mongo/by-id :organizations org-id [:phrases])
       :phrases))

(deftest import-export-phrase-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "phrase"}]

    (testing "no org choices returned as none have phrases"
      (is (= {:ok true :organizations {}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (testing "add some custom phrase categories"
      ;; One for sipoo
      (is (= {:ok true}
             (command sipoo :save-phrase-category {:organizationId "753-R"
                                                   :category       sipoo-phrase-1})))
      ;; .. and one for järvenpää
      (is (= {:ok true}
             (command jarvenpaa :save-phrase-category {:organizationId "186-R"
                                                       :category       {:fi "Fraasikategoria Järvenpää suomeksi"
                                                                        :sv "Fraasikategoria Järvenpää ruotsiksi"
                                                                        :en "Fraasikategoria Järvenpää englanniksi"}}))))

    (testing "still no org choices returned as none have phrases"
      (is (= {:ok true :organizations {}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (let [categories  (get-phrase-categories-from-mongo! "753-R")
          category-id (name (first (keys categories)))
          phrase-1    {:category category-id
                       :tag      "tag"
                       :phrase   "Fraasi #1"}
          phrase-2    {:category "sopimus"
                       :tag      "tag"
                       :phrase   "Fraasi #2"}]

      (testing "add some phrase(s)"
        (is (= {:ok true}
               (command sipoo :upsert-phrase (assoc phrase-1 :organizationId "753-R"))))
        (is (= {:ok true}
               (command sipoo :upsert-phrase (assoc phrase-2 :organizationId "753-R")))))

      (testing "sipoo returned as a choice"
        (is (= {:ok true :organizations {"753-R" {:fi "Sipoon rakennusvalvonta"
                                                  :sv "Sipoon rakennusvalvonta"
                                                  :en "Sipoon rakennusvalvonta"}}}
               (query admin :import-export-fetch-organizations fetch-orgs-args))))

      (let [db-phrases       (get-phrases-from-mongo! "753-R")
            id-1             (->> db-phrases
                                  (util/find-by-key :phrase "Fraasi #1")
                                  :id)
            id-2             (->> db-phrases
                                  (util/find-by-key :phrase "Fraasi #2")
                                  :id)
            phrase-1         (assoc phrase-1 :id id-1)
            phrase-2         (assoc phrase-2 :id id-2)
            fetch-items-args {:target "phrase"
                              :org-id "753-R"}]

        (testing "get item(s) from sipoo"
          (let [{:keys [ok items]} (query admin :import-export-fetch-items fetch-items-args)]
            (is ok)
            (is (= {id-1 phrase-1
                    id-2 phrase-2}
                   items))

            (testing "export item(s) from sipoo"
              (let [export-args {:target   "phrase"
                                 :org-id   "753-R"
                                 :item-ids id-1}]

                (testing "an empty export is technically ok"
                  (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "091-R"))
                        {:strs [Content-Type
                                Content-Disposition]}     headers
                        edn-body                          (m/decode-response-body resp)]
                    (is (= 200 status))
                    (is (= "application/transit+json" Content-Type))
                    (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_phrase_091-R.json\""
                                    Content-Disposition))
                    (is (= {:target :phrase
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
                    (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_phrase_753-R.json\""
                                    Content-Disposition))
                    (is (= {:target :phrase
                            :org-id "753-R"
                            :env    "local"
                            :items  {id-1 phrase-1}}
                           edn-body))))

                (testing "multiple items"
                  (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :item-ids (str id-1 "," id-2)))
                        {:strs [Content-Type
                                Content-Disposition]}     headers
                        edn-body                          (m/decode-response-body resp)]
                    (is (= 200 status))
                    (is (= "application/transit+json" Content-Type))
                    (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_phrase_753-R.json\""
                                    Content-Disposition))
                    (is (= {:target :phrase
                            :org-id "753-R"
                            :env    "local"
                            :items  {id-1 phrase-1
                                     id-2 phrase-2}}
                           edn-body))))))

            (testing "import"
              (let [import-args {:org-id   "753-R"
                                 :filename "some-file.json"
                                 :document {:target "phrase"
                                            :org-id "123-R"
                                            :env    "whatever"
                                            :items  {}}}]
                (testing "invalid import args"
                  (let [unknown-category (mongo/create-id)
                        phrase           (assoc phrase-1 :category unknown-category)]
                    (is (= {:ok        false
                            :text      "error.invalid-categories"
                            :text-args #{(keyword unknown-category)}}
                           (command admin :import-export-import-items (-> import-args
                                                                          (assoc-in [:document :items] {id-1 phrase}))))
                        "cannot replace all as phrase category `id` is in use")))

                (testing "happy path imports"
                  (testing "an empty import is technically ok"
                    (is (= {:ok true}
                           (command admin :import-export-import-items import-args)))
                    (is (= [phrase-1 phrase-2]
                           (get-phrases-from-mongo! "753-R"))))
                  (testing "merging a new phrase is ok"
                    (let [new-id     (mongo/create-id)
                          new-phrase {:id       new-id
                                      :category "sopimus"
                                      :tag      "foo"
                                      :phrase   "Fraasi #3"}]
                      (is (= {:ok true}
                             (command admin :import-export-import-items (-> import-args
                                                                            (assoc :replace? false)
                                                                            (assoc-in  [:document :items] {new-id new-phrase})))))
                      (let [db-phrases (get-phrases-from-mongo! "753-R")]
                        (is (= 3 (count db-phrases)))
                        (is (not= [phrase-1 phrase-2 new-phrase] db-phrases))
                        ;; Phrase 3 gets a new id when it is imported
                        (is (= [phrase-1 phrase-2 (dissoc new-phrase :id)]
                               (->> db-phrases
                                    (mapv (fn [phrase]
                                            (if (= "Fraasi #3" (:phrase phrase))
                                              (dissoc phrase :id)
                                              phrase)))))))))
                  (testing "can replace all"
                    (let [new-id     (mongo/create-id)
                          new-phrase {:id       new-id
                                      :category "sopimus"
                                      :tag      "foo"
                                      :phrase   "Fraasi #4"}]
                      (is (= {:ok true}
                             (command admin :import-export-import-items (-> import-args
                                                                            (assoc :replace? true)
                                                                            (assoc-in  [:document :items] {new-id new-phrase})))))
                      (let [db-phrases (get-phrases-from-mongo! "753-R")]
                        (is (= 1 (count db-phrases)))
                        (is (not= [new-phrase] db-phrases))
                        ;; Phrase 4 gets a new id when it is imported
                        (is (= [(dissoc new-phrase :id)]
                               (->> db-phrases
                                    (mapv (fn [phrase] (dissoc phrase :id))))))))))))))))))
