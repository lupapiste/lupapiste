(ns lupapalvelu.dev.ui-doc.i18n
  (:require [lupapalvelu.ui.common :refer [loc]]))

(def locales {:en    {:short-name "En"
                      :name       "English (GB)"}
              :fi    {:short-name "Fi"
                      :name       "Finnish"}
              :sv    {:short-name "Sv"
                      :name       "Swedish"}})

(defn make-tr
  [locale]
  (if (= :none locale)
    pr-str
    loc))
