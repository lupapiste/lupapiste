(ns lupapalvelu.appeal
  (:require [lupapalvelu.appeal-schema :as appeal-schema]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn next-rectification-id
  "Returns the next id for a rectification appeal (oikaisuvaatimus) on the given application.
  These are needed for rectification verdicts and are based on a running counter on the application"
  [{application-id :id}]
  (->> (format "rectification-verdict-%s" application-id)
       mongo/get-next-sequence-value
       (format "%s-OIK-%s" application-id)))

(defn rectification-label
  "Returns a string with the id, person making the appeal and time of appeal for this rectification appeal"
  [{:keys [rectification-id appellant datestamp]}]
  (format "%s, %s %s"
          rectification-id
          (or appellant "-")
          (date/finnish-date datestamp :zero-pad)))

(defn create-appeal [target-verdict-id type appellant datestamp text]
  (util/strip-nils
    {:id             (mongo/create-id)
     :target-verdict target-verdict-id
     :type           (some-> type name)
     :appellant      appellant
     :datestamp      datestamp
     :text           text}))

(defn create-bulletin-appeal
  "Create appeal that was submitted through the bulletin."
  [application target-verdict-id type {:keys [firstName lastName street zip city]} datestamp text email]
  (-> (create-appeal target-verdict-id type (str firstName " " lastName) datestamp text)
      (merge (util/strip-nils {:appellant-street street
                               :appellant-zip    zip
                               :appellant-city   city
                               :appellant-email  email
                               :from-bulletin?   true})
             (when (util/=as-kw type :rectification)
               {:rectification-id (next-rectification-id application)}))
      ss/trimwalk))

(defn appeal-data-for-upsert
  "'Dispatcher' function for appeal data.
   If appealId is given as last parameter, returns validated update data without id.
   If appealId is not given, returns validated appeal data with generated id"
  [target-verdict-id type appellant datestamp text & [appealId]]
  (if appealId
    (let [update-data (dissoc (create-appeal target-verdict-id type appellant datestamp text) :id)]
      (when-not (appeal-schema/appeal-checker (assoc update-data :id appealId))
        (dissoc update-data :target-verdict)))
    (let [new-data (create-appeal target-verdict-id type appellant datestamp text)]
      (when-not (appeal-schema/appeal-checker new-data)
        new-data))))
