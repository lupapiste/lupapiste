(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts. Utilises a simple
  schema-based mechanism for the layout definition and generation."
  (:require [hiccup.core]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schemas :refer [resolve-verdict-attachment-type]]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all])
  (:import [java.io InputStream]
           [java.util Locale]))


(defn party-details
  "Returns list of name, address maps for properly filled party
  documents. If a doc does not have name information it is omitted."
  [{:keys [application lang]} party-documents]
  (->> party-documents
       (map :data)
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

(defn format-party-details
  "Formats the party detail with a line for the party name and another for the party address"
  [party-details]
  (->> party-details
       (map (fn [{:keys [name address contact]}]
              (cols/join-non-blanks "\n" name address contact)))
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

(defn designer-approved?
  [designer]
  (= "approved" (get-in designer [:meta :_approved])))

(defn designers [{lang :lang app :application}]
  (let [role-keys [:pdf :kuntaRoolikoodi]
        head-loc (i18n/localize lang role-keys "p\u00e4\u00e4suunnittelija")
        heads    (->> (domain/get-documents-by-name app :paasuunnittelija)
                      (filter designer-approved?)
                      (remove :disabled)
                      (map :data)
                      (map #(assoc % :role head-loc)))
        others   (->> (domain/get-documents-by-name app :suunnittelija)
                      (filter designer-approved?)
                      (remove :disabled)
                      (map :data))]
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

(defn operation-infos
  [application]
  (mapv (util/fn-> :schema-info :op)
        (app-utils/get-sorted-operation-documents application)))

(defn operation-name
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
  (let [infos         (map (util/fn->> (operation-name application)
                                       (i18n/localize lang :operations)
                                       (hash-map :text))
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
    (some-<>> [:kiinteiston-autopaikat :rakennetut-autopaikat]
              (map park)
              (sort-by :text)
              vec
              (conj <> (park :autopaikat-yhteensa))
              (remove (comp ss/blank? :amount))
              not-empty
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

(defn- double->str
  "String representation with a maximum of one decimal. Zeroes are nil.
  3.921 -> 3.9
  3.990 -> 4
  0 -> nil
  0.02 -> nil
  nil -> nil"
  [v]
  (when v
    (let [s         (String/format Locale/ROOT "%.1f" (to-array [(double v)]))
          ; default format has changed in new Java
          ; https://www.oracle.com/technetwork/java/javase/9-relnote-issues-3704069.html#JDK-8008577
          ; but we use Locale/ROOT to preseve "dot" as separator
          [_ int-s] (re-find #"(\d+).0+$" s)
          s         (or int-s s)]
      (when-not (= s "0")
        s))))

(defn dimensions
  "Map of :kerrosala, :rakennusoikeudellinenKerrosala, :kokonaisala
  and :tilavuus keys. The values are a _sum_ of the corresponding
  fields in the supported document schemas. Values are strings and
  doubles are shown with one decimal. Nil if none of the application
  documents is supported."
  [{:keys [documents]}]
  ;; schema name - data path
  (let [supported {:uusiRakennus                             :mitat
                   :uusi-rakennus-ei-huoneistoa              :mitat
                   :rakennuksen-laajentaminen                :laajennuksen-tiedot.mitat
                   :rakennuksen-laajentaminen-ei-huoneistoja :laajennuksen-tiedot.mitat}
        schemas   (-> supported keys set)
        fields    [:kerrosala :rakennusoikeudellinenKerrosala
                   :kokonaisala :tilavuus]]
    (some->> documents
             (filter (util/fn->> :schema-info :name keyword (contains? schemas)))
             (reduce (fn [acc {:keys [data schema-info]}]
                       (update-sum-map acc
                                       (->> schema-info :name keyword
                                            (get supported)
                                            util/split-kw-path
                                            (get-in data))
                                       fields))
                     {})
             (util/map-values double->str))))

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

(defn verdict-properties
  "Adds all kinds of different properties to the options. It is then up
  to category-specific verdict-body methods and corresponding
  pdf-layouts whether every property is displayed in the pdf or not."
  [{:keys [lang application verdict] :as options}]
  (let [buildings (verdict-buildings options)]
    (assoc (cols/verdict-properties options)
           :application-id               (:id application)
           :property-id                  (property-id application)
           :property-id-ya               (property-id-ya options)
           :applicants                   (->> options applicants format-party-details)
           :site-responsibles            (->> options site-responsibles format-party-details)
           :operations                   (assoc-in (operations options) [0 ::styles :text] :bold)
           :designers                    (designers options)
           :dimensions                   (dimensions application)
           :paloluokka                   (combine-building-fields :paloluokka buildings)
           :vss-luokka                   (combine-building-fields :vss-luokka buildings)
           :kokoontumistilanHenkilomaara (combine-building-fields :kokoontumistilanHenkilomaara buildings)
           :parking                      (parking buildings lang)
           :attachments                  (verdict-attachments options)
           :organization                 (html/organization-name lang application verdict)
           :link-permits                 (link-permits options)
           :tj-vastattavat-tyot          (tj-vastattavat-tyot application lang)
           :signatures                   (signatures options))))

(defn verdict-tags
    "Source-data is a map containing keys referred in pdf-layout source
  definitions. Returns :header, :body, :footer map."
  [application verdict]
  {:body   (cols/content (verdict-properties {:lang        (cols/language verdict)
                                              :application (tools/unwrapped application)
                                              :verdict     verdict})
                         (layouts/pdf-layout verdict))
   :header (html/verdict-header (cols/language verdict) application verdict)
   :footer (html/verdict-footer)
   :title  (html/pdf-title (cols/language verdict) application verdict)})

(defn verdict-tags-html
  "Processes `verdict-tags` result into html."
  [{:keys [body header footer title]}]
  {:pre [body]} ;; At least
  {:body   (html/html body)
   :header (html/html header)
   :footer (hiccup.core/html footer)
   :title  title})

(defn verdict-html
  [application verdict]
  (verdict-tags-html (verdict-tags application verdict)))

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
  [{:keys [application created] :as command} verdict pdf]
  (let [proposal?    (vc/proposal? verdict)
        contract?    (vc/contract? verdict)]
    (upload-and-attach-pdf!
      {:command            command
       :pdf                pdf
       :attachment-options {:created         created
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
                                                              :else :pate-verdict))}
       :file-options       {:filename (i18n/localize-and-fill (cols/language verdict)
                                                              (cond
                                                                proposal? :pdf.proposal.filename
                                                                contract? :pdf.contract.filename
                                                                :else :pdf.filename)
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
                                                                 :else :pdf.filename)
                                                               (:id application)
                                                               (date/finnish-datetime created :zero-pad))
                             :content  stream})))

(defn verdict-html->pdf
  "Given `html` to pdf with margins suitable for verdicts."
  [html]
  (html-pdf/html->pdf html {:top    "35mm"
                            :bottom "25mm"
                            :left   "9mm"
                            :right  "9mm"}))

(defn create-verdict-preview
  "Creates draft version of the verdict
  PDF. Returns :pdf-file-stream, :filename map or :error map."
  [{:keys [application created]} verdict]
  (let [pdf (verdict-html->pdf (verdict-html application (assoc verdict :preview? true)))]
    (if (:ok pdf)
      (assoc pdf :filename (i18n/localize-and-fill (cols/language verdict)
                                                   :pdf.draft
                                                   (:id application)
                                                   (date/finnish-date created)))
      (assoc pdf :error :pate.pdf-verdict-error))))
