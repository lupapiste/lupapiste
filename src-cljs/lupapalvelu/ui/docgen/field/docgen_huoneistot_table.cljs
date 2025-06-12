(ns lupapalvelu.ui.docgen.field.docgen-huoneistot-table
  "The premises (huoneistot) table for docgen

  Note that the table looks & works differently depending on whether
  - we are building a new building (empty table, no previous data).
  - we are extending an existing building (table initialized using building
    data from the backend).
  - we are applying for a change permit (muutoslupa) against a previous
    permit (table initialized using building data from the previous permit).

  Implementation notes:
  - Different functionalities target either the table, a row, or a
    particular cell. The corresponding path variables are named
    `table-path` (`[:huoneistot]`), `row-path` (`[:huoneistot 2]`),
    and `cell-path` (`[:huoneistot 2 :huoneluku]`) respectively.
  - Unlike the old implementation, columns can be freely re-ordered
    (See the `columns` function.).
  - The actual business logic tries to follow the original implementation,
    whether it makes sense or not. Thus some of the terminology is a bit
    weird (in-changes vs not-in-changes etc.)."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.group-approval :as group-approval]
            [lupapalvelu.ui.docgen.component.help-toggle :as help-toggle]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.field.huoneistot.headers :as headers]
            [lupapalvelu.ui.docgen.field.huoneistot.highlight :as highlight]
            [lupapalvelu.ui.docgen.field.huoneistot.in-changes :as in-changes]
            [lupapalvelu.ui.docgen.field.huoneistot.premise-upload :as premise-upload]
            [lupapalvelu.ui.docgen.field.huoneistot.rows :as rows]
            [lupapalvelu.ui.docgen.field.huoneistot.table-actions :as table-actions]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc]))

(rf/reg-sub ::data :-> ::data)

(defn on-unload
  [{:keys [db]}]
  {:db (-> db
           (dissoc ::data)
           highlight/unload-data
           premise-upload/unload-data)})

;;; Various help texts and table legends

(defn- title-and-help
  [{:keys [doc-id table-path schema]}]
  (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
        ;; Can't use `loc-key-group-label` because of course huoneistot table
        ;; calculates the title key differently from other groups :(
        ;; Normally if a group uses `i18nkey`, it includes the `_group_label`
        ;; prefix already. But huoneistot table doesn't.
        loc-key       (docgen-loc/loc-key doc-i18n-name table-path schema)
        title-key     (util/kw-path loc-key :_group_label)]
    [:div
     [:span.group-title
      (common/loc title-key)]
     [help-toggle/help-toggle
      {:help-content (common/loc-html-reagent :div :huoneistotTable.help)}]]))

(defn- group-help-note
  [{:keys [group-help]}]
  (when group-help
    [:span.group-help-text
     (common/loc group-help)]))

;;; Table

(defn- building-table
  [{:keys [disabled? doc-id removable? schema table-path]}]
  [:table
   {:class (str (name (peek table-path)) "-table")}
   [headers/table-headers (util/mmap disabled? doc-id schema table-path)]
   [rows/table-rows (util/mmap disabled? doc-id removable? schema table-path)]])

(sc/defn render
  "See `docgen-huoneistot-table-template.html`."
  [{table-path :path
    :keys      [disabled? doc-id override-auth-source schema]
    :as        args} :- schemas/DocgenFieldArguments]
  (let [disabled?                (boolean disabled?)
        approvable?              (boolean (:approvable schema))
        removable?               (di/group-removable? args :ignore-read-only? true)
        allow-premise-upload?    (boolean (:allow-premise-upload? schema))
        allow-highlight-changes? (boolean (:allow-highlight-changes? schema))
        css                      (docgen-common/schema->css schema)]
    [:div {:class css}
     [group-approval/render (util/mmap {:path table-path} doc-id override-auth-source approvable?)]
     [:div.form-table.huoneistot-table
      [title-and-help (util/mmap doc-id table-path schema)]
      [group-help-note schema]
      [in-changes/premises-note]
      [premise-upload/note (util/mmap doc-id allow-premise-upload? allow-highlight-changes?)]
      [highlight/changes-container (util/mmap doc-id table-path allow-highlight-changes?)]
      [:div.table-container
       [building-table (util/mmap disabled? doc-id removable? schema table-path)]]]
     [table-actions/button-group (util/mmap disabled? doc-id schema table-path)]]))
