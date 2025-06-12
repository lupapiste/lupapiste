(ns lupapalvelu.ui.docgen.field.docgen-property-group
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.toggle :as toggle]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.maaraala :as maaraala]
            [lupapalvelu.ui.docgen.feature.visibility :as visibility]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-maaraala-tunnus :as maaraala-field]
            [lupapalvelu.ui.docgen.loc :as docgen-loc]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]))

;; The `property-group` component and the `maaraala-tunnus` field within
;; are quite interlinked.
(def maaraala "maaraalaTunnus")

(defn- group-label
  [{:keys [doc-id schema path]}]
  (let [doc-i18n-name (<sub [::docs/i18n-name doc-id])
        loc-key       (docgen-loc/loc-key-group-label doc-i18n-name path schema)]
    [:label.form-label.form-label-property-group
     (common/loc loc-key)]))

(defn- maaraala-toggle
  "Choose whether we use the property id (kiinteistötunnus) from the application
  or allow the user to submit a määräala code."
  [{:keys [app-id doc-id disabled? maaraala? maaraala-path]}]
  {:pre [app-id doc-id (boolean? maaraala?) (vector? maaraala-path)]}
  (let [enabled?   (boolean maaraala?)
        on-enable  (fn [_]
                     ;; Enable & display the maaraala input field.
                     (>evt [::maaraala/toggle {:doc-id   doc-id
                                               :path     maaraala-path
                                               :enabled? true}]))
        on-disable (fn [_]
                     ;; Disable & hide the maaraala input field.
                     ;; But also, clear the old maaraala field in the document
                     ;; by acting as-if the user had submitted "" as the maaraala value.
                     (let [callback (docgen-common/make-on-change-immediate
                                      {:app-id        app-id
                                       :doc-id        doc-id
                                       :path          maaraala-path
                                       ;; If the backend accepted the value, close the maaraala input
                                       :extra-effects [[maaraala/toggle-fx {:doc-id   doc-id
                                                                            :path     maaraala-path
                                                                            :enabled? false}]]})]
                       (callback "")))]
    [toggle/toggle {:ltext     :kiinteisto.maaraala
                    :prefix    "buttonbox"
                    :disabled? disabled?
                    :checked?  enabled?
                    :on-change (if enabled?
                                 on-disable
                                 on-enable)}]))

(defn- prefer-maaraala
  [a b]
  (let [a? (= maaraala a)
        b? (= maaraala b)]
    (cond
      (and a? (not b?)) -1
      (and (not a?) b?) 1
      :else             0)))

(sc/defn render
  "See `docgen-property-group-[template.html,model.js]`."
  [{:keys [app-id doc-id schema path render-fn disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [args            (assoc args :prefer-ko-component? true)
        id              (path/path->group-id path doc-id)
        maaraala-path   (conj path (keyword maaraala))
        ;; Just in case: remove any hidden children
        body            (visibility/keep-visible-bodies args (:body schema))
        ;; Does this property-group contain a maaraala field?
        ;; In our current schemas the answer is always "yes", but
        ;; let's not assume that.
        maaraala-child? (util/find-by-key :name maaraala body)
        body            (cond->> body
                          ;; Make sure the maaraala field is the first field
                          maaraala-child? (sort-by :name prefer-maaraala))
        ;; maraala or property id?
        maaraala?       (and maaraala-child?
                             (<sub [::maaraala-field/maaraala? doc-id maaraala-path]))]
    [:div.form-group
     {:id id}
     [:div.property-group-header
      [group-label (util/mmap doc-id schema path)]
      (when maaraala-child?
        [maaraala-toggle (util/mmap app-id doc-id disabled? maaraala? maaraala-path)])]
     (when maaraala?
       [:div.pate-note
        (common/loc :rakennuspaikka.kiinteisto.maaraalaTunnus.help)])
     (->> body
          (map (fn [subschema]
                 ^{:key (path/path->str (conj path (:name subschema)))}
                 [:div.form-entry
                  [render-fn [subschema] (dissoc args :schema)]]))
          doall)]))
