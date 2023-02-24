(ns lupapalvelu.review-pdf
  "Review minutes (pöytäkirja) PDF generation. The layout and implementation leverages Pate PDF mechanisms."
  (:require [hiccup.core :as h]
            [lupapalvelu.application-meta-fields :as app-meta]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.tiedonohjaus :as tos]
            [sade.core :refer [fail!]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

;; ----------------------------
;; Layouts
;; ----------------------------

(def entry--tos-function
  [{:loc :review.pdf.tos-function
    :source :tos-function}])

(def entry--construction-site
  (list [{:loc    :rakennuspaikka._group_label
          :styles [:pad-before]
          :source :construction-site}]
        [{:loc    :review.pdf.property-name
          :source {:doc [:rakennuspaikka :kiinteisto.tilanNimi]}}]))

(def entry--buildings
  '([{:loc      :review.pdf.building
      :loc-many :review.pdf.buildings
      :source   :buildings}]))

(def entry--foremen
  '([{:loc :pdf.tj
      :loc-many :pate-foremen
      :source :foremen
      :styles [:pad-before :border-top]}
     {:loc-prefix :osapuoli.tyonjohtaja.kuntaRoolikoodi
      :path :code
      :styles :nowrap}
     {:path :fullname
      :width 100}]))

(defn entry--review-field [field & [{:keys [extra-path styles]}]]
  [{:loc    (util/kw-path :review.pdf field)
    :source :review
    :styles (or styles [:pad-before :border-top])}
   {:path (concat [:data :katselmus] (util/split-kw-path extra-path) [field])}])

(def entry--review-rectification
  [{:loc :review.pdf.rectification
    :source :rectification
    :styles [:bold :page-break]
    :post-fn (fn [v]
               (list [:div.markup (markup/markup->tags v)]))}])

(def entry--review-officer
  [{:loc :review.pdf.pitaja
    :source :review
    :styles [:pad-before :border-top]
    :post-fn (fn [v]
               (util/pcond-> (-> v :data :katselmus :pitaja)
                 :_atomic-map? :name))}])

(def entry--notes
  [{:loc :review.pdf.huomautukset
    :source :notes
    :styles [:pad-before :border-top]}])

(def pdf-layout
  (layouts/build-layout (layouts/entry--applicant :applicant :pdf.applicants)
                        layouts/entry--operation
                        entry--tos-function
                        layouts/entry--link-permits
                        entry--construction-site
                        entry--buildings
                        entry--foremen
                        entry--notes
                        (entry--review-field :maaraAika {:extra-path :huomautukset
                                                         :styles     [:pad-before]})
                        (entry--review-field :toteaja {:extra-path :huomautukset
                                                       :styles     []})
                        (entry--review-field :toteamisHetki {:extra-path :huomautukset
                                                             :styles     []})
                        (entry--review-field :poikkeamat)
                        (entry--review-field :lasnaolijat)
                        entry--review-officer
                        entry--review-rectification))

;; ----------------------------
;; Properties
;; ----------------------------


(defn review-type-id [review]
  (case (-> review :data :katselmuksenLaji ss/lower-case)
    "aloituskokous"  "aloituskokouksen_poytakirja"
    "loppukatselmus" "loppukatselmuksen_poytakirja"
    "katselmuksen_tai_tarkastuksen_poytakirja"))


(defn tag-and-description
  "Finds operation (and its description) for the building from the buildings array and then the tag
  from the corresponding document."
  [{:keys [buildings] :as application} vtj-prt index]
  (when-let [{:keys [operationId
                     description]} (reduce (fn [_ [vtj-prt index]]
                                             (some-> (util/assoc-when {}
                                                                      :nationalId vtj-prt
                                                                      :index index)
                                                     (util/find-by-keys buildings)
                                                     reduced))
                                           nil
                                           [[vtj-prt index] ; This should always be enough.
                                            [vtj-prt] ; These others are just in case there is a weird mismatch
                                            [index]])] ; between review and application buildings.
    (let [doc (domain/get-document-by-operation application operationId)]
      (ss/join-non-blanks " \u2013 " [(some-> doc :data :tunnus) description]))))

(defn buildings [lang application review]
  (some->> review :data :rakennus
           vals
           (filter (util/fn-> :tila :tila ss/not-blank?))
           (map (fn [{:keys [rakennus tila]}]
                  (let [vtj-prt     (:valtakunnallinenNumero rakennus)
                        in-use?     (:kayttoonottava tila)
                        tila        (:tila tila)
                        description (tag-and-description application vtj-prt (:jarjestysnumero rakennus))
                        parts (->> [vtj-prt
                                    (when (ss/not-blank? tila)
                                      (i18n/localize lang (util/kw-path :review.pdf tila :katselmus)))
                                    (when in-use?
                                      (i18n/localize lang :review.pdf.building-in-use))]
                                   (ss/join-non-blanks ", "))]
                    (ss/join-non-blanks ": " [description parts]))))
           seq))

(defn foremen [application]
  (some->> (foreman-util/get-linked-foreman-applications application)
           (map (fn [app]
                  (let [{:keys [kuntaRoolikoodi
                                henkilotiedot]} (-> app
                                                    (domain/get-document-by-name "tyonjohtaja-v2")
                                                    tools/unwrapped
                                                    :data)]
                    {:code     kuntaRoolikoodi
                     :fullname (->> [:etunimi :sukunimi]
                                    (map #(ss/trim (% henkilotiedot)))
                                    (ss/join " "))})))
           seq))

(defn review-notes [lang katselmus]
  (ss/join-non-blanks "\n\n"
                      [(ss/trim (some-> katselmus :huomautukset :kuvaus))
                       (when (:tiedoksianto katselmus)
                         (i18n/localize lang :task-katselmus.katselmus.tiedoksianto))]))

(defn link-permits
  "Application ids and primary operations for the link permits, excluding foreman permits."
  [application]
  (:linkPermitData (app-meta/enrich-with-link-permit-data application)))

(defn subtitle
  "Combines the review state and name. The result can include both (e.g., Osittainen loppukatselmus)
  or only name if the state is not valid (lopullinen/osittainen), or only the (valid) state if the
  name is blank."
  [lang taskname tila]
  (let [tila     (some-> tila ss/trim ss/lower-case)
        taskname (ss/trim taskname)]
    (-> (if (#{"osittainen" "lopullinen"} tila)
          (i18n/localize-and-fill lang (util/kw-path :review.pdf tila ) (ss/lower-case taskname))
          (ss/capitalize taskname))
        ss/trim
        ss/blank-as-nil)))

(defn review-properties
  "Options are have been `tools/unwrapped` and organization is not a delay.
  Note: Calls `tos/tos-function-with-name` that eventually makes an http request."
  [{:keys [lang application organization review] :as options}]
  (let [{:keys [taskname source data]}       review
        {:keys [katselmus katselmuksenLaji]} data
        {:keys [tila]}                       katselmus
        verdict                              (if (= (:type source) "verdict")
                                               (vif/find-verdict application (:id source))
                                               (vif/latest-published-verdict options))
        type-id                              (review-type-id review)
        type-group                           "katselmukset_ja_tarkastukset"
        ;; Note: 'osittainen loppukatselmus' is not loppukatselmus
        loppukatselmus?                      (when (ss/=trim-i "loppukatselmus" katselmuksenLaji)
                                               true)

        {:keys [rectification-enabled rectification-info
                contact]}                    (:review-pdf organization)
        rectification                        (when (and loppukatselmus? rectification-enabled)
                                               rectification-info)]
    (util/filter-map-by-val util/fullish?
                            (merge options
                                   {:buildings            (buildings lang application review)
                                    :lang                 lang
                                    :application-id       (:id application)
                                    :construction-site    (ss/join "\n" [(pdf/property-id application)
                                                                         (:address application)])
                                    :kuntalupatunnus      (vc/verdict-municipality-permit-id verdict)
                                    :organization-name    (html/organization-name lang application verdict)
                                    :applicants           (-> options pdf/applicants pdf/format-party-details)
                                    :operations           (assoc-in (pdf/operations options) [0 ::styles :text] :bold)
                                    :tos-function         (:name (tos/tos-function-with-name (:tosFunction application)
                                                                                             (:id organization)))
                                    :notes                (review-notes lang katselmus)
                                    :loppukatselmus?      loppukatselmus?
                                    :type-id              type-id
                                    :type-group           type-group
                                    :title                (i18n/localize lang (util/kw-path :attachmentType type-group type-id))
                                    :subtitle             (subtitle lang taskname tila)
                                    :foremen              (foremen application)
                                    :rectification        rectification
                                    :organization-contact contact
                                    :link-permits         (link-permits application)}))))


;; ----------------------------
;; PDF and attachment
;; ----------------------------

(defn review-header
  [{:keys [lang organization-name title subtitle application-id kuntalupatunnus review]}]
  [:div.header
   [:div.section.header
    [:div.row
     [:div.cell.cell--50 organization-name]
     [:div.cell.cell--50 title]]
    [:div.row
     [:div.cell.cell--50 (i18n/localize-and-fill lang :review.pdf.lupatunnus application-id )]
     [:div.cell.cell--50 subtitle]]
    [:div.row
     [:div.cell.cell--50 (when kuntalupatunnus
                           (i18n/localize-and-fill lang :review.pdf.kuntalupatunnus kuntalupatunnus))]
     [:div.cell.cell--20 (some-> review :data :katselmus :pitoPvm)]
     [:div.cell.cell--30.right.page-number.nowrap
      (i18n/localize lang :pdf.page)
      " " [:span.pageNumber ""]]]]])

(defn review-footer
  [{:keys [organization-name organization-contact]}]
  [:div {:style "width: 100%"}
   [:style html-pdf/simple-footer-style]
   [:div.footer
    [:div.left organization-name]
    [:div.right (when organization-contact
                  [:div.markup (markup/markup->tags organization-contact)])]]])

(defn review-tags [properties]
  {:header (review-header properties)
   :body   (cols/content properties pdf-layout)
   :footer (review-footer properties)})

(defn review-html [properties]
  {:header (html/html (review-header properties))
   :body   (html/html (cols/content properties pdf-layout))
   :footer (h/html (review-footer properties))})

(defn create-review-attachment
  "Creates PDF for the review and uploads it as an attachment. Returns
  the attachment-id or nil if the attachment could not be created."
  [{:keys [lang application created organization] :as command} review]
  (let [properties (review-properties {:lang         lang
                                       :organization (util/pcond-> organization
                                                                   delay? deref)
                                       :application  (tools/unwrapped application)
                                       :review       (tools/unwrapped review)})
        pdf        (html-pdf/html->pdf (review-html properties)
                                       {:top    "35mm"
                                        :bottom "25mm"
                                        :left   "9mm"
                                        :right  "9mm"})]
    (when-not (:ok pdf)
      (fail! :pate.pdf-review-error))
    (pdf/upload-and-attach-pdf!
      {:command            command
       :pdf                pdf
       :attachment-options {:created         created
                            :attachment-type (select-keys properties [:type-group :type-id])
                            :target          {:type "task"
                                              :id   (:id review)}
                            :source          {:type "tasks"
                                              :id   (:id review)}
                            :locked          true
                            :read-only       true
                            :contents        (:taskname review)}
       :file-options       {:filename (i18n/localize-and-fill lang
                                                              :review.pdf.filename
                                                              (:id application)
                                                              (:title properties)
                                                              (date/finnish-datetime created
                                                                                     :zero-pad))}})))
