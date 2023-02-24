(ns lupapalvelu.campaign
  "Campaign code schemas and handling."
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.date :as date]
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

(defn- command->campaign
  "Creates Campaign value from command data."
  [{{:keys [code starts ends] :as data} :data}]
  (merge (select-keys data (keys Campaign))
         {:id     (code->id code)
          :starts (date/timestamp starts)
          :ends   (date/timestamp (date/end-of-day ends))}))

(defn- campaign->front
  "Front-friendly campaign object. Timestamps as dates, id as code."
  [{:keys [id starts ends] :as campaign}]
  (when campaign
    (merge {:code id
            :starts (date/iso-date starts :local)
            :ends (date/iso-date ends :local)}
           (select-keys campaign
                        [:account5 :account15
                         :account30 :lastDiscountDate]))))


(defn good-campaign
  "Pre-checker that fails if the command does not contain valid
  and consistent campaign data."
  [command]
  (try
    (let [{:keys [starts ends account5 account15 account30 lastDiscountDate]
           :as   campaign} (command->campaign command)
          end-date         (date/zoned-date-time ends)
          last-date        (date/zoned-date-time lastDiscountDate)]
      (cond
        (not (valid-campaign? campaign))          (fail :error.invalid-campaign)
        (> starts ends)                           (fail :error.campaign-period)
        (.isAfter end-date last-date)             (fail :error.campaign-last-date)
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

(defn contract-info
  "Provide campaign map for populating the company registration
  contract. See docx namespace for the client code."
  [{code :campaign account :accountType}]
  (let [campaign     (active-campaign code)
        _            (assert campaign (format "Campaign %s is not active." code))
        last-date    (-> campaign :lastDiscountDate date/zoned-date-time)
        regular-date (.plusDays last-date 1)]
    {:price        (->  account keyword campaign)
     :lastDiscount (date/finnish-date last-date)
     :firstRegular (date/finnish-date regular-date)}))
