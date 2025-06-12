(ns lupapalvelu.ui.docgen.field.docgen-rakennuskohde
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))


(def rakennuskohde-document-schema-names #{"uusiRakennuskohde"})

(rf/reg-sub ::rakennuskohde-document-ids
  :<- [::docs/documents]
  (fn [documents]
    (->> documents
         ;; This is definitely not the best place for this logic. Should be moved to more
         ;; suitable location when the nature of actual usage comes clearer
         (filterv #(rakennuskohde-document-schema-names (-> % second :schema-info :name)))
         (mapv (comp :id second)))))

(defn toggle [s elem]
  (if (s elem)
    (disj s elem)
    (conj s elem)))

(defn rakennuskohde-description
  "Returns a localized string that describes a 'rakennuskohde' with its currently
   stored data, if one is selected. Can be used in the UI to differentiate between application's structures."
  [rk-document-id]
  (when-let [kohteen-laji (<sub [::value/current-field-value rk-document-id [:kaytto :kohteen-laji]])]
    (let [nimi (ss/blank-as-nil (<sub [::value/current-field-value rk-document-id [:kaytto :nimi]]))
          prt  (ss/blank-as-nil (<sub [::value/current-field-value rk-document-id [:pysyva-rakennustunnus]]))
          {:keys [kayttotarkoitus loc-prefix]}
          (case kohteen-laji
            "rakennus"   {:kayttotarkoitus (<sub [::value/current-field-value rk-document-id [:kaytto :rakennusluokka]])
                          :loc-prefix      "rakennusluokka"}
            "rakennelma" {:kayttotarkoitus (<sub [::value/current-field-value rk-document-id [:kaytto :rakennelman-kayttotarkoitus]])
                          :loc-prefix      "rakennelman-kayttotarkoitus"}
            "etvra"      {:kayttotarkoitus (<sub [::value/current-field-value rk-document-id [:kaytto :erityista-toimintaa-tyyppi]])
                          :loc-prefix      "erityista-toimintaa-varten-rakennettava-alue.tyyppi"})]
      (str (common/loc (util/kw-path loc-prefix kayttotarkoitus))
           (when nimi (str " - " nimi))
           (when prt (str " (" prt ")"))))))

(rf/reg-sub ::rakennuskohde-ids+labels
  :<- [::rakennuskohde-document-ids]
  (fn [ids]
    (->> ids
         (keep (fn [rk-id]
                 ;; Ignore documents where the user has not yet chosen the "kohteen laji"
                 (when-let [description (rakennuskohde-description rk-id)]
                   {:rk-id rk-id :label description})))
         (into []))))

(sc/defn render [{:keys [app-id doc-id schema path disabled?]} :- schemas/DocgenFieldArguments]
  (let [{:keys [i18nkey]}        schema
        input-id-prefix          (path/path->id path doc-id)
        on-change-fn             (docgen-common/make-on-change-immediate (util/mmap app-id doc-id path))
        selected                 (set (<sub [::value/current-field-value doc-id path]))
        rakennuskohde-ids+labels (<sub [::rakennuskohde-ids+labels])]
    (when (seq rakennuskohde-ids+labels)
      [:div {:class ["form-entry" "form-full-width"]}
       [:div.flex
        [:div (common/loc i18nkey)]
        [ping/ping {:doc-id doc-id :field-path path}]]
       [:div
        (->> rakennuskohde-ids+labels
             (map (fn [{:keys [rk-id label]}]
                    (let [input-id (str input-id-prefix "-" rk-id)]
                      ^{:key input-id}
                      [:<>
                       [ui-input/checkbox
                        {:id        input-id
                         :checked?  (selected rk-id)
                         :on-change #(on-change-fn (toggle selected rk-id))
                         :disabled? (boolean disabled?)
                         :class     "hidden"}]
                       [di/docgen-label {:id         input-id
                                         :label-text label
                                         :size       (:size schema)
                                         :class      ["inline-label" "checkbox-label"]}]])))
             doall)]])))
