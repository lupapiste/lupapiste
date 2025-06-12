(ns lupapalvelu.pdf.html-templates.foreman-info-template
  (:require [garden.core :as garden]
            [hiccup.core :as hiccup]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as html-common]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

;; Convenience functions

(defn- grid-style
  "Convenience function for generating grid layout styles
   Options:
   - grid-template-cols: explicit grid template string
   - cols: number of columns (uses repeat)"
  [{:keys [grid-template-cols cols]}]
  (html-common/map->style
    {:display               "grid"
     :grid-template-columns (cond
                              grid-template-cols grid-template-cols
                              cols               (str "repeat(" cols ", minmax(0, 1fr))"))
     ;; Use col-gap 0.25em (half of standard 0.5em) for percentage-based columns to align with
     ;; fr-based grids. Fr units account for gaps automatically; percentages don't.
     :column-gap            (if grid-template-cols "0.25em" "0.5em")
     :row-gap               "0.4em"
     :align-items           "start"
     :padding               "0.4em"
     :border                "solid 1px black"}))

(def default-foreman-styles
  (garden/css [{:font-size "11pt"}
               [:div#foreman-info {:page-break-after "always"}]
               [:h3 {:margin-bottom "0em"
                     :margin-top    "0.5em"}]]))

(def cell-content-style
  (html-common/map->style {:overflow-wrap "break-word"
                           :word-break    "break-word"}))

(defn- resolve-name [data path-or-string]
  (cond
    (vector? path-or-string) (get-in data path-or-string)
    (string? path-or-string) path-or-string
    :else ""))

(defn join-name [{:keys [data empty-text]} first-name-path-or-string last-name-path-or-string]
  (let [firstName (resolve-name data first-name-path-or-string)
        lastName  (resolve-name data last-name-path-or-string)]
    (if (or (ss/not-blank? firstName) (ss/not-blank? lastName))
      (ss/trim (str firstName " " lastName))
      empty-text)))

(defn extract-foreman-tasks
  "Returns a list of all selected foreman tasks ('vastattavatTyotehtavat)."
  [data]
  (->> (canonical-common/foreman-tasks-by-role data)
       (keep (fn [[k v]] (when (and (not (= k :muuMikaValue))
                                    v)
                           k)))))

(defn- create-get-or-empty
  "Tries to find the given path given from data, if not found returns empty-text
   Can also take an optional loc-prefix for localizations"
  [data loc empty-text]
  (fn [path & {:keys [loc-prefix]}]
    (let [v (get-in data path "")]
      (if (ss/not-blank? v)
        (if loc-prefix
          (loc (util/kw-path loc-prefix v))
          v)
        empty-text))))

(defn- foreman-v3-state [foreman-doc]
  (let [application?         (= "application" (:type foreman-doc))
        {:keys [value user]} (->> foreman-doc
                                  :changeLog
                                  (filter #(-> % :field (= "state")))
                                  last)
        termination          (:foremanTermination foreman-doc)]
    (cond
      (and application?
           (= "acknowledged" value))
      {:state-key :foreman.task.acknowledged.application.label
       :user      user}

      (= (:state termination) "confirmed")
      {:state-key :foreman.termination.status.terminated
       :user      (:confirmer termination)
       :reason    (:reason termination)}

      :else
      {:state-key (util/kw-path :foreman.task value :label)
       :user      user})))

(defn- foreman-v2-state [foreman-v2-app]
  (let [latest-state          (->> foreman-v2-app
                                   :history
                                   (util/filter-by-key :state (:state foreman-v2-app))
                                   last)
        latest-verdict-status (:status (or (:latest-verdict-status foreman-v2-app)
                                           (foreman-util/select-latest-verdict-status foreman-v2-app)))]
    {:state-key (case latest-verdict-status
                  "ok"       :foreman.task.acknowledged.application.label
                  "rejected" :foreman.task.rejected.label
                  latest-verdict-status)
     :user      (:user latest-state)}))

(defn- foreman-state [foreman-doc foreman-v2-app]
  (if (foreman/foreman-doc-is-v3? foreman-doc)
    (foreman-v3-state foreman-doc)
    (foreman-v2-state foreman-v2-app)))

(defn- get-foreman-v2-app
  "In the case of single-foreman being generated the 'application' parameter is the foreman-v2 application.
  And we return the `foreman-application-info` of the given foreman application.
  `foreman-application-info` is an enriched subset of the foreman application.

  In the case of generating a foreman-list we first need to find the linked foreman application.
  `get-linked-foreman-applications` gets the linked app and also calls `foreman-application-info` on it"
  [single-foreman? project-application application foreman-doc]
  (when-not (foreman/foreman-doc-is-v3? foreman-doc)
    (if single-foreman?
      (foreman-util/foreman-application-info project-application application)
      (some #(when (-> % (domain/get-document-by-name "tyonjohtaja-v2") :id (= (:id foreman-doc))) %)
            (foreman-util/get-linked-foreman-applications project-application)))))

(defn- resolve-project-application
  "Resolves the project application.
  - For V3 docs: the passed application is the project application.
  - For V2 single-foreman: looks up the linked project app from the DB.
  - For V2 foreman-list: application is already the project app."
  [application foreman-doc single-foreman?]
  (if (and single-foreman?
           (not (foreman/foreman-doc-is-v3? foreman-doc)))
    (foreman-util/get-project-application-for-foreman application)
    application))

(defn foreman-data
  "Resolves necessary values, creates localization and getter functions.
  Options:
  - :single-foreman? - true when generating HTML for a single foreman (application is the foreman app)"
  [application foreman-doc lang & {:keys [single-foreman?]}]
  (let [data                (tools/unwrapped (:data foreman-doc))
        loc                 (partial i18n/localize lang)
        empty-text          (loc "application.export.empty")
        get-or-empty        (create-get-or-empty data loc empty-text)
        project-application (resolve-project-application application foreman-doc single-foreman?)
        foreman-v2-app      (get-foreman-v2-app single-foreman? project-application application foreman-doc)
        state               (foreman-state foreman-doc foreman-v2-app)
        responsibility      (if (foreman/foreman-doc-is-v3? foreman-doc)
                              (foreman-util/get-foreman-responsibility-timestamps foreman-doc project-application)
                              (select-keys foreman-v2-app [:started :ended]))]
    {:data                data
     :loc                 loc
     :get-or-empty        get-or-empty
     :empty-text          empty-text
     :foreman-doc         foreman-doc
     :foreman-v2-app      foreman-v2-app
     :state               state
     :project-application project-application
     :responsibility      responsibility}))

;; HTML

(defn- header-html [app lang & {:keys [foreman-list?]}]
  [:div {:style "width: 100%"}
   [:div.header {:style (html-common/map->style {:font-family     "'Mulish'"
                                                 :font-size       "12pt"
                                                 :margin          "0em 2.5em"
                                                 :padding         "0em 0"
                                                 :display         "flex"
                                                 :justify-content "space-between"
                                                 :align-items     "start"})}
    [:div
     [:div {:style (html-common/map->style {:margin 0 :padding 0})}
      (when foreman-list?
        (i18n/localize lang "attachmentType.osapuolet.tyonjohtajaluettelo"))]
     [:b (str (:id app) ", " (:address app))]]
    [:div.logo {:style (html-common/map->style {:width "120px"})}
     html-pdf/lupapiste-logo]]])

(defn- foreman-header [{:keys [get-or-empty foreman-doc foreman-v2-app state loc] :as f-data}]
  (let [foreman-type (if (= "notification" (:type foreman-doc))
                       (loc :permitSubtype.tyonjohtaja-ilmoitus)
                       (loc :permitSubtype.tyonjohtaja-hakemus))]
    [:div {:style (html-common/map->style {:display         "flex"
                                           :flex-direction  "column"
                                           :justify-content "center"
                                           :align-items     "center"})}
     [:h2 {:style (html-common/map->style {:margin-top    "0.35em"
                                           :margin-bottom "0.25em"})}
      (str (get-or-empty [:kuntaRoolikoodi]
                         {:loc-prefix :osapuoli.tyonjohtaja.kuntaRoolikoodi})
           " "
           (join-name f-data [:henkilotiedot :etunimi] [:henkilotiedot :sukunimi]))]
     [:h4 {:style (html-common/map->style {:margin "0.15em"})}
      "(" foreman-type
      (when foreman-v2-app
        (str ": " (:id foreman-v2-app)))
      ", " (loc (:state-key state)) ")"]]))

(defn- personal-info [{:keys [loc get-or-empty] :as f-data}]
  [:div
   [:h3
    (loc "osapuoli.henkilotiedot._group_label")]
   [:div.grid-holder {:style (grid-style {:cols 2})}
    [:div
     [:b (loc "name")]
     [:div (join-name f-data [:henkilotiedot :etunimi] [:henkilotiedot :sukunimi])]]
    [:div
     [:b (loc "userinfo.personId")]
     [:div (get-or-empty [:henkilotiedot :hetu])]]]])

(defn- company-info
  "If no company-info is given we do not display this at all"
  [{:keys [loc get-or-empty empty-text]}]
  (let [company-name (get-or-empty [:yritys :yritysnimi])
        y-tunnus     (get-or-empty [:yritys :liikeJaYhteisoTunnus])]
    (when-not (every? (partial = empty-text)
                      [company-name y-tunnus])
      [:div
       [:h3
        (loc "osapuoli.yritys._group_label")]
       [:div {:style (grid-style {:cols 2})}
        [:div
         [:b (loc "name")]
         [:div company-name]]
        [:div
         [:b (loc "y-tunnus")]
         [:div y-tunnus]]]])))

(defn- contact-address [{:keys [loc get-or-empty]}]
  (let [country  (get-or-empty [:osoite :maa])
        finnish? (= country "FIN")]
    [:div
     [:h3
      (loc "yhteystiedot._group_label")]
     [:div {:style (grid-style {:grid-template-cols (if finnish?
                                                      "50% 16.6% 33%"
                                                      "50% 13% 27% 10%")})}
      [:div {:style cell-content-style}
       [:b (loc "userinfo.street")]
       [:div (get-or-empty [:osoite :katu])]]
      [:div
       [:b (loc "userinfo.zip")]
       [:div (get-or-empty [:osoite :postinumero])]]
      [:div {:style cell-content-style}
       [:b (loc "userinfo.city")]
       [:div (get-or-empty [:osoite :postitoimipaikannimi])]]
      ;; Only show country if it is a non-finnish address
      (when-not finnish?
        [:div
         [:b (loc "country.country")]
         [:div country]])
      [:div
       [:b (loc "email")]
       [:div (get-or-empty [:yhteystiedot :email])]]
      [:div
       [:b (loc "puhelin")]
       [:div (get-or-empty [:yhteystiedot :puhelin])]]]]))

(defn- construction-info [{:keys [loc get-or-empty]}]
  [:div
   [:h3
    (loc "osapuoli.tyonjohtajaHanketieto._group_label")]
   [:div {:style (grid-style {:cols 3})}
    [:div
     [:b (loc "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label")]
     [:div (get-or-empty [:kuntaRoolikoodi]
                         {:loc-prefix :osapuoli.tyonjohtaja.kuntaRoolikoodi})]]
    [:div
     [:b (loc "osapuoli.patevyysvaatimusluokka._group_label")]
     [:div (get-or-empty [:patevyysvaatimusluokka]
                         {:loc-prefix :osapuoli.patevyys.patevyysluokka})]]
    [:div
     [:b (loc "osapuoli.tyonjohtajaHanketieto.taysiaikainenOsaaikainen")]
     [:div (get-or-empty [:tyonjohtajaHanketieto :taysiaikainenOsaaikainen]
                         {:loc-prefix :osapuoli.tyonjohtajaHanketieto.taysiaikainenOsaaikainen})]]
    [:div
     [:b (loc "osapuoli.tyonjohtajaHanketieto.hankeKesto")]
     [:div (get-or-empty [:tyonjohtajaHanketieto :hankeKesto])]]
    [:div
     [:b (loc "osapuoli.tyonjohtajaHanketieto.kaytettavaAika")]
     [:div (get-or-empty [:tyonjohtajaHanketieto :kaytettavaAika])]]
    [:div
     [:b (loc "osapuoli.tyonjohtajaHanketieto.kayntienMaara")]
     [:div (get-or-empty [:tyonjohtajaHanketieto :kayntienMaara])]]]])

(defn- foreman-tasks [{:keys [loc get-or-empty data]}]
  [:div
   [:h3
    (loc "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label")]
   (into
     [:ul {:style (html-common/map->style {:margin-top    "0em"
                                           :margin-bottom "0em"
                                           :padding       "0.5em"
                                           :border        "solid 1px black"})}]
     (mapv (fn [task]
             [:li {:class "task-template" :style "list-style-type: none;"}
              [:b (let [loc-task (loc (util/kw-path "osapuoli.tyonjohtaja.vastattavatTyotehtavat" task))]
                    (if (= task :muuMika)
                      (str loc-task ": " (get-or-empty [:vastattavatTyotehtavat :muuMikaValue]))
                      loc-task))]])
           (extract-foreman-tasks data)))])

(defn- substitution-info
  "If no substitution-info is given we do not display this at all"
  [{:keys [loc get-or-empty empty-text] :as f-data}]
  (let [substituted-name       (join-name f-data
                                          [:sijaistus :sijaistettavaHloEtunimi]
                                          [:sijaistus :sijaistettavaHloSukunimi])
        substituted-start-date (get-or-empty [:sijaistus :alkamisPvm])
        substituted-end-date   (get-or-empty [:sijaistus :paattymisPvm])]
    (when-not (every? (partial = empty-text) [substituted-name
                                              substituted-start-date
                                              substituted-end-date])
      [:div
       [:h3
        (loc "tyonjohtaja.sijaistus._group_label")]
       [:div {:style (grid-style {:cols 3})}
        [:div
         [:b (loc "sijaistettava")]
         [:div substituted-name]]
        [:div
         [:b (loc "tyonjohtaja.sijaistus.alkamisPvm")]
         [:div substituted-start-date]]
        [:div
         [:b (loc "tyonjohtaja.sijaistus.paattymisPvm")]
         [:div substituted-end-date]]]])))

(defn- eligibility-info [{:keys [loc get-or-empty empty-text]}]
  [:div
   [:h3
    (loc "osapuoli.patevyys-tyonjohtaja._group_label")]
   [:div {:style (grid-style {:cols 3})}
    [:div
     [:b (loc "osapuoli.patevyys.koulutus")]
     [:div (get-or-empty [:patevyys-tyonjohtaja :koulutusvalinta]
                         {:loc-prefix :koulutus})]]
    [:div
     [:b (loc "muukoulutus")]
     [:div (if (= "other" (get-or-empty [:patevyys-tyonjohtaja :koulutusvalinta]))
             (get-or-empty [:patevyys-tyonjohtaja :koulutus])
             empty-text)]]
    [:div
     [:b (loc "osapuoli.patevyys-tyonjohtaja.valmistumisvuosi")]
     [:div (get-or-empty [:patevyys-tyonjohtaja :valmistumisvuosi])]]
    [:div
     [:b (loc "osapuoli.patevyys.kokemusvuodet")]
     [:div (get-or-empty [:patevyys-tyonjohtaja :kokemusvuodet])]]
    [:div
     [:b (loc "tyonjohtaja.patevyys.valvottavienKohteidenMaara")]
     [:div (get-or-empty [:patevyys-tyonjohtaja :valvottavienKohteidenMaara])]]]])

(defn- qualifications-info [{:keys [loc get-or-empty]}]
  [:div
   [:h3
    (loc "osapuoli.patevyystodistus-tyonjohtaja")]
   [:div {:style (grid-style {:grid-template-cols "67% 33%"})}
    [:div
     [:b (loc "osapuoli.patevyys.patevyydentoteaja._group_label")]
     [:div (get-or-empty [:patevyys-tyonjohtaja :patevyydentoteaja]
                         {:loc-prefix :osapuoli.patevyys.patevyydentoteaja})]]
    [:div
     [:b (loc "osapuoli.patevyys.patevyysvoimassapvm")]
     [:div (get-or-empty [:patevyys-tyonjohtaja :patevyysvoimassapvm])]]]])

(defn- approval-info [{:keys [loc data empty-text foreman-doc state responsibility] :as f-data}]
  (let [{:keys [state-key user reason]} state
        foreman-user-id             (get data :userId)
        approver                    (join-name f-data
                                               (get user :firstName)
                                               (get user :lastName))]
    [:div
     [:h3
      (loc "tila")]
     [:div {:style (grid-style {:cols 4})}
      [:div
       [:b (loc "foreman.status")]
       [:div (loc state-key)]]
      [:div
       [:b (loc "tyonjohtaja.historia.hyvaksyja")]
       ;; If a foreman has declared his own notification, we leave the approver blank
       [:div (if (and (-> foreman-doc :type (= "notification"))
                      (= foreman-user-id (:id user)))
               empty-text
               approver)]]
      [:div
       [:b (loc :application.foreman.table.responsibility-period)]
       [:div
        (if (:started responsibility)
          (str (date/finnish-date (:started responsibility))
               (when (:ended responsibility)
                 (str " - " (date/finnish-date (:ended responsibility)))))
          empty-text)]]
      [:div
       [:b (loc "tyonjohtaja.historia.hyvaksynta.ilman-viranomainen")]
       [:div (loc (if (get-in data [:tyonjohtajanHyvaksynta :tyonjohtajanHyvaksynta])
                    :yes
                    :no))]]
      (when reason
        [:div {:style (html-common/map->style {:grid-column "1/-1"})}
         [:b (loc :foreman.termination.reason)]
         [:div (ss/limit reason 107 "...")]])]]))

(defn- foreman-hiccup
  "Return a hiccup fragment for a single foreman

  The function automatically handles differences between tyonjohtaja-v2 and tyonjohtaja-v3
  document schemas."
  [application foreman-doc lang & {:keys [single-foreman?]}]
  (let [f-data (foreman-data application foreman-doc lang :single-foreman? single-foreman?)]
    [:div#foreman-info
     (foreman-header f-data)
     (personal-info f-data)
     (company-info f-data)
     (contact-address f-data)
     (construction-info f-data)
     (foreman-tasks f-data)
     (substitution-info f-data)
     (eligibility-info f-data)
     (when (foreman/foreman-doc-is-v3? foreman-doc)
       (qualifications-info f-data))
     (approval-info f-data)]))

(defn single-foreman-html
  "Generates a complete HTML document for a single foreman document. "
  [foreman-doc application lang]
  (let [header (header-html application lang)
        body   [:div
                [:style (str (html-common/styles) default-foreman-styles)]
                (foreman-hiccup application foreman-doc lang :single-foreman? true)]]
    {:body   (hiccup/html body)
     :header (hiccup/html header)
     :footer (hiccup/html (html-common/default-footer lang))}))

(defn foreman-list-html
  "Generates a complete HTML document containing information about all foremen.
  Foremen should be a collection of related foreman documents"
  [foremen project-application lang]
  (let [header (header-html project-application lang :foreman-list? true)
        body   (into [:div
                      [:style (str (html-common/styles) default-foreman-styles)]]
                     (mapv #(foreman-hiccup project-application % lang)
                           foremen))]
    {:body   (hiccup/html body)
     :header (hiccup/html header)
     :footer (hiccup/html (html-common/default-footer lang))}))
