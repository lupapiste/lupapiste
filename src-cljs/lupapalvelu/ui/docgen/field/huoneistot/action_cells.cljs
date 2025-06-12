(ns lupapalvelu.ui.docgen.field.huoneistot.action-cells
  "Render table cells, specifically the action cells which manipulate the row."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-table :as field-docgen-table]
            [lupapalvelu.ui.docgen.field.huoneistot.cells-common :as cells-common]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::show-remove-button?
  (fn [[_ doc-id row-path]]
    (rf/subscribe [::cells-common/row-is-new? doc-id row-path]))
  :-> identity)

(rf/reg-sub ::show-undo-button?
  (fn [[_ doc-id row-path]]
    [(rf/subscribe [::cells-common/row-is-edited? doc-id row-path])
     (rf/subscribe [::cells-common/row-is-to-be-removed? doc-id row-path])
     (rf/subscribe [::in-changes/not-in-changes? doc-id row-path])])
  (fn [[edited-row? to-be-removed-row? not-in-changes?]]
    (boolean (and (not not-in-changes?)
                  (or edited-row?
                      to-be-removed-row?)))))

(rf/reg-sub ::show-mark-removed-button?
  (fn [[_ doc-id row-path]]
    [(rf/subscribe [::cells-common/row-is-new? doc-id row-path])
     (rf/subscribe [::cells-common/row-is-edited? doc-id row-path])
     (rf/subscribe [::cells-common/row-is-to-be-removed? doc-id row-path])
     (rf/subscribe [::in-changes/not-in-changes? doc-id row-path])])
  (fn [[new-row? edited-row? to-be-removed-row? not-in-changes?]]
    (boolean (and (not not-in-changes?)
                  (not new-row?)
                  (not edited-row?)
                  (not to-be-removed-row?)))))

(rf/reg-sub ::undo-value
  (fn [[_ doc-id cell-path]]
    [(rf/subscribe [::source-value/source-value-or-state doc-id cell-path])
     (rf/subscribe [::value/current-field-value doc-id cell-path])])
  (fn [[source-value-or-state current-value]]
    (cond
      ;; Are we undoing a cell with no source value?
      ;; Might happen e.g. in a change permit where the original
      ;; permit added a new row.
      ;; Ideally we could consult the schema to figure out the proper
      ;; default value, but the old implementation just guessed so let's
      ;; do that.
      (= ::source-value/no-source-value source-value-or-state)
      (let [default-value (cond
                            (boolean? current-value) false
                            :else                    "")]
        (if (not= current-value default-value)
          default-value
          ::not-modified))

      (= ::source-value/not-modified source-value-or-state)
      ::not-modified

      ;; return original source value
      :else
      source-value-or-state)))

(rf/reg-sub ::undo-muutostapa
  (fn [[_ doc-id row-path]]
    [(rf/subscribe [::muutostapa/current-muutostapa doc-id row-path])
     (rf/subscribe [::muutostapa/original-muutostapa doc-id row-path])
     (rf/subscribe [::huoneistot-common/change-permit?])
     (rf/subscribe [::cells-common/row-is-new? doc-id row-path])])
  (fn [[current-value source-value change-permit? new-row?]]
    (let [next-muutostapa (muutostapa/resolve-muutostapa
                            (util/mmap {:change-type :reverted}
                                       change-permit? new-row? current-value source-value))]
      (when (not= next-muutostapa current-value)
        ;; Return an explicit `""` if we are clearing the muutostapa
        (or next-muutostapa "")))))

(rf/reg-sub ::undo-payload
  (fn undo-payload-inputfn [[_ doc-id row-path fields]]
    {:pre [(string? doc-id) (vector? row-path) (vector? fields)]}
    ;; Check which fields have a source value which differs from the current value
    (->> fields
         (map (fn [field] (conj row-path field)))
         (map (fn [cell-path] (rf/subscribe [::undo-value doc-id cell-path])))
         (into [(rf/subscribe [::undo-muutostapa doc-id row-path])])))
  (fn [[muutostapa & values] [_ _doc-id row-path fields]]
    (let [changes (->> (zipmap fields values)
                       (remove (fn [[_k v]] (= ::not-modified v))))
          changes (if muutostapa
                    (concat changes [[:muutostapa muutostapa]])
                    changes)]
      ;; Build a command payload that resets all the modified fields
      ;; to match their source values.
      (reduce (fn [acc [field value]]
                (let [path (conj row-path field)]
                  (-> acc
                      (update :field-paths conj path)
                      (update :values conj value))))
              {:field-paths []
               :values      []}
              changes))))

(defn undo-fields
  "When undoing local changes, which columns/fields should we check for modifications?"
  [schema]
  (->> schema
       :body
       (remove :readonly)
       (remove field-docgen-table/ryhti-metadata?)
       (remove (util/fn-> :whitelist :roles first (util/=as-kw :none)))
       (map (comp keyword :name))
       (remove #{:muutostapa})
       (into [])))

(defn make-undo-callback
  [{:keys [app-id doc-id row-path schema]}]
  (let [fields        (undo-fields schema)
        undo-payload  (<sub [::undo-payload doc-id row-path fields])
        callback-args {:app-id      app-id
                       :doc-id      doc-id
                       :field-paths (:field-paths undo-payload)
                       :values      (:values undo-payload)}]
    (fn [_]
      (>evt [::docgen-common/change-immediate
             (assoc callback-args
                    :on-success [::docgen-common/on-save-success callback-args]
                    :on-error [::docgen-common/on-save-error callback-args])]))))

(defn- action-button
  [{:keys [label icon on-click]}]
  [:button.secondary.icon-only.no-border
   {:aria-label (common/loc label)
    :on-click   on-click}
   [:i {:class       icon
        :aria-hidden true}]])

(defn actions-cell
  "Render various buttons to manipulate the row"
  [{:keys [doc-id removable? row-path schema]}]
  [:td.action-column
   (when removable?
     (let [app-id (<sub [::app/application-id])]
       [:<>
        ;; User can remove fresh rows created for this application
        (when (<sub [::show-remove-button? doc-id row-path])
          [action-button {:label    :remove
                          :icon     :lupicon-remove
                          :on-click (fn [_] (>evt [::field-docgen-table/remove-row-clicked
                                                   (util/mmap {:path row-path} app-id doc-id)]))}])
        ;; User can undo changes made to rows inherited from a previous application
        (when (<sub [::show-undo-button? doc-id row-path])
          (let [reset-values (make-undo-callback (util/mmap app-id doc-id row-path schema))]
            [action-button {:label    :undo
                            :icon     :lupicon-undo
                            :on-click reset-values}]))
        ;; User can mark an inherited row as removed
        (when (<sub [::show-mark-removed-button? doc-id row-path])
          ;; "mark as removed" => set `muutostapa` to `poisto`
          (let [mark-as-removed (muutostapa/make-mark-as-removed-callback (util/mmap app-id doc-id row-path))]
            [action-button {:label    :remove
                            :icon     :lupicon-remove
                            :on-click mark-as-removed}]))]))])
