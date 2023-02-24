(ns lupapalvelu.price-catalogues
  "A common interface for accessing price catalogues and related data"
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.set :refer [union intersection]]
            [lupapalvelu.invoices.schemas :as invsc :refer [Modified
                                                            PriceCatalogue
                                                            PriceCatalogueDraft]]
            [lupapalvelu.invoices.shared.schemas :refer [CatalogueRow]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [debug error]]))

(defn fetch-price-catalogue-by-id [price-catalogue-id]
  (mongo/by-id :price-catalogues price-catalogue-id))

(defn command->catalogue [{data :data}]
  (-> data :price-catalogue-id fetch-price-catalogue-by-id))

(defn published? [{:keys [state] :as price-catalogue}]
  (= state "published"))

(defn validate-price-catalogues [price-catalogues]
  (debug ">> validate-price-catalogues price-catalogues: " price-catalogues)
  (when (seq price-catalogues)
    (sc/validate [(sc/conditional
                    #(= (:state %) "draft") PriceCatalogueDraft
                    :else PriceCatalogue)] price-catalogues)))

(def time-format (tf/formatter "dd.MM.YYYY"))

(defn validate-price-catalogue [price-catalogue]
  (sc/validate invsc/PriceCatalogue price-catalogue))

(defn update-catalogue! [{:keys [id] :as catalogue}]
  (validate-price-catalogue catalogue)
  (mongo/update-by-id :price-catalogues id (dissoc catalogue :id))
  id)

(defn delete-catalogues! [catalogues]
  (doseq [{:keys [id]} catalogues]
    (when id
      (mongo/remove :price-catalogues id))))

(defn ->catalogue-type [org]
  (cond
    (org/has-permit-type? org :R)  "R"
    (org/has-permit-type? org :YA) "YA"
    :else nil))

(sc/defn ^:always-validate command->modified :- Modified
  [{:keys [user created]}]
  {:modified    created
   :modified-by (invsc/->invoice-user user)})

(defn save-no-billing-periods! [{:keys [data] :as command}]
  (let [{:keys [price-catalogue-id no-billing-periods]} data]
    (mongo/update-by-id :price-catalogues price-catalogue-id
                        {$set (util/assoc-when
                                {:no-billing-periods (ss/trimwalk no-billing-periods)}
                                :meta (when (= (:state (command->catalogue command)) "draft")
                                        (command->modified command)))})))

(defn- ->finnish-to-datetime [date-string]
  (some-> date-string
          date/timestamp ;; Finnish format is supported
          tc/from-long))

(defn no-billing-period->date-time-interval [no-billing-period]
  (let [start (->finnish-to-datetime (:start no-billing-period))
        end (t/plus (->finnish-to-datetime (:end no-billing-period)) (t/days 1))]
    (t/interval start end)))

(defn- interval->seq [date-time-interval]
  (let [count-indays (t/in-days date-time-interval)]
    (reduce (fn [memo interval]
              (let [date (t/plus (t/start date-time-interval) (t/days interval))]
                (conj memo date)))
            []
            (range 0 count-indays))))

(defn- calculate-work-time-interval [billable-work-start billable-work-end]
  (try
    (t/interval billable-work-start (t/plus billable-work-end (t/days 1)))
    (catch Exception ex
      (error (.getMessage ex)))))

(defn- get-overlapping-interval [work-time-interval no-billing-period]
  (try
    (t/overlap work-time-interval (no-billing-period->date-time-interval no-billing-period))
    (catch Exception ex
      (error (.getMessage ex)))))

(defn no-billing-period-days-in-billable-work-time
  "Returns #{dates}"
  [billable-work-start
   billable-work-end
   no-billing-periods]
  (let [billable-work-start-datetime (->finnish-to-datetime billable-work-start)
        billable-work-end-datetime (->finnish-to-datetime  billable-work-end)
        work-time-interval (calculate-work-time-interval billable-work-start-datetime billable-work-end-datetime)
        no-billing-period-values (vals no-billing-periods)]
    (if work-time-interval
      (into (sorted-set) (map (fn [datetime]
                                (date/finnish-date (tc/to-long datetime)))
                              (reduce (fn [overlapping-days no-billing-period]
                                        (let [overlapping-interval (get-overlapping-interval work-time-interval no-billing-period)]
                                          (if overlapping-interval
                                            (union overlapping-days (interval->seq overlapping-interval))
                                            overlapping-days)))
                                      [] no-billing-period-values)))
      #{})))

(defn- valid-catalog? [{:keys [valid-from valid-until]} ts]
  (let [from  (or valid-from 0)
        until (or valid-until (inc ts))]
    (<= from ts until)))

(defn- compare-catalogs [ts a b]
  (let [a? (valid-catalog? a ts)
        b? (valid-catalog? b ts)]
    (cond
      (= a? b?) (compare (-> b :meta :modified) (-> a :meta :modified))
      a?        -1
      :else     1)))

(defn application-price-catalogues
  "Price catalogues that can be used for the application invoices. Sorted first by validity,
  then by published."
  [{:keys [organization]} ts]
  (sort (partial compare-catalogs ts)
        (mongo/select :price-catalogues
                      {:organization-id organization
                       :state           :published})))

(defmulti process-draft-edit
  "Returns updated draft."
  (fn [_ edit]
    (first (intersection #{:valid :row :delete-row :move-row :name}
                         (-> edit keys set)))))

(defn parse-valid-period [{:keys [valid]}]
  {:valid-from  (some-> valid :from date/start-of-day date/timestamp)
   :valid-until (some-> valid :until date/end-of-day date/timestamp)})

(defmethod process-draft-edit :valid
  [draft data]
  (merge draft (parse-valid-period data)))

(defmethod process-draft-edit :row
  [draft {:keys [row]}]
  (update draft :rows (fn [rows]
                        (if-let [row-id (:id row)]
                          (map (fn [old-row]
                                 (cond-> old-row
                                   (= (:id old-row) row-id)
                                   (util/deep-merge row)))
                               rows)
                          (cons (assoc row :id (mongo/create-id)) rows)))))

(defmethod process-draft-edit :delete-row
  [draft {:keys [delete-row]}]
  (update draft :rows (partial remove #(= (:id %) delete-row))))

(defmethod process-draft-edit :name
  [draft {:keys [name]}]
  (assoc draft :name name))

(defn edit-price-catalogue-draft [{:keys [data] :as command}]
  (let [{:keys [edit]} data
        updated        (-> (command->catalogue command)
                           (process-draft-edit edit)
                           util/strip-nils
                           (assoc :meta (command->modified command))
                           ss/trimwalk)]
    (mongo/update-by-id :price-catalogues
                        (:id updated)
                        (dissoc (sc/validate PriceCatalogueDraft updated) :id))
    updated))

(defn move-row [{:keys [data] :as command}]
  (let [{:keys [price-catalogue-id direction
                row-id]} data
        {:keys [state]
         :as   catalog}  (update (command->catalogue command)
                                 :rows
                                 (fn [rows]
                                   (loop [[x & xs] rows
                                          passed   []]
                                     (cond
                                       (nil? x)
                                       rows ;; row-id not in rows

                                       (= (:id x) row-id)
                                       (if (= direction "up")
                                         (if (empty? passed)
                                           ;; [X a b c] -> [a b c X]
                                           (concat xs [x])
                                           ;; [a b X c] -> [a X b c]
                                           (concat (butlast passed) [x (last passed)] xs))
                                         ;; Direction is down
                                         (if (empty? xs)
                                           ;; [a b c X] -> [X a b c]
                                           (concat [x] passed)
                                           ;; [a X b c] -> [a b X c]
                                           (concat passed [(first xs) x] (rest xs))))

                                       :else
                                       (recur xs (conj passed x))))))
        catalog          (cond-> catalog
                           (= state "draft") (assoc :meta (command->modified command)))]
    (mongo/update-by-id :price-catalogues
                        price-catalogue-id
                        {$set (select-keys catalog [:rows :meta])})
    catalog))

(def row-defaults {:code              ""
                   :text              ""
                   :discount-percent  0
                   :operations        []
                   :product-constants {:kustannuspaikka  ""
                                       :alv              ""
                                       :laskentatunniste ""
                                       :muu-tunniste     ""}})

(defn promote-row
  "Returns a map that either contains `:row` with promoted row or `:error` with row-id."
  [{:keys [product-constants] :as row}]
  (let [row (cond-> (util/deep-merge row-defaults row)
              (every? ss/blank? (vals product-constants))
              (dissoc :product-constants))]
    (if (sc/check CatalogueRow row)
      {:error (:id row)}
      {:row row})))


(defn promote-price-catalogue-draft
  "Returns a 'publish candidate' does not update mongo. If fail!s, the error includes information of
  the invalid rows."
  [command]
  (let [draft (command->catalogue command)
        rows  (map promote-row (:rows draft))]
    (when-let [bad-row-ids (some->> (map :error rows)
                                    (remove nil?)
                                    seq)]
      (fail! :error.price-catalogue.bad-rows
             :row-ids bad-row-ids))
    (-> draft
        (assoc :state "published"
               :rows (map :row rows)
               :meta (command->modified command)))))
