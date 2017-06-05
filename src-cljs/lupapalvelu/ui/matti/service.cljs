(ns lupapalvelu.ui.matti.service
  (:require [lupapalvelu.ui.common :as common]
            [clojure.walk :as walk]))

(defonce schemas (atom {}))
;; List of id, name, published maps.
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

(defn publish-template [template]
  (let [data (walk/postwalk #(if (map? %)
                               (dissoc % :_meta)
                               %) template)]
    ))

(defn save-draft-value [template-id path value callback]
  (common/command {:command "save-verdict-template-draft-value"
                   :success callback}
                  :template-id template-id
                  :path path
                  :value value))

(defn fetch-template-draft [template-id callback]
  (common/query "verdict-template-draft"
                callback
                :template-id template-id))

(defn new-template [callback]
  (common/command {:command "new-verdict-template"
                   :success (fn [response]
                              (fetch-template-list)
                              (callback response))}))

(defn set-template-name [template-id name callback]
  (common/command {:command "set-verdict-template-name"
                   :success (fn [response]
                              (fetch-template-list)
                              (callback response))}
                  :template-id template-id
                  :name name))
