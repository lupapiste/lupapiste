(ns lupapalvelu.ui.docgen.schemas
  (:require [schema.core :as sc :refer [defschema]]))

;; With repeating groups (and tables?) the path might also contain indexes.
;; NOTE: The original data will have such indexes as keywords (`:1` instead of `1`),
;; because the hub mechanism aggressively keywordizes map keys whether we want to or not.
(defschema Path
  "A path to the `:data` of a form field."
  (sc/constrained [(sc/cond-pre sc/Keyword sc/Int)] vector?))

(defschema OverrideAuthSource
  (sc/enum :auth/global?
           :auth/application?
           :auth/attachment?
           :auth/task?
           :auth/disabled))

(defschema OtherSource
  {:path Path
   :type (sc/enum :checkbox :select)})

(defschema ValidationResultLevel (sc/enum :tip :warn :err))

(defschema DocgenSchema (sc/pred map?))

(defschema DocgenBody [DocgenSchema])

(defschema DocgenFieldArguments
  {:app-id                                    sc/Str
   :doc-id                                    sc/Str
   ;; `path` includes the child element. In other words, a field named "kuvaus"
   ;; in the root document body will have a path of `[:kuvaus]`.
   :path                                      Path
   :schema                                    DocgenSchema
   ;; Function for rendering field children (if any).
   ;; Takes a sequence of child elements and an options map.
   ;; At the moment always points to `lupapalvelu.ui.docgen.view/render-children`.
   (sc/optional-key :render-fn)               (sc/pred fn?)
   (sc/optional-key :disabled?)               sc/Bool
   (sc/optional-key :override-auth-source)    OverrideAuthSource
   ;; The visibility of this field
   (sc/optional-key :primary-source)          OtherSource
   (sc/optional-key :secondary-field-hidden?) sc/Bool
   ;; When within certain components (e.g. `docgen-group`), we prefer to use
   ;; components based on the original KO components rather than the `docmodel.js` ones.
   ;; E.g. `docgen-select-model.js` vs `buildSelect()` in `docmodel.js`.
   (sc/optional-key :prefer-ko-component?)    sc/Bool})
