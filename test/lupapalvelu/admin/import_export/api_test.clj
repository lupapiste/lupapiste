(ns lupapalvelu.admin.import-export.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.admin.import-export.api :as api]
            [schema.utils :as scu]))

(deftest coercion-test
  (let [coercer   api/target+org-id+item-ids
        validator (api/validate coercer)]
    (testing "single item"
      (let [input {:target   "phrase"
                   :org-id   "753-R"
                   :item-ids "1"}]
        (is (= {:target   :phrase
                :org-id   "753-R"
                :item-ids ["1"]}
               (coercer input)))
        (is (nil? (validator {:data input})))))

    (testing "multiple item"
      (let [input {:target   "phrase"
                   :org-id   "753-R"
                   :item-ids "1,2  ,  3"}]
        (is (= {:target   :phrase
                :org-id   "753-R"
                :item-ids ["1" "2" "3"]}
               (coercer input)))
        (is (nil? (validator {:data input})))))

    (testing "invalid values"
      (doseq [input [nil {}
                     {:target   "phrase"
                      :org-id   "753-R"}
                     {:target   "phrase"
                      :org-id   "753-R"
                      :item-ids 123}
                     {:target   "phrase"
                      :org-id   "753-R"
                      :item-ids ""}
                     {:target   "phrase"
                      :org-id   "753-R"
                      :item-ids "   "}]]
        (is (scu/error? (coercer input)))
        (is (= {:ok false :text "error.illegal-value:schema-validation"}
               (validator {:data input})))))))
