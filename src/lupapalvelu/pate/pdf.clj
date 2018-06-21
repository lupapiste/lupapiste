(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts. Utilises a simple
  schema-based mechanism for the layout definiton and generation."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as common]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(defn join-non-blanks
  "Trims and joins."
  [separator & coll]
  (->> coll
       flatten
       (map ss/trim)
       (remove ss/blank?)
       (ss/join separator)))

(defn loc-non-blank
  "Localized string or nil if the last part is blank."
  [lang & parts]
  (when-not (-> parts last ss/blank?)
    (i18n/localize lang parts)))

(defn loc-fill-non-blank
  "Localize and fill if every value is non-blank"
  [lang loc-key & values]
  (when (every? (comp ss/not-blank? str) values)
    (apply (partial i18n/localize-and-fill lang loc-key) values)))

(defn applicants
  "Returns list of name, address maps for properly filled party
  documents. If a doc does not have name information it is omitted."
  [{app :application lang :lang}]
  (->> (domain/get-applicant-documents (:documents app))
       (map :data)
       (map (fn [{:keys [_selected henkilo yritys]}]
              (if (util/=as-kw :yritys _selected)
                yritys
                henkilo)))
       (map (fn [{:keys [yritysnimi henkilotiedot osoite]}]
              {:name (or (-> yritysnimi ss/trim not-empty)
                         (join-non-blanks " "
                                          (:etunimi henkilotiedot)
                                          (:sukunimi henkilotiedot)))
               :address (let [{:keys [katu postinumero postitoimipaikannimi
                                      maa]} osoite]
                          (->> [katu (str postinumero " " postitoimipaikannimi)
                                (when (util/not=as-kw maa :FIN)
                                  (i18n/localize lang :country maa))]
                               (join-non-blanks ", ")))}))
       (remove (util/fn-> :name ss/blank?))))

(defn complexity [{lang :lang :as options}]
  (not-empty (filter not-empty
                     [(loc-non-blank lang
                                     :pate.complexity
                                     (html/dict-value options :complexity))
                      (html/dict-value options :complexity-text)])))

(defn property-id [application]
  (join-non-blanks "-"
                   [(-> application :propertyId
                        property/to-human-readable-property-id)
                    (util/pcond->> (html/doc-value application
                                                   :rakennuspaikka
                                                   :kiinteisto.maaraalaTunnus)
                                   ss/not-blank? (str "M"))]))

(defn value-or-other [lang value other & loc-keys]
  (if (util/=as-kw value :other)
    other
    (if (seq loc-keys)
      (i18n/localize lang loc-keys value)
      value)))

(defn designers [{lang :lang app :application}]
  (let [role-keys [:pdf :kuntaRoolikoodi]
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
                    role         :role}]
                (let [designer-name (join-non-blanks " "
                                                     [(:etunimi info)
                                                      (:sukunimi info)])]
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
                                  (join-non-blanks ", "))}))))
         (remove nil?)
         (sort (fn [a b]
                 (if (= (:role a) head-loc) -1 1))))))

(defn primary-operation-data [application]
  (->> application
       :primaryOperation
       :id
       (domain/get-document-by-operation application)
       :data))

(defn operation-infos
  [application]
  (mapv (util/fn-> :schema-info :op)
        (app/get-sorted-operation-documents application)))

(defn operations
  "If the verdict has an :operation property, its value overrides the
  application primary operation."
  [{:keys [lang verdict application]}]
  (let [infos     (map (util/fn->> :name
                                   (i18n/localize lang :operations)
                                   (hash-map :text))
                       (operation-infos application))
        operation (ss/trim (get-in verdict [:data :operation]))]
    (vec (if (ss/not-blank? operation)
           (cons {:text operation} (rest infos))
           infos))))

(defn verdict-buildings [{:keys [application] :as options}]
  (let [buildings (reduce-kv (fn [acc k {flag? :show-building :as v}]
                               (cond-> acc
                                 flag? (assoc k v)))
                             {}
                             (html/dict-value options :buildings))]
    (->> (map (comp keyword :id) (operation-infos application))
         (map #(get buildings %))
         (remove nil?))))

(defn building-parking [lang {:keys [description tag building-id]
                              :as   building}]
  (letfn [(park [kw]
            (hash-map :text (i18n/localize lang :pate-buildings.info kw)
                      :amount (kw building)))]
    (-<>> [:kiinteiston-autopaikat :rakennetut-autopaikat]
          (map park)
          (sort-by :text)
          vec
          (conj <> (park :autopaikat-yhteensa))
          (remove (comp ss/blank? :amount))
          (cons {:text   (-<>> [tag description]
                               (join-non-blanks ": ")
                               (join-non-blanks " \u2013 " <> building-id)
                               (vector :strong))
                 :amount ""}))))

(defn parking-section [lang buildings]
  (let [rows (->> buildings
                  (map (partial building-parking lang))
                  (remove nil?))]
    (when (seq rows)
      [:div.section rows])))

(defn pack-draft-attachments [{:keys [attachments]}
                              {verdict-id :id}
                              att-ids]
  (->> attachments
       (filter (fn [{:keys [id target]}]
                 (or (util/includes-as-kw? att-ids id)
                     (and (util/=as-kw (:type target) :verdict)
                          (= (:id target) verdict-id)))))
       (group-by :type)
       (map (fn [[type xs]]
              (assoc type :amount (count xs))))))

(defn verdict-attachments [{:keys [lang application verdict] :as options}]
  (let [v (html/dict-value options :attachments)]

    (->> (if (:published verdict)
           v
           (pack-draft-attachments application verdict v))
        (map (fn [{:keys [type-group type-id amount]}]
               {:text   (i18n/localize lang :attachmentType type-group type-id)
                :amount amount}))
        (sort-by :text))))

(defn references-included? [{:keys [verdict]} kw]
  (get-in verdict [:data (keyword (str (name kw) "-included"))]))

(defn references [{:keys [lang verdict] :as options} kw]
  (when (references-included? options kw)
    (let [ids (html/dict-value options kw)]
     (->> (get-in verdict [:references kw])
          (filter #(util/includes-as-kw? ids (:id %)))
          (map (keyword lang))
          sort))))

(defn review-info [options]
  (when (references-included? options :reviews)
    (html/dict-value options :review-info)))

(defn conditions [options]
  (let [tags (->> (html/dict-value options :conditions)
                  (map (fn [[k v]]
                         {:id   (name k)
                          :text (ss/trim (:condition v))}))
                  (remove (comp ss/blank? :text))
                  (sort-by :id)
                  (map (comp markup/markup->tags :text)))]
    (when (seq tags)
      ;; Extra "layer" needed for proper entry-row layout.
      [[:div.markup tags]])))

(defn statements [{lang :lang :as options}]
  (->> (html/dict-value options :statements)
       (filter :given)
       (map (fn [{:keys [given text status]}]
              (join-non-blanks ", "
                               text
                               (date/finnish-date given)
                               (i18n/localize lang :statement status))))
       not-empty))

(defn collateral [{:keys [lang] :as options}]
  (when (html/dict-value options :collateral-flag)
    (join-non-blanks ", "
                     [(html/add-unit lang :eur (html/dict-value options
                                                                :collateral))
                      (loc-non-blank lang :pate.collateral-type
                                     (html/dict-value options
                                                      :collateral-type))
                      (html/dict-value options :collateral-date)])))



(defn handler
  "Handler with title (if given)"
  [options]
  (->> [:handler-title :handler]
       (map (partial html/dict-value options))
       (map ss/trim)
       (remove ss/blank?)
       (ss/join " ")))

(defn link-permits
  "Since link-permits resolution is quite database intensive operation
  it is only done for YA and TJ category."
  [{:keys [verdict application]}]
  (when (or (util/=as-kw :ya (:category verdict))
            (util/=as-kw :tj (:category verdict)))
    (:linkPermitData (app-meta/enrich-with-link-permit-data application))))

(defn tj-vastattavat-tyot [application lang]
  (let [doc (foreman/get-foreman-document application)
        vastattavat-data (tools/unwrapped (get-in doc [:data :vastattavatTyotehtavat]))
        vastattavat-loc-keys (reduce (fn [m {:keys [name i18nkey]}]
                                       (assoc m (keyword name) i18nkey))
                                     {}
                                     (get-in schemas/vastattavat-tyotehtavat-tyonjohtaja-v2 [0 :body]))]
    (->> vastattavat-data
         (reduce-kv (fn [result key val]
                      (cond
                        (and (= :muuMika (keyword key))
                             (not (ss/blank? val))) (conj result val)
                        (true? val) (conj result (i18n/localize lang (get vastattavat-loc-keys key)))
                        :else result))
                    []))))
(defn signatures
  "Signatures as a timestamp ordered list"
  [{:keys [verdict]}]
  (when (util/=as-kw :contract (:category verdict))
    (->> verdict :data :signatures
         vals
         (sort-by :date)
         (map #(update % :date date/finnish-date)))))

(defn verdict-properties
  "Adds all kinds of different properties to the options. It is then up
  to category-specific verdict-body methods and corresponding
  pdf-layouts whether every property is displayed in the pdf or not."
  [{:keys [lang application verdict] :as options}]
  (let [buildings (verdict-buildings options)]
    (assoc options
           :application-id (:id application)
           :property-id (property-id application)
           :applicants (->> (applicants options)
                            (map #(format "%s\n%s"
                                          (:name %) (:address %)))
                            (interpose "\n"))
           :operations (assoc-in (operations options)
                                 [0 ::styles :text] :bold)
           :complexity (complexity options)
           :designers (designers options)
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
           :attachments (verdict-attachments options)
           :reviews (references options :reviews)
           :review-info (review-info options)
           :plans   (references options :plans)
           :conditions (conditions options)
           :statements (statements options)
           :collateral (collateral options)
           :organization (html/organization-name lang application)
           :muutoksenhaku (loc-fill-non-blank lang
                                              :pdf.not-later-than
                                              (html/dict-value options
                                                               :muutoksenhaku))
           :voimassaolo (loc-fill-non-blank lang
                                            :pdf.voimassa.text
                                            (html/dict-value options
                                                             :aloitettava)
                                            (html/dict-value options
                                                             :voimassa))
           :voimassaolo-ya (loc-fill-non-blank lang
                                               :pdf.voimassaolo-ya
                                               (html/dict-value options
                                                                :start-date)
                                               (html/dict-value options
                                                                :end-date))
           :handler (handler options)
           :link-permits (link-permits options)
           :tj-vastattavat-tyot (tj-vastattavat-tyot application lang)
           :signatures (signatures options))))


(defn verdict-html
  [application verdict]
  (html/verdict-html application
                     verdict
                     (verdict-properties {:lang        (html/language verdict)
                                          :application (tools/unwrapped application)
                                          :verdict     verdict})
                     (layouts/pdf-layout verdict)))

(defn create-verdict-attachment
  "Creates PDF for the verdict and uploads it as an attachment. Returns
  the attachment-id."
  [{:keys [application created] :as command} verdict]
  (when-let [html (get-in verdict [:verdict-attachment :html])]
    (let [pdf       (html-pdf/html->pdf application
                                        "pate-verdict"
                                        html)
         contract? (util/=as-kw (:category verdict) :contract)]
     (when-not (:ok pdf)
       (fail! :pate.pdf-verdict-error))
     (with-open [stream (:pdf-file-stream pdf)]
       (:id (att/upload-and-attach!
             command
             {:created         created
              :attachment-type (-<>> (:permitType application)
                                     keyword
                                     att-type/get-all-attachment-types-for-permit-type
                                     (util/find-by-key :type-id :paatos)
                                     (select-keys <> [:type-group :type-id])
                                     (reduce-kv (fn [acc k v]
                                                  (assoc acc k (name v)))
                                                {}))
              :source          {:type "verdicts"
                                :id   (:id verdict)}
              :locked          true
              :read-only       true
              :contents        (i18n/localize (html/language verdict)
                                              (if contract?
                                                :pate.verdict-table.contract
                                                :pate-verdict))}
             {:filename (i18n/localize-and-fill (html/language verdict)
                                                (if contract?
                                                  :pdf.contract.filename
                                                  :pdf.filename)
                                                (:id application)
                                                (util/to-local-datetime (:published verdict)))
              :content  stream}))))))

(defn create-verdict-attachment-version
  "Creates a verdict attachments as a new version to previously created
  verdict attachment. Used when a contract is signed."
  [{:keys [application created] :as command} verdict]
  (let [pdf                 (html-pdf/html->pdf application
                                                "pate-verdict"
                                                (verdict-html application verdict))
        contract?           (util/=as-kw (:category verdict) :contract)
        {attachment-id :id} (util/find-first (fn [{source :source}]
                                               (and (util/=as-kw (:type source) :verdicts)
                                                    (= (:id source) (:id verdict))))
                                             (:attachments application))]
    (when-not (:ok pdf)
      (fail! :pate.pdf-verdict-error))
    (with-open [stream (:pdf-file-stream pdf)]
      (att/upload-and-attach! command
                              {:created       created
                               :attachment-id attachment-id}
                              {:filename (i18n/localize-and-fill (html/language verdict)
                                                                 (if contract?
                                                                   :pdf.contract.filename
                                                                   :pdf.filename)
                                                                 (:id application)
                                                                 (util/to-local-datetime created))
                               :content  stream}))))

(defn create-verdict-preview
  "Creates draft version of the verdict
  PDF. Returns :pdf-file-stream, :filename map or :error map."
  [{:keys [application created] :as command} verdict]
  (let [pdf (html-pdf/html->pdf application
                                "pate-verdict-draft"
                                (verdict-html application verdict))]
    (if (:ok pdf)
      (assoc pdf :filename (i18n/localize-and-fill (html/language verdict)
                                                   :pdf.draft
                                                   (:id application)
                                                   (date/finnish-date created)))
      {:error :pate.pdf-verdict-error})))
