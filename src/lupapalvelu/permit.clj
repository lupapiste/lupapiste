(ns lupapalvelu.permit
  (:require [taoensso.timbre :as timbre :refer [error errorf warn]]
            [schema.core :as sc]
            [sade.core :refer [fail]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.states :as states]))

(defonce ^:private permit-type-defs (atom {}))
(defn permit-types [] @permit-type-defs)

(def PermitMetaData
  {:subtypes         [sc/Keyword]
   :sftp-directory   sc/Str
   :allowed-task-schemas #{sc/Str}
   :multiple-parties-allowed sc/Bool
   :extra-statement-selection-values sc/Bool
   :state-graph     {sc/Keyword [sc/Keyword]}
   (sc/optional-key :allow-state-change) (sc/cond-pre sc/Keyword [(sc/cond-pre sc/Keyword sc/Str)] )
   (sc/optional-key :wfs-krysp-ns-name) sc/Str
   (sc/optional-key :wfs-krysp-url-asia-prefix) sc/Str})

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
  `(if-let [res# (sc/check PermitMetaData ~m)]
     (let [invalid-meta# (merge-with (fn [val-in-result# val-in-latter#]
                                       (if-not (nil? val-in-latter#) val-in-latter# val-in-result#))
                                     res#
                                     (select-keys ~m (keys res#)))]

       (throw (AssertionError. (str "Permit '" ~description "' has invalid meta data: " invalid-meta#))))
     (do
       (def ~permit-name ~(str description) ~(str permit-name))
       (swap! permit-type-defs util/deep-merge {~permit-name ~m}))))

(defpermit R  "Rakennusluvat"
  {:subtypes         []
   :state-graph      states/full-application-state-graph
   :allow-state-change ["tyonjohtaja-hakemus" :empty]
   :sftp-directory   "/rakennus"
   :allowed-task-schemas #{"task-katselmus" "task-vaadittu-tyonjohtaja" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values true
   :wfs-krysp-ns-name "rakennusvalvonta"
   :wfs-krysp-url-asia-prefix "rakval:luvanTunnisteTiedot/"})

(defpermit YA "Yleisten alueiden luvat"
  {:subtypes             []
   :state-graph          states/default-application-state-graph
   :allow-state-change []
   :sftp-directory       "/yleiset_alueet"
   :allowed-task-schemas #{"task-katselmus-ya" "task-lupamaarays"}
   :multiple-parties-allowed false
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "yleisenalueenkaytonlupahakemus"
   :wfs-krysp-url-asia-prefix "yak:luvanTunnisteTiedot/"})

(defpermit YI  "Ymparistoilmoitukset"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "ymparisto/ilmoitukset"})

(defpermit YL  "Ymparistolupa"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "ymparisto/ymparistoluvat"
   :wfs-krysp-url-asia-prefix "ymy:luvanTunnistetiedot/"})

(defpermit YM  "Muut ymparistoluvat"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false})

(defpermit VVVL  "Vapautushakemus vesijohtoon ja viemariin liittymisesta"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "ymparisto/vesihuoltolaki"
   :wfs-krysp-url-asia-prefix "ymv:luvanTunnistetiedot/"})

(defpermit P  "Poikkeusluvat"
  {:subtypes         [poikkeamislupa suunnittelutarveratkaisu]
   :state-graph      states/full-application-state-graph
   :allow-state-change :all
   :sftp-directory   "/poikkeusasiat"
   :allowed-task-schemas #{"task-katselmus" "task-vaadittu-tyonjohtaja" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values true
   :wfs-krysp-ns-name "poikkeamispaatos_ja_suunnittelutarveratkaisu"
   :wfs-krysp-url-asia-prefix "ppst:luvanTunnistetiedot/"})

(defpermit MAL "Maa-ainesluvat"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "ymparisto/maa_ainesluvat"
   :wfs-krysp-url-asia-prefix "ymm:luvanTunnistetiedot/"})

(defpermit KT "Kiinteistotoimitus"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :sftp-directory "/kiinteistotoimitus"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "kiinteistotoimitus"})

(defpermit MM "Maankayton muutos"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :sftp-directory "/maankaytonmuutos"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :wfs-krysp-ns-name "maankaytonmuutos"})

;;
;; Helpers
;;
(defn get-metadata [permit-type k & [default]]
  (if permit-type
    (-> (permit-types) (get (name permit-type)) (get k default))
    default))

(defn permit-subtypes [permit-type]
  (get-metadata permit-type :subtypes []))

(defn get-state-graph [permit-type]
  (get-metadata permit-type :state-graph states/default-application-state-graph))

(defn get-sftp-directory [permit-type]
  (get-metadata permit-type :sftp-directory))

(defn get-application-mapper
  "Returns a function that maps application into KRYSP XML and saves the XML to disk."
  [permit-type]
  (get-metadata permit-type :app-krysp-mapper))

(defn get-review-mapper
  "Returns a function that maps reviews (katselmus) into KRYSP XML and saves the XML to disk."
  [permit-type]
  (get-metadata permit-type :review-krysp-mapper))

(defn get-verdict-reader
  "Returns a function that reads verdicts (sequence) from KRYSP xml.
   Function takes xml as parameter.
   Use fetch-xml-from-krysp to fetch the XML."
  [permit-type]
  (get-metadata permit-type :verdict-krysp-reader))

(defn get-verdict-validator
  "Returns a function that validates verdicts from KRYSP xml.
   Function takes xml and organization map as parameters.
   Use fetch-xml-from-krysp to fetch the XML."
  [permit-type]
  (get-metadata permit-type :verdict-krysp-validator))

(defn get-verdict-extras-reader
  "Returns a function that reads some extras from verdict KRYSP xml.
   Function takes xml as parameter and returns a map that should be merged into the application."
  [permit-type]
  (get-metadata permit-type :verdict-extras-krysp-reader))

(defn get-tj-suunnittelija-verdict-reader
  "Returns a function that reads tj/suunnittelija verdicts from KRYSP xml.
   Function takes xml, party type and party's kuntaRoolikoodi as parameter.
   Use fetch-xml-from-krysp to fetch the XML."
  [permit-type]
  (get-metadata permit-type :tj-suunnittelija-verdict-krysp-reader))

(defmulti fetch-xml-from-krysp
  "Fetches KRYSP XML from municipality backend."
  (fn [permit-type server-url credentials id search-type raw?]
    (keyword permit-type)))

(defmethod fetch-xml-from-krysp :default
  [permit-type & _]
  (error "No fetch method for permit type: " permit-type)
  nil)

(defn multiple-parties-allowed? [permit-type]
  (get-metadata permit-type :multiple-parties-allowed))

(defn permit-type
  "gets the permit-type of application"
  [application]
  {:post [(string? %)]}
  (:permitType application))

;;
;; Validate
;;

(defn permit-type-validator [{{:keys [permitType]} :data}]
  (when-not (valid-permit-type? permitType)
    (warn "invalid permit type" permitType)
    (fail :error.missing-parameters :parameters [:permitType])))

(defn validate-permit-type-is-not [& validator-permit-types]
  (let [invalid-permit-types (set (map name validator-permit-types))]
    (fn [{:keys [application]}]
      (if application
        (when (invalid-permit-types (permit-type application))
          (fail :error.invalid-permit-type :permit-type validator-permit-types))
        (fail :error.invalid-application-parameter)))))

(defn validate-permit-type-is [& validator-permit-types]
  (let [valid-permit-types (set (map name validator-permit-types))]
    (fn [{:keys [application]}]
      (if application
        (when-not (valid-permit-types (permit-type application))
          (fail :error.invalid-permit-type :permit-type validator-permit-types))
        (fail :error.invalid-application-parameter)))))

(defn valid-permit-types
  "Prechecker for permit types. permit-types is a map of of
  permit-types and supported subtypes. Some examples:

  {:R [] :P :all} -> R applications without subtypes and Ps with any
  subtype are valid.

  {:R [\"tyonjohtaja-hakemus\"]} -> Only Rs with tyonjohtaja-hakemus
  subtype are valid.

  {:R [\"tyonjohtaja-hakemus\" :empty]} -> Only Rs with tyonjohtaja-hakemus
  subtype or no subtype are valid."
  [permit-types {{:keys [permitType permitSubtype]} :application}]
  (let [app-type (keyword permitType)
        subs     (get permit-types app-type)]
    (when-not (and subs
                   (or (= subs :all)
                       (and (ss/blank? permitSubtype)
                            (or (empty? subs)
                                ((set subs) :empty)))
                       ((set subs) permitSubtype)))
      (fail :error.unsupported-permit-type))))

(defn valid-permit-types-for-state-change
  "Convenience pre-checker."
  [command]
  (valid-permit-types (->> (permit-types)
                           (util/map-keys keyword)
                           (util/map-values :allow-state-change))
                      command))
