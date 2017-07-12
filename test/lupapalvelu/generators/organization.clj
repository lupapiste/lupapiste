(ns lupapalvelu.generators.organization
  (:require [schema.core :as sc]
            [clojure.test.check.generators :as gen]
            [lupapalvelu.generators.stamp]
            [lupapalvelu.organization :as org]
            [sade.schema-generators :as ssg]
            ))

(def org-id-gen
  (gen/fmap str (gen/large-integer* {:min 100, :max 999})))

(ssg/register-generator org/OrgId org-id-gen)

(def org-with-const-id
  (gen/fmap (fn [org] (assoc org :id "100"))
            (ssg/generator org/Organization)))

(defn generate
  "Generates a set of organizations with different ids"
  ([] (generate 10))
  ([num]
   (gen/let [org-ids (gen/set (ssg/generator org/OrgId) {:num-elements num
                                                         :max-tries 50})
             orgs (gen/vector org-with-const-id num)]
            (let [fix-id (fn [id org] (assoc org :id id))
                  with-fixed-ids (map fix-id org-ids orgs)]
              (set with-fixed-ids)))))
