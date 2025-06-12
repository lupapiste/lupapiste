(ns lupapalvelu.application-search
  (:require [clojure.set :as set]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.logging :refer [errorf] :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.entry :as stats-e]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [slingshot.slingshot :refer [try+]]))

(set! *warn-on-reflection* true)

;;
;; Query construction
;;

(def max-date 253402214400000)

(def mongo-max-query-time
  "One minute since that is also the browser/nginx side timeout."
  (* 60 1000))

(defn parse-municipality-codes
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


(defn get-general-handlers-for-authority-organizations
  "Fetches the first (and only) general handler id for each of the given organizations.
  Used for filtering the results by general and secondary role below."
  [authority]
  (let [organizations (usr/organization-ids authority)]
    (->> (mongo/select :organizations {:_id {$in organizations}} [:handler-roles])
         (map organization/general-handler-id-for-organization)
         (keep identity)
         vec)))

(defn make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^[Ll][PpXx]-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches p/property-id-pattern filter-search) {:propertyId (p/to-property-id filter-search)}
    (re-matches v/rakennustunnus-pattern filter-search) {:buildings.nationalId filter-search}
    :else (make-free-text-query filter-search)))

(def authority-application-states
  #{"submitted" "complementNeeded" "sent"})

(def applicant-application-states
  (conj authority-application-states "draft" "open"))

(def ^:private applicant-application-states-query
  {:state {$in applicant-application-states}})

(def ^:private authority-application-states-query
  {:state {$in authority-application-states}})

(def no-handler "no-authority")

(defn handler-email-to-id [handler]
  (if (ss/includes? handler "@")
    (:id (usr/get-user-by-email handler))
    handler))

(def archival-base-without-ya
  { :state               {$in ["verdictGiven" "constructionStarted" "appealed"
                               "inUse" "foremanVerdictGiven" "acknowledged" "ready" "extinct"]}
   :archived.application nil
   :permitType           {$ne "YA"}})

(def archival-base-query-rest
  {:state              {$in states/archival-final-states}
   :archived.completed nil
   :permitType         {$ne permit/ARK}})

(def archival-base-query
  {$or [archival-base-without-ya
        archival-base-query-rest]})

(defn archival-org-datas
  "For each organization in `org-ids` returns map with `:id` and (optional) `:since-ts`
  keys. If the organization does not have archive enabled, it is omitted. Nil, if no good
  organizations."
  [org-ids]
  (some->> (mongo/select :organizations {:_id (if (= 1 (count org-ids))
                                                (first org-ids)
                                                {$in org-ids})
                                         :permanent-archive-enabled true}
                         [:permanent-archive-in-use-since])
           (mapv #(set/rename-keys % {:permanent-archive-in-use-since :since-ts}))
           not-empty
           ;; Helps testing
           (sort-by :id)))

(defn- archival-org-conditions [org-ids]
  (some->> (archival-org-datas org-ids)
           (mapv (fn [{org-id :id since-ts :since-ts}]
                   (let [m     {:organization org-id :permitType {$ne permit/ARK}}
                         since (some->> since-ts (hash-map $gte))]
                     (cond-> m
                       since (assoc $or [{:submitted since}
                                         {:submitted nil :created since}])))))
           not-empty))

(defn- archival-query [user params]
  (let [user-orgs      (usr/organization-ids-by-roles user #{:archivist})
        params-orgs    (some->> params :organizations seq set)
        orgs           (cond-> user-orgs
                         params-orgs (set/intersection params-orgs))
        org-conditions (archival-org-conditions orgs)]

    (case (count org-conditions)
      0 archival-base-query
      1 {$and [archival-base-query (first org-conditions)]}
      {$and [archival-base-query {$or org-conditions}]})))

(defn event-search [event]
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
                          event includeConverted includePaperApplications
                          foremanDocs foremanRoles]
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
              (when foremanDocs
                {:documents {$elemMatch (merge {:tj-document true
                                                :state       (if (= applicationType "all")
                                                               {$in foreman/foreman-doc-states}
                                                               applicationType)}
                                               (when (seq foremanRoles)
                                                 {"data.kuntaRoolikoodi.value" {$in foremanRoles}}))}})
              (if (usr/applicant? user)
                (case applicationType
                  "unlimited"          {}
                  "inforequest"        {:state {$in ["answered" "info"]}}
                  "application"        applicant-application-states-query
                  "construction"       {:state {$in construction-states}}
                  "finalized"          {:state {$in finalized-states}}
                  "canceled"           {:state "canceled"}
                  "verdict"            {:state {$in verdict-states}}
                  "foremanApplication" (assoc applicant-application-states-query
                                              :permitSubtype "tyonjohtaja-hakemus")
                  "foremanNotice"      (assoc applicant-application-states-query
                                              :permitSubtype "tyonjohtaja-ilmoitus")
                  "state-filter"       (state-filter-states params)
                  "processing"         stats-db/processing-query
                  "demolition-notice"  (assoc applicant-application-states-query
                                              :primaryOperation.name "purkamisilmoitus")
                  {:state {$ne "canceled"}})
                (case applicationType
                  "unlimited"          {}
                  "inforequest"        {:state {$in ["answered" "info" "open" ]} :permitType {$ne permit/ARK}}
                  "application"        authority-application-states-query
                  "construction"       {:state {$in construction-states}}
                  "verdict"            {:state {$in verdict-states}}
                  "finalized"          {:state {$in finalized-states}}
                  "canceled"           {:state "canceled"}
                  "foremanApplication" (assoc authority-application-states-query
                                              :permitSubtype "tyonjohtaja-hakemus")
                  "foremanNotice"      (assoc authority-application-states-query
                                              :permitSubtype "tyonjohtaja-ilmoitus")
                  "readyForArchival"   (archival-query user params)
                  "archivingProjects"  {:permitType permit/ARK :state {$nin [:archived :canceled]}}
                  "state-filter"       (state-filter-states params)
                                        ; {:state {$nin ["draft" "canceled"}}, permitType {$ne permit/ARK}}
                  "processing"         stats-db/processing-query
                  "demolition-notice"  (assoc authority-application-states-query
                                              :primaryOperation.name "purkamisilmoitus")
                  {$and [{:state      {$ne "canceled"}
                          :permitType {$ne permit/ARK}}
                                        ; TODO this cover like 1% of use cases, where the authority is itself
                                        ; also applicant, and has draft applications
                                        ; come up with another way to shown "own but drafts" and return the simpler query without $and+$or
                         {$or [{:state {$ne "draft"}}
                               (domain/auth-id-query user)]}]}))
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
                {:primaryOperation.name {$nin (cond-> ["tyonjohtajan-nimeaminen-v2"]
                                                ;; nil or absent includePaperApplications is interpreted as true
                                                (false? includePaperApplications) (conj "aiemmalla-luvalla-hakeminen"))}})
              (when-not (empty? areas)
                (app-utils/make-area-query areas user))
              (when (and (usr/authority? user) (not includeConverted))
                {:facta-imported {$ne true}})])}))

;;
;; Fields
;;

(def- db-fields ; projection
  [:_attachment_indicator_reset :_comments-seen-by :_statements-seen-by
   :_verdicts-seen-by :address :applicant :archived :attachments
   :auth :authorityNotice :comments :created :creator
   :documents :foreman :foremanRole :handlers.firstName
   :handlers.lastName :handlers.roleId :handlers.userId :history :infoRequest :location :modified
   :municipality :neighbors :organization :pate-verdicts :permitSubtype :permitType
   :primaryOperation
   :state :statements :submitted :tasks :urgency :verdictDate :verdicts
   stats-db/processing-projection])

(def- indicator-fields
  (map :field meta-fields/search-result-indicator-meta-fields))

(def- frontend-fields
  [:id :application-id :address :applicant :creator :handlers :authorityNotice
   :infoRequest :kind :location :modified :municipality :organization
   :primaryOperation :state :submitted :urgency :documents
   :foreman :foremanRole :permitType :verdictDate :kuntalupatunnus
   :processing :type :tyotehtavat])

(defn select-fields [application]
  (select-keys
    application
    (concat frontend-fields indicator-fields)))

(defn- filter-foreman-docs [foremanDocs applicationType application]
  (if foremanDocs
    (update application :documents (fn [docs]
                                     (filterv #(and (:tj-document %)
                                                    (or (and (= applicationType "all")
                                                             (contains? foreman/foreman-doc-states (:state %)))
                                                        (= (:state %) applicationType)))
                                              docs)))
    application))


(defn enrich-row [app]
  (-> app
      (assoc :handlers (into [] (distinct (:handlers app))) ;; Each handler only once.
             :kuntalupatunnus (vif/published-kuntalupatunnus app))
      app-utils/with-application-kind
      app-utils/location->object))

(def- sort-field-mapping {"applicant"      :applicant
                          "handler"        [:handlers.0.lastName :handlers.0.firstName]
                          "location"       :address
                          "modified"       :modified
                          "submitted"      :submitted
                          "foreman"        :foreman
                          "foremanRole"    :foremanRole
                          "state"          :state
                          "verdictDate"    :verdictDate
                          "proc-remaining" (util/kw-path stats-db/processing-projection :remaining)
                          "proc-months"    (util/kw-path stats-db/processing-projection :months)
                          "proc-status"    (util/kw-path stats-db/processing-projection :status)
                          "proc-request"   (util/kw-path stats-db/processing-projection :requests)
                          "id"             :_id})

(defn dir [asc] (if asc 1 -1))

(defn make-sort [{{:keys [field asc]} :sort foremanDocs :foremanDocs}]
  (let [sort-field (sort-field-mapping field)]
    (apply array-map
           (cond-> (cond
                     (true? foremanDocs) [:documents.0.created (dir false)]
                     (= "type" field) [:permitSubtype (dir asc) :infoRequest (dir (not asc))]
                     (nil? sort-field) []
                     (sequential? sort-field) (vec (interleave sort-field (repeat (dir asc))))
                     :else [sort-field (dir asc)])
             ;; For consistency, set :modified timestamp as a secondary sort field. Newer applications
             ;; first (desc) by default, and older first (asc) if the main sort is reversed.
             (not= field "modified") (conj :modified (dir (not asc)))))))

(defn search [query db-fields sort skip limit xf]
  (try+
    (mongo/select-options :applications query {:projection      db-fields
                                               :order-by        sort
                                               :skip            skip
                                               :max-time        mongo-max-query-time
                                               :limit           limit
                                               :xf              xf
                                               :read-secondary? true})
    (catch [:type :timeout] {:keys [deadline start end]}
      (errorf "Application search query=%s, sort=%s failed: timeout, start: %s, deadline: %s, end: %s"
              query sort start deadline end)
      (fail! :error.unknown))
    (catch com.mongodb.MongoException e
      (errorf "Application search query=%s, sort=%s failed: %s" query sort e)
      (fail! :error.unknown))))

(defn- store-xf-ts [*tss]
  (fn [val] (swap! *tss conj (System/nanoTime)) val))

(defn- calculate-xf-time [*tss]
  (-> (->> @*tss
           (partition 2)
           (map (fn [[start end]] (- end start)))
           (apply +))
      (/ 1000000)
      long))


(defn processing-search? [{:keys [foremanDocs applicationType]}]
  (and (= applicationType "processing") (not foremanDocs)))

(defn with-processing [params application]
  (cond-> application
    (processing-search? params)
    (util/assoc-when :processing (stats-db/read-processing application))))

(defn with-processing-unseen [user applications]
  (let [unseen-ids (stats-e/applications-with-unseen-entries user (mapv :id applications))]
    (cond->> applications
      unseen-ids (mapv (fn [{app-id :id proc :processing :as application}]
                         (cond-> application
                           (and proc (contains? unseen-ids app-id))
                           (assoc-in [:processing :unseen?] true)))))))

(defn applications-for-user [user {:keys [foremanDocs applicationType foremanRoles] :as params}]
  (let [start-ts        (now)
        user-query      (domain/basic-application-query-for user)
        query           (make-query user-query params user)
        user-total      (future (if (mongo/select-one :applications user-query) 1 0))
        skip            (or (util/->long (:skip params)) 0)
        limit           (or (util/->long (:limit params)) 10)
        *xf-tss         (atom [])
        xf              (comp
                          (map (store-xf-ts *xf-tss))
                          (filter (partial auth/application-allowed-for-company-user? user))
                          (map
                            (comp
                              select-fields
                              enrich-row
                              (partial with-processing params)
                              (partial meta-fields/with-search-result-indicators user)
                              #(domain/filter-application-content-for % user)
                              mongo/with-id
                              (partial filter-foreman-docs foremanDocs applicationType)))
                          (map (store-xf-ts *xf-tss)))
        search-start-ts (now)
        rows            (cond-> (search query db-fields (make-sort params) skip limit xf)
                          (true? foremanDocs)
                          (->> (mapcat (fn [{:keys [documents] :as application}]
                                         (cond->> documents
                                           (seq foremanRoles) (filter #((set foremanRoles) (get-in % [:data :kuntaRoolikoodi :value])))
                                           true (map #(merge application
                                                             (meta-fields/foreman-index %)
                                                             {:state     (:state %)
                                                              :submitted (:created %)
                                                              :kind      (if (= (get % :type) "application")
                                                                           "permitSubtype.tyonjohtaja-hakemus"
                                                                           "permitSubtype.tyonjohtaja-ilmoitus")
                                                              :documents [%]})))))
                               (sort-by :submitted)
                               (into []))
                          (processing-search? params)
                          (->> (with-processing-unseen user)))
        xf-time         (calculate-xf-time *xf-tss)
        end-ts          (now)]
    (log/debugf "applications-for-user from mongo. Query generation: %sms, Mongo: %sms, Data processing: %sms, Total: %sms, Row count %s"
                (- search-start-ts start-ts) (- end-ts search-start-ts xf-time) xf-time (- end-ts start-ts) (count rows))
    {:applications   rows
     ;; This is only used in the front-end to find out if the user has any applications at
     ;; all. Full mongo count is expensive, so we rather just check if the user has at
     ;; least one application.
     :userTotalCount @user-total}))

(defn query-total-count [user params]
  (let [user-query (domain/basic-application-query-for user)
        query      (make-query user-query params user)]
    {:totalCount (mongo/count-options :applications query {:max-time        mongo-max-query-time
                                                           :read-secondary? true})}))

(defn map-markers-for-user [user params]
  (let [user-query (domain/basic-application-query-for user)
        query      (make-query user-query params user)
        xf         (comp
                     (filter (partial auth/application-allowed-for-company-user? user))
                     (map #(dissoc % :auth))
                     (map app-utils/location->object))]
    (mongo/select-options :applications
                          query
                          {:projection      [:auth :location :permitType :state :infoRequest]
                           :xf              xf
                           :order-by        (make-sort params)
                           :max-time        mongo-max-query-time
                           :read-secondary? true})))

(defn map-marker-infos [user application-ids]
  (let [user-query (domain/basic-application-query-for user)
        query      (assoc user-query :_id {$in application-ids})]
    (for  [app   (mongo/select-options :applications
                                       query
                                       {:projection      [:auth :permitType :state :infoRequest
                                                          :address :municipality :primaryOperation
                                                          :documents :submitted :verdictDate]
                                        :max-time        mongo-max-query-time
                                        :read-secondary? true})
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
