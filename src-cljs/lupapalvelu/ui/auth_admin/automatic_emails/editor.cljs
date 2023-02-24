(ns lupapalvelu.ui.auth-admin.automatic-emails.editor
  (:require [clojure.walk :refer [keywordize-keys]]
            [rum.core :as rum]
            [goog.object :as googo]
            [sade.shared-util :refer [find-by-key]]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.components :refer [autocomplete-tags text-edit textarea-edit icon-button]]
            [lupapalvelu.ui.common :refer [loc reset-if-needed! query command show-dialog]]))

;; The component args are stored here; see the end of the file
(defonce args (atom {}))

(def api-command-strings
  "Used for invoking commands and checking auths for them"
  {:add     "add-organization-automatic-email-template"
   :remove  "remove-organization-automatic-email-template"
   :save    "save-organization-automatic-email-template-field"})

(defn- disabled?
  "Returns true if the action is not allowed by auth"
  [auth-model action-key]
  (not (auth/ok? auth-model (get api-command-strings action-key))))

(defn get-email-templates-from-db!
  "Updates the argument atom with the up-to-date templates in the backend"
  [org-id]
  (let [add-test-id #(assoc %1 :test-id (str "automatic-email-template-" %2))]
    (query "get-organization-automatic-email-templates"
           #(swap! args assoc :email-templates (mapv add-test-id (:templates %) (range)))
           :organizationId org-id)))

(defn save-email-template-field!
  "Saves the given field in the database"
  [org-id email-id field value]
  (command (:save api-command-strings)
           #()
           :organizationId org-id
           :emailId email-id
           :field field
           :value (if (vector? value) (mapv :value value) value)))

(defn add-email-template!
  [org-id]
  (command (:add api-command-strings)
           #(get-email-templates-from-db! org-id)
           :organizationId org-id))

(defn remove-email-template!
  "Removes the given email from the database with a confirmation pop-up"
  [org-id email-id]
  (show-dialog {:ltitle   "areyousure"
                :ltext    "remove"
                :type     :yes-no
                :callback #(command (:remove api-command-strings)
                                    (partial get-email-templates-from-db! org-id)
                                    :organizationId org-id
                                    :emailId email-id)}))

(defn- get-full-tags
  "Matches the tags from the database to the options in the autocomplete.
   The values coming from the database are strings, tags are maps."
  [values tags]
  (mapv #(if-let [tag (find-by-key :value % tags)]
           tag
           {:value % :text %})
        values))

(rum/defc automatic-email-item < rum/reactive
  "The main component definition.
   Gets the database values for the form (enriched with organization and email ids) as a parameter."
  [{:keys                                                                                             [title contents org-id operations states parties test-id auth-model
                                                         operations-items states-items parties-items] :as params}]
  (let [email-id   (:id params)
        save-fn    (fn [field] #(save-email-template-field! org-id email-id field %))
        ac-options (fn [items k] {:items       items
                                  :disabled?   (disabled? auth-model :save)
                                  :callback    (save-fn k)
                                  :test-id     (str test-id "-" (name k))
                                  :placeholder (loc "choose")})]
    [:section
     [:table.admin-settings.form-grid
      [:thead [:tr [:th {:col-span 2} (str title)]]]
      [:tbody
       [:tr
        [:td.sub-table-container
         [:table
          [:tbody
           [:tr
            [:td
             [:label (loc "auth-admin.automatic-emails.title")]
             (text-edit title {:callback (save-fn :title)
                               :disabled (disabled? auth-model :save)
                               :test-id  (str test-id "-title")})]]
           [:tr
            [:td {:row-span 2}
             [:label (loc "auth-admin.automatic-emails.contents")]
             (textarea-edit contents {:callback (save-fn :contents)
                                      :disabled (disabled? auth-model :save)
                                      :test-id  (str test-id "-contents")})]]]]]
        [:td.sub-table-container
         [:table
          [:tbody
           [:tr
            [:td
             [:label (loc "auth-admin.automatic-emails.primary-operation")]
             (autocomplete-tags operations
                                (ac-options operations-items :operations))]]
           [:tr
            [:td
             [:label (loc "auth-admin.automatic-emails.state")]
             (autocomplete-tags states
                                (ac-options states-items :states))]]
           [:tr
            [:td
             [:label (loc "auth-admin.automatic-emails.parties")]
             (autocomplete-tags parties
                                (ac-options parties-items :parties))]]]]]]
       [:tr
        [:td.buttons {:col-span 2}
         (icon-button {:text-loc  "remove"
                       :test-id   (str test-id "-remove")
                       :disabled? (disabled? auth-model :remove)
                       :on-click  #(remove-email-template! org-id email-id)
                       :icon      :lupicon-remove
                       :class     "secondary"})]]]]]))


(rum/defc automatic-email-editor < rum/reactive
  "The main component definition for the automatic email page"
  [_]
  (let [arguments (rum/react args)] ; Subscribes to the argument atom
    [:div
     [:h2 (loc "auth-admin.automatic-emails")]
     (->> (:email-templates arguments)
          (map (partial merge arguments))  ; Enriches template info with org-id, operation-items, etc
          (map automatic-email-item)
          (into [:section]))
     (icon-button {:text-loc    "auth-admin.automatic-emails.add"
                   :test-id     "automatic-email-template-add"
                   :disabled?   (disabled? (:auth-model arguments) :add)
                   :on-click    #(add-email-template! (:org-id arguments))
                   :icon        :lupicon-circle-plus
                   :class       "positive"})]))

(defn mount-component []
  "Rum component method: called when the component is added to the page"
  (rum/mount (automatic-email-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params] ; params = component parameters from template
  "Rum component method: called when the component starts up"
  (let [auth-model      (googo/get params "authModel")
        organization-id ((googo/get params "organizationId"))
        get-param-vec   #(-> ((googo/get params %)) js->clj keywordize-keys)]
    (swap! args assoc
           :auth-model        auth-model
           :org-id            organization-id
           :email-templates   [] ; Updated via query below
           :operations-items  (get-param-vec "operationsItems")
           :states-items      [] ; Retrieved via query below
           :parties-items     (get-param-vec "partiesItems")
           :dom-id (name domId))
    (when (auth/ok? auth-model :get-organization-automatic-email-templates)
      (get-email-templates-from-db! organization-id)
      (query "get-organization-application-states"
             #(swap! args assoc :states-items (:states %))
             :organizationId organization-id
             :lang (js/loc.getCurrentLanguage)))
    (mount-component)))
