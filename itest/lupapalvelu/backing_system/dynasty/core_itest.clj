(ns lupapalvelu.backing-system.dynasty.core-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.backing-system.dynasty.core :as dynasty]
            [lupapalvelu.fixture.core :as fix]
            [lupapalvelu.itest-util :as itest :refer [command create-and-submit-application kuopio-property-id
                                                      local-fixture-factory ok? query]]
            [lupapalvelu.mongo :as mongo]))

(def ^:private fixture :dynasty-ymp-fixture)

(use-fixtures :each (local-fixture-factory :db-name itest/test-db-name :fixture (name fixture)))

(def applicant (fix/apikey-for fixture "pena"))
(def auth-admin (fix/apikey-for fixture "kuopio-ymp"))

(def dynasty-options {:applicant-role            "APP_ROLE"
                      :auth-host                 "AUTH-HOST"
                      :case-base-id              "ID"
                      :client                    "CLIENT"
                      :client-secret             "SECRET"
                      :document-phase            "PHASE"
                      :document-type             "DOC-TYPE"
                      :handler-id                "HANDLER"
                      :host                      "HOST"
                      :organization              "ORG"
                      :organization-hierarchy-id "OHID"
                      :organization-name         "ORG-NAME"
                      :owner                     "OWNER"})

(defn dynasty-enabled? [org-id app-id]
  (let [org (mongo/by-id :organizations org-id)
        app (mongo/by-id :applications app-id)]
    (dynasty/enabled-for-organization? org app)))

(deftest enabled-for-organization-test
  (let [org-id                    "297-YMP"
        ;; YM permit
        {app-id-ym :id}           (create-and-submit-application applicant
                                                                 :propertyId kuopio-property-id
                                                                 :operation "jatteen-keraystoiminta")
        ;; MAL permit
        {app-id-mal :id}          (create-and-submit-application applicant
                                                                 :propertyId kuopio-property-id
                                                                 :operation "maa-aineslupa")
        ;; VVVL permit
        {app-id-vvvl :id}         (create-and-submit-application applicant
                                                                 :propertyId kuopio-property-id
                                                                 :operation "vvvl-viemarista")

        ;; 297 specifically does not support vvvl-hulevesiviemarista
        {app-id-vvvl-ignored :id} (create-and-submit-application applicant
                                                                 :propertyId kuopio-property-id
                                                                 :operation "vvvl-hulevesiviemarista")]

    (testing "no integration defined"
      (is (not (dynasty-enabled? org-id app-id-ym)))
      (is (not (dynasty-enabled? org-id app-id-mal)))
      (is (not (dynasty-enabled? org-id app-id-vvvl)))
      (is (not (dynasty-enabled? org-id app-id-vvvl-ignored))))

    (testing "add integration config"
      (testing "at first only dynasty available"
        (let [{:keys [data] :as resp} (query auth-admin :integration-config :organizationId org-id)]
          (is (ok? resp))
          (is (= {:configs   []
                  :available {:dynasty #{"MAL" "VVVL" "YI" "YL" "YM"}}}
                 data))))
      (testing "add dynasty integration (except for MAL)"
        (let [resp (command auth-admin :save-integration-config {:organizationId org-id
                                                                 :config         {:integration-type "dynasty"
                                                                                  :targets          ["VVVL" "YI" "YL" "YM"]
                                                                                  :enabled?         true
                                                                                  :options          dynasty-options}})]
          (is (ok? resp))))

      (testing "adding an overlapping config is an error"
        (let [resp (command auth-admin :save-integration-config {:organizationId org-id
                                                                 :config         {:integration-type "dynasty"
                                                                                  :targets          ["VVVL" "YI" "YL" "YM"]
                                                                                  :enabled?         true
                                                                                  :options          dynasty-options}})]
          (is (= {:ok false :text "error.dynasty-target-in-use"} resp))))

      (testing "MAL still available, dynasty integration added for other targets"
        (let [{{:keys [available configs]} :data :as resp} (query auth-admin :integration-config :organizationId org-id)
              config                                       (first configs)]
          (is (ok? resp))
          (is (= {:dynasty #{"MAL"}} available))
          (is (= 1 (count configs)))
          (is (= {:targets          #{"YM" "YL" "VVVL" "YI"}
                  :options          (assoc dynasty-options :client-secret "---dynasty-secret---")
                  :enabled?         true
                  :integration-type :dynasty}
                 (dissoc config :id :created-at))))))

    (is (dynasty-enabled? org-id app-id-ym))
    (is (not (dynasty-enabled? org-id app-id-mal))
        "integration config did not include MAL")
    (is (dynasty-enabled? org-id app-id-vvvl))
    (is (not (dynasty-enabled? org-id app-id-vvvl-ignored))
        "although VVVL is enabled, the specific operation is not")

    (testing "disable integration"
      (let [{{:keys [configs]} :data :as resp} (query auth-admin :integration-config :organizationId org-id)
            _                                  (is (ok? resp))
            _                                  (is (= 1 (count configs)))
            id                                 (->> configs first :id)
            resp                               (command auth-admin :save-integration-config {:organizationId org-id
                                                                                             :config         {:id               id
                                                                                                              :integration-type "dynasty"
                                                                                                              :targets          ["VVVL" "YI" "YL" "YM"]
                                                                                                              :enabled?         false
                                                                                                              :options          dynasty-options}})]
        (is (ok? resp))))

    (testing "integration defined but disabled"
      (is (not (dynasty-enabled? org-id app-id-ym)))
      (is (not (dynasty-enabled? org-id app-id-mal)))
      (is (not (dynasty-enabled? org-id app-id-vvvl)))
      (is (not (dynasty-enabled? org-id app-id-vvvl-ignored))))))
