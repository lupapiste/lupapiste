(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as common]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(def page-number-script
  [:script
   {:dangerouslySetInnerHTML
    {:__html
     (-> common/wkhtmltopdf-page-numbering-script-path
         io/resource
         slurp)}}])


(defn html [body & [script?]]
  (str "<!DOCTYPE html>"
       (rum/render-static-markup
        [:html
         [:head
          [:meta {:http-equiv "content-type"
                  :content    "text/html; charset=UTF-8"}]
          [:style
           {:type "text/css"
            :dangerouslySetInnerHTML
            {:__html (garden/css
                      [[:* {:font-family "'Carlito', sans-serif"}]
                       [:.permit {:text-transform :uppercase
                                  :font-weight    :bold}]
                       [:div.header {:padding-bottom "1em"}]
                       [:div.footer {:padding-top "1em"}]
                       [:.page-break {:page-break-before :always}]
                       [:.section {:display :table
                                   :width   "100%"}
                        [:&.header {:padding       0
                                    :border-bottom "1px solid black"}]
                        [:&.footer {:border-top "1px solid black"}]
                        [:>.row {:display :table-row}
                         [:&.border-top [:>.cell {:border-top "1px solid black"}]]
                         [:&.border-bottom [:>.cell {:border-bottom "1px solid black"}]]
                         [:&.pad-after [:>.cell {:padding-bottom "0.5em"}]]
                         [:&.pad-before [:>.cell {:padding-top "0.5em"}]]
                         [:.cell {:display       :table-cell
                                  :white-space   :pre-wrap
                                  :padding-right "1em"}
                          [:&:last-child {:padding-right 0}]
                          [:&.right {:text-align :right}]
                          [:&.center {:text-align :center}]
                          [:&.bold {:font-weight :bold}]
                          [:&.nowrap {:white-space :nowrap}]]
                         (map (fn [n]
                                [(keyword (str ".cell.cell--" n))
                                 {:width (str n "%")}])
                              (range 10 101 5))]]])}}]]
         [:body body (when script?
                       page-number-script)]])))

(defn applicants
  "Returns list of name, address maps for properly filled party
  documents. If a doc does not have name information it is omitted."
  [{app :application lang :lang}]
  (->> (domain/get-applicant-documents (:documents app))
       (map :data)
       (map (fn [{:keys [_selected henkilo yritys]}]
              (if (util/=as-kw :yritys)
                yritys
                henkilo)))
       (map (fn [{:keys [yritysnimi henkilotiedot osoite]}]
              {:name (ss/trim (or yritysnimi
                                  (format "%s %s"
                                          (:etunimi henkilotiedot)
                                          (:sukunimi henkilotiedot))))
               :address (let [{:keys [katu postinumero postitoimipaikannimi
                                      maa]} osoite]
                          (->> [katu (str postinumero " " postitoimipaikannimi)
                                (when (util/not=as-kw maa :FIN)
                                  (i18n/localize lang :country maa))]
                               (remove ss/blank?)
                               (map ss/trim)
                               (ss/join ", ")))}))
       (remove (util/fn-> :name ss/blank?))))

(defn- pathify [kw-path]
  (map keyword (ss/split (name kw-path) #"\.")))

(defn doc-value [doc-name kw-path]
  (fn [{app :application}]
    (get-in (domain/get-document-by-name app (name doc-name))
            (cons :data (pathify kw-path)))))

(defn dict-value [dict]
  (fn [{verdict :verdict}]
    (get-in verdict [:data dict])))

(defn primary-value [kw-path]
  (fn [{app :application}]
    (-<>> app
          :primaryOperation
          :id
          (domain/get-document-by-operation app)
          :data
          (get-in <> (pathify kw-path)))))

(defn wrap [wrap-fn fetch]
  (letfn [(do-wrap [data w v]
            (when (seq v)
              (w data v)))]
    (fn [data]
      (if (fn? fetch)
        (->> data fetch (do-wrap data wrap-fn))
        (do-wrap data wrap-fn fetch)))))

(defn unit-wrap [unit]
  (fn [{lang :lang} v]
    (case unit
      :ha (str v " " (i18n/localize lang :unit.hehtaaria))
      :m2 [:span v " m" [:sup 2]]
      :m3 [:span v " m" [:sup 3]])))

(defn loc-wrap [prefix]
  (fn [{lang :lang} v]
    (when-not (-> v str ss/blank?)
      (i18n/localize lang prefix v))))

(defn complexity [data]
  (let [[c txt]   ((juxt (wrap (loc-wrap :pate.complexity)
                               (dict-value :complexity))
                         (dict-value :complexity-text)) data)]
    (remove nil? [c (when txt [:p txt])])))

(defn property-id [data]
  (->> [(-> data :application :propertyId
            property/to-human-readable-property-id)
        ((doc-value :rakennuspaikka :kiinteisto.maaraalaTunnus) data)]
       (remove ss/blank?)
       (ss/join "-")))

(defn value-or-other [lang value other & loc-keys]
  (if (util/=as-kw value :other)
    other
    (if (seq loc-keys)
      (i18n/localize lang loc-keys value)
      value)))

(defn designers [{lang :lang app :application}]
  (let [role-keys [:osapuoli :suunnittelija :kuntaRoolikoodi]
        head-loc (i18n/localize lang role-keys "p\u00e4\u00e4suunnittelija")
        heads    (->> (domain/get-documents-by-name app :paasuunnittelija)
                      (map :data)
                      (map #(assoc % :role head-loc)))
        others   (map :data
                      (domain/get-documents-by-name app :suunnittelija))]
    (->> (concat heads others)
         (remove nil?)
         (map (fn [{role-code    :kuntaRoolikoodi
                    other-role   :muuSuunnittelijaRooli
                    difficulty   :suunnittelutehtavanVaativuusluokka
                    info         :henkilotiedot
                    skills       :patevyys
                    degree       :koulutusvalinta
                    other-degree :koulutus
                    role         :role}]
                {:role       (or role
                                 (value-or-other lang
                                                 role-code other-role
                                                 role-keys))
                 :difficulty (i18n/localize lang
                                            :osapuoli
                                            :suunnittelutehtavanVaativuusluokka
                                            difficulty)
                 :name       (format "%s %s"
                                     (or (:etunimi info) "")
                                     (or (:sukunimi info) ""))
                 :education (value-or-other lang
                                            (:koulutusvalinta skills)
                                            (:koulutus skills))}))
         (remove (util/fn-> :name ss/blank?))
         (sort (fn [a b]
                 (if (= a head-loc) -1 1))))))

(defn design-complexity [designers]
  (when (seq designers)
    [:div.section
     (map (fn [{:keys [role difficulty]}]
            [:div.row
             [:div.cell.nowrap role]
             [:div.cell.cell--100 difficulty]])
          designers)]))

(defn designer-info [designers]
  (when (seq designers)
    [:div.section
     (map (fn [{:keys [role name education]}]
            [:div.row
             [:div.cell.nowrap role]
             [:div.cell.cell--100 (->> [name education]
                                       (remove ss/blank?)
                                       (ss/join ", ")
                                       (ss/trim))]])
          designers)]))

(defn entry-row
  [{lang :lang :as data} title fetch & opts]
  (when-let [entry (not-empty (if (fn? fetch)
                                (fetch data)
                                fetch))]
    (let [{:keys [bold-title] :as opts} (zipmap opts (repeat true))]
      [:div.row
       {:class (->> (keys opts)
                    (util/intersection-as-kw [:pad-after :pad-before
                                              :border-top :border-bottom])
                    (mapv name)
                    (ss/join " "))}
       [:div.cell.cell--30
        {:class (when bold-title "bold")}
        (i18n/localize lang title)]
       [:div.cell.cell--70 (util/pcond-> entry string? ss/trim)]])))

(defn content
  [data entries]
  [:div.section
   (->> entries
        (map (partial apply (partial entry-row data)))
        (filter not-empty))])

(defn loc-alternative [xs one many]
  (if (= (count xs) 1) one many))

(defmulti verdict-body (util/fn-> :verdict :category keyword))

(defmethod verdict-body :r
  [{:keys [lang application verdict] :as data}]
  (content data
           (concat
            [[:pate-verdict.application-id (:id application) :bold-title :pad-after]

             ;; Rakennuspaikka
             [:rakennuspaikka._group_label " " :bold-title]
             [:rakennuspaikka.kiinteisto.kiinteistotunnus property-id]
             [:rakennuspaikka.kiinteisto.tilanNimi
              (doc-value :rakennuspaikka :kiinteisto.tilanNimi)]
             [:pdf.pinta-ala (wrap (unit-wrap :ha)
                                   (doc-value :rakennuspaikka
                                              :kiinteisto.maapintaala))]
             [:rakennuspaikka.kaavatilanne._group_label
              (wrap (loc-wrap :rakennuspaikka.kaavatilanne)
                    (doc-value :rakennuspaikka :kaavatilanne))
              :pad-after]
             [:pate-purpose (dict-value :purpose) :pad-after]

             ;; Applicants
             (let [apps (applicants data)]
               [(loc-alternative apps :pdf.applicant :pdf.applicants)
                (->> apps
                     (map (fn [{:keys [name address]}]
                            (format "%s\n%s" name address)))
                     (ss/join "\n\n"))
                :pad-after :border-bottom])

             ;; Operations
             (let [[x & xs :as op-names]
                   (->> (app/get-sorted-operation-documents application)
                        (map (util/fn-> :schema-info :op :name))
                        (map #(i18n/localize lang :operations %)))]
               [(loc-alternative op-names :applications.operation :operations)
                (concat [[:b x] "\n"] (ss/join "\n" xs))
                :pad-before])
             [:pate-extra-info (dict-value :extra-info) :pad-before]
             [:pate.complexity complexity :pad-before]
             [:pate-rights (dict-value :rights) :pad-before]]

            ;; Designers
            (let [designers (designers data)]
              [[:pdf.design-complexity (design-complexity designers) :pad-before]
               [(loc-alternative designers :pdf.designer :pdf.designers)
                (designer-info designers) :pad-before]])

            ;; Primary operation
            [[:verdict.kerrosala (wrap (unit-wrap :m2)
                                       (primary-value :mitat.kerrosala))
              :pad-before]
             [:verdict.kokonaisala (wrap (unit-wrap :m2)
                                         (primary-value :mitat.kokonaisala))]
             [:pdf.volume (wrap (unit-wrap :m3)
                                (primary-value :mitat.tilavuus))]])))

(defn verdict-header
  [lang {:keys [organization]} {:keys [published category data]}]
  [:div.header
   [:div.section.header
    [:div.row.pad-after
     [:div.cell.cell--30 (org/get-organization-name organization lang)]
     [:div.cell.cell--40.center
      [:div (i18n/localize lang :attachmentType.paatoksenteko.paatos)]]
     [:div.cell.cell--30.right
      [:div.permit (i18n/localize lang :pdf category :permit)]]]
    [:div.row
     [:div.cell.cell--30 (:verdict-section data)]
     [:div.cell.cell--40.center [:div (util/to-local-date published)]]
     [:div.cell.cell--30.right "Sivu " [:span#page-number "sivu"]]]]])

(defn verdict-footer []
  [:div.footer
   [:div.section
    [:div.row.pad-after.pad-before
     [:cell.cell--100 {:dangerouslySetInnerHTML {:__html "&nbsp;"}}]]]])

(defn verdict-pdf [lang application {:keys [published] :as verdict}]
  (html-pdf/create-and-upload-pdf
   application
   "pate-verdict"
   (html (verdict-body {:lang        lang
                        :application (tools/unwrapped application)
                        :verdict     verdict}))
   {:filename (i18n/localize-and-fill lang
                                      :pate.pdf-filename
                                      (:id application)
                                      (util/to-local-datetime published))
    :header   (html (verdict-header lang application verdict) true)
    :footer   (html (verdict-footer))}))


(defn create-verdict-attachment
  "1. Create PDF file for the verdict.
   2. Create verdict attachment.
   3. Bind 1 into 2."
  [{:keys [lang application user created] :as command} verdict]
  (let [{:keys [file-id mongo-file]} (verdict-pdf lang application verdict)
        _                            (when-not file-id
                                       (fail! :pate.pdf-verdict-error))
        {attachment-id :id
         :as           attachment}   (att/create-attachment!
                                      application
                                      {:created           created
                                       :set-app-modified? false
                                       :attachment-type   {:type-group "paatoksenteko"
                                                           :type-id    "paatos"}
                                       :target            {:type "verdict"
                                                           :id   (:id verdict)}
                                       :locked            true
                                       :read-only         true
                                       :contents          (i18n/localize lang
                                                                         :pate-verdict)})]
    (bind/bind-single-attachment! (update-in command
                                             [:application :attachments]
                                             #(conj % attachment))
                                  (mongo/download file-id)
                                  {:fileId       file-id
                                   :attachmentId attachment-id}
                                  nil)))
