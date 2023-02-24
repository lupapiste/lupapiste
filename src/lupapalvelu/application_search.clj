(ns lupapalvelu.application-search
  (:require [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [taoensso.timbre :refer [errorf]]))

;;
;; Query construction
;;

(def max-date 253402214400000)

(defn- parse-municipality-codes
  "Splits the term into parts and tries to resolve municipality codes if the last
  term part is equal to any municipality name."
  [term]
  (let [parts (ss/split term ss/whitespace-pattern)
        munis (find-address/municipality-codes (last parts) =)]
    {:parsed-term    (when-not (empty? munis)
                       (ss/join " " (butlast parts)))
     :original-term  term
     :municipalities munis}))

(defn- make-free-text-query [filter-search]
  (let [ops         (operations/operation-names filter-search)
        search-keys [:_applicantIndex
                     :_id
                     :address
                     :_creatorIndex
                     :documents.data.henkilotiedot.sukunimi.value
                     :documents.data.henkilo.henkilotiedot.sukunimi.value
                     :documents.data.yritys.yritysnimi.value
                     :documents.data.yritys.yhteyshenkilo.henkilotiedot.sukunimi.value
                     :foreman
                     :verdicts.kuntalupatunnus
                     :pate-verdicts.data.kuntalupatunnus._value ;; Draft
                     :pate-verdicts.data.kuntalupatunnus ;; Published
                     ]
        fuzzy       (ss/fuzzy-re filter-search)
        or-query    (if fuzzy
                      {$or (map #(hash-map % {$regex fuzzy $options "i"}) search-keys)}
                      {})]
    (cond-> or-query
      (seq ops) (update $or conj {:primaryOperation.name {$in ops}} {:secondaryOperations.name {$in ops}}))))


(defn- get-general-handlers-for-authority-organizations
  "Fetches the first (and only) general handler id for each of the given organizations.
  Used for filtering the results by general and secondary role below."
  [authority]
  (let [organizations (usr/organization-ids authority)]
    (->> (mongo/select :organizations {:_id {$in organizations}} [:handler-roles])
         (map organization/general-handler-id-for-organization))))

(defn make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^[Ll][PpXx]-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
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
    (:id (usr/get-user-by-email handler))
    handler))

(defn archival-start-ts-query [organization start-ts]
  {$and
   [{:organization organization}
    {:permitType {$ne permit/ARK}}
    {$or [{:submitted {$gte start-ts}}
          {:submitted nil
           :created   {$gte start-ts}}]}]})

(def archival-base-without-ya
  {$and [{:state {$in ["verdictGiven" "constructionStarted" "appealed"
                       "inUse" "foremanVerdictGiven" "acknowledged" "ready" "extinct"]}}
         {:archived.application nil}
         {:permitType {$ne "YA"}}]})

(def archival-base-query-rest
  {$and [{:state {$in states/archival-final-states}}
         {:archived.completed nil}
         {:permitType {$ne permit/ARK}}]})

(def archival-base-query
  {$or [archival-base-without-ya
        archival-base-query-rest]})

(defn- archival-query [user]
  (let [user-orgs (usr/organization-ids-by-roles user #{:archivist})
        from-ts-by-orgs (map (fn [org] {:org org :ts (organization/earliest-archive-enabled-ts [org])}) user-orgs)]
    (if (some :ts from-ts-by-orgs)
      {$and
       [archival-base-query
        {$or
         (->> from-ts-by-orgs
              (filter :ts)
              (map (fn [org-detail]
                     (archival-start-ts-query (:org org-detail) (:ts org-detail)))))}]}
      archival-base-query)))

(defn- event-search [event]
  (and (seq event)
       (seq (:eventType event))))

(defn- state-filter-states [{:keys [states]}]
  (case (count states)
    0 nil
    1 {:state (first states)}
    {:state {$in states}}))

(defn make-query [query
                  {:keys [searchText applicationType handlers
                          userIsGeneralHandler tags companyTags
                          organizations operations areas modifiedAfter
                          event]
                   :as   params}
                  user]
  (let [searchText                           (ss/trim searchText)
        {:keys [parsed-term municipalities]} (parse-municipality-codes searchText)
        term                                 (or parsed-term searchText)
        construction-states                  ["constructionStarted" "inUse" "onHold"]
        verdict-states                       ["verdictGiven" "appealed"
                                              "foremanVerdictGiven" "acknowledged"]
        finalized-states                     ["closed" "ready" "finished" "extinct"
                                              "agreementPrepared" "agreementSigned"]]
    {$and
     (filter seq
             [query
              (when-not (ss/blank? term) (make-text-query term))
              (when (seq municipalities) {:municipality {$in municipalities}})
              (when-let [modified-after (util/->long modifiedAfter)]
                {:modified {$gt modified-after}})
              (if (usr/applicant? user)
                (case applicationType
                  "unlimited"          {}
                  "inforequest"        {:state {$in ["answered" "info"]}}
                  "application"        applicant-application-states
                  "construction"       {:state {$in construction-states}}
                  "finalized"          {:state {$in finalized-states}}
                  "canceled"           {:state "canceled"}
                  "verdict"            {:state {$in verdict-states}}
                  "foremanApplication" (assoc applicant-application-states :permitSubtype "tyonjohtaja-hakemus")
                  "foremanNotice"      (assoc applicant-application-states :permitSubtype "tyonjohtaja-ilmoitus")
                  "state-filter"       (state-filter-states params)
                  {:state {$ne "canceled"}})
                (case applicationType
                  "unlimited"          {}
                  "inforequest"        {:state {$in ["answered" "info" "open" ]} :permitType {$ne permit/ARK}}
                  "application"        authority-application-states
                  "construction"       {:state {$in construction-states}}
                  "verdict"            {:state {$in verdict-states}}
                  "finalized"          {:state {$in finalized-states}}
                  "canceled"           {:state "canceled"}
                  "foremanApplication" (assoc authority-application-states :permitSubtype "tyonjohtaja-hakemus")
                  "foremanNotice"      (assoc authority-application-states :permitSubtype "tyonjohtaja-ilmoitus")
                  "readyForArchival"   (archival-query user)
                  "archivingProjects"  {:permitType permit/ARK :state {$nin [:archived :canceled]}}
                  "state-filter"       (state-filter-states params)
                                        ; {:state {$nin ["draft" "canceled"}}, permitType {$ne permit/ARK}}
                  {$and [{:state      {$ne "canceled"}
                          :permitType {$ne permit/ARK}}
                                        ; TODO this cover like 1% of use cases, where the authority is itself
                                        ; also applicant, and has draft applications
                                        ; come up with another way to shown "own but drafts" and return the simpler query without $and+$or
                         {$or [{:state {$ne "draft"}}
                               {:organization {$nin (->> user :orgAuthz keys (map name))}}]}]}))
              (when-not (empty? handlers)
                (if ((set handlers) no-handler)
                  {:handlers {$size 0}}
                  (when-let [handler-ids (seq (remove nil? (map handler-email-to-id handlers)))]
                    {:handlers.userId {$in handler-ids}})))
              (when (some? userIsGeneralHandler)
                (let [userId     (:id user)
                      generalIds (get-general-handlers-for-authority-organizations user)]
                  (if userIsGeneralHandler
                    {:handlers {$elemMatch {:userId userId :roleId {$in generalIds}}}}
                    {$or
                     [{:handlers {$elemMatch {:userId userId :roleId {$nin generalIds}}}}
                      {:auth {$elemMatch {:id userId :role "statementGiver"}}}]})))
              (when-not (empty? tags)
                {:tags {$in tags}})
              (when-not (empty? companyTags)
                {:company-notes {$elemMatch {:companyId (get-in user [:company :id]) :tags {$in companyTags}}}})
              (when-not (empty? organizations)
                {:organization (domain/organization-optimize-query organizations)})
              (when (event-search event)
                (case (first (:eventType event))
                  "warranty-period-end"                {$and [{:warrantyEnd {"$gte" (or (:start event) 0)
                                                                             "$lt"  (or (:end event) max-date)}}]}
                  "license-period-start"               {$and [{:documents.data.tyoaika-alkaa-ms.value {"$gte" (or (:start event) 0)
                                                                                                       "$lt"  (or (:end event) max-date)}}]}
                  "license-period-end"                 {$and [{:documents.data.tyoaika-paattyy-ms.value {"$gte" (or (:start event) 0)
                                                                                                         "$lt"  (or (:end event) max-date)}}]}
                  "license-started-not-ready"          {$and [{:documents.data.tyoaika-alkaa-ms.value {"$lt" (now)}},
                                                              {:state {$ne "closed"}}]}
                  "license-ended-not-ready"            {$and [{:documents.data.tyoaika-paattyy-ms.value {"$lt" (now)}},
                                                              {:state {$ne "closed"}}]}
                  "announced-to-ready-state-not-ready" {$and [{:closed {"$lt" (now)}},
                                                              {:state {$ne "closed"}},
                                                              {:permitType "YA"}]}))
              (cond
                (seq operations) {:primaryOperation.name {$in operations}}
                (and (usr/authority? user) (not= applicationType "unlimited"))
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
   :auth :handlers.firstName :handlers.lastName :handlers.userId :handlers.roleId
   :authorityNotice :comments :created :documents
   :foreman :foremanRole :infoRequest :location :modified :municipality
   :neighbors :permitType :permitSubtype :primaryOperation :state :statements
   :organization ; required for authorization checks
   :submitted :tasks :urgency :verdicts :archived :pate-verdicts :verdictDate])

(def- indicator-fields
  (map :field meta-fields/indicator-meta-fields))

(def- frontend-fields
  [:id :address :applicant :creator :handlers :authorityNotice
   :infoRequest :kind :location :modified :municipality :organization
   :primaryOperation :state :submitted :urgency
   :foreman :foremanRole :permitType :verdictDate :kuntalupatunnus])

(defn- select-fields [application]
  (select-keys
    application
    (concat frontend-fields indicator-fields)))


(defn- enrich-row [app]
  (-> app
      (assoc :handlers (distinct (:handlers app)) ;; Each handler only once.
             :kuntalupatunnus (vif/published-kuntalupatunnus app))
      app-utils/with-application-kind
      app-utils/location->object))

(def- sort-field-mapping {"applicant"   :applicant
                          "handler"     [:handlers.0.lastName :handlers.0.firstName]
                          "location"    :address
                          "modified"    :modified
                          "submitted"   :submitted
                          "foreman"     :foreman
                          "foremanRole" :foremanRole
                          "state"       :state
                          "verdictDate" :verdictDate
                          "id"          :_id})

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
        query (make-query user-query params user)
        user-total  (future (if (mongo/select-one :applications user-query) 1 0))
        skip        (or (util/->long (:skip params)) 0)
        limit       (or (util/->long (:limit params)) 10)
        apps        (search query db-fields (make-sort params) skip limit)
        rows        (->> apps
                         (filter (partial auth/application-allowed-for-company-user? user))
                         (map
                           (comp
                             select-fields
                             enrich-row
                             (partial meta-fields/with-indicators user)
                             #(domain/filter-application-content-for % user)
                             mongo/with-id)))]
    {:applications rows
     ; This is only used in the front-end to find out if the user has any applications at all. Full mongo count
     ; is expensive, so we rather just check if the user has at least one application.
     :userTotalCount @user-total}))

(defn query-total-count [user params]
  (let [user-query  (domain/basic-application-query-for user)
        query       (make-query user-query params user)]
    {:totalCount (mongo/count :applications query)}))

(defn map-markers-for-user [user {:keys [searchText] :as params}]
  (when (> (count searchText) (env/value :search-text-max-length))
    (fail! :error.search-text-is-too-long))
  (let [user-query (domain/basic-application-query-for user)
        query      (make-query user-query params user)]
    (for  [app   (mongo/select :applications query [:auth :location :permitType :state :infoRequest])
           :when (auth/application-allowed-for-company-user? user app)]
      (dissoc app :auth))))

(defn map-marker-infos [user application-ids]
  (let [user-query (domain/basic-application-query-for user)
        query      (assoc user-query :_id {$in application-ids})]
    (for  [app   (mongo/select :applications query [:auth :permitType :state :infoRequest
                                                    :address :municipality :primaryOperation
                                                    :documents :submitted :verdictDate])
           :let  [{:keys [_selected yritys henkilo]} (->> (:documents app)
                                                          domain/get-applicant-document
                                                          :data
                                                          lupapalvelu.document.tools/unwrapped)]
           :when (auth/application-allowed-for-company-user? user app)]
      (-> app
          (dissoc :primaryOperation :documents :auth)
          (assoc :operation (-> app :primaryOperation :name)
                 :applicant (cond
                              (:infoRequest app)
                              (let [{:keys [firstName lastName]} (-> app :auth first)]
                                (ss/join-non-blanks " " [firstName lastName]))

                              (= _selected "yritys") (:yritysnimi yritys)

                              :else
                              (ss/join-non-blanks " " [(some-> henkilo :henkilotiedot :etunimi)
                                                       (some-> henkilo :henkilotiedot :sukunimi)])))
          ss/trimwalk
          util/strip-blanks))))

;;
;; Public API
;;

(defn public-fields [{:keys [municipality submitted] :as application}]
  {:municipality municipality
   :timestamp submitted
   :operation (app-utils/operation-description application :fi)
   :operationName (i18n/supported-langs-map (partial app-utils/operation-description application))})
