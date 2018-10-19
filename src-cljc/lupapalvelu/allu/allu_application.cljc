(ns lupapalvelu.allu.allu-application
  #?(:clj
     (:require [sade.env :as env])))

(defn allu-application?
  "Should ALLU integration be used?"
  [organization-id permit-type]
  (let [feature-flag :allu]
    (and #?(:clj (env/feature? feature-flag)
            :cljs (js/features.enabled (name feature-flag)))
         (= organization-id "091-YA") (= permit-type "YA"))))