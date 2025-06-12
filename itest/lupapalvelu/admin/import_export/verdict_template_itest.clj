(ns lupapalvelu.admin.import-export.verdict-template-itest
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [admin command local-fixture-factory local-raw query sipoo]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :as pate-util]
            [lupapalvelu.pate.schemas :as pate-schemas]
            [lupapalvelu.pate.verdict-template :as vt]
            [muuntaja.core :as m]
            [sade.core :refer [now]]
            [sade.strings :as ss]))

(use-fixtures :each (local-fixture-factory))

(defn- get-templates-from-mongo!
  [org-id & kvs]
  (apply vt/verdict-templates org-id :raw? true kvs))

(def valid-template! pate-schemas/valid-pate-saved-template!)

(defn imported-template-name [org-id old-templates]
  (let [old-ids     (set (map :id old-templates))
        current-ids (set (map :id (get-templates-from-mongo! org-id)))
        new-ids     (set/difference current-ids old-ids)
        _           (assert (= 1 (count new-ids)) "Only one new template")]
    (:name (vt/verdict-template org-id (first new-ids)))))

(deftest import-export-verdict-template-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "verdict-template"}]

    (testing "single org choice"
      (is (= {:ok true :organizations {"092-YA" {:fi "Vantaa - Yleiset alueet"
                                                 :sv "Vanda - Yleiset alueet"
                                                 :en "Vantaa - Yleiset alueet"}}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (testing "set some default template settings and draft template(s) for Sipoo"
      (pate-util/toggle-pate "753-R" true)
      (pate-util/set-default-r-verdict-template-settings sipoo "753-R")
      (pate-util/init-verdict-template sipoo "753-R" :r))

    (testing "multiple org choices"
      (is (= {:ok true :organizations {"092-YA" {:fi "Vantaa - Yleiset alueet"
                                                 :sv "Vanda - Yleiset alueet"
                                                 :en "Vantaa - Yleiset alueet"}
                                       "753-R"  {:fi "Sipoon rakennusvalvonta"
                                                 :sv "Sipoon rakennusvalvonta"
                                                 :en "Sipoon rakennusvalvonta"}}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (let [fetch-items-args {:target "verdict-template"
                            :org-id "753-R"}]
      (testing "get item(s) from sipoo"
        (let [{:keys [ok items]} (query admin :import-export-fetch-items fetch-items-args)
              [_ value]          (first items)
              [id]               (keys items)
              [sipoo-db-template
               :as db-templates] (get-templates-from-mongo! "753-R")]
          (is (= 1 (count db-templates)))
          (is ok)
          (is (= {(:id sipoo-db-template) sipoo-db-template}
                 items))
          (is (valid-template! value))

          (testing "export item(s) from sipoo"
            (let [export-args {:target   "verdict-template"
                               :org-id   "753-R"
                               :item-ids id}]

              (testing "an empty export is technically ok"
                (let [{:keys [status headers]
                       :as   resp}                  (local-raw admin :import-export-export-items
                                                               (assoc export-args :org-id "091-R"))
                      {:strs [Content-Type
                              Content-Disposition]} headers
                      edn-body                      (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_verdict-template_091-R.json\""
                                  Content-Disposition))
                  (is (= {:target :verdict-template
                          :org-id "091-R"
                          :env    "local"
                          :items  {}}
                         edn-body))))

              (testing "single item"
                (let [{:keys [status headers]
                       :as   resp}                  (local-raw admin :import-export-export-items export-args)
                      {:strs [Content-Type
                              Content-Disposition]} headers
                      edn-body                      (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_verdict-template_753-R.json\""
                                  Content-Disposition))
                  (is (= {:target :verdict-template
                          :org-id "753-R"
                          :env    "local"
                          :items  {id sipoo-db-template}}
                         edn-body))))

              (testing "multiple items"
                (let [vantaa-db-templates           (get-templates-from-mongo! "092-YA")
                      ids                           (ss/join "," (map :id vantaa-db-templates))
                      {:keys [status headers]
                       :as   resp}                  (local-raw admin :import-export-export-items
                                                               (assoc export-args :org-id "092-YA"
                                                                      :item-ids ids))
                      {:strs [Content-Type
                              Content-Disposition]} headers
                      edn-body                      (m/decode-response-body resp)]
                  (is (= 2 (count vantaa-db-templates)))
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_verdict-template_092-YA.json\""
                                  Content-Disposition))
                  (is (= {:target :verdict-template
                          :org-id "092-YA"
                          :env    "local"
                          :items  (->> vantaa-db-templates
                                       (map (juxt :id identity))
                                       (into {}))}
                         edn-body))))))

          (testing "import"
            (let [vantaa-db-templates (get-templates-from-mongo! "092-YA")
                  sipoo-db-templates  (get-templates-from-mongo! "753-R")
                  import-args         {:org-id   "753-R"
                                       :filename "some-file.json"
                                       :document {:target "verdict-template"
                                                  :org-id "123-R"
                                                  :env    "whatever"
                                                  :items  {}}}
                  ts                  (now)
                  mock-id             (mongo/create-id)
                  mock-template       {:id           mock-id
                                       :organization "123-R"
                                       :category     "r"
                                       :name         {:_value    "mock-template"
                                                      :_user     "testing"
                                                      :_modified ts}
                                       :deleted      {:_value    false
                                                      :_user     "testing"
                                                      :_modified ts}
                                       :created      ts
                                       :modified     ts
                                       :draft        "some-data"}]
              (is (valid-template! mock-template))
              (testing "invalid import args"
                (is (= {:ok        false
                        :text      "error.invalid-categories"
                        :text-args #{"ya"}}
                       (command admin :import-export-import-items
                                (-> import-args
                                    (assoc-in [:document :items]
                                              {mock-id (assoc mock-template :category "ya")}))))
                    "Unsuitable (YA category for R org) categories are rejected")
                (is (= {:ok        false
                        :text      "error.import.replacing-published-templates"
                        :text-args #{"Päätöspohja" "Sopimuspohja"}}
                       (command admin :import-export-import-items
                                (-> import-args
                                    (assoc :org-id "092-YA" :replace? true)
                                    (assoc-in [:document :items]
                                              {mock-id (assoc mock-template :category "ya")}))))
                    "Cannot replace existing templates if they are published"))

              (testing "happy path imports"
                (testing "an empty import is technically ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items import-args))))
                (testing "adding new templates is ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items
                                  (-> import-args
                                      (assoc-in [:document :items] {mock-id mock-template})))))
                  (is (= "mock-template" (imported-template-name "753-R" sipoo-db-templates))))
                (testing "adding new templates is ok, even if there are also existing published templates"
                  (is (= {:ok true}
                         (command admin :import-export-import-items
                                  (-> import-args
                                      (assoc :org-id "092-YA")
                                      (assoc-in [:document :items] {mock-id (assoc mock-template
                                                                                   :category "ya")})))))
                  (is (= "mock-template" (imported-template-name "092-YA" vantaa-db-templates))))
                (testing "can replace all if no existing templates are published"
                  (is (= {:ok true}
                         (command admin :import-export-import-items
                                  (-> import-args
                                      (assoc :replace? true)
                                      (assoc-in [:document :items] {mock-id mock-template})))))
                  (is (= "mock-template" (imported-template-name "753-R" #{}))))
                ;; User might want to empty the existing templates first, perhaps by
                ;; manually editing the .edn file
                (testing "an empty import with replace? clears entries"
                  (is (= {:ok true}
                         (command admin :import-export-import-items
                                  (assoc import-args :replace? true))))
                  (is (empty? (get-templates-from-mongo! "753-R"))))))))))))
