(ns lupapalvelu.ui.docgen.field.docgen-textlink
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [schema.core :as sc]))

(def ^:private link-pattern
  ;; Link texts look like:
  ;; "Click [here] to go somewhere"
  ;;              ^^^^^^^^^^^^^^^^-- group 2: suffix
  ;;         ^^^^------------------- group 1: link
  ;;  ^^^^^^------------------------ group 0: prefix
  (let [prefix "([^\\[]*)"
        link   "\\[([^\\]]+)]"
        suffix "(.*)"]
    (re-pattern (str prefix link suffix))))

(defn- build-link
  [text-loc url-loc]
  (let [link-text              (common/loc text-loc)
        url-text               (common/loc url-loc)
        [_ prefix link suffix] (re-find link-pattern link-text)]
    [:span prefix
     [:a {:href   url-text
          :target "_blank"}
      link]
     suffix]))

(sc/defn render
  "See `docgen-textlink-template.html`"
  [{:keys [doc-id schema path] :as args} :- schemas/DocgenFieldArguments]
  (let [input-id                (path/path->id path doc-id)
        {:keys [label]}         (di/args->label args)
        {:keys [icon url text]} schema]
    [:<>
     (when label
       ;; For some reason this label exists but it's hidden by CSS
       [di/docgen-label {:id         input-id
                         :label-text common/nbsp
                         :size       (:size schema)}])
     (if (seq icon) ; vector of keywords
       [:div.like-btn
        [:i {:class icon}]
        [build-link text url]]
       [build-link text url])]))
