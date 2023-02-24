(ns lupapalvelu.missing-templates-itest
  "The missing templates are tested in the unit tests only when the `template-placeholder`
  feature is enabled. However, it is prudent to run the test always in itest."
  (:require [lupapalvelu.html-email.template :refer [missing-templates]]
            [midje.sweet :refer :all]))

(fact "No missing templates"
  (missing-templates) => [])
