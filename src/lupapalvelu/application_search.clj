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
;; Table definition
;;

(def- col-sources [(fn [app] (select-keys app [:urgency :authorityNotice]))
                            :indicators
                            :attachmentsRequiringAction
                            :unseenComments
                            (fn [app] (if (:infoRequest app) "inforequest" "application"))
                            (juxt :address :municipality)
                            :primaryOperation
                            :applicant
                            :submitted
                            :modified
                            :state
                            :authority])

(def- order-by (assoc col-sources
                          0 nil
                          1 nil
                          2 nil
                          3 nil
                          4 :infoRequest
                          5 :address
                          6 nil
                          ; 7 applicant - sorted as is
                          ; 8 submitted - sorted as is
                          ; 9 modified - sorted as is
                          ; 10 state - sorted as is
                          11 ["authority.lastName" "authority.firstName"]
                          ))

(def- col-map (zipmap col-sources (map str (range))))

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

(defn- make-sort [params]
  (let [col (get order-by (:iSortCol_0 params))
        dir (if (= "asc" (:sSortDir_0 params)) 1 -1)]
    (cond
      (nil? col) {}
      (sequential? col) (zipmap col (repeat dir))
      :else {col dir})))

;;
;; Result presentation
;;

(defn- add-field [application data [app-field data-field]]
  (assoc data data-field (app-field application)))

(defn- make-row [application]
  (let [base {"id" (:_id application)
              "kind" (if (:infoRequest application) "inforequest" "application")}]
    (reduce (partial add-field application) base col-map)))

;;
;; Public API
;;

(defn applications-for-user [user params]
  (let [user-query  (domain/basic-application-query-for user)
        user-total  (mongo/count :applications user-query)
        query       (make-query user-query params user)
        query-total (mongo/count :applications query)
        skip        (or (:iDisplayStart params) 0)
        limit       (or (:iDisplayLength params) 10)
        apps        (query/with-collection "applications"
                      (query/find query)
                      (query/sort (make-sort params))
                      (query/skip skip)
                      (query/limit limit))
        rows        (map (comp make-row (partial meta-fields/with-indicators user) #(domain/filter-application-content-for % user) ) apps)
        echo        (str (util/->int (str (:sEcho params))))] ; Prevent XSS
    {:aaData                rows
     :iTotalRecords         user-total
     :iTotalDisplayRecords  query-total
     :sEcho                 echo}))

(defn- enrich-row [app]
  (assoc app :kind (if (:infoRequest app) "inforequest" "application")))

(def- sort-field-mapping {"applicant" :applicant
                          "handler" ["authority.lastName" "authority.firstName"]
                          "location" :address
                          "modified" :modified
                          "submitted" :submitted
                          "type" :infoRequest
                          "state" :state})

(defn- make-sort-v2 [{{:keys [field dir]} :sort}]
  (when-let [sort-field (sort-field-mapping field)]
    {sort-field (if (= "asc" dir) 1 -1)}))

(def kind-mapping {"all" "both"
                   "inforequest" "inforequests"
                   "canceled" "both"})

(defn applications-for-user-v2 [user {application-type :applicationType :as params}]
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
        skip        (or 0)
        limit       (or (:limit params) 10)
        apps        (query/with-collection "applications"
                      (query/find query)
                      (query/sort (make-sort-v2 params))
                      (query/skip skip)
                      (query/limit limit))
        rows        (map (comp enrich-row (partial meta-fields/with-indicators user) #(domain/filter-application-content-for % user) ) apps)]
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


