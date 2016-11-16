(ns lupapalvelu.opendata.schemas
  (:require [schema.core :as sc]
            [ring.swagger.json-schema :as rjs]))

(sc/defschema Asiointitunnus
  (rjs/field sc/Str {:description "Hakemuksen asiointitunnus esim. LP-2016-000-90001"}))

(sc/defschema PublicApplicationData
  {:asiointitunnus Asiointitunnus})

(sc/defschema OrganizationId
  (rjs/field sc/Str {:description "Organisaation tunnus"}))

