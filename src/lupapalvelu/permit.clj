(ns lupapalvelu.permit
  (:require [taoensso.timbre :refer [error errorf warn]]
            [schema.core :as sc]
            [sade.core :refer [fail]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.integrations.ely :as ely]
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
   :ely-statement-types [sc/Str]
   (sc/optional-key :allow-state-change) (sc/cond-pre sc/Keyword [(sc/cond-pre sc/Keyword sc/Str)] )
   (sc/optional-key :wfs-krysp-ns-name) sc/Str
   (sc/optional-key :wfs-krysp-url-asia-prefix) sc/Str
   (sc/optional-key :kuntagml-asia-key) sc/Keyword})

(def poikkeamislupa :poikkeamislupa)
(def suunnittelutarveratkaisu :suunnittelutarveratkaisu)

(defn valid-permit-type? [permit-type]
  (contains? (permit-types) permit-type))

(def PermitType (sc/pred valid-permit-type? "Valid permit type"))

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
   :allow-state-change ["tyonjohtaja-hakemus" "muutoslupa" :empty]
   :sftp-directory   "/rakennus"
   :allowed-task-schemas #{"task-katselmus" "task-vaadittu-tyonjohtaja" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values true
   :ely-statement-types ely/r-statement-types
   :wfs-krysp-ns-name "rakennusvalvonta"
   :wfs-krysp-url-asia-prefix "rakval:luvanTunnisteTiedot/"
   :kuntagml-asia-key :RakennusvalvontaAsia})

(defpermit YA "Yleisten alueiden luvat"
  {:subtypes             []
   :state-graph          states/default-application-state-graph
   :allow-state-change :all
   :sftp-directory       "/yleiset_alueet"
   :allowed-task-schemas #{"task-katselmus-ya" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :ely-statement-types ely/ya-statement-types
   :wfs-krysp-ns-name "yleisenalueenkaytonlupahakemus"
   :wfs-krysp-url-asia-prefix "yak:luvanTunnisteTiedot/"
   :kuntagml-asia-key :yleinenAlueAsiatieto})

(defpermit YI  "Ymparistoilmoitukset"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :ely-statement-types ely/ymp-statement-types
   :wfs-krysp-ns-name "ymparisto/ilmoitukset"})

(defpermit YL  "Ymparistolupa"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :ely-statement-types ely/ymp-statement-types
   :wfs-krysp-ns-name "ymparisto/ymparistoluvat"
   :wfs-krysp-url-asia-prefix "ymy:luvanTunnistetiedot/"})

(defpermit YM  "Muut ymparistoluvat"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :ely-statement-types ely/ymp-statement-types
   :extra-statement-selection-values false})

(defpermit VVVL  "Vapautushakemus vesijohtoon ja viemariin liittymisesta"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :allow-state-change []
   :sftp-directory "/ymparisto"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :ely-statement-types ely/ymp-statement-types
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
   :ely-statement-types ely/p-statement-types
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
   :ely-statement-types ely/ymp-statement-types
   :wfs-krysp-ns-name "ymparisto/maa_ainesluvat"
   :wfs-krysp-url-asia-prefix "ymm:luvanTunnistetiedot/"})

(defpermit KT "Kiinteistotoimitus"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :sftp-directory "/kiinteistotoimitus"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :ely-statement-types ely/mm-kt-statement-types
   :wfs-krysp-ns-name "kiinteistotoimitus"})

(defpermit MM "Maankayton muutos"
  {:subtypes       []
   :state-graph    states/default-application-state-graph
   :sftp-directory "/maankaytonmuutos"
   :allowed-task-schemas #{"task-katselmus" "task-lupamaarays"}
   :multiple-parties-allowed true
   :extra-statement-selection-values false
   :ely-statement-types ely/mm-kt-statement-types
   :wfs-krysp-ns-name "maankaytonmuutos"})

(defpermit ARK "Arkistointiprojekti"
  {:subtypes                         []
   :state-graph                      states/ark-state-graph
   :allow-state-change               :all
   :sftp-directory                   ""
   :allowed-task-schemas             #{}
   :multiple-parties-allowed         true
   :extra-statement-selection-values false
   :ely-statement-types              []})

(defpermit A "ALLU"
  {:subtypes                         []
   :state-graph                      states/allu-state-graph
   :allow-state-change               :all
   :sftp-directory                   ""
   :allowed-task-schemas             #{}
   :multiple-parties-allowed         false
   :extra-statement-selection-values false
   :ely-statement-types              []})

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


(defmulti application-krysp-mapper
  "Maps application into KRYSP XML and provides attachments data to caller.
  Returns a map with 'xml' and 'attachments' keys."
  {:arglists '([application organization lang krysp-version begin-of-link])}
  (fn [{permit-type :permitType} & _]
    (keyword permit-type)))

(defmethod application-krysp-mapper :default
  [{permit-type :permitType} & _]
  (error "KRYSP 'application mapper' method not defined for permit type: " permit-type)
  nil)

(defmulti review-krysp-mapper
  "Maps reviews (katselmus) into KRYSP XML and saves the XML to disk."
  {:arglists '([application organization review user lang krysp-version begin-of-link])}
  (fn [{permit-type :permitType} & _]
    (keyword permit-type)))

(defmethod review-krysp-mapper :default
  [{permit-type :permitType} & _]
  (error "KRYSP 'review mapper' method not defined for permit type: " permit-type)
  nil)

(defmulti verdict-krysp-mapper
  "Maps verdicts into KRYSP XML."                           ; as of writing this comment, paatostieto mapping was only available for R 2.2.2
  {:arglists '([application organization verdict lang krysp-version begin-of-link])}
  (fn [{permit-type :permitType} & _]
    (keyword permit-type)))

(defmethod verdict-krysp-mapper :default
  [{permit-type :permitType} & _]
  (error "KRYSP 'verdict mapper' method not defined for permit type: " permit-type)
  nil)

(defmulti read-verdict-xml
  "Reads verdicts (sequence) from KRYSP xml."
  {:arglists '([permit-type xml-without-ns])}
  (fn [permit-type & _]
    (keyword permit-type)))

(defmethod read-verdict-xml :default
  [permit-type & _]
  (error "No verdict reader for permit type: " permit-type)
  nil)

(defmulti validate-verdict-xml
  "Reads verdicts (sequence) from KRYSP xml."
  {:arglists '([permit-type xml-without-ns organization])}
  (fn [permit-type & _]
    (keyword permit-type)))

(defmethod validate-verdict-xml :default
  [permit-type & _]
  (error "No verdict validator for permit type: " permit-type)
  nil)

(defmulti read-verdict-extras-xml
  "Reads some extras from verdict KRYSP xml.
  Returns application mongo updates.
  Method is called even if xml validation fails"
  {:arglists '([application app-xml & [modified]])}
  (fn [{permit-type :permitType} & _]
    (keyword permit-type)))

(defmethod read-verdict-extras-xml :default
  [& _]
  nil)

(defmulti read-tj-suunnittelija-verdict-xml
  "Reads tj/suunnittelija verdicts (sequence) from KRYSP xml."
  {:arglists '([permit-type doc party-type target-kuntaRoolikoodi xml-without-ns])}
  (fn [permit-type & _]
    (keyword permit-type)))

(defmethod read-tj-suunnittelija-verdict-xml :default
  [permit-type & _]
  (error "No tj/suunnitelija verdict reader for permit type: " permit-type)
  nil)

(defmulti fetch-xml-from-krysp
  "Fetches KRYSP XML from municipality backend."
  {:arglists '([permit-type server-url credentials ids search-type raw?])}
  (fn [permit-type & _]
    (keyword permit-type)))

(defmethod fetch-xml-from-krysp :default
  [permit-type & _]
  (error "No fetch method for permit type: " permit-type)
  nil)

(defmulti parties-krysp-mapper
  "Maps designer documents into KRYSP XML, returns map where document ids are keys and XML models are values."
  {:arglists '([application doc-subtype lang krysp-version])}
  (fn [{permit-type :permitType} & _]
    (keyword permit-type)))

(defmethod parties-krysp-mapper :default
  [{permit-type :permitType} & _]
  (error "KRYSP 'parties mapper' method not defined for permit type: " permit-type)
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
  (let [valid-types (set (map name validator-permit-types))]
    (fn [{:keys [application]}]
      (if application
        (when-not (valid-types (permit-type application))
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

(defn is-ya-permit [permit-type]
  (= permit-type (name YA)))

(defn ymp-permit-type? [permit-type]
  (let [ymp-permit-types (set (map name [YI YL YM VVVL MAL]))]
    (ymp-permit-types permit-type)))

(defn archiving-project? [{:keys [permitType]}]
  (= permitType (name ARK)))

(defn is-archiving-project [{:keys [application]}]
  (when-not (archiving-project? application)
    (fail :error.unsupported-permit-type)))

(defn is-not-archiving-project [{{:keys [permitType]} :application}]
  (when (= permitType (name ARK))
    (fail :error.unsupported-permit-type)))
