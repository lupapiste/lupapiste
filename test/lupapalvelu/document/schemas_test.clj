(ns lupapalvelu.document.schemas-test
  ;; Make sure all schema related namespaces are loaded
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.document.all-schemas];; Ensure all document schemas are registered
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.shared-tools :as shared-tools]
            [lupapalvelu.tasks]
            [sade.util :as util]))

(deftest repeatable-test
  (is (= [{:name      "beers"
           :type      :group
           :repeating true
           :body      [{:name :beer
                        :type :string}]}]
         (schemas/repeatable "beers" {:name :beer
                                      :type :string}))))

(deftest update-in-body-test
  (let [body [{:name "foo"
               :body [{:name "bar"
                       :body [{:name "baz"}]}]}
              {:name "hoo"
               :body [{:name "haa"}
                      {:name "huu"}]}]]
    (is (= [{:name "foo"
             :body [{:name "bar"
                     :body [{:name "baz" :testi true}]}]}
            {:name "hoo"
             :body [{:name "haa"}
                    {:name "huu"}]}]
           (schemas/update-in-body body ["foo" "bar" "baz"] :testi (constantly true))))
    (is (= body
           (schemas/update-in-body body ["foo" "bar" "wrong"] :testi (constantly true))))
    (is (= {:name  "hoo"
            :body  [{:name "haa"}
                    {:name "huu"}]
            :testi false}
           (last (schemas/update-in-body body ["hoo"] :testi (constantly false)))))
    (is (= {:name "foo" :body []}
           (first (schemas/update-in-body body ["foo"] :body (fn [old-val] (remove map? old-val))))))
    (is (= {:name "foo"
            :body [{:name "bar"
                    :body [{:name "baz"}]}
                   "faa"]}
           (first (schemas/update-in-body body ["foo"] :body #(conj % "faa")))))))

(deftest resolve-accordion-field-values-test
  (testing "Resolve accordion field values"
    (let [doc {:schema-info {:accordion-fields [{:type  :selected
                                                 :paths [["foo" "first"]
                                                         ["foo" "second"]
                                                         ["bar" "third"]]}
                                                {:type  :text
                                                 :paths [["text" "tax"]]}
                                                [["str1" "s1"]
                                                 ["str2" "s2"]
                                                 ["not" "available" "in" "data"]]]}
               :data        {:foo  {:first  {:value "First"}
                                    :second {:value "Second"}}
                             :bar  {:third {:value "Third"}}
                             :text {:hii {:value "blaah"}
                                    :tax {:value "Tax"}}
                             :str1 {:s1 {:value "String1"}}
                             :str2 {:s2 {:value "String2"}}}}]
      (testing "None selected"
        (is (= ["Tax" "String1" "String2"]
               (schemas/resolve-accordion-field-values doc))))
      (testing "Foo selected"
        (is (= ["First" "Second" "Tax" "String1" "String2"]
               (schemas/resolve-accordion-field-values
                 (update-in doc [:data :_selected :value] (constantly "foo"))))))
      (testing "Bar selected"
        (is (= ["Third" "Tax" "String1" "String2"]
               (schemas/resolve-accordion-field-values
                 (update-in doc [:data :_selected :value] (constantly "bar")))))))))

(deftest rakennusoikeudellinenKerrosala
  (testing "min value is 1 when rakentaminen"
    (is (= 1
           (->> schemas/rakennuksen-tiedot
                (util/find-by-key :name "mitat")
                :body
                (util/find-by-key :name "rakennusoikeudellinenKerrosala")
                :min))))
  (testing "min value is -9999999 when laajentaminen"
    (is (= -9999999
           (->> schemas/rakennuksen-laajentaminen
                (util/find-by-key :name "laajennuksen-tiedot")
                :body
                (util/find-by-key :name "mitat")
                :body
                (util/find-by-key :name "rakennusoikeudellinenKerrosala")
                :min)))))

(deftest mapping->options-test
  (testing "with no data returns empty vector"
    (is (= [] (schemas/mapping->options {})))
    (is (= [] (schemas/mapping->options []))))
  (testing "will transform a map into options for a select component"
    (is (= [{:name "foo"}]
           (schemas/mapping->options {:foo "42"}))))
  (testing "can add a i18n-prefix via the :i18nkey argument"
    (is (= [{:name    "foo"
             :i18nkey "suomi.fi.foo"}
            {:name    "bar"
             :i18nkey "suomi.fi.bar"}]
           (schemas/mapping->options {:foo "some-value" :bar "42"} :i18nkey :suomi.fi))))
  (testing "can also take a vector of options and add the i18nkey-prefix to each element"
    (is (= [{:name    "foo"
             :i18nkey "suomi.fi.foo"}
            {:name    "bar"
             :i18nkey "suomi.fi.bar"}]
           (schemas/mapping->options [{:name :foo} {:name "bar"}] :i18nkey :suomi.fi))))
  (testing "options can contain other keys, they will be retained"
    (is (= [{:name     "foo"
             :i18nkey  "suomi.fi.foo"
             :optgroup true
             :baz      "42"}
            {:name    "bar"
             :i18nkey "suomi.fi.bar"
             :foo     true}]
           (schemas/mapping->options [{:name     :foo
                                       :optgroup true
                                       :baz      "42"}
                                      {:name "bar"
                                       :foo  true}]
                                     :i18nkey :suomi.fi)))))

(defn- find-schemas-with-highlight
  "Returns a map of `schema name` -> `paths`."
  [schemas flag enabled?]
  (reduce (fn [acc schema]
            (let [paths (shared-tools/walk-schema
                          schema
                          (fn [{:keys [path element]}]
                            (when (= enabled? (get element flag))
                              {:result path})))]
              (if (seq paths)
                (assoc acc (-> schema :info :name) paths)
                acc)))
          {}
          schemas))

(deftest schemas-with-highlights
  (let [all-schemas (vals (first (vals (schemas/get-all-schemas))))]
    ;; sanity check that all schemas are loaded
    ;; Equality check is difficult, as some test namespaces register test schemas
    (is (<= 150 (count all-schemas)))
    (testing "highlight-changes?"
      (is (= {"rakennuskohteenTietojenKorjaus" [[:rakennuskohteen-osa :huoneistot]]
              "rakennustietojen-korjaus"       [[:huoneistot]]
              "rakennuksen-laajentaminen"      [[:huoneistot]]
              "rakennuskohteenPurkaminen"      [[:rakennuskohteen-osa :huoneistot]]
              "rakennuskohteenPurkaminen-purkamisilmoitus" [[:rakennuskohteen-osa :huoneistot]]
              "rakennuskohteenLaajentaminen"   [[:rakennuskohteen-osa :huoneistot]]
              "rakennuskohteenMuuttaminen"     [[:rakennuskohteen-osa :huoneistot]]
              "rakennuksen-muuttaminen"        [[:huoneistot]]
              "aiemman-luvan-toimenpide"       [[:huoneistot]]}
             (find-schemas-with-highlight all-schemas :allow-highlight-changes? true)))
      (is (= {"uusiRakennus"      [[:huoneistot]]
              "uusiRakennuskohde" [[:rakennuskohteen-osa :huoneistot]]}
             (find-schemas-with-highlight all-schemas :allow-highlight-changes? false))))
    (testing "allow premise upload?"
      (is (= {"uusiRakennus"      [[:huoneistot]]
              "uusiRakennuskohde" [[:rakennuskohteen-osa :huoneistot]]}
             (find-schemas-with-highlight all-schemas :allow-premise-upload? true)))
      (is (= {"rakennuskohteenTietojenKorjaus" [[:rakennuskohteen-osa :huoneistot]]
              "rakennustietojen-korjaus"       [[:huoneistot]]
              "rakennuksen-laajentaminen"      [[:huoneistot]]
              "rakennuskohteenPurkaminen"      [[:rakennuskohteen-osa :huoneistot]]
              "rakennuskohteenPurkaminen-purkamisilmoitus" [[:rakennuskohteen-osa :huoneistot]]
              "rakennuskohteenLaajentaminen"   [[:rakennuskohteen-osa :huoneistot]]
              "rakennuskohteenMuuttaminen"     [[:rakennuskohteen-osa :huoneistot]]
              "rakennuksen-muuttaminen"        [[:huoneistot]]
              "aiemman-luvan-toimenpide"       [[:huoneistot]]}
             (find-schemas-with-highlight all-schemas :allow-premise-upload? false))))))

(defn- schema-contains-building-id-field?
  "Returns true if the schema contains any of the building ID field names."
  [schema]
  (let [building-field-ids (set schemas/building-id-doc-fields)
        found-fields (shared-tools/walk-schema
                       schema
                       (fn [{:keys [element]}]
                         (when (building-field-ids (-> element :name keyword))
                           {:result (:name element)})))]
    (-> found-fields seq boolean)))

(deftest schemas-with-building-id-fields-must-have-building-flag
  (testing "All schemas containing building ID fields must have :info :building? true"
    (let [all-schemas       (vals (first (vals (schemas/get-all-schemas))))
          document-schemas  (remove #(= :task (-> % :info :type)) all-schemas)
          schemas-build-id  (filter schema-contains-building-id-field? document-schemas)
          missing-build-ids (->> schemas-build-id
                                 (remove #(-> % :info :building?))
                                 (mapv #(-> % :info :name)))]
      ;; Sanity check that we found a schema with building ID fields
      (is (seq schemas-build-id)
          "Expected to find at least one schema with building ID fields")
      (is (= [] missing-build-ids)
          "Schemas with building ID fields missing :info :building? true"))))
