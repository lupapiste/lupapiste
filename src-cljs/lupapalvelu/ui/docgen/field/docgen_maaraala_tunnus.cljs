(ns lupapalvelu.ui.docgen.field.docgen-maaraala-tunnus
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.maaraala :as maaraala]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-inline-string :as inline-string]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(rf/reg-sub ::maaraala-has-value?
  (fn maaraala-specified-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::value/current-field-value doc-id field-path]))
  (fn [value]
    (some? (ss/blank-as-nil value))))

(rf/reg-sub ::maaraala?
  (fn [[_ doc-id field-path]]
    [(rf/subscribe [::maaraala-has-value? doc-id field-path])
     (rf/subscribe [::maaraala/enabled? doc-id field-path])])
  (fn [[has-value? toggle-enabled?]]
    (boolean
      (if (some? toggle-enabled?)
        toggle-enabled?
        has-value?))))

(defn- maaraala-label
  [{:keys [doc-id path]}]
  [:label.form-label
   {:style {:display "block"}}
   (common/loc (if (<sub [::maaraala? doc-id path])
                 :kiinteisto.maaraala.label
                 :kiinteisto.kiinteisto.label))])

;; Sometimes we have a sibling "kiinteistoTunnus" field (`lisakohde-rakennuspaikka`).
;; If we find it, use that property ID rather than the application property ID
;; In legacy docgen this was handled in the parent `PropertyGroupModel`.
(rf/reg-sub ::property-id
  (fn [[_ doc-id path]]
    (let [parent-path           (pop path)
          kiinteistotunnus-path (conj parent-path :kiinteistoTunnus)]
      [(rf/subscribe [::value/current-field-value doc-id kiinteistotunnus-path])
       (rf/subscribe [::app/property-id-humanized])]))
  (fn [[kiinteistotunnus app-property-id]]
    (or (some-> kiinteistotunnus
                property/to-human-readable-property-id)
        app-property-id)))

(defn- maaraala-input
  [{:keys [doc-id path] :as args}]
  (let [maaraala? (<sub [::maaraala? doc-id path])]
    [:span.kiintun-and-maaraalatunnus
     [:span.form-maaraala
      (<sub [::property-id doc-id path])]
     (when maaraala?
       [:span "-M"])
     (when maaraala?
       [:f> inline-string/render (assoc-in args [:schema :label] false)])]))

(defn- manual-property-id-warning
  []
  (when (= :user (<sub [::app/property-id-source]))
    [:div.form-warning-message
     [:i.lupicon-circle-attention.negative]
     [:span
      (common/loc :application.property-id.entered-manually)]]))

(sc/defn render
  "See `maaraala-tunnus-template.html`"
  [{:keys [doc-id path] :as args} :- schemas/DocgenFieldArguments]
  [:div.form-entry
   [maaraala-label (util/mmap doc-id path)]
   [maaraala-input args]
   [manual-property-id-warning]])
