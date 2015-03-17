(ns lupapalvelu.kopiolaitos-api
  (:require [sade.core :refer [ok fail]]
            [sade.util :as util]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.kopiolaitos :as kopiolaitos]))

(defcommand order-verdict-attachment-prints
  {:description "Orders prints of marked verdict attachments from copy institute.
                 If the command is run more than once, the already ordered attachment copies are ordered again."
   :parameters [:id :lang :attachmentsWithAmounts :orderInfo]
   :states     [:verdictGiven :constructionStarted]
   :user-roles #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang])
                      (partial action/vector-parameters-with-map-items-with-required-keys [:attachmentsWithAmounts] [:forPrinting :amount :versions])
                      (fn [{{attachments :attachmentsWithAmounts} :data :as command}]
                        (when (some #(or
                                       (not (= true (:forPrinting %)))
                                       (nil? (util/->int (:amount %) nil))
                                       (empty? (:versions %)))
                                attachments)
                          (fail :error.kopiolaitos-print-order-invalid-parameters-content)))
                      (partial action/map-parameters [:orderInfo])]}
  [command]
  (kopiolaitos/do-order-verdict-attachment-prints command)
  (ok))
