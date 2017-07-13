(ns lupapalvelu.ui.matti.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.state :as state]))

(defn fetch-schemas []
  (common/query "schemas"
                #(reset! state/schemas (:schemas %))))
(defn schema
  ([schema-name version]
   (get-in @state/schemas [(-> version str keyword) (keyword schema-name)]))
  ([schema-name]
   (schema schema-name 1)))

(defn fetch-template-list []
  (common/query "verdict-templates"
                #(reset! state/template-list (:verdict-templates %))))

(defn- list-update-response [callback]
  (fn [response]
    (fetch-template-list)
    (callback response)))

(defn fetch-categories [callback]
  (common/query "verdict-template-categories"
                #(do
                   (reset! state/categories (:categories %))
                   (callback @state/categories))))

(defn publish-template [template-id callback]
  (common/command {:command "publish-verdict-template"
                   :success (list-update-response callback)}
                  :template-id template-id))

(defn save-draft-value [template-id path value callback]
  (common/command {:command "save-verdict-template-draft-value"
                   :success callback}
                  :template-id template-id
                  :path path
                  :value value))

(defn fetch-template [template-id callback]
  (common/query "verdict-template"
                callback
                :template-id template-id))

(defn new-template [category callback]
  (common/command {:command "new-verdict-template"
                   :success (list-update-response callback)}
                  :category category))

(defn set-template-name [template-id name callback]
  (common/command {:command "set-verdict-template-name"
                   :success (list-update-response callback)}
                  :template-id template-id
                  :name name))

(defn toggle-delete-template [template-id delete callback]
  (common/command {:command "toggle-delete-verdict-template"
                   :success (list-update-response callback)}
                  :template-id template-id
                  :delete delete))

(defn copy-template [template-id callback]
  (common/command {:command "copy-verdict-template"
                   :success (list-update-response callback)}
                  :template-id template-id))

(defn settings [category callback]
  (common/query "verdict-template-settings"
                callback
                :category category))

(defn save-settings-value [category path value callback]
  (common/command {:command "save-verdict-template-settings-value"
                   :success callback}
                  :category category
                  :path path
                  :value value))

(defn generics [generic-type category callback]
  (common/query (js/sprintf "verdict-template-%ss"
                            (name generic-type))
                callback
                :category category))

(defn new-generic [generic-type category callback]
  (common/command {:command (str "add-verdict-template-" (name generic-type))
                   :success callback}
                  :category category))

(defn update-generic [generic-type gen-id callback & updates]
  (apply (partial common/command {:command (str "update-verdict-template-"
                                                (name generic-type))
                                  :success (fn [{:keys [modified] :as response}]
                                             (when (= (-> response generic-type :category)
                                                      @state/current-category)
                                               (swap! state/settings
                                                      #(assoc % :modified modified)))
                                             (callback response))}
                  (keyword (str (name generic-type) "-id")) gen-id)
         updates))
