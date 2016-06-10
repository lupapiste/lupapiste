(ns lupapalvelu.pdf.pdf-export
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [clojure.java.io :as io]
            [pdfa-generator.core :as pdf]
            [clj-time.local :as tl]
            [clj-time.format :as tf]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [sade.util :as util]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream File]
           [javax.imageio ImageIO]))

; *** Deprecated ****
; This whole class is deprecated. Use libre-template instead.

; ----------------------- combining schema and data

(def- not-nil? (complement nil?))

(defn- combine-schema-field-and-value
  "Gets the field name and field value from a datum field and schema"
  [{field-name :name field-type :type, :keys [i18nkey body] :as field-schema} data locstring]
  (let [locstring (str locstring "." field-name)
        localized-field-name (cond
                               (not-nil? i18nkey) (loc i18nkey)
                               (= :select field-type) (loc locstring "_group_label")
                               :else (loc locstring))

        field-name-key (keyword field-name)

        ; extract, cast and localize field-value according to type
        field-value (-> data field-name-key :value)

        subschema (util/find-first #(= field-value (:name %)) body)

        localized-field-value (cond
                                (= :checkbox field-type) (loc (if field-value "yes" "no"))
                                (:i18nkey subschema) (loc (:i18nkey subschema))
                                (and (= field-value "other") (= :select field-type)) (loc "select-other")
                                (and field-value (not (ss/blank? field-value)) (= :select field-type)) (loc (if i18nkey i18nkey locstring) field-value)
                                :else field-value)]
    [localized-field-name localized-field-value]))

(defn- collect-fields
  "Map over the fields in a schema, pulling the data and fieldname from both"
  [field-schemas data locstring]
  (map #(combine-schema-field-and-value % data locstring) field-schemas))

; Helpers for filtering schemas
; (at least :personSelector and :radioGroup do not belong to either printable groups or fields
(def- printable-group-types #{:group :table})
(defn- is-printable-group-type [schema] (printable-group-types (:type schema)))

(def- field-types #{:string :checkbox :select :date :text :hetu :radioGroup})
(defn- is-field-type [schema] (and (not (:hidden schema)) (field-types (:type schema))))

(declare collect-groups)

(defn- removable-groups [schema doc-data]
  (let [subschemas (:body schema)
        schema-names (map :name subschemas)
        selected-key schemas/select-one-of-key]
    (when (some #(= selected-key %) schema-names)
      (let [select-schema (first (filter #(= (:name %) selected-key) subschemas))
            select-options (map :name (:body select-schema))
            default-selection (:default select-schema)
            selected-val (get-in doc-data [(keyword selected-key) :value] default-selection)]
        (remove #(= selected-val %) select-options)))))

(defn- table? [schema] (= (:type schema) :table))

(defn- index-sorted-values [doc]
  (map second (sort-by #(util/->int (first %)) doc)))

(defmulti filter-fields-by-group-subtype (fn [_ group-schema & _]
                                           (:subtype group-schema)))

(defmethod filter-fields-by-group-subtype :default [fields & _] fields)

(defmethod filter-fields-by-group-subtype :foreman-tasks
  [fields _ doc]
  ;; Only include those foreman tasks that correspond to the the selected role.
  (let [code (->> schemas/kuntaroolikoodi-tyonjohtaja-v2 first :body
                  (util/find-first #(= (:name %) (-> doc :kuntaRoolikoodi :value)))
                  :code)]
    (filter #(contains? (set (:codes %)) code) fields)))

(defn- collect-single-group
  "Build a map from the data of a single group. Groups can be in document root or inside other groups"
  [group-schema group-doc locstring doc]
  (let [subgroups (->> (:body group-schema)
                       (filter is-printable-group-type)
                       (remove :exclude-from-pdf)
                       (remove :hidden))
        group-schema-body (remove #(= schemas/select-one-of-key (:name %)) (:body group-schema)) ; remove _selected radioGroup
        fields (->> (filter is-field-type group-schema-body)
                    (remove :exclude-from-pdf))
        fields (filter-fields-by-group-subtype fields group-schema doc)
        group-title (:name group-schema)
        i18name (:i18name group-schema)
        locstring (cond
                    (not-nil? i18name) i18name
                    :else (str locstring "." group-title))
        i18nkey (:i18nkey group-schema)
        group-title (cond
                      (not-nil? i18nkey) i18nkey
                      :else (str locstring "._group_label"))
        repeating? (:repeating group-schema)
        group-doc (if repeating?
                    (index-sorted-values group-doc)
                    [group-doc])

        group-collect-fn (fn [doc]                          ; if the group contains a radio button selection, it's schema needs adjustments based on the selection
                           (let [unselected-groups (set (removable-groups group-schema doc))
                                 subgroups (remove #(unselected-groups (:name %)) subgroups)]
                             (collect-groups subgroups doc locstring)))
        group-type (:type group-schema)]
    ; a group consists of a title, maybe fields in the body of the doc and maybe groups containing more fields/groups
    (array-map
      :title (loc group-title)
      :fields (if (table? group-schema)
                (map #(collect-fields fields % locstring) group-doc)
                (mapcat #(collect-fields fields % locstring) group-doc))
      :groups (mapcat group-collect-fn group-doc)
      :type group-type)))

(defn- group-data [group-schema doc]
  ((keyword (:name group-schema)) doc))

(defn- collect-groups
  "Iterate over data groups in document, building a data map of each group"
  [group-schemas doc locstring]
  (map #(collect-single-group % (group-data % doc) locstring doc) group-schemas))

(defn- collect-single-document
  "Build a map of the data of a single document. Entry point for recursive traversal of document data."
  [doc]
  (let [schema (schemas/get-schema (:schema-info doc))
        doc-data (:data doc)
        unselected-groups (removable-groups schema doc-data)
        schema-body (remove #(= schemas/select-one-of-key (:name %)) (:body schema)) ; don't print _selected radioGroups
        group-schemas (->> schema-body
                           (filter is-printable-group-type)
                           (remove :exclude-from-pdf)
                           (remove :hidden)
                           (remove #(some #{(:name %)} unselected-groups)))
        field-schemas (->> (filter is-field-type schema-body)
                           (remove :exclude-from-pdf))
        doc-title (-> schema :info :name)
        doc-title (if (#{:op} (:schema-info doc))
                    (str "operations." (-> doc :schema-info :op :name))
                    doc-title)
        doc-desc (-> doc :schema-info :op :description)
        i18name (-> schema :info :i18name)
        locstring (if-not (nil? i18name)
                    i18name
                    doc-title)]
    ; document is (almost) like just another group
    (array-map
      :title doc-title
      :title-desc doc-desc
      :fields (collect-fields field-schemas doc-data locstring)
      :groups (collect-groups group-schemas doc-data locstring))))

(defn- doc-order-comparator [x y]
  (cond
    (nil? x) 1
    (nil? y) -1
    :else (compare x y)))

(defn- sort-docs [docs]
  (let [ordered-docs (sort-by #(-> % :schema-info :order) doc-order-comparator docs)
        type-grouped-docs (group-by #(= "party" (-> % :schema-info :type)) ordered-docs)
        party-docs (type-grouped-docs true)
        non-party-docs (type-grouped-docs false)]
    (concat non-party-docs party-docs)))

(defn- decorate-doc-with-op [app doc]
  (let [doc-op-id (-> doc :schema-info :op :id)
        operations (conj (seq (:secondaryOperations app)) (:primaryOperation app))
        doc-op (first (filter #(= doc-op-id (:id %)) operations))]
    (if-not (nil? doc-op)
      (assoc-in doc [:schema-info :op] doc-op)
      doc)))

(defn- collect-documents
  "Map over all application documents, collecting correctly structured data of each"
  [app]
  (let [docs (:documents app)
        decorated-docs (map (comp (partial decorate-doc-with-op app) schemas/with-current-schema-info) docs)]
    (map collect-single-document (sort-docs decorated-docs))))

(defn- get-authority [{authority :authority :as application}]
  (if (and (:authority application)
           (domain/assigned? application))
    (str (:lastName authority) " " (:firstName authority))
    (loc "application.export.empty")))

(defn- get-operations [{:keys [primaryOperation secondaryOperations]}]
  (ss/join ", " (map (fn [[op c]] (str (if (> c 1) (str c " \u00D7 ")) (loc "operations" op)))
                     (frequencies (map :name (remove nil? (conj (seq secondaryOperations) primaryOperation)))))))

(defn- collect-common-fields
  "Static header section does not need to be formed using schema"
  [app]
  (array-map
    (loc "application.muncipality") (loc (str "municipality." (:municipality app)))
    (loc "application.export.state") (loc (:state app))
    (loc "kiinteisto.kiinteisto.kiinteistotunnus") (p/to-human-readable-property-id (:propertyId app))
    (loc "submitted") (str (or (util/to-local-date (:submitted app)) "-"))
    (loc "application.id") (:id app)
    (loc "applications.authority") (get-authority app)
    (loc "application.address") (:address app)
    (loc "applicant") (clojure.string/join ", " (:_applicantIndex app))
    (loc "operations") (get-operations app)))

; Deprecated, statement is replaced with replaced with libre-template
(defn- collect-statement-fields [statements]
  (map
    (fn [{:keys [requested given status text dueDate reply] {giver :name} :person :as stm}]
      (cond->> [(loc "statement.requested") (str "" (or (util/to-local-date requested) "-"))
                (loc "statement.giver") (if (ss/blank? giver) (loc "application.export.empty") (str giver))
                (loc "export.statement.given") (str "" (or (util/to-local-date given) "-"))
                (loc "statement.title") (if (ss/blank? status) (loc "application.export.empty") (str status))
                (loc "statement.statement.text") (if (ss/blank? text) (loc "application.export.empty") (str text))
                (loc "add-statement-giver-maaraaika") (str "" (or (util/to-local-date dueDate) "-"))]
               reply (#(conj % (loc "statement.reply.text") (if (:nothing-to-add reply) (loc "statement.nothing-to-add.label") (:text reply))))
               true (apply array-map)))
    statements))

(defn collect-neighbour-fields [neighbours]
  (map
    (fn [{:keys [propertyId status] {owner-name :name} :owner}]
      (let [final-status (last status)
            user (:user final-status)
            vetuma (:vetuma final-status)
            signature (if (empty? vetuma) user vetuma)
            state (:state final-status)
            message (:message final-status)]
        (array-map
          (loc "neighbors.edit.propertyId") (str propertyId)
          (loc "neighbors.edit.name") (if (ss/blank? owner-name) (loc "application.export.empty") (str owner-name))
          (loc "neighbors.status") (loc (str "neighbor.state." state))
          (loc "neighbor.show.message") (if (ss/blank? message) (loc "application.export.empty") (str message))
          (loc "export.signed.electronically") (str
                                                 (:firstName signature)
                                                 " "
                                                 (:lastName signature)
                                                 ", "
                                                 (or (util/to-local-date (:created final-status)) "-")))))
    neighbours))

(defn collect-rakennus [rakennus]
  (map
    (fn [[k v]]
      (array-map
        (loc "task-katselmus.rakennus.rakennusnumero") (get-in v [:rakennusnro :value])
        (loc "task-katselmus.rakennus.kiinteistotunnus") (get-in v [:kiinttun :value])
        (loc "task-katselmus.rakennus.jarjestysnumero") (get-in v [:jarjestysnumero :value])
        (loc "task-katselmus.rakennus.valtakunnallinenNumero") (get-in v [:valtakunnallinenNumero :value])
        (loc "task-katselmus.rakennus.kunnanSisainenPysyvaRakennusnumero") (get-in v [:kunnanSisainenPysyvaRakennusnumero :value])
        (loc "task-katselmus.rakennus.tila.kayttoonottava") (if (get-in v [:kayttoonottava :value]) (loc "yes") (loc "no"))
        (loc "task-katselmus.rakennus.tila.tila._group_label") (get-in v [:tila :value]))) rakennus))

(defn collect-task-fields [tasks]
  (map
    (fn [{:keys [duedate taskname closed created state schema-info source assignee data]}]
      (let [i18n-prefix (:i18nprefix schema-info)
            katselmuksenLaji (get-in data [:katselmuksenLaji :value])
            vaadittuLupaehtona (get-in data [:vaadittuLupaehtona :value])
            pitoPvm (get-in data [:katselmus :pitoPvm :value])
            pitaja (get-in data [:katselmus :pitaja :value])
            huomautus-kuvaus (get-in data [:katselmus :huomautukset :kuvaus :value])
            huomautus-maaraAika (get-in data [:katselmus :huomautukset :maaraAika :value])
            huomautus-toteaja (get-in data [:katselmus :huomautukset :toteaja :value])
            huomautus-toteasHetki (get-in data [:katselmus :huomautukset :toteamisHetki :value])
            lasnaolijat (get-in data [:katselmus :lasnaolijat :value])
            poikkeamat (get-in data [:katselmus :poikkeamat :value])
            tila (get-in data [:katselmus :tila :value])
            rakennus (:rakennus data)]
        (array-map
          (loc "task-katselmus.katselmuksenLaji._group_label") (if (ss/blank? katselmuksenLaji) "-" (loc (str i18n-prefix "." katselmuksenLaji)))
          (loc "vaadittuLupaehtona") (if vaadittuLupaehtona (loc "yes") (loc "no"))
          (loc "task-katselmus.katselmus.pitoPvm") (if (ss/blank? pitoPvm) (loc "application.export.empty") pitoPvm)
          (loc "task-katselmus.katselmus.pitaja") pitaja
          (loc "task-katselmus.katselmus.huomautukset.kuvaus") huomautus-kuvaus
          (loc "task-katselmus.katselmus.huomautukset.maaraAika.export") (if (ss/blank? huomautus-maaraAika) (loc "application.export.empty") huomautus-maaraAika)
          (loc "task-katselmus.katselmus.huomautukset.toteaja.export") huomautus-toteaja
          (loc "task-katselmus.katselmus.huomautukset.toteamisHetki.export") huomautus-toteasHetki
          (loc "task-katselmus.katselmus.lasnaolijat") lasnaolijat
          (loc "task-katselmus.katselmus.poikkeamat") poikkeamat
          (loc "task-katselmus.katselmus.tila._group_label") tila
          (loc "osapuoli.patevyys.Liiteet") "0"
          :rakennus (collect-rakennus rakennus))))
    tasks))

(defn- collect-export-data
  "Create a map containing combined schema and data for pdf export"
  [app title show-address?]
  {:title         title
   :address       (:address app)
   :common-fields (collect-common-fields app)
   :documents     (collect-documents app)})


; ----------------------- generating PDF

(def- single-column-table-opts {:border false :bounding-box [1 1] :cell-border false})
(def- two-column-table-opts {:border false :bounding-box [2 2] :cell-border false})
(def- table-cell-table-opts {:border false :cell-border false})

(defn- table-cell [header content cols]
  [:pdf-table table-cell-table-opts [cols]
   [[:pdf-cell {:border false} [:paragraph {:style :bold :size 9} header]]]
   [[:pdf-cell {:border false} (cond
                                 (ss/blank? content) (loc "application.export.empty")
                                 :else (str content))]]
   ])

(defn- single-col-table-cell [header content] (table-cell header content 1))
(defn- two-col-table-cell [header content] (table-cell header content 2))

(defn- single-col-pdf-row [row]
  [[:pdf-cell (apply single-col-table-cell row)]])

(defn- single-column-pdf-table [data]
  (let [rows (seq data)]
    `[:pdf-table ~single-column-table-opts [1]
      ~@(map (fn [row] (single-col-pdf-row row)) rows)
      ]))

(defn- two-col-pdf-row [row]
  (case (count row)
    1 (let [cell (first row)]
        [[:pdf-cell {:colspan 2} (apply two-col-table-cell cell)] [:pdf-cell ""]])
    2 (let [left-cell (first row)
            right-cell (second row)]
        [[:pdf-cell (apply two-col-table-cell left-cell)]
         [:pdf-cell (apply two-col-table-cell right-cell)]])))

(defn- two-column-pdf-table [data]
  (let [rows (partition-all 2 (seq data))]
    `[:pdf-table ~two-column-table-opts [1 1]
      ~@(map (fn [row] (two-col-pdf-row row)) rows)
      ]))

(defn- group-section-header [group]
  [
   [:pdf-table single-column-table-opts [1]
    [[:pdf-cell {:border false} [:paragraph {:size 10 :style :bold} (:title group)]]]
    ]])

(defn- long-fields [fields]
  (let [longest-string (apply max (map (fn [[k v]] (apply max [(count (str k)) (count (str v))])) fields))]
    (> longest-string 100)))

(defn- render-fields [fields]
  [
   (if (long-fields fields)
     (single-column-pdf-table fields)
     (two-column-pdf-table fields))])

(defmulti ^:private render-group-by-type (fn [group] (:type group)))

(defn- render-group [group]
  (let [fields (:fields group)
        subgroups (:groups group)]
    `[
      ~@(group-section-header group)
      ~@(when (not-empty fields)
          (render-fields fields))
      ~@(when (not-empty subgroups)
          (mapcat render-group-by-type subgroups))
      ]))



(defn- render-table-group-row [row & {:keys [style] :or {style :normal}}]
  (map (fn [value]
         [:pdf-cell [:paragraph {:size 7 :style style} value]])
       row))

(defn- headers [row]
  (map first row))

(defn- values [row]
  (map second row))

(defn- render-table-group [group]
  (when-let [rows (seq (:fields group))]
    (let [col-count (count (first rows))
          col-width (/ 1 col-count)]
      `[
        ~@(group-section-header group)
        [:pdf-table {:bounding-box [1 1]} ~(repeat col-count col-width)
         ~(render-table-group-row (headers (first rows)) :style :bold :size 10)
         ~@(map render-table-group-row (map values rows))]
        ])))

(defmethod render-group-by-type :table [group]
  (render-table-group group))

(defmethod render-group-by-type :default [group]
  (render-group group))

(defn- localized-title [doc]
  (let [description (:title-desc doc)
        desc-postfix (str " - " description)
        title (loc (str (:title doc) "." "_group_label"))]
    (str
      title
      (when-not (ss/blank? description)
        desc-postfix))))

(defn- document-section-header [title]
  [[:pdf-table single-column-table-opts [1]
    [[:pdf-cell {:color [230 230 230] :valign :middle} [:paragraph {:size 12 :style :bold} title]]]]])

; TODO check that document group order matches ui
(defn- render-single-document [doc]
  (let [fields (:fields doc)
        groups (:groups doc)]
    `[
      ~@(document-section-header (localized-title doc))
      ~@(when (not-empty groups)
          `[
            ~@(mapcat render-group-by-type groups)
            [:spacer]])
      ~@(when (not-empty fields)
          (render-fields fields))
      [:spacer]
      ]))

(defn- common-header [app-data]
  [
   [:image {:xscale 1 :yscale 1} (ImageIO/read (io/resource "public/lp-static/img/logo-v2-flat.png"))]
   [:spacer]
   [:heading {:style {:size 20}} (:title app-data)]
   [:spacer]
   [:heading {:style {:size 15}} (:address app-data)]
   [:spacer]
   [:line]
   [:spacer]
   ])

(defn- common-fields [common-field-data]
  [
   (two-column-pdf-table common-field-data)
   [:spacer]
   ])

(defn- section-header [title]
  [
   [:heading {:style {:size 15}} title]
   [:line]
   [:spacer]
   ])

(defn pdf-metadata []
  {:title  "Lupapiste.fi"
   :size   "a4"
   :footer {:text  (ss/join " - " [(loc "application.export.name")
                                   (tf/unparse (tf/formatter-local "dd.MM.yyyy HH:mm") (tl/local-now))
                                   (loc "application.export.page")])
            :align :right}
   :pages  true})


(defn- gen-pdf-data [{subtype :permitSubtype :as app} out]
  (let [title (if (ss/blank? subtype)
                (loc "application.export.title")
                (loc "permitSubtype" subtype))
        app-data (collect-export-data app title true)
        ; Below, the quote - splice-unquote -syntax (i.e. `[~@(f x y)]) "unwraps" the vector returned by each helper
        ; function into the body of the literal vector defined here.
        ; E.g. if (defn x [] [3 4]) then:
        ; [1 2 (x)] -> [1 2 [3 4]]
        ; `[1 2 ~@(x)] -> [1 2 3 4]
        pdf-data `[~(pdf-metadata)
                   ~@(common-header app-data)
                   ~@(common-fields (:common-fields app-data))
                   ~@(section-header (loc "application.export.subtitle"))
                   ~@(map render-single-document (:documents app-data))]]
    (pdf/pdf pdf-data, out)))

(defn generate
  ([application lang]
   (let [out (ByteArrayOutputStream.)]
     (with-lang lang
                (gen-pdf-data application out)
                (ByteArrayInputStream. (.toByteArray out)))))
  ([application lang file]
   (let [stream (generate application lang)]
     (with-open [out (io/output-stream file)]
       (io/copy stream out)))))

(defn- render-fields-plain [stm]
  `[~@(render-fields stm) [:spacer]])

(defn- render-tasks [fields]
  (let [title (loc "application.building")
        empty (loc "ei-tiedossa")
        buildings (:rakennus fields)]
    `[~@(render-fields (take 12 fields))
      [:pagebreak]
      ~@(document-section-header (loc "application.building"))
      [:pdf-table {:border false :bounding-box [1 1] :cell-border false} [1]
       ~@(map (fn [rak] `[[:pdf-cell
                           [:pdf-table {:border false :bounding-box [1 1] :cell-border false} [1]
                            [[:pdf-cell {:border false} [:paragraph {:style :bold :size 9} ~title]]]
                            [[:pdf-cell {:border false}
                              ~@(render-fields rak)]]]]]) buildings)
       [[:pdf-cell {:border false} [:paragraph {:style :bold :size 9} ~(if (empty? buildings) empty "")]]]
       ]]))

(defn- child-renderer [type]
  (cond
    (= type :documents) render-single-document
    (= type :tasks) render-tasks
    :else render-fields-plain))

(defn- generate-pdf-data-with-child [{subtype :permitSubtype :as app} child-type id lang]
  (with-lang lang (let [title (cond
                                (= child-type :statements) (loc "lausunto")
                                (= child-type :neighbors) (loc "application.neighbors")
                                (= child-type :verdicts) (loc "application.verdict.title")
                                (= child-type :tasks) (loc "task-katselmus.rakennus.tila._group_label")
                                (ss/blank? (str subtype)) (loc "application.export.title")
                                :else (loc "permitSubtype" subtype))
                        app-data (collect-export-data app title false)
                        child (filter #(= id (:id %)) (child-type app))
                        child-data (cond
                                     (= child-type :statements) (collect-statement-fields child)
                                     (= child-type :neighbors) (collect-neighbour-fields child)
                                     (= child-type :tasks) (collect-task-fields child)
                                     (= child-type :verdicts) nil
                                     :else (collect-documents app))]
                    ; Below, the quote - splice-unquote -syntax (i.e. `[~@(f x y)]) "unwraps" the vector returned by each helper
                    ; function into the body of the literal vector defined here.
                    ; E.g. if (defn x [] [3 4]) then:
                    ; [1 2 (x)] -> [1 2 [3 4]]
                    ; `[1 2 ~@(x)] -> [1 2 3 4]
                    ;(debug "child: " (with-out-str (clojure.pprint/pprint child)))
                    ;(debug "child-data: " (with-out-str  (clojure.pprint/pprint child-data)))
                    `[~(pdf-metadata)
                      ~@(common-header app-data)
                      ~@(common-fields (:common-fields app-data))
                      ~@(document-section-header title)
                      ~@(map (child-renderer child-type) child-data)])))

(defn generate-pdf-with-child
  ([app child-type id lang out]
   "Generates PDF from given child type (Statement, Verdict (todo), Document) to given ByteArrayInputStream (out). "
   (pdf/pdf (generate-pdf-data-with-child app child-type id lang), out))

  ([app child-type id lang]
   "Returns PDF ByteArrayInputStream of given application child (Document, Statemtn, Verdict)"
   (let [out (ByteArrayOutputStream.)]
     (generate-pdf-with-child app child-type id lang out)
     (ByteArrayInputStream. (.toByteArray out)))))

(defn generate-pdf-a-application-to-file
  "Returns application data in PDF/A temp file"
  [application lang]
  (let [file (File/createTempFile "application-pdf-a-" ".tmp")]
    (generate application lang file)
    (pdf-conversion/ensure-pdf-a-by-organization file (:organization application))
    file))
