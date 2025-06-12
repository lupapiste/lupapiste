(ns lupapalvelu.pate.kuntagml-226-i-test
  (:require [clojure.test :refer :all]
            [clojure.walk :as walk]
            [lupapalvelu.fixture.pate-verdict]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.rakennuslupa-canonical-util :as canon-util]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env])
  (:import [clojure.lang BigInt]))

(set! *warn-on-reflection* true)

(def sijoittamislupas
  {:v2                (assoc canon-util/application-sijoittamislupa-v2
                             :id "LP-753-2013-sijoittamislupa-v2-00001")
   :v3                (assoc canon-util/application-sijoittamislupa-v3
                             :id "LP-753-2013-sijoittamislupa-v3-00002")
   :puhtaan-siirtyman (assoc canon-util/application-puhtaan-siirtyman-sijoittamislupa
                             :id "LP-753-2023-puhtaan-siirtyman-sijoittamislupa-00003")})

(use-fixtures :once
  (local-fixture-factory (-> {:fixture "pate-verdict"}
                             test-system/with-mock-laundry))
  (fn [f]
    (mongo/update-by-id :organizations "753-R"
                        {$set {:scope.0.pate.sftp                true
                               :use-attachment-links-integration true}})
    ;; Insert three test applications with unique ids
    (doseq [application (->> sijoittamislupas
                             vals
                             (map (fn [app]
                                    (walk/postwalk (fn [v]
                                                     ;; Mongo cannot handle BigInts.
                                                     (if (instance? BigInt v)
                                                       (.longValue ^BigInt v)
                                                       v))
                                                   (assoc app
                                                          :bulletinOpDescription "hello"
                                                          :attachments [])))))]
      (mongo/insert :applications application)
      (mongo/insert :submitted-applications application))
    (env/with-feature-value :ignore-kuntagml-publish-date true
      (f))))

(def kuntagml-fail? (partial expected-failure? :error.kuntagml-226-required))

(defn application-authed? [apikey action app-id]
  (-> (query apikey :allowed-actions :id app-id)
      :actions action :ok))

(defn kuntagml-226-required? [apikey app-id]
  (application-authed? apikey :kuntagml-226-required app-id))

(defn pate-kuntagml-226-required? [apikey verdict-id app-id]
  (-> (query apikey :allowed-actions-for-category :id app-id
             :category "pate-verdicts")
      :actionsById (get verdict-id)
      :pate-kuntagml-226-required :ok))

(defn poll-verdict-pdf? [app-id verdict-id]
  (boolean (reduce (fn [_ _]
                     (if (mongo/any? :applications
                                     {:_id                app-id
                                      :attachments.source {:type :verdicts
                                                           :id   verdict-id}})
                       (reduced true)
                       (Thread/sleep 2000)))
                   false (range 20))))

(defn- kuntagml-test-for-label [label app-id]
  ;; Set 2.2.4 version for Sipoo R
  (is (ok? (command admin :set-organization-permit-type-krysp-version
                    :org-id "753-R" :permitType "R"
                    :version "2.2.4")))
  (mongo/update-by-id :applications app-id {$unset {:transfers true}})

  (testing (str "KuntaGML-226 requirement flow for " label)
    (testing "Sijoittamislupa cannot be approved"
      (is (kuntagml-fail? (command sonja :approve-application
                                   :id app-id :lang "fi")))

      (testing "Pseudo query is authed for Sonja (approver) but not for others."
        (is (true? (kuntagml-226-required? sonja app-id)))
        (is (false? (kuntagml-226-required? pena app-id)))
        ;; This test also originally checked Kosti & Luukas, but they
        ;; are not even part of the fixture.
        ;; (is (false? (kuntagml-226-required? kosti app-id)))
        ;; (is (false? (kuntagml-226-required? luukas app-id)))
        (is (false? (kuntagml-226-required? ronja app-id)))))

    (let [{vid :verdict-id} (command sonja :new-pate-verdict-draft
                                     :id app-id
                                     :template-id "5a7aff3e5266a1d9c1581956")]
      (testing "Regular filled verdict cannot be published"
        (local-fill-verdict sonja app-id vid
                            :verdict-code "myonnetty"
                            :verdict-date (date/timestamp (date/today))
                            :automatic-verdict-dates true
                            :giver "Decider"
                            :giver-first-name "Gi"
                            :giver-last-name "Ver")
        (is (kuntagml-fail? (command sonja :publish-pate-verdict
                                     :id app-id :verdict-id vid)))
        (is (kuntagml-fail? (command sonja :scheduled-verdict-publish
                                     :id app-id :verdict-id vid)))

        (testing "Verdict pseudo query is authed"
          (is (true? (pate-kuntagml-226-required? sonja vid app-id)))))

      (testing "Raise the Sipoo KuntaGML version to 2.2.6"
        (is (ok? (command admin :set-organization-permit-type-krysp-version
                          :org-id "753-R" :permitType "R"
                          :version "2.2.6")))
        (testing "Pseudo queries are no longer authed"
          (is (false? (kuntagml-226-required? sonja app-id)))
          (is (false? (pate-kuntagml-226-required? sonja vid app-id))))

        (testing "Approve application is authed"
          (is (true? (application-authed? sonja :approve-application app-id))))

        (testing "Verdict can be published"
          (is (ok? (command sonja :publish-pate-verdict
                            :id app-id :verdict-id vid)))

          (is (true? (poll-verdict-pdf? app-id vid))))

        (testing "Revert KuntaGML version and check approval after verdict."
          (is (ok? (command admin :set-organization-permit-type-krysp-version
                            :org-id "753-R" :permitType "R"
                            :version "2.2.4")))
          (mongo/update-by-id :applications app-id {$unset {:transfers true}})
          (is (kuntagml-fail? (command sonja :approve-application-after-verdict
                                       :id app-id :lang "fi")))
          (is (true? (kuntagml-226-required? sonja app-id)))
          (testing "Increase version again and approve"
            (is (ok? (command admin :set-organization-permit-type-krysp-version
                              :org-id "753-R" :permitType "R"
                              :version "2.2.6")))
            (is (false? (kuntagml-226-required? sonja app-id)))
            (is (ok? (command sonja :approve-application-after-verdict :id app-id
                              :lang "fi")))))))))

(deftest kuntagml226-requirement-test-v2
  (kuntagml-test-for-label "sijoittamislupa-v2" (-> sijoittamislupas :v2 :id)))

(deftest kuntagml226-requirement-test-v3
  (kuntagml-test-for-label "sijoittamislupa-v3" (-> sijoittamislupas :v3 :id)))

(deftest kuntagml226-requirement-test-puhtaan-siirtyman-sijoittamislupa
  (kuntagml-test-for-label "puhtaan-siirtyman-sijoittamislupa" (-> sijoittamislupas :puhtaan-siirtyman :id)))
