(ns lupapalvelu.application-search-elastic
  (:require [clojure.set :as set]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.applications :as es-applications]
            [lupapalvelu.elasticsearch.query :as es-query]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.logging :as log]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]))


(defn- make-free-text-query
  "Builds a free-text Elasticsearch query for applications, matching the given filter string
   against :text-search field and application's operation names. If operation names are found
   in the filter, the query matches either the text or the operation names."
  [filter-search]
  (let [make-full-text-search (fn [fields phrase]
                                (es-query/full-text-search fields phrase {:fuzziness 0
                                                                          :operator  :and}))
        ops                   (operations/operation-names filter-search)
        {:keys [codes words]} (es-applications/parse-address filter-search)
        address-clauses       (keep identity
                                    [(when (not-empty words) (make-full-text-search [:address] words))
                                     (when (not-empty codes) (make-full-text-search [:address-codes] codes))])]
    (es-query/es-or
      (cond-> [(make-full-text-search [:text-search] filter-search)
               (make-full-text-search [:kuntalupatunnus] filter-search)]
              (seq address-clauses) (conj (es-query/must address-clauses))
              (seq ops) (into [(es-query/term :primaryOperation.name ops)
                               (es-query/term :secondaryOperations.name ops)])))))

(defn make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^[Ll][PpXx]-\d{3}-\d{4}-\d{5}$" filter-search) (es-query/es-or [(es-query/term :id (ss/upper-case filter-search))
                                                                                  ;; Foreman documents store applications id in different field
                                                                                  (es-query/term :application-id (ss/upper-case filter-search))])
    (re-matches p/property-id-pattern filter-search) (es-query/term :propertyId (p/to-property-id filter-search))
    (re-matches v/rakennustunnus-pattern filter-search) (es-query/term :buildings.nationalId filter-search)
    :else (make-free-text-query filter-search)))

(def archival-base-without-ya
  (es-query/must [(es-query/term :state ["verdictGiven" "constructionStarted" "appealed"
                                         "inUse" "foremanVerdictGiven" "acknowledged" "ready" "extinct"])
                  (es-query/must-not [{:exists {:field :archived.application}}])
                  (es-query/must-not [(es-query/term :permitType "YA")])]))

(def archival-base-query-rest
  (es-query/must [(es-query/term :state states/archival-final-states)
                  (es-query/must-not [{:exists {:field :archived.completed}}])
                  (es-query/must-not [(es-query/term :permitType permit/ARK)])]))

(def archival-base-query
  (es-query/es-or [archival-base-without-ya
                   archival-base-query-rest]))

(defn- archival-org-conditions [org-ids]
  (some->> (search/archival-org-datas org-ids)
           (mapv (fn [{org-id :id since-ts :since-ts}]
                   (let [xs    [(es-query/term :organization org-id)
                                (es-query/must-not [(es-query/term :permitType permit/ARK)])]
                         since (some->> since-ts (hash-map :gte))]
                     (es-query/must
                       (cond-> xs
                         since (conj (es-query/es-or
                                       [{:range {:submitted since}}
                                        (es-query/must
                                          [(es-query/must-not [{:exists {:field :submitted}}])
                                           {:range {:created since}}])])))))))
           not-empty))

(defn- archival-query [user params]
  (let [user-orgs      (usr/organization-ids-by-roles user #{:archivist})
        params-orgs    (some->> params :organizations seq set)
        orgs           (cond-> user-orgs
                         params-orgs (set/intersection params-orgs))
        org-conditions (archival-org-conditions orgs)]
    (case (count org-conditions)
      0 archival-base-query
      1 (es-query/must [archival-base-query (first org-conditions)])
      (es-query/must [archival-base-query (es-query/es-or org-conditions)]))))

(defn make-query [query
                  {:keys [searchText applicationType handlers
                          userIsGeneralHandler tags companyTags
                          organizations operations areas modifiedAfter
                          event includeConverted includePaperApplications
                          foremanRoles]
                   :as   params}
                  user]
  (let [searchText                           (ss/trim searchText)
        {:keys [parsed-term municipalities]} (search/parse-municipality-codes searchText)
        term                                 (or parsed-term searchText)
        construction-states                  ["constructionStarted" "inUse" "onHold"]
        verdict-states                       ["verdictGiven" "appealed"
                                              "foremanVerdictGiven" "acknowledged"]
        finalized-states                     ["closed" "ready" "finished" "extinct"
                                              "agreementPrepared" "agreementSigned"]]
    (es-query/must
      (filterv seq
               [query
                (when-not (ss/blank? term) (make-text-query term))
                (when (seq municipalities) (es-query/term :municipality municipalities))
                (when foremanRoles (es-query/term :foremanRole foremanRoles))
                (when-let [modified-after (util/->long modifiedAfter)]
                  {:range {:modified {:gt modified-after}}})
                (if (usr/applicant? user)
                  (case applicationType
                    "unlimited"          {}
                    "foreman-all"        (es-query/term :state foreman/foreman-doc-states)
                    "inforequest"        (es-query/term :state ["answered" "info"])
                    "application"        (es-query/term :state search/applicant-application-states)
                    "construction"       (es-query/term :state construction-states)
                    "finalized"          (es-query/term :state finalized-states)
                    "canceled"           (es-query/term :state "canceled")
                    "declared"           (es-query/term :state "declared")
                    "submitted"          (es-query/term :state "submitted")
                    "acknowledged"       (es-query/term :state "acknowledged")
                    "approved"           (es-query/term :state "approved")
                    "rejected"           (es-query/term :state "rejected")
                    "verdict"            (es-query/term :state verdict-states)
                    "foremanApplication" (es-query/must [(es-query/term :state search/applicant-application-states)
                                                         (es-query/term :permitSubtype "tyonjohtaja-hakemus")])
                    "foremanNotice"      (es-query/must [(es-query/term :state search/applicant-application-states)
                                                         (es-query/term :permitSubtype "tyonjohtaja-ilmoitus")])
                    "state-filter"       (if (seq (:states params))
                                           (es-query/term :state (:states params))
                                           {})
                    "processing"         stats-db/processing-query-es
                    "demolition-notice"  (es-query/must [(es-query/term :state search/applicant-application-states)
                                                         (es-query/term :primaryOperation.name "purkamisilmoitus")])
                    (es-query/must-not [(es-query/term :state "canceled")]))
                  (case applicationType
                    "unlimited"          {}
                    "foreman-all"        (es-query/term :state foreman/foreman-doc-states)
                    "inforequest"        (es-query/must [(es-query/term :state ["answered" "info" "open"])
                                                         (es-query/must-not [(es-query/term :permitType permit/ARK)])])
                    "application"        (es-query/term :state search/authority-application-states)
                    "construction"       (es-query/term :state construction-states)
                    "verdict"            (es-query/term :state verdict-states)
                    "finalized"          (es-query/term :state finalized-states)
                    "canceled"           (es-query/term :state "canceled")
                    "declared"           (es-query/term :state "declared")
                    "submitted"          (es-query/term :state "submitted")
                    "acknowledged"       (es-query/term :state "acknowledged")
                    "approved"           (es-query/term :state "approved")
                    "rejected"           (es-query/term :state "rejected")
                    "foremanApplication" (es-query/must [(es-query/term :state search/authority-application-states)
                                                         (es-query/term :permitSubtype "tyonjohtaja-hakemus")])
                    "foremanNotice"      (es-query/must [(es-query/term :state search/authority-application-states)
                                                         (es-query/term :permitSubtype "tyonjohtaja-ilmoitus")])
                    "readyForArchival"   (archival-query user params)
                    "archivingProjects"  (es-query/must [(es-query/term :permitType permit/ARK)
                                                         (es-query/must-not [(es-query/term :state ["archived" "canceled"])])])
                    "state-filter"       (if (seq (:states params))
                                           (es-query/term :state (:states params))
                                           {})
                    "processing"         stats-db/processing-query-es
                    "demolition-notice"  (es-query/must [(es-query/term :state search/authority-application-states)
                                                         (es-query/term :primaryOperation.name "purkamisilmoitus")])
                    (es-query/must [(es-query/must-not [(es-query/term :state "canceled")
                                                        (es-query/term :permitType permit/ARK)])
                                    (es-query/es-or [(es-query/must-not [(es-query/term :state "draft")])
                                                     (domain/auth-id-es-query user)])])))
                (when-not (empty? handlers)
                  (if ((set handlers) search/no-handler)
                    {:bool {:must_not {:nested
                                       {:path  :handlers
                                        :query {:exists {:field :handlers.userId}}}}}}
                    (when-let [handler-ids (seq (remove nil? (map search/handler-email-to-id handlers)))]
                      (es-query/nested-term :handlers :userId handler-ids))))
                (when (some? userIsGeneralHandler)
                  (let [userId     (:id user)
                        generalIds (search/get-general-handlers-for-authority-organizations user)]
                    (if userIsGeneralHandler
                      ;; Joissa lupakäsittelijänä
                      (es-query/nested-term :handlers :userId userId :roleId generalIds)
                      ;; Muussa roolissa
                      (es-query/es-or [{:nested
                                        {:path  :handlers
                                         :query {:bool
                                                 {:must     [(es-query/term :handlers.userId userId)]
                                                  :must_not [(es-query/term :handlers.roleId generalIds)]}}}}
                                       (es-query/nested-term :auth :id userId :role "statementGiver")]))))
                (when-not (empty? tags)
                  (es-query/term :tags tags))
                (when-not (empty? companyTags)
                  (es-query/nested-term :company-notes
                                        :companyId (get-in user [:company :id])
                                        :tags companyTags))
                (when-not (empty? organizations)
                  (es-query/term :organization organizations))
                (when (search/event-search event)
                  (case (first (:eventType event))
                    "warranty-period-end"                {:range {:warrantyEnd {:gte (or (:start event) 0)
                                                                                :lt  (or (:end event) search/max-date)}}}
                    "license-period-start"               {:range {:documents.data.tyoaika-alkaa-ms.value {:gte (or (:start event) 0)
                                                                                                          :lt  (or (:end event) search/max-date)}}}
                    "license-period-end"                 {:range {:documents.data.tyoaika-paattyy-ms.value {:gte (or (:start event) 0)
                                                                                                            :lt  (or (:end event) search/max-date)}}}
                    "license-started-not-ready"          (es-query/must [{:range {:documents.data.tyoaika-alkaa-ms.value {:lt (now)}}}
                                                                         (es-query/must-not [(es-query/term :state "closed")])])
                    "license-ended-not-ready"            (es-query/must [{:range {:documents.data.tyoaika-paattyy-ms.value {:lt (now)}}}
                                                                         (es-query/must-not [(es-query/term :state "closed")])])
                    "announced-to-ready-state-not-ready" (es-query/must [{:range {:closed {:lt (now)}}},
                                                                         (es-query/must-not [(es-query/term :state "closed")])
                                                                         (es-query/term :permitType "YA")])))
                (cond
                  (seq operations) (es-query/term :primaryOperation.name operations)
                  (and (usr/authority? user) (not= applicationType "unlimited"))
                  (es-query/must-not [(es-query/term :primaryOperation.name
                                                     (cond-> ["tyonjohtajan-nimeaminen-v2"]
                                                       ;; nil or absent includePaperApplications is interpreted as true
                                                       (false? includePaperApplications) (conj "aiemmalla-luvalla-hakeminen")))]))
                (when-not (empty? areas)
                  (app-utils/make-area-es-query areas user))
                (when (and (usr/authority? user) (not includeConverted))
                  (es-query/must-not [(es-query/term :facta-imported true)]))]))))

(def- sort-field-mapping {"applicant"       [:applicant]
                          "handler"         [:handler-sortfield]
                          "location"        [:address.keyword]
                          "modified"        [:modified]
                          "submitted"       [:submitted]
                          "state"           [:state]
                          "verdictDate"     [:verdictDate]
                          "proc-remaining"  [(util/kw-path stats-db/processing-projection :remaining)]
                          "proc-complexity" [(util/kw-path stats-db/processing-projection :complexity)]
                          "proc-status"     [(util/kw-path stats-db/processing-projection :status)]
                          "proc-requests"   [(util/kw-path stats-db/processing-projection :requests)]
                          "id"              [:id]})

(defn dir [asc] {:order   (if asc "asc" "desc")
                 :missing (if asc "_first" "_last")})

(defn make-sort [{{:keys [field asc]} :sort}]
  (let [sort-field (sort-field-mapping field)]
    (cond-> (cond
              (= "type" field) [{:permitSubtype (dir asc)}
                                {:infoRequest (dir (not asc))}]
              (nil? sort-field) []
              :else (mapv #(array-map % (dir asc)) sort-field))
      ;; For consistency, set :modified timestamp as a secondary sort field. Newer applications
      ;; first (desc) by default, and older first (asc) if the main sort is reversed.
      (not= field "modified") (conj {:modified (dir (not asc))}))))

(def projection
  [:_attachment_indicator_reset :_comments-seen-by :_statements-seen-by :_verdicts-seen-by
   :address :applicant :archived :attachments :auth :authorityNotice :comments :created
   :creator :handlers :id :indicator-data :infoRequest :kind :kuntalupatunnus
   :modified :municipality :organization :pate-verdicts :permitSubtype :permitType
   :primaryOperation :state :statements :submitted :urgency :verdictDate :verdicts
   :doc-version :indexed-ts :location stats-db/processing-projection :foreman :foremanRole])

(def foreman-projection
  [:address :auth :created :type
   :creator :handlers :history :application-id :id :kuntalupatunnus
   :modified :municipality :organization :state :submitted
   :foreman :foremanRole :tyotehtavat
   :doc-version :indexed-ts stats-db/processing-projection])

(defn make-xf [user params]
  (comp
    (map :_source)
    (map es-applications/restore-dynamic-key-maps)
    (map #(domain/filter-application-content-for % user))
    (map (partial meta-fields/with-search-result-indicators user))
    (map (partial search/with-processing params))
    (map search/select-fields)))

(defn applications-for-user [{:keys [user] :as command} params]
  (let [start-ts            (now)
        elastic             (components/command->elastic command)
        user-query          (domain/basic-application-es-query-for user)
        query               (make-query user-query params user)
        user-total          (future (if (es-api/any-matches? elastic
                                                             :elastic.index/applications
                                                             user-query)
                                      1
                                      0))
        skip                (or (util/->long (:skip params)) 0)
        limit               (or (util/->long (:limit params)) 10)
        es-start-ts         (now)
        search-body         {:track_total_hits true
                             :query            query
                             :_source          projection
                             :from             skip
                             :size             limit
                             :sort             (make-sort params)}
        result              (es-api/search-raw elastic
                                               :elastic.index/applications
                                               search-body)
        total-count         (-> result :hits :total :value)
        prosessing-start-ts (now)
        rows                (cond->> (->> result :hits :hits (into [] (make-xf user params)))
                              (search/processing-search? params)
                              (search/with-processing-unseen user))
        end-ts              (now)]
    (log/debugf "applications-for-user from elastic. Query generation: %sms, Elasticsearch: %sms (%sms), Data processing: %sms, Total: %sms, Row count %s"
                (- es-start-ts start-ts)
                (- prosessing-start-ts es-start-ts)
                (-> result :took)
                (- end-ts prosessing-start-ts)
                (- end-ts start-ts)
                (count rows))
    {:applications   rows
     :totalCount     total-count
     ;; This is only used in the front-end to find out if the user has any applications at all.
     :userTotalCount @user-total}))

(defn foreman-documents-for-user [{:keys [user] :as command} params]
  (let [start-ts            (now)
        elastic             (components/command->elastic command)
        user-query          (domain/basic-application-es-query-for user)
        query               (make-query user-query params user)
        skip                (or (util/->long (:skip params)) 0)
        limit               (or (util/->long (:limit params)) 10)
        es-start-ts         (now)
        search-body         {:track_total_hits true
                             :query            query
                             :_source          foreman-projection
                             :from             skip
                             :size             limit
                             :sort             (make-sort params)}
        result              (es-api/search-raw elastic
                                               :elastic.index/foremen
                                               search-body)
        total-count         (-> result :hits :total :value)
        prosessing-start-ts (now)
        rows                (->> result :hits :hits (into [] (make-xf user params)))
        end-ts              (now)]
    (log/debugf "foreman-documents-for-user from elastic. Query generation: %sms, Elasticsearch: %sms (%sms), Data processing: %sms, Total: %sms, Row count %s"
                (- es-start-ts start-ts)
                (- prosessing-start-ts es-start-ts)
                (-> result :took)
                (- end-ts prosessing-start-ts)
                (- end-ts start-ts)
                (count rows))
    {:applications rows
     :totalCount   total-count}))

(defn map-markers-for-user [{:keys [user] :as command} params]
  (let [elastic    (components/command->elastic command)
        user-query (domain/basic-application-es-query-for user)
        query      (make-query user-query params user)]
    (->> (es-api/search-raw-all elastic :elastic.index/applications
                                {:query   query
                                 :sort    (make-sort params)
                                 :_source [:id :location :permitType
                                           :state :infoRequest]})
         :hits
         :hits
         (mapv :_source))))
