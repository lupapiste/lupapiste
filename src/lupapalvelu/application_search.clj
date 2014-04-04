(ns lupapalvelu.application-search
  (:require [clojure.string :as s]
            [monger.operators :refer :all]
            [monger.query :as query]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.user :refer [applicant?]]
            [lupapalvelu.application-meta-fields :as meta-fields]))

;; Operations

(defn- normalize-operation-name [i18n-text]
  (when-let [lc (ss/lower-case i18n-text)]
    (s/replace lc #"\p{Punct}" "")))

(def operation-index
  (reduce
    (fn [ops [k _]]
      (let [localizations (map #(i18n/localize % "operations" (name k)) ["fi" "sv"])
            normalized (map normalize-operation-name localizations)]
        (conj ops {:op (name k) :locs (remove ss/blank? normalized)})))
    []
    operations/operations))

(defn- operation-names [filter-search]
  (let [normalized (normalize-operation-name filter-search)]
    (map :op
      (filter
        (fn [{locs :locs}] (some (fn [i18n-text] (.contains i18n-text normalized)) locs))
        operation-index))))

;;
;; Table definition
;;

(def ^:private col-sources [(fn [app] (if (:infoRequest app) "inforequest" "application"))
                            (juxt :address :municipality)
                            meta-fields/get-application-operation
                            :applicant
                            :submitted
                            :indicators
                            :unseenComments
                            :modified
                            :state
                            :authority])

(def ^:private order-by (assoc col-sources
                          0 :infoRequest
                          1 :address
                          2 nil
                          3 nil
                          5 nil
                          6 nil))

(def ^:private col-map (zipmap col-sources (map str (range))))

;;
;; Query construction
;;

(defn- make-free-text-query [filter-search]
  (let [or-query {$or [{:address {$regex filter-search $options "i"}}
                       {:verdicts.kuntalupatunnus {$regex filter-search $options "i"}}
                       ; TODO applicant
                       ]}
        ops (operation-names filter-search)]
    (if (seq ops)
      (update-in or-query [$or] conj {:operations.name {$in ops}})
      or-query)))

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^([Ll][Pp])-\d{3}-\d{4}-\d{5}$" filter-search) {:_id (ss/upper-case filter-search)}
    (re-matches util/property-id-pattern filter-search) {:propertyId (util/to-property-id filter-search)}
    :else (make-free-text-query filter-search)))

(defn- make-query [query {:keys [filter-search filter-kind filter-state filter-user]} user]
  (merge
    query
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
    (when-not (ss/blank? filter-search) (make-text-query (ss/trim filter-search)))))

(defn- make-sort [params]
  (let [col (get order-by (:iSortCol_0 params))
        dir (if (= "asc" (:sSortDir_0 params)) 1 -1)]
    (if col {col dir} {})))

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
        rows        (map (comp make-row (partial meta-fields/with-meta-fields user)) apps)
        echo        (str (util/->int (str (:sEcho params))))] ; Prevent XSS
    {:aaData                rows
     :iTotalRecords         user-total
     :iTotalDisplayRecords  query-total
     :sEcho                 echo}))

