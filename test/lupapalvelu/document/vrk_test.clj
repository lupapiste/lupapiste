(ns lupapalvelu.document.vrk-test
  (:require [lupapalvelu.document.model :refer [apply-update]]
            [lupapalvelu.document.validator :as v]
            [lupapalvelu.test-util :refer [dummy-doc]]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :refer [fn->]]))

(defn check-validator
  "Runs generated facts of a single validator."
  [{:keys [code doc schema level paths] validate-fn :fn {:keys [ok fail]} :facts}]
  (when (and ok fail)
    (let [dummy                    (dummy-doc schema)
          doc                      (ss/replace doc #"\s+" " ")
          code-without-schema-name (subs (name code) (inc (count schema)))
          update                   (fn [values]
                                     (v/delay-data
                                       (reduce
                                         (fn [d i]
                                           (apply-update d (get paths i) (get values i)))
                                         dummy
                                         (range 0 (count paths)))))]

      (facts {:midje/description (str "Embedded validator facts for " doc)}
        (doseq [values ok]
          (fact {:midje/description (str "- ok check: " values)}
            (validate-fn (update values)) => nil?))
        (doseq [values fail]
          (fact {:midje/description (str "- fail check: " values)}
            (validate-fn (update values)) => (has some (contains {:result [level code-without-schema-name]}))))))))

(defn check-all-validators []
  (let [validators (->> v/validators deref vals (filter (fn-> :facts nil? not)))]
    (println "Checking" (str (count validators) "/" (count @v/validators)) "awesome validators!")
    (doseq [validator validators]
      (check-validator validator))))

(facts "Embedded validator facts"
  (check-all-validators))
