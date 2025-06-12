(ns lupapalvelu.ui.docgen.field.table
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.button :as button]
            [lupapalvelu.ui.docgen.component.group-approval :as group-approval]
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
                   ;; NOTE: Title and text likely differnet to docmodel version,
                   ;; which uses a generic "remove document part" message.
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
    ;; NOTE: alternatively we could manually cleanup any references to the
    ;; removed row, but it's quite a bit of work and there's always a risk we'll
    ;; miss something. Much _easier_ to just refresh the whole application, as removing
    ;; rows isn't that common.
    ;; NOTE: See property-list for updating state without full application load
    {:application/load app-id}))

(defn- render-row
  [{:keys [args doc-i18n-name group-id removable? row-id]}]
  (let [{:keys [app-id doc-id path
                render-fn schema]} args
        {:keys [body]}             schema
        path                       (conj path row-id)
        args                       (-> args
                                       (dissoc :schema)
                                       (assoc :path path))
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
          [:<>
           [:td
            [:span.form-entry.form-input
             [render-fn [subschema] args]]]]))

      [:td.action-column
       (when removable?
         [:button.secondary.icon-only.no-border
          {:on-click   (fn [_] (>evt [::remove-row-clicked {:app-id app-id
                                                            :doc-id doc-id
                                                            :path   path}]))
           :aria-label (common/loc :remove)}
          [:i.lupicon-remove {:aria-hidden true}]])]]]))

(sc/defn render
  [{:keys [app-id doc-id schema path override-auth-source] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [group-help]}     schema
        group-id                 (path/path->table-id path doc-id)
        table-class              (str (name (peek path)) "-table")
        doc-i18n-name            (<sub [::docs/i18n-name doc-id])
        removable?               (di/group-removable? args)
        update-command           (:update (<sub [::docs/commands doc-id]))
        updateable?              (<sub [::auth/auth? doc-id update-command override-auth-source])
        group-label              (docgen-loc/loc-key-group-label doc-i18n-name path schema)
        row-ids                  (<sub [::docs/field-child-keys doc-id path])
        approvable?              (boolean (:approvable schema))]
    [(di/container schema)
     [:div
      {:class "form-table"}
      [:label.form-label.form-label-table
       (common/loc group-label)]
      (when group-help
        [:span.group-help-text
         (common/loc group-help)])

      ;; NOTE: Can't find any uses for this
      (when approvable?
        [group-approval/render
         {:doc-id               doc-id
          :path                 path
          :override-auth-source override-auth-source
          :approvable?          approvable?
          :removable?           removable?}])

      [:div.table-container
       [:table
        {:class table-class}
        [:thead
         [:tr
          (for [col-schema (:body schema)]
            ^{:key (:name col-schema)}
            [:th
             (common/loc (docgen-loc/loc-key doc-i18n-name (conj path (:name col-schema)) col-schema))])
          [:th]]]

        [:tbody
         (for [row-id row-ids]
           ^{:key row-id}
           [render-row
            {:row-id        row-id
             :args          args
             :group-id      group-id
             :removable?    removable?
             :doc-i18n-name doc-i18n-name}])]]]]

     (when updateable?
       (let [;; NOTE: Juho: I would prefer to calculate the next-idx in the event handler using db value.
             next-idx       (<sub [::docs/next-child-id doc-id path])]
         [:div.button-group
          [:span
           [button/button {:id       (str group-id "-append-button")
                           :class    "secondary"
                           :icon     :lupicon-circle-plus
                           :label    (docgen-loc/loc-key-append-label doc-i18n-name path schema)
                           :on-click (fn [_] (>evt [::append-row-clicked {:doc-id   doc-id
                                                                          :path     path
                                                                          :next-idx next-idx
                                                                          ;; Why does add-group-child need this for these tables?
                                                                          ;; no :hairiolle-alttiit-kohteet key present for empty doc?
                                                                          ;; Possibly due to :repeating-init-empty true
                                                                          :ignore-missing-parent true}]))}]
           " "]
          ;; NOTE: Doesn't seem like this would be used for any table-component now
          (when (:copybutton schema)
            (let [readonly-paths (<sub [::field-schemas/readonly-paths doc-id path])]
              [:span
               [button/button {:id       (str group-id "-copy-button")
                               :class    "secondary"
                               :icon     :lupicon-copy
                               :label    (docgen-loc/loc-key-with-suffix :copyLabel false doc-i18n-name path schema)
                               :on-click (fn [_] (>evt [::docgen-common/copy-last-row {:ignore-paths readonly-paths
                                                                                       :app-id       app-id
                                                                                       :doc-id       doc-id
                                                                                       :path         path
                                                                                       :next-idx     next-idx}]))}]]))]))]))

