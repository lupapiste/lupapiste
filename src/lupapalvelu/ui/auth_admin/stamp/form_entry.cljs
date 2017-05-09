(ns lupapalvelu.ui.auth-admin.stamp.form-entry
  (:require [rum.core :as rum]))

(rum/defc form-entry [label-key]
          [:span.form-entry
           [:label.form-label.form-label-string label-key]
           [:input.form-input.text]])
