(ns lupapalvelu.document.schema-repository-api
  (:require [sade.core :refer [ok]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.action :refer [defquery]]))

(defquery schemas
  {:roles [:applicant :authority]}
  [_]
  (ok :schemas (schemas/get-all-schemas)))
