(ns lupapalvelu.reports.applications
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-common :as vc]
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
            [sade.util :as util])
  (:import (java.io OutputStream)))

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

(defn submitted-applications-between [orgId startTs endTs excluded-operations]
  (let [now       (now)
        query     (cond-> {:organization orgId
                                        ;:state {$in ["submitted" "open" "draft"]
                           :submitted {$gte startTs
                                       $lte (if (< now endTs) now endTs)}
                           :infoRequest false}
                    excluded-operations (assoc :primaryOperation.name {$nin excluded-operations}))
        roles-org (handler-roles-org orgId)]
    (map #(app/enrich-application-handlers % roles-org)
         (mongo/select :applications
                       query
                       [:_id :submitted :modified :state :handlers
                        :primaryOperation :secondaryOperations :verdictDate]
                       {:submitted 1}))))

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
                 ::address :verdicts :pate-verdicts]))

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

(defn- applicants [app]
  (->> (domain/get-applicant-documents (:documents app))
       (map app-meta/applicant-name-from-doc-first-last)
       (ss/join "; ")))

(defn- applicants-emails [app]
  (->> (domain/get-applicant-documents (:documents app))
       (map domain/get-applicant-info-from-doc)
       (map :email)
       (ss/join "; ")))

(defn- localized-state [lang app]
  (i18n/localize lang (get app :state)))

(defn- date-value [key app]
  (fin-date (get app key)))

(defn- localized-operation [lang operation]
  (i18n/localize lang "operations" (:name operation)))

(defn- localized-primary-operation [lang app]
  (localized-operation lang (:primaryOperation app)))

(defn- localized-secondary-operations [lang {:keys [secondaryOperations]}]
  (when (seq secondaryOperations)
    (ss/join "\n" (map (partial localized-operation lang) secondaryOperations))))

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

(defn applications-between-excel ^OutputStream
  [organizationId startTs endTs lang excluded-operations]
  (let [data               (submitted-applications-between organizationId startTs endTs excluded-operations)
        sheet-name         (str (i18n/localize lang "applications.report.applications-between.sheet-name-prefix")
                                " "
                                (fin-date (now)))
        header-row-content (map (partial i18n/localize lang) ["applications.id.longtitle"
                                                              "applications.authority"
                                                              "application.handlers.other"
                                                              "applications.status"
                                                              "applications.submitted"
                                                              "pate-dates.verdict-date"
                                                              "operations.primary"
                                                              "application.operations.secondary"])
        row-fn (juxt :id
                     authority
                     (partial other-handlers lang)
                     (partial localized-state lang)
                     (partial date-value :submitted)
                     (partial date-value :verdictDate)
                     (partial localized-primary-operation lang)
                     (partial localized-secondary-operations lang))]

    (excel/xlsx-stream (excel/create-workbook data sheet-name header-row-content row-fn))))


(defn parties-between-excel ^OutputStream
  [organizationId startTs endTs lang]
  (let [[foreman-apps other-apps] ((juxt filter remove)
                                    foreman/foreman-app?
                                    (parties-between-data organizationId startTs endTs))
        result-map {:private-applicants  []
                    :company-applicant   []
                    :designers           []
                    :foremen             []}
        reducer-fn (fn [res app]
                     (-> res
                         (update :private-applicants concat (parties/private-applicants app lang))
                         (update :company-applicants concat (parties/company-applicants app lang))
                         (update :designers concat (parties/designers app lang))))
        enriched-foremen (parties/enrich-foreman-apps foreman-apps)
        data (-> (reduce reducer-fn result-map other-apps)
                 (assoc :foremen (map #(parties/foremen % lang) enriched-foremen)))
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
               :data (:foremen data)}])]
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
                              "operations.primary"
                              "applications.status"
                              "pate-verdict-template.published"
                              "pate-dates.verdict-date"
                              (when archiving-enabled? "tos.function")]
          header-row-content (map (partial i18n/localize lang) (remove nil? header-titles))
          data               (post-verdict-applications organizationId startTs endTs)
          cell-data-fns      [:id
                              :address
                              applicants
                              applicants-emails
                              (partial localized-primary-operation lang)
                              (partial localized-state lang)
                              latest-published-date
                              (comp fin-date :verdictDate)
                              (when archiving-enabled? tos-function-name)]
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
   :required-actions            (count (filter #(= "rejected" (-> % :meta :_approved :value)) (:documents application)))
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
                                    foreman/foreman-app?
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
