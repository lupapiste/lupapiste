(ns lupapalvelu.pate.pdf-html
  "HTML facilities for Pate verdicts. Provides a simple schema-based
  mechanism for the layout definition and generation. The resulting
  HTML can be converted into PDF via `lupapalvelu.pdf.html-template`
  functions."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pdf.html-template-common :as common]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all]))

(def cell-widths (range 10 101 5))
(def row-styles [:pad-after :pad-before
                 :border-top :border-bottom
                 :page-break :bold :spaced])
(def cell-styles [:bold :center :right :nowrap])

(defschema Source
  "Value of PdfEntry :source property aka data source for the row."
  (sc/conditional
   ;; Keyword corresponds to a key in the data context.
   keyword? sc/Keyword
   :else (shared-schemas/only-one-of [:doc :dict]
                                     ;; Vector is a path to application
                                     ;; document data. The first item is the
                                     ;; document name and rest are path within
                                     ;; the data.
                                     {(sc/optional-key :doc)     [sc/Keyword]
                                      ;; Kw-path into published verdict data.
                                      (sc/optional-key :dict)    sc/Keyword})))

(defn styles
  "Definition that only allows either individual kw or subset of kws."
  [kws]
  (let [enum (apply sc/enum kws)]
    (sc/conditional
     keyword? enum
     :else    [enum])))

(defschema PdfEntry
  "An entry in the layout consists of left- and right-hand sides. The
  former contains the entry title and the latter actual data. In the
  schema, an entry is modeled as a vector, where the first element
  defines both the title and the data source for the whole entry.

  On styles: the :styles definition of the first item applies to the
  whole row. Border styles (:border-top and :border-bottom) include
  padding and margin so the adjacent rows should not add any
  padding. Cell items' :styles only apply to the corresponding cell.

  In addition to the schema definition, styles can be added in
  'runtime': if source value has ::styles property, it should be map
  with the following possible keys:

  :row    Row styles

  :cell   Cell styles (applied to every cell)

  path    Path is the value of the :path property. For example, if
  the :path has a value of :foo, then the cell could be emphasized
  with ::styles map {:foo :bold}. "
  [(sc/one {;; Localisation key for the row (left-hand) title.
            :loc                        sc/Keyword
            ;; If :loc-many is given it is used as the title key if
            ;; the source value denotes multiple values.
            (sc/optional-key :loc-many) sc/Keyword
            (sc/optional-key :source)   Source
            ;; Post-processing function for source value.
            (sc/optional-key :post-fn) (sc/conditional
                                        keyword? sc/Keyword
                                        :else   (sc/pred fn?))
            (sc/optional-key :styles)   (styles row-styles)}
           {})
   ;; Note that the right-hand side can consist of multiple
   ;; cells/columns. As every property is optional, the cells can be
   ;; omitted. In that case, the value of the right-hand side is the
   ;; source value.
   ;; Path within the source value. Useful, when the value is a map.
   {(sc/optional-key :path)       shared-schemas/path-type
    ;; Textual representation that is static and
    ;; independent from any source value.
    (sc/optional-key :text)       shared-schemas/keyword-or-string
    (sc/optional-key :width)      (apply sc/enum cell-widths)
    (sc/optional-key :unit)       (sc/enum :ha :m2 :m3 :kpl)
    ;; Additional localisation key prefix. Is
    ;; applied both to path and text values.
    (sc/optional-key :loc-prefix) shared-schemas/path-type
    (sc/optional-key :styles)     (styles cell-styles)}])

(defschema PdfLayout
  "PDF contents layout."
  {;; Width of the left-hand side.
   :left-width (apply sc/enum cell-widths)
   :entries    [PdfEntry]})

(def page-number-script
  [:script
   {:dangerouslySetInnerHTML
    {:__html
     (-> common/wkhtmltopdf-page-numbering-script-path
         io/resource
         slurp)}}])


(defn html [body & [script?]]
  (str "<!DOCTYPE html>"
       (rum/render-static-markup
        [:html
         [:head
          [:meta {:http-equiv "content-type"
                  :content    "text/html; charset=UTF-8"}]
          [:style
           {:type "text/css"
            :dangerouslySetInnerHTML
            {:__html (garden/css
                      [[:* {:font-family "'Carlito', sans-serif"}]
                       [:.permit {:text-transform :uppercase
                                  :font-weight    :bold}]
                       [:.preview {:text-transform :uppercase
                                   :color          :red
                                   :font-weight    :bold
                                   :letter-spacing "0.2em"}]
                       [:div.header {:padding-bottom "1em"}]
                       [:div.footer {:padding-top "1em"}]
                       [:.page-break {:page-break-before :always}]
                       [:.section {:display :table
                                   :width   "100%"}
                        [:&.border-top {:margin-top  "1em"
                                        :border-top  "1px solid black"
                                        :padding-top "1em"}]
                        [:&.border-bottom {:margin-bottom  "1em"
                                           :border-bottom  "1px solid black"
                                           :padding-bottom "1em"}]
                        [:&.header {:padding       0
                                    :border-bottom "1px solid black"}]
                        [:&.footer {:border-top "1px solid black"}]
                        [:>.row {:display :table-row}
                         [:&.border-top [:>.cell {:border-top "1px solid black"}]]
                         [:&.border-bottom [:>.cell {:border-bottom "1px solid black"}]]
                         [:&.pad-after [:>.cell {:padding-bottom "0.5em"}]]
                         [:&.pad-before [:>.cell {:padding-top "0.5em"}]]
                         [:.cell {:display       :table-cell
                                  :white-space   :pre-wrap
                                  :padding-right "1em"}
                          [:&:last-child {:padding-right 0}]
                          [:&.right {:text-align :right}]
                          [:&.center {:text-align :center}]
                          [:&.bold {:font-weight :bold}]
                          [:&.nowrap {:white-space :nowrap}]]
                         (map (fn [n]
                                [(keyword (str ".cell.cell--" n))
                                 {:width (str n "%")}])
                              cell-widths)
                         [:&.spaced
                          [(sel/+ :.row :.row)
                           [:.cell {:padding-top "0.5em"}]]]]]
                       [:.markup
                        [:p {:margin-top    "0"
                             :margin-bottom "0.25em"}]
                        [:ul {:margin-top    "0"
                              :margin-bottom "0"}]
                        [:ol {:margin-top    "0"
                              :margin-bottom "0"}]
                        ;; wkhtmltopdf does not seem to support text-decoration?
                        [:span.underline {:border-bottom "1px solid black"}]]])}}]]
         [:body body (when script?
                       page-number-script)]])))

(defn- verdict-schema [{:keys [category schema-version legacy?] :as opts}]
  (if legacy?
    (legacy/legacy-verdict-schema category)
    (verdict-schemas/verdict-schema category schema-version)))

(defn pathify [kw-path]
  (map keyword (ss/split (name kw-path) #"\.")))

(defn dict-value
  "Dictionary value for the given kw-path. Options can either
  have :verdict key or be verdict itself. The returned values are
  transformed into friendly formats (e.g., timestamp -> date)."
  [options kw-path]
  (let [{data :data :as opts} (get options :verdict options)
        path                  (pathify kw-path)
        value                 (get-in data path)
        {schema :schema}      (schema-util/dict-resolve path
                                                        (:dictionary
                                                         (verdict-schema opts)))]
    (cond
      (and (:phrase-text schema) (ss/not-blank? value))
      (list [:div.markup (markup/markup->tags value)])

      (and (:date schema) (integer? value))
      (date/finnish-date value)

      :else
      value)))

(defn doc-value [application doc-name kw-path]
  (get-in (domain/get-document-by-name application (name doc-name))
          (cons :data (pathify kw-path))))

(defn add-unit
  "Result is nil for blank value."
  [lang unit v]
  (when-not (ss/blank? (str v))
    (case unit
      :ha      (str v " " (i18n/localize lang :unit.hehtaaria))
      :m2      [:span v " m" [:sup 2]]
      :m3      [:span v " m" [:sup 3]]
      :kpl     (str v " " (i18n/localize lang :unit.kpl))
      :section (str "\u00a7" v)
      :eur     (str v "\u20ac"))))

(defn resolve-source
  [{:keys [application] :as data} {doc-source  :doc
                                   dict-source :dict
                                   :as         source}]
  (cond
    doc-source  (apply doc-value (cons application doc-source))
    dict-source (dict-value data dict-source)
    :else       (get data source)))

(defn resolve-class [all selected & extra]
  (->> (util/intersection-as-kw all (flatten [selected]))
       (concat extra)
       (remove nil?)))

(defn resolve-cell [{lang :lang :as data}
                    source-value
                    {:keys [text width unit loc-prefix styles] :as cell}]
  (let [path (some-> cell :path pathify)
        class (resolve-class cell-styles
                             styles
                             (when width (str "cell--" width))
                             (or (when (seq path)
                                   (get-in source-value
                                           (cons ::styles path)))
                                 (get-in source-value
                                         [::styles ::cell])))
        value (or text (util/pcond-> (get-in source-value path source-value)
                                     string? ss/blank-as-nil))]
    (when value
      [:div.cell (cond-> {}
                   (seq class) (assoc :class class))
       (cond->> value
         loc-prefix (i18n/localize lang loc-prefix)
         unit (add-unit lang unit))])))

(defn post-process [value post-fn]
  (cond-> value
    (and value post-fn) post-fn))

(defn entry-row
  [left-width {:keys [lang] :as data} [{:keys [loc loc-many source post-fn styles]} & cells]]
  (let [source-value (post-process (util/pcond-> (resolve-source data source)
                                                 string? ss/trim)
                                   post-fn)
        multiple?    (and (sequential? source-value)
                          (> (count source-value) 1))]
    (when (or (nil? source) (not-empty source-value))
      (let [section-styles  [:page-break :border-top :border-bottom]
            row-styles      (resolve-class row-styles styles
                                           (get-in source-value [::styles :row]))
            multiple-cells? (or (> (count cells) 1) multiple?)
            cell-values     (if multiple-cells?
                              (remove nil?
                                      (for [v    (if (sequential? source-value)
                                                   source-value
                                                   (vector source-value))
                                            :let [value (map (partial resolve-cell data v)
                                                             (if (empty? cells) [{}] cells))]]
                                        (when-not (every? nil? value)
                                          [:div.row
                                           {:class (get-in v [::styles :row])}
                                           value])))
                              (resolve-cell data
                                            (util/pcond-> source-value
                                                          sequential? first)
                                            (first cells)))]

        (when-not (util/empty-or-nil? cell-values)
          [:div.section
           {:class (util/intersection-as-kw section-styles row-styles)}
           [:div.row
            {:class (util/difference-as-kw row-styles section-styles)}
            [:div.cell
             {:class (resolve-class [:bold] styles (when left-width
                                                     (str "cell--" left-width)))}
             (i18n/localize lang (if multiple? (or loc-many loc) loc))]
            (if multiple-cells?
              [:div.cell
               [:div.section
                cell-values]]
              cell-values)]])))))

(defn content
  [data {:keys [left-width entries]}]
  (->> entries
       (map (partial entry-row left-width data))
       (filter not-empty)))

(defn organization-name [lang {organization :organization}]
  (org/get-organization-name organization lang))

(defn verdict-header
  [lang application {:keys [category published legacy?] :as verdict}]
  [:div.header
   [:div.section.header
    (let [category-kw    (util/kw-path (when legacy? :legacy) category)
          legacy-kt-ymp? (contains? #{:legacy.kt :legacy.ymp}
                                    category-kw)
          contract?      (util/=as-kw category :contract)
          loc-fn         (fn [& kws]
                           (apply i18n/localize lang (flatten kws)))]
      [:div.row.pad-after
       [:div.cell.cell--40
        (organization-name lang application)
        (when-let [boardname (some-> verdict :references :boardname)]
          [:div boardname])]
       [:div.cell.cell--20.center
        [:div (cond
                (not published)
                [:span.preview (i18n/localize lang :pdf.preview)]

                (and (not contract?) (not legacy-kt-ymp?))
                (i18n/localize lang (case category-kw
                                      :p :pdf.poikkeamispaatos
                                      :attachmentType.paatoksenteko.paatos)))]]
       [:div.cell.cell--40.right
        [:div.permit (loc-fn (cond
                               legacy-kt-ymp? :attachmentType.paatoksenteko.paatos
                               contract?      :pate.verdict-table.contract
                               :else          (case category-kw
                                                :ya        [:pate.verdict-type
                                                            (dict-value verdict :verdict-type)]
                                                :legacy.ya [:pate.verdict-type
                                                            (schema-util/ya-verdict-type application)]
                                                [:pdf category :permit])))]]])
    [:div.row
     [:div.cell.cell--40
      (add-unit lang :section (dict-value verdict :verdict-section))]
     [:div.cell.cell--20.center
      [:div (dict-value verdict :verdict-date)]]
     [:div.cell.cell--40.right (i18n/localize lang :pdf.page) " " [:span#page-number ""]]]]])

(defn verdict-footer []
  [:div.footer
   [:div.section
    [:div.row.pad-after.pad-before
     [:cell.cell--100 {:dangerouslySetInnerHTML {:__html "&nbsp;"}}]]]])

(defn language [verdict]
  (-> verdict :data :language))

(defn verdict-html
  "Source-data is a map containing keys referred in pdf-layout source
  definitions. Returns :header, :body, :footer map."
  [application verdict source-data pdf-layout]
  {:body   (html (content source-data pdf-layout))
   :header (html (verdict-header (language verdict) application verdict) true)
   :footer (html (verdict-footer))})
