(ns lupapalvelu.application-search
  (:require [taoensso.timbre :as timbre :refer [debug info warn error]]
            [clojure.string :as s]
            [clojure.set :refer [rename-keys]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.core :refer :all]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :as user]
            [lupapalvelu.states :as states]
            [lupapalvelu.geojson :as geo]
            [lupapalvelu.organization :as organization]))

;; Operations

(defn- normalize-operation-name [i18n-text]
  (when-let [lc (ss/lower-case i18n-text)]
    (-> lc
      (s/replace #"\p{Punct}" "")
      (s/replace #"\s{2,}"    " "))))

(def operation-index
  (reduce
    (fn [ops k]
      (let [localizations (map #(i18n/localize % "operations" (name k)) ["fi" "sv"])
            normalized (map normalize-operation-name localizations)]
        (conj ops {:op (name k) :locs (remove ss/blank? normalized)})))
    []
    (keys operations/operations)))

(defn- operation-names [filter-search]
  (let [normalized (normalize-operation-name filter-search)]
    (map :op
      (filter
        (fn [{locs :locs}] (some (fn [i18n-text] (ss/contains? i18n-text normalized)) locs))
        operation-index))))

;;
;; Query construction
;;

(defn- fuzzy-re
  "Takes search term and turns it into 'fuzzy' regular expression
  string (not pattern!) that matches any string that contains the
  substrings in the correct order. The search term is split both for
  regular whitespace and Unicode no-break space. The original string
  parts are escaped for (inadvertent) regex syntax.
  Sample matching: 'ear onk' will match 'year of the monkey' after fuzzying"
  [term]
  (let [whitespace "[\\s\u00a0]+"
        fuzzy      (->> (ss/split term (re-pattern whitespace))
                        (map #(java.util.regex.Pattern/quote %))
                        (ss/join (str ".*" whitespace ".*")))]
    (str "^.*" fuzzy ".*$")))


(defn- make-free-text-query [filter-search]
  (let [search-keys [:address :verdicts.kuntalupatunnus :_applicantIndex :foreman :_id]
        fuzzy       (fuzzy-re filter-search)
        or-query    {$or (map #(hash-map % {$regex fuzzy $options "i"}) search-keys)}
        ops         (operation-names filter-search)]
    (if (seq ops)
      (update-in or-query [$or] concat [{:primaryOperation.name {$in ops}}
                                        {:secondaryOperations.name {$in ops}}])
      or-query)))

(defn make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches p/property-id-pattern filter-search) {:propertyId (p/to-property-id filter-search)}
    (re-matches v/rakennustunnus-pattern filter-search) {:buildings.nationalId filter-search}
    :else (make-free-text-query filter-search)))

(defn- make-area-query [areas user]
  {:pre [(sequential? areas)]}
  (let [orgs (user/organization-ids-by-roles user #{:authority :commenter :reader})
        orgs-with-areas (mongo/select :organizations {:_id {$in orgs} :areas-wgs84.features.id {$in areas}} [:areas-wgs84])
        features (flatten (map (comp :features :areas-wgs84) orgs-with-areas))
        selected-areas (set areas)
        filtered-features (filter (comp selected-areas :id) features)]
    (when (seq filtered-features)
      {$or (map (fn [feature] {:location-wgs84 {$geoWithin {"$geometry" (:geometry feature)}}}) filtered-features)})))

(def applicant-application-states
  {:state {$in ["open" "submitted" "sent" "complementNeeded" "draft"]}})

(def authority-application-states
  {:state {$in ["submitted" "sent" "complementNeeded"]}})

(def no-handler "no-authority")

(defn- handler-email-to-id [handler]
  (if (ss/contains? handler "@")
    (:id (user/get-user-by-email handler))
    handler))

(defn- archival-query [user]
  (let [from-ts (->> (user/organization-ids-by-roles user #{:archivist})
                     (organization/earliest-archive-enabled-ts))
        base-query {$or [{$and [{:state {$in ["verdictGiven" "constructionStarted" "appealed"]}} {:archived.application nil}]}
                         {$and [{:state "closed"} {:archived.completed nil}]}]}]
    (if from-ts
      {$and [base-query
             {:created {$gte from-ts}}]}
      base-query)))

(defn make-query [query {:keys [searchText applicationType handlers tags organizations operations areas modifiedAfter]} user]
  {$and
   (filter seq
     [query
      (when-not (ss/blank? searchText) (make-text-query (ss/trim searchText)))
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
          "inforequest"        {:state {$in ["open" "answered" "info"]}}
          "application"        authority-application-states
          "construction"       {:state {$in ["verdictGiven" "constructionStarted"]}}
          "verdict"            {:state {$in states/post-verdict-states}}
          "canceled"           {:state "canceled"}
          "foremanApplication" (assoc authority-application-states :permitSubtype "tyonjohtaja-hakemus")
          "foremanNotice"      (assoc authority-application-states :permitSubtype "tyonjohtaja-ilmoitus")
          "readyForArchival"   (archival-query user)
          {:state {$nin ["draft" "canceled"]}}))
      (when-not (empty? handlers)
        (if ((set handlers) no-handler)
          {$or [{:authority.id  {$exists false}}, {:authority.id {$in [nil ""]}}]}
          (when-let [handler-ids (seq (remove nil? (map handler-email-to-id handlers)))]
            {$or [{:auth.id {$in handler-ids}}
                  {:authority.id {$in handler-ids}}]})))
      (when-not (empty? tags)
        {:tags {$in tags}})
      (when-not (empty? organizations)
        {:organization {$in organizations}})
      (if-not (empty? operations)
        {:primaryOperation.name {$in operations}}
        (when (and (user/authority? user) (not= applicationType "unlimited"))
          ; Hide foreman applications in default search, see LPK-923
          {:primaryOperation.name {$ne "tyonjohtajan-nimeaminen-v2"}}))
      (when-not (empty? areas)
        (make-area-query areas user))])})

;;
;; Fields
;;

(def- db-fields ; projection
  [:_comments-seen-by :_statements-seen-by :_verdicts-seen-by
   :_attachment_indicator_reset :address :applicant :attachments
   :auth :authority :authorityNotice :comments :created :documents
   :foreman :foremanRole :infoRequest :location :modified :municipality
   :neighbors :permitSubtype :primaryOperation :state :statements
   :submitted :tasks :urgency :verdicts])

(def- indicator-fields
  (map :field meta-fields/indicator-meta-fields))

(def- frontend-fields
  [:id :address :applicant :authority :authorityNotice
   :infoRequest :kind :location :modified :municipality
   :primaryOperation :state :submitted :urgency :verdicts
   :foreman :foremanRole])

(defn- select-fields [application]
  (select-keys
    application
    (concat frontend-fields indicator-fields)))


(defn- enrich-row [{:keys [permitSubtype infoRequest] :as app}]
  (-> app
      (assoc :kind (cond
                     (not (ss/blank? permitSubtype)) (str "permitSubtype." permitSubtype)
                     infoRequest "applications.inforequest"
                     :else       "applications.application"))
      app-utils/location->object))

(def- sort-field-mapping {"applicant" :applicant
                          "handler" ["authority.lastName" "authority.firstName"]
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
      (ss/blank? sort-field) {}
      (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
      :else (array-map sort-field (dir asc)))))

(defn applications-for-user [user params]
  (let [user-query  (domain/basic-application-query-for user)
        user-total  (mongo/count :applications user-query)
        query       (make-query user-query params user)
        query-total (mongo/count :applications query)
        skip        (or (util/->long (:skip params)) 0)
        limit       (or (util/->long (:limit params)) 10)
        apps        (mongo/with-collection "applications"
                      (query/find query)
                      (query/fields db-fields)
                      (query/sort (make-sort params))
                      (query/skip skip)
                      (query/limit limit))
        rows        (map
                      (comp
                        select-fields
                        enrich-row
                        (partial meta-fields/with-indicators user)
                        #(domain/filter-application-content-for % user)
                        mongo/with-id)
                      apps)]
    {:userTotalCount user-total
     :totalCount query-total
     :applications rows}))

;;
;; Public API
;;

(defn public-fields [{:keys [municipality submitted primaryOperation]}]
  (let [op-name (:name primaryOperation)]
    {:municipality municipality
     :timestamp submitted
     :operation (i18n/localize :fi "operations" op-name)
     :operationName {:fi (i18n/localize :fi "operations" op-name)
                     :sv (i18n/localize :sv "operations" op-name)}}))



