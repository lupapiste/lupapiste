(ns lupapalvelu.ui.attachment.components
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as jsutil]))

(rum/defc upload-link [id]
  (let [input-id (or id (jsutil/unique-elem-id))]
    [:div.inline
     [:input.hidden {:type "file"
                     :name "files[]"
                     :id input-id}]
     [:a.link-btn.link-btn--link
      [:label {:for input-id}
       [:i.lupicon-circle-plus]
       [:i.wait.spin.lupicon-refresh]
       [:span "Lisää tiedosto"]]]]))
