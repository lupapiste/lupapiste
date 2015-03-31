(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [defquery non-blank-parameters]]
            [sade.core :refer [ok]]
            [lupapalvelu.tiedonohjaus :as t]))

(defquery available-tos-functions
          {:user-roles #{:anonymous}
           :parameters [organizationId]
           :input-validators [(partial non-blank-parameters [:organizationId])]}
          (let [functions (t/get-functions-from-toj organizationId)]
            (ok :functions functions)))
