(ns lupapalvelu.campaign
  "Campaign code schemas and handling."
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf]]))

(defonce campaign-feature :company-campaign)

(sc/defschema Campaign
  {:id        sc/Str
   :starts    ssc/Timestamp
   :ends      ssc/Timestamp
   :message   sc/Str
   :account5  sc/Int
   :account15 sc/Int
   :account30 sc/Int
   :billing   sc/Str})

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
  [{{code :code :as data} :data}]
  (merge {:id (code->id code)}
         (select-keys data [:starts :ends
                            :message :account5
                            :account15 :account30
                            :billing])))

(defn good-campaign
  "Pre-checker that fails if the command does not contain valid
  and consistent campaign data."
  [command]
  (let [{:keys [starts ends account5
                account15 account30] :as campaign} (command->campaign command)]
    (cond
      (not (valid-campaign? campaign)) (fail :error.invalid-campaign)
      (>= starts ends) (fail :error.campaign-period)
      (not (<= account5 account15 account30)) (fail :error.campaign-pricing))))

(defn add-campaign [command]
  (try
   (mongo/insert :campaigns (command->campaign command))
   (catch com.mongodb.DuplicateKeyException e
     (errorf "Campaign code conflict: %s" (.getMessage e))
     (fail :error.campaign-conflict))))

(defn campaigns []
  (mongo/select :campaigns))

(defn active-campaign
  ([code]
   (active-campaign code (now)))
  ([code timestamp]
   (mongo/select-one :campaigns {:_id    (code->id code)
                                 :starts {$lt timestamp}
                                 :ends   {$gt timestamp}})))
