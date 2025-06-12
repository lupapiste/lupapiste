(ns lupapalvelu.admin.import-export.verdict-template-settings-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [admin command local-fixture-factory local-raw query sipoo]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :as pate-util]
            [lupapalvelu.pate.schemas :as pate-schemas]
            [muuntaja.core :as m]
            [sade.core :refer [now]]
            [schema.core :as sc]))

(use-fixtures :each (local-fixture-factory))

(defn- get-settings-from-mongo!
  [org-id]
  (->> (mongo/by-id :organizations org-id [:verdict-settings])
       :verdict-settings))

(def valid-settings! (sc/validator pate-schemas/PateSavedSettings))

(deftest import-export-verdict-template-settings-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "verdict-template-settings"}]

    (testing "single org choice"
      (is (= {:ok true :organizations {"092-YA" {:fi "Vantaa - Yleiset alueet"
                                                 :sv "Vanda - Yleiset alueet"
                                                 :en "Vantaa - Yleiset alueet"}}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (testing "set some default template settings for Sipoo"
      (pate-util/toggle-pate "753-R" true)
      (pate-util/set-default-r-verdict-template-settings sipoo "753-R")
      (pate-util/init-and-publish-verdict-template sipoo "753-R" :r
                                                   :language "fi"
                                                   :giver "viranhaltija"
                                                   :verdict-code "ehdollinen"
                                                   :paatosteksti "Verdict text."))

    (testing "multiple org choices"
      (is (= {:ok true :organizations {"092-YA" {:fi "Vantaa - Yleiset alueet"
                                                 :sv "Vanda - Yleiset alueet"
                                                 :en "Vantaa - Yleiset alueet"}
                                       "753-R"  {:fi "Sipoon rakennusvalvonta"
                                                 :sv "Sipoon rakennusvalvonta"
                                                 :en "Sipoon rakennusvalvonta"}}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (let [fetch-items-args {:target "verdict-template-settings"
                            :org-id "753-R"}]
      (testing "get item(s) from sipoo"
        (let [{:keys [ok items]} (query admin :import-export-fetch-items fetch-items-args)
              [_ value]          (first items)
              [id]               (keys items)]
          (is ok)
          (is (= {"r" (:r (get-settings-from-mongo! "753-R"))}
                 items))
          (is (valid-settings! value))

          (testing "export item(s) from sipoo"
            (let [export-args {:target   "verdict-template-settings"
                               :org-id   "753-R"
                               :item-ids id}]

              (testing "an empty export is technically ok"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "091-R"))
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_verdict-template-settings_091-R.json\""
                                  Content-Disposition))
                  (is (= {:target :verdict-template-settings
                          :org-id "091-R"
                          :env    "local"
                          :items  {}}
                         edn-body))))

              (testing "single item"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items export-args)
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)
                      db-settings                       (get-settings-from-mongo! "753-R")]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_verdict-template-settings_753-R.json\""
                                  Content-Disposition))
                  (is (= {:target :verdict-template-settings
                          :org-id "753-R"
                          :env    "local"
                          :items  {id (:r db-settings)}}
                         edn-body))))

              (testing "multiple items"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "092-YA" :item-ids "ya,contract"))
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)
                      db-settings                       (get-settings-from-mongo! "092-YA")]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_verdict-template-settings_092-YA.json\""
                                  Content-Disposition))
                  (is (= {:target :verdict-template-settings
                          :org-id "092-YA"
                          :env    "local"
                          :items  {"ya"       (:ya db-settings)
                                   "contract" (:contract db-settings)}}
                         edn-body))))))

          (testing "import"
            (let [import-args   {:org-id   "753-R"
                                 :filename "some-file.json"
                                 :document {:target "verdict-template-settings"
                                            :org-id "123-R"
                                            :env    "whatever"
                                            :items  {}}}
                  mock-settings {:modified (now)
                                 :draft    {}}]
              (testing "invalid import args"
                (is (= {:ok        false
                        :text      "error.invalid-categories"
                        :text-args #{"foobar"}}
                       (command admin :import-export-import-items (-> import-args
                                                                      (assoc-in [:document :items] {"foobar" mock-settings}))))
                    "Unknown categories are rejected")
                (is (= {:ok        false
                        :text      "error.invalid-categories"
                        :text-args #{"ya"}}
                       (command admin :import-export-import-items (-> import-args
                                                                      (assoc-in [:document :items] {"ya" mock-settings}))))
                    "Unsuitable (YA category for R org) categories are rejected")
                (is (= {:ok        false
                        :text      "error.import.settings-in-use"
                        :text-args #{"r"}}
                       (command admin :import-export-import-items (-> import-args
                                                                      (assoc-in [:document :items] {"r" mock-settings}))))
                    "Cannot overwrite existing settings, unless replace? is set")
                (is (= {:ok   false
                        :text "error.import.templates-in-use"}
                       (command admin :import-export-import-items (-> import-args
                                                                      (assoc :replace? true)
                                                                      (assoc-in [:document :items] {"tj" mock-settings}))))
                    "Cannot overwrite existing settings if corresponding templates are in use, even if replace? is set"))

              (testing "happy path imports"
                (testing "an empty import is technically ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items import-args))))
                (testing "merging new settings is ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items (-> import-args
                                                                        (assoc-in [:document :items] {"tj" mock-settings})))))
                  (is (= #{:r :tj}
                         (set (keys (get-settings-from-mongo! "753-R"))))))
                (testing "can replace if all used categories are replaced"
                  (is (= {:ok true}
                         (command admin :import-export-import-items (-> import-args
                                                                        (assoc :replace? true)
                                                                        (assoc-in [:document :items] {"r"  mock-settings
                                                                                                      "tj" mock-settings})))))
                  (is (= #{:r :tj}
                         (set (keys (get-settings-from-mongo! "753-R"))))))))))))))
