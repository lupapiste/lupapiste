(ns lupapalvelu.generators.user
  (require [clojure.test.check.generators :as gen]
           [lupapalvelu.roles :as roles]
           [lupapalvelu.user :refer :all]
           [sade.schema-generators :as ssg]))

(def org-id-num-generator
  (gen/choose 100 1000))

(def org-id-suffix-generator
  (gen/elements #{"YA" "YMP" "R"}))

(defn org-id-generator [& {:keys [id-num id-suffix]
                           :or {:id-num org-id-num-generator
                                :id-suffix org-id-suffix-generator}}]
  (gen/let [number-part org-id-num-generator
            suffix org-id-suffix-generator]
    (keyword (str number-part "-" suffix))))

(def keyword-authz-generator
  (let [default-roles (gen/elements roles/default-org-authz-roles)
        all-roles (gen/elements roles/all-authz-roles)]
    (gen/frequency [[1 default-roles]
                    [1 all-roles]])))

(def authz-generator
  (gen/fmap name keyword-authz-generator))

(ssg/register-generator OrgId (org-id-generator))
(ssg/register-generator Authz authz-generator)

(def distinct-authz-generator
  (let [authz-gen (ssg/generator Authz)]
    (gen/vector-distinct authz-gen
                         {:min-elements 0
                          :max-elements (count roles/all-authz-roles)})))

(ssg/register-generator [Authz] distinct-authz-generator)


