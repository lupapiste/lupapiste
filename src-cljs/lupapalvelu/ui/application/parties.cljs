(ns lupapalvelu.ui.application.parties
  "This namespace is mostly a placeholder for the `parties` tab"
  (:require [re-frame.core :as rf]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.next.util :as nutil]))

(rf/reg-event-fx ::toggle-view
  (fn [cofx [_ visible?]]
    (nutil/merge-every-fn cofx
                          [(if visible?
                             [focus/open-accordion-for-focus-fx]
                             [focus/forget-focus-fx])])))
