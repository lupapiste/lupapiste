(ns lupapalvelu.ui.attachment.components.dropzone
  (:require [lupapalvelu.ui.common :as common]))

(defn dropzone
  "ClojureScript version of the legacy (Knockout) DropZone component."
  []
  [:drop-zone
   [:div.drop-zone-placeholder
    [:i.lupicon-upload]
    [:div (common/loc "dropzone.placeholder")]]])
