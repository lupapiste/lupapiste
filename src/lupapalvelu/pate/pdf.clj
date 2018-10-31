(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts. Utilises a simple
  schema-based mechanism for the layout definiton and generation."
  (:require [clojure.edn :as edn]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schemas :refer [resolve-verdict-attachment-type]]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [sade.core :refer :all]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))


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
                         (cols/join-non-blanks " "
                                               (:etunimi henkilotiedot)
                                               (:sukunimi henkilotiedot)))
               :address (let [{:keys [katu postinumero postitoimipaikannimi
                                      maa]} osoite]
                          (->> [katu (str postinumero " " postitoimipaikannimi)
                                (when (and (not-empty maa) (util/not=as-kw maa :FIN))
                                  (i18n/localize lang :country maa))]
                               (cols/join-non-blanks ", ")))}))
       (remove (util/fn-> :name ss/blank?))))

(defn property-id [application]
  (cols/join-non-blanks "-"
                        [(-> application :propertyId
                             property/to-human-readable-property-id)
                         (util/pcond->> (cols/doc-value application
                                                        :rakennuspaikka
                                                        :kiinteisto.maaraalaTunnus)
                                        ss/not-blank? (str "M"))]))

(defn property-id-ya [options]
  (cols/join-non-blanks "\n"
                        (->> (get-in options [:verdict :data :propertyIds])
                             (map (fn [[_ v]] (ss/trim (:property-id v)))))))

(defn value-or-other [lang value other & loc-keys]
  (cond (or (nil? value) (empty? value)) ""
        (util/=as-kw value :other) other
        (seq loc-keys) (i18n/localize lang loc-keys value)
        :else value))

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
                (let [designer-name (cols/join-non-blanks " "
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
                                  (cols/join-non-blanks ", "))}))))
         (remove nil?)
         (sort (fn [a _]
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

(defn operation-name
  [app op-info]
  (if (= (:name op-info) "yleiset-alueet-hankkeen-kuvaus-kaivulupa")
    (:name (util/find-by-id (:id op-info)
                            (concat [(:primaryOperation app)]
                                    (:secondaryOperations app))))
    (:name op-info)))

(defn operations
  "If the verdict has an :operation property, its value overrides the
  application primary operation."
  [{:keys [lang verdict application]}]
  (let [infos     (map (util/fn->> (operation-name application)
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
                             (cols/dict-value options :buildings))]
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
                               (cols/join-non-blanks ": ")
                               (cols/join-non-blanks " \u2013 " <> building-id)
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
  (let [v (cols/dict-value options :attachments)]

    (->> (if (:published verdict)
           v
           (pack-draft-attachments application verdict v))
        (map (fn [{:keys [type-group type-id amount]}]
               {:text   (i18n/localize lang :attachmentType type-group type-id)
                :amount amount}))
        (sort-by :text))))

(defn link-permits
  "Since link-permits resolution is quite database intensive operation
  it is only done for YA and TJ category."
  [{:keys [verdict application]}]
  (when (or (vc/has-category? verdict :ya)
            (vc/has-category? verdict :tj))
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
  (when (vc/contract? verdict)
    (->> (vc/verdict-summary-signatures verdict)
         (map #(update % :date date/finnish-date)))))

(defn verdict-properties
  "Adds all kinds of different properties to the options. It is then up
  to category-specific verdict-body methods and corresponding
  pdf-layouts whether every property is displayed in the pdf or not."
  [{:keys [lang application] :as options}]
  (let [buildings (verdict-buildings options)]
    (assoc (cols/verdict-properties options)
           :application-id (:id application)
           :property-id (property-id application)
           :property-id-ya (property-id-ya options)
           :applicants (->> (applicants options)
                            (map #(format "%s\n%s"
                                          (:name %) (:address %)))
                            (interpose "\n"))
           :operations (assoc-in (operations options)
                                 [0 ::styles :text] :bold)
           :designers (designers options)
           :primary (primary-operation-data application)
           :paloluokka (->> buildings
                            (map :paloluokka)
                            (remove ss/blank?)
                            distinct
                            (ss/join " / "))
           :parking (->> buildings
                         (map (partial building-parking lang))
                         (interpose {:text    "" :amount ""
                                     ::styles {:row :pad-before}})
                         flatten)
           :attachments (verdict-attachments options)
           :organization (html/organization-name lang application)
           :link-permits (link-permits options)
           :tj-vastattavat-tyot (tj-vastattavat-tyot application lang)
           :signatures (signatures options))))

(defn verdict-tags
    "Source-data is a map containing keys referred in pdf-layout source
  definitions. Returns :header, :body, :footer map."
  [application verdict]
  {:body   (cols/content (verdict-properties {:lang        (cols/language verdict)
                                              :application (tools/unwrapped application)
                                              :verdict     verdict})
                         (layouts/pdf-layout verdict))
   :header (html/verdict-header (cols/language verdict) application verdict)
   :footer (html/verdict-footer)})

(defn verdict-tags-html
    "Processes `verdict-tags` result into html."
  [{:keys [body header footer]}]
  {:body   (html/html body)
   :header (html/html header true)
   :footer (html/html footer)})

(defn verdict-html
  [application verdict]
  (verdict-tags-html (verdict-tags application verdict)))

(defn create-verdict-attachment
  "Creates PDF for the verdict and uploads it as an attachment. Returns
  the attachment-id."
  [{:keys [application created] :as command} verdict]
  (when-let [html (some-> verdict :published :tags
                          edn/read-string verdict-tags-html)]
    (let [pdf       (html-pdf/html->pdf application
                                        "pate-verdict"
                                        html)
          proposal? (vc/proposal? verdict)
          contract? (vc/contract? verdict)]
     (when-not (:ok pdf)
       (fail! :pate.pdf-verdict-error))
     (with-open [stream (:pdf-file-stream pdf)]
       (:id (att/upload-and-attach!
             command
             {:created         created
              :attachment-type (if proposal?
                                 (resolve-verdict-attachment-type application :paatosehdotus)
                                 (resolve-verdict-attachment-type application))
              :source          {:type "verdicts"
                                :id   (:id verdict)}
              :locked          true
              :read-only       true
              :contents        (i18n/localize (cols/language verdict)
                                              (cond
                                                proposal? :pate-verdict-proposal
                                                contract? :pate.verdict-table.contract
                                                :else     :pate-verdict))}
             {:filename (i18n/localize-and-fill (cols/language verdict)
                                                (cond
                                                  proposal? :pdf.proposal.filename
                                                  contract? :pdf.contract.filename
                                                  :else     :pdf.filename)
                                                (:id application)
                                                (util/to-local-datetime (some-> verdict
                                                                                :published
                                                                                :published)))
              :content  stream}))))))

;; TODO: Verdict details MUST NOT change in the new version. Only the
;; signatures must be replaced.
(defn create-verdict-attachment-version
  "Creates a verdict attachments as a new version to previously created
  verdict attachment. Used when a contract is signed."
  [{:keys [application created] :as command} verdict]
  (let [{:keys [tags attachment-id
                proposal-attachment-id]} (:published verdict)
        attachment-id                    (or attachment-id proposal-attachment-id)
        pdf                              (html-pdf/html->pdf application
                                                             "pate-verdict"
                                                             (verdict-tags-html (edn/read-string tags)))
        proposal? (vc/proposal? verdict)
        contract? (vc/contract? verdict)]
    (when-not (:ok pdf)
      (fail! :pate.pdf-verdict-error))
    (with-open [stream (:pdf-file-stream pdf)]
      (att/upload-and-attach! command
                              {:created       created
                               :attachment-id attachment-id}
                              {:filename (i18n/localize-and-fill (cols/language verdict)
                                                                 (cond
                                                                   proposal? :pdf.proposal.filename
                                                                   contract? :pdf.contract.filename
                                                                   :else     :pdf.filename)
                                                                 (:id application)
                                                                 (util/to-local-datetime created))
                               :content  stream}))))

(defn create-verdict-preview
  "Creates draft version of the verdict
  PDF. Returns :pdf-file-stream, :filename map or :error map."
  [{:keys [application created]} verdict]
  (let [pdf (html-pdf/html->pdf application
                                "pate-verdict-draft"
                                (verdict-html application verdict))]
    (if (:ok pdf)
      (assoc pdf :filename (i18n/localize-and-fill (cols/language verdict)
                                                   :pdf.draft
                                                   (:id application)
                                                   (date/finnish-date created)))
      {:error :pate.pdf-verdict-error})))
