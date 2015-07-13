(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :refer :all]
            [sade.core :refer [ok now]]
            [lupapalvelu.action :refer [defquery defraw disallow-impersonation]]))

(defquery owners
  {:parameters [propertyId]
   :pre-checks [disallow-impersonation]
   :user-roles #{:authority}}
  [_]
  (ok :owners (get-owners propertyId)))

(defraw mml-owners
  {:parameters [propertyId]
   :user-roles #{:admin}}
  {:status 200
   :body (get-owners-raw propertyId)
   :headers {"Content-Type" "application/xml;charset=UTF-8"
             "Content-Disposition" (format "attachment;filename=\"%s-%s.xml\"" propertyId (now))
             "Cache-Control" "no-cache"}})
