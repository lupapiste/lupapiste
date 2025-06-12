(ns lupapalvelu.ui.docgen.field.foreman-other-applications
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.button :as button]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc]))

(rf/reg-event-fx ::append-row-clicked
  (fn [{:keys [db]} [_ args]]
    (when-let [new-db (docs/add-group-child db args)]
      {:db new-db})))

(rf/reg-event-fx ::remove-row-clicked
  (fn [_ [_ args]]
    {:dialog/show {:type   :yes-no
                   :ltitle :remove
                   ;; From JS
                   ;; :ltext  :document.delete.group.subGroup.message
                   ;; Makes more sense with a table:
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

(rf/reg-sub ::rows
  (fn [[_ doc-id path]]
    (rf/subscribe [::docs/field-child-keys doc-id path]))
  (fn [child-keys [_ doc-id path]]
    (let [{rows false, autoupdated-rows true}
          (group-by (fn [row-id]
                      (let [autoupdated-path (conj path row-id :autoupdated)]
                        (boolean (<sub [::docs/field-data-value doc-id autoupdated-path]))))
                    child-keys)]
      {:autoupdated-rows autoupdated-rows
       :rows rows})))

(defn tbody-rows [{:keys [rows subschemas removable?]} {:keys [app-id doc-id path render-fn] :as args}]
  (let [args (dissoc args :schema)]
    [:tbody
     (for [row-id rows]
       (let [row-path (conj path row-id)
             row-args (assoc args :path row-path)]
         [:tr
          {:key row-id}
          (for [subschema subschemas]
            (when-not (:hidden subschema)
              [:td
               {:key (:name subschema)}
               ;; render-fn adds the field name to path itself
               [render-fn [subschema] row-args]]))
          [:td.action-column
           (when removable?
             [:i.lupicon-remove.primary
              {:on-click (fn [_] (>evt [::remove-row-clicked (util/mmap {:path row-path} app-id doc-id)]))
               :style {:cursor "pointer"}}])]]))]))

(rf/reg-event-fx ::update-foreman-other-applications
  (fn [_ [_ app-id doc-id]]
    {:action/command {:name    :update-foreman-other-applications
                      :params  {:id    app-id
                                :docId doc-id}
                      :success [::update-foreman-other-applications.success app-id]}}))

(rf/reg-event-fx ::update-foreman-other-applications.success
  (fn [_ [_ _response app-id]]
    {:application/load app-id}))

(sc/defn render*
  "Native docgen CLJS component"
  [{:keys [app-id doc-id path schema disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [doc-i18n-name        (<sub [::docs/i18n-name doc-id])
        {update-command :update,
         remove-command :remove} (<sub [::docs/commands doc-id])
        update-allowed (<sub [::auth/auth? doc-id update-command])
        remove-allowed (<sub [::auth/auth? doc-id remove-command])
        {:keys [body]}       schema

        ;; rows are list of row-ids
        {:keys [rows autoupdated-rows]} (<sub [::rows doc-id path])]
    ;; NOTE: Not sure if documentChangedInBackend can ever be triggered when hanke-table is visible.
    ;; It is triggered from fillMyInfoButton, which is visible for applicants, and the table is visible for authority users.
    (react/useEffect (fn []
                       (let [s (.subscribe js/hub #js {:eventType "documentChangedInBackendCljs"
                                                       :docId doc-id}
                                           (fn [_e]
                                             (>evt [::update-foreman-other-applications app-id doc-id])))]
                         (fn []
                           (.unsubscribe js/hub s))))
                     #js [])
    (react/useEffect (fn []
                       (let [s (.subscribe js/hub #js {:eventType "hetuChangedCljs"
                                                       :docId doc-id}
                                           (fn [_e]
                                             (>evt [::update-foreman-other-applications app-id doc-id])))]
                         (fn []
                           (.unsubscribe js/hub s))))
                     #js [])
    [:div.form-table
     [:label.form-label.form-label-table
      (common/loc :muutHankkeet.label)]
     [:div.table-container
      [:table
       {:class (str (name (last path)) "-table")}
       [:thead
        [:tr
         [:th {:col-span 5}]
         [:th {:col-span 4} (common/loc :tyonjohtaja.muutHankkeet.ajantarve)]
         [:th]]
        [:tr
         (for [header body]
           (when-not (:hidden header)
             [:th
              {:key (:name header)}
              (common/loc (:i18nkey header))]))
         [:th]]]

       (when (and (empty? rows) (empty? autoupdated-rows))
         [:tbody
          [:tr
           [:td {:col-span (count body)}
            (common/loc :tyonjohtaja.noProjects)]]])

       (when (seq autoupdated-rows)
         (let [readonly-field? (set (.. js/LUPAPISTE -config -foremanReadonlyFields))
               subschemas (map (fn [schema]
                                 (let [readonly (readonly-field? (:name schema))]
                                   (assoc schema
                                          :readonly readonly
                                          :inputType (if (and readonly (:locPrefix schema))
                                                       "localized-string"

                                                       (:inputType schema)))))
                               body)]
           [tbody-rows
            {:rows autoupdated-rows
             :subschemas subschemas
             :removable? false}
            args]))

       (when (seq rows)
         (let [subschemas (map (fn [schema]
                                 (let [i18npath (or [(:i18nkey schema)]
                                                    (conj (:i18npath args) (:name schema)))]
                                   (assoc schema
                                          ;; :schemaI18name not used in cljs version
                                          :i18npath i18npath
                                          :readonly false
                                          :label (boolean (:label schema)))))
                               body) ]
           [tbody-rows
            {:rows rows
             :subschemas subschemas
             :removable? (fn [schema]
                           (and (not (some :readonly (:body schema)))
                                (not disabled?)
                                remove-allowed))}
            args]))]]

     (when update-allowed
       ;; NOTE: Would be nice to calculate next-id in the row-clicked handler, but needs a sub now.
       (let [next-idx (<sub [::docs/next-child-id doc-id path])]
         [:div.button-group
          [:span
           [button/button {:class    "positive"
                           :icon     :lupicon-circle-plus
                           :label    (docgen-loc/loc-key-append-label doc-i18n-name path schema)
                           :on-click (fn [_] (>evt [::append-row-clicked (util/mmap doc-id path next-idx)]))}]]]))]))

(defn render [args]
  [:f> render* (assoc args :prefer-ko-component? true)])
