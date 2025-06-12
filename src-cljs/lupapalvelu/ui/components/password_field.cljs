(ns lupapalvelu.ui.components.password-field
  "See `resources/private/ui-components/input/password-field-*`"
  (:require [camel-snake-kebab.core :as csk]
            [lupapalvelu.ui.common :as common]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defschema PasswordFieldArgs
  {:id                               sc/Str
   :label                            sc/Str
   (sc/optional-key :autocomplete)   sc/Str
   (sc/optional-key :class)          sc/Str
   (sc/optional-key :extra-class)    sc/Str
   (sc/optional-key :value)          (sc/maybe sc/Str)
   (sc/optional-key :error-message)  sc/Str
   (sc/optional-key :quality)        (sc/enum "poor" "average" "good" "excellent" "estimating")
   (sc/optional-key :required?)      sc/Bool
   (sc/optional-key :disabled?)      sc/Bool
   (sc/optional-key :show-password?) sc/Bool
   :on-change                        (sc/pred fn? 'on-change)})

(sc/defn password-field
  "A single password field"
  [{:keys [id
           label
           autocomplete
           class
           extra-class
           value
           required?
           disabled?
           quality
           show-password?
           error-id
           error-message
           on-change]
    :or   {autocomplete   "current-password"
           show-password? false
           disabled?      false
           required?      true}} :- PasswordFieldArgs]
  (let [quality-info  (when quality
                        (common/loc (util/kw-path :mypage.quality quality)))
        input-classes (vec (keep identity [quality extra-class]))
        valid?        (nil? error-message)]
    [:div.field--vertical.w--max-20em
     {:class class}
     [:label.lux.field--block
      {:for   id
       :class [(when (and required? (nil? (ss/blank-as-nil value)))
                 "required")]}
      label]
     (let [common-props {:id           id
                         :class        input-classes
                         :defaultValue value
                         :disabled     disabled?
                         :aria-invalid (not valid?)
                         :on-change    (fn [v]
                                         (on-change (util/tval v)))}]
       [:input.lux.field--block.w--100
        (merge common-props
               (if show-password?
                 {:type         :text
                  :autoComplete "off"}
                 {:type         :password
                  :autoComplete autocomplete}))])
     [:div.w--100
      (if valid?
        (when quality-info
          [:span.info-message.field--block
           {:class [quality]}
           quality-info])
        [:div.error-note.field--block
         {:id   error-id
          :role :alert}
         error-message])]]))

;; Validators


(defn valid-password-length?
  [value]
  (boolean (and value (js/util.isValidPasswordLength value))))

(defn valid-password-quality?
  [value]
  (boolean (and value (js/util.isValidPasswordQuality value))))

(sc/defschema PasswordQualityOpts
  (sc/conditional
    :token {:token sc/Str}
    :user-id {:user-id sc/Str}
    :else {:password   sc/Str
           :first-name sc/Str
           :last-name  sc/Str
           :email      sc/Str}))

(sc/defschema PasswordQualityArgs
  {:password               sc/Str
   :callback               (sc/pred fn? 'callback)
   (sc/optional-key :opts) PasswordQualityOpts})

(sc/defn get-password-quality-async
  [{:keys [password
           callback
           opts]} :- PasswordQualityArgs]
  (js/util.getPwQualityAsync password callback (some->> opts
                                                        (util/map-keys csk/->camelCase)
                                                        clj->js)))
