(ns lupapalvelu.ya-extension
  "YA extension application related. Preferred namespace alias yax."
  (:require [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as su]
            [sade.xml :as xml]))

;; Dates in mongo are stored in the Finnish date format.
#_(def mongo-date-formatter (fmt/formatter "dd.MM.yyyy"))

#_(defn- parse-date [formatter s]
  (try
    (fmt/parse formatter s)
    (catch Exception _)))

(defn- extension-link-permits [application]
  (filter #(su/=as-kw (:operation %) :ya-jatkoaika)
          (or (:appsLinkingToUs application)
              (:appsLinkingToUs (meta-fields/enrich-with-link-permit-data
                                application)))))

(defn ya-extension-app? [application]
  (su/=as-kw (-> application :primaryOperation :name) :ya-jatkoaika))

(defn has-extension-info-or-link-permits
  "Prechecker that fails if the application does not have any
  extension applications or internal extension info."
  [{application :application}]
  (when (and (not (some-> application :jatkoaika :alkuPvm ss/not-blank?))
             (empty? (extension-link-permits application)))
    (fail :error.no-extensions)))

(defn- details [app-id]
  (let [app (domain/get-application-no-access-checking app-id)
        doc (domain/get-document-by-name app  :tyo-aika-for-jatkoaika)]
    {:id app-id
     :startDate (-> doc :data :tyoaika-alkaa-pvm :value)
     :endDate (-> doc :data :tyoaika-paattyy-pvm :value)
     :state (:state app)}))

(defn- augment-details
  "If application extension (jatkoaika property) matches any link
  permit, then the extension reason is added to the permit
  detail. Otherwise, a non-link-permit detail with reason is appended
  to the link-details."
  ([{:keys [alkuPvm loppuPvm perustelu]} link-details]
   (augment-details (date/zoned-date-time alkuPvm)
                    (date/zoned-date-time loppuPvm)
                    perustelu
                    link-details))
  ([start-date end-date reason link-details]
   (let [{:keys [startDate endDate] :as item} (first link-details)]
     (cond
       ;; Link permit that matches application extension time
       (and (= start-date (date/zoned-date-time startDate))
            (= end-date   (date/zoned-date-time endDate)))
       (cons (assoc item :reason reason) (rest link-details))
       ;; No match found. Add new reason detail
       (empty? link-details)
       [{:startDate (date/finnish-date start-date :zero-pad)
         :endDate   (date/finnish-date end-date :zero-pad)
         :reason    reason}]
       ;; Keep looking
       :else
       (cons item (augment-details start-date end-date reason (rest link-details)))))))


(defn extensions-details
  "Details for all YA extension link permits."
  [{app-extension :jatkoaika :as application}]
  (let [link-details (map (comp details :id)
                          (extension-link-permits application))]
    (if (-> app-extension :alkuPvm ss/not-blank?)
      (augment-details app-extension link-details)
      link-details)))

(defn- parse-application-id
  [xml]
  (some #(when (ss/=trim-i (xml/get-text % [:sovellus]) "Lupapiste")
           (ss/upper-case (xml/get-text % [:tunnus])))
        (xml/select xml [:luvanTunnisteTiedot :LupaTunnus :MuuTunnus])))

(defn- format-xml-date
  "XML date after given path in the Finnish date format (or nil)."
  [xml path]
  (date/finnish-date (xml/get-text xml path) :zero-pad))

(defn update-application-extensions
  "Parses KRYSP message and updates corresponding application's
  extension dates. Given XML should no longer contain namespaces."
  [xml]
  (let [start-date (format-xml-date xml [:lisaaikatieto :Lisaaika :alkuPvm])
        end-date   (format-xml-date xml [:lisaaikatieto :Lisaaika :loppuPvm])
        reason     (xml/get-text xml [:lisaaikatieto :Lisaaika :perustelu])]
    (mongo/update-by-id :applications
                        (parse-application-id xml)
                        {$set {:jatkoaika {:alkuPvm   start-date
                                           :loppuPvm  end-date
                                           :perustelu reason}}})))
