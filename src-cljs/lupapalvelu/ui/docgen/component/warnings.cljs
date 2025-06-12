(ns lupapalvelu.ui.docgen.component.warnings
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [sade.util :as util]))

(defn- warning
  [{:keys [id label error]}]
  ^{:key (str id "-" error)}
  [:div
   {:id         id
    :role       "alert"
    :aria-label (str label ": " (common/loc error))}
   [:span.warning
    {:aria-hidden true}
    ;; The field name (label) might contain HTMl
    [:strong (common/loc-html-reagent :span label) ": "]
    [:span (common/loc error)]]])

(defn warnings
  [{:keys [warnings]}]
  [:div.warnings (->> warnings
                      (map warning)
                      doall)])

(defn resolve-warnings
  "Helper function for resolving warnings for fields/schemas"
  [{:keys [doc-id doc-i18n-name]} paths-and-schemas]
  (->> paths-and-schemas
       (mapcat (fn [{:keys [path schema]}]
                 (when-let [warns (<sub [::validation/field-warnings doc-id path])]
                   (->> warns
                        (map (fn [warn]
                               ;; enrich the warning with data from the schema field
                               (assoc warn
                                      ;; Just like in legacy docgen, multiple warnings on the same field get the same id..
                                      :id (path/path->warning-message-id path doc-id)
                                      :label (docgen-loc/loc-key doc-i18n-name path schema)
                                      :path path)))
                        (into [])))))
       (into [])
       not-empty))

(defn body->warnings
  [{:keys [doc-i18n-name doc-id path body]}]
  (->> body
       (map (fn [schema]
              {:schema schema
               :path   (conj path (keyword (:name schema)))}))
       (resolve-warnings (util/mmap doc-id doc-i18n-name))))

(defn show-warnings
  [args]
  (when-let [warns (body->warnings args)]
    [warnings {:warnings warns}]))
