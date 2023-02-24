(ns lupapalvelu.notice-forms-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.notice-forms :as forms]
            [lupapalvelu.organization :as org]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]))

;; --------------------------------
;; Authority admin
;; --------------------------------

(defn- org-check [& checks]
  {:pre [(empty? (util/difference-as-kw checks
                                        [:r-scope? :form-enabled?]))]}
  (let [{:keys [r-scope? form-enabled?]} (zipmap checks (repeat true))]
    (fn [{data :data}]
      (when-let [organization (some-> (:organizationId data)
                                      (org/get-organization [:scope
                                                             :notice-forms
                                                             :handler-roles
                                                             :assignments-enabled]))]
        (let [checkers [[#(and r-scope?
                               (not (util/find-by-key :permitType "R"
                                                      (:scope organization))))
                         :error.no-R-scope]
                        [#(and form-enabled?
                               (:type data)
                               (not (get-in organization
                                            [:notice-forms (keyword (:type data)) :enabled])))
                         :error.notice-form-not-enabled]]]
          (->> checkers
               (reduce
                 (fn [error [check-fn err-msg]]
                   (or error (when (check-fn) (fail err-msg))))
                 nil)))))))



(defcommand toggle-organization-notice-form
  {:description      "Enable/disable construction/Terrain notice form for
  the organization"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [forms/ToggleFormParams]
   :pre-checks       [(org-check :r-scope?)]}
  [{data :data}]
  (forms/toggle-form data)
  (ok))

(defcommand set-organization-notice-form-text
  {:description      "Help text for an organization notice form"
   :permissions      [{:required [:organization/admin]}]
   :input-validators [forms/FormTextParams]
   :pre-checks       [(org-check :form-enabled?)]}
  [{data :data}]
  (forms/set-form-text data))

(defcommand toggle-organization-notice-form-integration
  {:description      "Toggle whether a form approval will generate
  corresponding KuntaGML message. Only supported for construction
  forms."
   :permissions      [{:required [:organization/admin]}]
   :input-validators [forms/ToggleFormParams
                      (partial action/select-parameters [:type] #{"construction"})]
   :pre-checks       [(org-check :form-enabled?)]}
  [{data :data}]
  (forms/toggle-form-integration data))

(defquery notice-forms-supported
  {:description      "Pseudo-query that fails if the organization does not
  support notice forms."
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [(org-check :r-scope?)]})

;; --------------------------------
;; Applicant
;; --------------------------------

(defn form-enabled [{:keys [data organization]}]
  (when-let [form-type (some-> data :type keyword)]
    (when-not (get-in @organization [:notice-forms form-type :enabled])
      (fail :error.notice-form-not-enabled))))

(defn type-wrapper [type pre-check-fn]
  (fn [command]
    (pre-check-fn (assoc-in command [:data :type] type))))

(def applicant-notice-permissions
  [{:context  {:application {:state      states/post-verdict-but-terminal
                             :permitType (partial util/=as-kw :R)}}
    :required [:application/edit]}])

(defquery new-notice-data
  {:description         "Convenience query for the new notice form data. This
  facilitates easier testing and more light-weight frontend."
   :parameters          [:id type]
   :optional-parameters [:lang]
   :permissions         applicant-notice-permissions
   :input-validators    [(partial action/non-blank-parameters [:id])
                         (partial action/parameters-matching-schema [:type] forms/form-type)]
   :pre-checks          [form-enabled
                         forms/pre-checks-by-type]}
  [command]
  (ok (forms/new-notice-data command type)))


(defn new-form-description [type]
  (format "Creates new %s (%s) notice form and the corresponding
          assignment (if enabled). The :parameters definition is
          incomplete, see `forms/NewNoticeFormParams` for details."
          type (i18n/localize :fi (util/kw-path :notice-forms type))))

(defn new-form-pre-checks [type]
  (mapv (partial type-wrapper type)
        [form-enabled
         forms/pre-checks-by-type
         forms/buildings-available]))

;; Due to the auth model, we need separate creation command for each
;; notice form type.

(defcommand new-construction-notice-form
  {:description      (new-form-description "construction")
   :parameters       [:id]
   :permissions      applicant-notice-permissions
   :input-validators [forms/NewNoticeFormParams]
   :pre-checks       (new-form-pre-checks "construction")}
  [command]
  (ok :form-id (:id (forms/new-notice-form command "construction"))))

(defcommand new-location-notice-form
  {:description      (new-form-description "location")
   :parameters       [:id]
   :permissions      applicant-notice-permissions
   :input-validators [forms/NewNoticeFormWithCustomerParams]
   :pre-checks       (new-form-pre-checks "location")}
  [command]
  (ok :form-id (:id (forms/new-notice-form command "location"))))

(defcommand new-terrain-notice-form
  {:description      (new-form-description "terrain")
   :parameters       [:id]
   :permissions      applicant-notice-permissions
   :input-validators [forms/NewNoticeFormWithCustomerParams]
   :pre-checks       (new-form-pre-checks "terrain")}
  [command]
  (ok :form-id (:id (forms/new-notice-form command "terrain"))))

(defn form-exists
  "Pre-check that fails if the notice form does not exist
  or (optionally) has a wrong state."
  [& form-states]
  (fn [{:keys [application data]}]
    (when-let [form-id (:formId data)]
      (if-let [form (util/find-by-id form-id
                                     (:notice-forms application))]
       (when-not (or (empty? form-states)
                     (util/includes-as-kw? (flatten form-states)
                                           (forms/form-state form)))
         (fail :error.notice-form-in-wrong-state))
       (fail :error.notice-form-not-found)))))

(defmethod action/allowed-actions-for-category :notice-forms
  [command]
  (action/allowed-actions-for-collection :notice-forms
                                         (fn [{app-id :id} {form-id :id}]
                                           {:id     app-id
                                            :formId form-id})
                                         command))

(defcommand delete-notice-form
  {:description      "Applicant can delete open forms (with their
  attachments). Assignments are updated accordingly."
   :parameters       [:id formId]
   :categories       #{:notice-forms}
   :permissions      applicant-notice-permissions
   :input-validators [(partial action/non-blank-parameters [:id :formId])]
   :pre-checks       [(form-exists :open)]}
  [command]
  (forms/delete-notice-form command formId)
  (ok))

(def authority-notice-permissions (update-in applicant-notice-permissions
                                             [0 :required]
                                             conj :document/approve))

(defcommand approve-notice-form
  {:description "Approve a notice form. There are two side-effects:

    - the form attachments are also approved.
    - the assignment targets corresponding to the form are removed."
   :parameters          [:id formId]
   :optional-parameters [info]
   :categories          #{:notice-forms}
   :permissions         authority-notice-permissions
   :input-validators    [(partial action/non-blank-parameters [:id :formId])]
   :pre-checks          [(form-exists :open :rejected)]}
  [command]
  (forms/set-notice-form-state command formId "ok" info)
  (ok))

(defcommand reject-notice-form
  {:description "Reject a notice form. There are two side-effects:

    - the form attachments' are also rejected. Reject note is only on
      the form, though.
    - the assignment targets corresponding to the form are removed.

   Note that a rejected form can be rejected again. The use cases
   include fixing a typo in the rejection note, for example."
   :parameters          [:id formId]
   :optional-parameters [info]
   :categories          #{:notice-forms}
   :permissions         authority-notice-permissions
   :input-validators    [(partial action/non-blank-parameters [:id :formId])]
   :pre-checks          [(form-exists)]}
  [command]
  (forms/set-notice-form-state command formId "rejected" info)
  (ok))

(defquery notice-forms
  {:description      "Convenience query for application notice forms."
   :parameters       [:id lang]
   :permissions      (assoc-in applicant-notice-permissions
                               [0 :required] [:application/read])
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/supported-lang :lang)]}
  [command]
  (ok :noticeForms (forms/notice-forms command lang)))
