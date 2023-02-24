(ns lupapalvelu.ui.auth-admin.stamp.preview
  (:require [rum.core :as rum]
            [lupapalvelu.ui.rum-util :as rum-util]))

(defmulti preview-element (comp keyword :type))

(defmethod preview-element :current-date [_]
  (.format (js/moment) "D.M.YYYY"))

(defmethod preview-element :verdict-date [_]
  (.format (js/moment) "D.M.YYYY"))

(defmethod preview-element :backend-id [_] "R123456-001")

(defmethod preview-element :user [_]
  (.displayName js/lupapisteApp.models.currentUser))

(defmethod preview-element :organization [_] "Kunnan rakennusvalvonta")

(defmethod preview-element :application-id [_] "LP-123-2000-00111")

(defmethod preview-element :building-id [_] "123456789A")

(defmethod preview-element :custom-text [{:keys [text]}] text)

(defmethod preview-element :extra-text [_] "teksti / text")

(defmethod preview-element :section [_] "§123")

(defmethod preview-element :lupapiste [_] "www.lupapiste.fi")

(rum/defc stamp-elem-preview [elem]
  [:span.stamp-preview-element (preview-element elem)])

(rum/defc stamp-row-preview [row]
  [:li.stamp-preview-row (rum-util/map-with-key stamp-elem-preview row)])

(rum/defc preview-component < rum/reactive [rows]
  [:div.col-2
   [:ul.stamp-preview (rum-util/map-with-key stamp-row-preview (conj (rum/react rows) [{:type :lupapiste}]))]])
