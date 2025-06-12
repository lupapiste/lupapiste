(ns lupapalvelu.pate.settings-schemas
  (:require [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [sade.util :as util]
            [schema.core :as sc]))


;; -----------------------------
;; Settings subschemas
;; -----------------------------

(def ^:private valid-pate-settings! (sc/validator schemas/PateSettings))

(defn build-settings-schema
  "Combines subschemas and validates the result."
  [title & subschemas]
  (valid-pate-settings!
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

(defn setsub-announcement-dates
  "Like `setsub-date-deltas`, but includes dates for both julkipano and kuulutus"
  [category]
  (let [dates           (helper/verdict-dates category)
        dates-kuulutus  (helper/kuulutus-dates category)
        deltas          (helper/date-deltas dates true)
        deltas-kuulutus (helper/date-deltas dates-kuulutus true)]
    {:dictionary (merge {:announcement-dates         {:loc-text :pate-verdict-dates-settings}
                         :julkipano-dates-help-text  {:loc-text :pate-julkipano-dates-help-text}
                         :kuulutus-dates-help-text   {:loc-text :pate-kuulutus-dates-help-text}
                         :kuulutus-dates-help-text-2 {:loc-text :pate-kuulutus-dates-help-text-2}}
                        (:dictionary deltas)
                        (:dictionary deltas-kuulutus))
     :section    {:id         :announcement-dates
                  :loc-prefix :pate-verdict-dates-settings
                  :grid       {:columns 17
                               :rows    [[{:col  17
                                           :dict :julkipano-dates-help-text}]
                                         (:row deltas)
                                         [{:col  17
                                           :dict :kuulutus-dates-help-text}]
                                         [{:col  17
                                           :dict :kuulutus-dates-help-text-2}]
                                         (:row deltas-kuulutus)]}}}))

(def setsub-verdict-code
  {:dictionary {:verdict-code
                (schema-util/required {:multi-select {:label? false
                                                      :items  (keys helper/verdict-code-map)
                                                      :loc-prefix :pate.verdict-code
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

;; Must be included if reviews, plans or verdict-givers is included.
(defn setsub-lang-titles
  [required?]
  {:dictionary {:title-fi {:css      (when required? :pate-label.required)
                           :loc-text :pate-verdict.language.fi}
                :title-sv {:css      (when required? :pate-label.required)
                           :loc-text :pate-verdict.language.sv}
                :title-en {:css      (when required? :pate-label.required)
                           :loc-text :pate-verdict.language.en}}})

(defn setsub-verdict-giver-lang-titles
  [required?]
  {:dictionary {:boardname-fi {:css      (when required? :pate-label.required)
                               :loc-text :pate-settings.boardname.fi}
                :boardname-sv {:css      (when required? :pate-label.required)
                               :loc-text :pate-settings.boardname.sv}
                :boardname-en {:css      (when required? :pate-label.required)
                               :loc-text :pate-settings.boardname.en}}})

(defn settings-repeating [{:keys [dict loc-prefix add-dict add-loc remove-dict
                                  move-up-dict move-down-dict manual-dict
                                  verdict-giver-names?]}]
  (let [arrow-buttons?  (and move-up-dict move-down-dict manual-dict)
        columns         (if arrow-buttons? 7 6)
        language-params (cond-> {:text {:label? false}}
                          (not verdict-giver-names?) schema-util/required)]
    {:dictionary {dict     (cond-> {:repeating (merge {:fi         language-params
                                                       :sv         language-params
                                                       :en         language-params
                                                       remove-dict {:button {:i18nkey :remove
                                                                             :label?  false
                                                                             :icon    :lupicon-remove
                                                                             :css     [:primary :outline]
                                                                             :remove  dict}}}
                                                      (when arrow-buttons?
                                                        {move-up-dict   {:button {:label? false
                                                                                  :text?  false
                                                                                  :icon   :lupicon-arrow-up
                                                                                  :css    [:primary :outline]
                                                                                  :move   {:direction :up
                                                                                           :manual    manual-dict}}}
                                                         move-down-dict {:button {:label? false
                                                                                  :text?  false
                                                                                  :icon   :lupicon-arrow-down
                                                                                  :css    [:primary :outline]
                                                                                  :move   {:direction :down
                                                                                           :manual    manual-dict}}}}))}
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
                                          :row   (cond->> [{:dict (if verdict-giver-names?
                                                                    :boardname-fi
                                                                    :title-fi)}
                                                           {:dict (if verdict-giver-names?
                                                                    :boardname-sv
                                                                    :title-sv)}
                                                           {:dict (if verdict-giver-names?
                                                                    :boardname-en
                                                                    :title-en)}]
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

(defn- add-repeating-field
  "Adds a column to the given repeating (i.e. table) settings configuration"
  [settings {:keys [main-dict title-loc field-kw field-title-kw field-schema required-label?] :or {required-label? true}}]
  (-> settings
      (assoc-in [:dictionary field-title-kw] {:css      (when required-label?
                                                          [:pate-label.required])
                                              :loc-text title-loc})
      (assoc-in [:dictionary main-dict :repeating field-kw]
                field-schema)
      (update-in [:section :grid :rows 0 :row] #(concat % [{:dict field-title-kw}]))
      (update-in [:section :grid :rows 1 :row 0 :grid :rows 0 :row]
                 #(concat (butlast %)
                          [{:align :full :dict field-kw}]
                          [(last %)]))))

(defn setsub-plans [& {:keys [ryhti-data-2026]}]
  (cond-> (settings-repeating {:dict           :plans
                               :loc-prefix     :pate-settings.plans
                               :add-dict       :add-plan
                               :add-loc        :pate.add-plan
                               :remove-dict    :remove-plan
                               :move-up-dict   :move-up-plan
                               :move-down-dict :move-down-plan
                               :manual-dict    :plan-index})
    ryhti-data-2026
    (add-repeating-field {:main-dict      :plans
                          :title-loc      :pate.plan-type
                          :field-kw       :type
                          :field-title-kw :title-plan-type
                          :field-schema   (schema-util/required {:select {:label?     false
                                                                          :loc-prefix :suomi.fi.lupamaarayksen-laji
                                                                          :items      helper/plan-condition-type-items
                                                                          :sort-by    :text}})})))

(defn setsub-reviews [review-types]
  (-> (settings-repeating {:dict           :reviews
                           :loc-prefix     :pate-settings.reviews
                           :add-dict       :add-review
                           :add-loc        :pate.add-review
                           :remove-dict    :remove-review
                           :move-up-dict   :move-up-review
                           :move-down-dict :move-down-review
                           :manual-dict    :review-index})
      (add-repeating-field {:main-dict      :reviews
                            :title-loc      :pate.review-type
                            :field-kw       :type
                            :field-title-kw :title-type
                            :field-schema   (schema-util/required {:select {:label?     false
                                                                            :loc-prefix :pate.review-type
                                                                            :items      review-types
                                                                            :sort-by    :text}})})))

(def setsub-handler-titles
  (settings-repeating {:dict        :handler-titles
                       :loc-prefix  :pate-settings.handler-titles
                       :add-dict    :add-handler-title
                       :add-loc     :pate.add-handler-title
                       :remove-dict :remove-handler-title}))

(def setsub-verdict-givers-base
  (-> (settings-repeating {:dict           :verdict-givers
                           :loc-prefix     :pate-settings.verdict-givers
                           :add-dict       :add-verdict-giver
                           :add-loc        :pate.add-verdict-giver
                           :remove-dict    :remove-verdict-giver
                           :move-up-dict   :move-up-verdict-giver
                           :move-down-dict :move-down-verdict-giver
                           :manual-dict    :verdict-giver-index})
      (add-repeating-field {:main-dict      :verdict-givers
                            :title-loc      :pate.settings.verdict-giver.code
                            :field-kw       :code
                            :field-title-kw :title-verdict-giver-code
                            :field-schema   (schema-util/required {:text {:label? false}})})))

(def setsub-verdict-givers
  "Extended verdict-givers settings with first-name/last-name fields.
   Used for R and P verdicts where viranhaltija needs separate name fields."
  (-> (settings-repeating {:dict                 :verdict-givers
                           :loc-prefix           :pate-settings.verdict-givers
                           :add-dict             :add-verdict-giver
                           :add-loc              :pate.add-verdict-giver
                           :remove-dict          :remove-verdict-giver
                           :move-up-dict         :move-up-verdict-giver
                           :move-down-dict       :move-down-verdict-giver
                           :manual-dict          :verdict-giver-index
                           :verdict-giver-names? true})
      (add-repeating-field {:main-dict      :verdict-givers
                            :title-loc      :pate.settings.verdict-giver.code
                            :field-kw       :code
                            :field-title-kw :title-verdict-giver-code
                            :field-schema   (schema-util/required {:text {:label? false}})})
      (add-repeating-field {:main-dict       :verdict-givers
                            :title-loc       :pate.settings.verdict-giver.first-name
                            :field-kw        :first-name
                            :field-title-kw  :title-verdict-giver-firstname
                            :field-schema    {:text {:label? false}}
                            :required-label? false})
      (add-repeating-field {:main-dict       :verdict-givers
                            :title-loc       :pate.settings.verdict-giver.last-name
                            :field-kw        :last-name
                            :field-title-kw  :title-verdict-giver-last-name
                            :field-schema    {:text {:label? false}}
                            :required-label? false})))

(def setsub-verdict-giver-titles
  (-> (settings-repeating {:dict           :verdict-giver-titles
                           :loc-prefix     :pate-settings.verdict-giver-titles
                           :add-dict       :add-verdict-giver-title
                           :add-loc        :pate.add-verdict-giver-title
                           :remove-dict    :remove-verdict-giver-title
                           :move-up-dict   :move-up-verdict-giver-title
                           :move-down-dict :move-down-verdict-giver-title
                           :manual-dict    :verdict-giver-title-index})
      (add-repeating-field {:main-dict      :verdict-giver-titles
                            :title-loc      :pate.settings.verdict-giver.code
                            :field-kw       :code
                            :field-title-kw :title-verdict-giver-title-code
                            :field-schema   (schema-util/required {:text {:label? false}})})))

(def r-settings (build-settings-schema "pate-r"
                                       (setsub-organization-name)
                                       (setsub-announcement-dates :r)
                                       setsub-verdict-code
                                       setsub-board
                                       (setsub-lang-titles false)
                                       (setsub-verdict-giver-lang-titles false)
                                       (setsub-plans :ryhti-data-2026 true)
                                       (setsub-reviews helper/available-review-types)
                                       setsub-verdict-giver-titles
                                       setsub-verdict-givers))

(def p-settings (build-settings-schema "pate-p"
                                       (setsub-organization-name)
                                       (setsub-announcement-dates :p)
                                       setsub-verdict-code
                                       setsub-board
                                       (setsub-lang-titles false)
                                       (setsub-verdict-giver-lang-titles false)
                                       setsub-verdict-giver-titles
                                       setsub-verdict-givers))

(def ya-settings (build-settings-schema "pate-ya"
                                        (setsub-organization-name)
                                        (setsub-date-deltas :ya)
                                        setsub-verdict-code
                                        setsub-board
                                        (setsub-lang-titles true)
                                        (setsub-plans)
                                        setsub-handler-titles
                                        (setsub-reviews helper/ya-review-types)
                                        setsub-verdict-giver-titles
                                        setsub-verdict-givers-base))

(def tj-settings (build-settings-schema "pate-tj"
                                        (setsub-organization-name)
                                        (setsub-date-deltas :tj)
                                        setsub-verdict-code
                                        setsub-board
                                        (setsub-lang-titles true)
                                        setsub-verdict-giver-titles
                                        setsub-verdict-givers-base))

(def contract-settings (build-settings-schema "pate-contract"
                                              (setsub-organization-name :pate.contract.organization-name)))

(def oik-settings (build-settings-schema "pate-oik"
                                         (setsub-organization-name)
                                         (setsub-announcement-dates :oik)
                                         setsub-verdict-code
                                         (setsub-lang-titles true)))


(defn settings-schema
  "Determines the available settings for a given verdict category.
  These are the settings which are shared between all verdict templates of the same category (r, p, oik, etc.)
  and which are copied onto the verdict template when it is published.

  This is one of _four_ definitions needed for a PATE verdict category:
  For the verdict layout, see `lupapalvelu.pate.pdf-layouts/pdf-layout`
  For the editor schema, see `lupapalvelu.pate.verdict-schemas/verdict-schema`
  For the verdict template, see `lupapalvelu.pate.verdict-template-schemas/verdict-template-schema`"
  [category]
  (case (keyword category)
    :r r-settings
    :p p-settings
    :ya ya-settings
    :tj tj-settings
    :contract contract-settings
    :oik oik-settings))
