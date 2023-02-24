(ns lupapalvelu.allu-api
  "Allu-provided information for the fronted"
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.allu :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [ApplicationType ApplicationKind]]
            [lupapalvelu.drawing :refer [DrawingId]]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- application-kind-supported
  "Input validator for kind parameter."
  [{data :data}]
  (when (some->> data :kind keyword (sc/check ApplicationKind))
    (fail :error.allu-bad-kind)))

(defn- application-type-supported
  "Input validator for type parameter."
  [{data :data}]
  (when (some->> data :type keyword (sc/check ApplicationType))
    (fail :error.allu-bad-application-type)))

(defn- drawing-id-ok
  "Input validator for drawingId"
  [{data :data}]
  (when (sc/check DrawingId (:drawingId data))))

(def- drawing-permissions [{:context  {:application {:state      #{:draft}
                                                     :permitType #(util/=as-kw :A %)}}
                            :required [:application/edit-draft :application/edit-drawings]}
                           {:context  {:application {:state      states/pre-sent-application-states
                                                     :permitType #(util/=as-kw :A %)}}
                            :required [:application/edit-drawings]}])

(defquery allu-sites
  {:description         "Name, id, source maps of the Allu
                        sites (drawings) for the given kind (or every kind). The sites
                        already present as drawings in the application are excluded."
   :parameters          [:id]
   :optional-parameters [kind]
   :input-validators    [(partial action/non-blank-parameters [:id])
                         application-kind-supported]
   :permissions         drawing-permissions}
  [{:keys [application]}]
  (ok :sites (allu/site-list application kind)))

(defn- update-application-or-fail [command updates-or-error]
  (cond
    (nil? updates-or-error) (ok)
    (map? updates-or-error) (action/update-application command updates-or-error)
    :else (fail updates-or-error)))

(defcommand filter-allu-drawings
  {:description      "Remove application Allu drawings that do not match
  the given kind."
   :parameters       [:id kind]
   :input-validators [(partial action/non-blank-parameters [:id :kind])
                      application-kind-supported]
   :permissions      drawing-permissions}
  [{:keys [application] :as command}]
  (if-let [updates (allu/filter-allu-drawings application kind)]
    (update-application-or-fail command updates)
    (ok :text "no changes")))

(defcommand add-allu-drawing
  {:description      "Adds Allu site as a drawing into the application."
   :parameters       [:id kind siteId]
   :input-validators [(partial action/non-blank-parameters [:id])
                      application-kind-supported
                      (partial action/positive-integer-parameters [:siteId])]
   :permissions      drawing-permissions}
  [{:keys [application] :as command}]
  (update-application-or-fail command
                              (allu/add-allu-drawing application
                                                     kind
                                                     siteId)))

(defcommand remove-application-drawing
  {:description      "Removes application drawing with the given id."
   :parameters       [:id drawingId]
   :input-validators [(partial action/non-blank-parameters [:id])
                      drawing-id-ok]
   :permissions      drawing-permissions}
  [{:keys [application] :as command}]
  (update-application-or-fail command
                              (allu/remove-drawing application drawingId)))

(defquery application-kinds
  {:description      "Returns supported application kinds for the given
                     application type."
   :parameters       [:id type]
   :input-validators [(partial action/non-blank-parameters [:id])
                      application-type-supported]
   :permissions      [{:context  {:application {:state      #{:draft}
                                                :permitType #(util/=as-kw :A %)}}
                       :required [:application/edit-draft]}
                      {:context  {:application {:state      states/pre-sent-application-states
                                                :permitType #(util/=as-kw :A %)}}
                       :required [:application/edit]}]}
  [_]
  (ok :kinds (allu/application-kinds (keyword type))))
