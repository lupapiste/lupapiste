(ns lupapalvelu.ui2.variant-builder
  "Component variant builder

  Inspired by CVA"
  (:require [malli.core :as m]
            [malli.error :as me]
            [reagent.core :as r]))

;; https://cva.style/docs/getting-started/variants

(def Classes [:vector :string])
(def Elements [:map-of :keyword Classes])
(def VariantKey :keyword)
(def VariantValue :any)

(def VariantConfig
  [:map
   [:base
    {:optional true}
    Elements]
   [:defaults
    {:optional true}
    [:map-of VariantKey VariantValue]]
   [:variants
    [:map-of VariantKey [:map-of VariantValue Elements]]]])

(def validator (m/validator VariantConfig))

;; TODO: Could be a macro to optimize for performance?
;; Or just do something to build optimal datastructure ahead
;; of render time.
;; TODO: Write test for this fn.
(defn builder [config]
  (when-not (validator config)
    (throw (ex-info (str "Invalid Variant Config: " (me/humanize (m/explain VariantConfig config)))
                    {})))

  (fn build-classes
    ([props] (build-classes props :root))
    ([props el-name]
     (r/class-names (get (get config :base) el-name)
                    (reduce (fn [acc [variant-k variant-values]]
                              (let [selected (or (get props variant-k)
                                                 (get (:defaults config) variant-k))
                                    klass (get (get variant-values selected) el-name)]
                                (if klass
                                  (into acc klass)
                                  acc)))
                            []
                            (:variants config))))))

;; TODO: Combine with builder or something?
;; Automatically remove builder flags from the props from the
;; base html-element?
(defn merge-props-classes [props classes]
  (update props :class r/class-names classes))
