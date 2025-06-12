(ns lupapalvelu.dev.impl
  (:require [lupapalvelu.ui.enabled-state :as cljs-docgen-enabled-state]
            [lupapalvelu.ui.topbar-state :as topbar-state]
            re-frame.db
            [reagent-dev-tools.core :as rdt]
            [reagent-dev-tools.state :as rdts]
            [reagent-dev-tools.state-tree :as rdtst]
            [reagent.core :as r]
            [reagent.ratom :as ra]
            [sade.strings :as ss]))

(defn toggle [{:keys [name label callback checked]}]
  (let [;; same ids as the debug.js for now
        name (str "cljs-" name)]
    [:<>
     [:input
      {:id        name
       :type      "checkbox"
       :checked   checked
       :on-change (fn [e] (callback (.. e -target -checked)))}]
     [:label
      {:for name}
      label]
     [:br]]))

(def apply-done (r/atom false))

(defn apply-fixture [fixture]
  (-> (.query js/lpAjax "apply-fixture" #js {:name fixture})
      (.success (fn []
                  ;; TODO: Show notification for 100ms
                  ))
      (.error (fn [e] (js/alert (.-text e))))
      (.call)))

(defn create-application [operation permit-type app-id-callback]
  (let [municipality (if (.. js/lupapisteApp -models -currentUser isAuthority)
                       (let [probable-org       (str "753-" permit-type)
                             org-auth-muni-keys (.keys js/_ (.. js/lupapisteApp -models -currentUser orgAuthz))
                             org                (if (.includes js/_ org-auth-muni-keys probable-org)
                                                  probable-org
                                                  (first org-auth-muni-keys))]
                         (first (ss/split org #"-")))
                       "753")]
    (-> (.ajax js/$ #js {:url     "/dev/create"
                         :data    #js {:address    "Latokuva 3"
                                       :propertyId (str municipality "-416-55-7")
                                       :operation  operation
                                       :x          "404369.304000"
                                       :y          "6693806.957000"}
                         :success (fn [id]
                                    (when app-id-callback
                                      (app-id-callback id))
                                    ;; TODO: Show notification for 100ms
                                    )}))))

(defn create-application-and-publish-bulletin [count]
  (-> (.ajax js/$ #js {:url     "/dev/publish-bulletin-quickly"
                       :data    #js {:count (or count 1)}
                       :success (fn []
                                  ;; TODO: Show notification for 100ms
                                  )})))

(defonce throttles (r/atom {:web 0
                            :db  0}))

;; NOTE: js/ajax isn't available on release bundle on the dynamic module load time?
;; or something like that. Delaying the call until panel render seems to work?
(defonce get-throttles
  (delay (-> (.get js/lpAjax (str (.-protocol js/location)
                                  "//"
                                  (.-host js/location)
                                  "/perfmon/throttle"))
             (.success (fn [^js data]
                         (reset! throttles {:web (.-web data)
                                            :db  (.-db data)})))
             (.call))))

(defonce proxy-ctrl (r/atom false))
(defonce maps-disabled (r/atom false))

(defonce get-proxy-ctrl
  (delay (-> (.get js/lpAjax "/api/proxy-ctrl")
             (.success (fn [^js data]
                         (reset! proxy-ctrl (.-data data))
                         (reset! maps-disabled (.enabled js/features "maps-disabled"))))
             (.call))))

(defn throttle [type value]
  (-> (.post js/lpAjax (str (.-protocol js/location)
                            "//"
                            (.-host js/location)
                            "/perfmon/throttle/" (name type)))
      (.raw)
      (.json #js {:value value})
      (.header "npm" "true")
      (.success (fn []
                  (swap! throttles assoc type value)))
      (.fail (fn [] nil))
      (.call)))

(defn link-button [label callback]
  [:<>
   [:a
    {:href     "#"
     :on-click (fn [e]
                 (.preventDefault e)
                 (callback))}
    label]
   [:br]])

(defn debug-panel []
  @get-throttles
  @get-proxy-ctrl
  [:div
   [:span.group-title "Development"]
   [:div
    [toggle
     {:name     "debug-tab-flow"
      :label    "Flowing tabs"
      :callback (fn [enabled] (.send js/hub "set-debug-tag-flow" #js {:value enabled}))}]

    [toggle
     {:name     "debug-toggle-hidden"
      :label    "Toggle hidden"
      :callback (fn [_enabled] (.toggleClass (js/$ ".page") "visible"))}]

    [toggle
     {:name     "debugProxy"
      :label    "Proxy enabled"
      :callback (fn [enabled]
                  (-> (.post js/lpAjax (str "/api/proxy-ctrl/" (if enabled "on" "off")))
                      (.call)))}]

    [toggle
     {:name     "debugMaps"
      :label    "Disable maps"
      :checked  (boolean @maps-disabled)
      :callback (fn [enabled]
                  (-> (.command js/lpAjax "set-feature" #js {:feature "maps-disabled" :value enabled})
                      (.call)))}]

    [toggle
     {:name     "debug-hub"
      :label    "Log events"
      :callback (fn [enabled] (.setDebug js/hub enabled))}]

    [toggle
     {:name     "docgenCljs"
      :label    [:<> "CLJS Docgen (feature flag: " (str cljs-docgen-enabled-state/feature-flag) ")"]
      :checked  (boolean @cljs-docgen-enabled-state/state)
      :callback (fn [enabled] (cljs-docgen-enabled-state/toggle enabled))}]

    [toggle
     {:name     "newTopbar"
      :label    [:<> "New topbar (feature flag: " (str topbar-state/feature-flag) ")"]
      :checked  (boolean @topbar-state/state)
      :callback (fn [enabled] (topbar-state/toggle enabled))}]

    [:a
     {:href   "/api/last-email"
      :target "_blank"}
     "Last email"]
    " - "
    [:a
     {:href   "/api/last-emails"
      :target "_blank"}
     "All"]
    [:br]

    [:a {:href     "#"
         :on-click (fn [_e] (apply-fixture "minimal"))}
     "Apply minimal"]

    [:p
     [:a {:href   "/app/fi/bulletins#!/bulletins"
          :target "_blank"}
      "Julkipano"]]

    [:p "Create:" [:br]
     [link-button "R/asuinkerrostalo" (fn [] (create-application "kerrostalo-rivitalo" "R" nil))]
     [link-button "R/sisatilojen muutos" (fn [] (create-application "sisatila-muutos" "R" nil))]
     [link-button "YA/katulupa" (fn [] (create-application "ya-katulupa-vesi-ja-viemarityot" "YA" nil))]
     [link-button "A/promootio" (fn [] (create-application "promootio" "A" nil))]
     [link-button "A/lyhytaikainen maanvuokraus" (fn [] (create-application "lyhytaikainen-maanvuokraus" "A" nil))]
     [link-button "KT/kiinteistonmuodostus" (fn [] (create-application "kiinteistonmuodostus" "R" nil))]
     [link-button "VVVL/Vapautus viemäristä" (fn [] (create-application "vvvl-viemarista" "VVVL" nil))]]

    [:p "Create and publish in julkipano.fi (Oulu):" [:br]
     [link-button "YM/lannan-varastointi" (fn [] (create-application-and-publish-bulletin nil))]
     [link-button "YM/lannan-varastointi × 5" (fn [] (create-application-and-publish-bulletin 5))]]

    [:span "Throttle web: "
     [:b (:web @throttles)] [:br]
     [:input {:type       "range"
              :value      (or (:web @throttles) 0)
              :min        0
              :max        2000
              :step       10
              :aria-label "Throttle web"
              :on-change  (fn [e] (throttle :web (.. e -target -value)))}]]
    [:br]

    [:span "Throttle DB: "
     [:b (:db @throttles)] [:br]
     [:input {:type       "range"
              :value      (or (:db @throttles) 0)
              :min        0
              :max        2000
              :step       10
              :aria-label "Throttle DB"
              :on-change  (fn [e] (throttle :db (.. e -target -value)))}]]]])

(defonce navbar-margin
  ;; navbar doesn't follow body margin
  (delay
    (let [nav-el     (first (.getElementsByClassName js/document "nav-wrapper"))
          new-nav-el (fn [] (first (.querySelectorAll js/document "#newApp nav")))]
      (ra/run!
        (let [{:keys [open? place width]} @rdts/dev-state
              panel-right                 (if (and open? (= :right place))
                                            (str width "px")
                                            nil)]
          ;; Legacy nav
          (set! (.. nav-el -style -left) (if panel-right "0" nil))
          (set! (.. nav-el -style -width) (if panel-right "auto" nil))
          (set! (.. nav-el -style -right) panel-right)
          ;; New topbar — shrink its right edge instead of hiding behind the panel
          (when-let [el (new-nav-el)]
            (set! (.. el -style -right) (or panel-right "0"))))))))

(defn info-handler [type-name]
  (fn [x]
    [:span {:title type-name}
     "{"
     (count x) " keys"
     "}"
     (when-let [s (:schema-info x)]
       [:span " schema: " (:name s)])]))

(defn start! []
  ;; NOTE: Is this slow? Probably not much slower than the built-in handler, only difference
  ;; is the schema-info check.
  (rdtst/register-collection-info-handler PersistentHashMap (info-handler "PersistentHashMap"))
  (rdtst/register-collection-info-handler PersistentArrayMap (info-handler "PersistentArrayMap"))

  ;; NOTE: we could implement moveable :toggle-btn, if the current btn does go over something important
  (rdt/start!
    {:margin-element (.-body js/document)
     :state-atom     re-frame.db/app-db
     :panels         [{:key   :lupapiste-debug
                       :label "Debug"
                       :view  [debug-panel]}]})
  (deref navbar-margin))
