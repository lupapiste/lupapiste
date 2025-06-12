(ns lupapalvelu.ui.docgen.field.group-grid
  "Utility functions for calculating row stuff for 'form-grid-docgen-group-template'.
  NOTE: most of this could be removed if the schema for the `:rows` was simplified."
  (:require [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.schemas :as docgen-schemas]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(defschema TitleRow
  {:ltext sc/Str
   :class [sc/Keyword]})

(defschema Column
  {;; Field schema
   :schema    docgen-schemas/DocgenSchema
   ;; Path to the parent field
   :path      docgen-schemas/Path
   :col-class [sc/Str]})

(defschema FieldRow
  {:columns                   [Column]
   (sc/optional-key :row-css) [(sc/cond-pre sc/Keyword sc/Str)]})

(defschema Row
  (sc/conditional
    :ltext TitleRow
    :columns FieldRow))

(defschema Options
  {;; Path of the parent group
   :path   docgen-schemas/Path
   ;; Schema of the parent group
   :schema sc/Any})

(defn- parse-column
  [column-description]
  ;; Columns within a row look like
  ;; "foo", "foo/bar", "foo/bar::3"
  ;; NOTE: original JS implementation also tried to parse class names
  ;; from the column string, but it seems like no existing schema uses them.
  (let [[_ path cols] (re-find #"([^:]+)(?:::)?([\w\.]+)?" column-description)]
    (cond-> {:path (ss/split path #"/")
             :cols 1}
      cols (assoc :cols cols))))

(defn- field-row
  [{:keys [row css]} {parent-path   :path
                      parent-schema :schema
                      path-map      :path-map}]
  (let [result (cond-> {:columns (->> row
                                      ;; a row consists of one or more columns/fields
                                      (map parse-column)
                                      (keep (fn [{:keys [path col-class cols]}]
                                              (when-let [child-schema (get path-map path)]
                                                {:schema    (-> child-schema
                                                                (docgen-loc/apply-parent-i18nkey parent-schema))
                                                 ;; Remove the reference to this field from the path
                                                 ;; The `path` should refer to the parent, as `render-fn`
                                                 ;; will take the field name from the child schema and build
                                                 ;; the updated path later when rendering.
                                                 :path      (into parent-path (map keyword (pop path)))
                                                 :col-class (cond-> [(str "col-" cols)]
                                                              col-class (conj col-class))})))
                                      (into []))}
                 css (assoc :row-css css))]
    (when (seq (:columns result))
      result)))

(defn- title-row
  "Display text with the specified style"
  [row]
  (let [[class ltext] (first row)]
    {:ltext ltext
     :class (util/split-kw-path class)}))

(sc/defn process-row :- (sc/maybe Row)
  [row args]
  (if (:row row)
    ;; row looks like `{:row ["field-a" "field-b"] :css [:foo]}`
    (field-row row args)
    ;; row looks like `{:p :some-label}`
    (title-row row)))

(defn build-path-map
  "Build a lookup-table from paths -> schema.
  When we later encounter a path like `[foo bar baz]` we
  can retrieve the schema from the map."
  ([body]
   (build-path-map body []))
  ([body prefix]
   (->> body
        (mapcat (fn [{field-name :name
                      children   :body
                      :as        field}]
                  (let [path (conj prefix field-name)]
                    (into [[path field]]
                          (build-path-map children path)))))
        (into {}))))

(defn- normalize-rows
  [rows]
  ;; Individual rows can look like
  ;; - vector of field(s):  ["field-a" "field-b"]
  ;; - tag & loc-key:       {:p :some-label}
  ;; - a map with `:row`    {:row ["field-a" "field-b"]
  ;;                         :css [:foo]}
  ;; Here we normalize the first case into the third case
  (->> rows
       (map (fn [row]
              (if (vector? row)
                {:row row}
                row)))))

(sc/defn process-rows :- [Row]
  [{:keys [schema path]} :- Options]
  (let [{:keys [body rows]} schema
        path-map            (build-path-map body)]
    (->> rows
         normalize-rows
         (keep (fn [row] (process-row row {:schema   schema
                                           :path     path
                                           :path-map path-map})))
         (into []))))
