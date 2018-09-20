(ns lupapalvelu.pate.settings-schemas
  (:require [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [sade.shared-util :as util]
            [schema.core :as sc]))

(defn date-delta-row [kws]
  (->> kws
       (reduce (fn [acc kw]
                 (conj acc
                       (when (seq acc)
                         {:dict :plus
                          :css [:date-delta-plus]})
                       {:col 2 :dict kw}))
               [])
       (remove nil?)))

;; -----------------------------
;; Settings subschemas
;; -----------------------------

(defn build-settings-schema
  "Combines subschemas and validates the result."
  [title & subschemas]
  (sc/validate schemas/PateSettings
               (assoc (apply schema-util/combine-subschemas subschemas)
                      :title title)))

(defn setsub-date-deltas
  "Dates is vector of date dict keys."
  [dates]
  (let [date-dicts (reduce-kv (fn [acc k v]
                                (assoc acc k (schema-util/required
                                              {:date-delta {:unit    v
                                                            :i18nkey (util/kw-path :pate-verdict k)}})))
                              {}
                              {:julkipano     :days
                               :anto          :days
                               :muutoksenhaku :days
                               :lainvoimainen :days
                               :aloitettava   :years
                               :voimassa      :years})]
    {:dictionary (merge {:verdict-dates {:loc-text :pate-verdict-dates-settings}
                         :plus          {:loc-text :plus}}
                        (select-keys date-dicts dates))
     :section    {:id         :verdict-dates
                  :loc-prefix :pate-verdict-dates-settings
                  :grid       {:columns 17
                               :rows    [(date-delta-row dates)]}}}))

(def setsub-verdict-code
  {:dictionary {:verdict-code
                (schema-util/required {:multi-select {:label? false
                                                      :items  (keys helper/verdict-code-map)
                                                      :loc-prefix :pate-r.verdict-code}})}
   :section    {:id         :verdict
                :required?  true
                :loc-prefix :pate-settings.verdict
                :grid       {:columns    1
                             :rows       [[{:dict :verdict-code}]]}}})

(def setsub-board ;; Lautakunta
  {:dictionary {:lautakunta-muutoksenhaku (schema-util/required {:date-delta {:unit :days
                                                                              :loc-prefix :pate-verdict.muutoksenhaku}})
                :boardname                (schema-util/required {:text {:loc-prefix :pate-settings.boardname}})}
   :section {:id         :board
             :loc-prefix :pate-settings.boardname-title
             :grid       {:columns 4
                          :rows    [[{:dict       :lautakunta-muutoksenhaku}]
                                    [{:col        1
                                      :align      :full
                                      :dict       :boardname}]]}}})

;; Must be included if reviews or plans is included.
(def setsub-lang-titles
  {:dictionary {:title-fi {:css      :pate-label.required
                           :loc-text :pate-verdict.language.fi}
                :title-sv {:css      :pate-label.required
                           :loc-text :pate-verdict.language.sv}
                :title-en {:css      :pate-label.required
                           :loc-text :pate-verdict.language.en}}})

(defn settings-repeating [{:keys [dict loc-prefix add-dict add-loc remove-dict]}]
  {:dictionary {dict     {:repeating {:fi         (schema-util/required {:text {:label? false}})
                                      :sv         (schema-util/required {:text {:label? false}})
                                      :en         (schema-util/required {:text {:label? false}})
                                      remove-dict {:button {:i18nkey :remove
                                                            :label?  false
                                                            :icon    :lupicon-remove
                                                            :css     [:primary :outline]
                                                            :remove  dict}}}}
                add-dict {:button {:icon    :lupicon-circle-plus
                                   :i18nkey add-loc
                                   :css     :positive
                                   :add     dict}}}
   :section    {:id         dict
                :loc-prefix loc-prefix
                :grid       {:columns 6
                             :rows    [{:css      [:pate-label :row--tight]
                                        :show? dict
                                        :row      [{:dict :title-fi}
                                                   {:dict :title-sv}
                                                   {:dict :title-en}]}
                                       {:css :row--tight
                                        :row [{:col  6
                                               :grid {:columns   6
                                                      :repeating dict
                                                      :rows      [{:css [:row--tight]
                                                                   :row [{:align :full
                                                                          :dict  :fi}
                                                                         {:align :full
                                                                          :dict  :sv}
                                                                         {:align :full
                                                                          :dict  :en}
                                                                         {:dict remove-dict}]}]}}]}
                                       [{:dict add-dict}]]}}})

(def setsub-plans
  (settings-repeating {:dict        :plans
                       :loc-prefix  :pate-settings.plans
                       :add-dict    :add-plan
                       :add-loc     :pate.add-plan
                       :remove-dict :remove-plan}))

(defn setsub-reviews [review-types]
  (-> (settings-repeating {:dict        :reviews
                           :loc-prefix  :pate-settings.reviews
                           :add-dict    :add-review
                           :add-loc     :pate.add-review
                           :remove-dict :remove-review})
      (assoc-in [:dictionary :title-type] {:css      [:pate-label.required]
                                           :loc-text :pate.review-type})
      (assoc-in [:dictionary :reviews :repeating :type]
                (schema-util/required {:select {:label?     false
                                                :loc-prefix :pate.review-type
                                                :items      review-types
                                                :sort-by    :text}}))
      (update-in [:section :grid :rows 0 :row] #(conj % {:dict :title-type}))
      (update-in [:section :grid :rows 1 :row 0 :grid :rows 0 :row]
                 #(concat (butlast %)
                          [{:align :full :dict :type}]
                          [(last %)]))))

(def setsub-handler-titles
  (settings-repeating {:dict        :handler-titles
                       :loc-prefix  :pate-settings.handler-titles
                       :add-dict    :add-handler-title
                       :add-loc     :pate.add-handler-title
                       :remove-dict :remove-handler-title}))

(def r-settings (build-settings-schema "pate-r"
                                       (setsub-date-deltas helper/verdict-dates)
                                       setsub-verdict-code
                                       setsub-board
                                       setsub-lang-titles
                                       setsub-plans
                                       (setsub-reviews helper/review-types)))

(def p-settings (build-settings-schema "pate-p"
                                       (setsub-date-deltas helper/verdict-dates)
                                       setsub-verdict-code
                                       setsub-board))

(def ya-settings (build-settings-schema "pate-ya"
                                        (setsub-date-deltas helper/ya-verdict-dates)
                                        setsub-verdict-code
                                        setsub-board
                                        setsub-lang-titles
                                        setsub-plans
                                        setsub-handler-titles
                                        (setsub-reviews helper/ya-review-types)))

(def tj-settings (build-settings-schema "pate-tj"
                                        (setsub-date-deltas helper/tj-verdict-dates)
                                        setsub-verdict-code
                                        setsub-board))

(def contract-settings (build-settings-schema "pate-contract"))

(defn settings-schema [category]
  (case (keyword category)
    :r r-settings
    :p p-settings
    :ya ya-settings
    :tj tj-settings
    :contract contract-settings))
