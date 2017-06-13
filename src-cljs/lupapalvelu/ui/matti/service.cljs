(ns lupapalvelu.ui.matti.service
  (:require [clojure.walk :as walk]
            [lupapalvelu.ui.common :as common]))

(defonce schemas (atom {}))
;; List of id, name, published, deleted maps.
(defonce template-list (atom []))

(defn fetch-schemas []
  (common/query "schemas"
                #(reset! schemas (:schemas %))))
(defn schema
  ([schema-name version]
   (get-in @schemas [(-> version str keyword) (keyword schema-name)]))
  ([schema-name]
   (schema schema-name 1)))

(defn fetch-template-list []
  (common/query "verdict-templates"
                #(reset! template-list (:verdict-templates %))))

(defn- list-update-response [callback]
  (fn [response]
    (fetch-template-list)
    (callback response)))

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

(defn new-template [callback]
  (common/command {:command "new-verdict-template"
                   :success (list-update-response callback)}))

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
