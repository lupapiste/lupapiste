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
            [lupapalvelu.user :refer [applicant?]]
            [lupapalvelu.application-meta-fields :as meta-fields]))

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

(defn- make-free-text-query [filter-search]
  (let [or-query {$or [{:address {$regex filter-search $options "i"}}
                       {:verdicts.kuntalupatunnus {$regex filter-search $options "i"}}
                       {:_applicantIndex {$regex filter-search $options "i"}}]}
        ops (operation-names filter-search)]
    (if (seq ops)
      (update-in or-query [$or] concat [{:primaryOperation.name {$in ops}} {:secondaryOperations.name {$in ops}}])
      or-query)))

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches p/property-id-pattern filter-search) {:propertyId (p/to-property-id filter-search)}
    :else (make-free-text-query filter-search)))

(defn make-query [query {:keys [filter-search filter-kind filter-state filter-user tags]} user]
  {$and
   (filter seq
     [query
      (when-not (ss/blank? filter-search) (make-text-query (ss/trim filter-search)))
      (merge
        (case filter-kind
          "applications" {:infoRequest false}
          "inforequests" {:infoRequest true}
          nil) ; defaults to both
        (let [all (if (applicant? user) {:state {$ne "canceled"}} {:state {$nin ["draft" "canceled"]}})]
          (case filter-state
           "application"       {:state {$in ["open" "submitted" "sent" "complement-needed" "info"]}}
           "construction"      {:state {$in ["verdictGiven" "constructionStarted"]}}
           "canceled"          {:state "canceled"}
           all))
        (when-not (contains? #{nil "0"} filter-user)
          {$or [{"auth.id" filter-user}
                {"authority.id" filter-user}]})
        (when-not (empty? tags)
          {:tags {$in tags}}))])})


;;
;; Public API
;;

(defn- enrich-row [app]
  (assoc app :kind (if (:infoRequest app) "inforequest" "application")))

(def- sort-field-mapping {"applicant" :applicant
                          "handler" ["authority.lastName" "authority.firstName"]
                          "location" :address
                          "modified" :modified
                          "submitted" :submitted
                          "type" :infoRequest
                          "state" :state})

(defn- make-sort [{{:keys [field asc]} :sort}]
  (let [sort-field (sort-field-mapping field)
        dir (if asc 1 -1)]
    (cond
      (nil? sort-field) {}
      (sequential? sort-field) (zipmap sort-field (repeat dir))
      :else {sort-field dir})))

(def kind-mapping {"all" "both"
                   "inforequest" "inforequests"
                   "canceled" "both"})

(defn applications-for-user [user {application-type :applicationType :as params}]
  (let [user-query  (domain/basic-application-query-for user)
        user-total  (mongo/count :applications user-query)
        kind        (get kind-mapping application-type "applications")
        params      (merge
                      {:filter-kind kind}
                      (rename-keys params {:searchText :filter-search
                                           :applicationType :filter-state
                                           :handler :filter-user
                                           :applicationTags :tags}))
        query       (make-query user-query params user)
        query-total (mongo/count :applications query)
        skip        (or (:skip params) 0)
        limit       (or (:limit params) 10)
        apps        (query/with-collection "applications"
                      (query/find query)
                      (query/sort (make-sort params))
                      (query/skip skip)
                      (query/limit limit))
        rows        (map
                      (comp
                        enrich-row
                        (partial meta-fields/with-indicators user)
                        #(domain/filter-application-content-for % user)
                        mongo/with-id)
                      apps)]
    {:userTotalCount user-total
     :totalCount query-total
     :applications rows}))

(defn public-fields [{:keys [municipality submitted primaryOperation]}]
  (let [op-name (:name primaryOperation)]
    {:municipality municipality
     :timestamp submitted
     :operation (i18n/localize :fi "operations" op-name)
     :operationName {:fi (i18n/localize :fi "operations" op-name)
                     :sv (i18n/localize :sv "operations" op-name)}}))


