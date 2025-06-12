(ns lupapalvelu.ui.docgen.component.accordion
  "The accordion wraps a document in a collapsible section and provides
   a toolbar for manipulating the document.

  Note that this accordion (and the toolbar within) are not generic UI
  components usable elsewhere; they are strongly coupled with documents
  and know their semantics and behaviour."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.accordion-state :as state]
            [lupapalvelu.ui.docgen.component.accordion-toolbar :as at]
            [lupapalvelu.ui.docgen.component.collapse :as collapse]))

(defn- section-help-text
  [doc-id]
  (if-let [help (<sub [::state/section-help doc-id])]
    [common/loc-html-reagent :span.group-help-text help]
    [:span.group-help-text]))

(defn accordion
  "Consists of a sticky toolbar and the accordion contents

  * `expand-accordions?` -- If true, expand all accordions by default.
                            If false, check `:enable-accordions` in auth-model."
  [{:keys [doc-id override-auth-source expand-accordions?]} & children]
  (let [open? (<sub [::state/accordion-open? doc-id expand-accordions?])]
    [:section.accordion
     [:div.sticky
      [:f> at/accordion-toolbar {:doc-id               doc-id
                                 :open?                open?
                                 :on-toggle            (fn []
                                                         (>evt [::state/toggle-accordion {:doc-id doc-id
                                                                                          :open?  (not open?)}]))
                                 :override-auth-source override-auth-source}]]
     [collapse/collapse
      {:open? open?}
      [:div.accordion_content.new_docgen
       {:id (str "document-" doc-id)}
       [section-help-text doc-id]
       (into [:div.accordion-fields] children)]]]))
