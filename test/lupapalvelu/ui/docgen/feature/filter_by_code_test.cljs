(ns lupapalvelu.ui.docgen.feature.filter-by-code-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.filter-by-code :as filter-by-code]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

(def vt-schema {:name   "vastattavatTyotehtavat"
                :type   :group
                :listen ["filterByCode"]
                :body   [{:name "ivLaitoksenAsennustyo" :codes ["ivt"] :type :checkbox}
                         {:name "sisapuolinenKvvTyo" :codes ["kvv"] :type :checkbox}
                         {:name "rakennuksenMuutosJaKorjaustyo" :codes ["vtj"] :type :checkbox}
                         {:name "uudisrakennustyoMaanrakennustoineen" :codes ["vtj" "kvv"] :type :checkbox}
                         {:name "someRandomChild" :type :string}]})

(def doc-tj
  {:id     "doc-tj"
   :schema {:info {:name             "tyonjohtaja-v3"
                   :accordion-fields [{:type   "text"
                                       :format "- %s %s %s"
                                       :paths  [["kuntaRoolikoodi"]
                                                ["henkilotiedot" "etunimi"]
                                                ["henkilotiedot" "sukunimi"]]}]}
            :body [{:name "kuntaRoolikoodi"
                    :type "select"
                    :emit ["filterByCode"]
                    :body [{:name "työnjohtaja" :code "vtj"}
                           {:name "KVV-työnjohtaja" :code "kvv"}]}
                   vt-schema]}
   :data   {:kuntaRoolikoodi {:value "työnjohtaja"}}})

(def app (assoc test-utils/app :documents [doc-tj]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest filter-by-code-test
  (rf-test/run-test-sync
    (let [{:keys [expect-action handler]} (test-utils/make-mock-command)
          doc-id                          "doc-tj"]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (is (= {:listeners #{[:vastattavatTyotehtavat]}
              :emitter   {:path  [:kuntaRoolikoodi]
                          :codes {"työnjohtaja"     "vtj"
                                  "KVV-työnjohtaja" "kvv"}}}
             (<sub [::filter-by-code/doc-emitter-and-listeners doc-id])))

      (testing "Check current code"
        (is (= "vtj"
               (<sub [::filter-by-code/listen-for-code doc-id [:vastattavatTyotehtavat]]))))

      (testing "schema body filtering by code"
        (is (= [{:name "rakennuksenMuutosJaKorjaustyo" :codes ["vtj"] :type :checkbox}
                {:name "uudisrakennustyoMaanrakennustoineen" :codes ["vtj" "kvv"] :type :checkbox}
                {:name "someRandomChild" :type :string}]
               (filter-by-code/filter-body {:schema vt-schema
                                            :path   [:vastattavatTyotehtavat]
                                            :doc-id doc-id}))))
      (testing "changing the select value should change the code"
        (let [on-change! (docgen-common/make-on-change-immediate {:app-id app-id
                                                                  :doc-id doc-id
                                                                  :path   [:kuntaRoolikoodi]})]
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["kuntaRoolikoodi" "KVV-työnjohtaja"]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:kuntaRoolikoodi]]
                                                                     :values      ["KVV-työnjohtaja"]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:kuntaRoolikoodi]]
                                                                   :values      ["KVV-työnjohtaja"]}]
                          :resp    {:ok      true
                                    :results []}})
          (on-change! "KVV-työnjohtaja")

          (testing "Check code was changed"
            (is (= "kvv"
                   (<sub [::filter-by-code/listen-for-code doc-id [:vastattavatTyotehtavat]]))))

          (testing "schema body filtering by updated code"
            (is (= [{:name "sisapuolinenKvvTyo" :codes ["kvv"] :type :checkbox}
                    {:name "uudisrakennustyoMaanrakennustoineen" :codes ["vtj" "kvv"] :type :checkbox}
                    {:name "someRandomChild" :type :string}]
                   (filter-by-code/filter-body {:schema vt-schema
                                                :path   [:vastattavatTyotehtavat]
                                                :doc-id doc-id})))))))))
