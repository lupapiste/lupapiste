(ns lupapalvelu.ui.docgen.field.construction-waste-report
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.button :as button]
            [lupapalvelu.ui.docgen.component.warnings :as warnings]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [schema.core :as sc]))

(rf/reg-event-fx ::append-row-clicked
  (fn [{:keys [db]} [_ args]]
    (when-let [new-db (docs/add-group-child db args)]
      {:db new-db})))

(rf/reg-event-fx ::remove-row-clicked
  (fn [_ [_ args]]
    {:dialog/show {:type   :yes-no
                   :ltitle :remove
                   :ltext  :document.delete.table.subGroup.message
                   :event  [::remove-row args]}}))

(rf/reg-event-fx ::remove-row
  (fn [{:keys [db]} [_ {:keys [app-id doc-id path] :as args}]]
    (let [{remove-cmd :remove
           coll       :collection} (docs/get-document-commands db doc-id)]
      {:action/command {:name    remove-cmd
                        :params  {:id         app-id
                                  :doc        doc-id
                                  :collection coll
                                  :path       (path/stringify-path path)}
                        :success [::remove-field.success args]}})))

(rf/reg-event-fx ::remove-field.success
  (fn [_ [_ _response {:keys [app-id]}]]
    {:application/load app-id}))

(defn- render-row
  [{:keys [args doc-i18n-name group-id row-id schema path]}]
  (let [{:keys [app-id doc-id render-fn]} args
        {:keys [body]}             schema
        path                       (conj path row-id)
        args                       (-> args
                                       (dissoc :schema)
                                       (assoc :path path))
        removable?                 (di/group-removable? (assoc args :schema schema))
        warnings                   (warnings/show-warnings {:doc-id        doc-id
                                                            :doc-i18n-name doc-i18n-name
                                                            :path          path
                                                            :body          body})]
    [:<>
     (when warnings
       [:tr
        [:td {:colSpan (inc (count body))}
         warnings]])
     [:tr
      {:id (str group-id "-row-" row-id)}

      (for [col-schema body]
        (let [subschema (-> col-schema
                            (docgen-loc/apply-parent-i18nkey schema)
                            (update :label boolean))]
          ^{:key (:name col-schema)}
          [:td
           (when (:__renderElement col-schema)
             [:span.form-entry.form-input
              ;; JS version did override all to use docgen-{type} fields, likely using
              ;; the cljs prefer-ko is quite close to that.
              [render-fn [subschema] (assoc args :prefer-ko-component? true)]])]))

      [:td.action-column
       (when removable?
         [:button.secondary.icon-only.no-border
          {:on-click (fn [_]
                       (>evt [::remove-row-clicked {:app-id app-id
                                                    :doc-id doc-id
                                                    :path   path}]))
           :aria-label (common/loc :remove)}
          [:i.lupicon-remove {:aria-hidden true}]])]]]))

(sc/defn render
  [{:keys [app-id doc-id schema path override-auth-source disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [group-help]} schema
        group-id             (path/path->table-id path doc-id)
        table-class          (str (name (peek path)) "-table")
        doc-i18n-name        (<sub [::docs/i18n-name doc-id])
        update-command       (:update (<sub [::docs/commands doc-id]))
        updateable?          (<sub [::auth/auth? doc-id update-command override-auth-source])
        group-label          (docgen-loc/loc-key-group-label doc-i18n-name path schema)

        {:keys [suunniteltuJate suunnittelematonJate]}
        (into {} (map (juxt (comp keyword :name) identity) (:body schema)))

        groups (mapv (fn [schema]
                       (let [path (conj path (keyword (:name schema)))]
                         {:key (:name schema)
                          :path path
                          :row-ids (<sub [::docs/field-child-keys doc-id path])
                          ;; See self.buildSubSchemas.
                          ;; Take list of "fields" from suunniteltuJate
                          ;; but then use schema definition for the same field
                          ;; from the group schema (which is either suunniteltu or suunnittelamatonJate).
                          :schema (assoc schema
                                         :body (let [field-name->sub-schema (into {} (map (juxt :name identity) (:body schema)))]
                                                 (mapv (fn [schema]
                                                         (let [group-schema (get field-name->sub-schema (:name schema))]
                                                           ;; NOTE: group-schema could be nil, so ensure name and i18nkey exist.
                                                           (assoc group-schema
                                                                  :name (:name schema)
                                                                  ;; JS version did override component for each field, I don't think we need this?
                                                                  ;; :uicomponent (str "docgen-" (:type schema))
                                                                  :i18nkey (:i18nkey schema)
                                                                  ;; Magic property for render-row to ignore rendering fields that don't exist for this schema.
                                                                  ;; I.e., suunitteltujaJate->arvioituMaara doesn't exist for suunnittelamatonJate.
                                                                  :__renderElement (some? group-schema))))
                                                       (:body suunniteltuJate))))}))
                     [suunniteltuJate
                      suunnittelematonJate])]
    [(di/container schema)
     [:div
      {:class "form-table"}
      [:label.form-label.form-label-table
       (common/loc group-label)]
      (when group-help
        [:span.group-help-text
         (common/loc group-help)])

      [:div.form-table--waste.waste-report-table
       [:table
        {:class table-class}
        [:thead
         [:tr
          (for [col-schema (:body suunniteltuJate)
                :let [path (conj path :suunniteltuJate)]]
            ^{:key (:name col-schema)}
            [:th
             (common/loc (docgen-loc/loc-key doc-i18n-name (conj path (:name col-schema)) col-schema))])
          [:th
           (when (di/group-removable? {:disabled? disabled?
                                       :doc-id doc-id
                                       :schema suunnittelematonJate})
             (common/loc "remove"))]]]

        [:tbody
         (for [group groups]
           [:<>
            {:key (:key group)}
            (for [row-id (:row-ids group)]
              ^{:key row-id}
              [render-row
               {:row-id        row-id
                :args          args
                :schema        (:schema group)
                :path          (:path group)
                :group-id      group-id
                :doc-i18n-name doc-i18n-name}])])]]]]

     (when updateable?
       (let [;; NOTE: Juho: I would prefer to calculate the next-idx in the event handler using db value.
             path     (conj path :suunnittelematonJate)
             next-idx (<sub [::docs/next-child-id doc-id path])]
         [:div.button-group
          [:span
           [button/button {:id       (str group-id "-append-button")
                           :class    "secondary"
                           :icon     :lupicon-circle-plus
                           :label    (docgen-loc/loc-key-append-label doc-i18n-name path schema)
                           :on-click (fn [_] (>evt [::append-row-clicked {:doc-id   doc-id
                                                                          :path     path
                                                                          :next-idx next-idx
                                                                          ;; NOTE: was needed for table, not sure about if needed here
                                                                          :ignore-missing-parent true}]))}]
           " "]
          (let [readonly-paths (<sub [::field-schemas/readonly-paths doc-id path])]
            [:span
             [button/button {:id       (str group-id "-copy-button")
                             :class    "secondary"
                             :icon     :lupicon-copy
                             :label    (docgen-loc/loc-key-copy-label doc-i18n-name path schema)
                             :on-click (fn [_] (>evt [::docgen-common/copy-last-row {:ignore-paths readonly-paths
                                                                                     :app-id       app-id
                                                                                     :doc-id       doc-id
                                                                                     :path         path
                                                                                     :next-idx     next-idx}]))}]])]))]))

