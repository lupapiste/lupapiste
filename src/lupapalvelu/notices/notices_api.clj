(ns lupapalvelu.notices.notices-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.notices.core :as notices]
            [lupapalvelu.notices.pdf :as pdf]
            [lupapalvelu.notices.schemas :refer [ResolutionState]]
            [lupapalvelu.notices.util :as notices-util]
            [lupapalvelu.quill :as quill]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.db :as stats-db]
            [sade.core :refer [ok]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(defcommand demolition-notice-to-application
  {:description      "Converts purkamisilmoitus into purkaminen application."
   :permissions      [{:required [:application/edit]
                       :context  {:application {:state     states/pre-sent-application-states
                                                :documents notices-util/purkamisilmoitus-doc}}}]
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :on-success       [stats-db/post-reset-processing]}
  [command]
  ;; We do not check the checkbox in the pre-checks, since that could mess up the auth
  ;; model in the frontend.
  (or (notices-util/is-building-demolition command)
      (notices/purkamisilmoitus->purkaminen command) ;; Always nil
      (ok)))

(defquery notice-resolution
  {:description      "The current notice resolution (or nil). Can be used as guard for showing
  the notices tab. The applicants see the tab only when when there is a resolution to be
  shown."
   :permissions      [{:required [:application/read]
                       :context  {:application {:state #{:ready}}}}
                      {:required [:application/edit-notices]
                       :context  {:application {:state states/post-submitted-states}}}]
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [notices-util/is-notice]}
  [command]
  (util/assoc-when (ok) :resolution (notices/resolution command :frontend)))

(defquery poll-notice-resolution
  {:description      "When a notice resolution is published, the
  corresponding attachment is generated and this can take some time. This query returns
  either `:resolution` (can be nil) or `:waiting`. The polling is long-running in a sense
  that it returns after success or (internal) timeout."
   :permissions      [{:required [:application/read]
                       :context  {:application {:state #{:ready}}}}]
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [notices-util/published-resolution]}
  [command]
  (ok (notices/poll-resolution id)))

(defcommand save-notice-resolution
  {:description      "Saves a draft notice resolution. Returns `modified` timestamp."
   :permissions      [{:required [:application/edit-notices]
                       :context  {:application {:state states/post-submitted-states}}}]
   :input-validators [{:id ssc/ApplicationId
                       (sc/optional-key :state) ResolutionState
                       (sc/optional-key :text) sc/Any}
                      (quill/input-validator :text true)]
   :pre-checks       [notices-util/draft-resolution]}
  [{:keys [created] :as command}]
  (notices/save-resolution command)
  (ok :modified created))

(defcommand publish-notice-resolution
  {:description      "Publish notice resolution. Application state is changed to `ready` and
  the notice resolution attachment is created. Returns the published resolution"
   :permissions      [{:required [:application/edit-notices]
                       :context  {:application {:state states/post-submitted-states}}}]
   :parameters       [:id :text :state lang]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/supported-lang :lang)
                      (quill/input-validator :text)
                      (partial action/parameters-matching-schema [:state] ResolutionState)]
   :pre-checks       [notices-util/draft-resolution]
   :notified         true
   :on-success       (action/notify :application-state-change)}
  [command]
  (let [command    (assoc command :lang lang)]
    (notices/publish-resolution command lang)
    (pdf/start-generation-job command)
    (ok :resolution (notices/resolution command :refresh :frontend))))

(defcommand delete-published-notice-resolution
  {:description      "Delete published notice resolution. Resolution attachment is also deleted
  and the application state is rewound to `submitted`."
   :permissions      [{:required [:application/edit-notices]
                       :context  {:application {:state #{:ready}}}}]
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [notices-util/published-resolution]
   :notified         true
   :on-success       (action/notify :application-state-change)}
  [command]
  (notices/delete-resolution command)
  (ok))

(defcommand delete-draft-notice-resolution
  {:description "Delete draft notice resolution."
   :permissions      [{:required [:application/edit-notices]
                       :context  {:application {:state states/post-submitted-states}}}]
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [notices-util/draft-resolution]}
  [command]
  (notices/delete-resolution command)
  (ok))
