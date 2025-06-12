(ns lupapalvelu.ui.docgen.field.link-permit-selector-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.field.link-permit-selector :as selector]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(use-fixtures :once test-utils/js-services-fixture)

(def doc {:id     "doc-1"
          :schema {:info {}
                   :body [{:name           "sijoitusLuvanTunniste"
                           :size           :l
                           :type           :linkPermitSelector
                           :operationsPath ["yleisten-alueiden-luvat" "sijoituslupa"]}]}
          :data   {:sijoitusLuvanTunniste "foobar"}})

(def app (assoc test-utils/app :documents [doc]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest link-permit-selector-empty-test
  (rf-test/run-test-sync
    (let [op-path           ["yleisten-alueiden-luvat" "sijoituslupa"]
          {:keys [expect-action
                  handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)
      (rf/reg-fx :action/query handler)

      (expect-action {:name    :all-operations-in
                      :params  {:id   app-id
                                :path "yleisten-alueiden-luvat.sijoituslupa"}
                      :success [::selector/all-operations-in.success app-id op-path]
                      :resp    {:ok         true
                                :operations ["ya-sijoituslupa-maalampoputkien-sijoittaminen"]}})

      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (is (= #{"ya-sijoituslupa-maalampoputkien-sijoittaminen"}
             (<sub [::selector/operations-for app-id op-path])))

      (testing "no link permit -> no status"
        (is (= {}
               (<sub [::selector/status app-id op-path])))))))

(deftest link-permit-selector-single-linked-test
  (rf-test/run-test-sync
    (let [app               (assoc app
                                   :linkPermitData
                                   [{:id            "LP-1"
                                     :type          "lupapistetunnus"
                                     :operation     "ya-sijoituslupa-maalampoputkien-sijoittaminen"
                                     :permitSubtype ""}
                                    ;; another link permit, but wrong operation -> ignored
                                    {:id            "LP-WRONG-TYPE"
                                     :type          "lupapistetunnus"
                                     :operation     "ya-some-random-stuff"
                                     :permitSubtype ""}])
          op-path           ["yleisten-alueiden-luvat" "sijoituslupa"]
          {:keys [expect-action
                  handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)
      (rf/reg-fx :action/query handler)

      (expect-action {:name    :all-operations-in
                      :params  {:id   app-id
                                :path "yleisten-alueiden-luvat.sijoituslupa"}
                      :success [::selector/all-operations-in.success app-id op-path]
                      :resp    {:ok         true
                                :operations ["ya-sijoituslupa-maalampoputkien-sijoittaminen"]}})

      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (is (= #{"ya-sijoituslupa-maalampoputkien-sijoittaminen"}
             (<sub [::selector/operations-for app-id op-path])))

      (testing "single link permit -> use as value and mark as read only"
        (is (= {:value      "LP-1"
                :read-only? true}
               (<sub [::selector/status app-id op-path])))))))

(deftest link-permit-selector-multiple-linked-test
  (rf-test/run-test-sync
    (let [app               (assoc app
                                   :linkPermitData
                                   [{:id            "LP-1"
                                     :type          "lupapistetunnus"
                                     :operation     "ya-sijoituslupa-maalampoputkien-sijoittaminen"
                                     :permitSubtype ""}
                                    {:id            "LP-2"
                                     :type          "lupapistetunnus"
                                     :operation     "ya-sijoituslupa-maalampoputkien-sijoittaminen"
                                     :permitSubtype ""}])
          op-path           ["yleisten-alueiden-luvat" "sijoituslupa"]
          {:keys [expect-action
                  handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)
      (rf/reg-fx :action/query handler)

      (expect-action {:name    :all-operations-in
                      :params  {:id   app-id
                                :path "yleisten-alueiden-luvat.sijoituslupa"}
                      :success [::selector/all-operations-in.success app-id op-path]
                      :resp    {:ok         true
                                :operations ["ya-sijoituslupa-maalampoputkien-sijoittaminen"]}})

      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (is (= #{"ya-sijoituslupa-maalampoputkien-sijoittaminen"}
             (<sub [::selector/operations-for app-id op-path])))

      (testing "multiple link permits -> use first, show warning"
        (is (= {:value                   "LP-1"
                :read-only?              true
                :validation-result-level :warn}
               (<sub [::selector/status app-id op-path])))))))
