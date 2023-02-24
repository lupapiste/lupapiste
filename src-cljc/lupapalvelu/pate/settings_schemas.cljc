(ns lupapalvelu.pate.settings-schemas
  (:require [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [schema.core :as sc]))


;; -----------------------------
;; Settings subschemas
;; -----------------------------

(defn build-settings-schema
  "Combines subschemas and validates the result."
  [title & subschemas]
  (sc/validate schemas/PateSettings
               (assoc (apply schema-util/combine-subschemas subschemas)
                      :title title)))

(defn setsub-organization-name
  ([i18nkey]
   {:dictionary {:organization-name (schema-util/required {:text {:i18nkey i18nkey}})}
    :section    {:id      :organization-name
                 :i18nkey :empty
                 :grid    {:columns 4
                           :rows    [[{:dict  :organization-name
                                       :align :full}]]}}})
  ([] (setsub-organization-name :pate.verdict.organization-name)))

(defn setsub-date-deltas
  "Dates is vector of date dict keys."
  [category]
  (let [dates                    (helper/verdict-dates category)
        {:keys [dictionary row]} (helper/date-deltas dates true)]
    {:dictionary (assoc dictionary
                        :verdict-dates {:loc-text :pate-verdict-dates-settings})
     :section    {:id         :verdict-dates
                  :loc-prefix :pate-verdict-dates-settings
                  :grid       {:columns 17
                               :rows    [row]}}}))

(def setsub-verdict-code
  {:dictionary {:verdict-code
                (schema-util/required {:multi-select {:label? false
                                                      :items  (keys helper/verdict-code-map)
                                                      :loc-prefix :pate-r.verdict-code
                                                      :sort? true}})}
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

(defn settings-repeating [{:keys [dict loc-prefix add-dict add-loc remove-dict
                                  move-up-dict move-down-dict manual-dict]}]
  (let [arrow-buttons? (and move-up-dict move-down-dict manual-dict)
        columns        (if arrow-buttons? 7 6)]
    {:dictionary {dict     (cond-> {:repeating (merge {:fi         (schema-util/required {:text {:label? false}})
                                                       :sv         (schema-util/required {:text {:label? false}})
                                                       :en         (schema-util/required {:text {:label? false}})
                                                       remove-dict {:button {:i18nkey :remove
                                                                             :label?  false
                                                                             :icon    :lupicon-remove
                                                                             :css     [:primary :outline]
                                                                             :remove  dict}}}
                                                      (when arrow-buttons?
                                                        {move-up-dict   {:button {:label?  false
                                                                                  :text?   false
                                                                                  :icon    :lupicon-arrow-up
                                                                                  :css     [:primary :outline]
                                                                                  :move {:direction :up
                                                                                         :manual manual-dict}}}
                                                         move-down-dict {:button {:label?  false
                                                                                  :text?   false
                                                                                  :icon    :lupicon-arrow-down
                                                                                  :css     [:primary :outline]
                                                                                  :move {:direction :down
                                                                                         :manual manual-dict}}}}))}
                             arrow-buttons? (assoc :sort-by {:manual manual-dict}))

                  add-dict {:button {:icon    :lupicon-circle-plus
                                     :i18nkey add-loc
                                     :css     :positive
                                     :add     dict}}}
     :section    {:id         dict
                  :loc-prefix loc-prefix
                  :grid       {:columns columns
                               :rows    [{:css   [:pate-label :row--tight]
                                          :show? dict
                                          :row   (cond->> [{:dict :title-fi}
                                                           {:dict :title-sv}
                                                           {:dict :title-en}]
                                                   arrow-buttons? (cons {}))}
                                         {:css :row--tight
                                          :row [{:col  columns
                                                 :grid {:columns   columns
                                                        :repeating dict
                                                        :rows      [{:css [:row--tight]
                                                                     :row (cond->> [{:align :full
                                                                                     :dict  :fi}
                                                                                    {:align :full
                                                                                     :dict  :sv}
                                                                                    {:align :full
                                                                                     :dict  :en}
                                                                                    {:dict remove-dict}]
                                                                            arrow-buttons?
                                                                            (cons {:list {:labels? false
                                                                                          :items   [{:dict move-down-dict}
                                                                                                    {:dict move-up-dict}]}}))}]}}]}
                                         [{:dict add-dict}]]}}}))

(def setsub-plans
  (settings-repeating {:dict           :plans
                       :loc-prefix     :pate-settings.plans
                       :add-dict       :add-plan
                       :add-loc        :pate.add-plan
                       :remove-dict    :remove-plan
                       :move-up-dict   :move-up-plan
                       :move-down-dict :move-down-plan
                       :manual-dict    :plan-index}))

(defn setsub-reviews [review-types]
  (-> (settings-repeating {:dict           :reviews
                           :loc-prefix     :pate-settings.reviews
                           :add-dict       :add-review
                           :add-loc        :pate.add-review
                           :remove-dict    :remove-review
                           :move-up-dict   :move-up-review
                           :move-down-dict :move-down-review
                           :manual-dict    :review-index})
      (assoc-in [:dictionary :title-type] {:css      [:pate-label.required]
                                           :loc-text :pate.review-type})
      (assoc-in [:dictionary :reviews :repeating :type]
                (schema-util/required {:select {:label?     false
                                                :loc-prefix :pate.review-type
                                                :items      review-types
                                                :sort-by    :text}}))
      (update-in [:section :grid :rows 0 :row] #(concat % [{:dict :title-type}]))
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
                                       (setsub-organization-name)
                                       (setsub-date-deltas :r)
                                       setsub-verdict-code
                                       setsub-board
                                       setsub-lang-titles
                                       setsub-plans
                                       (setsub-reviews helper/review-types)))

(def p-settings (build-settings-schema "pate-p"
                                       (setsub-organization-name)
                                       (setsub-date-deltas :p)
                                       setsub-verdict-code
                                       setsub-board))

(def ya-settings (build-settings-schema "pate-ya"
                                        (setsub-organization-name)
                                        (setsub-date-deltas :ya)
                                        setsub-verdict-code
                                        setsub-board
                                        setsub-lang-titles
                                        setsub-plans
                                        setsub-handler-titles
                                        (setsub-reviews helper/ya-review-types)))

(def tj-settings (build-settings-schema "pate-tj"
                                        (setsub-organization-name)
                                        (setsub-date-deltas :tj)
                                        setsub-verdict-code
                                        setsub-board))

(def contract-settings (build-settings-schema "pate-contract"
                                              (setsub-organization-name :pate.contract.organization-name)))

(defn settings-schema [category]
  (case (keyword category)
    :r r-settings
    :p p-settings
    :ya ya-settings
    :tj tj-settings
    :contract contract-settings))
