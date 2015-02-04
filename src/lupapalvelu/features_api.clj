(ns lupapalvelu.features-api
  (:require [sade.env :as env]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.document.tools :as tools]))

(defquery "features"
   {:description "returns list of features"
    :roles [:anonymous]}
   [_] (ok :features (filter second (tools/path-vals (env/features)))))

(when (env/dev-mode?)
  (defquery "set-feature"
    {:description "Sets a feature flag to given value"
     :parameters [:feature :value]
     :roles [:anonymous]}
    [{{:keys [feature value]} :data}]
    (env/set-feature! (env/read-value value) [feature])
    (ok :feature feature :value value)))
