(ns lupapalvelu.ui.bulletins.local-bulletins
  (:require [lupapalvelu.ui.bulletins.state :as state]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.dom :as ui-dom]
            [rum.core :as rum]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [goog Uri]))

(defonce args (atom {}))

(defn- make-search-options
  []
  (let [process @state/local-bulletins-process]
    {:organization    @state/current-organization
     :searchText      ""
     :sort            {:field (if (= :kuulutus process)
                                "appealPeriodStartsAt+section"
                                "verdictGivenAt")}
     :page            (:page @state/local-bulletins-query)
     :bulletinProcess process}))

(defn- load-initial-data!
  []
  (common/query :local-application-bulletins
                (fn [{:keys [data left]}]
                  (reset! state/local-bulletins data)
                  (swap! state/local-bulletins-query assoc :left left))
                (make-search-options)))

(defn init [init-state _]
  (reset! state/current-organization (:organization @args))
  (reset! state/local-bulletins-process (:bulletinProcess @args))
  (reset! state/local-bulletins-query {:page 1 :left -1 :pending? false})
  (common/query :local-bulletins-page-settings
                (fn [{:keys [enabled texts]}]
                  (when-not enabled
                    (set! js/window.location "/"))
                  (reset! state/local-bulletins-page-settings {:texts texts}))
                :organization @state/current-organization)
  (common/query :local-kuulutus-page-settings
                (fn [{:keys [enabled texts]}]
                  (when-not enabled
                    (set! js/window.location "/"))
                  (reset! state/local-kuulutus-page-settings {:texts texts}))
                :organization @state/current-organization)
  (load-initial-data!)
  init-state)

(defn load-more-bulletins []
  (swap! state/local-bulletins-query update :page inc)
  (swap! state/local-bulletins-query assoc :pending? true)
  (common/query :local-application-bulletins
                (fn [{:keys [data left]}]
                  (swap! state/local-bulletins concat data)
                  (swap! state/local-bulletins-query assoc :pending? false :left left))
                (make-search-options)))

(defn open-bulletin [id]
  (js/pageutil.openPage "r-bulletin" id))

(defn handle-key-up [id]
  (fn [e]
    (when (= (.-key e) "Enter")
      (open-bulletin id))))

(rum/defc bulletins-table < rum/reactive
  ;; Note: the columns are the same as `lupapalvelu.application-bulletins/bulletin-rows`
  []
  (let [bulletins (rum/react state/local-bulletins)
        process   (rum/react state/local-bulletins-process)]
    [:table#application-bulletins-list
     [:thead
      [:tr
       [:th (common/loc :bulletin.verdict-section)]
       [:th (common/loc :bulletin.lupatunnus)]
       [:th (common/loc :bulletin.building-site)]
       [:th (common/loc :bulletin.municipality)]
       [:th (common/loc :bulletin.type)]
       [:th (common/loc :bulletin.verdict-giver)]
       ;; Show `antopäivämäärä` for julkipano but appeal period start date for kuulutus
       [:th (common/loc (if (= :kuulutus process)
                          :bulletin.versions.published
                          :bulletin.verdict.verdictGivenAt))]
       [:th (common/loc :bulletin.rectification-period-ends)]]]
     [:tbody
      (for [{:keys [id address application-id verdictGivenAt
                    appealPeriodStartsAt appealPeriodEndsAt
                    bulletinOpDescription category municipality]
             {:keys [code status section contact]}
             :verdictData} bulletins]
        [:tr
         {:key       id
          :on-click  #(open-bulletin id)
          :on-key-up (handle-key-up id)
          :tabIndex  0}
         [:td (str section " "
                   (cond
                     (and category code)
                     (common/loc (str "pate.verdict-code." code))

                     status
                     (common/loc (str "verdict.status." status))))]
         [:td (or application-id id)]
         [:td address]
         [:td (common/loc (str "municipality." municipality))]
         [:td bulletinOpDescription]
         [:td contact]
         [:td (common/format-timestamp (if (= :kuulutus process)
                                         appealPeriodStartsAt
                                         verdictGivenAt))]
         [:td (common/format-timestamp appealPeriodEndsAt)]])]]))

(rum/defc load-more-button < rum/reactive
  []
  (let [{:keys [left pending?]} (rum/react state/local-bulletins-query)]
    (when (nat-int? left)
      [:button.bulletin.bg--violet
       {:on-click load-more-bulletins
        :class [(when pending? "waiting")]}
       [:i.lupicon-circle-plus]
       [:i.wait.spin.lupicon-refresh]
       [:span (common/loc :bulletin.search.button)]
       [:span (str left " " (common/loc :unit.kpl))]])))

(rum/defc heading < rum/reactive
  []
  (let [lang            (common/get-current-language)
        process         (rum/react state/local-bulletins-process)
        texts-julkipano (get-in (rum/react state/local-bulletins-page-settings) [:texts (keyword lang)])
        texts-kuulutus  (get-in (rum/react state/local-kuulutus-page-settings) [:texts (keyword lang)])
        ;; If authority admin has not specified kuulutus texts, show julkipano texts
        texts-kuulutus  (if (ss/not-blank? (:heading1 texts-kuulutus))
                          texts-kuulutus
                          texts-julkipano)
        texts           (if (= :kuulutus process)
                          texts-kuulutus
                          texts-julkipano)]
    [:div
     [:div.full.content.bg--white
      [:div.content-center
       ;; NB: These will be read from db, no need now for localization
       [:h1.slogan.municipal-heading (:heading1 texts)]
       [:h2.slogan.municipal-heading (:heading2 texts)]]]
     [:div.full.content
      [:div.content-center.municipal-caption.ws--pre-wrap
       (for [[idx paragraph] (util/indexed (:caption texts))]
         [:p {:key idx} paragraph])]]]))

(defn- build-process-url
  "Takes the current URL and updates the `bulletinProcess`"
  [process]
  (-> js/window
      .-location
      .toString
      Uri/parse
      (.setParameterValue "bulletinProcess" (name process))
      .toString))

(rum/defc process-selector < rum/reactive
  [target-process label-kw]
  (let [process (rum/react state/local-bulletins-process)]
    [:button
     {:class    (if (= process target-process)
                  ["bg-violet-900" "text-white"]
                  "bg-gray-200")
      :on-click (fn []
                  ;; To make sure the URL is up to date, refresh the whole page
                  ;; Otherwise navigating to a bulletin and back might return
                  ;; the user to a different process view
                  (.open js/window (build-process-url target-process) "_self"))}
     (common/loc label-kw)]))

(defn extract-lang-from-url [url]
 (let [pattern #"/app/([^/]+)/"]
   (second (re-find pattern (.-pathname url)))))

(defn build-rss-feed-url []
  (let [url     (js/URL. (-> js/window .-location .toString))
        host    (.-origin url)
        lang    (extract-lang-from-url url)
        process (rum/react state/local-bulletins-process)
        org     (rum/react state/current-organization)]
    (-> (Uri/parse (str host "/rss/" (name process)))
        (.setParameterValue "organization" org)
        (.setParameterValue "lang" lang)
        .toString)))

(rum/defc rss-feed-button < rum/reactive
  []
  [:a {:href (build-rss-feed-url)}
   [:img.w-10 {:src "/lp-static/img/rss-feed-icon.png"}]])


(rum/defc process-selectors < rum/reactive
  []
  [:div.flex.justify-between
   [:div.flex.mb-2.border-0.border-b.border-gray-500.border-solid
    (process-selector :julkipano :bulletins)
    (process-selector :kuulutus :kuulutukset)]
   (rss-feed-button)])

(rum/defc local-bulletins < {:init init}
  []
  [:div
   (heading)
   [:div.full.content
    [:div.content-center
     (process-selectors)
     (bulletins-table)]
    [:div.search-button-wrapper
     (load-more-button)]]])

(defonce mounter (ui-dom/init :rum #(local-bulletins)))

(defn ^:export start [domId componentParams]
  (reset! args {:organization    (aget componentParams "organization")
                :bulletinProcess (keyword (aget componentParams "bulletinProcess"))})
  (ui-dom/mount! mounter domId))
