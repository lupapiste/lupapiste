(ns lupapalvelu.ui.auth-admin.stamp.metadata
  (:require [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.stamp.form-entry :refer [form-entry]]))

(rum/defc header-component < rum/reactive
          []
          [:div.group-buttons
           {:style {:background-color "#f6f6f6"
                    :border "1px solid #dddddd"}}
           ;;TODO: onks joku otsikkorivicontainer-luokka josta tulis toi oikee harmaa taustaväri ja muut tyylit niinku haitareissa?
           (form-entry "Leiman nimi:")
           [:button.secondary.is-right
            [:i.lupicon-remove]
            [:span "Poista"]]])

(rum/defc metadata-component < rum/reactive
          []
          [:div.form-group {:style {:width "60%"
                                    :display :inline-block}}
           [:label.form-label.form-label-group "Leiman sijainti"]
           [:div
            (form-entry "Oikeasta reunasta (mm)")
            (form-entry "Alareunasta (mm)")]
           [:div
            (form-entry "Leiman tausta") ;;TODO: vaihda dropdowniin
            (form-entry "Leimattava sivu")] ;;TODO: vaihda dropdowniin
           ])
