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

;; ----------------------------------
;; Pre-checks for verdict templates
;; ----------------------------------
(defn- verdict-template-exists [{data :data :as command}]
  (when-not (matti/verdict-template (command->organization command)
                                    (:template-id data))
    (fail :error.verdict-template-not-found)))

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

;; ----------------------------------
;; Verdict template API
;; ----------------------------------

(defcommand new-verdict-template
  {:description "Creates new empty template. Returns template id, name and draft."
   :user-roles  #{:authorityAdmin}}
  [{:keys [created user lang]}]
  (ok (matti/new-verdict-template (usr/authority-admins-organization-id user)
                                   created
                                   lang)))

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
                                  created)
  (ok :published created))

(defquery verdict-templates
  {:description "Id, name, modified, published and deleted maps for
  every verdict template."
   :user-roles #{:authorityAdmin}}
  [command]
  (ok :verdict-templates (->> (command->organization command)
                              :verdict-templates
                              :templates
                              (map matti/verdict-template-summary))))
(defquery verdict-template
  {:description "Verdict template summary plus draft data. The
  template must be editable."
   :user-roles #{:authorityAdmin}
   :parameters [template-id]
   :input-validators [verdict-template-editable]}
  [command]
  (let [template (matti/verdict-template (command->organization command)
                                         template-id)]
    (ok (assoc (matti/verdict-template-summary template)
               :draft (:draft template)))))

(defcommand toggle-delete-verdict-template
  {:description "Toggle template's deletion status"
   :user-roles #{:authorityAdmin}
   :parameters [template-id delete]
   :input-validators [verdict-template-exists
                      (partial action/boolean-parameters [:delete])]}
  [command]
  (matti/set-deleted (command->organization command)
                     template-id
                     delete))

(defcommand copy-verdict-template
  {:description "Makes copy of the template. The new template does not
  have any published versions. In other words, the draft of the
  original is copied."
   :user-roles #{:authorityAdmin}
   :parameters [template-id]
   :input-validators [verdict-template-exists]}
  [{:keys [created lang] :as command}]
  (ok (matti/copy-verdict-template (command->organization command)
                                    template-id
                                    created
                                    lang)))

;; ----------------------------------
;; Verdict template settings API
;; ----------------------------------

(defquery verdict-template-settings
  {:description "Settings matching the id or empty response."
   :user-roles #{:authorityAdmin}
   :parameters [category]
   :input-validators [(partial action/non-blank-parameters [:category])]}
  [command]
  (when-let [settings (matti/settings (command->organization command)
                                      category)]
    (ok :settings settings)))

(defcommand save-verdict-template-settings-value
  {:description      "Incremental save support for verdict template
  settings. Returns modified timestamp. Creates settings if needed."
   :user-roles       #{:authorityAdmin}
   :parameters       [category path value]
   :input-validators [(partial action/vector-parameters [:path])
                      (partial action/non-blank-parameters [:category])]}
  [{:keys [created] :as command}]
  (matti/save-settings-value (command->organization command)
                             category
                             created
                             path
                             value)
  (ok :modified created))
