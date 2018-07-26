(ns lupapalvelu.ui.pate.sections
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.layout :as layout]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn- contract? [{info :info}]
  (util/=as-kw (path/value [:category] info)
               :contract))

(rum/defc remove-section-checkbox < rum/reactive
  [{:keys [state schema] :as options}]
  (let [path       [:removed-sections (:id schema)]
        state*     (path/state path state)]
    (components/toggle state*
                       {:text-loc  (if (contract? options)
                                     :pate.contract.template.include
                                     :pate.template-removed)
                        :callback  #(path/meta-updated (assoc options
                                                              :path path))
                        :disabled? (path/disabled? options)
                        :negate?   true
                        :test-id   [:section (:id schema)]})))

(declare section)
(declare section-header)
(declare section-body)

;; -------------------------
;; Default
;; -------------------------

(rum/defc default-section < rum/reactive
  {:key-fn #(common/unique-id "section")}
  [options section-type]
  (when (path/visible? options)
    [:div.pate-section
     {:class (path/css options)}
     (section-header options section-type)
     (section-body options section-type)]))

(defn default-section-body
  [{:keys [schema] :as options}]
  [:div.section-body
   (layout/pate-grid (path/schema-options options
                                          (:grid schema)))])

;; -------------------------
;; Verdict template
;; -------------------------

(defn template-section-header
  [{:keys [dictionary schema] :as options}]
  [:div.section-header.pate-grid-2
   [:div.row.row--tight
    [:div.col-1
     [:span.row-text.section-title (path/loc options)]]
    [:div.col-1.col--right
     (if (contains? (-> dictionary :removed-sections :keymap)
                    (keyword (:id schema)))
       [:span
        (remove-section-checkbox options)]
       [:span.row-text (common/loc (if (contract? options)
                                     :pate.contract.template.include-always
                                     :pate.always-in-verdict))])]]])

(rum/defc template-section-body < rum/reactive
  [{:keys [schema state] :as options} _]
  (when-not (path/react [:removed-sections (:id schema)] state)
    [:div
     (when-let [help (:help schema)]
       (let [{:keys [loc html? css]} help
             text                    (common/loc (or loc help))]
         [:div (merge {:class (if css
                                (common/css css)
                                :pate-template-help)}
                      (when html?
                        {:dangerouslySetInnerHTML {:__html text}}))
          (when-not html? text)]))
     (default-section-body options)]))


(defn- section-type-fn [_ section-type] section-type)


;; -------------------------
;; Section
;; -------------------------

(defmulti section section-type-fn)

(defmethod section :default
  [options section-type]
  (default-section options section-type))

;; -------------------------
;; Section header
;; -------------------------

(defmulti section-header section-type-fn)

(defmethod section-header :default [_])

(defmethod section-header :verdict-template
  [options _]
  (template-section-header options))

;; -------------------------
;; Section body
;; -------------------------

(defmulti section-body section-type-fn)

(defmethod section-body :default
  [options _]
  (default-section-body options))

(defmethod section-body :verdict-template
  [options _]
  (template-section-body options))

(defn sections
  "Layout sections defined in the schema using section-type for the
  component selection."
  [{schema :schema :as options} section-type]
  (for [sec (:sections schema)]
    (section (path/schema-options options sec) section-type)))
