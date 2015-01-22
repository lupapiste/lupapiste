(ns lupapalvelu.kopiolaitos_api
  (:require [sade.core :refer [ok]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify boolean-parameters] :as action]
            [lupapalvelu.kopiolaitos :as kopiolaitos]))

(defcommand order-verdict-attachment-prints
  {:description "Orders prints of marked verdict attachments from copy institute.
                 If the command is run more than once, the already ordered attachment copies are ordered again."
   :parameters [:id :lang :attachmentIdsAndAmounts :orderInfo]
   :states     [:verdictGiven :constructionStarted]
   :roles      [:authority]
   :input-validators [(partial action/non-blank-parameters [:lang])
                      (partial action/vector-parameters [:attachmentIdsAndAmounts])
                      (partial action/map-parameters [:orderInfo])]
   ;; TODO: Poista, kun feature on kaytossa
   :feature    :verdict-attachment-order}
  [command]
  (kopiolaitos/do-order-verdict-attachment-prints command)
  (ok))
