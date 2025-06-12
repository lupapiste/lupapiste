(ns lupapalvelu.ui.admin.elasticsearch.core
  (:require [cljs.pprint :refer [pprint]]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.elasticsearch.dashboard-search-settings :as elastic-dashboard-search-settings]
            [lupapalvelu.ui.components :as ui]
            [lupapalvelu.ui.components.info-grid :as info-grid]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.tab-panel :as tab-panel]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn db->selected-index [db]
  (get-in db [::data :selected-index]))

;; Init

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:db (assoc db ::data {:visible? true})
       :fx [[:action/query {:name    :get-es-config
                            :success ::get-es-config.success}]
            [:dispatch [::elastic-dashboard-search-settings/init visible?]]]}
      {:db (dissoc db ::data)
       :fx [[:dispatch [::elastic-dashboard-search-settings/init visible?]]]})))

(rf/reg-event-db ::get-es-config.success
  (fn [db [_ {:keys [config]}]]
    (assoc-in db [::data :config] config)))

;; Fetch index data

(rf/reg-event-db ::get-es-index-info.success
  (fn [db [_ {:keys [data]}]]
    (let [selected-index (db->selected-index db)]
      (assoc-in db [::data selected-index :info] data))))

(rf/reg-event-fx ::select-index
  (fn [_ [_ index]]
    {:fx [[:action/query {:name    :get-es-index-info
                          :params  {:index (str index)}
                          :success ::get-es-index-info.success}]]}))

;; General Subs

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::config
  :<- [::data]
  :-> :config)

(rf/reg-sub ::elasticsearch-enabled
  :<- [::data]
  :-> (comp :enabled? :config))

(rf/reg-sub ::indices
  :<- [::config]
  :-> (util/fn->> :indices (mapv (comp keyword :name))))


;; Selected Index

(rf/reg-sub ::selected-index
  :<- [::data]
  :-> :selected-index)

(rf/reg-sub ::selected-index-data
  :<- [::data]
  (fn [data]
    (when-let [selected-index (some-> data :selected-index)]
      (some-> data selected-index))))

(rf/reg-sub ::selected-index-info
  :<- [::selected-index-data]
  :-> :info)

(rf/reg-event-fx ::set-es-index-aliases-to-current-version
  (fn [{:keys [db]} _]
    (let [index (db->selected-index db)]
      {:action/command {:name    :set-es-index-aliases-to-current-version
                        :params  {:index index}
                        :success #(>evt [::select-index index])}})))

(rf/reg-event-fx ::set-es-index-refresh-interval
  (fn [{:keys [db]} _]
    (let [index (db->selected-index db)]
      {:action/command {:name    :set-es-index-refresh-interval
                        :params  {:index index}
                        :success #(>evt [::select-index index])}})))

(rf/reg-event-fx ::full-index-update
  (fn [{:keys [db]} [_ force?]]
    (if force?
      {:action/command {:name   :full-es-index-update
                        :params {:index (db->selected-index db)}}}
      {:dialog/show {:ltitle   :areyousure
                     :text     "Haluatko varmasti indeksoida kaikki dokumentit?"
                     :type     :yes-no
                     :callback #(>evt [::full-index-update true])}})))

(rf/reg-event-fx ::es-index-cleanup
  (fn [{:keys [db]} [_ force?]]
    (if force?
      {:action/command {:name   :es-index-cleanup
                        :params {:index (db->selected-index db)}}}
      {:dialog/show {:ltitle   :areyousure
                     :text     "Haluatko varmasti suorittaa indeksin siivouksen?"
                     :type     :yes-no
                     :callback #(>evt [::es-index-cleanup true])}})))

;; Update by ID

(rf/reg-event-db ::set-update-by-id-text
  (fn [db [_ text]]
    (assoc-in db [::data (db->selected-index db) :update-by-id-text] text)))

(rf/reg-sub ::update-by-id-text
  :<- [::selected-index-data]
  :-> :update-by-id-text)

(rf/reg-event-fx ::update-by-id
  (fn [{:keys [db]} _]
    (let [selected-index (db->selected-index db)
          doc-id         (some-> db ::data selected-index :update-by-id-text ss/trim)]
      (when (ss/not-blank? doc-id)
        {:action/command {:name   :update-es-document-by-mongo-id
                          :params {:index  selected-index
                                   :doc-id doc-id}}}))))

;; Search by ID

(rf/reg-event-db ::set-id-search-text
  (fn [db [_ text]]
    (assoc-in db [::data (db->selected-index db) :id-search-text] text)))

(rf/reg-sub ::id-search-text
  :<- [::selected-index-data]
  :-> :id-search-text)

(rf/reg-event-fx ::id-search
  (fn [{:keys [db]} _]
    (let [selected-index (db->selected-index db)
          search-id      (some-> db ::data selected-index :id-search-text ss/trim)]
      (when (ss/not-blank? search-id)
        {:action/query {:name    :get-es-document-by-id
                        :params  {:index     selected-index
                                  :search-id search-id}
                        :success ::id-search.success}}))))

(rf/reg-event-db ::id-search.success
  (fn [db [_ {:keys [result error]}]]
    (assoc-in db [::data (db->selected-index db) :id-search-result] (or result error))))

(rf/reg-sub ::id-search-result
  :<- [::selected-index-data]
  :-> :id-search-result)

;; Components

(defn id-search []
  [:div.mt-8
   [:h3.no-twp "Etsi dokumentin ID:llä"]
   [:div.flex.gap-1
    (let [id-search-text (<sub [::id-search-text])]
      [:<>
       [ui-input/text-input
        {:class       ["no-twp" "textinput" "textinput--big" "border" "border-ui-gray-51" "w-150"]
         :placeholder "Dokumentin ID"
         :on-change   #(>evt [::set-id-search-text %])
         :on-key-down #(when (and (= ui/KEY-ENTER %)
                                  (ss/blank-as-nil id-search-text))
                         (>evt [::id-search]))
         :value       id-search-text}]
       [:button {:class    ["no-twp" "btn" "primary"]
                 :disabled (ss/blank? id-search-text)
                 :on-click #(>evt [::id-search])}
        [:i.lupicon-search]]])]
   (when-let [result (<sub [::id-search-result])]
     [:div.bg-ui-gray-238.p-2.rounded-sm.mt-2.max-w-full.overflow-x-auto
      [:pre (with-out-str (pprint result))]])])

(def old-docs-help-text "Sellaisten dokumenttien lukumäärä, joiden :doc-version on pienempi kuin indeksin ajantasainen versio.")

(defn aliases-view [aliases full-version]
  [:div [:pre (with-out-str (pprint aliases))]
   (when (or (not= (count aliases) 2)
             (seq (remove #(ss/ends-with? (:index %) (-> full-version :major str)) aliases)))
     [:div.flex.gap-2.mt-4
      [:button {:class    ["btn" "primary" "mt-2" "mb-2"]
                :on-click #(>evt [::set-es-index-aliases-to-current-version])}
       "Päivitä aliakset nykyiseen versioon"]
      [:div.italic "Jos päivityksen yhteydessä on luotu indeksille uusi versio ja sen sisällön generointi on vielä käynnissä,
                    on normaalia, että luku- ja kirjoitusaliakset osoittavat eri versioihin. Jos indeksin luonnin yhteydessä
                    tapahtuneen virheen seurauksena alias tai aliakset ovat jääneet päivittämättä, tämä muuttaa ne osoittamaan
                    konfiguraation mukaiseen versioon."]])])

(defn settings-view [settings]
  [:div [:pre (with-out-str (pprint settings))]
   (when (-> settings :refresh_interval (not= "1s"))
     [:div.flex.gap-2.mt-4
      [:button {:class    ["btn" "primary" "mt-2" "mb-2"]
                :on-click #(>evt [::set-es-index-refresh-interval])}
       "Aseta refresh interval 1 sekuntiin"]
      [:div.italic "Uuden indeksin luonnin yhteydessä asetetaan dokumenttien ensimmäisen indeksoinnin ajaksi
                    refresh_interval-asetuksen arvoksi -1. Jos indeksoinnin jälkeen refresh_interval on jäänyt automaattisesti
                    muuttamatta, tämä korjaa sen normaalikäytön aikaiseksi oletusarvoksi."]])])

(defn update-by-mongo-id []
  [:div.mt-8
   [:h3.no-twp "Päivitä Mongo-dokumentin ID:llä"]
   [:div.flex.gap-1
    (let [update-by-id-text (<sub [::update-by-id-text])]
      [:<>
       [ui-input/text-input
        {:class       ["no-twp" "textinput" "textinput--big" "border" "border-ui-gray-51" "w-150"]
         :placeholder "Mongo-dokumentin ID"
         :on-change   #(>evt [::set-update-by-id-text %])
         :on-key-down #(when (and (= ui/KEY-ENTER %)
                                  (ss/blank-as-nil update-by-id-text))
                         (>evt [::update-by-id]))
         :value       update-by-id-text}]
       [:button {:class    ["no-twp" "btn" "primary"]
                 :disabled (ss/blank? update-by-id-text)
                 :on-click #(>evt [::update-by-id])}
        "Päivitä"]])]])

(defn index-view []
  (let [{:keys [document-count old-document-count lupapiste-index-name aliases full-version settings]} (<sub [::selected-index-info])]
    [:<>
     [:div.mt-4
      [info-grid/info-grid [{:title "Indeksin nimi Lupapistessä" :content [:pre lupapiste-index-name]}
                            {:title "Versio" :content (->> full-version ((juxt :major :minor)) (ss/join "."))}
                            {:title "Aliakset" :content [aliases-view aliases full-version]}
                            {:title "Asetukset" :content [settings-view settings]}
                            {:title "Dokumenttien lukumäärä" :content document-count}
                            {:title [:span {:title old-docs-help-text} "Vanhoja dokumentteja"] :content (str old-document-count "")}]]
      [:div.flex.gap-2
       [:button {:class    ["no-twp" "btn" "primary" "mt-8"]
                 :on-click #(>evt [::full-index-update])}
        "Indeksoi kaikki dokumentit"]
       [:button {:class    ["no-twp" "btn" "primary" "mt-8"]
                 :on-click #(>evt [::es-index-cleanup])}
        "Indeksin siivous"]]

      [update-by-mongo-id]
      [id-search]]]))

(defn view []
  [:<>
   [:h1.mb-4 "Elasticsearch"]
   (if (<sub [::elasticsearch-enabled])
     [:<>
      [elastic-dashboard-search-settings/view]
      [:h2.mt-4.mb-4 "Indeksit"]
      (when-let [indices (seq (<sub [::indices]))]
        [tab-panel/tab-panel
         {:id                    "elasticsearch-indices-tabbar"
          :class                 "h-full"
          :tab-class             "h-full"
          :key->str              identity
          :select-first-on-load? false
          :data-path             [::data :selected-index]
          :tabs                  (mapv (fn [index] (hash-map :key index
                                                             :content [index-view]
                                                             :on-select-fn #(>evt [::select-index index])))
                                       indices)}])]
     [:p "Elasticsearch ei ole käytössä tässä ympäristössä"])])

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
