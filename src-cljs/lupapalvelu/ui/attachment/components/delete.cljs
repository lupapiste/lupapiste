(ns lupapalvelu.ui.attachment.components.delete
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.rum-util :as rum-util]
            [rum.core :as rum]))

(defn- loc-escape [k]
  (-> k common/loc common/escape-html))

(defn delete-with-confirmation [attachment]
  (hub/send  "show-dialog"
             {:ltitle          "attachment.delete.header"
              :size            "medium"
              :component       "yes-no-dialog"
              :componentParams {:text  (if-let [latest (:latestVersion attachment)]
                                         (js/sprintf "%s: %s<br/>%s"
                                                     (loc-escape :attachment.delete)
                                                     (common/escape-html (:filename latest))
                                                     (loc-escape :attachment.delete.message))
                                         (loc-escape "attachment.delete.message.no-versions"))
                                :yesFn #(js/lupapisteApp.services.attachmentsService.removeAttachment (:id attachment))}}))

(rum/defc delete-attachment-link < {:key-fn #(str "delete-attachment-" (:id %1))}
  (rum-util/hubscribe "attachmentsService::remove"
                      (fn [state]
                        {:commandName  "delete-attachment"
                         :ok           false
                         :attachmentId (-> state :rum/args first :id)})
                      (fn [_ resp] (.showSavedIndicator js/util resp)))
  (rum-util/hubscribe "attachmentsService::remove"
                      (fn [state]
                        {:commandName  "delete-attachment"
                         :ok           true
                         :attachmentId (-> state :rum/args first :id)})
                      (fn [state resp] (let [[_ callback] (-> state :rum/args)] (callback resp))))
  [attachment _]
  (components/click-link {:click   #(delete-with-confirmation attachment)
                          :test-id "delete-attachment-link"
                          :class   :gap--l1
                          :text    (str "[" (common/loc "remove") "]")}))
