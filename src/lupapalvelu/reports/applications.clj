(ns lupapalvelu.reports.applications
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vi]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.reports.parties :as parties]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all])
  (:import (java.io OutputStream)))

(set! *warn-on-reflection* true)

(defn fin-date [ts]
  (date/finnish-date ts :zero-pad))

(defn handler-roles-org [org-id]
  (mongo/select-one :organizations {:_id org-id} [:handler-roles]))

(defn open-applications-for-organization [organizationId excluded-operations]
  (let [query     (cond-> {:organization organizationId
                           :state {$in ["submitted" "open" "draft" "sent" "complementNeeded"]}
                           :infoRequest false}
                    excluded-operations (assoc :primaryOperation.name {$nin excluded-operations}))
        roles-org (handler-roles-org organizationId)]
    (map #(app/enrich-application-handlers % roles-org)
         (mongo/select :applications
                       query
                       [:_id :created :opened :submitted :modified
                        :state :handlers
                        :primaryOperation :secondaryOperations]))))

(defn- submitted-applications-between [orgId startTs endTs excluded-operations]
  (let [now        (now)
        endTs      (min now endTs)
        query      (cond-> {:organization orgId
                            $or           [{:submitted   {$gte startTs
                                                          $lte endTs}
                                            :infoRequest false}
                                           {:opened      {$gte startTs
                                                          $lte endTs}
                                            :infoRequest true}]}
                     excluded-operations (assoc :primaryOperation.name {$nin excluded-operations}))
        projection [:submitted :modified :state :handlers :documents :permitType
                    :primaryOperation :secondaryOperations :verdictDate :history
                    :infoRequest :opened :answered]
        aggregate  [{$match query}
                    {$addFields {:sortfield {$ifNull ["$submitted" "$opened"]}}}
                    {$sort {:sortfield 1}}
                    {$project (merge
                                {:id "$_id"}
                                (zipmap projection (repeat 1)))}]
        roles-org  (handler-roles-org orgId)]
    (map #(app/enrich-application-handlers % roles-org)
         (mongo/aggregate :applications aggregate))))




(defn parties-between-data [orgId startTs endTs]
  (mongo/select :applications
                {:organization orgId
                 :submitted    {$gte startTs
                                $lte endTs}
                 :infoRequest  false}
                [:_id :submitted  :address :modified :state
                 :documents.schema-info.name :documents.schema-info.subtype
                 :documents.data
                 :primaryOperation]
                {:submitted 1}))

(defn company-applications-between [company-id start-ts end-ts]
  (mongo/select :applications
                {:auth.id company-id
                 :modified {$gte (Long/parseLong start-ts 10)
                            $lte (Long/parseLong end-ts 10)}}))

(defn digitized-applications-between [user start-ts end-ts projection]
  (let [base-query {:permitType   "ARK"
                    :created      {$gte (Long/parseLong start-ts 10)
                                   $lte (Long/parseLong end-ts 10)}}
        org-ids    (usr/organization-ids-by-roles (usr/with-org-auth user) #{:digitizer :archivist :authorityAdmin})
        query      (if (nil? user)
                     base-query
                     (if (usr/user-is-pure-digitizer? user)
                       (assoc base-query :auth.id (:id user))
                       (assoc base-query :organization {$in org-ids})))]
    (mongo/select :applications
                  query
                  projection)))

(defn- post-verdict-applications [organizationId startTs endTs]
  (mongo/select :applications
                {:organization organizationId
                 :state        {$in states/post-verdict-states}
                 :verdictDate  {$gte startTs $lte endTs}}
                [:_id :state :primaryOperation :verdictDate
                 :documents :tosFunction :organization
                 ::address :verdicts :pate-verdicts
                 :started]))

(defn- authority [app]
  (->> app
       :handlers
       (util/find-first :general)
       ((juxt :firstName :lastName))
       (ss/join " ")))

(defn- other-handlers [lang app]
  (->> app
       :handlers
       (remove :general)
       (map #(format "%s %s (%s)" (:firstName %) (:lastName %) (get-in % [:name (keyword lang)])))
       (ss/join ", ")))

(defn- contact [party key]
  (or (-> party :yhteystiedot key)
      (-> party :yhteyshenkilo :yhteystiedot key)))

(defn- applicants [app]
  (->> (domain/get-applicant-documents (:documents app))
       (map app-meta/party-name-from-doc-first-last)
       (ss/join "; ")))

(defn- applicants-emails [app]
  (->> (domain/get-applicant-documents (:documents app))
       (map domain/get-applicant-info-from-doc)
       (map :email)
       (ss/join "; ")))

(defn- applicants-addresses [app]
  (->> (domain/get-applicant-documents (:documents app))
       (map domain/get-party-info-from-doc)
       (map parties/get-osoite)
       (ss/join "; ")))

(defn- applicants-phones [app]
  (->> (domain/get-applicant-documents (:documents app))
       (map domain/get-party-info-from-doc)
       (map #(contact % :puhelin))
       (ss/join "; ")))

(defn- tyomaasta-vastaava [app]
  (-> app
      (domain/get-document-by-subtype :tyomaasta-vastaava)
      app-meta/party-name-from-doc-first-last))

(defn- tyomaasta-vastaava-contact [key app]
  (-> app
      (domain/get-document-by-subtype :tyomaasta-vastaava)
      domain/get-party-info-from-doc
      (contact key)))

(defn- tyomaasta-vastaava-address [app]
  (-> app
      (domain/get-document-by-subtype :tyomaasta-vastaava)
      domain/get-party-info-from-doc
      parties/get-osoite))

(defn- maksaja [app]
  (-> app
      (domain/get-document-by-subtype :maksaja)
      app-meta/party-name-from-doc-first-last))

(defn- localized-state [lang app]
  (i18n/localize lang (get app :state)))

(defn- localized-application-type [lang app]
  (i18n/localize lang (if (:infoRequest app)
                        "applications.inforequest"
                        "applications.application")))

(defn- date-value [key app]
  (fin-date (get app key)))

(defn- inforequest-opened [app]
  (some->> app
           :history
           (util/filter-by-key :state "info")
           seq
           (apply min-key :ts)
           :ts
           fin-date))

(defn- localized-operation [lang operation]
  (i18n/localize lang "operations" (:name operation)))

(defn- localized-primary-operation [lang app]
  (localized-operation lang (:primaryOperation app)))

(defn- localized-secondary-operations [lang {:keys [secondaryOperations]}]
  (when (seq secondaryOperations)
    (ss/join "\n" (map (partial localized-operation lang) secondaryOperations))))

(defn- tyoaika [key app]
  (-> app (domain/get-document-by-name "tyoaika") :data key :value fin-date))

(defn open-applications-for-organization-in-excel! ^OutputStream
  [organizationId lang & [excluded-operations]]
  ;; Create a spreadsheet and save it
  (let [data               (open-applications-for-organization organizationId excluded-operations)
        sheet-name         (str (i18n/localize lang "applications.report.open-applications.sheet-name-prefix")
                                " "
                                (fin-date (now)))
        header-row-content (map (partial i18n/localize lang) ["applications.id.longtitle"
                                                              "applications.authority"
                                                              "application.handlers.other"
                                                              "applications.status"
                                                              "applications.opened"
                                                              "applications.submitted"
                                                              "applications.lastModified"
                                                              "operations.primary"
                                                              "application.operations.secondary"])
        row-fn (juxt :id
                     authority
                     (partial other-handlers lang)
                     (partial localized-state lang)
                     (partial date-value :opened)
                     (partial date-value :submitted)
                     (partial date-value :modified)
                     (partial localized-primary-operation lang)
                     (partial localized-secondary-operations lang))]
    (excel/xlsx-stream (excel/create-workbook data sheet-name header-row-content row-fn))))

(defn- document-area
  "First `kokonaisala` field value (as double) or nil. Note: even an empty/bad value is
  returned (no fallbacks). The resolution uses only data and does not care about
  schemas. Thus, an accidental fallback is a (somewhat theoretical) possibility."
  [{data :data}]
  (when-let [area (some-<>> [:laajennuksen-tiedot.mitat.kokonaisala
                             :mitat.kokonaisala
                             :kokonaisala
                             :toimenpiteet.kokonaisala]
                            (some #(when-let [m (get-in data (util/split-kw-path %))]
                                     (str (:value m))))
                            ss/blank-as-nil
                            (ss/replace <> "," ".") ;; "1,23" = "1.23" = 1.23
                            util/->double)]
    (when-not (= "0.0" (str area))
      area)))

(defn- total-application-area [{:keys [permitType documents]}]
  (when (#{"R" "P"} permitType)
    (some->> documents
             (keep document-area)
             seq
             (apply +))))

(defn applications-between-excel ^OutputStream
  [organizationId startTs endTs lang excluded-operations]
  (let [data               (submitted-applications-between organizationId startTs endTs excluded-operations)
        sheet-name         (str (i18n/localize lang "applications.report.applications-between.sheet-name-prefix")
                                " "
                                (fin-date (now)))
        header-row-content (map (partial i18n/localize lang) ["applications.id.longtitle"
                                                              "applications.type"
                                                              "applications.authority"
                                                              "application.handlers.other"
                                                              "applications.status"
                                                              "applications.submitted"
                                                              "pate-dates.verdict-date"
                                                              "operations.primary"
                                                              "application.operations.secondary"
                                                              "report.kokonaisala"
                                                              "inforequest_opened"
                                                              "inforequest_answered"])
        row-fn (juxt :id
                     (partial localized-application-type lang)
                     authority
                     (partial other-handlers lang)
                     (partial localized-state lang)
                     (partial date-value :submitted)
                     (partial date-value :verdictDate)
                     (partial localized-primary-operation lang)
                     (partial localized-secondary-operations lang)
                     total-application-area
                     inforequest-opened
                     (partial date-value :answered))]

    (excel/xlsx-stream (excel/create-workbook data sheet-name header-row-content row-fn))))


(defn parties-between-excel ^OutputStream
  [system organizationId startTs endTs lang]
  {:pre [(map? system)]}
  (let [[foreman-apps other-apps] ((juxt filter remove)
                                    foreman-util/foreman-app?
                                    (parties-between-data organizationId startTs endTs))
        result-map {:private-applicants  []
                    :company-applicant   []
                    :designers           []
                    :foremen             []}
        reducer-fn (fn [res app]
                     (-> res
                         (update :private-applicants concat (parties/private-applicants system app lang))
                         (update :company-applicants concat (parties/company-applicants system app lang))
                         (update :designers concat (parties/designers system app lang))
                         (update :private-payers concat (parties/parties system app "maksaja" false lang))
                         (update :company-payers concat (parties/parties system app "maksaja" true lang))
                         (update :private-site-responsibles concat (parties/parties system app "tyomaasta-vastaava" false lang))
                         (update :company-site-responsibles concat (parties/parties system app "tyomaasta-vastaava" true lang))))
        enriched-foremen (parties/enrich-foreman-apps foreman-apps)
        data (-> (reduce reducer-fn result-map other-apps)
                 (assoc :foremen (map #(parties/foremen system % lang) enriched-foremen)))
        wb (excel/create-workbook
             [{:sheet-name (i18n/localize lang "henkilohakijat")
               :header (parties/applicants-field-localization :private lang)
               :row-fn parties/private-applicants-row-fn
               :data (:private-applicants data)}
              {:sheet-name (i18n/localize lang "yrityshakijat")
               :header (parties/applicants-field-localization :company lang)
               :row-fn parties/company-applicants-row-fn
               :data (:company-applicants data)}
              {:sheet-name (i18n/localize lang "suunnittelijat")
               :header (parties/designer-fields-localized lang)
               :row-fn parties/designers-row-fn
               :data (:designers data)}
              {:sheet-name (i18n/localize lang "tyonjohtajat")
               :header (parties/foreman-fields-lozalized lang)
               :row-fn parties/foremen-row-fn
               :data (:foremen data)}
              {:sheet-name (i18n/localize lang "henkilomaksajat")
               :header (parties/applicants-field-localization :private lang)
               :row-fn parties/private-applicants-row-fn
               :data (:private-payers data)}
              {:sheet-name (i18n/localize lang "yritysmaksajat")
               :header (parties/applicants-field-localization :company lang)
               :row-fn parties/company-applicants-row-fn
               :data (:company-payers data)}
              {:sheet-name (i18n/localize lang "henkilo-tyomaasta-vastaavat")
               :header (parties/applicants-field-localization :private lang)
               :row-fn parties/private-applicants-row-fn
               :data (:private-site-responsibles data)}
              {:sheet-name (i18n/localize lang "yritys-tyomaasta-vastaavat")
               :header (parties/applicants-field-localization :company lang)
               :row-fn parties/company-applicants-row-fn
               :data (:company-site-responsibles data)}])]
    (excel/hyperlinks-to-formulas! wb)
    (excel/xlsx-stream wb)))

(defn post-verdict-excel ^OutputStream
  [organizationId startTs endTs lang]
  (letfn [(tos-function-name [{tos-function :tosFunction org-id :organization}]
            (:name (tos/tos-function-with-name tos-function org-id)))
          (latest-published-date [{:keys [verdicts pate-verdicts]}]
            (->> (concat verdicts pate-verdicts)
                 (map vc/verdict-published)
                 (remove nil?)
                 (apply max)
                 fin-date))]
    (let [archiving-enabled? (:permanent-archive-enabled (org/get-organization organizationId))
          sheet-name         (str (i18n/localize lang "authorityAdmin.postVerdictReports.sheet-name-prefix")
                                  " "
                                  (fin-date (now)))
          header-titles      ["applications.id.longtitle"
                              "application.address"
                              "application.applicants"
                              "application.applicants.email"
                              "application.applicants.address"
                              "application.applicants.phone"
                              "application.site-responsible"
                              "application.site-responsible.email"
                              "application.site-responsible.address"
                              "application.site-responsible.phone"
                              "application.payer"
                              "operations.primary"
                              "applications.status"
                              "pate-verdict-template.published"
                              "pate-dates.verdict-date"
                              "tyoaika.tyoaika-alkaa-ms"
                              "tyoaika.tyoaika-paattyy-ms"
                              "constructionStarted"
                              "pate-verdict.giver"
                              (when archiving-enabled? "tos.function")]
          header-row-content (map (partial i18n/localize lang) (remove nil? header-titles))
          data               (post-verdict-applications organizationId startTs endTs)
          cell-data-fns      [:id
                              :address
                              applicants
                              applicants-emails
                              applicants-addresses
                              applicants-phones
                              tyomaasta-vastaava
                              (partial tyomaasta-vastaava-contact :email)
                              tyomaasta-vastaava-address
                              (partial tyomaasta-vastaava-contact :puhelin)
                              maksaja
                              (partial localized-primary-operation lang)
                              (partial localized-state lang)
                              latest-published-date
                              (comp fin-date :verdictDate)
                              (partial tyoaika :tyoaika-alkaa-ms)
                              (partial tyoaika :tyoaika-paattyy-ms)
                              (comp fin-date :started)
                              vi/verdict-giver
                              (when archiving-enabled? tos-function-name)
                              ]
          row-fn             (->> cell-data-fns
                                  (remove nil?)
                                  (apply juxt))]
      (excel/xlsx-stream (excel/create-workbook data sheet-name header-row-content row-fn)))))

(defn- company-report-headers [lang]
  (map (partial i18n/localize lang) ["company.report.excel.header.buildingid"
                                     "company.report.excel.header.operation"
                                     "company.report.excel.header.usage"
                                     "company.report.excel.header.useractions"
                                     "company.report.excel.header.attachment.useractions"
                                     "company.report.excel.header.inUse"
                                     "company.report.excel.header.review"
                                     "company.report.excel.header.reviews"
                                     "company.report.excel.header.id"
                                     "company.report.excel.header.title"
                                     "company.report.excel.header.state"
                                     "company.report.excel.header.organization"
                                     "company.report.excel.header.applicant"
                                     "company.report.excel.header.attachment.pre"
                                     "company.report.excel.header.attachment.post"
                                     "company.report.excel.header.building-type"
                                     "company.report.excel.header.property-id"]))

(defn- company-foreman-headers [lang]
  (map (partial i18n/localize lang) ["company.report.excel.header.operation"
                                     "company.report.excel.header.useractions"
                                     "company.report.excel.header.attachment.useractions"
                                     "company.report.excel.header.id"
                                     "company.report.excel.header.title"
                                     "company.report.excel.header.state"
                                     "company.report.excel.header.organization"
                                     "company.report.excel.header.applicant"
                                     "company.report.excel.header.attachment"]))

(defn- digitizer-report-headers [lang]
  (map (partial i18n/localize lang) ["digitizer.report.excel.header.applicationId"
                                     "digitizer.report.excel.header.date"
                                     "digitizer.report.excel.header.attachmentCount"]))

(defn- digitizer-application-report-headers [lang include-canceled-apps?]
  (let [headers (cond-> ["digitizer.report.excel.header.applicationId"
                         "digitizer.report.excel.header.created"
                         "digitizer.report.excel.header.archived"
                         "digitizer.report.excel.header.digitizer"
                         "digitizer.report.excel.header.user-id"
                         "digitizer.report.excel.header.organization"
                         "digitizer.report.excel.header.address"]
                        include-canceled-apps? (conj "digitizer.report.excel.header.canceled"))]
    (map (partial i18n/localize lang) headers)))



(defn- usage [application]
  (when-let [documents (:documents application)]
    (let [building-info (domain/get-document-by-name documents "uusiRakennus")]
      (get-in building-info [:data :kaytto :kayttotarkoitus :value]))))

(defn- building-type [application]
  (when-let [documents (:documents application)]
    (let [building-info          (domain/get-document-by-name documents "uusiRakennus")]
        (get-in building-info [:data :kaytto :rakennusluokka :value]))))

(defn- inUse [application]
  (let [state-history (util/find-first #(= "inUse" (:state %)) (:history application))]
    (fin-date (:ts state-history))))

(defn- final-review-date [application]
  (let [tasks           (:tasks application)
        review-tasks    (filter #(= "task-katselmus" (-> % :schema-info :name)) tasks)
        final-review    (util/find-by-key :taskname "Loppukatselmus" review-tasks)]
    (get-in final-review [:data :katselmus :pitoPvm :value])))

(defn- applicant-name [application]
  (when-let [applicant-data (:data (first (domain/get-documents-by-subtype (:documents application) :hakija)))]
    (if (= (get-in applicant-data [:_selected :value]) "henkilo")
      (str (get-in applicant-data [:henkilo :henkilotiedot :etunimi :value]) " " (get-in applicant-data [:henkilo :henkilotiedot :sukunimi :value]))
      (str (get-in applicant-data [:yritys :yritysnimi :value])))))

(defn- building-id [application operation]
  (let [operation-doc (domain/get-document-by-operation application operation)
        tunnus (get-in operation-doc [:data :tunnus :value])
        valtakunnallinen-numero (get-in operation-doc [:data :valtakunnallinenNumero :value])]
    (str tunnus " - " valtakunnallinen-numero)))

(defn- row-data [application operation lang user]
  {:building-id                 (building-id application operation)
   :operation                   (localized-operation lang operation)
   :usage                       (usage application)
   :required-actions            (count (filter approval/rejected? (:documents application)))
   :attachment-required-actions (app-meta/count-attachments-requiring-action user application)
   :inuse-date                  (inUse application)
   :final-review-date           (final-review-date application)
   :reviews-count               (count (filter #(= "task-katselmus" (-> % :schema-info :name)) (:tasks application)))
   :id                          (:id application)
   :title                       (:title application)
   :state                       (i18n/localize lang (:state application))
   :organization                (org/get-organization-name (org/get-organization (:organization application)))
   :applicant                   (applicant-name application)
   :attachments-count           (count (:attachments application))
   :pre-verdict-attachments     (count (filter #(contains? states/pre-verdict-states (keyword (:applicationState %))) (:attachments application)))
   :post-verdict-attachments    (count (filter #(contains? states/post-verdict-states (keyword (:applicationState %))) (:attachments application)))
   :permit-type                 (:permitType application)
   :building-type               (building-type application)
   :propertyId                  (p/to-human-readable-property-id (:propertyId application))})

(defn report-data-by-operations [applications lang user]
  (let [apps-with-primary-operation (map (fn [app] (row-data app (:primaryOperation app) lang user)) applications)
        apps-with-secondary-operations (flatten (remove empty? (map (fn [app] (map (fn [opp] (row-data app opp lang user)) (:secondaryOperations app))) applications)))]
    (into apps-with-primary-operation apps-with-secondary-operations)))

(defn company-applications ^OutputStream
  [company-id start-ts end-ts lang user]
  (let [[foreman-apps applications] ((juxt filter remove)
                                    foreman-util/foreman-app?
                                    (company-applications-between company-id start-ts end-ts))
        permit-types (distinct (map :permitType applications))
        applications-row-data (report-data-by-operations applications lang user)
        foreman-app-row-data (report-data-by-operations foreman-apps lang user)
        row-fn (juxt :building-id :operation :usage :required-actions :attachment-required-actions :inuse-date :final-review-date
                     :reviews-count :id :title :state :organization :applicant :pre-verdict-attachments :post-verdict-attachments
                     :building-type :propertyId)
        foreman-row-fn (juxt :operation :required-actions :attachment-required-actions :id :title :state :organization :applicant :attachments-count)
        application-data (map (fn [permit] {:sheet-name (str permit)
                                      :header     (company-report-headers lang)
                                      :row-fn     row-fn
                                      :data       (filter #(= permit (:permit-type %)) applications-row-data)
                                      }) permit-types)
        foreman-app-data {:sheet-name (i18n/localize lang "tyonjohtajat")
                          :header     (company-foreman-headers lang)
                          :row-fn     foreman-row-fn
                          :data       foreman-app-row-data}
        wb (excel/create-workbook (flatten [application-data foreman-app-data]))]
    (excel/xlsx-stream wb)))

(defn digi-report-data [application]
  {:date          (fin-date (:created application))
   :id            (:id application)
   :attachments   (count (:attachments application))})

(defn digi-report-sum [rows]
  (->> (group-by :date rows)
       (map (fn [row] {:date (first row)
                       :attachments (->> row
                                         (second)
                                         (flatten)
                                         (map :attachments)
                                         (apply +))}))))

(defn digitized-attachments ^OutputStream
  [user start-ts end-ts lang]
  (let [applications  (digitized-applications-between user start-ts end-ts [:_id :created :attachments])
        row-data      (map digi-report-data applications)
        sum-data      (digi-report-sum row-data)
        sum-sheet     (i18n/localize lang "digitizer.excel.sum.sheet.name")
        wb            (excel/create-workbook
                        [{:sheet-name  (i18n/localize lang "digitizer.excel.data.sheet.name")
                          :header      (digitizer-report-headers lang)
                          :row-fn      (juxt :id :date :attachments)
                          :data        row-data}
                         {:sheet-name  sum-sheet
                          :header      (rest (digitizer-report-headers lang))
                          :row-fn      (juxt :date :attachments)
                          :data        sum-data}])
        _       (excel/add-sum-row! sum-sheet wb [(i18n/localize lang "digitizer.excel.sum") (apply + (map :attachments sum-data))])]
    (excel/xlsx-stream wb)))

(defn new-digitized-applications-row-data [include-canceled-apps? application]
  (util/assoc-when {:id            (:id application)
                    :organization  (:organization application)
                    :address       (:address application)
                    :created       (fin-date (:created application))
                    :digitizer     (->> application :creator ss/trimwalk ((juxt :firstName :lastName)) (ss/join " "))
                    :user-id       (-> application :creator :username)
                    :archived      (fin-date (-> application :archived :completed))}
                   :canceled (when include-canceled-apps?
                               (fin-date (:canceled application)))))

(defn new-digitized-applications ^OutputStream
  [user start-ts end-ts lang]
  (let [projection                 [:address :created :archived :creator :organization :state :canceled]
        applications               (digitized-applications-between user start-ts end-ts projection)
        {canceled-apps true
         active-apps   false}      (group-by #(-> % :canceled number?) applications)
        row-data-for-active-apps   (->> active-apps
                                        (sort-by :created)
                                        (map (partial new-digitized-applications-row-data false)))
        row-data-for-canceled-apps (->> canceled-apps
                                        (sort-by :created)
                                        (map (partial new-digitized-applications-row-data true)))
        wb                         (excel/create-workbook
                                     [{:sheet-name (i18n/localize lang "digitizer.excel.data.sheet.name")
                                       :header     (digitizer-application-report-headers lang false)
                                       :row-fn     (juxt :id :created :archived :digitizer
                                                         :user-id :organization :address)
                                       :data       row-data-for-active-apps}
                                      {:sheet-name (i18n/localize lang "digitizer.excel.data.sheet.name-canceled")
                                       :header     (digitizer-application-report-headers lang true)
                                       :row-fn     (juxt :id :created :archived :digitizer
                                                         :user-id :organization :address :canceled)
                                       :data       row-data-for-canceled-apps}])]
    (excel/xlsx-stream wb)))
