(ns lupapalvelu.pdf.pdf-export
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [clojure.java.io :as io]
            [pdfa-generator.core :as pdf]
            [clj-time.local :as tl]
            [clj-time.format :as tf]
            [lupapalvelu.i18n :refer [loc] :as i18n]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [sade.core :refer :all]
            [sade.files :as files]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [javax.imageio ImageIO]))

; *** Deprecated ****
; This whole class is deprecated. Use libre-template instead.

; ----------------------- combining schema and data

(def- not-nil? (complement nil?))

(defn- combine-schema-field-and-value
  "Gets the field name and field value from a datum field and schema"
  [{field-name :name field-type :type, :keys [i18nkey body] :as field-schema} data i18npath]
  (let [locstring (ss/join "." (conj i18npath field-name))
        localized-field-name (cond
                               (not-nil? i18nkey) (loc i18nkey)
                               (= :select field-type) (loc locstring "_group_label")
                               :else (loc locstring))

        ; extract, cast and localize field-value according to type
        field-value (get-in data [(keyword field-name) :value])

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
  [field-schemas doc path i18npath]
  (map #(combine-schema-field-and-value % (get-in doc path) i18npath) field-schemas))

; Helpers for filtering schemas
; (at least :personSelector and :radioGroup do not belong to either printable groups or fields
(def- printable-group-types #{:group :table})
(defn- is-printable-group-type [schema] (printable-group-types (:type schema)))

(def- field-types #{:string :checkbox :select :date :text :hetu :radioGroup})
(defn- is-field-type [schema] (and (not (:hidden schema)) (field-types (:type schema))))

(declare collect-groups)

(defn- removable-groups [doc schema path]
  (let [select-schema (util/find-first schemas/select-one-of-schema? (:body schema))
        selection (get-in doc (conj path (keyword schemas/select-one-of-key) :value) (:default select-schema))]
    (->> (:body select-schema)
         (map :name)
         (remove #{selection})
         set)))

(defn- table? [schema] (= (:type schema) :table))

(defmulti filter-fields-by-group-subtype (fn [_ group-schema _]
                                           (:subtype group-schema)))

(defmethod filter-fields-by-group-subtype :default [_ _ fields] fields)

(defmethod filter-fields-by-group-subtype :foreman-tasks
  [doc _ fields]
  ;; Only include those foreman tasks that correspond to the the selected role.
  (let [code (->> schemas/kuntaroolikoodi-tyonjohtaja-v2 first :body
                  (util/find-first #(= (:name %) (-> doc :kuntaRoolikoodi :value)))
                  :code)]
    (filter #(contains? (set (:codes %)) code) fields)))

(defn- get-value-by-path
  "Get element value by target-path string. target-path can be absolute (/path/to/element) or ralative (sibling/element).
  If target path is given as relative, parent-path is used to resoleve absolute path."
  [doc parent-path target-path]
  (let [absolute-path (->> (ss/split target-path #"/")
                           ((fn [path] (if (ss/blank? (first path)) (rest path) (concat parent-path path))))
                           (mapv keyword))]
    (get-in doc (conj absolute-path :value))))

(defn- hide-by-hide-when [doc parent-path {hide-when :hide-when :as schema}]
  (and hide-when ((:values hide-when) (get-value-by-path doc parent-path (:path hide-when)))))

(defn- show-by-show-when [doc parent-path {show-when :show-when :as schema}]
  (or (not show-when) ((:values show-when) (get-value-by-path doc parent-path (:path show-when)))))

(defn- filter-subschemas-by-data [doc group-schema path subschemas]
  (->> (remove (comp (removable-groups doc group-schema path) :name) subschemas)
       (remove (partial hide-by-hide-when doc path))
       (filter (partial show-by-show-when doc path))))

(defn- get-subschemas
  "Returns group subschemas as hash-map. Index of a repeating group is conjoined in [:path :to :group].
  {[:path :to :group :0] {:fields [field-type-schemas] :groups [group-type-schemas]}}."
  [doc group-schema path]
  (let [paths      (if (:repeating group-schema)
                     (->> (get-in doc path) keys (sort-by util/->int) (map (partial conj path)))
                     [path])
        subschemas (->> (:body group-schema)
                        (remove :exclude-from-pdf)
                        (remove :hidden)
                        (remove schemas/select-one-of-schema?)
                        (remove #(util/=as-kw :calculation (:type %)))
                        (filter-fields-by-group-subtype doc group-schema))
        fields     (filter is-field-type subschemas)
        groups     (filter is-printable-group-type subschemas)]
    (->> (map #(hash-map :fields (filter-subschemas-by-data doc group-schema % fields)
                         :groups (filter-subschemas-by-data doc group-schema % groups)) paths)
         (zipmap paths))))

(defn- collect-single-group
  "Build a map from the data of a single group. Groups can be in document root or inside other groups"
  [doc group-schema path i18npath]
  (let [subschemas (get-subschemas doc group-schema path)]

    (array-map :title  (loc (or (:i18nkey group-schema)
                                (:i18name group-schema)
                                (conj i18npath "_group_label")))
               :fields (cond->> (map (fn [[path {schemas :fields}]] (collect-fields schemas doc path i18npath)) subschemas)
                         (not (table? group-schema)) (apply concat))
               :groups (mapcat (fn [[path {schemas :groups}]] (collect-groups schemas doc path i18npath)) subschemas)
               :type   (:type group-schema))))

(defn- collect-groups
  "Iterate over data groups in document, building a data map of each group"
  [group-schemas doc path i18npath]
  (map (fn [{name :name :as schema}] (collect-single-group doc schema (conj path (keyword name)) (conj i18npath name))) group-schemas))

(defn- collect-single-document
  "Build a map of the data of a single document. Entry point for recursive traversal of document data."
  [{:keys [data schema-info] :as doc}]
  (let [schema (schemas/get-schema (:schema-info doc))
        op     (:op schema-info)
        subschemas (-> (get-subschemas data schema []) first val) ; root data is never repeating
        doc-name (or (-> schema :info :i18name not-empty) (-> schema :info :name))]

    (array-map :title (if (ss/not-blank? (:name op)) (str "operations." (:name op)) (-> schema :info :name))
               :title-desc (:description op)
               :fields (collect-fields (:fields subschemas) data [] [doc-name])
               :groups (collect-groups (:groups subschemas) data [] [doc-name]))))

(defn- doc-order-comparator [x y]
  (cond
    (identical? x y)  0
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
        (loc "task-katselmus.rakennus.rakennusnumero") (get-in v [:rakennus :rakennusnro :value])
        (loc "task-katselmus.rakennus.kiinteistotunnus") (get-in v [:rakennus :kiinttun :value])
        (loc "task-katselmus.rakennus.jarjestysnumero") (get-in v [:rakennus :jarjestysnumero :value])
        (loc "task-katselmus.rakennus.valtakunnallinenNumero") (get-in v [:rakennus :valtakunnallinenNumero :value])
        (loc "task-katselmus.rakennus.kunnanSisainenPysyvaRakennusnumero") (get-in v [:rakennus :kunnanSisainenPysyvaRakennusnumero :value])
        (loc "task-katselmus.rakennus.tila.kayttoonottava") (if (get-in v [:tila :kayttoonottava :value]) (loc "yes") (loc "no"))
        (loc "task-katselmus.rakennus.tila.tila._group_label") (get-in v [:tila :tila :value]))) rakennus))

(defn collect-task-fields [tasks attachment-count app]
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
          (loc "osapuoli.patevyys.Liiteet") (str attachment-count)
          :rakennus (collect-rakennus rakennus)
          :permit-type (:permitType app))))
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

(defn- localized-title [{:keys [title title-desc]}]
  (let [desc-postfix (str " - " title-desc)
        loc-title (if (i18n/has-term? i18n/*lang* title)
                    (loc title)
                    (loc title :_group_label))]
    (str loc-title (when-not (ss/blank? title-desc) desc-postfix))))

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
     (i18n/with-lang lang
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
        buildings (:rakennus fields)
        permit-type (:permit-type fields)]
    (if (not (#{:YA} (keyword permit-type)))
      `[~@(render-fields (take 12 fields))
        [:pagebreak]
          ~@(document-section-header (loc "application.building"))
          [:pdf-table {:border false :bounding-box [1 1] :cell-border false} [1]
           ~@(map (fn [rak] `[[:pdf-cell
                               [:pdf-table {:border false :bounding-box [1 1] :cell-border false} [1]
                                [[:pdf-cell {:border false} [:paragraph {:style :bold :size 9} ~title]]]
                                [[:pdf-cell {:border false}
                                  ~@(render-fields rak)]]]]]) buildings)
           [[:pdf-cell {:border false} [:paragraph {:style :bold :size 9} ~(if (empty? buildings) empty "")]]]]
        ]
      `[~@(render-fields (take 12 fields))]
    )))

(defn- child-renderer [type]
  (cond
    (= type :documents) render-single-document
    (= type :tasks) render-tasks
    :else render-fields-plain))

(defn- task-attachment-count [{:keys [attachments]} {task-id :id}]
  (->> attachments
       (filter (fn [{:keys [target source]}] (and (= (keyword (:type target)) :task)
                                                  (= (:id target) task-id)
                                                  ;; Do not count the existing attachment generated from this task, if any
                                                  (or (not= (keyword (:type source)) :tasks)
                                                      (not= (:id source) task-id)))))
       count))

(defn- generate-pdf-data-with-child [{subtype :permitSubtype :as app} child-type id lang]
  (i18n/with-lang lang
    (let [title (cond
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
                       (= child-type :tasks) (collect-task-fields child (task-attachment-count app (first child)) app)
                       (= child-type :verdicts) nil
                       :else (collect-documents app))
          permit-type (:permitType app)]
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

(defn ^java.io.InputStream generate-application-pdfa
  "Returns application data in a self-destructing input stream to a PDF/A document"
  [application lang]
  (let [file (files/temp-file "application-pdf-a-" ".tmp")] ; deleted via temp-file-input-stream
    (generate application lang file)
    (pdf-conversion/ensure-pdf-a-by-organization file (:organization application))
    (files/temp-file-input-stream file)))
