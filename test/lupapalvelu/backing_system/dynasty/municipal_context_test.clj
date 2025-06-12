(ns lupapalvelu.backing-system.dynasty.municipal-context-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.backing-system.dynasty.municipal-context :as ctx]
            [lupapalvelu.permit :as permit]
            [sade.date :as date]))

(deftest supported-permit-types-test
  (testing "Tampere"
    (is (empty? (ctx/supported-permit-types {:id    "837-YMP"
                                             :scope [{:permitType "YM"}
                                                     {:permitType "YI"}]}))))
  (testing "Kuopio"
    (is (empty? (ctx/supported-permit-types {:id "297-YMP"})))
    (is (= #{"YM" "YI"}
           (ctx/supported-permit-types {:id    "297-YMP"
                                        :scope [{:permitType "YM"}
                                                {:permitType "YI"}
                                                {:permitType "R"}]})))
    (is (= #{"YM" "YI" "MAL" "YL" "VVVL"}
           (ctx/supported-permit-types {:id    "297-YMP"
                                        :scope (map #(hash-map :permitType %)
                                                    (keys (permit/permit-types)))})))))

(deftest operations-for-test
  (testing "Tampere"
    (is (empty? (ctx/operations-for {:organization "837-YMP"}))))
  (testing "Kuopio"
    (is (= 28 (count (ctx/operations-for {:organization "297-YMP"}))))))

(def ^:private test-permit-config
  (-> {::ctx/default {::ctx/attachment-tos-title-secret "default Hakemuksen liite"
                      ::ctx/document-title              "default Otsikko"
                      ::ctx/document-tos-title          "default Hakemus liitteineen"
                      ::ctx/document-type               "default Hakemus"
                      ::ctx/tos-class                   "default 10.00.00"}
       permit/MAL    {::ctx/default  {::ctx/attachment-tos-title-secret "MAL Hakemuksen liite"
                                      ::ctx/document-title              "MAL Otsikko"
                                      ::ctx/document-tos-title          "MAL Hakemus liitteineen"
                                      ::ctx/document-type               "MAL Ilmoitus"
                                      ::ctx/tos-class                   "MAL 11.02.00"}
                      ;; Maa-ainesten ottaminen (Maa-aineslupa, MAL 4 §)
                      :maa-aineslupa {::ctx/document-title "Maa-aineslupahakemus"}}}
      ctx/valid-config!))

(def app {:created          (date/timestamp "1.1.2025")
          :organization     "123-YMP"
          :permitType       "YM"
          :primaryOperation {:name "foobar"}})

(def integration {:integration-type :dynasty
                  :targets          #{}
                  :enabled?         true
                  :options          {:case-base-id              "CBID"
                                     :document-phase            "Phase 1"
                                     :document-subphase         "Phase 2"
                                     :handler-id                "Handler ID"
                                     :organization-hierarchy-id "HID"
                                     :organization-name         "Org"
                                     :owner                     "Omistaja"}})

(deftest municipal-context-test
  (testing "Tampere"
    (let [app (assoc app :organization "837-YMP")]
      (is (thrown?
            RuntimeException
            (ctx/build-municipal-context integration app :fi (date/timestamp "1.1.2025"))))))
  (testing "Kuopio"
    (let [app (assoc app :organization "297-YMP")]
      (is (ctx/build-municipal-context integration app :fi (date/timestamp "1.1.2025"))))))

(deftest common-document-fn-test
  (testing "default config"
    (let [f (ctx/common-document-fn test-permit-config)]
      (is (= {:title                   "default Otsikko"
              :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector [{:title "Phase 1"}
                                                                                                {:title "Phase 2"}]}}
                                        :tos-title      "default Hakemus liitteineen"}
              :meta-json               {:document-type             "default Hakemus"
                                        :handler-id                "Handler ID"
                                        :organisation-hierarchy-id "HID"
                                        :received-date             "2025-01-01"}}
             (f app integration)))))
  (testing "no subphase"
    (let [integration (assoc-in integration [:options :document-subphase] nil)
          f           (ctx/common-document-fn test-permit-config)]
      (is (= {:title                   "default Otsikko"
              :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector [{:title "Phase 1"}]}}
                                        :tos-title      "default Hakemus liitteineen"}
              :meta-json               {:document-type             "default Hakemus"
                                        :handler-id                "Handler ID"
                                        :organisation-hierarchy-id "HID"
                                        :received-date             "2025-01-01"}}
             (f app integration)))))
  (testing "empty subphase"
    (let [integration (assoc-in integration [:options :document-subphase] "")
          f           (ctx/common-document-fn test-permit-config)]
      (is (= {:title                   "default Otsikko"
              :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector [{:title "Phase 1"}]}}
                                        :tos-title      "default Hakemus liitteineen"}
              :meta-json               {:document-type             "default Hakemus"
                                        :handler-id                "Handler ID"
                                        :organisation-hierarchy-id "HID"
                                        :received-date             "2025-01-01"}}
             (f app integration)))))
  (testing "specific permit"
    (let [app (assoc app :permitType "MAL")
          f   (ctx/common-document-fn test-permit-config)]
      (is (= {:title                   "MAL Otsikko"
              :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector [{:title "Phase 1"}
                                                                                                {:title "Phase 2"}]}}
                                        :tos-title      "MAL Hakemus liitteineen"}
              :meta-json               {:document-type             "MAL Ilmoitus"
                                        :handler-id                "Handler ID"
                                        :organisation-hierarchy-id "HID"
                                        :received-date             "2025-01-01"}}
             (f app integration)))))
  (testing "specific operation"
    (let [app (-> app
                  (assoc :permitType "MAL")
                  (assoc-in [:primaryOperation :name] "maa-aineslupa"))
          f   (ctx/common-document-fn test-permit-config)]
      (is (= {:title                   "Maa-aineslupahakemus"
              :case-attachment-options {:phase-selector {:smart-attach-options {:path-selector [{:title "Phase 1"}
                                                                                                {:title "Phase 2"}]}}
                                        :tos-title      "MAL Hakemus liitteineen"}
              :meta-json               {:document-type             "MAL Ilmoitus"
                                        :handler-id                "Handler ID"
                                        :organisation-hierarchy-id "HID"
                                        :received-date             "2025-01-01"}}
             (f app integration))))))
