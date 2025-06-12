(ns lupapalvelu.ui.docgen.field.docgen-table
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.button :as button]
            [lupapalvelu.ui.docgen.component.table-footer-sum :as footer-sum]
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
    {:application/load app-id}))

;; NOTE: we should implement better filtering for `:repeating` groups tables.
;; For example, if some column / field is hidden for all rows => don't draw column
(defn ryhti-metadata? [schema]
  (= "ryhti" (:name schema)))

(defn- column-headers
  [{:keys [body columnCss]} path doc-i18n-name removable?]
  (let [columns (->> body
                     (remove ryhti-metadata?)
                     (mapv (fn [schema]
                             {:loc       (docgen-loc/loc-key doc-i18n-name (conj path (:name schema)) schema)
                              :css       (get columnCss (keyword (:name schema)))
                              :required? (:required schema)})))]
    (conj columns {:loc (if removable? :remove "")})))

(defn- render-heading-cell
  [{:keys [loc css required?]}]
  ^{:key loc}
  [:th
   {:class css}
   [:span (common/loc loc)]
   (when required?
     [:span "*"])])

(defn- render-row
  [{:keys [args doc-i18n-name group-id headers removable? row-id]}]
  (let [{:keys [app-id doc-id path
                render-fn schema]} args
        {:keys [body]}             schema
        body                       (remove ryhti-metadata? body)
        header-count               (count headers)
        path                       (conj path row-id)
        args                       (-> args
                                       (dissoc :schema)
                                       (assoc :path path))
        warnings                   (warnings/show-warnings (util/mmap doc-id doc-i18n-name path body))]
    [:<>
     (when warnings
       [:tr
        [:td {:colSpan header-count}
         warnings]])
     (let [field-cells (->> (map (fn [body header] {:subschema body :header header}) body headers)
                            (map (fn [{:keys [subschema header]}]
                                   (let [subschema (-> subschema
                                                       (docgen-loc/apply-parent-i18nkey schema)
                                                       ;; coerce `nil` to `false` => do not display child labels by default
                                                       (update :label boolean))
                                         col-class (:css header)]
                                     ^{:key (path/path->str (conj path (:name subschema)))}
                                     [:td
                                      {:class col-class}
                                      [:span.form-entry.form-input
                                       [render-fn [subschema] args]]]))))
           action-cell ^{:key (path/path->str (conj path :action))} [:td.action-column
                                                                     (when removable?
                                                                       [:button.secondary.icon-only.no-border
                                                                        {:on-click   (fn [_] (>evt [::remove-row-clicked
                                                                                                    (util/mmap app-id doc-id path)]))
                                                                         :aria-label (common/loc :remove)}
                                                                        [:i.lupicon-remove {:aria-hidden true}]])]]
       [:tr
        {:id (str group-id "-row-" row-id)}
        (doall (concat field-cells
                       [action-cell]))])]))

(defn- find-footer
  [footer-sums column]
  (->> footer-sums
       (filter (fn [item]
                 (or (= item column)
                     (= (:amount item) column))))
       first))

(defn get-footer-sums
  [{:keys [body footer-sums]}]
  (->> body
       ;; The first column is reserved for the "Yhteensä: "-cell
       ;; so we don't support calculating the sum for it.
       (drop 1)
       (keep (fn [subschema]
               (when-let [footer (find-footer footer-sums (:name subschema))]
                 {:subschema subschema
                  :td-style  (if (= "calculation" (:type subschema))
                               "td-center" "td-left")
                  :footer    (cond-> footer
                               (:unitKey footer) (update :unitKey keyword))})))
       (into [])))

(sc/defn render
  "See `docgen-table-template.html`"
  [{:keys [app-id doc-id schema path override-auth-source] :as args} :- schemas/DocgenFieldArguments]
  (let [args                 (assoc args :prefer-ko-component? true)
        {:keys [group-help]} schema
        css                  (docgen-common/schema->css schema)
        group-id             (path/path->table-id path doc-id)
        table-class          (str (name (peek path)) "-table")
        doc-i18n-name        (<sub [::docs/i18n-name doc-id])
        removable?           (di/group-removable? args)
        update-command       (:update (<sub [::docs/commands doc-id]))
        updateable?          (<sub [::auth/auth? doc-id update-command override-auth-source])
        group-label          (docgen-loc/loc-key-group-label doc-i18n-name path schema)
        headers              (column-headers schema path doc-i18n-name removable?)
        footers              (get-footer-sums schema)
        row-ids              (<sub [::docs/field-child-keys doc-id path])]
    [(di/container schema)
     [:div
      {:id    group-id
       :class (if (seq css)
                css
                ["form-table"])}
      [:label.form-label.form-label-table
       (common/loc group-label)]
      (when group-help
        [:span.group-help-text
         (common/loc group-help)])
      (when-let [warnings (warnings/resolve-warnings (util/mmap doc-id doc-i18n-name)
                                                     [{:schema schema
                                                       :path   path}])]
        [warnings/warnings {:warnings warnings}])
      [:table
       {:class table-class}
       ;; Table headers
       [:thead
        [:tr
         (->> headers
              (map render-heading-cell)
              doall)]]
       ;; Table body
       [:tbody
        (->> row-ids
             (map (fn [row-id]
                    ^{:key (path/path->str (conj path row-id))}
                    [render-row (util/mmap row-id args group-id headers removable? doc-i18n-name)]))
             doall)]
       ;; Table footer
       (when (seq footers)
         [:tfoot
          [:tr
           [:td.footer-sum--total
            (common/loc :waste.total)]
           [:<>
            (->> footers
                 (map (fn [{:keys [td-style footer subschema]}]
                        ^{:key (path/path->str (conj path (:name subschema)))}
                        [:td
                         {:class td-style}
                         (when footer
                           [footer-sum/footer-sum-value (util/mmap path doc-id footer subschema)])]))
                 doall)]]])]]
     (when updateable?
       (let [next-idx       (<sub [::docs/next-child-id doc-id path])
             readonly-paths (<sub [::field-schemas/readonly-paths doc-id path])]
         [:div.button-group
          [:span
           [button/button {:id       (str group-id "-append-button")
                           :class    "secondary"
                           :icon     :lupicon-circle-plus
                           :label    (docgen-loc/loc-key-append-label doc-i18n-name path schema)
                           :on-click (fn [_] (>evt [::append-row-clicked (util/mmap doc-id path next-idx)]))}]
           " "]
          (when (and (get schema :copybutton true)
                     (not (zero? next-idx)))
            [:span
             [button/button {:id       (str group-id "-copy-button")
                             :class    "secondary"
                             :icon     :lupicon-copy
                             :label    (docgen-loc/loc-key-copy-label doc-i18n-name path schema)
                             :on-click (fn [_] (>evt [::docgen-common/copy-last-row (util/mmap {:ignore-paths readonly-paths}
                                                                                               app-id doc-id path next-idx)]))}]])]))]))
