(ns lupapalvelu.application-search
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [taoensso.timbre :refer [debug info warn error errorf]]))

;;
;; Query construction
;;

(def max-date 253402214400000)

(defn- parse-search-term
  "Splits the term into actual term and municipality codes if the last
  term part matches (even partly) any municipality names."
  [term]
  (let [parts (ss/split term #"[\s,\\.]+")
        munis (find-address/municipality-codes (last parts))]
    {:term           (if (empty? munis)
                       term
                       (ss/join " " (butlast parts)))
     :municipalities munis}))

(defn- make-free-text-query [filter-search]
  (let [search-keys [:_applicantIndex
                     :_id
                     :address
                     :documents.data.henkilo.henkilotiedot.sukunimi.value
                     :documents.data.yritys.yritysnimi.value
                     :foreman
                     :verdicts.kuntalupatunnus
                     :pate-verdicts.data.kuntalupatunnus._value ;; Draft
                     :pate-verdicts.data.kuntalupatunnus ;; Published
                     ]
        fuzzy       (ss/fuzzy-re filter-search)
        or-query    {$or (map #(hash-map % {$regex fuzzy $options "i"}) search-keys)}
        ops         (operations/operation-names filter-search)]
    (cond-> or-query
      (seq ops) (update-in [$or] concat [{:primaryOperation.name {$in ops}}
                                         {:secondaryOperations.name {$in ops}}]))))

(defn make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches p/property-id-pattern filter-search) {:propertyId (p/to-property-id filter-search)}
    (re-matches v/rakennustunnus-pattern filter-search) {:buildings.nationalId filter-search}
    :else (make-free-text-query filter-search)))

(def applicant-application-states
  {:state {$in ["open" "submitted" "sent" "complementNeeded" "draft"]}})

(def authority-application-states
  {:state {$in ["submitted" "sent" "complementNeeded"]}})

(def no-handler "no-authority")

(defn- handler-email-to-id [handler]
  (if (ss/contains? handler "@")
    (:id (user/get-user-by-email handler))
    handler))

(defn archival-start-ts-query [organization start-ts]
  {$and
   [{:organization organization}
    {$or [{:submitted {$gte start-ts}}
          {:submitted nil
           :created   {$gte start-ts}}]}]})

(defn- archival-query [user]
  (let [user-orgs (user/organization-ids-by-roles user #{:archivist})
        from-ts-by-orgs (map (fn [org] {:org org :ts (organization/earliest-archive-enabled-ts [org])}) user-orgs)
        base-query {$or [{$and [{:state {$in ["verdictGiven" "constructionStarted" "appealed" "inUse" "foremanVerdictGiven" "acknowledged"]}} {:archived.application nil} {:permitType {$ne "YA"}}]}
                         {$and [{:state {$in states/archival-final-states}} {:archived.completed nil}]}]}]
    (if-not (empty? from-ts-by-orgs)
      {$and [base-query {$or
                         (map (fn [org-detail] (archival-start-ts-query (:org org-detail) (:ts org-detail))) from-ts-by-orgs)}]}
      base-query)))

(defn- event-search [event]
  (and (not (empty? event))
       (not (empty? (:eventType event)))))

(defn make-query [query {:keys [searchText applicationType handlers tags companyTags organizations operations areas modifiedAfter event]} user]

  (let [{:keys [term municipalities]} (when-not (ss/blank? searchText)
                                        (parse-search-term (ss/trim searchText)))]
    {$and
    (filter seq
            [query
             (when-not (ss/blank? term) (make-text-query (ss/trim term)))
             (when (seq municipalities) {:municipality {$in municipalities}})
             (when-let [modified-after (util/->long modifiedAfter)]
               {:modified {$gt modified-after}})
             (if (user/applicant? user)
               (case applicationType
                 "unlimited"          {}
                 "inforequest"        {:state {$in ["answered" "info"]}}
                 "application"        applicant-application-states
                 "construction"       {:state {$in ["verdictGiven" "constructionStarted"]}}
                 "canceled"           {:state "canceled"}
                 "verdict"            {:state {$in states/post-verdict-states}}
                 "foremanApplication" (assoc applicant-application-states :permitSubtype "tyonjohtaja-hakemus")
                 "foremanNotice"      (assoc applicant-application-states :permitSubtype "tyonjohtaja-ilmoitus")
                 {:state {$ne "canceled"}})
               (case applicationType
                 "unlimited"          {}
                 "inforequest"        {:state {$in ["open" "answered" "info"]} :permitType {$ne permit/ARK}}
                 "application"        authority-application-states
                 "construction"       {:state {$in ["verdictGiven" "constructionStarted"]}}
                 "verdict"            {:state {$in states/post-verdict-states}}
                 "canceled"           {:state "canceled"}
                 "foremanApplication" (assoc authority-application-states :permitSubtype "tyonjohtaja-hakemus")
                 "foremanNotice"      (assoc authority-application-states :permitSubtype "tyonjohtaja-ilmoitus")
                 "readyForArchival"   (archival-query user)
                 "archivingProjects"  {:permitType permit/ARK :state {$nin [:archived :canceled]}}
                 {$and [{:state {$ne "canceled"}
                         :permitType {$ne permit/ARK}}
                        {$or [{:state {$ne "draft"}}
                              {:organization {$nin (->> user :orgAuthz keys (map name))}}]}]}))
             (when-not (empty? handlers)
               (if ((set handlers) no-handler)
                 {:handlers {$size 0}}
                 (when-let [handler-ids (seq (remove nil? (map handler-email-to-id handlers)))]
                   {$or [{:auth.id {$in handler-ids}}
                         {:handlers.userId {$in handler-ids}}]})))
             (when-not (empty? tags)
               {:tags {$in tags}})
             (when-not (empty? companyTags)
               {:company-notes {$elemMatch {:companyId (get-in user [:company :id]) :tags {$in companyTags}}}})
             (when-not (empty? organizations)
               {:organization {$in organizations}})
             (when (event-search event)
               (case (first (:eventType event))
                 "warranty-period-end"                 {$and [{:warrantyEnd {"$gte" (or (:start event) 0)
                                                                             "$lt" (or (:end event) max-date)}}]}
                 "license-period-start"                {$and [{:documents.data.tyoaika-alkaa-ms.value {"$gte" (or (:start event) 0)
                                                                                                       "$lt" (or (:end event) max-date)}}]}
                 "license-period-end"                  {$and [{:documents.data.tyoaika-paattyy-ms.value {"$gte" (or (:start event) 0)
                                                                                                         "$lt" (or (:end event) max-date)}}]}
                 "license-started-not-ready"           {$and [{:documents.data.tyoaika-alkaa-ms.value {"$lt" (now)}},
                                                              {:state {$ne "closed"}}]}
                 "license-ended-not-ready"             {$and [{:documents.data.tyoaika-paattyy-ms.value {"$lt" (now)}},
                                                              {:state {$ne "closed"}}]}
                 "announced-to-ready-state-not-ready"  {$and [{:closed {"$lt" (now)}},
                                                              {:state {$ne "closed"}},
                                                              {:permitType "YA"}]}))
             (cond
               (seq operations) {:primaryOperation.name {$in operations}}
               (and (user/authority? user) (not= applicationType "unlimited"))
                                        ; Hide foreman applications in default search, see LPK-923
               {:primaryOperation.name {$nin (cond-> ["tyonjohtajan-nimeaminen-v2"]
                                               (= applicationType "readyForArchival") (conj "aiemmalla-luvalla-hakeminen"))}})
             (when-not (empty? areas)
               (app-utils/make-area-query areas user))])}))

;;
;; Fields
;;

(def- db-fields ; projection
  [:_comments-seen-by :_statements-seen-by :_verdicts-seen-by
   :_attachment_indicator_reset :address :applicant :creator :attachments
   :auth :handlers.firstName :handlers.lastName :authorityNotice :comments :created :documents
   :foreman :foremanRole :infoRequest :location :modified :municipality
   :neighbors :permitType :permitSubtype :primaryOperation :state :statements
   :organization ; required for authorization checks
   :submitted :tasks :urgency :verdicts :archived])

(def- indicator-fields
  (map :field meta-fields/indicator-meta-fields))

(def- frontend-fields
  [:id :address :applicant :creator :handlers :authorityNotice
   :infoRequest :kind :location :modified :municipality
   :primaryOperation :state :submitted :urgency :verdicts
   :foreman :foremanRole :permitType])

(defn- select-fields [application]
  (select-keys
    application
    (concat frontend-fields indicator-fields)))


(defn- enrich-row [app]
  (-> app
      (assoc :handlers (distinct (:handlers app))) ;; Each handler only once.
      app-utils/with-application-kind
      app-utils/location->object))

(def- sort-field-mapping {"applicant" :applicant
                          "handler" [:handlers.0.lastName :handlers.0.firstName]
                          "location" :address
                          "modified" :modified
                          "submitted" :submitted
                          "foreman" :foreman
                          "foremanRole" :foremanRole
                          "state" :state
                          "id" :_id})

(defn dir [asc] (if asc 1 -1))

(defn make-sort [{{:keys [field asc]} :sort}]
  (let [sort-field (sort-field-mapping field)]
    (cond
      (= "type" field) (array-map :permitSubtype (dir asc) :infoRequest (dir (not asc)))
      (nil? sort-field) {}
      (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
      :else (array-map sort-field (dir asc)))))

(defn search [query db-fields sort skip limit]
  (try
    (mongo/with-collection "applications"
      (query/find query)
      (query/fields db-fields)
      (query/sort sort)
      (query/skip skip)
      (query/limit limit))
    (catch com.mongodb.MongoException e
      (errorf "Application search query=%s, sort=%s failed: %s" query sort e)
      (fail! :error.unknown))))


(defn applications-for-user [user {:keys [searchText] :as params}]
  (when (> (count searchText) (env/value :search-text-max-length))
    (fail! :error.search-text-is-too-long))
  (let [user-query  (domain/basic-application-query-for user)
        query       (make-query user-query params user)
        user-total  (future (if (mongo/select-one :applications user-query) 1 0))
        skip        (or (util/->long (:skip params)) 0)
        limit       (or (util/->long (:limit params)) 10)
        apps        (search query db-fields (make-sort params) skip limit)
        rows        (map
                      (comp
                        select-fields
                        enrich-row
                        (partial meta-fields/with-indicators user)
                        #(domain/filter-application-content-for % user)
                        mongo/with-id)
                      apps)]
    {:applications rows
     ; This is only used in the front-end to find out if the user has any applications at all. Full mongo count
     ; is expensive, so we rather just check if the user has at least one application.
     :userTotalCount @user-total}))

(defn query-total-count [user params]
  (let [user-query  (domain/basic-application-query-for user)
        query       (make-query user-query params user)]
    {:totalCount (mongo/count :applications query)}))

;;
;; Public API
;;

(defn public-fields [{:keys [municipality submitted] :as application}]
  {:municipality municipality
   :timestamp submitted
   :operation (app-utils/operation-description application :fi)
   :operationName (i18n/supported-langs-map (partial app-utils/operation-description application))})
