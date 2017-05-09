(ns lupapalvelu.ui.auth-admin.stamp.preview
  (:require [rum.core :as rum]))

(rum/defc preview-component < rum/reactive
          []
          [:div.form-group {:style {:width "35%"
                                    :border "1px solid"
                                    :display :inline-block}}
           [:div
            "sisältöä"]])

