(ns lupapalvelu.next.modern
  (:require [lupapalvelu.next.ajax :as ajax]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.session :as sess]
            [lupapalvelu.ui.lazy :refer [lazy-component]]
            [lupapalvelu.ui.topbar-connected :refer [connected-topbar]]
            [lupapalvelu.ui.topbar-state :as topbar-state]
            [reagent.core :refer [defc]]
            [reagent.dom.client :as rdomc]
            [reagent.hooks :as hooks]))

(defc wrap-check-session
  [view]
  (let [allow-anon?      js/lupapisteApp.allowAnonymous
        user             (<sub [::sess/user])
        in-hashbang-app? (= js/lupapisteApp.startPage "hashbang")]
    (hooks/use-effect (fn []
                        (cond
                          ;; Check if the entrypoint was the hashbang.js
                          ;; in that case we should go the start, instead of
                          ;; displaying current view component.
                          in-hashbang-app?
                          (>evt [:route/replace (keyword js/lupapisteApp.startPage)])

                          (or allow-anon? user) nil

                          (not allow-anon?)
                          (ajax/query "user"
                                      ;; No need for callbacks, when user is available, new wait is hidden
                                      {:success-fn  (partial sess/user-success (fn router-callback-nop []))
                                       :error-fn    sess/error-response
                                       :complete-fn (fn complate-callback-nop [])})

                          :else
                          (>evt [:route/replace :login])))
                      [in-hashbang-app? allow-anon? user])
    (cond
      in-hashbang-app?
      nil

      ;; Show the defined route view component
      (or allow-anon? user)
      view

      ;; Show loading spinner when loading the user session
      (not allow-anon?)
      [:div.ajax-loader-container.block
       [:div
        [:p ""]]]

      :else
      nil)))

(defc topbar-mount
  "Mounts the new topbar and manages the `new-topbar-active` body class
  that hides the legacy nav.nav-wrapper. Only rendered when a user is authenticated."
  []
  (let [user (<sub [::sess/user])]
    (hooks/use-effect
      (fn []
        (when user
          (.add (.-classList (.-body js/document)) "new-topbar-active"))
        (fn []
          (.remove (.-classList (.-body js/document)) "new-topbar-active")))
      #js [user])
    (when user
      [connected-topbar])))

(defc wrap-page-height
  "Follow <footer> element height so we can make the new app container
  at least the full page height. This is so the route views can e.g. set
  a background color.

  All this is a workaround as it is not(?) trivial to make <body> use flexbox
  without breaking old views."
  [child]
  (let [footer-el                         (js/document.querySelector "footer")
        [footer-height set-footer-height] (hooks/use-state 0)
        o                                 (hooks/use-memo (fn []
                                                            (js/ResizeObserver. (fn [entries]
                                                                                  (set-footer-height (.. (aget entries 0) -contentRect -bottom)))))
                                                          [])]
    (hooks/use-layout-effect (fn []
                               (.observe o footer-el)
                               (fn []
                                 (.disconnect o)))
                             [footer-el])
    (let [topbar? @topbar-state/state]
      (when (or child topbar?)
        [:div.flex.flex-col.twp.nobase.lp-theme
         {:style {:min-height    (str "calc(100vh - " footer-height "px)")
                  :margin-bottom "calc(-1 * var(--footer-top-margin) - var(--header-height))"}}
         (when topbar?
           [topbar-mount])
         child]))))

(defn new-root-view
  "React root for routes which use either :view or :loadable property.

  The div where this is mountred, is empty if the current match doesn't have
  either of the properties.

  Old legacy routes use `default-legacy-controller` to hide their own DOM
  elements themselves, so we don't need to care about those.

  Components should use Reagent defc themselves if they want to enable
  fn component implementation for hooks."
  []
  ;; NOTE: Consider route "identity" (React key) so user 1 to user 2
  ;; component would unmount & mount instead of update view?
  ;; Or just handle inside the view-component itself?
  (let [{:keys [loadable view]} (:data (<sub [:route/match]))]
    [wrap-page-height
     (cond
       loadable [wrap-check-session [lazy-component {:loadable loadable}]]
       view     [wrap-check-session [view]]
       :else    nil)]))

(defonce root (rdomc/create-root (js/document.getElementById "newApp")))

(defn ^:dev/after-load init-new-root-view!
  []
  (rdomc/render root [new-root-view]))
