(ns lupapalvelu.dev.ui-doc.defaults
  (:require [core.front.icons :as i]))

(def icon-choices
  {:options ["warning" "trash" "none"]
   :mapping {"warning" i/warning
             "trash"   i/trash
             "none"    nil}
   :control {:type :select}})
