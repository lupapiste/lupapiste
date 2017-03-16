(ns lupapalvelu.campaign
  "Campaign code schemas and handling."
  (:require [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clj-time.local :as local]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf]]))

(sc/defschema Campaign
  {:id               (sc/constrained sc/Str #(re-matches #"[a-z0-9-]+" %))
   :starts           ssc/Timestamp  ;; At midnight
   :ends             ssc/Timestamp  ;; At 23:59:59
   :account5         sc/Int         ;; EUR/month
   :account15        sc/Int         ;; EUR/month
   :account30        sc/Int         ;; EUR/month
   :lastDiscountDate (ssc/date-string "yyyy-MM-dd")})

(defn code->id [code]
  (-> code ss/trim ss/lower-case))

(defn- valid-campaign?
  "Validates campaign against schema."
  [campaign]
  (if-let [result (sc/check Campaign campaign)]
    (errorf "Campaign validation: %s" result)
    true))

(defn- timestamp [local-time-string]
  (-> local-time-string local/to-local-date-time coerce/to-long))

(defn- tz-formatter [formatter]
  (fmt/with-zone
    formatter
    (time/default-time-zone)))

(defn- datestring
  "Local time presentation of the given UTC timestamp. If no formatter
  is given :date formatter is used."
  ([utc-timestamp formatter]
   (->> utc-timestamp
        coerce/from-long
        (fmt/unparse (tz-formatter formatter))))
  ([utc-timestamp]
   (datestring utc-timestamp (fmt/formatters :date))))

(defn- command->campaign
  "Creates Campaign value from command data."
  [{{:keys [code starts ends] :as data} :data}]
  (merge (select-keys data (keys Campaign))
         {:id (code->id code)
          :starts (timestamp starts)
          :ends (timestamp (format "%sT23:59:59" ends))}))

(defn- campaign->front
  "Front-friendly campaign object. Timestamps as dates, id as code."
  [{:keys [id starts ends] :as campaign}]
  (when campaign
    (merge {:code id
            :starts (datestring starts)
            :ends (datestring ends)}
           (select-keys campaign
                        [:account5 :account15
                         :account30 :lastDiscountDate]))))


(defn good-campaign
  "Pre-checker that fails if the command does not contain valid
  and consistent campaign data."
  [command]
  (try
    (let [{:keys [starts ends account5
                  account15 account30
                  lastDiscountDate] :as campaign} (command->campaign command)
          end-date (->> ends datestring (fmt/parse (fmt/formatters :date)))
          last-date (fmt/parse (fmt/formatters :date) lastDiscountDate)]
      (cond
        (not (valid-campaign? campaign))        (fail :error.invalid-campaign)
        (> starts ends)                         (fail :error.campaign-period)
        (time/after? end-date last-date)        (fail :error.campaign-last-date)
        (not (<= 0 account5 account15 account30)) (fail :error.campaign-pricing)))
    (catch Exception e
      (errorf "Bad campaign: %s" (.getMessage e))
      (fail :error.invalid-campaign))))

(defn add-campaign [command]
  (try
   (mongo/insert :campaigns (command->campaign command))
   (catch com.mongodb.DuplicateKeyException e
     (errorf "Campaign code conflict: %s" (.getMessage e))
     (fail :error.campaign-conflict))))

(defn delete-campaign [code]
  (mongo/remove :campaigns (code->id code)))

(defn campaigns []
  (map campaign->front
       (mongo/select :campaigns)))

(defn active-campaign
  ([code]
   (active-campaign code (now)))
  ([code timestamp]
   (campaign->front (mongo/select-one :campaigns {:_id    (code->id code)
                                                  :starts {$lt timestamp}
                                                  :ends   {$gt timestamp}}))))

(defn campaign-is-active
  "Pre-checker that fails if campaign parameter is given but not refer to
  an active campaign."
  [command]
  (when-let [id (-> command :data :company :campaign code->id)]
    (when-not (or (ss/blank? id) (active-campaign id))
      (fail :error.campaign-not-found))))
(def finnish-formatter (fmt/formatter "d.M.yyyy"))

(defn contract-info
  "Provide campaign map for populating the company registration
  contract. See docx namespace for the client code."
  [{code :campaign account :accountType}]
  (let [campaign     (active-campaign code)
        _            (assert campaign (format "Campaign %s is not active." code))
        last-date    (-> campaign :lastDiscountDate fmt/parse-local-date)
        regular-date (time/plus last-date (time/days 1))]
    {:price        (->  account keyword campaign)
     :lastDiscount (fmt/unparse-local finnish-formatter last-date)
     :firstRegular (fmt/unparse-local finnish-formatter regular-date)}))
