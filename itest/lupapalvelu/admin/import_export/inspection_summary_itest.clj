(ns lupapalvelu.admin.import-export.inspection-summary-itest
  "lupapalvelu.admin.import-export.phrase-category-itest contains more general assertions"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [admin command jarvenpaa local-fixture-factory local-raw ok? query sipoo]]
            [lupapalvelu.mongo :as mongo]
            [muuntaja.core :as m]))

(use-fixtures :each (local-fixture-factory))

(defn- get-templates-from-mongo!
  [org-id]
  (->> (mongo/by-id :organizations org-id [:inspection-summary.templates])
       :inspection-summary
       :templates))

(defn- without-modified
  [template-map]
  (->> template-map
       (map (fn [[id template]]
              [id (dissoc template :modified)]))
       (into {})))

(deftest import-export-inspection-summary-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "inspection-summary"}]

    (testing "no org choices returned as none have inspection summary templates"
      (is (= {:ok true :organizations {}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (testing "enable feature"
      (is (= {:ok true}
             (command sipoo :set-organization-inspection-summaries {:organizationId "753-R"
                                                                    :enabled        true})))
      (is (= {:ok true}
             (command jarvenpaa :set-organization-inspection-summaries {:organizationId "186-R"
                                                                        :enabled        true}))))

    (testing "add some custom phrase categories"
      (let [;; Two for sipoo
            {sipoo-template-id :id :as sipoo-resp}          (command sipoo :create-inspection-summary-template
                                                                     {:organizationId "753-R"
                                                                      :name           "template-sipoo"
                                                                      :templateText   "a\nb\nc"})
            {sipoo-template-id-2 :id :as sipoo-resp-2}      (command sipoo :create-inspection-summary-template
                                                                     {:organizationId "753-R"
                                                                      :name           "template-sipoo-2"
                                                                      :templateText   "Foo\nBar\nBaz"})
            ;; .. and one for järvenpää
            {jarvenpaa-template-id :id :as jarvenpaa-resp } (command jarvenpaa :create-inspection-summary-template
                                                                     {:organizationId "186-R"
                                                                      :name           "template-jarvenpaa"
                                                                      :templateText   "x\ny\nz"})]
        (is (ok? sipoo-resp))
        (is (ok? sipoo-resp-2))
        (is (ok? jarvenpaa-resp))
        (is sipoo-template-id)
        (is sipoo-template-id-2)
        (is jarvenpaa-template-id)

        (testing "add operation to jarvenpaa template"
          (let [op-resp (command jarvenpaa :modify-inspection-summary-template {:organizationId     "186-R"
                                                                                :templateId         jarvenpaa-template-id
                                                                                :templateOperations ["aita"]})]
            (is (ok? op-resp))))

        (testing "still no org choices returned as none have phrases"
          (is (= {:ok true :organizations {"186-R" {:fi "Järvenpään rakennusvalvonta"
                                                    :sv "Järvenpään rakennusvalvonta"
                                                    :en "Järvenpään rakennusvalvonta"}
                                           "753-R" {:fi "Sipoon rakennusvalvonta"
                                                    :sv "Sipoon rakennusvalvonta"
                                                    :en "Sipoon rakennusvalvonta"}}}
                 (query admin :import-export-fetch-organizations fetch-orgs-args))))

        (let [fetch-items-args {:target "inspection-summary"
                                :org-id "753-R"}]

          (testing "get item(s) from sipoo"
            (let [{:keys [items] :as resp} (query admin :import-export-fetch-items fetch-items-args)]
              (is (ok? resp))
              (is (= {sipoo-template-id   {:id    sipoo-template-id
                                           :name  "template-sipoo"
                                           :items ["a" "b" "c"]}
                      sipoo-template-id-2 {:id    sipoo-template-id-2
                                           :name  "template-sipoo-2"
                                           :items ["Foo" "Bar" "Baz"]}}
                     (without-modified items)))

              (testing "export item(s) from sipoo"
                (let [export-args {:target   "inspection-summary"
                                   :org-id   "753-R"
                                   :item-ids sipoo-template-id}]

                  (testing "an empty export is technically ok"
                    (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "091-R"))
                          {:strs [Content-Type
                                  Content-Disposition]}     headers
                          edn-body                          (m/decode-response-body resp)]
                      (is (= 200 status))
                      (is (= "application/transit+json" Content-Type))
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_inspection-summary_091-R.json\""
                                      Content-Disposition))
                      (is (= {:target :inspection-summary
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
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_inspection-summary_753-R.json\""
                                      Content-Disposition))
                      (is (= {:target :inspection-summary
                              :org-id "753-R"
                              :env    "local"
                              :items  {sipoo-template-id {:id    sipoo-template-id
                                                          :name  "template-sipoo"
                                                          :items ["a" "b" "c"]}}}
                             (update edn-body :items without-modified)))))

                  (testing "multiple items"
                    (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :item-ids (str sipoo-template-id "," sipoo-template-id-2)))
                          {:strs [Content-Type
                                  Content-Disposition]}     headers
                          edn-body                          (m/decode-response-body resp)]
                      (is (= 200 status))
                      (is (= "application/transit+json" Content-Type))
                      (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_inspection-summary_753-R.json\""
                                      Content-Disposition))
                      (is (= {:target :inspection-summary
                              :org-id "753-R"
                              :env    "local"
                              :items  {sipoo-template-id   {:id    sipoo-template-id
                                                            :name  "template-sipoo"
                                                            :items ["a" "b" "c"]}
                                       sipoo-template-id-2 {:id    sipoo-template-id-2
                                                            :name  "template-sipoo-2"
                                                            :items ["Foo" "Bar" "Baz"]}}}
                             (update edn-body :items without-modified)))))))

              (testing "import"
                (let [import-args {:org-id   "753-R"
                                   :filename "some-file.json"
                                   :document {:target "inspection-summary"
                                              :org-id "123-R"
                                              :env    "whatever"
                                              :items  {}}}]
                  (testing "invalid import args"
                    (testing "importing template with identical id"
                      (is (= {:ok        false
                              :text      "error.import.duplicate-ids"
                              :text-args #{jarvenpaa-template-id}}
                             (command admin :import-export-import-items (-> import-args
                                                                            (assoc :org-id "186-R")
                                                                            (assoc-in [:document :items] {jarvenpaa-template-id {:id       jarvenpaa-template-id
                                                                                                                                 :name     "template-jarvenpaa"
                                                                                                                                 :modified 123
                                                                                                                                 :items    ["foo"]}}))))
                          "cannot import as `id` matches existing template"))
                    (testing "replacing template which is mapped to an operation"
                      (is (= {:ok   false
                              :text "error.import.inspection-templates-in-use"}
                             (command admin :import-export-import-items (-> import-args
                                                                            (assoc :org-id "186-R" :replace? true)
                                                                            (assoc-in [:document :items] {jarvenpaa-template-id {:id       jarvenpaa-template-id
                                                                                                                                 :name     "template-jarvenpaa"
                                                                                                                                 :modified 123
                                                                                                                                 :items    ["foo"]}}))))
                          "cannot replace as existing template already assigned to an operation")))

                  (testing "happy path imports"
                    (testing "an empty import is technically ok"
                      (is (= {:ok true}
                             (command admin :import-export-import-items import-args)))
                      (is (= [{:id    sipoo-template-id
                               :name  "template-sipoo"
                               :items ["a" "b" "c"]}
                              {:id    sipoo-template-id-2
                               :name  "template-sipoo-2"
                               :items ["Foo" "Bar" "Baz"]}]
                             (->> (get-templates-from-mongo! "753-R")
                                  (mapv (fn [template] (dissoc template :modified)))))))
                    (testing "merging a new template is ok"
                      (let [new-id       (mongo/create-id)
                            new-template {:id       new-id
                                          :name     "new-template"
                                          :items    ["aaa" "bbb"]
                                          :modified 2345}]
                        (is (= {:ok true}
                               (command admin :import-export-import-items (-> import-args
                                                                              (assoc :replace? false)
                                                                              (assoc-in  [:document :items] {new-id new-template})))))
                        (is (= [{:id    sipoo-template-id
                                 :name  "template-sipoo"
                                 :items ["a" "b" "c"]}
                                {:id    sipoo-template-id-2
                                 :name  "template-sipoo-2"
                                 :items ["Foo" "Bar" "Baz"]}
                                {:id    new-id
                                 :name  "new-template"
                                 :items ["aaa" "bbb"]}]
                               (->> (get-templates-from-mongo! "753-R")
                                    (mapv (fn [template] (dissoc template :modified))))))))
                    (testing "can replace all"
                      (let [new-id       (mongo/create-id)
                            new-template {:id       new-id
                                          :name     "yet-another-template"
                                          :items    ["replacing"]
                                          :modified 52345}]
                        (is (= {:ok true}
                               (command admin :import-export-import-items (-> import-args
                                                                              (assoc :replace? true)
                                                                              (assoc-in  [:document :items] {new-id new-template})))))
                        (is (= [{:id       new-id
                                 :name     "yet-another-template"
                                 :items    ["replacing"]
                                 :modified 52345}]
                               (get-templates-from-mongo! "753-R")))))))))))))))
