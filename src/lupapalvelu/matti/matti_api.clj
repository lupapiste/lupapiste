(ns lupapalvelu.matti.matti-api
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.matti.matti :as matti]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))


(defn- command->organization [{user :user}]
  (usr/authority-admins-organization user))

(defn- verdict-template-editable
  "Template exists and is editable (not deleted)."
  [{data :data :as command}]
  (if-let [template (matti/verdict-template (command->organization command)
                                            (:template-id data))]
    (when (:deleted template)
      (fail :error.verdict-template-deleted))
    (fail :error.verdict-template-not-found)))

(defn- verdict-template-has-name [{data :data :as command}]
  (when-not (-> (matti/verdict-template (command->organization command)
                                        (:template-id data))
                :name ss/not-blank?)
    (fail :error.verdict-template-name-missing)))

(defcommand new-verdict-template
  {:description "Creates new empty template. Returns template id, name and draft."
   :user-roles  #{:authorityAdmin}}
  [{:keys [created user lang]}]
  (let [{:keys [id draft name]}
        (matti/new-verdict-template (usr/authority-admins-organization-id user)
                                    created
                                    lang)]
    (ok :id id :name name :draft draft)))

(defcommand set-verdict-template-name
  {:parameters [template-id name]
   :input-validators [(partial action/non-blank-parameters [:name])
                      verdict-template-editable]
   :user-roles #{:authorityAdmin}}
  [{created :created :as command}]
  (matti/set-name (command->organization command)
                  template-id
                  created
                  name)
  (ok :modified created))

(defcommand save-verdict-template-draft-value
  {:description      "Incremental save support for verdict template
  drafts. Returns modified timestamp."
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id path value]
   :input-validators [(partial action/vector-parameters [:path])
                      verdict-template-editable]}
  [{:keys [created] :as command}]
  (matti/save-draft-value (command->organization command)
                          template-id
                          created
                          path
                          value)
  (ok :modified created))

(defcommand publish-verdict-template
  {:description "Creates new verdict template version."
   :user-roles #{:authorityAdmin}
   :parameters [template-id]
   :input-validators [verdict-template-editable
                      verdict-template-has-name]}
  [{:keys [created user user-organizations] :as command}]
  (matti/publish-verdict-template (command->organization command)
                                  template-id
                                  created))
(defquery verdict-templates
  {:description "Id, name, published maps for every editable verdict
  template."
   :user-roles #{:authorityAdmin :authority}}
  [command]
  (ok :verdict-templates (->> (command->organization command)
                              :verdict-templates
                              (remove :deleted)
                              (map (fn [{:keys [id modified name versions]}]
                                     {:id id
                                      :name name
                                      :published (-> versions last :published)})))))
(defquery verdict-template-draft
  {:description "Draft data for the given template. Template must be
  editable."
   :user-roles #{:authorityAdmin}
   :parameters [template-id]
   :input-validators [verdict-template-editable]}
  [command]
  (ok :draft (:draft (matti/verdict-template (command->organization command)
                                             template-id))))
