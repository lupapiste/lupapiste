(ns lupapalvelu.allu.allu-application
  #?(:clj
     (:require [sade.env :as env])))

(defn allu-application?
  "Should ALLU integration be used?"
  [organization-id permit-type]
  (and #?(:clj  (env/feature? :allu)
          :cljs (js/features.enabled (name :allu)))
       (or (= permit-type "A")
           (and (= organization-id "091-YA")                ; HACK: Hardcoded 091-YA
                (= permit-type "YA")))))
