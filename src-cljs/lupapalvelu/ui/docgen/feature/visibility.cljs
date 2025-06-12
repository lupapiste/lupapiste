(ns lupapalvelu.ui.docgen.feature.visibility
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.hidden :as hidden]
            [lupapalvelu.ui.docgen.feature.select-one :as select-one]
            [lupapalvelu.ui.docgen.feature.show-hide :as show-hide]
            [lupapalvelu.ui.docgen.feature.whitelist :as whitelist]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

(defn- schema-hidden?
  [{:keys [select-one permit-type]} {:keys [schema] :as args}]
  (or
    ;; The `:hidden` hides the field?
    (hidden/schema-hidden? args)
    ;; The `schema-include`/`exclude` flags hide the field?
    (not (flags/include-schema? schema))
    ;; The `show-when`/`hide-when` options hide the field?
    ;; NOTE: In the original docgen, we would apply classes
    ;; and try to hide the target element (see `docgen.js`/`displayOrHide`).
    ;; Here we just discard the element.
    (not (show-hide/show-field? args))
    ;; The "select one" feature hide the field?
    (select-one/hide-field? select-one args)
    ;; The whitelist hides the field?
    (whitelist/schema-hidden? schema permit-type)))

(defn- with-path
  "Add child schema name to `path`."
  [{:keys [schema] :as options}]
  (update options :path conj (keyword (:name schema))))

(defschema RenderChildrenOptions
  (-> schemas/DocgenFieldArguments
      (st/dissoc :schema)))

(sc/defn filter-body-xf
  "Returns a transducer, which merges the `body` fields and `options` and filters out hidden fields."
  [body :- schemas/DocgenBody options :- RenderChildrenOptions]
  (let [select-one  (select-one/resolve-selectable-fields body (:path options))
        permit-type (<sub [::app/permit-type])]
    (comp
      (map (fn [child-schema] (assoc options :schema child-schema)))
      (map with-path)
      (remove (partial schema-hidden? (util/mmap select-one permit-type))))))

(defn keep-visible-bodies
  "Given a seq of `body` fields, return those which are visible."
  [options body]
  (let [xf (comp (filter-body-xf body (-> options
                                          (dissoc :schema)))
                 (map :schema))]
    (->> (into [] xf body))))
