(ns lupapalvelu.features-api
  (:require [sade.env :as env]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]))

(defquery "features"
   {:description "returns list of features"
    :user-roles #{:anonymous}}
   [_] (ok :features (into {} (filter second (env/features)))))

(when (env/dev-mode?)
  (defcommand set-feature
    {:description "Sets a feature flag to given value"
     :parameters [feature value]
     :input-validators [(partial action/non-blank-parameters [:feature])
                        (partial action/boolean-parameters [:value])]
     :user-roles #{:anonymous}}
    [_]
    (env/set-feature! value [feature])
    (ok :feature feature :value value)))
