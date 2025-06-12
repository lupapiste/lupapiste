(ns lupapalvelu.ui.docgen.field.docgen-review-buildings
  "UI component for displaying and editing review task buildings.

  This component requires a specific schema structure defined in docgen schemas
  (see lupapalvelu.tasks/task-katselmus-body).

  Expected schema structure:
  {:name 'rakennus'
   :type :group
   :repeating true
   :uicomponent :docgen-review-buildings
   :body [{:name 'rakennus' :type :group
           :body [{:name 'jarjestysnumero' ...}
                  {:name 'valtakunnallinenNumero' ...}
                  ...]}
          {:name 'tila' :type :group
           :body [{:name 'tila' :type :select ...}
                  {:name 'kayttoonottava' :type :checkbox ...}]}]}

  The nested structure is critical:
  - Top-level :body must contain a 'tila' group
  - The 'tila' group's :body must contain both:
    - 'tila' (a select field for status)
    - 'kayttoonottava' (a checkbox for marking building as in use)"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def tila "tila")

(def kayttoonottava "kayttoonottava")

(defn- building-description
  [building]
  (when building
    (let [nat-id       (:nationalId building)
          op-id        (:operationId building)
          building-doc (<sub [::docs/op-or-nat->related-document op-id nat-id])
          tunnus       (get-in building-doc [:data :tunnus :value])
          base-desc    (or (:description building) "")
          joined       (->> [tunnus base-desc]
                            (ss/join-non-blanks ": "))]
      (or joined ""))))

(defn- render-child
  [schema {:keys [render-fn] :as cell-args}]
  (let [body [(assoc schema :label false)]]
    [render-fn body cell-args]))

(defn- building-row
  "Building row is rendered based on documents :rakennus index and values fetched accordingly.
   Description is fetched from the application data :buidings for some reason it's not in the document data"
  [{:keys [doc-id base-path row-index status-select-schema checkbox-schema cell-args]}]
  (let [;; Get data from the document row
        building-path (conj base-path row-index :rakennus)
        nat-id        (<sub [::value/current-field-value doc-id (conj building-path :valtakunnallinenNumero)])
        order-num     (<sub [::value/current-field-value doc-id (conj building-path :jarjestysnumero)])
        property-id   (<sub [::value/current-field-value doc-id (conj building-path :kiinttun)])
        ;; Find matching building from application buildings to get description
        buildings     (<sub [::application/buildings])
        building      (common/find-matching-building buildings nat-id (str order-num))
        description   (building-description building)]
    [:tr
     [:td description]
     [:td [render-child status-select-schema cell-args]]
     [:td [render-child checkbox-schema cell-args]]
     [:td nat-id]
     [:td property-id]]))

(sc/defn render
  [{:keys [doc-id schema] base-path :path :as args} :- schemas/DocgenFieldArguments]
  (let [tila-schema          (->> schema
                                  :body
                                  (util/find-by-key :name tila))
        status-select-schema (->> tila-schema
                                  :body
                                  (util/find-by-key :name tila))
        checkbox-schema      (->> tila-schema
                                  :body
                                  (util/find-by-key :name kayttoonottava))
        row-indexes          (<sub [::docs/field-child-keys doc-id base-path])]
    [(di/container schema)
     [:div.h2 (common/loc :task-katselmus.rakennukset)]
     (if (seq row-indexes)
       [:table.review-buildings-table
        [:thead
         [:tr
          [:th.review-buildings--description (common/loc :task-katselmus.selite)]
          [:th (common/loc :task-katselmus.tila)]
          [:th (common/loc :task-katselmus.rakennus.tila.kayttoonottava)]
          [:th (common/loc :task-katselmus.rakennus.valtakunnallinenNumero)]
          [:th (common/loc :task-katselmus.rakennus.kiinteistotunnus)]]]
        [:tbody
         (for [row-index row-indexes]
           (let [cell-args (-> args
                               (dissoc :schema)
                               (assoc :path (conj base-path row-index :tila)))]
             ^{:key row-index}
             [building-row {:doc-id               doc-id
                            :base-path            base-path
                            :row-index            row-index
                            :checkbox-schema      checkbox-schema
                            :status-select-schema status-select-schema
                            :cell-args            cell-args}]))]]
       [:span (common/loc :task-katselmus.no-buildings)])]))
