(ns lupapalvelu.ui.docgen.field.huoneistot.columns
  "Premises table configuration"
  (:require [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.field.huoneistot.common :as huoneistot-common]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(defschema ColumnSpec
  {;; Name of the column.
   ;; For non-synthetic columns the name should refer to a field in the schema.
   ;; If the column-name refers to a non-existing schema field, the column is not drawn.
   :column-name                  sc/Keyword
   ;; How to render the column
   :column-type                  (sc/enum :select :text :checkbox :label :actions :in-changes)
   ;; Does the `column-name` refer to a schema field (true) or
   ;; is the column for other purposes (false)?
   (sc/optional-key :synthetic?) sc/Bool})

(defschema ColumnContext
  "Context used to determine which columns to display"
  {:change-permit? sc/Bool
   :table-children #{sc/Keyword}})

(sc/defn columns :- [ColumnSpec]
  "Specifies which columns to display, in what order, and in what type."
  [{:keys [change-permit? table-children]} :- ColumnContext]
  (->> [;; In-changes is a special column for displaying a checkbox for change permits
        (when change-permit?
          {:column-name :in-changes
           :column-type :in-changes
           :synthetic?  true})
        {:column-name :huoneistoTyyppi
         :column-type :select}
        {:column-name :porras
         :column-type :text}
        {:column-name :huoneistonumero
         :column-type :text}
        {:column-name :jakokirjain
         :column-type :text}
        {:column-name :sijaintikerros ; Ryhti
         :column-type :text}
        {:column-name :sijaintikerroksen-tarkenne ; Ryhti
         :column-type :select}
        {:column-name :huoneluku
         :column-type :text}
        {:column-name :keittionTyyppi
         :column-type :select}
        {:column-name :huoneistoala
         :column-type :text}
        {:column-name :wc ; Ryhti
         :column-type :text}
        {:column-name :WCKytkin
         :column-type :checkbox}
        {:column-name :suihku ; Ryhti
         :column-type :text}
        {:column-name :ammeTaiSuihkuKytkin
         :column-type :checkbox}
        {:column-name :sauna ; Ryhti
         :column-type :text}
        {:column-name :saunaKytkin
         :column-type :checkbox}
        {:column-name :parveke ; Ryhti
         :column-type :text}
        {:column-name :parvekeTaiTerassiKytkin
         :column-type :checkbox}
        {:column-name :lammin-vesi ; Ryhti
         :column-type :text}
        {:column-name :lamminvesiKytkin
         :column-type :checkbox}
        {:column-name :pysyvaHuoneistotunnus
         :column-type :label}
        ;; Actions column for rows (undo, delete..)
        {:column-name :huoneistotTable.actions
         :column-type :actions
         :synthetic?  true}]
       (filter (fn [column]
                 ;; Synthetic columns don't originate from the schema
                 (or (:synthetic? column)
                     ;; Check that the schema actually contains the field
                     (contains? table-children (:column-name column)))))
       (into [])))

(rf/reg-sub ::table-children
  (fn [[_ doc-id table-path]]
    {:pre [(string? doc-id) (vector? table-path)]}
    ;; NOTE: we are looking at the raw schema! It contains all the fields,
    ;; and does not take into account hiding, whitelisting, flags etc.
    (rf/subscribe [::field-schemas/doc-field-schemas doc-id]))
  (fn [schemas [_ _doc-id table-path]]
    (let [child-path-len (inc (count table-path))]
      (->> (keys schemas)
           ;; Find the immediate children of this table
           (filter (fn [path]
                     (and (util/prefix-matches? path table-path)
                          (= child-path-len (count path)))))
           (map (fn [path]
                  (nth path (dec child-path-len))))
           (into #{})))))

(rf/reg-sub ::columns
  (fn [[_ doc-id table-path]]
    {:pre [(string? doc-id) (vector? table-path)]}
    [(rf/subscribe [::huoneistot-common/change-permit?])
     (rf/subscribe [::table-children doc-id table-path])])
  (fn [[change-permit? table-children]]
    {:pre [(boolean? change-permit?) (set? table-children)]}
    (columns {:change-permit? change-permit?
              :table-children table-children})))

(rf/reg-sub ::column-non-synthetic
  (fn [[_ doc-id table-path]]
    {:pre [(string? doc-id) (vector? table-path)]}
    (rf/subscribe [::columns doc-id table-path]))
  (fn [columns]
    (->> columns
         (remove :synthetic?)
         (into []))))
