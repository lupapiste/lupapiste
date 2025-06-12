(ns lupapalvelu.ui.docgen.component.accordion-helper
  "Helper subscriptions which extract data from the accordion"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.accordion-toolbar :as at]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub ::operation-doc-localization
  (fn [[_ doc-id]]
    [(rf/subscribe [::docs/op-name doc-id])
     (rf/subscribe [::at/header-description doc-id])])
  (fn [[op-name header]]
    (ss/blank-as-nil (str op-name header))))

(rf/reg-sub ::operation-localization
  (fn [[_ op-id]]
    (rf/subscribe [::docs/op-id->doc-id op-id]))
  (fn [doc-id]
    (when doc-id
      (<sub [::operation-doc-localization doc-id]))))

(defn- path->attachment
  [path]
  (common/loc (util/kw-path :application.attachments path)))

(def op-regex #"op-id-([1234567890abcdef]{24})")

(defn attachment-or-operation-accordion-name
  "Fetch attachment (or operation) name.
  Should be called within a reactive context, as we may perform
  subscriptions.
  See `attachmentAccordionName`."
  [path]
  (if-let [[_ op-id] (re-matches op-regex path)]
    (<sub [::operation-localization op-id])
    (path->attachment path)))

(defn attachment-accordion-name
  "Fetch attachment name.
  See `attachmentAccordionName`."
  [path]
  (when (not (re-matches op-regex path))
    (path->attachment path)))
