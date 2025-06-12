(ns lupapalvelu.ui2.icons
  "Wrapper ns for Carbon Icons.

  These should be preferred for new views over Lupicons.

  Any icons required here are included in the JS bundle, so we avoid
  requiring icons that aren't used."
  (:refer-clojure :exclude [filter])
  (:require ["@carbon/icons-react/es/ArrowDownRight$default" :as ArrowDownRight]
            ["@carbon/icons-react/es/Close$default" :as Close]
            ["@carbon/icons-react/es/Filter$default" :as Filter]
            ["@carbon/icons-react/es/Search$default" :as Search]
            ["@carbon/icons-react/es/Settings$default" :as Settings]
            [reagent.core :as r]))

(def arrow-down-right (r/adapt-react-class ArrowDownRight))
(def close (r/adapt-react-class Close))
(def filter (r/adapt-react-class Filter))
(def search (r/adapt-react-class Search))
(def settings (r/adapt-react-class Settings))
