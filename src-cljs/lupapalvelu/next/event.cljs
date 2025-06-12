(ns lupapalvelu.next.event
  (:require [re-frame.core :as rf]
            [reagent.ratom :as ratom]))

(def >evt rf/dispatch)

(defn- assert-in-reactive
  "Better warnings when `<sub` is used outside a reactive context.

  At the moment using subscriptions inside e.g. event handlers might leak memory,
  as re-frame will cache the subscription and might never release it. This
  might change in the future (https://github.com/day8/re-frame/issues/753)."
  [query]
  (when ^boolean js/goog.DEBUG
    (when-not (ratom/reactive?)
      (.groupCollapsed js/console (str "Warning: <sub called outside view/subscription: " (pr-str query)))
      (.trace js/console)
      (.groupEnd js/console))))

(defn <sub
  ([query]
   (assert-in-reactive query)
   (deref (rf/subscribe query)))
  ([query dynv]
   (assert-in-reactive query)
   (deref (rf/subscribe query dynv))))
