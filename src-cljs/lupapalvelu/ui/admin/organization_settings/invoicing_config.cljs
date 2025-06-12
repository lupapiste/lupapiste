(ns lupapalvelu.ui.admin.organization-settings.invoicing-config
  "Lupapiste admin UI for invoice integration configuration"
  (:require [lupapalvelu.invoices.shared.schemas :as invoice-schemas]
            [lupapalvelu.invoices.shared.util :as invoices-shared-util]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as ui]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn fetch-config-query [db]
  (let [organizationId (router/path-parameter db :org-id)]
    {:fx [[:action/query {:name    :invoicing-config
                          :params  (util/mmap organizationId)
                          :success [::invoicing-config.success]}]]}))

(defn on-visible
  [{:keys [db]} _]
  (assoc (fetch-config-query db) :db (assoc db ::visible? true)))

(defn on-hidden
  [{:keys [db]} _]
  {:db (dissoc db ::data ::visible?)})

(rf/reg-event-db ::invoicing-config.success
  (fn [db [_ {:keys [invoicing-config _backend-id-config]}]]
    (assoc db ::data (util/mmap {:invoicing-config-edit invoicing-config} invoicing-config))))

(rf/reg-sub ::visible? :-> ::visible?)

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::original-config-data
  :<- [::data]
  :-> :invoicing-config)

(rf/reg-sub ::edited-config-data
  :<- [::data]
  :-> :invoicing-config-edit)

(rf/reg-sub ::changes?
  :<- [::original-config-data]
  :<- [::edited-config-data]
  :-> (partial apply not=))

(rf/reg-sub ::get-value
  :<- [::edited-config-data]
  (fn [data [_ field]]
    (get-in data field)))

(rf/reg-event-db ::set-value
  (fn [db [_ field value]]
    (assoc-in db (concat [::data :invoicing-config-edit] field) value)))

(rf/reg-event-db ::cancel-invoicing-config-changes
  (fn [db _]
    (assoc-in db
              [::data :invoicing-config-edit]
              (get-in db [::data :invoicing-config]))))

(rf/reg-event-fx ::update-invoicing-config.success
  (fn [{:keys [db]} _]
    (fetch-config-query db)))

(rf/reg-sub ::external-sftp?
  :<- [::edited-config-data]
  :-> (comp #{:sftp-ssh :sftp-password} invoices-shared-util/determine-delivery-type))

(rf/reg-event-fx ::save-invoicing-config-changes
  (fn [{:keys [db]} _]
    (let [organizationId (router/path-parameter db :org-id)
          cfg            (some-> db ::data :invoicing-config-edit)
          cfg            (assoc cfg :delivery-type (invoices-shared-util/determine-delivery-type cfg))]
      (if-not (invoices-shared-util/validate-config-credentials cfg)
        {:dialog/show {:title "Täytä pakolliset kentät"
                       :text  "SFTP-yhteyden muodostamiseksi on annettava käyttäjätunnus sekä määritettävä autentikointitapa: joko SSH-avaimella tai salasanalla."}}
        {:action/command {:name                  :update-invoicing-config
                          :success               ::update-invoicing-config.success
                          :show-saved-indicator? true
                          :params                {:org-id           organizationId
                                                  :invoicing-config (get-in db [::data :invoicing-config-edit])}}}))))

(rf/reg-event-fx ::test-invoicing-sftp
  (fn [{:keys [db]} _]
    {:action/command {:name    ::test-invoicing-sftp
                      :success ::test-invoicing-sftp.success
                      :params  {:org-id (router/path-parameter db :org-id)}}}))

(rf/reg-event-db ::test-invoicing-sftp.success
  (fn [db [_ result]]
    (assoc-in db
              [::data :sftp-test-result]
              (update result :logs (partial mapv #(update % 0 js/util.finnishDateAndTime))))))

(rf/reg-sub ::sftp-test-result
  :<- [::data]
  (fn [{:keys [sftp-test-result]} _]
    (when (:ok sftp-test-result)
      sftp-test-result)))

(rf/reg-sub ::invoicing-system
  :<- [::edited-config-data]
  :-> :invoicing-system)

(rf/reg-sub ::show-file-prefix?
  :<- [::invoicing-system]
  (fn [invoicing-system] (not= "sap-idoc" invoicing-system)))

(rf/reg-sub ::show-constants?
  :<- [::invoicing-system]
  (fn [invoicing-system] (not= "sap-idoc" invoicing-system)))

;; COMPONENTS

(defn input-row [text field & [additional-text]]
  [:tr
   [:td text]
   [:td [ui-input/text-input
         {:class     :form-input
          :on-change #(>evt [::set-value field %])
          :value     (<sub [::get-value field])}]
    additional-text]])

(defn checkbox-row [text field & [additional-text]]
  [:tr
   [:td text]
   [:td [ui-input/checkbox
         {:id        (str "checkbox-" (->> field (map name) (ss/join "-")))
          :checked?  (boolean (<sub [::get-value field]))
          :on-change #(>evt [::set-value field %])}]
    additional-text]])

(defn invoicing-system-row
  []
  (let [field [:invoicing-system]]
    [:tr
     [:td (common/loc :organization.invoicing-system)]
     [:td [ui/select
           (or (<sub [::get-value field]) "")
           (->> invoice-schemas/invoicing-systems
                (mapv (fn [system]
                        [system (common/loc (util/kw-path :invoicing-system system))])))
           {:callback (fn [v] (>evt [::set-value field v]))}]]]))

(defn connection-config []
  (let [local-sftp? (<sub [::get-value [:local-sftp?]])]
    [:<>
     [:h3 "Yhteysasetukset"]
     [:table
      [:thead [:tr [:th "Asetus"] [:th "Arvo"]]]
      [:tbody
       [invoicing-system-row]
       [checkbox-row "Käytä Lupapisteen SFTP-palvelinta" [:local-sftp?]]
       (when-not local-sftp?
         [:<>
          [input-row "URL" [:integration-url]]
          (when (<sub [::external-sftp?])
            [checkbox-row
             [:span "Käytä SSH-avainta autentikoitumiseen" [:br]
              [:div.pate-note [:i.lupicon-circle-info.mr-1.text-base-lg]
               "SSH-avaimella autentikoituminen vaatii, että" [:br]
               "vastaanottavalle palvelimelle on konfiguroitu" [:br]
               "Lupapisteen SSH-avaimen julkinen osa."]]
             [:ssh-authentication?]])
          [input-row "Käyttäjätunnus" [:credentials :username]]
          (when (or (not (<sub [:value/get ::data :invoicing-config-edit :ssh-authentication?]))
                    (not (<sub [::external-sftp?])))
            [input-row "Salasana" [:credentials :password]])])]]]))

(defn transfer-batch-settings []
  [:<>
   [:h3 "Siirtotiedoston asetukset"]
   [:table
    [:thead [:tr [:th "Asetus"] [:th "Arvo"]]]
    [:tbody
     (when (<sub [::show-file-prefix?])
       [input-row
        [:span "Lähtevän laskun tiedostonimen etuliite." [:br] "(Koskee SFTP-integraatioita.)"]
        [:invoice-file-prefix]
        [:div.pate-note [:i.lupicon-circle-info.mr-1.text-base-lg] "Jos tämä tieto on asetettu, se korvaa korvaa vakioetuliitteen." [:br]
         "Tampereen SAP IDOC SFTP vakioetuliite:" [:i "ID334_Lupapiste_"] [:br]
         "Yleinen SFTP vakioetuliite:" [:i "Lupapiste_"] [:br]
         "Mahdollisia välilyöntejä ei huomioida."]])

     [checkbox-row "Asiakasnumero pakollinen" [:integration-requires-customer-number?]]
     [checkbox-row "Lataa XML -painike" [:download?]]
     [checkbox-row "Käytä kaksimerkkistä maakoodia" [:use-alpha-2-country-code?]
      [:div.inline.ml-2 [:i.lupicon-circle-info.mr-1.text-base-lg] "(esim. FI vs FIN)"]]
     [checkbox-row "Laskunumero käytössä" [:backend-id?]]]]])

(defn invoice-constant-field-settings []
  [:<>
   [:h3 "Laskujen vakioarvot"]
   [:table
    [:thead [:tr [:th "Kenttä"] [:th "Arvo"]]]
    [:tbody
     [input-row "Tilauslaji" [:constants :tilauslaji]]
     [input-row "Myyntiorg" [:constants :myyntiorg]]
     [input-row "Myyntitoimisto" [:constants :myyntitoimisto]]
     [input-row "Jakelutie" [:constants :jakelutie]]
     [input-row "Sektori" [:constants :sektori]]
     [input-row "Laskuttaja" [:constants :laskuttaja]]
     [input-row "Nimike" [:constants :nimike]]
     [input-row "Tulosyksikkö" [:constants :tulosyksikko]]]]])

(defn buttons []
  (let [local-sftp? (<sub [::get-value [:local-sftp?]])
        changes?    (<sub [::changes?])]
    [:div.flex.gap-2
     [:button.primary
      {:on-click #(>evt [::save-invoicing-config-changes])
       :disabled (not (boolean changes?))}
      (common/loc :save)]
     [:button.secondary
      {:on-click #(>evt [::cancel-invoicing-config-changes])
       :disabled (not (boolean changes?))}
      (common/loc :cancel)]
     [:button.secondary
      {:disabled (or changes? local-sftp?)
       :on-click #(>evt [::test-invoicing-sftp])}
      "Testaa SFTP-yhteys"]]))

(defn connection-test-results []
  (when-let [{:keys [exception logs details]} (<sub [::sftp-test-result])]
    [:div.mt-8
     (when exception
       [:div.mb-2
        [:strong "VIRHE: "]
        [:span exception]
        [:br]])
     (let [{:keys [client-version server-version server-algs]} details]
       [:<>
        (when client-version
          [:<>
           [:strong "Lupapiste versio: "]
           [:span client-version]
           [:br]])
        (when server-version
          [:<>
           [:strong "Palvelimen versio: "]
           [:span server-version]
           [:br]])
        (when server-algs
          [:<>
           [:strong "Neuvoteltu algoritmi: "]
           [:span server-algs]
           [:br]])])
     (when (seq logs)
       [:table
        [:thead [:tr [:th "Aika"] [:th "Taso"] [:th "Viesti"]]]
        (into [:tbody]
              (for [[ts level msg] logs]
                [:tr
                 [:td.w--10em [:span.text-nowrap ts]]
                 [:td.w--10em [:span.text-nowrap.uppercase level]]
                 [:td.w--max-30em [:span msg]]]))])]))

(defn view []
  (when (<sub [::visible?])
    [:div
     [:h2 "Laskutusintegraatio"]
     [connection-config]
     [transfer-batch-settings]
     (when (<sub [::show-constants?])
       [invoice-constant-field-settings])
     [buttons]
     [connection-test-results]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id))
