(ns lupapalvelu.ui.pate.published-verdict
  "After publishing the verdict layout is similar to verdict PDF."
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.state :as state]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.columns :as cols]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(rum/defc section < {:key-fn (fn [i _] (str "sec-" i))}
  [index section]
  section)

(rum/defc published-verdict
  "Verdict argument must in the backend format."
  [verdict]
  [:div.published-verdict
   (map-indexed (fn [i m]
                  (section i m))
                (cols/content (cols/verdict-properties {:lang (cols/language verdict)
                                                                 :verdict verdict})
                              (layouts/pdf-layout verdict)))])
