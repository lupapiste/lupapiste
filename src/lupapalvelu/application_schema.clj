(ns lupapalvelu.application-schema
  (:require [schema.core :refer [defschema] :as sc]
            [sade.validators :as validators]))

(defschema ApplicationId
  (sc/constrained sc/Str validators/application-id? "Application id"))
