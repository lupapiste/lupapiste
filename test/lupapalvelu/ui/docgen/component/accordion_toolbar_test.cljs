(ns lupapalvelu.ui.docgen.component.accordion-toolbar-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.component.accordion-toolbar :as toolbar]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

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
                    :body [{:name "työnjohtaja"}]}]}
   :data   {:kuntaRoolikoodi {:value "työnjohtaja"}
            :henkilotiedot   {:etunimi  {:value "Erkki"}
                              :sukunimi {:value "Esimerkki"}}}})

(def doc-suunnittelija
  {:id     "doc-suunnittelija"
   :schema {:info {:name             "suunnittelija"
                   :accordion-fields [{:type   "text"
                                       :format "- %s %s"
                                       :paths  [["henkilotiedot" "etunimi"]
                                                ["henkilotiedot" "sukunimi"]]}
                                      {:type   "text"
                                       :format "- %s"
                                       :paths  [["kuntaRoolikoodi"]]}
                                      {:type   "text"
                                       :format "(%s)"
                                       :paths  [["suunnittelutehtavanVaativuusluokka"]]}
                                      {:type   "text"
                                       :format "- %s"
                                       :paths  [["muuSuunnittelijaRooli"]]}]}
            :body [{:name      "kuntaRoolikoodi"
                    :type      "select"
                    :other-key "muuSuunnittelijaRooli"
                    :body      [{:name    "LVI-suunnittelija"
                                 :i18nkey "foobar.LVI-suunnittelija"}]}
                   {:name      "suunnittelutehtavanVaativuusluokka"
                    :type      "select"
                    :other-key "muuSuunnittelijaRooli"
                    :body      [{:name "A"}
                                {:name "B"}
                                {:name "C"}]}]}
   :data   {:kuntaRoolikoodi                    {:value "other"}
            :henkilotiedot                      {:etunimi  {:value "Sirpa"}
                                                 :sukunimi {:value "Suunnittelija"}}
            :suunnittelutehtavanVaativuusluokka {:value "A"}
            :muuSuunnittelijaRooli              {:value "harjoittelija"}}})

(def doc-tyoaika
  {:id     "doc-tyoaika"
   :schema {:info {:name             "tyoaika"
                   :accordion-fields [{:type   :workPeriod
                                       :paths  [["tyoaika-alkaa-ms"]
                                                ["tyoaika-paattyy-ms"]]
                                       :format "%s \u2013 %s"}]}
            :body []}
   :data   {:tyoaika-alkaa-ms   {:value 1757278800000}
            :tyoaika-paattyy-ms {:value 1761861600000}}})

(def doc-tyoaika-date
  {:id     "doc-tyoaika-date"
   :schema {:info {:name             "tyo-aika-for-jatkoaika"
                   :accordion-fields [{:type   :date
                                       :paths  [["tyoaika-alkaa-pvm"]
                                                ["tyoaika-paattyy-pvm"]]
                                       :format "%s \u2013 %s"}]}
            :body []}
   :data   {:tyoaika-alkaa-pvm   {:value "02.09.2025"}
            :tyoaika-paattyy-pvm {:value "15.10.2025"}}})

(def doc-hakija
  {:id     "doc-hakija"
   :schema {:info {:name             "hakija"
                   :accordion-fields [{:type   :selected
                                       :paths  [["henkilo" "henkilotiedot" "etunimi"]
                                                ["henkilo" "henkilotiedot" "sukunimi"]
                                                ["yritys" "yritysnimi"]]
                                       :format "- %s %s"}]}
            :body []}
   :data   {:henkilo   {:henkilotiedot {:etunimi  {:value "Heikki"}
                                        :sukunimi {:value "Hakija"}}}
            :yritys    {:yritysnimi {:value "Testi Oy Ab"}}
            :_selected {:value "yritys"}}})

(def app (assoc test-utils/app :documents [doc-tj
                                           doc-suunnittelija
                                           doc-tyoaika
                                           doc-tyoaika-date
                                           doc-hakija]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest accordion-header-test
  (rf-test/run-test-sync
    (let [{:keys [expect-action handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      ;; Note that translated values show up as plain strings

      (testing "Document with a single text accordion field"
        (is (= "- \"tyonjohtaja-v3.kuntaRoolikoodi.työnjohtaja\" Erkki Esimerkki"
               (<sub [::toolbar/header-description "doc-tj"]))))

      (testing "Document with multiple text accordion fields"
        (let [doc-id "doc-suunnittelija"]
          (is (= "- Sirpa Suunnittelija - \"select-other\" (\"suunnittelija.suunnittelutehtavanVaativuusluokka.A\") - harjoittelija"
                 (<sub [::toolbar/header-description doc-id])))
          (testing "choose another select option"
            (let [on-change! (docgen-common/make-on-change-immediate {:app-id app-id
                                                                      :doc-id doc-id
                                                                      :path   [:kuntaRoolikoodi]})]
              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["kuntaRoolikoodi" "LVI-suunnittelija"]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:kuntaRoolikoodi]]
                                                                         :values      ["LVI-suunnittelija"]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:kuntaRoolikoodi]]
                                                                       :values      ["LVI-suunnittelija"]}]
                              :resp    {:ok      true
                                        :results []}})
              (on-change! "LVI-suunnittelija")

              ;; Note that we still incorrectly show "harjoittelija"
              ;; But that is how the original docgen worked too.
              (is (= "- Sirpa Suunnittelija - \"foobar.LVI-suunnittelija\" (\"suunnittelija.suunnittelutehtavanVaativuusluokka.A\") - harjoittelija"
                     (<sub [::toolbar/header-description doc-id])))))))

      (testing "Document with `:workPeriod` field"
        (is (= "08.09.2025 – 31.10.2025"
               (<sub [::toolbar/header-description "doc-tyoaika"]))))

      (testing "Document with `:date` field"
        (is (= "02.09.2025 – 15.10.2025"
               (<sub [::toolbar/header-description "doc-tyoaika-date"]))))

      (testing "Document with a select accordion field"
        (let [doc-id "doc-hakija"]
          (is (= "- Testi Oy Ab "
                 (<sub [::toolbar/header-description doc-id])))

          (testing "switch from company to individual"
            (let [on-change! (docgen-common/make-on-change-immediate {:app-id app-id
                                                                      :doc-id doc-id
                                                                      :path   [:_selected]})]
              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["_selected" "henkilo"]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:_selected]]
                                                                         :values      ["henkilo"]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:_selected]]
                                                                       :values      ["henkilo"]}]
                              :resp    {:ok      true
                                        :results []}})
              (on-change! "henkilo")

              (is (= "- Heikki Hakija"
                     (<sub [::toolbar/header-description doc-id]))))))))))
