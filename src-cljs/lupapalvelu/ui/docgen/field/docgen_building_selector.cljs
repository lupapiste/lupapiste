(ns lupapalvelu.ui.docgen.field.docgen-building-selector
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.buildings :as buildings]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.field.docgen-string :as field-docgen-string]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as s]))

(rf/reg-event-fx ::merge-from-krysp
  (fn [_ [_ app-id doc-id building-id overwrite? callback]]
    {:fx [[:dispatch callback]
          [:action/command {:name    :merge-details-from-krysp
                            :params  {:id         app-id
                                      :documentId doc-id
                                      :buildingId building-id
                                      :overwrite  overwrite?}
                            :success #(>evt [::merge-from-krysp.success app-id])}]]}))

(rf/reg-event-fx ::merge-from-krysp.success
  (fn [_ [_ app-id]]
    {:fx [[:application/load app-id]]}))

;; NOTE: Same as in KO.js version: Selecting a building doesn't clear the
;; other-field value (manuaalinen_rakennusnro),

(rf/reg-event-fx ::new-value
  (fn [_ [_ {:keys [app-id doc-id path doc-schema value emit-data]}]]
    (let [callback-args {:app-id      app-id
                         :doc-id      doc-id
                         :field-paths [path]
                         :values      [value]
                         :emit-data   emit-data}
          ;; NOTE: Same as in KO.js version: selected value is saved:
          ;; - OTHER right away, same time as triggering cleanup accordion-fields
          ;; - buildings, after yes-no modal is used to choose yes/no to merge the building details
          ;;     - IF modal is cancelled with "x" or clicking outside of the modal content, the value change is ignored!
          ;; NOTE: Small difference, while the yes-no modal is open:
          ;; - js version shows the non-commited value (new value) as being selected
          ;; - cljs version currently goes back the old value after selection, and only shows the new value
          ;;   after modal is closed.
          save-event    [::docgen-common/change-immediate (assoc callback-args
                                                                 :on-success [::docgen-common/on-save-success callback-args]
                                                                 :on-error [::docgen-common/on-save-error callback-args])]]
      (if (docgen-common/other-selected? value)
        (when-let [paths (some->> doc-schema
                                  :info
                                  :accordion-fields
                                  (mapcat :paths)
                                  (mapv #(mapv keyword %)))]
          (let [values (->> (repeat (count paths) "")
                            (into []))]
            ;; Accordion-paths is the description of what fields to use for the header of the document accordion.
            ;; In case of these building schemas, it is the selected buildingId.
            ;; When "other" is selected, it should be empty.
            ;; Need to both save the empty value to BE and to add a updated value to Cljs state for the
            ;; accordion-toolbar to update.
            {:fx [[:dispatch save-event]
                  ;; What if these generated updates would be also merged into save-event? Would handle commit-changed-fields also.
                  [:dispatch [::command/update-doc {:app-id      app-id
                                                    :doc-id      doc-id
                                                    :field-paths paths
                                                    :values      values
                                                    :on-success  #(>evt [::new-value.success doc-id paths values])}]]]}))
        ;; NOTE: There has been discussion on just forcing overwrite always, not overwriting
        ;; will just cause inconsistent data when changing the building.
        {:fx [[:dialog/show {:type             :yes-no
                             :ltitle           "overwrite.confirm"
                             :ltext            "application.building.merge"
                             :component-params {:yesFn #(>evt [::merge-from-krysp app-id doc-id value true save-event])
                                                :noFn  #(>evt [::merge-from-krysp app-id doc-id value false save-event])}}]]}))))

(rf/reg-event-fx ::new-value.success
  (fn [x [_ doc-id paths values]]
    ;; Make the accordion-toolbar see the updated/cleared values.
    (committed/commit-changed-fields x [nil doc-id paths values])))

(rf/reg-sub ::select-options
  :<- [::buildings/building-options]
  :<- [::flags/include-schema? {:schema-include :rakennusluokka}]
  (fn [[building-options build-class-flag] _]
    (->> (for [building building-options
               :let     [build-class (when build-class-flag
                                       (ss/trim (:building-type building)))
                         usage (->> [(ss/trim (:usage building))
                                     build-class]
                                    (keep identity)
                                    (ss/join ", "))
                         year (ss/trim (:created building))]]
           {:value (:buildingId building)
            :text  (str
                     (:buildingId building)
                     (when usage
                       (str " (" usage ")"))
                     (when year
                       (str " - " year)))})
         (into []))))

(rf/reg-sub ::all-options
  (fn [[_ doc-id path]]
    [(rf/subscribe [::value/current-field-value doc-id path])
     (rf/subscribe [::select-options])])
  (fn [[value building-options]]
    (->> (concat (when (docgen-common/unknown-selected? value building-options)
                   [(docgen-common/unknown-value value)])
                 building-options
                 [(docgen-common/other-value)])
         (into []))))

(s/defn render [{:keys [app-id doc-id path disabled? schema] :as args} :- schemas/DocgenFieldArguments]
  (let [input-id                   (path/path->id path doc-id)
        {:keys [label aria-label]} (di/args->label args)

        read-only?                 (docgen-common/is-read-only? (util/mmap doc-id path schema))
        can-modify?                (and (not disabled?) (not read-only?))
        value                      (<sub [::value/current-field-value doc-id path])

        ;; NOTE: other-key support here is a bit different vs. feature.other-key which is
        ;; used e.g. by select component for a similar case?
        ;; With the select component uses, the other-key schema doesn't use :hidden true,
        ;; and selecting the Other value in select will then make that other field visible.
        ;; Here this component is itself rendering the other field text-input if the Other value
        ;; is selected.
        ;; NOTE: other-key for buildingSelector always refers to the root of document.

        other-id                   (str doc-id "-" (:other-key (:schema args)))
        doc-schema                 (<sub [::docs/schema doc-id])
        other-schema               (some (fn [s]
                                           (when (= (:other-key (:schema args)) (:name s))
                                             s))
                                         (:body doc-schema))
        ;; In docgen.core this has a default true for other fields
        other-schema               (assoc other-schema :label true)
        other-params               {:app-id    app-id
                                    :doc-id    doc-id
                                    :schema    other-schema
                                    :path      (path/to-path [(:other-key (:schema args))])
                                    :disabled? (boolean disabled?)}
        all-options                (<sub [::all-options doc-id path])]
    ;; Trigger initial building-options load
    ;; feature on-app-loaded will keep it up-to-date if property id changes.
    (react/useEffect (fn []
                       (>evt [::buildings/get-building-options app-id])
                       js/undefined)
                     #js [app-id])
    [:div.form-group
     [:div.building-select-container
      [:div.building-select-box
       [di/docgen-label
        {:id    input-id
         :label label}]
       [reselect/reselect
        value
        {:id         input-id
         :name       "buildingId"
         :class      ["max-w-2xl" "mb-2"]
         :aria-label (when aria-label
                       (common/loc aria-label))
         :disabled?  (or disabled? read-only?)
         :items      all-options
         :callback   (when can-modify?
                       (let [emit-data (emit/schema->emit-data {:app-id app-id
                                                                :doc-id doc-id
                                                                :schema schema})]
                         (fn [v]
                           (>evt [::new-value
                                  {:app-id     app-id
                                   :doc-id     doc-id
                                   :path       path
                                   :emit-data  emit-data
                                   :doc-schema doc-schema
                                   :value      v}]))))}]
       [ping/ping
        {:doc-id     doc-id
         :field-path path
         :mode       :icon}]
       (when (docgen-common/other-selected? value)
         [:div
          {:id other-id}
          [:f> field-docgen-string/render other-params]])]]]))
