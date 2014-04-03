(ns lupapalvelu.application-search
  (:require [monger.operators :refer :all]
            [monger.query :as query]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :refer [applicant?]]
            [lupapalvelu.application-meta-fields :as meta-fields]))

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

(defn- add-field [application data [app-field data-field]]
  (assoc data data-field (app-field application)))

(defn- make-row [application]
  (let [base {"id" (:_id application)
              "kind" (if (:infoRequest application) "inforequest" "application")}]
    (reduce (partial add-field application) base col-map)))

(defn- make-free-text-query [filter-search]
  {$or [{:address {$regex filter-search $options "i"}}
        {:verdicts.kuntalupatunnus {$regex filter-search $options "i"}}]}) ; TODO (or operations, applicant)

(defn- make-text-query [filter-search]
  {:pre [filter-search]}
  (cond
    (re-matches #"^LP-\d{3}-\d{4}-\d{5}$" filter-search) {:_id filter-search}
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

