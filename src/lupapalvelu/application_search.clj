(ns lupapalvelu.application-search
  (:require [taoensso.timbre :as timbre :refer [debug info warn error]]
            [clojure.string :as s]
            [clojure.set :refer [rename-keys]]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.property :as p]
            [sade.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :refer [applicant?] :as user]
            [lupapalvelu.states :as states]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.geojson :as geo]))

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

(defn- prefix-key [prefix key]
  (keyword (str prefix (name key))))

(defn- make-free-text-query [filter-search & {:keys [prefix] :or {prefix ""} }]
  (let [prefix        (when-not (ss/blank? prefix)
                        (str prefix "."))
        search-keys   [:address :verdicts.kuntalupatunnus :_applicantIndex :foreman]

        prefixed-keys (map (partial prefix-key prefix) search-keys)
        or-query      {$or (map #(hash-map % {$regex filter-search $options "i"}) prefixed-keys)}
        ops           (operation-names filter-search)]
    (if (seq ops)
      (update-in or-query [$or] concat [{(prefix-key prefix :primaryOperation.name) {$in ops}}
                                        {(prefix-key prefix :secondaryOperations.name) {$in ops}}])
      or-query)))

(defn make-text-query [filter-search & {:keys [prefix] :or {prefix ""} }]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches p/property-id-pattern filter-search) {(prefix-key prefix :propertyId) (p/to-property-id filter-search)}
    :else (make-free-text-query filter-search :prefix prefix)))

(defn- make-area-query [areas user]
  {:pre [(sequential? areas)]}
  (let [orgs (user/organization-ids-by-roles user #{:authority})
        orgs-with-areas (mongo/select :organizations {:_id {$in orgs} :areas.features.id {$in areas}} [:areas])
        features (flatten (map (comp :features :areas) orgs-with-areas))
        selected-areas (set areas)
        filtered-features (filter (comp selected-areas :id) features)
        coordinates (apply concat (map geo/resolve-polygons filtered-features))]
    (when (seq coordinates)
      {$or (map (fn [c] {:location {$geoWithin {"$polygon" c}}}) coordinates)})))

(def applicant-application-states
  {:state {$in ["open" "submitted" "sent" "complement-needed" "draft"]}})

(def authority-application-states
  {:state {$in ["submitted" "sent" "complement-needed"]}})

(defn make-query [query {:keys [searchText kind applicationType handlers tags organizations operations areas]} user]
  {$and
   (filter seq
     [query
      (when-not (ss/blank? searchText) (make-text-query (ss/trim searchText)))
      (if (applicant? user)
        (case applicationType
          "inforequest"        {:state {$in ["answered" "info"]}}
          "application"        applicant-application-states
          "construction"       {:state {$in ["verdictGiven" "constructionStarted"]}}
          "canceled"           {:state "canceled"}
          "verdict"            {:state {$in states/post-verdict-states}}
          "foremanApplication" (assoc applicant-application-states :permitSubtype "tyonjohtaja-hakemus")
          "foremanNotice"      (assoc applicant-application-states :permitSubtype "tyonjohtaja-ilmoitus")
          {:state {$ne "canceled"}})
        (case applicationType
          "inforequest"        {:state {$in ["open" "answered" "info"]}}
          "application"        authority-application-states
          "construction"       {:state {$in ["verdictGiven" "constructionStarted"]}}
          "verdict"            {:state {$in states/post-verdict-states}}
          "canceled"           {:state "canceled"}
          "foremanApplication" (assoc authority-application-states :permitSubtype "tyonjohtaja-hakemus")
          "foremanNotice"      (assoc authority-application-states :permitSubtype "tyonjohtaja-ilmoitus")
          {:state {$nin ["draft" "canceled"]}}))
      (when-not (empty? handlers)
        (if ((set handlers) "no-authority")
          {$or [{:authority.id  {$exists false}}, {:authority.id {$in [nil ""]}}]}
          {$or [{:auth.id {$in handlers}}
                {:authority.id {$in handlers}}]}))
      (when-not (empty? tags)
        {:tags {$in tags}})
      (when-not (empty? organizations)
        {:organization {$in organizations}})
      (when-not (empty? operations)
        {:primaryOperation.name {$in operations}})
      (when-not (empty? areas)
        (make-area-query areas user))])})

;;
;; Fields
;;

(def- db-fields ; projection
  [:_comments-seen-by :_statements-seen-by :_verdicts-seen-by
   :_attachment_indicator_reset :address :applicant :attachments
   :auth :authority :authorityNotice :comments :created :documents
   :foreman :foremanRole :infoRequest :modified :municipality
   :neighbors :permitSubtype :primaryOperation :state :statements
   :submitted :tasks :urgency :verdicts])

(def- indicator-fields
  (map :field meta-fields/indicator-meta-fields))

(def- frontend-fields
  [:id :address :applicant :authority :authorityNotice
   :infoRequest :kind :modified :municipality
   :primaryOperation :state :submitted :urgency :verdicts
   :foreman :foremanRole])

(defn- select-fields [application]
  (select-keys
    application
    (concat frontend-fields indicator-fields)))


(defn- enrich-row [{:keys [permitSubtype infoRequest] :as app}]
  (assoc app :kind (cond
                     (not (ss/blank? permitSubtype)) (str "permitSubtype." permitSubtype)
                     infoRequest "applications.inforequest"
                     :else       "applications.application")))

(def- sort-field-mapping {"applicant" :applicant
                          "handler" ["authority.lastName" "authority.firstName"]
                          "location" :address
                          "modified" :modified
                          "submitted" :submitted
                          "foreman" :foreman
                          "foremanRole" :foremanRole
                          "state" :state
                          "id" :_id})

(defn- dir [asc] (if asc 1 -1))

(defn- make-sort [{{:keys [field asc]} :sort}]
  (let [sort-field (sort-field-mapping field)]
    (cond
      (= "type" field) (array-map :permitSubtype (dir asc) :infoRequest (dir (not asc)))
      (nil? sort-field) {}
      (sequential? sort-field) (apply array-map (interleave sort-field (repeat (dir asc))))
      :else (array-map sort-field (dir asc)))))

(defn applications-for-user [user {application-type :applicationType :as params}]
  (let [user-query  (domain/basic-application-query-for user)
        user-total  (mongo/count :applications user-query)
        query       (make-query user-query params user)
        query-total (mongo/count :applications query)
        skip        (or (:skip params) 0)
        limit       (or (:limit params) 10)
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



