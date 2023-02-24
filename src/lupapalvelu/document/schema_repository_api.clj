(ns lupapalvelu.document.schema-repository-api
  (:require [sade.core :refer [ok]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.action :refer [defquery]]))

(defquery schemas
  {:user-roles #{:applicant :authority :oirAuthority :financialAuthority}}
  [_]
  (ok :schemas (schemas/get-all-schemas)))
