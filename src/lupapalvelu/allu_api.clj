(ns lupapalvelu.allu-api
  "Allu-provided information for the fronted"
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.allu :as allu]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [schema.core :as sc]))

(defn- kind-supported
  "Input validator for kind parameter."
  [{data :data}]
  (when (sc/check allu/kind-schema (some-> data :kind keyword))
    (fail :error.allu-bad-kind)))

(defn- drawing-id-ok
  "Input validator for drawingId"
  [{data :data}]
  (when (sc/check allu/drawing-id-schema (:drawingId data))))

(defquery allu-sites
  {:description      "Name, id, source mapss of the Allu sites (drawings)
  for the given kind. The sites already present as drawings in the
  application are excluded."
   :user-roles       #{:applicant :authority}
   :parameters       [:id kind]
   :input-validators [(partial action/non-blank-parameters [:id])
                      kind-supported]
   :states           states/pre-sent-application-states
   :pre-checks       [(partial permit/valid-permit-types {:A []})]}
  [{:keys [application]}]
  (ok :sites (allu/site-list application kind)))

(defn- update-application-or-fail [command updates-or-error]
  (cond
    (nil? updates-or-error) (ok)

    (map? updates-or-error)
    (action/update-application command
                               updates-or-error)

    :else (fail updates-or-error)))

(defcommand add-allu-drawing
  {:description      "Adds Allu site as a drawing into the application."
   :user-roles       #{:applicant :authority}
   :parameters       [:id kind siteId]
   :input-validators [(partial action/non-blank-parameters [:id])
                      kind-supported
                      (partial action/positive-integer-parameters [:siteId])]
   :states           states/pre-sent-application-states
   :pre-checks       [(partial permit/valid-permit-types {:A []})]}
  [{:keys [application] :as command}]
  (update-application-or-fail command
                              (allu/add-allu-drawing application
                                                     kind
                                                     siteId)))

(defcommand remove-application-drawing
  {:description      "Removes application drawing with the given id."
   :user-roles       #{:applicant :authority}
   :parameters       [:id drawingId]
   :input-validators [(partial action/non-blank-parameters [:id])
                      drawing-id-ok]
   :states           states/pre-sent-application-states
   :pre-checks       [(partial permit/valid-permit-types {:A []})]}
  [{:keys [application] :as command}]
  (update-application-or-fail command
                              (allu/remove-drawing application drawingId)))
