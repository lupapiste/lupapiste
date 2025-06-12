(ns lupapalvelu.ui.docgen.feature.validation
  (:require [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]))

(defn field-errors
  "Return a map of path -> errors."
  [validation-results]
  (reduce (fn [acc {:keys         [path element _document]
                    [level error] :result}]
            ;; NOTE: the same field might have multiple errors, add them in order
            ;; NOTE: path might contain indexes as strings, convert to integers.
            (update-in acc [(path/integerize-path path)]
                       (fnil conj []) {:loc-key (:locKey element)
                                       :level   (keyword level)
                                       :error   error}))
          {}
          validation-results))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  ;; `validationErrors` contains all the errors of the document
  ;; We can use it to populate the `::validation` data immediately
  {:db (assoc db ::validation (reduce (fn [acc {:keys [id validationErrors]}]
                                        (if (seq validationErrors)
                                          (assoc acc id (field-errors validationErrors))
                                          acc))
                                      {}
                                      (app/app->documents app)))})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::validation)})

(defn commit-field-errors
  "Update field validation issues when a field is updated.
  Note that the results include all errors, not just the field that was updated."
  [{:keys [db]} [_ doc-id {:keys [results]}]]
  {:db (assoc-in db [::validation doc-id] (field-errors results))})

;; Latest validation values of the form fields, initialized when the app is loaded
;; and updated when the backend responds to our field changes
(rf/reg-sub ::validation :-> ::validation)

(rf/reg-sub ::doc-validation
  :<- [::validation]
  (fn [validation [_ doc-id]]
    {:pre [(string? doc-id)]}
    (get validation doc-id)))

(rf/reg-sub ::field-validation
  (fn field-validation-inputfn [[_ doc-id _field-path]]
    {:pre [doc-id]}
    (rf/subscribe [::doc-validation doc-id]))
  (fn [doc-validation [_ _doc-id field-path]]
    (get doc-validation field-path [])))

(rf/reg-sub ::field-data-validation-result-level
  (fn field-data-validation-result-level-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::field-validation doc-id field-path]))
  (fn [validation-results]
    (when (not-empty validation-results)
      (:level (first validation-results)))))

(rf/reg-sub ::field-warnings
  (fn field-warnings-inputfn [[_ doc-id field-path]]
    (rf/subscribe [::field-validation doc-id field-path]))
  (fn [validation-results _]
    (->> validation-results
         (keep (fn [{:keys [level error]}]
                 (when (#{:warn :err} level)
                   {:error (str "error." error)})))
         (into [])
         not-empty)))
