(ns lupapalvelu.conversion.schemas
  (:require [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.permit :as permit :refer [PermitType]]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def ^:private unsupported #{:VAK})

(defn supported-backend-id?
  "True if the `backend-id` is in the known (parseable) format AND its type is importable."
  [backend-id]
  (let [{:keys [tyyppi]} (conv-util/destructure-permit-id backend-id)]
    (and tyyppi (not (contains? unsupported (keyword tyyppi))))))

(def SupportedBackendId (sc/pred supported-backend-id? "Backend-id supported by the conversion."))

(sc/defschema ConversionLocation
  {:x                            ssc/LocationX
   :y                            ssc/LocationY
   (sc/optional-key :propertyId) ssc/NonBlankStr
   (sc/optional-key :address)    ssc/NonBlankStr})

(sc/defschema ConversionEdn
  "Configuration EDN file syntax. If neither `:backend-ids` nor `:files` is given, the
  conversion targets are queried from the mongo conversion collection."
  {:organization-id                         ssc/NonBlankStr
   :permit-type                             PermitType
   (sc/optional-key :overwrite?)            sc/Bool
   (sc/optional-key :force-terminal-state?) sc/Bool
   ;; Backend ids of the permits that are fetched from the backing system.
   (sc/optional-key :backend-ids)           [SupportedBackendId]
   ;; Local KuntaGML message filenames
   (sc/optional-key :files)                 [ssc/NonBlankStr]
   ;; Keys are backend ids
   (sc/optional-key :location-overrides)    {ssc/NonBlankStr ConversionLocation}
   (sc/optional-key :location-fallback)     ConversionLocation})

(sc/defschema ConversionDocument
  {:id           ssc/NonBlankStr ;; Old document ids are not object id strings
   :backend-id   ssc/NonBlankStr
   :LP-id        ssc/NonBlankStr
   :organization ssc/NonBlankStr
   sc/Keyword    sc/Any})

(sc/defschema Target
  (sc/conditional
    :id {:id SupportedBackendId}
    :else {:filename ssc/NonBlankStr}))

(sc/defschema ResolvedTarget
  {;; From EDN or target file
   :id                                   SupportedBackendId
   (sc/optional-key :filename)           ssc/NonBlankStr
   ;; From target file. If found the the target should not be converted.
   (sc/optional-key :xml-application-id) ssc/NonBlankStr
   (sc/optional-key :conversion-doc)     ConversionDocument})

(sc/defschema ConversionConfiguration
  (st/merge
    (st/select-keys ConversionEdn [:organization-id :permit-type :overwrite? :force-terminal-state?
                                   :location-overrides :location-fallback])
    {;; Municipality for the first organization scope that matches `permit-type`.
     :municipality ssc/NonBlankStr
     ;; If not defined or empty, the targets are queried from the mongo conversion collection.
     (sc/optional-key :targets) [Target]}))

(sc/defschema DeleteOptions
  {(sc/optional-key :delete-conversion-document?) sc/Bool
   (sc/optional-key :force?)                      sc/Bool})
