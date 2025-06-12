(ns lupapalvelu.ui.attachment.components.upload-button
  (:require [lupapalvelu.ui.attachment.components.upload-mixin :as upload-mixin]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]))

(rum/defcs upload-button < (upload-mixin/upload-mixin)
  "Handles file upload with fileupload-service.
  First param must be callback function or map (see `upload-mixin` above), which receives filedata event via hub."
  [local-state _ & _]
  [:div.inline
   [:input.hide-input {:type         "file"
                       :name         "files[]"
                       :data-test-id "upload-button-input"
                       :id           (::upload-mixin/input-id local-state)}]
   [:label.tertiary {:for          (::upload-mixin/input-id local-state)
                     :data-test-id "upload-button-label"}
    [:i.lupicon-circle-plus {:aria-hidden true}]
    [:span (common/loc "attachment.addFile")]]])
