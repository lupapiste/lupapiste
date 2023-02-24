(ns lupapalvelu.bulletin-report.core
  (:require [clojure.set :as set]
            [lupapalvelu.bulletin-report.page :as page]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.pate.verdict-common :as vc]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.property :refer [to-human-readable-property-id]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [java.time ZonedDateTime]))

(def VISIT-LIMIT "The maximum number of visits listed in a report" 30)

(sc/defschema Autopaikkoja
  {(sc/optional-key :vahintaan)     ssc/NonBlankStr
   (sc/optional-key :enintaan)      ssc/NonBlankStr
   (sc/optional-key :rakennettava)  ssc/NonBlankStr
   (sc/optional-key :rakennettu)    ssc/NonBlankStr
   (sc/optional-key :kiinteistolla) ssc/NonBlankStr
   (sc/optional-key :ulkopuolella)  ssc/NonBlankStr})

(sc/defschema Poytakirja
  {(sc/optional-key :paatoksentekija) ssc/NonBlankStr
   (sc/optional-key :text-html)       ssc/NonBlankStr
   :paatoskoodi                       ssc/NonBlankStr
   :verdict-date                      ZonedDateTime
   (sc/optional-key :section)         ssc/NonBlankStr})

(sc/defschema Paatos
  {(sc/optional-key :autopaikkoja) Autopaikkoja
   (sc/optional-key :kerrosala)    ssc/NonBlankStr
   ;; Maaraykset and muutMaaraykset
   (sc/optional-key :maaraykset)   [ssc/NonBlankStr]
   (sc/optional-key :suunnitelmat) [ssc/NonBlankStr]
   (sc/optional-key :katselmukset) [ssc/NonBlankStr]
   (sc/optional-key :tyonjohtajat) [ssc/NonBlankStr]
   :poytakirjat                    [Poytakirja]})

(sc/defschema BulletinInfo
  {:id                                ssc/NonBlankStr
   :address                           sc/Str
   :application-id                    ssc/ApplicationId
   (sc/optional-key :section)         ssc/NonBlankStr
   :start-date                        ZonedDateTime
   :end-date                          ZonedDateTime
   (sc/optional-key :verdict-date)    ZonedDateTime
   (sc/optional-key :given-date)      ZonedDateTime
   (sc/optional-key :visits)          [ZonedDateTime]
   :description-html                  ssc/NonBlankStr
   :property-id                       ssc/NonBlankStr
   :municipality                      ssc/NonBlankStr
   (sc/optional-key :kuntalupatunnus) ssc/NonBlankStr
   :paatokset                         [Paatos]})

(sc/defn ^:always-validate ->Autopaikkoja :- (sc/maybe Autopaikkoja)
  "Whereas Pate verdicts can only have three different parking spots, we support every
  possible value here. However, zero values are omitted. Since the (mostly legacy and
  backing system) data sanity cannot be guaranteed and we are not going to do any 'parking
  calculations', the values are converted to strings."
  [lupamaaraykset]
  (let [kmap {:autopaikkojaEnintaan      :enintaan
              :autopaikkojaVahintaan     :vahintaan
              :autopaikkojaRakennettava  :rakennettava
              :autopaikkojaRakennettu    :rakennettu
              :autopaikkojaKiinteistolla :kiinteistolla
              :autopaikkojaUlkopuolella  :ulkopuolella}]
    (some-> lupamaaraykset
            (select-keys (keys kmap))
            (set/rename-keys kmap)
            (->> (util/map-values (comp ss/blank-as-nil ss/trim str)))
            (util/strip-nils)
            (util/strip-matches #{"0"})
            not-empty)))

(sc/defn ^:always-validate ->Maaraykset :- (sc/maybe [ssc/NonBlankStr])
  "Pate verdicts only have maaraykset."
  [lupamaaraykset markup?]
  (let [{:keys [maaraykset muutMaaraykset]} lupamaaraykset]
    (->> (concat maaraykset muutMaaraykset)
         (map #(:sisalto % %))
         (map #(page/string->html % markup?))
         not-empty)))

(sc/defn ^:always-validate ->Katselmukset :- (sc/maybe [ssc/NonBlankStr])
  [lupamaaraykset lang :- i18n/Lang]
  (->> (:vaaditutKatselmukset lupamaaraykset)
       (map (fn [m]
              (or (:tarkastuksenTaiKatselmuksenNimi m)
                  (i18n/localize lang
                                 :task-katselmus.katselmuksenLaji
                                 (:katselmuksenLaji m)))))
       not-empty))

(sc/defn ^:always-validate ->Tyonjohtajat :- (sc/maybe [ssc/NonBlankStr])
  [lupamaaraykset]
  (not-empty (for [s (some-> lupamaaraykset
                             :vaaditutTyonjohtajat
                             (ss/split #",")
                             (ss/trimwalk))
                   :when (ss/not-blank? s)]
               s)))

(sc/defn ^:always-validate ->Poytakirjat :- (sc/maybe [Poytakirja])
  [paatos markup?]
  (some->> (:poytakirjat paatos)
           (map (fn [{:keys [paatos pykala paatospvm] :as m}]
                  (-> (select-keys m [:paatoksentekija :paatoskoodi])
                      (assoc :section (some-> pykala str)
                             :verdict-date (date/zoned-date-time paatospvm)
                             :text-html (page/string->html paatos markup?))
                      util/strip-nils)))
           (remove empty?)
           not-empty))

(sc/defn ^:always-validate ->Paatos :- Paatos
  [{m :lupamaaraykset :as paatos} {:keys [lang markup?]}]
  (util/strip-nils {:autopaikkoja (->Autopaikkoja m)
                    :kerrosala    (:kerrosala m)
                    :maaraykset   (->Maaraykset m markup?)
                    :suunnitelmat (:vaaditutErityissuunnitelmat m)
                    :katselmukset (->Katselmukset m lang)
                    :tyonjohtajat (->Tyonjohtajat m)
                    :poytakirjat  (->Poytakirjat paatos markup?)}))

(sc/defn ^:always-validate bulletin-info :- BulletinInfo
  [bulletin lang :- i18n/Lang]
  (let [{:keys [verdicts verdictData visits bulletinOpDescription
                primaryOperation propertyId markup?]
         :as   version} (some->> bulletin
                                 :versions
                                 last
                                 ss/trimwalk)
        verdict         (some->> verdicts (remove :draft ) first)
        dates           (some-> verdict :paatokset first :paivamaarat)]
    (util/assoc-when (select-keys version [:address :application-id :municipality])
                     :id (:id bulletin)
                     :section (ss/blank-as-nil (str (or (:section verdictData)
                                                        (vc/verdict-section verdict))))
                     :start-date (-> (or (:appealPeriodStartsAt version)
                                         (:julkipano dates))
                                     date/zoned-date-time
                                     date/with-time) ;; 00:00:00
                     :end-date (-> (or (:appealPeriodEndsAt version)
                                       (:viimeinenValitus dates))
                                   date/zoned-date-time
                                   date/end-of-day)
                     :verdict-date (date/zoned-date-time (vc/verdict-date verdict))
                     :given-date (date/zoned-date-time (or (:verdictGivenAt version)
                                                           (:anto dates)))
                     :visits (some->> (sort visits) ;; Just in case if manually updated
                                      (map date/zoned-date-time)
                                      not-empty)
                     :description-html (or (page/string->html bulletinOpDescription markup?)
                                           (page/string->html (i18n/localize lang
                                                                             :operations
                                                                             (:name primaryOperation))))
                     :property-id (to-human-readable-property-id propertyId)
                     :kuntalupatunnus (:kuntalupatunnus verdict)
                     :paatokset (->> (:paatokset verdict)
                                     (map #(->Paatos % {:lang lang :markup? markup?}))
                                     not-empty))))

(def context-rules [;; Parkings are "faked" as lists.
                    {:field   :enintaan
                     :loc-one :verdict.autopaikkojaEnintaan}
                    {:field   :vahintaan
                     :loc-one :verdict.autopaikkojaVahintaan}
                    {:field   :rakennettava
                     :loc-one :verdict.autopaikkojaRakennettava}
                    {:field   :rakennettu
                     :loc-one :verdict.autopaikkojaRakennettu}
                    {:field   :kiinteistolla
                     :loc-one :verdict.autopaikkojaKiinteistolla}
                    {:field   :ulkopuolella
                     :loc-one :verdict.autopaikkojaUlkopuolella}
                    {:field   :kerrosala
                     :loc-one :verdict.kerrosala}
                    {:field    :tyonjohtajat
                     :loc-one  :pdf.required-foreman
                     :loc-many :verdict.vaaditutTyonjohtajat}
                    {:field    :katselmukset
                     :loc-one  :pdf.required-review
                     :loc-many :verdict.vaaditutKatselmukset}
                    {:field    :suunnitelmat
                     :loc-one  :pdf.required-plan
                     :loc-many :verdict.vaaditutErityissuunnitelmat}
                    {:field    :maaraykset
                     :safe?    true
                     :loc-one  :task-lupamaarays.maarays
                     :loc-many :verdict.maaraykset}])

(defn detail-list [paatos]
  (let [paatos (merge paatos (:autopaikkoja paatos))]
    (for [{:keys [loc-one loc-many field
                  safe?]} context-rules
          :let            [v     (field paatos)
                           items (flatten [v])]
          :when           v]
      {:safe?  safe?
       :items  items
       :loc-fn #(i18n/localize % (if (= 1 (count items)) loc-one loc-many))})))

(defn bulletin-context
  ([bulletin {:keys [lang]}]
   (let [lang        (or lang :fi)
         info        (bulletin-info bulletin lang)
         visit-count (count (:visits info))
         zoned-now   (date/zoned-date-time (now))
         past?       #(.isAfter zoned-now %)]
     (reduce (fn [acc k]
               (if-let [v (k acc)]
                 (assoc acc (->> (name k) (format "past-%s?") keyword) (past? v))
                 acc))
             (-> info
                 (assoc :lang lang
                        :visit-count visit-count
                        :visit-limit (when (> visit-count VISIT-LIMIT)
                                       VISIT-LIMIT))
                 (update :visits (util/fn->> (take VISIT-LIMIT) seq))
                 (update :paatokset (partial map (fn [p]
                                                   (assoc  p :lupamaaraykset (detail-list p))))))
             [:start-date :end-date :verdict-date :given-date])))
  ([bulletin]
   (bulletin-context bulletin nil)))

(defn html-report
  ([bulletin options]
   (let [ctx (bulletin-context bulletin options)]
     {:header (page/render-template :header ctx)
      :body   (page/html (page/render-template :report ctx))
      :footer (page/render-template :footer ctx)}))
  ([bulletin]
   (html-report bulletin nil)))

(defn pdf-report [bulletin options]
  (let [{:keys [header body footer]} (html-report bulletin options)]
    (laundry-client/html-to-pdf body
                                header
                                footer
                                (i18n/localize (:lang options) :bulletin-report.report)
                                {:top    "22mm"
                                 :bottom "28mm"
                                 :left   "0mm"
                                 :right  "0mm"})))
