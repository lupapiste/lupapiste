(ns lupapalvelu.generators.permit
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.permit :as permit]))

(def permit-type-gen (gen/elements (keys (permit/permit-types))))

(def YA-biased-permit-type
  (gen/frequency [[1 (gen/return "YA")]
                  [1 permit-type-gen]]))
