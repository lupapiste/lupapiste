(ns lupapalvelu.next.legacy-dom
  "Common stuff working with legacy DOM.
  'Adapters' for working with legacy DOM should be included here."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]))


(defn page-sections
  "Returns IDs of all <section.page> elements"
  []
  (for [sec (array-seq (js/document.getElementsByTagName "section"))
        :when (and (not (str/blank? (.-id sec)))
                   (str/includes? (.-className sec) "page"))]
    (.-id sec)))


(rf/reg-event-fx ::open-side-panel
  (fn [_ [_ tab delay]]
    {:hub/send {:event "side-panel::open"
                :data  {:tab   (or tab "conversation")
                        :delay (or delay 10)}}}))
