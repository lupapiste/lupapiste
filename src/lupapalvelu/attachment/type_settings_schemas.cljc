(ns lupapalvelu.attachment.type-settings-schemas
  (:refer-clojure :exclude [contains?])
  (:require [sade.core :refer :all]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all]))

(defschema AttachmentTypeMap {sc/Keyword [sc/Keyword]})

(defschema InheritableAttachmentTypeData
  "Data for :allowed-attachments (now, 2022-07-04)  and defaults-attachment-types (in future)

   Map keys:
   - :mode defines how inheritance is resolved.

   Currently (2022-07-04) there are only two modes
    * :set (= use types listing from this node) and
    * :inherit (use listing from ancestor).

   Possible, extensions are:
    :append (= all types from the parent and in addition those listed in this map), and
    :except (= all types from the parent except those listed in types listing).

    :types attachments as map."
  {:mode  (sc/enum :set :inherit)
   :types AttachmentTypeMap})

(defschema InheritableAttachmentNode
  "InheritableAttachmentNode is a container for hierachical attachment data.

    Currently (2022-11-09), node has following attributes:

    - :permit-type Permit type of the related operation.
    - :allowed-attachments It contains allowed attachment types either for operation or for permit type.
    - :default-attachments types of the default attachments.
    - :tos-function attachment default function in TOS (tiedonohjaussuunnitelma)
    - :default-attachments-mandatory? true if type listed in defaults-attachment-types are mandatory.
    - :deprecated? Operation is :hidden, which typically means that it is replaced and
      only present in the legacy applications.

    NOTE! Currently (2022-11-09) some attributes are NOT stored in Mongo's organization object in
    operations-attachment-settings as ExpandedOperationsAttachmentSettings. Use
    lupapiste.attachment.type/organization->organization-attachment-settings to aggregate them to this
    data structure.

    In the future, it may have the following attributes:
    - :are-default-attachment-mandatory-exceptions: types of the default that are mandatory unlike the others
      or vice verse."
  {(sc/optional-key :permit-type)                    sc/Keyword
   :allowed-attachments                              InheritableAttachmentTypeData
   (sc/optional-key :default-attachments)            AttachmentTypeMap
   (sc/optional-key :tos-function)                   sc/Str
   (sc/optional-key :default-attachments-mandatory?) sc/Bool
   (sc/optional-key :deprecated?)                    sc/Bool})

(defschema ExpandedOperationsAttachmentSettings
  "Intermediatery attechment settings object converted from organization.
   Note: This is not the format data is stored in database but rather one that is optimized for the business
   logic in backend.

   :defaults It contains defaults for all settings per permit-type.
   :permit-type-nodes contains overridden settings on permit-type level.
   :operations-nodes contains overridden settings on operation level."
  {:defaults            {:allowed-attachments {sc/Keyword AttachmentTypeMap}}
   :permit-type-nodes   {sc/Keyword InheritableAttachmentNode}
   :operation-nodes     {sc/Keyword InheritableAttachmentNode}})
