(ns lupapalvelu.ui.application.info
  "This namespace is mostly a placeholder for the `info` tab"
  (:require [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::toggle-view
  (fn [cofx [_ visible?]]
    (nutil/merge-every-fn cofx
                          [(if visible?
                             [focus/open-accordion-for-focus-fx]
                             [focus/forget-focus-fx])])))
