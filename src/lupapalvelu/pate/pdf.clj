(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts. Utilises a simple
  schema-based mechanism for the layout definition and generation."
  (:require [hiccup.core]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schemas :as pate-sc]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-schemas :as vs]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.statistics.core :as stats]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]
            [lupapalvelu.statistics.tags :as stats-tags])
  (:import [java.io InputStream]))


(defn party-details
  "Returns list of name, address maps for properly filled party
  documents. If a doc does not have name information it is omitted."
  [{:keys [application lang]} party-documents]
  (->> party-documents
       (map (comp ss/trimwalk :data))
       (map (fn [{:keys [_selected henkilo yritys]}]
              (if (util/=as-kw :yritys _selected)
                yritys
                henkilo)))
       (map (fn [{:keys [yritysnimi henkilotiedot osoite  yhteyshenkilo]}]
              (util/assoc-when-pred
                {:name    (or (-> yritysnimi ss/trim not-empty)
                              (cols/join-non-blanks " "
                                                    (:etunimi henkilotiedot)
                                                    (:sukunimi henkilotiedot)))
                 :address (let [{:keys [katu postinumero postitoimipaikannimi
                                        maa]} osoite]
                            (->> [katu (str postinumero " " postitoimipaikannimi)
                                  (when (and (not-empty maa) (util/not=as-kw maa :FIN))
                                    (i18n/localize lang :country maa))]
                                 (cols/join-non-blanks ", ")))}
                ss/not-blank?
                :contact (when-let [{:keys [etunimi sukunimi]} (and (= (:permitType application) "YA")
                                                                    (:henkilotiedot yhteyshenkilo))]
                           (cols/join-non-blanks " " etunimi sukunimi)))))
       (remove (util/fn-> :name ss/blank?))))

(defn applicants
  [{app :application :as options}]
  (->> (domain/get-applicant-documents (:documents app))
       (party-details options)))

(defn site-responsibles
  [{app :application :as options}]
  (->> (domain/get-documents-by-subtype (:documents app) :tyomaasta-vastaava)
       (party-details options)))

(defn format-omitted-party-details
  "Replaces the given party info with a black bar (e.g. personal info in a public PDF)"
  [_]
  "███████████")

(defn format-party-details
  "Formats the party detail with a line for the party name and another for the party address."
  [party-details]
  (->> party-details
       (map (fn [{:keys [name address contact]}]
              (cols/join-non-blanks "\n" name address contact)))
       (interpose "\n")))

(defn format-designer-details
  "Formats the designer name (non-personal details like role and difficulty handled elsewhere)"
  [{:keys [etunimi sukunimi]}]
  (cols/join-non-blanks " " [etunimi sukunimi]))

(defn format-applicant-party-details
  "Formats the party detail with just the party name and possible contact information"
  [party-details]
  (->> party-details
       (map (fn [{:keys [name contact]}]
              (cols/join-non-blanks "\n" name contact)))
       (interpose "\n")))

(defn property-id [application]
  (cols/join-non-blanks "-"
                        [(-> application :propertyId
                             property/to-human-readable-property-id)
                         (some #(some->> % :data :kiinteisto :maaraalaTunnus
                                         ss/blank-as-nil ss/trim (str "M"))
                               (domain/get-documents-by-type application :location))]))

(defn property-id-ya [options]
  (cols/join-non-blanks "\n"
                        (->> (get-in options [:verdict :data :propertyIds])
                             (map (fn [[_ v]] (ss/trim (:property-id v)))))))

(defn value-or-other [lang value other & loc-keys]
  (cond (or (nil? value) (empty? value)) ""
        (util/=as-kw value :other) other
        (seq loc-keys) (i18n/localize lang loc-keys value)
        :else value))

(defn- good-document-datas [documents]
  (for [{:keys [data disabled] :as doc} documents
        :when (and (approval/approved? doc)
                   (not disabled))]
    (tools/unwrapped data)))

(defn designers [formatter {lang :lang app :original-application}]
  (let [role-keys [:pdf :kuntaRoolikoodi]
        head-loc  (i18n/localize lang role-keys "pääsuunnittelija")
        heads     (->> (domain/get-documents-by-name app :paasuunnittelija)
                      good-document-datas
                      (map #(assoc % :role head-loc)))
        others    (good-document-datas (domain/get-documents-by-name app :suunnittelija))]
    (->> (concat heads others)
         (remove nil?)
         (map (fn [{role-code  :kuntaRoolikoodi
                    other-role :muuSuunnittelijaRooli
                    difficulty :suunnittelutehtavanVaativuusluokka
                    info       :henkilotiedot
                    skills     :patevyys
                    role       :role}]
                (let [designer-name (formatter info)]
                  (when-not (ss/blank? designer-name)
                    {:role       (or role
                                     (value-or-other lang
                                                     role-code other-role
                                                     role-keys))
                     :difficulty difficulty
                     :person     (->> [designer-name
                                       (value-or-other lang
                                                       (:koulutusvalinta skills)
                                                       (:koulutus skills))]
                                      (cols/join-non-blanks ", "))}))))
         (remove nil?)
         (sort (fn [a _]
                 (if (= (:role a) head-loc) -1 1))))))

(defn- operation-infos
  [application]
  (->> (app-utils/get-sorted-operation-documents application)
       tools/unwrapped ; Just to make sure
       (mapv #(-> % :schema-info :op
                  (util/assoc-when-pred ss/not-blank?
                                        :vtj-prt (get-in % [:data :valtakunnallinenNumero]))))))

(defn- operation-name
  [app op-info]
  (if (= (:name op-info) "yleiset-alueet-hankkeen-kuvaus-kaivulupa")
    (:name (util/find-by-id (:id op-info)
                            (concat [(:primaryOperation app)]
                                    (:secondaryOperations app))))
    (:name op-info)))

(defn operations
  "If the verdict has an :operation property, its value overrides the
  application primary operation.
  And if verdict has bulletin-desc-as-operation property checked,
  bulletin-op-description overrides both of the above."
  [{:keys [lang verdict application]}]
  (let [infos         (map (fn [{:keys [vtj-prt] :as m}]
                             (let [op-name (->> (operation-name application m)
                                                (i18n/localize lang :operations))]
                               {:text (ss/join-non-blanks " \u2013 " [op-name vtj-prt])}))
                           (operation-infos application))
        operation     (ss/trim (get-in verdict [:data :operation]))
        bulletin      (get-in verdict [:data :bulletin-desc-as-operation])
        bulletin-desc (ss/trim (get-in verdict [:data :bulletin-op-description]))]
    (vec (cond
           bulletin                  [{:text bulletin-desc}]
           (ss/not-blank? operation) [{:text operation}]
           :else                     infos))))

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
    (some->> vs/autopaikat
             (map park)
             (remove (comp ss/blank? :amount))
             seq
             (cons {:text   (-<>> [tag description]
                                  (cols/join-non-blanks ": ")
                                  (cols/join-non-blanks " \u2013 " <> building-id)
                                  (vector :strong))
                    :amount ""}))))

(defn- update-sum-field [result target field]
  (update result field (fn [v]
                         (+ (or v 0)
                            (util/->double (get target field 0))))))

(defn- update-sum-map [result target fields]
  (reduce (fn [acc field]
            (update-sum-field acc target field))
          result
          fields))

(def ^:private formatter (ss/decimal-formatter-fn 0 1))

(defn- double->str
  "String representation with a maximum of one decimal. Zeroes are nil.
  3.921 -> 3.9
  3.990 -> 4
  3810.0 -> 3810
  3810.1 -> 3810.1
  0 -> nil
  0.02 -> nil
  nil -> nil"
  [v]
  (when-let [s (formatter v)]
    (when-not (#{"0" "-0"} s)
      s)))

(defn- extract-dimensions [supported fields documents]
  (some->> documents
           (filter (util/fn->> :schema-info :name (util/includes-as-kw? (keys supported))))
           (reduce (fn [acc {:keys [data schema-info]}]
                     (update-sum-map acc
                                     (->> schema-info :name keyword
                                          (get supported)
                                          util/split-kw-path
                                          (get-in data))
                                     fields))
                   {})))

(def ^:private legacy-dimension-schemas
  "Schema name to path mapping for legacy applications"
  {:new-buildings      {:uusiRakennus                :mitat
                        :uusi-rakennus-ei-huoneistoa :mitat}
   :new-extensions     {:rakennuksen-laajentaminen                :laajennuksen-tiedot.mitat
                        :rakennuksen-laajentaminen-ei-huoneistoja :laajennuksen-tiedot.mitat}
   :extended-buildings {:rakennuksen-laajentaminen                :mitat
                        :rakennuksen-laajentaminen-ei-huoneistoja :mitat}})

(def ^:private ryhti-dimension-schemas
  "Schema name to path mapping for Ryhti applications.
  New buildings: dimensions in :rakennuskohteen-osa :ulkokuorentiedot
  Extensions: new extension dimensions in :laajennuksen-tiedot :mitat,
              existing building dimensions in :rakennuskohteen-osa :ulkokuorentiedot"
  {:new-buildings      {:uusiRakennuskohde :rakennuskohteen-osa.ulkokuorentiedot} ;; TODO: LPK-7990: Handle multiple 'rakennuskohteen-osa'
   :new-extensions     {:rakennuskohteenLaajentaminen :laajennuksen-tiedot.mitat}
   :extended-buildings {:rakennuskohteenLaajentaminen :rakennuskohteen-osa.ulkokuorentiedot}})

(defn dimensions
  "Map of :kerrosala, :rakennusoikeudellinenKerrosala, :rakennusoikeusKuutiometreina,
  :kokonaisala and :tilavuus keys. The values are a _sum_ of the corresponding
  fields in the supported document schemas. Values are strings and
  doubles are shown with one decimal. Nil if none of the application
  documents is supported."
  [{:keys [documents]
    :as   application}]
  (let [fields                  [:kerrosala :rakennusoikeudellinenKerrosala :rakennusoikeusKuutiometreina :kokonaisala :tilavuus]
        schemas                 (if (app-utils/ryhti-application? application)
                                  ryhti-dimension-schemas
                                  legacy-dimension-schemas)
        new-buildings           (extract-dimensions (:new-buildings schemas) fields documents)
        new-extensions          (extract-dimensions (:new-extensions schemas) fields documents)
        new-dimensions          (->> (reduce (fn [acc m]
                                               (update-sum-map acc m [:kerrosala
                                                                      :rakennusoikeudellinenKerrosala
                                                                      :rakennusoikeusKuutiometreina
                                                                      :tilavuus]))
                                             {}
                                             [new-buildings new-extensions])
                                     (util/map-values double->str)
                                     util/strip-nils)
        new-buildings-area      (:kokonaisala new-buildings)
        new-extensions-area     (:kokonaisala new-extensions)
        extended-buildings-area (->> documents
                                     (extract-dimensions (:extended-buildings schemas) [:kokonaisala])
                                     :kokonaisala)]
    (cond-> new-dimensions
      new-buildings-area      (assoc :rakennettavaKokonaisala (double->str new-buildings-area))
      new-extensions-area     (assoc :laajennettavaKokonaisala (double->str new-extensions-area))
      extended-buildings-area (assoc :uusiKokonaisala (if new-buildings-area
                                                        (double->str (+ extended-buildings-area new-buildings-area))
                                                        (double->str extended-buildings-area))))))

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

(defn verdict-rectifications
  [{:keys [verdict] :as options}]
  (let [chosen-rect-ids    (cols/dict-value options :parent-verdict-rectifications)
        rect-id->rect-data #(util/find-by-id % (-> verdict :references :parent-verdict-rectifications))]
  (map rect-id->rect-data chosen-rect-ids)))

(defn link-permits
  "Since link-permits resolution is quite database intensive operation
  it is only done if the application matches _any_ of the following conditions:
   - Application is either in YA or TJ category
   - Primary operation requires link permit
   - Permit subtype is muutoslupa."
  [{:keys [verdict application]}]
  (when (or (vc/has-category? verdict :ya)
            (vc/has-category? verdict :tj)
            (util/includes-as-kw? operations/link-permit-required-operations
                                  (-> application :primaryOperation :name))
            (util/=as-kw (:permitSubtype application) :muutoslupa))
    (:linkPermitData (app-meta/enrich-with-link-permit-data application))))

(defn tj-vastattavat-tyot [application lang]
  (foreman/vastattavat-tyotehtavat (foreman/get-foreman-document application) lang))

(defn signatures
  "Signatures as a timestamp ordered list"
  [{:keys [verdict]}]
  (when (vc/contract? verdict)
    (->> (vc/verdict-summary-signatures verdict)
         (map #(update % :date date/finnish-date)))))

(defn combine-building-fields [field-key buildings]
  (->> buildings
       (map field-key)
       (remove ss/blank?)
       distinct
       (ss/join " / ")))

(defn parking [buildings lang]
  (some->> buildings
           (map (partial building-parking lang))
           (remove nil?)
           not-empty
           (interpose {:text    "" :amount ""
                       ::styles {:row :pad-before}})
           flatten))

(defn processing [{:keys [application lang verdict] :as options}]
  (when (and (some-> verdict :template :inclusions (util/includes-as-kw? :processing))
             (stats/processing-application? application))
    (let [{:keys [complexity
                  used-period]} (stats-db/read-processing application)
          months                (some-> complexity stats-sc/complexity->months)]
      (when (some-> months pos?)
        {:months (i18n/localize-and-fill lang :statistics.period.m months)
         :used   (stats-tags/period->text (partial i18n/localize-and-fill lang)
                                          used-period)
         :text   (cols/dict-value options :processing)}))))

(defn verdict-properties
  "Adds all kinds of different properties to the options. It is then up
  to category-specific verdict-body methods and corresponding
  pdf-layouts whether every property is displayed in the pdf or not."
  [{:keys [lang application original-application verdict applicant-formatter designer-formatter party-formatter] :as options}]
  (let [buildings (verdict-buildings options)]
    (assoc (cols/verdict-properties options)
           :application-id               (:id application)
           :property-id                  (property-id application)
           :property-id-ya               (property-id-ya options)
           :applicants                   (->> options applicants applicant-formatter)
           :site-responsibles            (->> options site-responsibles party-formatter)
           :operations                   (assoc-in (operations options) [0 ::styles :text] :bold)
           :designers                    (designers (or designer-formatter format-designer-details) options)
           :dimensions                   (dimensions application)
           :paloluokka                   (combine-building-fields :paloluokka buildings)
           :vss-luokka                   (combine-building-fields :vss-luokka buildings)
           :kokoontumistilanHenkilomaara (combine-building-fields :kokoontumistilanHenkilomaara buildings)
           :parking                      (parking buildings lang)
           :attachments                  (verdict-attachments options)
           :verdict-rectifications       (verdict-rectifications options)
           :organization                 (html/organization-name lang application verdict)
           :link-permits                 (link-permits options)
           :tj-vastattavat-tyot          (tj-vastattavat-tyot original-application lang)
           :processing                   (processing options)
           :signatures                   (signatures options))))

(defn trim-verdict
  "Remove final verdict details from a proposal or proposal draft. The details are included
  when the proposal verdict is being given."
  [verdict]
  (let [proposal? (or (vc/proposal? verdict)
                      (and (vc/draft? verdict)
                           (some-> verdict :references :boardname ss/not-blank?)))
        flag?     (cols/dict-value verdict :verdict-flag)]
    (cond-> verdict
      (and proposal? (not flag?))
      (update :data dissoc :verdict-section :verdict-code :verdict-text))))

(defn verdict-tags
  "Takes application, verdict and possible additional props (to be used in generating verdict-properties)
  and generates the :body, :header and :footer html tags.
  Returns :header, :body, :footer map.
  Applicant info has to be explicitly included or excluded to prevent personal information leaks.
  Note that this can still be overridden with a body-prop."
  [application verdict omit-party-info? & [body-props]]
  (let [verdict (trim-verdict verdict)]
    {:body   (cols/content (verdict-properties (merge {:lang                 (cols/language verdict)
                                                       :original-application application
                                                       :application          (tools/unwrapped application)
                                                       :verdict              verdict
                                                       :applicant-formatter  (if omit-party-info?
                                                                               format-omitted-party-details
                                                                               format-applicant-party-details)
                                                       :designer-formatter   (if omit-party-info?
                                                                               format-omitted-party-details
                                                                               format-designer-details)
                                                       :party-formatter      (if omit-party-info?
                                                                               format-omitted-party-details
                                                                               format-party-details)}
                                                      body-props))
                           (layouts/pdf-layout verdict))
     :header (html/verdict-header (cols/language verdict) application verdict)
     :footer (html/verdict-footer)
     :title  (html/pdf-title (cols/language verdict) application verdict)}))

(defn verdict-tags-html
  "Processes `verdict-tags` result into html."
  [{:keys [body header footer title]}]
  {:pre [body]} ;; At least
  {:body   (html/html body)
   :header (html/html header)
   :footer (hiccup.core/html footer)
   :title  title})

(defn upload-and-attach-pdf!
  [{:keys [command pdf attachment-options file-options]}]
  (let [{:keys [user]} command
        user-id  (:id user)
        uploaded (file-upload/save-file (assoc file-options :content (:pdf-file-stream pdf))
                                        {:linked           false
                                         :uploader-user-id user-id})]
    ;; We know that laundry produces a PDF/A compliant file, so just state the archivability without validation
    (att/attach! command nil attachment-options uploaded {:result {:archivable true}})))

(defn create-verdict-attachment
  "Creates PDF for the verdict and uploads it as an attachment. Returns
  the attachment-id or nil if the attachment could not be created."
  [{:keys [application created] :as command} verdict pdf for-bulletin?]
  (let [proposal?        (vc/proposal? verdict)
        contract?        (vc/contract? verdict)
        verdict-att-type (partial pate-sc/resolve-verdict-attachment-type application)]
    (upload-and-attach-pdf!
      {:command            command
       :pdf                pdf
       :attachment-options {:created         created
                            :attachment-type (cond
                                               for-bulletin? pate-sc/bulletin-attachment-type
                                               proposal?     (verdict-att-type :paatosehdotus)
                                               :else         (verdict-att-type))
                            :source          {:type "verdicts"
                                              :id   (:id verdict)}
                            ;; Bulletin verdicts (aka "kuulutusasiakirja") should be replaceable by the authority user
                            :locked          (not for-bulletin?)
                            :read-only       (not for-bulletin?)
                            :contents        (i18n/localize (cols/language verdict)
                                                            (cond
                                                              proposal? :pate-verdict-proposal
                                                              contract? :pate.verdict-table.contract
                                                              :else     :pate-verdict))}
       :file-options       {:filename (i18n/localize-and-fill (cols/language verdict)
                                                              (cond
                                                                for-bulletin? :pdf.kuulutus.filename
                                                                proposal?     :pdf.proposal.filename
                                                                contract?     :pdf.contract.filename
                                                                :else         :pdf.verdict.filename)
                                                              (:id application)
                                                              (date/finnish-datetime
                                                                (or (some-> verdict :published :published)
                                                                    (some-> verdict :proposal :proposed))
                                                                :zero-pad))}})))

;; TODO: Verdict details MUST NOT change in the new version. Only the
;; signatures must be replaced.
(defn create-verdict-attachment-version
  "Creates a verdict attachments as a new version to previously created
  verdict attachment. Used when a contract is signed.
  For verdict proposals, creates a new version of verdict proposal attachment.
  Used when a verdict proposal is updated."
  [{:keys [application created] :as command} verdict attachment-id pdf]
  {:pre [(some? attachment-id)]}
  (with-open [^InputStream stream (:pdf-file-stream pdf)]
    (att/upload-and-attach! command
                            {:created       created
                             :attachment-id attachment-id}
                            {:filename (i18n/localize-and-fill (cols/language verdict)
                                                               (cond
                                                                 (vc/proposal? verdict) :pdf.proposal.filename
                                                                 (vc/contract? verdict) :pdf.contract.filename
                                                                 :else :pdf.verdict.filename)
                                                               (:id application)
                                                               (date/finnish-datetime created :zero-pad))
                             :content  stream})))

(defn verdict-html->pdf
  "Given `html` to pdf with margins suitable for verdicts."
  [html system]
  {:pre [(map? system)]}
  (html-pdf/html->pdf system html {:top    "35mm"
                                   :bottom "25mm"
                                   :left   "9mm"
                                   :right  "9mm"}))

(defn create-verdict-preview
  "Creates draft version of the verdict
  PDF. Returns :pdf-file-stream, :filename map or :error map."
  [{:keys [application created system]} verdict]
  (let [pdf (-> (verdict-tags application (assoc verdict :preview? true) false)
                verdict-tags-html
                (verdict-html->pdf system))]
    (if (:ok pdf)
      (assoc pdf :filename (i18n/localize-and-fill (cols/language verdict)
                                                   :pdf.draft
                                                   (:id application)
                                                   (date/finnish-date created)))
      (assoc pdf :error :pate.pdf-verdict-error))))
