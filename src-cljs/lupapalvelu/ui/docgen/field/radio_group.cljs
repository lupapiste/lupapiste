(ns lupapalvelu.ui.docgen.field.radio-group
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.input :as input]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- render-button
  [{:keys [args validation-result-level]} choice]
  (let [{:keys [doc-id schema path]} args
        {:keys [label]}              schema
        class-prefix                 (or (:classPrefix choice) "radio")
        choice-path                  (conj path (keyword (:name choice)))
        input-id                     (path/path->id choice-path doc-id)
        {:keys [label-text]
         :as   label-data}           (when label
                                       (docgen-common/make-label-data {:doc-id                  doc-id
                                                                       :input-id                input-id
                                                                       :schema                  schema
                                                                       :object-i18n-key         (:i18nkey choice)
                                                                       :parent-type             :radio
                                                                       :parent-path             choice-path
                                                                       :validation-result-level validation-result-level
                                                                       :hidden?                 true
                                                                       :use-aria-label?         true
                                                                       :class                   (str class-prefix "-label")}))
        input-args                   (cond-> args
                                       true                    input/args->input-args
                                       true                    (assoc :input-type :radio
                                                                      :input-id input-id
                                                                      :radio-label (:name choice))
                                       validation-result-level (assoc :validation-result-level validation-result-level)
                                       label                   (assoc :aria-label label-text))]
    [:div ;; wrapper
     {:class [(str class-prefix "-wrapper")
              (when (:vertical schema) "vertical-radio")]}
     [:f> input/render-input input-args]
     (when label
       (if-let [icon (:icon choice)]
         [:label (dissoc label-data :label-text)
          [:i {:class icon}]
          [:span {:class "gap--l1"} label-text]]
         [:label (dissoc label-data :label-text)
          label-text]))]))

(sc/defn render
  "See `buildRadioGroup` in `docmodel.js`"
  [{:keys [doc-id schema path] :as args} :- schemas/DocgenFieldArguments]
  (let [{:keys [show-label]}    schema
        validation-result-level (<sub [::validation/field-data-validation-result-level doc-id path])
        input-id                (path/path->id path doc-id)]
    [:div
     {:id    input-id
      :class [(str (:name schema) "-radioGroup")]}
     (into [docgen-common/entry-span (assoc args :radio-group? true)
            (when show-label
              [docgen-common/make-label
               {:doc-id                  doc-id
                :input-id                input-id
                :schema                  schema
                :parent-type             :select ;; seems incorrect :shrug:
                :parent-path             path
                :validation-result-level validation-result-level}])]
           (map (fn [choice]
                  [render-button (util/mmap args validation-result-level)
                   choice])
                (:body schema)))]))
