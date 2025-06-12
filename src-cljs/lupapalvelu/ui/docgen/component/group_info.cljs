(ns lupapalvelu.ui.docgen.component.group-info
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.help-toggle :as help-toggle]
            [lupapalvelu.ui.docgen.component.warnings :as warnings]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.misc]
            [sade.util :as util]))

(defn- make-help-content
  "See `helpHtml` in `docgen-group-info-model.js`."
  [{:keys [doc-i18n-name path body]}]
  (when-let [children (some->> body
                               (keep (fn [schema]
                                       (let [path      (conj path (keyword (:name schema)))
                                             title-key (docgen-loc/loc-key-help-title doc-i18n-name path schema)
                                             help-key  (docgen-loc/loc-key-help-label doc-i18n-name path schema)]
                                         (when (common/loc-has-term? help-key)
                                           ^{:key help-key}
                                           [:li
                                            [:strong (common/loc title-key)]
                                            [:span common/nbsp]
                                            ;; The `.help` might contain HTML
                                            [common/loc-html-reagent :span help-key]]))))
                               doall
                               seq)]
    [:ul.help-items children]))

(defn group-info
  [{:keys [doc-id path body schema]}]
  (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
        group-title   (common/loc (docgen-loc/loc-key-group-label doc-i18n-name path schema))
        validated?    (empty? (<sub [::validation/field-validation doc-id path]))
        help-content  (make-help-content (util/mmap doc-i18n-name path body))]
    [:div.docgen-group-info
     (when (and group-title validated?)
       [:span.group-title
        {:class (when help-content
                  "help-adjacent-title")}
        group-title])
     (when (and group-title (not validated?))
       [:span.group-title.tip
        {:class         (when (seq help-content)
                          "help-adjacent-title")
         :aria-required true}
        group-title])
     [help-toggle/help-toggle {:help-content help-content}]
     [warnings/show-warnings (util/mmap doc-id path body doc-i18n-name)]]))
