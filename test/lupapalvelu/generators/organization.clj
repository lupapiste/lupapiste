(ns lupapalvelu.generators.organization
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.generators.stamp]
            [lupapalvelu.organization :as org]
            [sade.schema-generators :as ssg]
            ))

(def org-id-gen
  (gen/fmap str (gen/large-integer* {:min 100, :max 999})))

(ssg/register-generator org/OrgId org-id-gen)
