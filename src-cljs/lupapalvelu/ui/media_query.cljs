(ns lupapalvelu.ui.media-query
  (:require [goog.functions :as gfunc]
            [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]))

(def min-widths
  "These should match the Tailwind screen defined in config/tailwind.config.js"
  ;; Tailwind uses 3xl and 2xl names, but it doesn't matter here
  [[:xxxl 1660]
   [:xxl 1536]
   [:xl 1280]
   [:lg 1024]
   [:md 768]
   [:sm 640]])

(defn on-resize-update []
  (->> min-widths
       (keep (fn [[bp min-width]]
               (when (.-matches (.matchMedia js/window (str "(min-width: " min-width "px)")))
                 bp)))
       (set)
       (vector ::set-matching-breakpoints)
       (>evt)))

(def on-resize-throttled (gfunc/throttle on-resize-update 300))

(defn init []
  ;; Init can be called multiple times at least when developing with shadow-cljs reloading, but adding
  ;; the same static function as listener should be a no-op.
  (.addEventListener js/window "resize" on-resize-throttled)
  (on-resize-update))

(rf/reg-event-db ::set-matching-breakpoints
  (fn [db [_ breakpoints]]
    (assoc db ::currently-matching-breakpoints breakpoints)))

(rf/reg-sub ::currently-matching-breakpoints :-> ::currently-matching-breakpoints)

(rf/reg-sub ::breakpoint-matches?
  :<- [::currently-matching-breakpoints]
  (fn [breakpoints [_ bp]]
    (contains? breakpoints bp)))
