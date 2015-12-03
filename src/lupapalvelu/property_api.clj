(ns lupapalvelu.property-api
  (:require [sade.core :refer :all]
            [sade.property :as p]
            [lupapalvelu.action :refer [defquery] :as action]))

(defquery municipality-by-property-id
  {:parameters [propertyId]
   :user-roles #{:anonymous}}
  [_]
  (if-let [municipality (p/municipality-id-by-property-id propertyId)]
    (ok :municipality municipality)
    (fail :municipalitysearch.notfound)))
