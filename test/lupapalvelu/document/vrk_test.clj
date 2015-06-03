(ns lupapalvelu.document.vrk-test
  (:use [lupapalvelu.document.tools]
        [lupapalvelu.document.validators]
        [lupapalvelu.document.model]
        [lupapalvelu.itest-util]
        [midje.sweet])
  (:refer-clojure :exclude [pos? neg? zero?])
  (:require [lupapalvelu.document.validator :as v]
            [clojure.string :as s]
            [sade.util :refer :all]))

(defn check-validator
  "Runs generated facts of a single validator."
  [{:keys [code doc schema level paths] validate-fn :fn {:keys [ok fail]} :facts}]
  (when (and ok fail)
    (let [dummy  (dummy-doc schema)
          doc    (s/replace doc #"\s+" " ")
          update (fn [values]
                   (reduce
                     (fn [d i]
                       (apply-update d (get paths i) (get values i)))
                     dummy
                     (range 0 (count paths))))]

      (facts "Embedded validator facts"
        (println "Checking:" doc)
        (doseq [values ok]
          (validate-fn (update values)) => nil?)
        (doseq [values fail]
          (validate-fn (update values)) => (has some (contains {:result [level (name code)]})))))))

(defn check-all-validators []
  (let [validators (->> v/validators deref vals (filter (fn-> :facts nil? not)))]
    (println "Checking" (str (count validators) "/" (count @v/validators)) "awesome validators!")
    (doseq [validator validators]
      (check-validator validator))))

(facts "Embedded validator facts"
  (check-all-validators))
