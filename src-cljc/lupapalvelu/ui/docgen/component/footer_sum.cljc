(ns lupapalvelu.ui.docgen.component.footer-sum
  "Helper functions for calculating the sum totals of columns in `docgenTable`s."
  (:require [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defn str->value
  [s]
  (if (ss/blank? s)
    0 ;; Blank inputs considered zero
    (util/->double s ::invalid)))

(defn safe-sum
  "Sum `values` but stop on `::invalid` values."
  [values]
  (reduce (fn [acc value]
            (if (= ::invalid value)
              (reduced ::invalid)
              (+ acc value)))
          0
          values))

(defn- footer-amount-calculation
  [{:keys [column-data subschema]}]
  ;; A `calculation` cell takes values from cells on the same row and calculates a sum.
  ;; So to get the total sum of the calculation(s) on multiple rows, we sum all the values of all the columns
  ;; referenced by the calculation.
  (->> (:columns subschema)
       (mapcat column-data)
       (map str->value)
       safe-sum))

(defn- footer-amount-unit-sum
  [{:keys [column-data footer subschema]} output-unit]
  ;; We're summing up values, which each have their corresponding unit cell
  (let [units (column-data (:unit footer))]
    (->> (column-data (:name subschema))
         (map str->value)
         (map-indexed (fn [idx value]
                        (if (= ::invalid value)
                          ::invalid
                          (if-let [u (nth units idx)]
                            (if (util/=as-kw output-unit u)
                              value
                              ;; NOTE: we make a big assumption here that if the unit doesn't match
                              ;; then we're dealing with the case where we have `:kg` and `:tonnia` units
                              ;; mixed and want to calculate everything in `:kg`. Instead we could specify
                              ;; transformation function(s) of [expected-type, actual-type, value] => new value,
                              ;; which would implement the `:tonnia`->`:kg` calculatino.
                              (* 1000 value))
                            ::invalid))))
         safe-sum)))

(defn- footer-amount-sum
  [{:keys [column-data subschema]}]
  ;; The easy case: sum the values in a single column as-is.
  (->> (column-data (:name subschema))
       (map str->value)
       safe-sum))

;; TODO: LPK-7725, these ought to be defined just once, see `lupapalvelu.document.schema-validation`.

(defschema Unit (sc/enum :t :kg :tonnia :m2))

(defschema Footer
  (sc/conditional
    string? sc/Str
    :unitKey {:amount  sc/Str
              :unitKey Unit}
    :unit  {:amount sc/Str
            :unit   sc/Str}))

(defschema Args
  {:footer      Footer
   :column-data (sc/pred ifn?)
   :subschema   (st/open-schema
                  {:name                      sc/Str
                   :type                      sc/Str
                   (sc/optional-key :columns) [sc/Str]})})

(sc/defn footer-amount :- (sc/cond-pre (sc/enum ::invalid) sc/Num)
  "Calculate the total sum of a column.

  The `unit` option only matters in the case when individual
  rows have a unit column and we want to force the result to be
  in a specific unit."
  [{:keys [footer subschema] :as args} :- Args unit :- (sc/maybe Unit)]
  (cond
    (= "calculation" (:type subschema))
    (footer-amount-calculation args)

    (:unit footer)
    (footer-amount-unit-sum args unit)

    :else
    (footer-amount-sum args)))

(sc/defn footer-unit :- (sc/maybe Unit)
  "Resolve the unit to use, or `nil`.

  Unit can be given explicitly in the footer, or the footer can specify
  if the units are specified by the user in a separate column."
  [{:keys [column-data footer]} :- Args]
  (let [{:keys [unit unitKey]} footer]
    (or
      ;; Unit selected for each row separately?
      (and unit
           ;; at least one row uses kg? -> calculate sum in kg
           (if (->> (column-data unit)
                    (some (fn [val]
                            (= "kg" val))))
             :kg :tonnia))
      (keyword unitKey))))
