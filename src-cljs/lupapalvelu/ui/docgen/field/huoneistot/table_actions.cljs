(ns lupapalvelu.ui.docgen.field.huoneistot.table-actions
  "Actions targeting the table (e.g. add new row, copy row..)"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.docgen.component.button :as button]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.field.huoneistot.premise-upload :as premise-upload]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-event-fx ::append-row-clicked
  (fn [{:keys [db] :as cofx} [_ {:keys [app-id doc-id table-path next-idx]}]]
    (when-let [db (docs/add-group-child db (util/mmap {:path table-path} doc-id next-idx))]
      ;; Assign the `muutostapa` field immediately when creating the empty row
      (let [path          (into table-path [next-idx :muutostapa])
            value         muutostapa/added
            callback-args {:app-id      app-id
                           :doc-id      doc-id
                           :field-paths [path]
                           :values      [value]}]
        (nutil/merge-every-fn (assoc cofx :db db)
                              [[command/update-doc-fx
                                (assoc callback-args
                                       :on-success  [::docgen-common/on-save-success callback-args]
                                       :on-error    [::docgen-common/on-save-error callback-args])]])))))

(defn make-copy-row-callback
  [{:keys [app-id doc-id table-path next-idx]}]
  (let [readonly-paths (<sub [::field-schemas/readonly-paths doc-id table-path])
        ;; Make sure the copied row is a new row, regardless of the previous row
        extra-data     {(into table-path [(dec next-idx) :muutostapa]) muutostapa/added}
        args           (util/mmap {:path         table-path
                                   :ignore-paths readonly-paths}
                                  app-id doc-id next-idx extra-data)]
    (fn [_] (>evt [::docgen-common/copy-last-row args]))))

(defn button-group
  [{:keys [disabled? doc-id schema table-path]}]
  [:div.button-group
   (when-not disabled?
     (let [app-id                   (<sub [::app/application-id])
           group-id                 (path/path->table-id table-path doc-id)
           doc-i18n-name            (<sub [::docs/i18n-name doc-id])
           next-idx                 (<sub [::docs/next-child-id doc-id table-path])
           allow-premise-upload?    (boolean (:allow-premise-upload? schema))
           allow-highlight-changes? (boolean (:allow-highlight-changes? schema))]
       [:<>
        ;; Add new empty row
        [:span
         [button/button {:id       (str group-id "-huoneistot-append-button")
                         :class    "secondary"
                         :icon     :lupicon-circle-plus
                         :label    (docgen-loc/loc-key-append-label doc-i18n-name table-path schema)
                         :on-click (fn [_] (>evt [::append-row-clicked
                                                  (util/mmap table-path app-id doc-id next-idx)]))}]
         " "]
        ;; Copy last row as a new row
        (when-not (zero? next-idx)
          [:span
           [button/button {:id       (str group-id "-huoneistot-copy-button")
                           :class    "secondary"
                           :icon     :lupicon-copy
                           :label    (docgen-loc/loc-key-copy-label doc-i18n-name table-path schema)
                           :on-click (make-copy-row-callback (util/mmap app-id doc-id table-path next-idx))}]
           " "])
        ;; Button for importing an Excel file with premises data
        [:f> premise-upload/button (util/mmap app-id doc-id disabled?
                                              allow-premise-upload?
                                              allow-highlight-changes?)]]))])
