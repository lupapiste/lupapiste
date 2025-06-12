(ns lupapalvelu.ui.bulletins.bulletin-verdict-data
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.util :as ui-util]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn text-span [text markup?]
  (if markup?
    (components/markup-span text)
    [:pre.wrapped_text text]))

(defmulti verdict-data
  (fn [bulletin]
    (some-> bulletin :bulletinProcess keyword))
  :default :julkipano)

(defmethod verdict-data :julkipano
  [{:keys [markup?] :as bulletin}]
  (when bulletin
    [:div.container
     [:div.bulletin-tab-content
      [:h3
       [:span (common/loc :application.verdict.title)]]
      [:div.spacerL
       (when-let [vid (-> bulletin :verdicts first :kuntalupatunnus)]
         [:div.key-value-pair
          [:label (common/loc :verdict.id)]
          [:span.value vid]])
       (when-let [verdict-date (:verdictDate bulletin)]
         [:div.key-value-pair
          [:label (common/loc :bulletin.verdict-date)]
          [:span.value (common/format-timestamp verdict-date)]])
       [:div.key-value-pair
        [:label (common/loc :verdict.anto)]
        [:span.value (common/format-timestamp (:verdictGivenAt bulletin))]]
       [:div.key-value-pair
        [:label (common/loc :verdict.muutoksenhaku.paattyy)]
        [:span.value (common/format-timestamp (:appealPeriodEndsAt bulletin))]]]
      [:div.spacerL
       (-> bulletin :verdictData :text (text-span markup?))]]]))

(defmethod verdict-data :kuulutus
  [{:keys [markup?] :as bulletin}]
  (when bulletin
    (let [verdict-date (common/format-timestamp (:verdictDate bulletin))
          appeal-start (common/format-timestamp (:appealPeriodStartsAt bulletin))
          appeal-end   (common/format-timestamp (:appealPeriodEndsAt bulletin))]
      [:div.container
       [:div.bulletin-tab-content
        [:h3
         [:span (common/loc :application.verdict.title)]]
        [:div.spacerL
         (when-let [vid (-> bulletin :verdicts first :kuntalupatunnus)]
           [:div.key-value-pair
            [:label (common/loc :verdict.id)]
            [:span.value vid]])
         [:div.key-value-pair
          [:label (common/loc :bulletin.verdict-date)]
          [:span.value verdict-date]]
         [:div.key-value-pair
          [:label (common/loc :bulletin.kuulutus.julkaisu)]
          [:span.value appeal-start]]
         [:div.key-value-pair
          [:label (common/loc :verdict.muutoksenhaku.paattyy)]
          [:span.value appeal-end]]
         [:div.key-value-pair
          [:label (common/loc :bulletin.kuulutus.poistuu)]
          [:span.value appeal-end]]]
        [:div.spacerL
         [:p (common/loc :bulletin.kuulutus.tiedoksianto)]]
        [:div.spacerL
         (-> bulletin :verdictData :text (text-span markup?))]]])))

(defn attachment-table [{:keys [attachments] :as bulletin}]
  (when (seq attachments)
    [:div
     [:h4
      [:span (common/loc :application.attachments)]]
     [:table
      [:thead
       [:tr
        [:th (common/loc :application.attachmentType)]
        [:th (common/loc :application.attachmentFile)]]]
      (into [:tbody]
            (for [{:keys [type latestVersion]} attachments]
              [:tr
               [:td (common/loc (str "attachmentType." (:type-group type) "." (:type-id type)))]
               [:td [:a {:href (str "/api/raw/download-bulletin-attachment?bulletin-id="
                                    (:id bulletin)
                                    "&attachment-id="
                                    (:fileId latestVersion))}
                     (:filename latestVersion)]]]))]]))

(defmulti verdict-content
  (fn [bulletin _verdict _paatos]
    (some-> bulletin :bulletinProcess keyword))
  :default :julkipano)

(defmethod verdict-content :julkipano
  [bulletin verdict paatos]
  [:div.spacerL
   (when-let [vid (:kuntalupatunnus verdict)]
     [:div.key-value-pair
      [:label (common/loc :verdict.id)]
      [:span.value vid]])
   (when-let [verdict-date (:verdictDate bulletin)]
     [:div.key-value-pair
      [:label (common/loc :bulletin.verdict-date)]
      [:span.value (common/format-timestamp verdict-date)]])
   [:div.key-value-pair
    [:label (common/loc :verdict.anto)]
    [:span.value (common/format-timestamp (:verdictGivenAt bulletin))]]
   [:div.key-value-pair
    [:label (common/loc :verdict.muutoksenhaku.paattyy)]
    [:span.value (common/format-timestamp (:appealPeriodEndsAt bulletin))]]
   (when (:aloittamisoikeusVakuudella paatos)
     [:label (common/loc :pate-collateral.flag)])])

(defmethod verdict-content :kuulutus
  [bulletin verdict paatos]
  (let [verdict-date (common/format-timestamp (:verdictDate bulletin))
        appeal-start (common/format-timestamp (:appealPeriodStartsAt bulletin))
        appeal-end   (common/format-timestamp (:appealPeriodEndsAt bulletin))]
    [:<>
     [:div.spacerL
      (when-let [vid (:kuntalupatunnus verdict)]
        [:div.key-value-pair
         [:label (common/loc :verdict.id)]
         [:span.value vid]])
      [:div.key-value-pair
       [:label (common/loc :bulletin.verdict-date)]
       [:span.value verdict-date]]
      [:div.key-value-pair
       [:label (common/loc :bulletin.kuulutus.julkaisu)]
       [:span.value appeal-start]]
      [:div.key-value-pair
       [:label (common/loc :verdict.muutoksenhaku.paattyy)]
       [:span.value appeal-end]]
      [:div.key-value-pair
       [:label (common/loc :bulletin.kuulutus.poistuu)]
       [:span.value appeal-end]]
      (when (:aloittamisoikeusVakuudella paatos)
        [:label (common/loc :pate-collateral.flag)])]
     [:div.spacerL
      [:p (common/loc :bulletin.kuulutus.tiedoksianto)]]]))

(defn detailed-verdict-data [{:keys [markup?] :as bulletin}]
  (when-let [verdict (first (:verdicts bulletin))]
    [:div.container
     (for [paatos (:paatokset verdict)]
       ^{:key (ui-util/unique-elem-id "verdict-")}
       [:div.verdict-content
        [:h3
         [:span (common/loc :application.verdict.title)]]
        [verdict-content bulletin verdict paatos]
        (when (seq (:poytakirjat paatos))
          [:div.spacerL
           [:h4 (common/loc :verdict.poytakirjat)]
           [:div.accordion-content-part.spacerM
            [:table.table.table-striped.tablesorter
             [:thead
              [:tr
               [:th {:colSpan 2} (common/loc :verdict.status)]
               [:th (common/loc :verdict.pykala)]
               [:th (common/loc :verdict.name)]
               [:th (common/loc :verdict.paatospvm)]]]
             [:tbody
              (for [[idx pk] (util/indexed (:poytakirjat paatos))]
                ^{:key (str "paatospk-" idx)}
                [:tr
                 [:td (when (:status pk)
                        (common/loc (str "verdict.status." (:status pk))))
                  (when (and (not (:status pk)) (:paatoskoodi pk))
                    (:paatoskoodi pk))]
                 [:td.verdict-text (text-span (:paatos pk) markup?)]
                 [:td (:pykala pk)]
                 [:td (:paatoksentekija pk)]
                 [:td (common/format-timestamp (:paatospvm pk))]])]]]])
        [attachment-table bulletin]])]))

(defn vetuma-params [return-url]
  {:success return-url
   :cancel  (str return-url "/cancel")
   :error   (str return-url "/error")
   :y       return-url
   :vtj     return-url})

(defn doc-download-link
  "Button to download the bulletin's verdict document.
  Document itself is a censored verdict poytakirja, either generated by Lupapiste or uploaded by authority"
  [{:keys [id attachment-id bulletinProcess]}]
  (when (= "kuulutus" bulletinProcess)
    [:div.container.my-2
     [:div.bulletin-tab-content
      (if attachment-id
        [:a.btn.btn-primary
         {:id   "doc-download-link"
          :href (str "/api/raw/download-bulletin-doc?bulletinId=" id)}
         (common/loc :bulletin.doc.download)]
        [:span (common/loc :bulletin.doc.empty)])]]))

(defn identification-link []
  (let [pathname   (aget js/window.location "pathname")
        search     (aget js/window.location "search")
        hash       (aget js/window.location "hash")
        return-url (str pathname search hash)
        params     (vetuma-params return-url)
        action-url (str js/LUPAPISTE.config.identMethods.loginUrl "?"
                        (ss/join "&" (map (fn [[k v]]
                                            (str (name k) "=" (js/encodeURIComponent v)))
                                          params)))]
    [:div.container
     [:p
      [:b (common/loc :bulletins.proceed-to-appeal.info)]]
     [:a.btn.btn-primary
      {:id   "vetuma-link"
       :href action-url}
      (common/loc :register.action)]]))

