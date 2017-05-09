(ns lupapalvelu.ui.auth-admin.stamp.form-entry
  (:require [rum.core :as rum]))

(rum/defc form-entry [label-string]
          [:span.form-entry
           [:label.form-label.form-label-string label-string]
           [:input.form-input.text]])
