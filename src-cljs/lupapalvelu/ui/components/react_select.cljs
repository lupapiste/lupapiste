(ns lupapalvelu.ui.components.react-select
  (:require ["react-select" :default Select]
            [goog.object :as g]
            [sade.shared-schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(defn- disabled? [^js state]
  (.-isDisabled state))

(def styles
  {:control           (fn [provided state]
                        (doto provided
                          (g/set "borderRadius" "0")
                          (g/set "fontSize" "16px")
                          (g/set "minHeight" "40px"))
                        (when (not (disabled? state))
                          (g/set provided "borderColor" "#bbbbbb"))
                        provided)
   :dropdownIndicator (fn [provided state]
                        (cond-> provided
                          (not (disabled? state))
                          (g/set "color" "black"))
                        provided)
   :option            (fn [provided state]
                        (cond-> provided
                          (some-> (some-> state (g/get "data") (g/get "divide")))
                          (g/set "borderBottom" "1px solid #bbbbbb"))
                        (g/set provided "whiteSpace" "nowrap")
                        (g/set provided "overflow" "hidden")
                        (g/set provided "textOverflow" "ellipsis")
                        provided)
   :groupHeading      (fn [provided]
                        (doto provided
                          (g/set "color" "black")
                          (g/set "fontSize" "16px")
                          (g/set "fontWeight" "bold")
                          (g/set "textTransform" "unset")))
   :menu              (fn [provided]
                        (doto provided
                          ;; need to set z-index so it's higher than sticky accordions, which is 1
                          (g/set "zIndex" "10")
                          ;; Even if the select is too narrow to show the selected option label in full,
                          ;; make sure the popup menu itself is wide enough the see the whole label(s).
                          (g/set "minWidth" "fit-content")))})


(sc/defschema OptionType
  "An individual selectable option"
  {:value                    (sc/cond-pre sc/Str sc/Keyword)
   :label                    sc/Str
   (sc/optional-key :divide) sc/Bool})
(sc/defschema OptionsType [OptionType])

(sc/defschema GroupType
  "Multiple options grouped under a group label"
  {:label   sc/Str
   :options OptionsType})

(sc/defschema GroupOrOption
  "Available options, optionally grouped"
  (sc/conditional
    :options GroupType
    :else OptionType))

(sc/defschema ValueType
  "The current value"
  (sc/cond-pre OptionType OptionsType))

(sc/defschema ReactSelectProperties
  "See https://react-select.com/props for the full list of properties"
  (st/merge
    {:onChange (sc/pred fn?)
     :options  [GroupOrOption]}
    (st/optional-keys
      {:aria-invalid             (sc/enum true false "false" "true" "grammar" "spelling")
       :aria-label               ssc/NonBlankStr
       :controlShouldRenderValue sc/Bool
       :inputId                  ssc/NonBlankStr
       :isClearable              sc/Bool
       :isDisabled               sc/Bool
       :isMulti                  sc/Bool
       :isRequired               sc/Bool
       :noOptionsMessage         (sc/pred fn?)
       :placeholder              (sc/maybe (sc/cond-pre sc/Str sc/Num sc/Bool))
       :value                    (sc/maybe ValueType)})))

(sc/defn select
  [properties :- ReactSelectProperties]
  (js/React.createElement Select
                          (clj->js (assoc properties :styles styles))))
