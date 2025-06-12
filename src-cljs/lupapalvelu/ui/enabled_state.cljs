(ns lupapalvelu.ui.enabled-state
  (:require [reagent.core :as r]))

(def feature-flag (js/features.enabled "enable-cljs-docgen"))

(def ^:private initial-state (js/features.cljsDocgenInitialState))

(def state (r/atom initial-state))

(defn toggle
  "Called from dev panel"
  [enabled]
  ;; Save value to atom - for dev panel
  (reset! state enabled)
  ;; Store value to localStorage so same value is used after restore.
  ;; If going back to feature flag state, remove localStorage value.
  (if (= enabled feature-flag)
    (.removeItem js/localStorage "cljsDocgenEnabled")
    (.setItem js/localStorage "cljsDocgenEnabled" (if enabled "true" "false")))
  ;; Send event for JS code to re-initialize JS code on change
  (.send js/hub "toggle-docgen-cljs" #js {:enabled enabled}))

;; Note:
;; The JS models have their own observable for showCljsDocgen state,
;; this is because they need to be able to clear up their state
;; before they update the observable which triggers KO template changes.
;; The important part is that those observables initialize to 
