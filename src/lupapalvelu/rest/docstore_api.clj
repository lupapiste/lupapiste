(ns lupapalvelu.rest.docstore-api
  (:require [clojure.string :as str]
            [cognitect.transit :as transit]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.permit :as permit :refer [PermitType]]
            [lupapalvelu.rest.docstore :as docstore]
            [lupapalvelu.rest.rest-api :refer [defendpoint-for defendpoint]]
            [lupapalvelu.rest.schemas :refer [ApiResponse]]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :refer [matches?]]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]])
  (:import [java.io ByteArrayOutputStream]))

;; Schema definitions

(defschema OrganizationMunicipalityInfo
  {:id         sc/Str
   :name       (i18n/lenient-localization-schema sc/Str)
   :permitType PermitType})

(defschema OrganizationDocstoreInfo
  (-> org/DocStoreInfo
      (st/dissoc :allowedTerminalAttachmentTypes)
      (assoc :id org/OrgId
             :name (i18n/lenient-localization-schema sc/Str)
             :municipalities [OrganizationMunicipalityInfo])))

(defschema OrganizationResponse
  (assoc ApiResponse :data OrganizationDocstoreInfo))

(defschema OrganizationsResponse
  (assoc ApiResponse :data [OrganizationDocstoreInfo]))

(defschema OrganizationDocterminalAllowedAttachmentTypesInfo
  (-> org/DocStoreInfo
      (st/select-keys [:docTerminalInUse :documentRequest :allowedTerminalAttachmentTypes :allowedDepartmentalAttachmentTypes])
      (assoc :id org/OrgId
             :name (i18n/lenient-localization-schema sc/Str)
             :municipalities [OrganizationMunicipalityInfo])))

(defschema AllowedAttachmentTypesResponse
  (assoc ApiResponse :data [OrganizationDocterminalAllowedAttachmentTypesInfo]))


;; Docstore info

(defn- municipality-name [municipality-code]
  (i18n/supported-langs-map #(i18n/localize % (str "municipality." municipality-code))))

(defn municipality-info [{:keys [municipality permitType]}]
  {:id         municipality
   :name       (municipality-name municipality)
   :permitType permitType})

(defn- serialize-instructions
  "Document request instructions markup -> tags -> transit json."
  [docstore-info]
  (update-in docstore-info
             [:documentRequest :instructions]
             (partial util/map-values
                      (fn [markup]
                        (let [markup (ss/trim markup)]
                          (if (ss/blank? markup)
                            markup
                            (let [buf (ByteArrayOutputStream. 4096)
                                  w   (transit/writer buf :json)]
                              (transit/write w (markup/markup->tags markup))
                              (str buf))))))))

(defn- make-docstore-info [{:keys [id docstore-info name scope]}]
  (-> docstore-info
      (dissoc :allowedTerminalAttachmentTypes)
      (assoc :id             id
             :name           name
             :municipalities (->> scope
                                  (map municipality-info)
                                  distinct))
      serialize-instructions))

(defn get-docstore-infos
  ([]
   (get-docstore-infos {}))
  ([query]
   (->> (org/get-organizations query {:docstore-info 1
                                      :name          1
                                      :scope         1})
        (map make-docstore-info)
        (remove (partial sc/check OrganizationDocstoreInfo)))))

(defschema OrganizationStatusFilter
  (sc/enum "all" "active" "inactive"))

(def status-query
  {"all"      {}
   "active"   {:docstore-info.docStoreInUse true}
   "inactive" {:docstore-info.docStoreInUse false}})

(defendpoint-for usr/docstore-user? "/rest/docstore/organizations" false
  {:summary             ""
   :description         ""
   :parameters          []
   :optional-parameters [:status OrganizationStatusFilter
                         :permit-type (apply sc/enum (keys (permit/permit-types)))]
   :returns             OrganizationsResponse}
  (let [query (cond-> (get status-query status {})
                      (not (str/blank? permit-type)) (assoc :scope.permitType permit-type))]
    {:ok true :data (get-docstore-infos query)}))


;; Allowed attachment types for docterminal

(defn- make-attachment-type-info [{:keys [id docstore-info scope name]}]
  (-> docstore-info
      (select-keys (concat [:allowedTerminalAttachmentTypes  :docTerminalInUse :documentRequest]
                           (when (:docDepartmentalInUse docstore-info)
                             [:allowedDepartmentalAttachmentTypes])))
      (assoc :id id
             :name name
             :municipalities (->> scope
                                  (map municipality-info)
                                  distinct))
      serialize-instructions))

(defn get-attachment-type-infos []
  (->> (org/get-organizations {$or [{:docstore-info.docTerminalInUse true}
                                    {:docstore-info.docDepartmentalInUse true}]}
                              [:docstore-info :scope :name])
       (map make-attachment-type-info)))

(defendpoint-for usr/docstore-user? "/rest/docstore/allowed-attachment-types" false
  {:summary     ""
   :description ""
   :parameters  []
   :returns     AllowedAttachmentTypesResponse}
  {:ok true :data (get-attachment-type-infos)})


(defschema BuildingsResponse
  (assoc ApiResponse :data [{:nationalId sc/Str :usage (sc/maybe sc/Str)}]))

(defendpoint-for usr/docstore-user? "/rest/docstore/property-building-ids" false
  {:summary "Given address or property id, return a list of buildings in the given property."
   :description ""
   :parameters [:organization org/OrgId
                :propertyId   (sc/pred (partial matches? #"\d+"))]
   :returns BuildingsResponse}
  (if-let [{url :url credentials :credentials} (org/get-building-wfs {:organization organization
                                                                      :permitType "R"})]
    {:ok true :data (->> (building-reader/building-info-list url credentials propertyId)
                         (map #(select-keys % [:usage :nationalId]))
                         (remove (comp nil? :nationalId))
                         (map #(merge {:usage nil} %))
                         (sort-by :nationalId)
                         vec)}
    {:ok true :data []}))

(defschema MessageResponse
  (assoc ApiResponse :data [sc/Str]))

(defendpoint-for usr/docstore-user? "/rest/docstore/screenmessages" false
  {:summary     "Screenmessages for the docstore instance."
   :description "Currently only Finnish is supported in the docstore applications."
   :parameters  [:instance-type (sc/enum "store" "terminal" "departmental")]
   :returns     MessageResponse}
  {:ok   true
   :data (map :fi (mongo/select :screenmessages {:products instance-type} [:fi]))})

;;-----------------------------------
;; Departmental
;;-----------------------------------

(defschema DepartmentalOrganizationsResponse
  (assoc ApiResponse
         :data [{:id              org/OrgId
                 ;; L10n names are docstore-info organization descriptions with organization names as
                 ;; fallback.
                 :name            (i18n/lenient-localization-schema sc/Str)
                 :documentRequest (:documentRequest org/DocStoreInfo)
                 :municipalities  [sc/Str]}]))

(defendpoint "/rest/docstore/departmental-organizations"
  {:summary     "Organizations for the departmental user."
   :description "Organization is included if its department terminal is enabled and the user has the access rights."
   :oauth-scope :read
   :returns     DepartmentalOrganizationsResponse}
  (->> (docstore/departmental-organizations user)
       (map (fn [{:keys [scope docstore-info id name]}]
              {:id              id
               :name            (->> docstore-info
                                     :organizationDescription
                                     util/strip-blanks
                                     (merge name))
               :documentRequest (:documentRequest docstore-info)
               :municipalities  (->> scope (map :municipality) distinct)}))
       (assoc {:ok true} :data)))
