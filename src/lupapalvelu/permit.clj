(ns lupapalvelu.permit
  (:require [taoensso.timbre :as timbre :refer [errorf warn]]
            [sade.core :refer [fail]]
            [sade.util :as util]))

(defonce ^:private permit-type-defs (atom {}))
(defn permit-types [] @permit-type-defs)

(def poikkeamislupa :poikkeamislupa)
(def suunnittelutarveratkaisu :suunnittelutarveratkaisu)

(defn valid-permit-type? [permit-type]
  (contains? (permit-types) permit-type))

(defn register-function [permit-type k f]
  {:pre [(valid-permit-type? permit-type)
         (keyword? k)
         (fn? f)]}
  (swap! permit-type-defs assoc-in [permit-type k] f))

;;
;; Enum
;;

(defmacro defpermit [permit-name description m]
  `(do
     (def ~permit-name ~(str description) ~(str permit-name))
     (swap! permit-type-defs util/deep-merge {~permit-name ~m})))

(defpermit R  "Rakennusluvat"
  {:subtypes         []
   :sftp-directory   "/rakennus"
   :applicant-doc-schema "hakija-r"
   :multiple-parties-allowed true
   :wfs-krysp-ns-name "rakennusvalvonta"
   :wfs-krysp-url-asia-prefix "rakval:luvanTunnisteTiedot/"})

(defpermit YA "Yleisten alueiden luvat"
  {:subtypes             []
   :sftp-directory       "/yleiset_alueet"
   :applicant-doc-schema "hakija-ya"
   :multiple-parties-allowed false
   :wfs-krysp-ns-name "yleisenalueenkaytonlupahakemus"
   :wfs-krysp-url-asia-prefix "yak:luvanTunnisteTiedot/"})

(defpermit YI  "Ymparistoilmoitukset"
  {:subtypes       []
   :sftp-directory "/ymparisto"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true
   :wfs-krysp-ns-name "ymparisto/ilmoitukset"})

(defpermit YL  "Ymparistolupa"
  {:subtypes       []
   :sftp-directory "/ymparisto"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true
   :wfs-krysp-ns-name "ymparisto/ymparistoluvat"
   :wfs-krysp-url-asia-prefix "ymy:luvanTunnistetiedot/"})

(defpermit VVVL  "Vapautushakemus vesijohtoon ja viemariin liittymisesta"
  {:subtypes       []
   :sftp-directory "/ymparisto"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true
   :wfs-krysp-ns-name "ymparisto/vesihuoltolaki"
   :wfs-krysp-url-asia-prefix "ymv:luvanTunnistetiedot/"})

(defpermit P  "Poikkeusluvat"
  {:subtypes         [poikkeamislupa suunnittelutarveratkaisu]
   :sftp-directory   "/poikkeusasiat"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true
   :wfs-krysp-ns-name "poikkeamispaatos_ja_suunnittelutarveratkaisu"
   :wfs-krysp-url-asia-prefix "ppst:luvanTunnistetiedot/"})

(defpermit MAL "Maa-ainesluvat"
  {:subtypes       []
   :sftp-directory "/ymparisto"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true
   :wfs-krysp-ns-name "ymparisto/maa_ainesluvat"
   :wfs-krysp-url-asia-prefix "ymm:luvanTunnistetiedot/"})

(defpermit KT "Kiinteistotoimitus"
  {:subtypes       []
   :sftp-directory "/rakennus"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true})

(defpermit MM "Maankayton muutos"
  {:subtypes       []
   :sftp-directory "/kaavat"
   :applicant-doc-schema "hakija"
   :multiple-parties-allowed true})

;;
;; Helpers
;;
(defn get-metadata [permit-type k & [default]]
  (if permit-type
    (-> (permit-types) (get (name permit-type)) (get k default))
    default))

(defn permit-subtypes [permit-type]
  (get-metadata permit-type :subtypes []))

(defn get-sftp-directory [permit-type]
  (get-metadata permit-type :sftp-directory))

(defn get-applicant-doc-schema [permit-type]
  (get-metadata permit-type :applicant-doc-schema))

(defn get-application-mapper
  "Returns a function that maps application into KRYSP XML and saves the XML to disk."
  [permit-type]
  (get-metadata permit-type :app-krysp-mapper))

(defn get-review-mapper [permit-type]
  "Returns a function that maps reviews (katselmus) into KRYSP XML and saves the XML to disk."
  (get-metadata permit-type :review-krysp-mapper))

(defn get-verdict-reader [permit-type]
  "Returns a function that reads verdicts (sequence) from KRYSP xml.
   Function takes xml as parameter.
   Use get-application-xml-getter to fetch the XML."
  (get-metadata permit-type :verdict-krysp-reader))

(defn get-verdict-validator [permit-type]
  "Returns a function that validates verdicts from KRYSP xml.
   Function takes xml as parameter.
   Use get-application-xml-getter to fetch the XML."
  (get-metadata permit-type :verdict-krysp-validator))

(defn get-verdict-extras-reader [permit-type]
  "Returns a function that reads some extras from verdict KRYSP xml.
   Function takes xml as parameter and returns a map that should be merged into the application."
  (get-metadata permit-type :verdict-extras-krysp-reader))

(defn get-application-xml-getter [permit-type]
  "Returns a function that fetches KRYSP XML from municipality backend.
   Function parameters: 1) url,
                        2) id,
                        3) keyword parameter: search-type (e.g. :application-id or :kuntalupatunnus)
                        4) optional boolean parameter: raw form."
  (get-metadata permit-type :xml-from-krysp))

(defn multiple-parties-allowed? [permit-type]
  (get-metadata permit-type :multiple-parties-allowed))

(defn permit-type
  "gets the permit-type of application"
  [application]
  {:post [(not= % nil)]}
  (:permitType application))

;;
;; Validate
;;

(defn permit-type-validator [{{:keys [permitType]} :data}]
  (when-not (valid-permit-type? permitType)
    (warn "invalid permit type" permitType)
    (fail :error.missing-parameters :parameters [:permitType])))

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
