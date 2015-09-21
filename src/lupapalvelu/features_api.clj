(ns lupapalvelu.features-api
  (:require [sade.env :as env]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.document.tools :as tools]))

(defquery "features"
   {:description "returns list of features"
    :user-roles #{:anonymous}}
   [_] (ok :features (into {} (filter second (env/features)))))

(when (env/dev-mode?)
  (defquery "set-feature"
    {:description "Sets a feature flag to given value"
     :parameters [:feature :value]
     :user-roles #{:anonymous}}
    [{{:keys [feature value]} :data}]
    (env/set-feature! (env/read-value value) [feature])
    (ok :feature feature :value value)))
