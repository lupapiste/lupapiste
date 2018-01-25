(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [hiccup.core :as hiccup]
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
            [sade.core :refer :all]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]))

(defonce page-number-script
  [:script
   (-> common/wkhtmltopdf-page-numbering-script-path
       io/resource
       slurp)])


(defn html [body & [script?]]
  (str "<!DOCTYPE html>"
       (hiccup/html
        [:html
         [:head
          [:meta {:http-equiv "content-type"
                  :content    "text/html; charset=UTF-8"}]
          [:style {:type "text/css"}
           (garden/css [[:* {:font-family "'Carlito', sans-serif"}]
                        [:.permit {:text-transform :uppercase
                                   :font-weight    :bold}]
                        [:div.header {:padding-bottom "1em"}]
                        [:.page-break {:page-break-before :always}]
                        [:.section {:display :table
                                    :width "100%"}
                         [:&.border {:border-top  "1px solid black"
                                     :padding-top  "1em"}]
                         [:&.header {:padding 0
                                     :border-bottom  "1px solid black"
                                     }]
                         [:.row {:display :table-row}
                          [:&.pad-after [:.cell {:padding-bottom "0.5em"}]]
                          [:&.pad-before [:.cell {:padding-top "0.5em"}]]
                          [:.cell {:display     :table-cell
                                   :white-space :pre-wrap
                                   :padding-right "1em"}
                           [:&:last-child {:padding-right 0}]
                           [:&.right {:text-align :right}]
                           [:&.center {:text-align :center}]
                           [:&.bold {:font-weight :bold}]]
                          (map (fn [n]
                                 [(keyword (str ".cell.cell--" n))
                                  {:display     :table-cell
                                   :white-space :pre-wrap
                                   :width       (str n "%")}])
                               (range 10 101 5))
                          ]]])]]
         [:body
          body
          (when script?
            page-number-script)]])))

(defn doc-value [doc-name kw-path]
  (fn [{app :application}]
    (get-in (domain/get-document-by-name app (name doc-name))
            (cons :data (map keyword (ss/split (name kw-path) #"\."))))))

(defn dict-value [dict]
  (fn [{verdict :verdict}]
    (get-in verdict [:data dict])))

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
    (i18n/localize lang prefix v)))

(defn entry-row
  [{lang :lang :as data} title fetch & opts]
  (when-let [entry (not-empty (if (fn? fetch)
                                (fetch data)
                                fetch))]
    (let [{:keys [bold-title] :as opts} (zipmap opts (repeat true))]
      [:div.row
       {:class (->> (keys opts)
                    (util/intersection-as-kw [:pad-after :pad-before])
                    (mapv name)
                    (ss/join " "))}
       [:div.cell.cell--30
        {:class (when bold-title "bold")}
        (i18n/localize lang title)]
       [:div.cell.cell--70 entry]])))

(defn content
  [data entries]
  [:div.section
   (->> entries
        (map (partial apply (partial entry-row data)))
        (filter not-empty))])

(defmulti verdict-body (util/fn-> :verdict :category keyword))

(defmethod verdict-body :r
  [{:keys [lang application verdict] :as data}]
  (content data
           [[:pate-verdict.application-id (:id application) :bold-title :pad-after]

            ;; Rakennuspaikka
            [:rakennuspaikka._group_label " " :bold-title]
            [:rakennuspaikka.kiinteisto.kiinteistotunnus
             (-> application
                 :propertyId
                 property/to-human-readable-property-id)]
            [:rakennuspaikka.kiinteisto.tilanNimi
             (doc-value :rakennuspaikka :kiinteisto.tilanNimi)]
            [:pdf.pinta-ala (wrap (unit-wrap :ha)
                                  (doc-value :rakennuspaikka
                                             :kiinteisto.maapintaala))]
            [:rakennuspaikka.kaavatilanne._group_label
             (wrap (loc-wrap :rakennuspaikka.kaavatilanne)
                   (doc-value :rakennuspaikka :kaavatilanne))
             :pad-after]
            [:pate-purpose (dict-value :purpose) :pad-after]]))

(defn verdict-header
  [lang {:keys [organization]} {:keys [published category data]}]
  [:div.header
   [:div.section.header
    [:div.row.pad
     [:div.cell.cell--30 (org/get-organization-name organization lang)]
     [:div.cell.cell--40.center
      [:div (i18n/localize lang :attachmentType.paatoksenteko.paatos)]]
     [:div.cell.cell--30.right
      [:div.permit (i18n/localize lang :pdf category :permit)]]]
    [:div.row
     [:div.cell.cell--30 (:verdict-section data)]
     [:div.cell.cell--40.center [:div (util/to-local-date published)]]
     [:div.cell.cell--30.right "Sivu " [:span#page-number "sivu"]]]]])


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
    :footer   (html nil)}))


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
