(ns lupapalvelu.ui2.topbar
  "Reusable top bar component. Props-driven, no global state.

  [topbar {:user        {:name \"Pena Panaani\" :org-name \"Jyväskylä\"}
           :apps        [{:label \"Dokumentit\" :icon IconArchive :href \"...\"}]
           :on-logout   my-logout-fn
           :user-items  [{:label \"Omat tiedot\" :icon IconUser :href \"#!/mypage\"}]}]"
  (:require ["@carbon/icons-react/es/Apps$default" :as IconApps]
            ["@carbon/icons-react/es/ChevronDown$default" :as IconChevronDown]
            ["@carbon/icons-react/es/Logout$default" :as IconLogout]
            ["@carbon/icons-react/es/User$default" :as IconUser]
            ["react" :as react]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]
            [reagent.core :refer [defc]]
            [reagent.hooks :as hooks]))

(def classes
  (builder
    {:base     {:root                     ["flex" "items-center"
                                           "bg-header" "text-white"
                                           "border-b" "border-header-border"
                                           "fixed" "top-0" "left-0" "right-0" "z-[2020]"]
                :inner                    ["max-w-7xl" "mx-auto" "w-full"
                                           "px-4" "sm:px-6" "lg:px-8" "py-3"
                                           "flex" "items-center" "justify-between"]
                :dropdown                 ["absolute" "top-full" "mt-1"
                                           "bg-surface" "text-text"
                                           "rounded-md" "shadow-lg"
                                           "border" "border-border"
                                           "min-w-[200px]"
                                           "list-none" "m-0" "p-1"]
                :menu-item                ["flex" "items-center" "gap-3"
                                           "px-3" "py-2"
                                           "w-full" "text-left" "whitespace-nowrap"
                                           "text-sm" "font-medium"
                                           "hover:bg-surface-muted"
                                           "rounded-sm"
                                           "cursor-pointer" "no-underline" "text-text"]
                :trigger                  ["flex" "items-center" "gap-2"
                                           "bg-transparent"
                                           "border-2" "border-transparent"
                                           "hover:border-brand-teal"
                                           "hover:bg-header-menu-hover"
                                           "px-3" "py-2"
                                           "rounded-md"
                                           "text-sm" "font-medium"
                                           "text-white" "cursor-pointer"
                                           "transition-all"]
                :user-avatar              ["w-6" "h-6"
                                           "bg-white" "rounded-full"
                                           "flex" "items-center" "justify-center"
                                           "shrink-0"]
                :separator                ["border-t" "border-border" "my-1"]
                :dropdown-header          ["px-4" "py-3"
                                           "border-b" "border-border"
                                           "mb-1"]
                :dropdown-header-title    ["text-sm" "font-semibold" "text-text"]
                :dropdown-header-subtitle ["text-xs" "text-text-muted" "mt-0.5"]}
     :variants {:align {:left  {:dropdown ["left-0"]}
                        :right {:dropdown ["right-0"]}}}}))

;; TODO: extract dropdown into separate component if it gets reused elsewhere
(defc dropdown-item
  [{:keys [label icon href on-click disabled? separator-before? close-menu!]}]
  [:<>
   (when separator-before?
     [:li [:hr (merge-props-classes {} (classes {} :separator))]])
   [:li {:role "menuitem"}
    (let [cls      (str (classes {} :menu-item) (when disabled? " opacity-40 pointer-events-none"))
          handlers (cond-> {}
                     (and on-click (not disabled?))       (assoc :on-click (fn [e] (.preventDefault e) (on-click e) (close-menu!)))
                     (and (not on-click) (not disabled?)) (assoc :on-click (fn [_] (close-menu!)))
                     disabled?                            (assoc :aria-disabled "true"))]
      (if href
        [:a (merge {:href href :class cls} handlers)
         (when icon [:> icon {:size 16 :aria-hidden "true"}])
         [:span label]]
        [:button (merge {:type "button" :class cls} handlers)
         (when icon [:> icon {:size 16 :aria-hidden "true"}])
         [:span label]]))]])

(defc dropdown-menu
  [{:keys [trigger-content items aria-label align header]}]
  (let [[open? set-open!] (react/useState false)
        close!            (react/useCallback #(set-open! false) #js [set-open!])
        container-ref     (react/useRef nil)]
    ;; While the dropdown is open, attach a document-level mousedown listener so that
    ;; clicking anywhere outside the dropdown closes it. The listener is removed when
    ;; the dropdown closes or the component unmounts (reagent.hooks handles nil return
    ;; when the dropdown is closed and no listener is needed).
    ;; We use capture phase (true) so the handler fires before any bubble-phase handlers
    ;; in the tree. The contains check skips clicks that land inside the dropdown itself
    ;; — without it, any click inside would also trigger the document handler and close
    ;; the menu immediately.
    (hooks/use-effect
      (fn []
        (when open?
          (let [handler (fn [e]
                          (when (and (.-current ^js container-ref)
                                     (not (.contains (.-current ^js container-ref) (.-target e))))
                            (close!)))]
            (.addEventListener js/document "mousedown" handler true)
            (fn cleanup [] (.removeEventListener js/document "mousedown" handler true)))))
      #js [open?])
    [:div {:class "relative" :ref container-ref}
     [:button (merge-props-classes
                {:type          "button"
                 :aria-haspopup "true"
                 :aria-expanded (str open?)
                 :aria-label    aria-label
                 :on-click      (fn [e] (.stopPropagation e) (set-open! (not open?)))}
                (classes {} :trigger))
      trigger-content]
     (when open?
       [:ul (merge-props-classes {:role "menu"} (classes {:align (or align :right)} :dropdown))
        (when header
          [:li {:role "presentation"}
           [:div (merge-props-classes {} (classes {} :dropdown-header))
            [:div (merge-props-classes {} (classes {} :dropdown-header-title)) (:title header)]
            (when (:subtitle header)
              [:div (merge-props-classes {} (classes {} :dropdown-header-subtitle)) (:subtitle header)])]])
        (map (fn [item]
               ^{:key (str (:key item) "|" (:label item) "|" (:href item))}
               [dropdown-item (assoc item :close-menu! close!)])
             items)])]))

(defn topbar
  "Props:
    :logo-href      – href for the logo (default \"#\")
    :logo-src       – src for the logo image (default \"/lp-static/img/lupapiste-logo.svg\")
    :on-logo-click  – fn (optional)
    :user           – {:name \"...\" :org-name \"...\" :role \"...\"}
    :apps           – seq of {:key (required) :label :icon :href :on-click :disabled? :separator-before?}
    :apps-label            – label for apps trigger button and dropdown header title
    :apps-subtitle         – subtitle shown in the apps dropdown header
    :user-items     – same shape as apps (with :key required)
    :on-logout      – fn
    :logout-label   – label for the logout item (default: (loc \"logout\"))
    :aria-label     – overrides \"Päänavigaatio\"

  Note: the interface uses separate named props (:apps, :user-items, :on-logout etc.) rather than
  a single generic :items vector. This keeps the caller's intent explicit and avoids leaking
  Lupapiste-specific concepts (app switcher vs. user menu) into a single opaque list.
  The trade-off is a slightly wider prop surface, which is acceptable as this component is
  only used in one place (topbar-connected)."
  [{:keys [logo-href logo-src on-logo-click user apps apps-label apps-subtitle user-items on-logout logout-label aria-label]}]
  (let [user-name       (:name user)
        user-role       (or (:role user) (:org-name user))
        apps-btn-label  (or apps-label (loc "topbar.apps.title"))
        apps-hdr-sub    (or apps-subtitle (loc "topbar.apps.subtitle"))
        full-user-items (into (vec user-items)
                              [{:key               "logout"
                                :label             (or logout-label (loc "logout"))
                                :icon              IconLogout
                                :separator-before? (boolean (seq user-items))
                                :on-click          on-logout}])]
    [:nav (merge-props-classes {:aria-label (or aria-label (loc "topbar.nav.aria-label"))} (classes {} :root))
     [:div (merge-props-classes {} (classes {} :inner))
      ;; Logo
      [:a {:href       (or logo-href "#")
           :aria-label "Lupapiste"
           :on-click   on-logo-click
           :class      "flex items-center"}
       [:img {:src (or logo-src "/lp-static/img/lupapiste-logo.svg") :alt "Lupapiste" :class "h-[35px] brightness-0 invert"}]]

      ;; Right-side controls
      [:div.flex.items-center.gap-3
       (when (seq apps)
         [dropdown-menu {:aria-label      apps-btn-label
                         :align           :left
                         :header          {:title    apps-btn-label
                                           :subtitle apps-hdr-sub}
                         :trigger-content [:<>
                                           [:> IconApps {:size 16 :aria-hidden "true"}]
                                           [:span apps-btn-label]
                                           [:> IconChevronDown {:size 16 :aria-hidden "true"}]]
                         :items           apps}])
       (when user-name
         [dropdown-menu {:aria-label      user-name
                         :header          {:title    user-name
                                           :subtitle user-role}
                         :trigger-content [:<>
                                           [:div (merge-props-classes {} (classes {} :user-avatar))
                                            [:> IconUser {:size        16
                                                          :aria-hidden "true"
                                                          :class       "text-header"}]]
                                           [:span user-name]
                                           [:> IconChevronDown {:size 16 :aria-hidden "true"}]]
                         :items           full-user-items}])]]]))
