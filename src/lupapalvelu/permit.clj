(ns lupapalvelu.permit
  (:require [lupapalvelu.domain :as domain]
            [sade.core :refer [fail]]
            [taoensso.timbre :as timbre :refer [errorf]]))

(defonce ^:private permit-type-defs (atom {}))
(defn permit-types [] @permit-type-defs)

(def poikkeamislupa :poikkeamislupa)
(def suunnittelutarveratkaisu :suunnittelutarveratkaisu)

(defn register-function [permit-type k f]
  {:pre [(contains? (permit-types) permit-type)
         (keyword? k)
         (fn? f)]}
  (swap! permit-type-defs assoc-in [permit-type k] f))

;;
;; Enum
;;

(defmacro defpermit [permit-name description m]
  `(do
     (def ~permit-name ~(str description) ~(str permit-name))
     (swap! permit-type-defs assoc ~permit-name ~m)))

(defpermit R  "Rakennusluvat"
  {:subtypes         []
   :sftp-directory   "/rakennus"})

(defpermit YA "Yleisten alueiden luvat"
  {:subtypes         []
   :sftp-directory   "/yleiset_alueet"})

(defpermit YI  "Ymparistoilmoitukset"
  {:subtypes       []
   :sftp-directory "/ymparisto"})

(defpermit YL  "Ymparistolupa"
  {:subtypes       []
   :sftp-directory "/ymparisto"})

(defpermit VVVL  "Vapautushakemus vesijohtoon ja viemariin liittymisesta"
  {:subtypes       []
   :sftp-directory "/ymparisto"})

(defpermit P  "Poikkeusluvat"
  {:subtypes         [poikkeamislupa suunnittelutarveratkaisu]
   :sftp-directory   "/poikkeusasiat"})

(defpermit MAL "Maa-ainesluvat"
  {:subtypes       []
   :sftp-directory "/ymparisto"})

(defpermit KM "Kiinteiston muodostus"
  {:subtypes       []
   :sftp-directory "/rakennus"})

;;
;; Helpers
;;
(defn- get-metadata [permit-type k & [default]]
  (if permit-type
    (-> (permit-types) (get (name permit-type)) (get k default))
    default))

(defn permit-subtypes [permit-type]
  (get-metadata permit-type :subtypes []))

(defn get-sftp-directory [permit-type]
  (get-metadata permit-type :sftp-directory))

(defn get-application-mapper
  "Returns a function that maps application into KRYSP XML and saves the XML to disk."
  [permit-type]
  (get-metadata permit-type :app-krysp-mapper))

(defn get-review-mapper [permit-type]
  "Returns a function that maps reviews (katselmus) into KRYSP XML and saves the XML to disk."
  (get-metadata permit-type :review-krysp-mapper))

(defn get-verdict-reader [permit-type]
  "Returns a function that reads verdics (sequence) from KRYSP xml.
   Function takes xml as parameter.
   Use ((get-application-xml-getter permit-type) url application-id) to fetch the XML."
  (get-metadata permit-type :verdict-krysp-reader))

(defn get-verdict-extras-reader [permit-type]
  "Returns a function that reads some extras from verdict KRYSP xml.
   Function takes xml as parameter and returns a map that should be merged into the application."
  (get-metadata permit-type :verdict-extras-krysp-reader))

(defn get-application-xml-getter [permit-type]
  "Returns a function that fetches KRYSP XML from municipality backend.
   Function parameters: 1) url,
                        2) id,
                        3) optional boolean parameter: raw form
                        4) optional boolean parameter: if true the id parameter is interpreted as kuntalupatunnus instead of application id."
  (get-metadata permit-type :xml-from-krysp))

(defn permit-type
  "gets the permit-type of application"
  [application]
  {:post [(not= % nil)]}
  (:permitType application))

;;
;; Validate
;;

(defn validate-permit-type-is-not [validator-permit-type]
  (fn [_ application]
    (if application
      (let [application-permit-type (permit-type application)]
        (when (= (keyword application-permit-type) (keyword validator-permit-type))
          (fail :error.invalid-permit-type :permit-type validator-permit-type)))
      (fail :error.invalid-application-parameter))))

(defn validate-permit-type-is [validator-permit-type]
  (fn [_ application]
    (if application
      (let [application-permit-type (permit-type application)]
        (when-not (= (keyword application-permit-type) (keyword validator-permit-type))
          (fail :error.invalid-permit-type :permit-type validator-permit-type)))
      (fail :error.invalid-application-parameter))))

(defn is-valid-subtype [permitSubtype {permitType :permitType}]
  (when-not (some #(= permitSubtype %) (permit-subtypes permitType))
    (fail :error.permit-has-no-such-subtype)))


(defn validate-permit-has-subtypes [_ {permitType :permitType}]
    (when (empty? (permit-subtypes permitType))
      (fail :error.permit-has-no-subtypes)))
