(ns lupapalvelu.ui.docgen.field.docgen-time
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.collapse :as collapse]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- pad-with-0 [s]
  (.padStart (str s) 2 "0"))

(defn- remove-leading-0 [s]
  (ss/replace s #"^0*(\d)" "$1"))

(defn time-selector [doc-id path set-time-fn close-fn]
  (let [value   (<sub [::value/current-field-value doc-id path])
        [old-h old-m] (when (ss/not-blank? value)
                        (some->> value
                                 (re-find #"(\d*):(\d*)")
                                 (drop 1)
                                 (map pad-with-0)))
        hours   (map pad-with-0 (range 24))
        minutes ["00" "15" "30" "45"]]
    (into [:div.tabby.timepicker-picker]
          (map (fn [[i row]]
                 (into [:div.tabby__row]
                       (conj
                         (mapv (fn [h]
                                 [:div.tabby__cell.timepicker--cell.timepicker--selectable.timepicker--hour
                                  {:class    (when (= h old-h) "timepicker--selected")
                                   :on-click #(set-time-fn (str (remove-leading-0 h) ":" old-m))}
                                  h])
                               row)
                         (when-let [m (nth minutes i nil)]
                           [:div.tabby__cell.timepicker--cell.timepicker--selectable.timepicker--minute
                            {:class    (when (= m old-m) "timepicker--selected")
                             :on-click #(do (set-time-fn (str (remove-leading-0 old-h) ":" m))
                                            (close-fn))}
                            (str ":" m)]))))
               (map-indexed vector (partition-all 5 hours))))))

(sc/defn render
  "See `docgen-time-template.html`"
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [validation-result-level    (<sub [::validation/field-data-validation-result-level doc-id path])
        read-only?                 (docgen-common/is-read-only? (util/mmap doc-id path schema))
        can-modify?                (and (not disabled?) (not read-only?))
        input-id                   (path/path->id path doc-id)
        {:keys [label aria-label]} (di/args->label args)
        flags                      (docgen-common/aria-flags validation-result-level)
        value                      (<sub [::value/current-field-value doc-id path])
        input-classes              (di/input-classes schema :time validation-result-level)
        emit-data                  (emit/schema->emit-data (util/mmap app-id doc-id schema))
        focus-ref                  (focus/use-focus doc-id path)]
    [:div.timepicker-container.flex
     [collapse/value-selector-wrapper
      [:div
       [di/docgen-label {:id                      input-id
                         :label                   label
                         :size                    (:size schema)
                         :validation-result-level validation-result-level}]
       [:div.flex.justify-start.border.border-ui-gray-221
        [ui-input/text-input
         (cond-> (merge {:id          input-id
                         :class       input-classes
                         :debounce-ms docgen-common/default-input-debounce-ms
                         :value       value}
                        (-> (di/schema->input-options schema)
                            (select-keys [:max-length :placeholder])))
           (seq flags) (merge flags)
           focus-ref   (assoc :ref focus-ref)
           aria-label  (assoc :aria-label (common/loc aria-label))
           read-only?  (assoc :read-only? true)
           disabled?   (assoc :disabled?  true)
           can-modify? (assoc :on-change  (docgen-common/make-on-change (util/mmap doc-id path))
                              :on-blur    (docgen-common/make-on-blur
                                            (util/mmap {:backend-value (<sub [::committed/backend-submitted-value doc-id path])}
                                                       app-id doc-id path emit-data))))]
        [:div [ping/ping {:doc-id doc-id :field-path path :mode :icon}]]]]
      ;; TODO: LPK-7712 time-selector dialog partially hidden if field at the end of the accordion
      (fn [close-fn]
        [time-selector doc-id path (docgen-common/make-on-change-immediate (util/mmap app-id doc-id path emit-data)) close-fn])]]))
