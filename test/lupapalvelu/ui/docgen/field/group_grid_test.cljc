(ns lupapalvelu.ui.docgen.field.group-grid-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.ui.docgen.field.group-grid :as grid]
            [schema.core :as sc]))

(use-fixtures :once (fn [test-fn] (sc/with-fn-validation
                                    (test-fn))))

(defn- with-rows
  [args rows]
  (assoc-in args [:schema :rows] rows))

(deftest parse-rows-test
  (let [parent-path [:foo :bar]
        field-a     {:name    "firstField"
                     :type    :string
                     :i18nkey "customKey"}
        field-b     {:name "other.secondField"
                     :type :date}
        field-c     {:name "nestedField"
                     :type :string}
        group       {:name "nestedGroup"
                     :type :group
                     :body [field-c]}
        options     {:path   parent-path
                     :schema {:body [field-a
                                     field-b
                                     group]
                              :rows []}}]
    (testing "path map"
      (is (= {["firstField"]                field-a
              ["other.secondField"]         field-b
              ["nestedGroup"]               group
              ["nestedGroup" "nestedField"] field-c}
             (grid/build-path-map [field-a
                                   field-b
                                   group]))))
    (testing "simple text row"
      (is (= [{:class [:h3]
               :ltext "laajennettuRakennusjateselvitys.rakennusJaPurkujate"}]
             (grid/process-rows
               (with-rows options
                 [{:h3 "laajennettuRakennusjateselvitys.rakennusJaPurkujate"}]))))
      (is (= [{:class [:h3 :uppercase]
               :ltext "laajennettuRakennusjateselvitys.rakennusJaPurkujate"}]
             (grid/process-rows
               (with-rows options
                 [{:h3.uppercase "laajennettuRakennusjateselvitys.rakennusJaPurkujate"}])))))
    (testing "simple row"
      (testing "no columns"
        (is (= [{:columns [{:path      [:foo :bar]
                            :schema    field-a
                            :col-class ["col-1"]}
                           {:path      [:foo :bar]
                            :schema    field-b
                            :col-class ["col-1"]}]}]
               (grid/process-rows
                 (with-rows options
                   [["firstField" "other.secondField"]])))))
      (testing "row with explicit columns"
        (is (= [{:columns [{:path      [:foo :bar]
                            :schema    field-a
                            :col-class ["col-3"]}
                           {:path      [:foo :bar]
                            :schema    field-b
                            :col-class ["col-1"]}]}]
               (grid/process-rows
                 (with-rows options
                   [["firstField::3" "other.secondField"]]))))))
    (testing "nested field"
      (testing "plain nested field"
        (is (= [{:columns [{:path      [:foo :bar :nestedGroup]
                            :schema    field-c
                            :col-class ["col-1"]}]}]
               (grid/process-rows
                 (with-rows options
                   [["nestedGroup/nestedField"]])))))
      (testing "nested field with columns"
        (is (= [{:columns [{:path      [:foo :bar :nestedGroup]
                            :schema    field-c
                            :col-class ["col-5"]}]}]
               (grid/process-rows
                 (with-rows options
                   [["nestedGroup/nestedField::5"]]))))))
    (testing "unknown row is ignored"
      (is (= [{:columns [{:path      [:foo :bar]
                          :schema    field-a
                          :col-class ["col-1"]}
                         {:path      [:foo :bar]
                          :schema    field-b
                          :col-class ["col-1"]}]}]
             (grid/process-rows
               (with-rows options
                 [["firstField" "other.secondField" "unknownField"]]))))
      (testing "row discarded if it only refers to unknown fields"
        (is (= [{:ltext "foobar" :class [:h3]}]
               (grid/process-rows
                 (with-rows options
                   [{:h3 "foobar"}
                    ["unknownField"]]))))
        (is (= []
               (grid/process-rows
                 (with-rows options
                   [["unknownField"]]))))))
    (testing "overwrite i18nkey"
      (is (= [{:columns [{:path      [:foo :bar]
                          :schema    field-a ;; field-a already has `:i18nkey`
                          :col-class ["col-1"]}
                         {:path      [:foo :bar]
                          :schema    (assoc field-b :i18nkey "whatever.other.secondField")
                          :col-class ["col-1"]}]}]
             (grid/process-rows
               (-> options
                   (with-rows [["firstField" "other.secondField"]])
                   (assoc-in [:schema :i18nkey] "whatever"))))))
    (testing "row with css"
      (is (= [{:columns [{:path      [:foo :bar]
                          :schema    field-a
                          :col-class ["col-1"]}]
               :row-css [:foo-bar]}]
             (grid/process-rows
               (with-rows options
                 [{:row ["firstField"]
                   :css [:foo-bar]}])))))))
