(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts. Utilises a simple
  schema-based mechanism for the layout definiton and generation."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as common]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.property :as property]
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
   :else (shared/only-one-of [:doc :dict]
                             ;; Vector is a path to applicatio
                             ;; document data. The first item is the
                             ;; document name and rest are path within
                             ;; the data.
                             {(sc/optional-key :doc)  [sc/Keyword]
                              ;; Kw-path into published verdict data.
                              (sc/optional-key :dict) sc/Keyword})))

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
  defines both the title and the data source for the whole entry."
  [(sc/one {;; Localisation key for the row (left-hand) title.
            :loc                        sc/Keyword
            ;; If :loc-many is given it is used as the title key if
            ;; the source value denotes multiple values.
            (sc/optional-key :loc-many) sc/Keyword
            (sc/optional-key :source)   Source
            (sc/optional-key :styles)   (styles row-styles)}
           {})
   ;; Note that the right-hand side can consist of multpiple
   ;; cells/columns. As every property is optional, the cells can be
   ;; omitted. In that case, the value of the right-hand side is the
   ;; source value.
   (shared/only-one-of [:value :text]
                       ;; Path within the source value. Useful, when the value is a map.
                       {(sc/optional-key :path)       shared/path-type
                        ;; Textual representation that is static and
                        ;; independent from any source value.
                        (sc/optional-key :text)       shared/keyword-or-string
                        (sc/optional-key :width)      (apply sc/enum cell-widths)
                        (sc/optional-key :unit)       (sc/enum :ha :m2 :m3 :kpl)
                        ;; Additional localisation key prefix. Is
                        ;; applied both to path and text values.
                        (sc/optional-key :loc-prefix) shared/path-type
                        (sc/optional-key :styles)     (styles cell-styles)})])

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
                       [:div.header {:padding-bottom "1em"}]
                       [:div.footer {:padding-top "1em"}]
                       [:.page-break {:page-break-before :always}]
                       [:.section {:display :table
                                   :width   "100%"}
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
                           [:.cell {:padding-top "0.5em"}]]]]]])}}]]
         [:body body (when script?
                       page-number-script)]])))

(def pdf-layouts
  {:r {:left-width 30
       :entries    [[{:loc    :pate-verdict.application-id
                      :source :application-id
                      :styles [:bold :pad-after]}]
                    [{:loc    :rakennuspaikka._group_label
                      :styles :bold}]
                    [{:loc    :rakennuspaikka.kiinteisto.kiinteistotunnus
                      :source :property-id}]
                    [{:loc    :rakennuspaikka.kiinteisto.tilanNimi
                      :source {:doc [:rakennuspaikka :kiinteisto.tilanNimi]}}]
                    [{:loc    :pdf.pinta-ala
                      :source {:doc [:rakennuspaikka :kiinteisto.maapintaala]}}
                     {:unit :ha}]
                    [{:loc    :rakennuspaikka.kaavatilanne._group_label
                      :source {:doc [:rakennuspaikka :kaavatilanne]}
                      :styles :pad-after}
                     {:loc-prefix :rakennuspaikka.kaavatilanne}]
                    [{:loc    :pate-purpose
                      :source {:dict :purpose}
                      :styles :pad-after}]
                    [{:loc      :pdf.applicant
                      :loc-many :pdf.applicants
                      :source   :applicants
                      :styles   [:pad-after :border-bottom]}]
                    [{:loc      :applications.operation
                      :loc-many :operations
                      :source   :operations
                      :styles   [:bold :pad-before]}
                     {:loc-prefix :operations
                      :path       :name}]
                    [{:loc    :pate-extra-info
                      :source {:dict :extra-info}
                      :styles :pad-before}]
                    [{:loc    :pate.complexity
                      :source :complexity
                      :styles [:spaced :pad-after :pad-before]}]
                    [{:loc    :pate-rights
                      :source {:dict :rights}
                      :styles :pad-before}]
                    [{:loc    :pdf.design-complexity
                      :source :designers
                      :styles :pad-before}
                     {:path   :role
                      :styles :nowrap}
                     {:path       :difficulty
                      :width      100
                      :loc-prefix :osapuoli.suunnittelutehtavanVaativuusluokka}]
                    [{:loc      :pdf.designer
                      :loc-many :pdf.designers
                      :source   :designers
                      :styles   :pad-before}
                     {:path   :role
                      :styles :nowrap}
                     {:path  :person
                      :width 100}]
                    [{:loc    :verdict.kerrosala
                      :source :primary
                      :styles :pad-before}
                     {:path :mitat.kerrosala
                      :unit :m2}]
                    [{:loc    :verdict.kokonaisala
                      :source :primary}
                     {:path :mitat.kokonaisala
                      :unit :m2}]
                    [{:loc    :pdf.volume
                      :source :primary}
                     {:path :mitat.tilavuus
                      :unit :m3}]
                    [{:loc    :purku.mitat.kerrosluku
                      :source :primary}
                     {:path :mitat.kerrosluku}]
                    [{:loc    :pate-buildings.info.paloluokka
                      :source :paloluokka}]
                    [{:loc    :pdf.parking
                      :source :parking
                      :styles :pad-before}
                     {:path   :text
                      :styles :nowrap}
                     {:path   :amount
                      :styles :right}
                     {:text  ""
                      :width 100}]
                    [{:loc    :pate-deviations
                      :source {:dict :deviations}
                      :styles :pad-before}]
                    [{:loc :statement.lausunto
                      :loc-many :pate-statements
                      :source :statements
                      :styles [:bold :pad-before :border-top]}]
                    [{:loc :phrase.category.naapurit
                      :source {:dict :neighbors}
                      :styles [:bold :pad-before]}]
                    [{:loc      :pdf.attachment
                      :loc-many :verdict.attachments
                      :source   :attachments
                      :styles   [:bold :pad-before :pad-after]}
                     {:path   :text
                      :styles :nowrap}
                     {:path   :amount
                      :styles [:right :nowrap]
                      :unit   :kpl}
                     {:text  ""
                      :width 100}]
                    [{:loc    :pate-verdict
                      :source {:dict :verdict-code}
                      :styles [:bold :pad-before :border-top]}
                     {:loc-prefix :pate-r.verdict-code}]
                    [{:loc    :empty
                      :source {:dict :verdict-text}
                      :styles :pad-before}]
                    [{:loc      :pdf.required-foreman
                      :loc-many :verdict.vaaditutTyonjohtajat
                      :source   {:dict :foremen}
                      :styles   :pad-before}
                     {:loc-prefix :pate-r.foremen}]
                    [{:loc      :pdf.required-review
                      :loc-many :verdict.vaaditutKatselmukset
                      :source   :reviews
                      :styles   :pad-before}]
                    [{:loc      :pdf.required-plan
                      :loc-many :verdict.vaaditutErityissuunnitelmat
                      :source   :plans
                      :styles   :pad-before}]
                    [{:loc      :pdf.condition
                      :loc-many :pdf.conditions
                      :source   :conditions
                      :styles   [:pad-before :spaced]}]
                    [{:loc    :pate-verdict.muutoksenhaku
                      :source {:dict :appeal}
                      :styles [:bold :page-break]}]]}})

(sc/validate PdfLayout (:r pdf-layouts))

(defn applicants
  "Returns list of name, address maps for properly filled party
  documents. If a doc does not have name information it is omitted."
  [{app :application lang :lang}]
  (->> (domain/get-applicant-documents (:documents app))
       (map :data)
       (map (fn [{:keys [_selected henkilo yritys]}]
              (if (util/=as-kw :yritys)
                yritys
                henkilo)))
       (map (fn [{:keys [yritysnimi henkilotiedot osoite]}]
              {:name (ss/trim (or yritysnimi
                                  (format "%s %s"
                                          (:etunimi henkilotiedot)
                                          (:sukunimi henkilotiedot))))
               :address (let [{:keys [katu postinumero postitoimipaikannimi
                                      maa]} osoite]
                          (->> [katu (str postinumero " " postitoimipaikannimi)
                                (when (util/not=as-kw maa :FIN)
                                  (i18n/localize lang :country maa))]
                               (remove ss/blank?)
                               (map ss/trim)
                               (ss/join ", ")))}))
       (remove (util/fn-> :name ss/blank?))))

(defn- pathify [kw-path]
  (map keyword (ss/split (name kw-path) #"\.")))

(defn doc-value [application doc-name kw-path]
  (get-in (domain/get-document-by-name application (name doc-name))
          (cons :data (pathify kw-path))))

(defn dict-value [verdict kw-path]
  (get-in verdict (cons :data (pathify kw-path))))

(defn add-unit [lang unit v]
  (case unit
    :ha  (str v " " (i18n/localize lang :unit.hehtaaria))
    :m2  [:span v " m" [:sup 2]]
    :m3  [:span v " m" [:sup 3]]
    :kpl (str v " " (i18n/localize lang :unit.kpl)) ))

(defn complexity [lang verdict]
  (remove nil?
          [(i18n/localize lang
                          :pate.complexity
                          (dict-value verdict :complexity))
           (dict-value verdict :complexity-text)]))

(defn property-id [application]
  (->> [(-> application :propertyId
            property/to-human-readable-property-id)
        (doc-value application :rakennuspaikka :kiinteisto.maaraalaTunnus)]
       (remove ss/blank?)
       (ss/join "-")))

(defn value-or-other [lang value other & loc-keys]
  (if (util/=as-kw value :other)
    other
    (if (seq loc-keys)
      (i18n/localize lang loc-keys value)
      value)))

(defn designers [{lang :lang app :application}]
  (let [role-keys [:osapuoli :suunnittelija :kuntaRoolikoodi]
        head-loc (i18n/localize lang role-keys "p\u00e4\u00e4suunnittelija")
        heads    (->> (domain/get-documents-by-name app :paasuunnittelija)
                      (map :data)
                      (map #(assoc % :role head-loc)))
        others   (map :data
                      (domain/get-documents-by-name app :suunnittelija))]
    (->> (concat heads others)
         (remove nil?)
         (map (fn [{role-code    :kuntaRoolikoodi
                    other-role   :muuSuunnittelijaRooli
                    difficulty   :suunnittelutehtavanVaativuusluokka
                    info         :henkilotiedot
                    skills       :patevyys
                    degree       :koulutusvalinta
                    other-degree :koulutus
                    role         :role}]
                (let [designer-name (format "%s %s"
                                      (or (:etunimi info) "")
                                      (or (:sukunimi info) ""))]
                  (when-not (ss/blank? designer-name)
                    {:role       (or role
                                     (value-or-other lang
                                                     role-code other-role
                                                     role-keys))
                     :difficulty difficulty
                     :person (->> [designer-name
                                   (value-or-other lang
                                                   (:koulutusvalinta skills)
                                                   (:koulutus skills))]
                                  (map ss/trim)
                                  (remove ss/blank?)
                                  (ss/join ", "))}))))
         (remove nil?)
         (sort (fn [a b]
                 (if (= a head-loc) -1 1))))))

(defn resolve-source
  [{:keys [application verdict] :as data} {doc-source  :doc
                                           dict-source :dict
                                           :as         source}]
  (cond
    doc-source  (apply doc-value (cons application doc-source))
    dict-source (apply dict-value (cons verdict [dict-source]))
    :else       (when source (source data))))

(defn resolve-class [all selected & extra]
  (->> (util/intersection-as-kw all (flatten [selected]))
       (concat extra)
       (remove nil?)))

(defn resolve-cell [{lang :lang :as data} source-value
                    {:keys [text width unit loc-prefix styles] :as cell}]
  (let [path (some-> cell :path pathify)]
    [:div.cell {:class (resolve-class cell-styles
                                      styles
                                      (when width (str "cell--" width))
                                      (or (when (seq path)
                                            (get-in source-value
                                                    (cons ::styles path)))
                                          (get-in source-value
                                                  [::styles ::cell])))}
     (cond->> (or text (get-in source-value path source-value))
       loc-prefix (i18n/localize lang loc-prefix)
       unit (add-unit lang unit))]))

(defn entry-row
  [left-width {lang :lang :as data} [{:keys [loc loc-many source styles]} & cells]]
  (let [source-value (util/pcond-> (resolve-source data source) string? ss/trim)
        multiple?    (and (sequential? source-value)
                          (> (count source-value) 1))]
    (when (or (nil? source) (not-empty source-value))
      (let [row-styles (resolve-class row-styles styles
                                      (get-in source-value [::styles :row]))]
        [:div.section
         {:class (util/intersection-as-kw [:page-break] row-styles)}
         [:div.row
          {:class (util/difference-as-kw row-styles [:page-break])}
          [:div.cell
           {:class (resolve-class [:bold] styles (when left-width
                                                   (str "cell--" left-width)))}
           (i18n/localize lang (if multiple? (or loc-many loc) loc))]
          (if (or (> (count cells) 1) multiple?)
            [:div.cell
             [:div.section
              (for [v (if (sequential? source-value)
                        source-value
                        (vector source-value))]
                [:div.row
                 {:class (get-in v [::styles :row])}
                 (map (partial resolve-cell data v)
                      (if (empty? cells) [{}] cells))])]]
            (resolve-cell data
                          (util/pcond-> source-value
                                        sequential? first)
                          (first cells)))]]))))

(defn content
  [data {:keys [left-width entries]}]
  (->> entries
       (map (partial entry-row left-width data))
       (filter not-empty)))

(defn primary-operation-data [application]
  (->> application
       :primaryOperation
       :id
       (domain/get-document-by-operation application)
       :data))

(defn operation-infos [application]
  (mapv (util/fn-> :schema-info :op)
        (app/get-sorted-operation-documents application)))

(defn verdict-buildings [{:keys [application verdict]}]
  (let [buildings (reduce-kv (fn [acc k {flag? :show-building :as v}]
                               (cond-> acc
                                 flag? (assoc k v)))
                             {}
                             (dict-value verdict :buildings))]
    (->> (map (comp keyword :id) (operation-infos application))
         (map #(get buildings %))
         (remove nil?))))

(defn join-non-blanks [separator & coll]
  (->> coll
       flatten
       (remove ss/blank?)
       (ss/join separator)))

(defn building-parking [lang {:keys [description tag building-id]
                              :as building}]
  (letfn [(park [kw]
            (hash-map :text (i18n/localize lang :pate-buildings.info kw)
                      :amount (kw building)))]
    (-<>> [:kiinteiston-autopaikat :rakennetut-autopaikat
           :rakennettavat-autopaikat :autopaikkoja-enintaan
           :autopaikkoja-vahintaan :ulkopuoliset-autopaikat]
          (map park)
          (sort-by :text)
          vec
          (conj <> (park :autopaikat-yhteensa))
          (remove (comp ss/blank? :amount))
          (cons {:text (-<>> [tag description]
                             (join-non-blanks ": ")
                             (join-non-blanks " \u2013 " <> building-id))
                 :amount ""}))))

(defn parking-section [lang buildings]
  (let [rows (->> buildings
                  (map (partial building-parking lang))
                  (remove nil?))]
    (when (seq rows)
      [:div.section rows])))

(defn verdict-attachments [lang verdict]
  (->> (dict-value verdict :attachments)
       (map (fn [{:keys [type-group type-id amount]}]
              {:text (i18n/localize lang :attachmentType type-group type-id)
               :amount amount}))
       (sort-by :text)))

(defn references [lang {:keys [references] :as verdict} kw]
  (let [ids (dict-value verdict kw)]
    (->> (get references kw)
         (filter #(util/includes-as-kw? ids (:id %)))
         (map #(get-in % [:name (keyword lang)])))))

(defn conditions [verdict]
  (->> (dict-value verdict :conditions)
       (map (fn [[k v]]
              {:id   (name k)
               :text (:condition v)}))
       (sort-by :id)
       (map :text)))

(defn finnish-date
  "Timestamp in the Finnish date format without zero-padding.
  For example, 30.1.2018."
  [timestamp]
  (util/format-timestamp-local-tz timestamp "D.M.YYYY"))

(defn statements [lang {statements :statements}]
  (->> statements
       (filter :given)
       (map (fn [{:keys [given person status]}]
              (join-non-blanks ", "
                               (:text person)
                               (finnish-date given)
                               (i18n/localize lang :statement status))))))

(defmulti verdict-body (util/fn-> :verdict :category keyword))

(defmethod verdict-body :r
  [{:keys [lang application verdict] :as data}]
  (let [buildings (verdict-buildings data)]
    (content (assoc data
                    :application-id (:id application)
                    :property-id (property-id application)
                    :applicants (->> (applicants data)
                                     (map #(format "%s\n%s"
                                                   (:name %) (:address %)))
                                     (ss/join "\n\n"))
                    :operations (assoc-in (operation-infos application)
                                          [0 ::styles :name] :bold)
                    :complexity (complexity lang verdict)
                    :designers (designers data)
                    :primary (primary-operation-data application)
                    :paloluokka (->> buildings
                                     (map :paloluokka)
                                     (remove ss/blank?)
                                     distinct
                                     (ss/join " / "))
                    :parking (->>  buildings
                                   (map (partial building-parking lang))
                                   (interpose {:text    "" :amount ""
                                               ::styles {:row :pad-before}})
                                   flatten)
                    :attachments (verdict-attachments lang verdict)
                    :reviews (references lang verdict :reviews)
                    :plans   (references lang verdict :plans)
                    :conditions (conditions verdict)
                    :statements (statements lang application))
             (:r pdf-layouts))))

(defn verdict-header
  [lang {:keys [organization]} {:keys [published category data]}]
  [:div.header
   [:div.section.header
    [:div.row.pad-after
     [:div.cell.cell--30 (org/get-organization-name organization lang)]
     [:div.cell.cell--40.center
      [:div (i18n/localize lang :attachmentType.paatoksenteko.paatos)]]
     [:div.cell.cell--30.right
      [:div.permit (i18n/localize lang :pdf category :permit)]]]
    [:div.row
     [:div.cell.cell--30 (:verdict-section data)]
     [:div.cell.cell--40.center [:div (util/to-local-date published)]]
     [:div.cell.cell--30.right "Sivu " [:span#page-number "sivu"]]]]])

(defn verdict-footer []
  [:div.footer
   [:div.section
    [:div.row.pad-after.pad-before
     [:cell.cell--100 {:dangerouslySetInnerHTML {:__html "&nbsp;"}}]]]])

(defn verdict-pdf [lang application {:keys [published] :as verdict}]
  (html-pdf/create-and-upload-pdf
   application
   "pate-verdict"
   (html (verdict-body {:lang        lang
                        :application (tools/unwrapped application)
                        :verdict     verdict}))
   {:filename (i18n/localize-and-fill lang
                                      :pate.pdf-filename
                                      (:id application)
                                      (util/to-local-datetime published))
    :header   (html (verdict-header lang application verdict) true)
    :footer   (html (verdict-footer))}))


(defn create-verdict-attachment
  "1. Create PDF file for the verdict.
   2. Create verdict attachment.
   3. Bind 1 into 2."
  [{:keys [lang application user created] :as command} verdict]
  (let [{:keys [file-id mongo-file]} (verdict-pdf lang application verdict)
        _                            (when-not file-id
                                       (fail! :pate.pdf-verdict-error))
        {attachment-id :id
         :as           attachment}   (att/create-attachment!
                                      application
                                      {:created           created
                                       :set-app-modified? false
                                       :attachment-type   {:type-group "paatoksenteko"
                                                           :type-id    "paatos"}
                                       :target            {:type "verdict"
                                                           :id   (:id verdict)}
                                       :locked            true
                                       :read-only         true
                                       :contents          (i18n/localize lang
                                                                         :pate-verdict)})]
    (bind/bind-single-attachment! (update-in command
                                             [:application :attachments]
                                             #(conj % attachment))
                                  (mongo/download file-id)
                                  {:fileId       file-id
                                   :attachmentId attachment-id}
                                  nil)))
