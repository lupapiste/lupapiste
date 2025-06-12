(ns lupapalvelu.ui.docgen.field.docgen-huoneistot-table-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.huoneistot.action-cells :as actions]
            [lupapalvelu.ui.docgen.field.huoneistot.cells :as cells]
            [lupapalvelu.ui.docgen.field.huoneistot.cells-common :as cells-common]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [lupapalvelu.ui.docgen.field.huoneistot.highlight :as highlight]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.field.huoneistot.premise-upload :as upload]
            [lupapalvelu.ui.docgen.field.huoneistot.rows :as rows]
            [lupapalvelu.ui.docgen.field.huoneistot.table-actions :as table-actions]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]
            [sade.util :as util]))

(use-fixtures :once test-utils/js-services-fixture)

(def base-huoneisto-schema
  "Same as `lupapalvelu.document.schemas/huoneistotTable`, except no `muutostapa`."
  {:group-help               "huoneistot.groupHelpText"
   :approvable               true
   :i18nkey                  "huoneistot"
   :repeating-allow-empty    true
   :uicomponent              :docgenHuoneistot
   :validator                :huoneistot
   :name                     "huoneistot"
   :type                     :table
   :copybutton               true
   :repeating                true
   :allow-highlight-changes? true
   :allow-premise-upload?    false
   :body                     [{:name     "huoneistoTyyppi"
                               :type     :select
                               :sortBy   :displayname
                               :size     :s
                               :label    false
                               :i18nkey  "huoneistot.huoneistoTyyppi"
                               :required true
                               :body     [{:name "asuinhuoneisto"}
                                          {:name "toimitila"}
                                          {:name    "ei tiedossa"
                                           :i18nkey "ei-tiedossa"}]}
                              {:max-len   1
                               :i18nkey   "huoneistot.porras"
                               :transform :upper-case
                               :name      "porras"
                               :type      :string
                               :size      :t
                               :label     false
                               :case      :upper
                               :subtype   :letter}
                              {:max-len  3
                               :min      0
                               :i18nkey  "huoneistot.huoneistonumero"
                               :name     "huoneistonumero"
                               :type     :string
                               :size     :s
                               :label    false
                               :min-len  1
                               :required true
                               :subtype  :number}
                              {:max-len   1
                               :i18nkey   "huoneistot.jakokirjain"
                               :transform :lower-case
                               :name      "jakokirjain"
                               :type      :string
                               :size      :t
                               :label     false
                               :case      :lower
                               :subtype   :letter}
                              {:min      1
                               :i18nkey  "huoneistot.huoneluku"
                               :name     "huoneluku"
                               :type     :string
                               :size     :t
                               :max      99
                               :label    false
                               :required true
                               :subtype  :number}
                              {:name     "keittionTyyppi"
                               :type     :select
                               :sortBy   :displayname
                               :required true
                               :size     :s
                               :label    false
                               :i18nkey  "huoneistot.keittionTyyppi"
                               :body     [{:name "keittio"}
                                          {:name "keittokomero"}
                                          {:name "keittotila"}
                                          {:name "tupakeittio"}
                                          {:name    "ei tiedossa"
                                           :i18nkey "ei-tiedossa"}]}
                              {:min      1
                               :i18nkey  "huoneistot.huoneistoala"
                               :name     "huoneistoala"
                               :type     :string
                               :size     :s
                               :max      9999999
                               :label    false
                               :required true
                               :subtype  :decimal}
                              {:name    "WCKytkin"
                               :type    :checkbox
                               :label   false
                               :i18nkey "huoneistot.WCKytkin"}
                              {:name    "ammeTaiSuihkuKytkin"
                               :type    :checkbox
                               :label   false
                               :i18nkey "huoneistot.ammeTaiSuihkuKytkin"}
                              {:name    "saunaKytkin"
                               :type    :checkbox
                               :label   false
                               :i18nkey "huoneistot.saunaKytkin"}
                              {:name    "parvekeTaiTerassiKytkin"
                               :type    :checkbox
                               :label   false
                               :i18nkey "huoneistot.parvekeTaiTerassiKytkin"}
                              {:name    "lamminvesiKytkin"
                               :type    :checkbox
                               :label   false
                               :i18nkey "huoneistot.lamminvesiKytkin"}
                              {:name     "pysyvaHuoneistotunnus"
                               :type     :string
                               :size     :s
                               :label    false
                               :i18nkey  "huoneistot.pysyvaHuoneistotunnus"
                               :readonly true}]})

(def huoneisto-schema-laajennos
  "Like `lupapalvelu.document.schemas/huoneistotTable`"
  (update base-huoneisto-schema :body conj {:name "muutostapa" :type  :select :sortBy  :displayname
                                            :size :s           :label false   :i18nkey "huoneistot.muutostapa"
                                            :body [{:name "poisto"}
                                                   {:name "lisäys" :i18nkey "huoneistot.muutostapa.lisays"}
                                                   {:name "muutos"}]}))

(def huoneisto-schema-new-building
  "Like `lupapalvelu.document.schemas/huoneistotTable-new-building`"
  (-> base-huoneisto-schema
      (assoc :allow-highlight-changes? false
             :allow-premise-upload? true)
      (update :body conj {:name    "muutostapa" :type  :select :sortBy  :displayname
                          :size    :s           :label false   :i18nkey "huoneistot.muutostapa"
                          :default "lisäys"
                          :hidden  true
                          :body    [{:name "poisto"}
                                    {:name "lisäys" :i18nkey "huoneistot.muutostapa.lisays"}
                                    {:name "muutos"}]})))

(def doc-uusi-rakennus
  {:id     "doc-uusi-rakennus"
   :schema {:info {:name "uusiRakennus"}
            :body [huoneisto-schema-new-building]}
   :data   {:huoneistot {:0 {:WCKytkin                {:value true}
                             :ammeTaiSuihkuKytkin     {:value false}
                             :huoneistoTyyppi         {:value "asuinhuoneisto"}
                             :huoneistoala            {:value "50"}
                             :huoneistonumero         {:value "001"}
                             :huoneluku               {:value "3"}
                             :jakokirjain             {:value "b"}
                             :keittionTyyppi          {:value "keittokomero"}
                             :lamminvesiKytkin        {:value true}
                             :muutostapa              {:value "lisäys"}
                             :parvekeTaiTerassiKytkin {:value false}
                             :porras                  {:value "A"}
                             :pysyvaHuoneistotunnus   {:value ""}
                             :saunaKytkin             {:value true}}}}})

(def doc-rakennuksen-laajentaminen
  {:id     "doc-rakennuksen-laajentaminen"
   :schema {:info {:name "rakennuksen-laajentaminen"}
            :body [huoneisto-schema-laajennos]}
   ;; NOTE: When a user creates a "laajentaminen" application and chooses
   ;; a building, the huoneistot table rows get populated. But the "muutostapa"
   ;; field is not generated. It is only after the user modifies the data
   ;; the "muutostapa" field is created.
   :data   {:huoneistot {:0 (-> {:WCKytkin                {:value true}
                                 :ammeTaiSuihkuKytkin     {:value false}
                                 :huoneistoTyyppi         {:value "asuinhuoneisto"}
                                 :huoneistoala            {:value "50"}
                                 :huoneistonumero         {:value "001"}
                                 :huoneluku               {:value "3"}
                                 :jakokirjain             {:value "b"}
                                 :keittionTyyppi          {:value "keittokomero"}
                                 :lamminvesiKytkin        {:value true}
                                 :parvekeTaiTerassiKytkin {:value false}
                                 :porras                  {:value "A"}
                                 :pysyvaHuoneistotunnus   {:value ""}
                                 :saunaKytkin             {:value true}}
                                (update-vals (fn [{:keys [value] :as data}]
                                               (assoc data :source "krysp" :sourceValue value))))}}})

(def doc-rakennuksen-laajentaminen-muutos
  {:id     "doc-rakennuksen-laajentaminen-muutos"
   :schema {:info {:name "rakennuksen-laajentaminen"}
            :body [huoneisto-schema-laajennos]}
   ;; Here we simulate a situation, where the original laajentaminen permit
   ;; had 4 rows and we are creating a muutoslupa for that permit.
   :data   {:huoneistot {;; Not modified in original permit
                         :0 (-> {:WCKytkin                {:value true}
                                 :ammeTaiSuihkuKytkin     {:value false}
                                 :huoneistoTyyppi         {:value "asuinhuoneisto"}
                                 :huoneistoala            {:value "50"}
                                 :huoneistonumero         {:value "001"}
                                 :huoneluku               {:value "3"}
                                 :jakokirjain             {:value "b"}
                                 :keittionTyyppi          {:value "keittokomero"}
                                 :lamminvesiKytkin        {:value true}
                                 :parvekeTaiTerassiKytkin {:value false}
                                 :porras                  {:value "A"}
                                 :pysyvaHuoneistotunnus   {:value ""}
                                 :saunaKytkin             {:value true}}
                                (update-vals (fn [{:keys [value] :as data}]
                                               (assoc data :source "krysp" :sourceValue value))))
                         ;; Modified in original permit
                         :1 (-> {:WCKytkin                {:value true}
                                 :ammeTaiSuihkuKytkin     {:value false}
                                 :huoneistoTyyppi         {:value "asuinhuoneisto"}
                                 :huoneistoala            {:value "50"}
                                 :huoneistonumero         {:value "002"}
                                 :huoneluku               {:value "3"}
                                 :jakokirjain             {:value "b"}
                                 :keittionTyyppi          {:value "keittokomero"}
                                 :lamminvesiKytkin        {:value true}
                                 :parvekeTaiTerassiKytkin {:value false}
                                 :porras                  {:value "A"}
                                 :pysyvaHuoneistotunnus   {:value ""}
                                 :saunaKytkin             {:value true}
                                 :muutostapa              {:value "muutos"}}
                                (update-vals (fn [{:keys [value] :as data}]
                                               (assoc data :source "krysp" :sourceValue value))))
                         ;; Removed in original permit
                         :2 (-> {:WCKytkin                {:value true}
                                 :ammeTaiSuihkuKytkin     {:value false}
                                 :huoneistoTyyppi         {:value "asuinhuoneisto"}
                                 :huoneistoala            {:value "50"}
                                 :huoneistonumero         {:value "002"}
                                 :huoneluku               {:value "3"}
                                 :jakokirjain             {:value "b"}
                                 :keittionTyyppi          {:value "keittokomero"}
                                 :lamminvesiKytkin        {:value true}
                                 :parvekeTaiTerassiKytkin {:value false}
                                 :porras                  {:value "A"}
                                 :pysyvaHuoneistotunnus   {:value ""}
                                 :saunaKytkin             {:value true}
                                 :muutostapa              {:value "poisto"}}
                                (update-vals (fn [{:keys [value] :as data}]
                                               (assoc data :source "krysp" :sourceValue value))))
                         ;; Added in original permit
                         :3 (-> {:WCKytkin                {:value true}
                                 :ammeTaiSuihkuKytkin     {:value false}
                                 :huoneistoTyyppi         {:value "asuinhuoneisto"}
                                 :huoneistoala            {:value "50"}
                                 :huoneistonumero         {:value "003"}
                                 :huoneluku               {:value "3"}
                                 :jakokirjain             {:value "b"}
                                 :keittionTyyppi          {:value "keittokomero"}
                                 :lamminvesiKytkin        {:value true}
                                 :parvekeTaiTerassiKytkin {:value false}
                                 :porras                  {:value "A"}
                                 :pysyvaHuoneistotunnus   {:value ""}
                                 :saunaKytkin             {:value true}
                                 :muutostapa              {:value "lisäys"}}
                                (update-vals (fn [{:keys [value] :as data}]
                                               (assoc data :source "krysp" :sourceValue value))))}}})

(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest huoneisto-table-uusi-rakennus-test
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-uusi-rakennus)
          {:keys [allow-highlight-changes?
                  allow-premise-upload?]} huoneisto-schema-new-building
          table-path                      [:huoneistot]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     (assoc test-utils/app :documents [doc-uusi-rakennus])
                                  :details details}])

      (testing "initial flags"
        (is (false? (<sub [::huoneistot-common/change-permit?])))
        (is (false? (<sub [::highlight/highlight-changes? doc-id allow-highlight-changes?])))
        (is (<sub [::upload/show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?])))

      (let [row-path (conj table-path 0)]
        (is (true? (<sub [::rows/row-visible? doc-id row-path])))
        (is (true? (<sub [::cells-common/row-is-new? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
        (is (true? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
        (is (nil? (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

        (is (= muutostapa/added
               (<sub [::muutostapa/current-muutostapa doc-id row-path])))
        (testing "interactive controls"
          (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
          (is (true? (<sub [::actions/show-remove-button? doc-id row-path])))
          (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
          (is (false? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

        (let [expected-cell-paths [[:huoneistot 0 :huoneistoTyyppi]
                                   [:huoneistot 0 :porras]
                                   [:huoneistot 0 :huoneistonumero]
                                   [:huoneistot 0 :jakokirjain]
                                   [:huoneistot 0 :huoneluku]
                                   [:huoneistot 0 :keittionTyyppi]
                                   [:huoneistot 0 :huoneistoala]
                                   [:huoneistot 0 :WCKytkin]
                                   [:huoneistot 0 :ammeTaiSuihkuKytkin]
                                   [:huoneistot 0 :saunaKytkin]
                                   [:huoneistot 0 :parvekeTaiTerassiKytkin]
                                   [:huoneistot 0 :lamminvesiKytkin]
                                   [:huoneistot 0 :pysyvaHuoneistotunnus]]]

          (is (= expected-cell-paths
                 (<sub [::cells/row-path->cell-paths doc-id row-path])))

          (testing "turn WCKytkin off"
            (let [cell-path  (conj row-path :WCKytkin)
                  on-change! (fn [value]
                               (>evt [::cells/change-immediate (util/mmap {:change-permit? false
                                                                           :cell-paths     expected-cell-paths}
                                                                          app-id doc-id cell-path value)]))]

              (is (true? (<sub [::value/current-field-value doc-id cell-path])))

              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["huoneistot.0.WCKytkin" false]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 0 :WCKytkin]]
                                                                         :values      [false]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:huoneistot 0 :WCKytkin]]
                                                                       :values      [false]}]
                              :resp    {:ok      true
                                        :results []}})

              (on-change! false)

              (is (false? (<sub [::value/current-field-value doc-id cell-path])))

              (is (true? (<sub [::rows/row-visible? doc-id row-path])))
              (is (true? (<sub [::cells-common/row-is-new? doc-id row-path])))
              (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
              (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
              (is (true? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
              (is (nil? (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))))

          (testing "copy last row"
            (let [next-idx 1]
              (is (= next-idx (<sub [::docs/next-child-id doc-id table-path])))
              (let [copy-row! (table-actions/make-copy-row-callback
                                (util/mmap app-id doc-id table-path next-idx))]

                (expect-action {:name    :update-doc
                                :params  {:id         app-id
                                          :doc        doc-id
                                          :updates    [["huoneistot.1.parvekeTaiTerassiKytkin" false]
                                                       ["huoneistot.1.huoneluku" "3"]
                                                       ["huoneistot.1.jakokirjain" "b"]
                                                       ["huoneistot.1.porras" "A"]
                                                       ["huoneistot.1.huoneistoTyyppi" "asuinhuoneisto"]
                                                       ["huoneistot.1.saunaKytkin" true]
                                                       ["huoneistot.1.muutostapa" "lisäys"]
                                                       ["huoneistot.1.keittionTyyppi" "keittokomero"]
                                                       ["huoneistot.1.lamminvesiKytkin" true]
                                                       ["huoneistot.1.huoneistonumero" "001"]
                                                       ["huoneistot.1.huoneistoala" "50"]
                                                       ["huoneistot.1.WCKytkin" false]
                                                       ["huoneistot.1.ammeTaiSuihkuKytkin" false]]
                                          :collection :documents}
                                :success [::docgen-common/on-save-success {:app-id      app-id
                                                                           :doc-id      doc-id
                                                                           :field-paths [[:huoneistot 1 :parvekeTaiTerassiKytkin]
                                                                                         [:huoneistot 1 :huoneluku]
                                                                                         [:huoneistot 1 :jakokirjain]
                                                                                         [:huoneistot 1 :porras]
                                                                                         [:huoneistot 1 :huoneistoTyyppi]
                                                                                         [:huoneistot 1 :saunaKytkin]
                                                                                         [:huoneistot 1 :muutostapa]
                                                                                         [:huoneistot 1 :keittionTyyppi]
                                                                                         [:huoneistot 1 :lamminvesiKytkin]
                                                                                         [:huoneistot 1 :huoneistonumero]
                                                                                         [:huoneistot 1 :huoneistoala]
                                                                                         [:huoneistot 1 :WCKytkin]
                                                                                         [:huoneistot 1 :ammeTaiSuihkuKytkin]]
                                                                           :values      [false "3" "b" "A" "asuinhuoneisto"
                                                                                         true "lisäys" "keittokomero" true
                                                                                         "001" "50" false false]}]
                                :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 1 :parvekeTaiTerassiKytkin]
                                                                                       [:huoneistot 1 :huoneluku]
                                                                                       [:huoneistot 1 :jakokirjain]
                                                                                       [:huoneistot 1 :porras]
                                                                                       [:huoneistot 1 :huoneistoTyyppi]
                                                                                       [:huoneistot 1 :saunaKytkin]
                                                                                       [:huoneistot 1 :muutostapa]
                                                                                       [:huoneistot 1 :keittionTyyppi]
                                                                                       [:huoneistot 1 :lamminvesiKytkin]
                                                                                       [:huoneistot 1 :huoneistonumero]
                                                                                       [:huoneistot 1 :huoneistoala]
                                                                                       [:huoneistot 1 :WCKytkin]
                                                                                       [:huoneistot 1 :ammeTaiSuihkuKytkin]]
                                                                         :values      [false "3" "b" "A" "asuinhuoneisto"
                                                                                       true "lisäys" "keittokomero" true
                                                                                       "001" "50" false false]}]
                                :resp    {:ok      true
                                          :results []}})

                (copy-row!))))

          (testing "create a new empty row"
            (let [next-idx 2]
              (is (= next-idx (<sub [::docs/next-child-id doc-id table-path])))

              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["huoneistot.2.muutostapa" "lisäys"]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 2 :muutostapa]]
                                                                         :values      ["lisäys"]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:huoneistot 2 :muutostapa]]
                                                                       :values      ["lisäys"]}]
                              :resp    {:ok      true
                                        :results []}})

              (>evt [::table-actions/append-row-clicked (util/mmap table-path app-id doc-id next-idx)]))))))))

(deftest huoneisto-table-rakennuksen-laajentaminen-test
  ;; Here we toggle a single checkbox on -> off -> on
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-rakennuksen-laajentaminen)
          {:keys [allow-highlight-changes?
                  allow-premise-upload?]} huoneisto-schema-laajennos
          table-path                      [:huoneistot]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     (assoc test-utils/app :documents [doc-rakennuksen-laajentaminen])
                                  :details details}])

      (testing "initial flags"
        (is (false? (<sub [::huoneistot-common/change-permit?])))
        (is (true? (<sub [::highlight/highlight-changes? doc-id allow-highlight-changes?])))
        (is (false? (<sub [::upload/show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?]))))

      (let [row-path (conj table-path 0)]
        (is (true? (<sub [::rows/row-visible? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
        (is (nil? (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

        (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path])))

        (testing "interactive controls"
          (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
          (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
          (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
          (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

        (let [expected-cell-paths [[:huoneistot 0 :huoneistoTyyppi]
                                   [:huoneistot 0 :porras]
                                   [:huoneistot 0 :huoneistonumero]
                                   [:huoneistot 0 :jakokirjain]
                                   [:huoneistot 0 :huoneluku]
                                   [:huoneistot 0 :keittionTyyppi]
                                   [:huoneistot 0 :huoneistoala]
                                   [:huoneistot 0 :WCKytkin]
                                   [:huoneistot 0 :ammeTaiSuihkuKytkin]
                                   [:huoneistot 0 :saunaKytkin]
                                   [:huoneistot 0 :parvekeTaiTerassiKytkin]
                                   [:huoneistot 0 :lamminvesiKytkin]
                                   [:huoneistot 0 :pysyvaHuoneistotunnus]]]

          (is (= expected-cell-paths
                 (<sub [::cells/row-path->cell-paths doc-id row-path])))

          (testing "turn WCKytkin off"
            (let [checkbox-state false
                  cell-path      (conj row-path :WCKytkin)
                  on-change!     (fn [value]
                                   (>evt [::cells/change-immediate (util/mmap {:change-permit? false
                                                                               :cell-paths     expected-cell-paths}
                                                                              app-id doc-id cell-path value)]))]

              (is (true? (<sub [::value/current-field-value doc-id cell-path])))

              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["huoneistot.0.WCKytkin" checkbox-state]
                                                     ["huoneistot.0.muutostapa" "muutos"]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                       [:huoneistot 0 :muutostapa]]
                                                                         :values      [checkbox-state "muutos"]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                     [:huoneistot 0 :muutostapa]]
                                                                       :values      [checkbox-state "muutos"]}]
                              :resp    {:ok      true
                                        :results []}})

              (on-change! checkbox-state)

              (is (false? (<sub [::value/current-field-value doc-id cell-path])))
              (is (= muutostapa/edited
                     (<sub [::muutostapa/current-muutostapa doc-id row-path])))

              (is (true? (<sub [::cells-common/edited-cell? doc-id cell-path])))
              (is (= ["edited-cell"]
                     (<sub [::cells-common/cell-css doc-id cell-path])))
              (is (= "\"huoneistotTable.cellTooltipText\": \"yes\""
                     (<sub [::cells-common/cell-tooltip doc-id cell-path :checkbox])))))

          (testing "interactive controls"
            (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            (is (true? (<sub [::actions/show-undo-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

          (testing "turn WCKytkin back on"
            (let [checkbox-state true
                  cell-path      (conj row-path :WCKytkin)
                  on-change!     (fn [value]
                                   (>evt [::cells/change-immediate (util/mmap {:change-permit? false
                                                                               :cell-paths     expected-cell-paths}
                                                                              app-id doc-id cell-path value)]))]

              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["huoneistot.0.WCKytkin" checkbox-state]
                                                     ["huoneistot.0.muutostapa" ""]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                       [:huoneistot 0 :muutostapa]]
                                                                         :values      [checkbox-state ""]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                     [:huoneistot 0 :muutostapa]]
                                                                       :values      [checkbox-state ""]}]
                              :resp    {:ok      true
                                        :results []}})

              (on-change! checkbox-state)

              (is (true? (<sub [::value/current-field-value doc-id cell-path])))
              (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path])))))

          (testing "interactive controls"
            (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
            (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path])))))))))

(deftest huoneisto-table-rakennuksen-laajentaminen-mark-as-removed-test
  ;; Here we toggle a single checkbox on -> off -> on
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-rakennuksen-laajentaminen)
          {:keys [allow-highlight-changes?
                  allow-premise-upload?]} huoneisto-schema-laajennos
          table-path                      [:huoneistot]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     (assoc test-utils/app :documents [doc-rakennuksen-laajentaminen])
                                  :details details}])

      (testing "initial flags"
        (is (false? (<sub [::huoneistot-common/change-permit?])))
        (is (true? (<sub [::highlight/highlight-changes? doc-id allow-highlight-changes?])))
        (is (false? (<sub [::upload/show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?]))))

      (let [row-path (conj table-path 0)]
        (is (true? (<sub [::rows/row-visible? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
        (is (nil? (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

        (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path])))

        (testing "interactive controls"
          (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
          (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
          (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
          (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

        (let [mark-as-removed! (muutostapa/make-mark-as-removed-callback (util/mmap app-id doc-id row-path))]

          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["huoneistot.0.muutostapa" "poisto"]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:huoneistot 0 :muutostapa]]
                                                                     :values      ["poisto"]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:huoneistot 0 :muutostapa]]
                                                                   :values      ["poisto"]}]
                          :resp    {:ok      true
                                    :results []}})

          (mark-as-removed!))

        (is (= muutostapa/removed
               (<sub [::muutostapa/current-muutostapa doc-id row-path])))

        (is (true? (<sub [::rows/row-visible? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
        (is (true? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
        (is (true? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
        (is (= ["removed-row"] (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

        (testing "interactive controls"
          (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
          (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
          (is (true? (<sub [::actions/show-undo-button? doc-id row-path])))
          (is (false? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

        (testing "undo the removal"
          (let [fields [:huoneistoTyyppi
                        :porras
                        :huoneistonumero
                        :jakokirjain
                        :huoneluku
                        :keittionTyyppi
                        :huoneistoala
                        :WCKytkin
                        :ammeTaiSuihkuKytkin
                        :saunaKytkin
                        :parvekeTaiTerassiKytkin
                        :lamminvesiKytkin]]
            ;; muutostapa isn't listed, it's handled separately
            (is (= fields (actions/undo-fields huoneisto-schema-laajennos)))

            (testing "undo payload"
              (is (= {:field-paths [[:huoneistot 0 :muutostapa]]
                      :values      [""]}
                     (<sub [::actions/undo-payload doc-id row-path fields])))))

          (testing "interactive controls"
            (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            (is (true? (<sub [::actions/show-undo-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

          (testing "call undo"
            (let [undo-callback! (actions/make-undo-callback (util/mmap {:schema huoneisto-schema-laajennos}
                                                                        app-id doc-id row-path))]

              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["huoneistot.0.muutostapa" ""]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 0 :muutostapa]]
                                                                         :values      [""]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:huoneistot 0 :muutostapa]]
                                                                       :values      [""]}]
                              :resp    {:ok      true
                                        :results []}})
              (undo-callback!))

            (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path])))

            (is (true? (<sub [::rows/row-visible? doc-id row-path])))
            (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
            (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
            (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
            (is (false? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
            (is (nil? (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

            (testing "interactive controls"
              (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
              (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
              (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
              (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))))))))

(deftest huoneisto-table-rakennuksen-laajentaminen-undo-test
  ;; Similar to `huoneisto-table-rakennuksen-laajentaminen-test`
  ;; except here we use the undo action to clear the changes
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-rakennuksen-laajentaminen)
          {:keys [allow-highlight-changes?
                  allow-premise-upload?]} huoneisto-schema-laajennos
          table-path                      [:huoneistot]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     (assoc test-utils/app :documents [doc-rakennuksen-laajentaminen])
                                  :details details}])

      (testing "initial flags"
        (is (false? (<sub [::huoneistot-common/change-permit?])))
        (is (true? (<sub [::highlight/highlight-changes? doc-id allow-highlight-changes?])))
        (is (false? (<sub [::upload/show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?]))))

      (let [row-path (conj table-path 0)]
        (is (true? (<sub [::rows/row-visible? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
        (is (false? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
        (is (nil? (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

        (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path])))

        (let [expected-cell-paths [[:huoneistot 0 :huoneistoTyyppi]
                                   [:huoneistot 0 :porras]
                                   [:huoneistot 0 :huoneistonumero]
                                   [:huoneistot 0 :jakokirjain]
                                   [:huoneistot 0 :huoneluku]
                                   [:huoneistot 0 :keittionTyyppi]
                                   [:huoneistot 0 :huoneistoala]
                                   [:huoneistot 0 :WCKytkin]
                                   [:huoneistot 0 :ammeTaiSuihkuKytkin]
                                   [:huoneistot 0 :saunaKytkin]
                                   [:huoneistot 0 :parvekeTaiTerassiKytkin]
                                   [:huoneistot 0 :lamminvesiKytkin]
                                   [:huoneistot 0 :pysyvaHuoneistotunnus]]]

          (is (= expected-cell-paths
                 (<sub [::cells/row-path->cell-paths doc-id row-path])))

          (testing "turn WCKytkin off"
            (let [checkbox-state false
                  cell-path      (conj row-path :WCKytkin)
                  on-change!     (fn [value]
                                   (>evt [::cells/change-immediate (util/mmap {:change-permit? false
                                                                               :cell-paths     expected-cell-paths}
                                                                              app-id doc-id cell-path value)]))]

              (is (true? (<sub [::value/current-field-value doc-id cell-path])))

              (expect-action {:name    :update-doc
                              :params  {:id         app-id
                                        :doc        doc-id
                                        :updates    [["huoneistot.0.WCKytkin" checkbox-state]
                                                     ["huoneistot.0.muutostapa" "muutos"]]
                                        :collection :documents}
                              :success [::docgen-common/on-save-success {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                       [:huoneistot 0 :muutostapa]]
                                                                         :values      [checkbox-state "muutos"]}]
                              :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                       :doc-id      doc-id
                                                                       :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                     [:huoneistot 0 :muutostapa]]
                                                                       :values      [checkbox-state "muutos"]}]
                              :resp    {:ok      true
                                        :results []}})

              (on-change! checkbox-state)

              (is (false? (<sub [::value/current-field-value doc-id cell-path])))
              (is (= muutostapa/edited
                     (<sub [::muutostapa/current-muutostapa doc-id row-path])))

              (is (true? (<sub [::cells-common/edited-cell? doc-id cell-path])))
              (is (= ["edited-cell"]
                     (<sub [::cells-common/cell-css doc-id cell-path])))
              (is (= "\"huoneistotTable.cellTooltipText\": \"yes\""
                     (<sub [::cells-common/cell-tooltip doc-id cell-path :checkbox])))))

          (testing "use undo to clear the changes"
            (let [fields [:huoneistoTyyppi
                          :porras
                          :huoneistonumero
                          :jakokirjain
                          :huoneluku
                          :keittionTyyppi
                          :huoneistoala
                          :WCKytkin
                          :ammeTaiSuihkuKytkin
                          :saunaKytkin
                          :parvekeTaiTerassiKytkin
                          :lamminvesiKytkin]]
              ;; muutostapa isn't listed, it's handled separately
              (is (= fields (actions/undo-fields huoneisto-schema-laajennos)))

              (is (nil? (<sub [::muutostapa/original-muutostapa doc-id row-path])))
              (is (= "" (<sub [::actions/undo-muutostapa doc-id row-path])))

              (testing "undo payload"
                (is (= {:field-paths [[:huoneistot 0 :WCKytkin]
                                      [:huoneistot 0 :muutostapa]]
                        :values      [true ""]}
                       (<sub [::actions/undo-payload doc-id row-path fields])))))

            (testing "interactive controls"
              (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
              (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
              (is (true? (<sub [::actions/show-undo-button? doc-id row-path])))
              (is (false? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))

            (testing "call undo"
              (let [checkbox-state true
                    cell-path      (conj row-path :WCKytkin)
                    undo-callback! (actions/make-undo-callback (util/mmap {:schema huoneisto-schema-laajennos}
                                                                          app-id doc-id row-path))]

                (expect-action {:name    :update-doc
                                :params  {:id         app-id
                                          :doc        doc-id
                                          :updates    [["huoneistot.0.WCKytkin" checkbox-state]
                                                       ["huoneistot.0.muutostapa" ""]]
                                          :collection :documents}
                                :success [::docgen-common/on-save-success {:app-id      app-id
                                                                           :doc-id      doc-id
                                                                           :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                         [:huoneistot 0 :muutostapa]]
                                                                           :values      [checkbox-state ""]}]
                                :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                         :doc-id      doc-id
                                                                         :field-paths [[:huoneistot 0 :WCKytkin]
                                                                                       [:huoneistot 0 :muutostapa]]
                                                                         :values      [checkbox-state ""]}]
                                :resp    {:ok      true
                                          :results []}})
                (undo-callback!)

                (is (true? (<sub [::value/current-field-value doc-id cell-path])))
                (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path]))))

              (testing "interactive controls"
                (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
                (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
                (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
                (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path])))))))))))

(deftest huoneisto-table-rakennuksen-laajentaminen-muutos--test
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-rakennuksen-laajentaminen-muutos)
          {:keys [allow-highlight-changes?
                  allow-premise-upload?]} huoneisto-schema-laajennos
          table-path                      [:huoneistot]]

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     (assoc test-utils/app :documents [doc-rakennuksen-laajentaminen-muutos]
                                                  :permitSubtype "muutoslupa")
                                  :details details}])

      ;; At the start the rows look like the end result of the previous permit;
      ;; If the original permit added a row, it will look added here (= green).
      ;; And the same for the unmodified rows or modified rows.

      ;; But if we _do_ actually alter the fields, then
      ;; the added row should become a modified row
      ;; the modified row remains a modified row.

      (testing "initial flags"
        (is (true? (<sub [::huoneistot-common/change-permit?])))
        (is (true? (<sub [::highlight/highlight-changes? doc-id allow-highlight-changes?])))
        (is (false? (<sub [::upload/show-premises-upload? doc-id allow-premise-upload? allow-highlight-changes?]))))

      (testing "first row looks unmodified"
        (let [row-path (conj table-path 0)]
          (is (<sub [::rows/row-visible? doc-id row-path]))
          (is (nil? (<sub [::muutostapa/current-muutostapa doc-id row-path])))

          (testing "interactive controls"
            ;; original permit did not touch this row => do not show "in changes" checkbox
            (is (false? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
            ;; all we can do is remove the row
            (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))))

      (testing "second row looks modified"
        (let [row-path (conj table-path 1)]
          (is (true? (<sub [::rows/row-visible? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
          (is (= ["edited-row"] (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

          (is (= muutostapa/edited
                 (<sub [::muutostapa/current-muutostapa doc-id row-path])))
          (testing "interactive controls"
            (is (true? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (true? (<sub [::in-changes/in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
            (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))))

      (testing "third row looks removed"
        (let [row-path (conj table-path 2)]
          (is (true? (<sub [::rows/row-visible? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
          (is (true? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
          (is (true? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
          (is (= ["removed-row"] (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

          (is (= muutostapa/removed
                 (<sub [::muutostapa/current-muutostapa doc-id row-path])))
          (testing "interactive controls"
            (is (true? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (true? (<sub [::in-changes/in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            ;; We show the undo button, in case the user decides the don't actually
            ;; want to remove the row they originally marked as to-be-removed.
            (is (true? (<sub [::actions/show-undo-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))))

      (testing "fourth row looks new"
        (let [row-path (conj table-path 3)]
          (is (true? (<sub [::rows/row-visible? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-new? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-edited? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-is-to-be-removed? doc-id row-path])))
          (is (false? (<sub [::cells-common/row-differs-from-original? doc-id row-path])))
          (is (= ["new-row"] (<sub [::rows/row-css doc-id row-path allow-highlight-changes?])))

          (is (= muutostapa/added
                 (<sub [::muutostapa/current-muutostapa doc-id row-path])))
          (testing "interactive controls"
            (is (true? (<sub [::in-changes/show-in-changes? doc-id row-path])))
            (is (true? (<sub [::in-changes/in-changes? doc-id row-path])))
            (is (false? (<sub [::actions/show-remove-button? doc-id row-path])))
            (is (false? (<sub [::actions/show-undo-button? doc-id row-path])))
            (is (true? (<sub [::actions/show-mark-removed-button? doc-id row-path]))))))

      (testing "cell infos for all rows are identical"
        (doseq [row-id [0 1 2 3]]
          (let [row-path            (conj table-path row-id)
                expected-cell-paths [[:huoneistot row-id :huoneistoTyyppi]
                                     [:huoneistot row-id :porras]
                                     [:huoneistot row-id :huoneistonumero]
                                     [:huoneistot row-id :jakokirjain]
                                     [:huoneistot row-id :huoneluku]
                                     [:huoneistot row-id :keittionTyyppi]
                                     [:huoneistot row-id :huoneistoala]
                                     [:huoneistot row-id :WCKytkin]
                                     [:huoneistot row-id :ammeTaiSuihkuKytkin]
                                     [:huoneistot row-id :saunaKytkin]
                                     [:huoneistot row-id :parvekeTaiTerassiKytkin]
                                     [:huoneistot row-id :lamminvesiKytkin]
                                     [:huoneistot row-id :pysyvaHuoneistotunnus]]]

            (is (= expected-cell-paths
                   (<sub [::cells/row-path->cell-paths doc-id row-path]))))))

      (testing "no edits made -> show only edited shows no changes"
        ;; It's a bit counterintuitive, but the removed row is considered as if
        ;; it's been modified. Even though we haven't touched anything.
        (is (= 1 (<sub [::highlight/modified-row-count doc-id table-path])))
        (is (false? (<sub [::highlight/show-only-edited? doc-id])))

        (testing "enable 'show only edited'"
          (>evt [::highlight/show-only-edited-clicked doc-id true])
          (is (true? (<sub [::highlight/show-only-edited? doc-id])))

          (is (false? (<sub [::rows/row-visible? doc-id (conj table-path 0)])))
          (is (false? (<sub [::rows/row-visible? doc-id (conj table-path 1)])))
          ;; Again, the removed row is visible as it's considered as edited. Even though it isn't.
          (is (true? (<sub [::rows/row-visible? doc-id (conj table-path 2)])))
          (is (false? (<sub [::rows/row-visible? doc-id (conj table-path 3)]))))))))
