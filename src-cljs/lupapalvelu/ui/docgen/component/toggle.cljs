(ns lupapalvelu.ui.docgen.component.toggle
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn toggle
  "See `toggle-[template.html, model.js]`."
  [{:keys [text ltext prefix checked? invalid? disabled? required? no-text? on-change]}]
  (let [aria-label (cond
                     ltext (common/loc ltext)
                     text  text)
        no-text?   (or no-text?
                       (ss/blank? aria-label))
        label-text (when-not no-text? aria-label)
        id         (str (gensym (str prefix "-toggle-")))]
    [:div {:class [(when no-text? "wrapper--no-border")
                   (when no-text? "wrapper--no-label")
                   (str prefix "-wrapper")]}
     [ui-input/checkbox
      (util/strip-nils
        {:id            id
         :checked?      checked?
         :disabled?     disabled?
         :aria-label    aria-label
         :aria-invalid? invalid?
         :required?     required?
         :on-change     on-change})]
     [:label
      {:aria-hidden true
       :for         id
       :class       [(str prefix "-label")
                     (when no-text? "no-label-text")]}
      label-text]]))
