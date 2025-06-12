(ns lupapalvelu.assurance-schemas
  (:require [lupapalvelu.i18n :as i18n]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(sc/defschema ApplicationSubmitAssuranceTemplate
              "Schema for assurance template that is stored in the organization document"
              {:id                               ssc/ObjectIdStr
               :modified                         ssc/Timestamp
               :text                             (i18n/lenient-localization-schema ssc/NonBlankStr)
               (sc/optional-key :required)       sc/Bool
               (sc/optional-key :all-operations) sc/Bool
               (sc/optional-key :operations)     [sc/Str]})

(sc/defschema ApplicationSubmitAssurancesData
              "Schema for assurance data that is stored in the application document"
              {:user       {:id    ssc/NonBlankStr
                            :email ssc/NonBlankStr}
               :ts         ssc/Timestamp
               :language   i18n/Lang
               :assurances [ApplicationSubmitAssuranceTemplate]})
