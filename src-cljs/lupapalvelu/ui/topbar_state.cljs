(ns lupapalvelu.ui.topbar-state
  "Feature flag state for the new top bar.

  Initial value comes from the server-side feature flag `enable-new-topbar`.
  The dev panel can override it locally via localStorage (same pattern as
  `lupapalvelu.ui.enabled-state` for CLJS Docgen)."
  (:require [reagent.core :as r]))

(def feature-flag (when (exists? js/features) (js/features.enabled "enable-new-topbar")))

(def ^:private initial-state
  (let [stored (.getItem js/localStorage "newTopbarEnabled")]
    (if stored
      (= stored "true")
      (boolean feature-flag))))

(def state (r/atom initial-state))

(defn toggle
  "Called from the dev panel."
  [enabled]
  (reset! state enabled)
  (if (= enabled (boolean feature-flag))
    (.removeItem js/localStorage "newTopbarEnabled")
    (.setItem js/localStorage "newTopbarEnabled" (if enabled "true" "false"))))
