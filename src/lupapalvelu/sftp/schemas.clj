(ns lupapalvelu.sftp.schemas
  (:require [lupapalvelu.i18n :refer [Lang]]
            [lupapalvelu.permit :refer [PermitType]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [java.io InputStream]))

(def SftpType
  "Organization `:sftpType` values. Defaults to legacy."
  (sc/enum "legacy" "gcs"))

(def SftpTypeField
  "If the map has `:sftpType` field it must be valid. The `m` is 'undelayed' just in case,
  since it is typically an organization."
  (sc/pred (fn [m]
             (let [field (select-keys (force m) [:sftpType])]
               (or (empty? field)
                   (nil? (sc/check {:sftpType SftpType} field)))))
           "Valid :sftpType field."))

(sc/defschema FileEntry
  {:name         ssc/NonBlankStr ; Basename of the file
   :size         ssc/Nat         ; Size in bytes
   :content-type ssc/NonBlankStr ; Fallbacks to application/octet-stream
   :modified     ssc/Timestamp})

(sc/defschema FileStream
  (assoc FileEntry
         :stream (sc/pred (partial instance? InputStream))))

(sc/defschema IntegrationMessages
  {:waiting [FileEntry]
   :ok      [FileEntry]
   :error   [FileEntry]})

(sc/defschema WriteApplicationOptions
  {:xml                                     sc/Any
   (sc/optional-key :ts)                    ssc/Timestamp
   (sc/optional-key :file-suffix)           ssc/NonBlankStr
   (sc/optional-key :lang)                  Lang
   (sc/optional-key :sftp-links?)           sc/Bool
   (sc/optional-key :attachments)           [{:fileId                     ssc/NonBlankStr
                                              ;; Filename is not needed if only links are exported.
                                              (sc/optional-key :filename) (sc/maybe ssc/NonBlankStr)
                                              sc/Keyword                  sc/Any}]
   (sc/optional-key :submitted-application) sc/Any})

(def SftpUser "Maybe too restrictive but inline with the legacy accounts" #"^[a-z0-9_\-]+$")

(sc/defschema SftpUserConfiguration
  {:type                          (sc/enum "backing-system" "case-management" "invoicing")
   :username                      SftpUser
   (sc/optional-key :permitTypes) [PermitType]})

(sc/defschema SftpOrganizationConfiguration
  {:sftpType                      SftpType
   :users                         [SftpUserConfiguration]
   (sc/optional-key :permitTypes) [PermitType]})
