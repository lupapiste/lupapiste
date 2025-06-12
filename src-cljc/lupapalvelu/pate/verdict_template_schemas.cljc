(ns lupapalvelu.pate.verdict-template-schemas
  (:require [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [sade.util :as util]
            [schema.core :as sc]))

;; -----------------------------
;; Verdict template subschemas
;; -----------------------------

(defn- settings-link [& kvs]
  {:excluded? true
   :link      (merge {:text-loc :pate.settings-link
                      :click    :open-settings}
                     (apply hash-map kvs))})

(def ^:private valid-pate-verdict-template! (sc/validator schemas/PateVerdictTemplate))

(defn build-verdict-template-schema
  "Some additions to `schema-util/combine-subschemas`: If a subschema has
  a :removable?  flag, then the section is added
  to :removed-sections. The resulting schema is validated."
  [& subschemas]
  (->> subschemas
       (cons {:dictionary
              {:removed-sections
               {:keymap
                (zipmap (reduce (fn [acc subschema]
                                  (cond-> acc
                                    (:removable? subschema)
                                    (concat (map (comp keyword :id)
                                                 (schema-util/subschema-sections subschema)))))
                                []
                                subschemas)
                        (repeat false))}}})
       (apply schema-util/combine-subschemas)
       valid-pate-verdict-template!))


(defn text-subschema
  "Template subschema that only shows text. Used for selecting whether
  the section is included in the verdict. Text section is removable by
  default, but this can be overridden with removable? parameter."
  ([dict loc-text removable?]
   {:dictionary {dict {:loc-text loc-text}}
    :section    (helper/text-section dict)
    :removable? removable?})
  ([dict loc-ext]
   (text-subschema dict loc-ext true)))

(defn temsub-verdict
  [category & {:keys [kuulutus?]}]
  (let [dates              (helper/verdict-dates category)
        dates-kuulutus     (when kuulutus?
                             (helper/kuulutus-dates category))
        {delta-dic :dictionary
         delta-row :row}   (helper/date-deltas dates :custom-date-deltas)
        {delta-dic-k :dictionary
         delta-row-k :row} (when kuulutus?
                             (helper/date-deltas dates-kuulutus :custom-date-deltas))]
    {:dictionary (merge (util/map-values #(assoc % :excluded? true) delta-dic)
                        (util/map-values #(assoc % :excluded? true) delta-dic-k)
                        {:language                   helper/language-select
                         :title                      {:text {:i18nkey     :pate-verdict.title
                                                             :placeholder :pate-verdict.title.placeholder
                                                             :items       :*ref.titles.titles}}
                         :subtitle                   {:text {:i18nkey     :pate-verdict.subtitle
                                                             :placeholder :pate-verdict.title.placeholder
                                                             :items       :*ref.titles.subtitles}}
                         :verdict-dates              {:multi-select {:items           dates
                                                                     :sort?           false
                                                                     :i18nkey         (if kuulutus?
                                                                                        :pate-verdict-dates-julkipano
                                                                                        :pate-verdict-dates)
                                                                     :item-loc-prefix :pate-verdict}}
                         :giver                      (schema-util/required helper/verdict-giver-select)
                         :verdict-code               {:reference-list {:path       :settings.verdict-code
                                                                       :type       :select
                                                                       :loc-prefix :pate.verdict-code}}
                         :verdict-code-link          (settings-link :loc-prefix :pate.verdict-code)
                         :paatosteksti               {:phrase-text {:loc-prefix :pate-verdict
                                                                    :category   :paatosteksti}}
                         :proposaltext               {:phrase-text {:loc-prefix :pate-verdict-proposal
                                                                    :category   :paatosteksti}}
                         :custom-date-deltas         {:excluded? true
                                                      :toggle    {:i18nkey  :pate.use-settings-date-deltas
                                                                  :inverse? true}}
                         :julkipano-dates-help-text  {:loc-text :pate-julkipano-dates-help-text}
                         :kuulutus-dates-help-text   {:loc-text :pate-kuulutus-dates-help-text}
                         :kuulutus-dates-help-text-2 {:loc-text :pate-kuulutus-dates-help-text-2}}
                        (when kuulutus?
                          {:kuulutus-dates {:multi-select {:items           dates-kuulutus
                                                           :sort?           false
                                                           :i18nkey         :pate-verdict-dates-kuulutus
                                                           :item-loc-prefix :pate-verdict}}}))
     :section    {:id         :verdict
                  :loc-prefix :pate-verdict-template.verdict-info
                  :grid       {:columns 12
                               :rows    [[{:col  3
                                           :dict :language}
                                          {:col   4
                                           :align :full
                                           :dict  :title}
                                          {}
                                          {:col   4
                                           :align :full
                                           :dict  :subtitle}]
                                         [{:col  12
                                           :dict :custom-date-deltas}]
                                         (when kuulutus?
                                           [{:show? :custom-date-deltas
                                             :col   20
                                             :dict  :julkipano-dates-help-text}])
                                         [{:show? :custom-date-deltas
                                           :col   12
                                           :grid  {:columns 20
                                                   :rows    [delta-row]}}]
                                         (when kuulutus?
                                           [{:show? :custom-date-deltas
                                             :col   20
                                             :dict  :kuulutus-dates-help-text}])
                                         (when kuulutus?
                                           [{:show? :custom-date-deltas
                                             :col   20
                                             :dict  :kuulutus-dates-help-text-2}])
                                         [{:show? :custom-date-deltas
                                           :col   12
                                           :grid  {:columns 20
                                                   :rows    [delta-row-k]}}]
                                         [{:col  12
                                           :dict :verdict-dates}]
                                         (when kuulutus?
                                           [{:col  12
                                             :dict :kuulutus-dates}])
                                         [{:col  3
                                           :dict :giver}
                                          {:align :full
                                           :col   3
                                           :hide? :*ref.settings.verdict-code
                                           :dict  :verdict-code-link}
                                          {:align :full
                                           :col   4
                                           :show? :*ref.settings.verdict-code
                                           :dict  :verdict-code}]
                                         [{:col  12
                                           :dict :paatosteksti}]
                                         [{:col   12
                                           :hide? :giver=viranhaltija
                                           :dict  :proposaltext}]]}}}))

(def temsub-rectification-verdict
  (let [dates              (helper/verdict-dates :oik)
        dates-kuulutus     (helper/kuulutus-dates :oik)
        {delta-dic :dictionary
         delta-row :row}   (helper/date-deltas dates :custom-date-deltas)
        {delta-dic-k :dictionary
         delta-row-k :row} (helper/date-deltas dates-kuulutus :custom-date-deltas)
        ]
    {:dictionary (merge (util/map-values #(assoc % :excluded? true) delta-dic)
                        (util/map-values #(assoc % :excluded? true) delta-dic-k)
                        {:language                helper/language-select
                         :title                   {:text {:i18nkey     :pate-verdict.title
                                                          :placeholder :pate-verdict.title.placeholder
                                                          :items       :*ref.titles.titles}}
                         :subtitle                {:text {:i18nkey     :pate-verdict.subtitle
                                                          :placeholder :pate-verdict.title.placeholder
                                                          :items       :*ref.titles.subtitles}}
                         :verdict-code            {:reference-list {:path       :settings.verdict-code
                                                                    :type       :select
                                                                    :loc-prefix :pate.verdict-code}}
                         :verdict-code-link       (settings-link :loc-prefix :pate.verdict-code)
                         :paatosteksti            {:phrase-text {:loc-prefix :pate-verdict
                                                                 :category   :paatosteksti}}
                         :proposal-text           {:phrase-text {:loc-prefix :pate-verdict-proposal
                                                                 :category   :paatosteksti}}
                         :proposal-reasoning-text {:phrase-text {:loc-prefix :pate-verdict-proposal-reasoning
                                                                 :category   :paatosteksti}}
                         :verdict-dates      {:multi-select {:items           dates
                                                             :sort?           false
                                                             :i18nkey         :pate-verdict-dates-julkipano
                                                             :item-loc-prefix :pate-verdict}}
                         :custom-date-deltas      {:excluded? true
                                                   :toggle    {:i18nkey  :pate.use-settings-date-deltas
                                                               :inverse? true}}
                         :julkipano-dates-help-text  {:loc-text :pate-julkipano-dates-help-text}
                         :kuulutus-dates-help-text   {:loc-text :pate-kuulutus-dates-help-text}
                         :kuulutus-dates-help-text-2 {:loc-text :pate-kuulutus-dates-help-text-2}}
                          {:kuulutus-dates {:multi-select {:items           dates-kuulutus
                                                           :sort?           false
                                                           :i18nkey         :pate-verdict-dates-kuulutus
                                                           :item-loc-prefix :pate-verdict}}})
     :section    {:id         :verdict
                  :loc-prefix :pate-verdict-template.verdict-info
                  :grid       {:columns 12
                               :rows    [[{:col  3
                                           :dict :language}
                                          {:col   4
                                           :align :full
                                           :dict  :title}
                                          {}
                                          {:col   4
                                           :align :full
                                           :dict  :subtitle}]
                                         [{:col  12
                                           :dict :custom-date-deltas}]
                                         [{:show? :custom-date-deltas
                                           :col   20
                                           :dict  :julkipano-dates-help-text}]
                                         [{:show? :custom-date-deltas
                                           :col   12
                                           :grid  {:columns 20
                                                   :rows    [delta-row]}}]
                                         [{:show? :custom-date-deltas
                                           :col   20
                                           :dict  :kuulutus-dates-help-text}]
                                         [{:show? :custom-date-deltas
                                           :col   20
                                           :dict  :kuulutus-dates-help-text-2}]
                                         [{:show? :custom-date-deltas
                                           :col   12
                                           :grid  {:columns 20
                                                   :rows    [delta-row-k]}}]
                                         [{:col  12
                                           :dict :verdict-dates}]
                                         [{:col  12
                                           :dict :kuulutus-dates}]
                                         [{:align :full
                                           :col   3
                                           :hide? :*ref.settings.verdict-code
                                           :dict  :verdict-code-link}
                                          {:align :full
                                           :col   4
                                           :show? :*ref.settings.verdict-code
                                           :dict  :verdict-code}]
                                         [{:col  12
                                           :dict :paatosteksti}]
                                         [{:col  12
                                           :dict :proposal-text}]
                                         [{:col  12
                                           :dict :proposal-reasoning-text}]]}}}))

(defn temsub-bulletin
  ([]
   (temsub-bulletin {}))
  ([{:keys [kuulutus?]}]
   {:dictionary {:bulletinOpDescription {:phrase-text {:category :toimenpide-julkipanoon
                                                       :i18nkey  (if kuulutus?
                                                                   :phrase.category.toimenpide-julkipanoon-ja-kuulutuksiin
                                                                   :phrase.category.toimenpide-julkipanoon)}}}
    :section    {:id         :bulletin
                 :loc-prefix (if kuulutus? :bulletin-and-kuulutus :bulletin)
                 :grid       {:columns 1
                              :rows    [[{:col  1
                                          :dict :bulletinOpDescription}]]}}
    :removable? true}))

(defn policy [m k]
  (assoc-in m [:section :policy] k))

(def temsub-foremen
  (->> helper/foreman-codes
       (map (fn [code]
              (let [codename     (name code)
                    included-key (keyword (str codename "-included"))]
                {:codename     codename
                 :toggle-key   code
                 :included     {:toggle {:i18nkey (keyword (str "pate-r.foremen."
                                                                codename))
                                         :label?  false}}
                 :included-key included-key
                 :toggle       {:toggle {:i18nkey  :pate.available-in-verdict
                                         :enabled? included-key
                                         :label?   false}}
                 :move-up      (keyword (str codename "-up"))
                 :move-down    (keyword (str codename "-down"))})))

       (reduce (fn [acc {:keys [codename toggle-key toggle included-key included move-up move-down]}]
                 (-> acc
                     (assoc-in [:dictionary toggle-key] toggle)
                     (assoc-in [:dictionary included-key] included)
                     (assoc-in [:dictionary move-up] {:excluded? true
                                                      :button    {:label? false
                                                                  :text?  false
                                                                  :icon   :lupicon-arrow-up
                                                                  :css    [:primary :outline]
                                                                  :move   {:direction :up
                                                                           :row-order :tj-order
                                                                           :row-id    codename}}})
                     (assoc-in [:dictionary move-down] {:excluded? true
                                                        :button    {:label? false
                                                                    :text?  false
                                                                    :icon   :lupicon-arrow-down
                                                                    :css    [:primary :outline]
                                                                    :move   {:direction :down
                                                                             :row-order :tj-order
                                                                             :row-id    codename}}})
                     (update-in [:section :grid :rows]
                                #(conj (vec %)
                                       {:css :row--tight
                                        :id  codename
                                        :row [{:col  2
                                               :dict included-key}
                                              {:col  1
                                               :dict toggle-key}
                                              {:list {:labels? false
                                                      :items   [{:dict move-down}
                                                                {:dict move-up}]}}]}))))
               {:dictionary {:tj-order {:row-order (mapv name helper/foreman-codes)}}
                :section    {:id         :foremen
                             :loc-prefix :pate-r.foremen
                             :policy     :copy
                             :grid       {:columns   6
                                          :row-order :tj-order
                                          :rows      []}}
                :removable? true})))

(defn settings-dependencies
  ([dict loc-prefix sorting selectable?]
   (let [link-dict (-> (name dict) (str  "-link") keyword)]
     {:dictionary {dict      {:repeating (cond-> {:included {:toggle {:label?    false
                                                                      :text-dict :text}}
                                                  :text-fi  {:text {:read-only? true}}
                                                  :text-sv  {:text {:read-only? true}}
                                                  :text-en  {:text {:read-only? true}}}
                                           ;; If this option can be selected to be on by default
                                           selectable?
                                           (assoc :selected {:toggle {:enabled? :-.included
                                                                      :label?   false
                                                                      :i18nkey  :pate.available-in-verdict}}))
                              :sort-by   sorting}
                   link-dict (settings-link :label? false)}
      :section    {:id         dict
                   :loc-prefix loc-prefix
                   :grid       {:columns 6
                                :rows    [{:hide? dict
                                           :css   :row--extra-tight
                                           :row   [{:col  4
                                                    :dict link-dict}]}
                                          {:css :row--extra-tight
                                           :row [{:col   4
                                                  :show? dict
                                                  :grid  {:columns   4
                                                          :repeating dict
                                                          :rows      [{:css :row--extra-tight
                                                                       :row (cond-> [{:col  2
                                                                                      :dict :included}]
                                                                              selectable?
                                                                              (conj {:col  1
                                                                                     :dict :selected}))}]}}]}]}}
      :removable? true}))
  ([dict loc-prefix selectable?]
   (settings-dependencies dict loc-prefix {:prefix :text} selectable?))
  ([dict loc-prefix]
   (settings-dependencies dict loc-prefix true)))

(def temsub-reviews
  (policy (settings-dependencies :reviews :pate-reviews {:manual :review-index} true)
          :copy))

(def temsub-plans
  (policy (settings-dependencies :plans :pate.plans {:manual :plan-index} true)
          :copy))

(def temsub-handler-titles
  (settings-dependencies :handler-titles :pate.handler-titles))

(defn temsub-verdict-givers
  "Creates verdict-givers template section with optional bulletin checkbox.
   When show-warnings? is true, adds warning disclaimers when a verdict giver
   is not valid for the selected giver type:
   - viranhaltija requires first-name and last-name
   - lautakunta requires fi, sv, and en names"
  ([{:keys [bulletin-check? verdict-giver-validation?]}]
   (let [base (settings-dependencies :verdict-givers :pate.verdict-givers false)]
     (cond-> base
       verdict-giver-validation?
       (-> ;; See fn sync-repeatings where these valid-for-* values added
         (assoc-in [:dictionary :verdict-givers :repeating :valid-for-viranhaltija]
                   {:toggle {:label? false}})
         (assoc-in [:dictionary :verdict-givers :repeating :valid-for-lautakunta]
                   {:toggle {:label? false}})
         (assoc-in [:dictionary :verdict-givers :repeating :warning-viranhaltija]
                   {:loc-text :pate.verdict-giver.warning-no-name
                    :css      [:txt--small]})
         (assoc-in [:dictionary :verdict-givers :repeating :warning-lautakunta]
                   {:loc-text :pate.verdict-giver.warning-missing-language
                    :css      [:txt--small]})
         ;; A bit confusing way to add these warnings to the grid
         (update-in [:section :grid :rows 1 :row 0 :grid :rows 0 :row]
                    conj
                    {:show? [:AND :giver=viranhaltija [:NOT :+.valid-for-viranhaltija]]
                     :col   2
                     :dict  :warning-viranhaltija
                     :css [:flex :items-center]}
                    {:show? [:AND :giver=lautakunta [:NOT :+.valid-for-lautakunta]]
                     :col   2
                     :dict  :warning-lautakunta
                     :css [:flex :items-center]}))
       bulletin-check?
       (-> (assoc-in [:dictionary :bulletin-code-giver]
                     {:toggle {:i18nkey :pate.bulletin-code-giver
                               :label?  false}})
           (assoc-in [:dictionary :bulletin-title]
                     {:loc-text :bulletin-and-kuulutus
                      :css      :txt--bold})
           (update-in [:section :grid :rows]
                      conj
                      [{:col  6
                        :dict :bulletin-title}]
                      {:css :row--extra-tight
                       :row [{:col  6
                              :dict :bulletin-code-giver}]})))))
  ([] (temsub-verdict-givers {})))

(def temsub-verdict-giver-titles
  (settings-dependencies :verdict-giver-titles :pate.verdict-giver-titles false))

(defn temsub-conditions [& {:keys [ryhti-data-2026]}] ;; Muut lupaehdot
  {:dictionary    {:conditions    {:repeating {:condition        {:phrase-text {:i18nkey  :pate-condition
                                                                                :category :lupaehdot}}
                                               :condition-type   helper/other-condition-type
                                               :remove-condition {:button {:i18nkey :remove
                                                                           :label?  false
                                                                           :icon    :lupicon-remove
                                                                           :css     [:primary :outline]
                                                                           :remove  :conditions}}}}
                   :add-condition {:button {:icon    :lupicon-circle-plus
                                            :i18nkey :pate-conditions.add
                                            :label? false
                                            :css     :positive
                                            :add     :conditions}}}
   :section       {:id         :conditions
                   :loc-prefix :phrase.category.lupaehdot
                   :grid       {:columns 1
                                :rows    [[{:grid {:columns   8
                                                   :repeating :conditions
                                                   :rows      [[{:col  6
                                                                 :dict :condition}
                                                                {}
                                                                {:dict :remove-condition}]
                                                               [(if ryhti-data-2026
                                                                  {:col 2
                                                                   :dict :condition-type}
                                                                  {})]]}}]
                                          [{:dict :add-condition}]]}}
   :removable? true})

(def temsub-neighbors (text-subschema :neighbors :pate-neighbors.text))

(def temsub-appeal
  {:dictionary {:appeal {:phrase-text {:category :muutoksenhaku
                                       :i18nkey  :pate-appeal-title}}}
   :section    {:id         :appeal
                :loc-prefix :pate-appeal
                :grid       {:columns 1
                             :rows    [[{:dict :appeal }]]}}
   :removable? true})

(def temsub-rationale
  {:dictionary {:rationale {:phrase-text {:category :yleinen
                                          :i18nkey  :pate.rationale}}}
   :section    {:id         :rationale
                :loc-prefix :pate.rationale
                :grid       {:columns 1
                             :rows    [[{:dict :rationale}]]}}
   :removable? true})

(def temsub-legalese
  {:dictionary {:legalese {:phrase-text {:category :yleinen
                                         :i18nkey  :pate.legalese}}}
   :section    {:id         :legalese
                :loc-prefix :pate.legalese
                :grid       {:columns 1
                             :rows    [[{:dict :legalese}]]}}
   :removable? true})

(def temsub-collateral (text-subschema :collateral :pate-collateral.text))

(def temsub-complexity
  {:dictionary {:complexity      helper/complexity-select
                :complexity-text {:phrase-text {:i18nkey  :pate-complexity.text
                                                :label?   false
                                                :category :vaativuus}}}
   :section    {:id         :complexity
                :loc-prefix :pate.complexity
                :grid       {:columns 1
                             :rows    [[{:dict :complexity }]
                                       [{:id   "text"
                                         :dict :complexity-text}]]}}
   :removable? true})

(def temsub-extra-info (text-subschema :extra-info :pate-extra-info.text))

(def temsub-deviations ;; Poikkeamiset
  (text-subschema :deviations :pate-deviations.text))
(def temsub-rights ;; Rakennusoikeus
  (text-subschema :rights :pate-rights.text))
(def temsub-purpose ;; Kaavan käyttötarkoitus
  (text-subschema :purpose :pate-purpose.text))
(def temsub-statements (text-subschema :statements :pate-statements.text))

(def temsub-buildings
  {:dictionary (reduce (fn [acc k]
                         (assoc acc k {:toggle {:i18nkey (util/kw-path
                                                          :pate-buildings.info k)}}))
                       {}
                       [:autopaikat :vss-luokka :paloluokka :kokoontumistilanHenkilomaara])
   :section
   {:id         :buildings
    :loc-prefix :pate-buildings
    :grid       {:columns 1
                 :rows    [[{:list {:title   "pate-buildings.info"
                                    :labels? false
                                    :items   (mapv (fn [check]
                                                     {:dict check
                                                      :css  [:pate-condition-box]})
                                                   [:autopaikat :vss-luokka :paloluokka :kokoontumistilanHenkilomaara])}}]]}}
   :removable? true})

(def temsub-attachments
  {:dictionary    {:upload {:toggle {:i18nkey :pate.attachments.upload}}}
   :section       {:id         :attachments
                   :loc-prefix :application.verdict-attachments
                   :grid       {:columns 1
                                :rows    [[{
                                            :list       {:labels? false
                                                         :items   [{:dict :upload}]}}]]}}
   :removable? true})

(def temsub-processing
  {:dictionary {:processing {:phrase-text {:category :kasittelyaika
                                           :i18nkey  :pate-processing-title}}}
   :section    {:id         :processing
                :loc-prefix :pate-processing
                :grid       {:columns 1
                             :rows    [[{:dict :processing }]]}}
   :removable? true})

(def r-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict :r {:kuulutus? true})
                                 temsub-rationale
                                 temsub-legalese
                                 temsub-processing
                                 (temsub-bulletin {:kuulutus? true})
                                 temsub-foremen
                                 temsub-reviews
                                 temsub-plans
                                 (temsub-conditions :ryhti-data-2026 true)
                                 temsub-neighbors
                                 temsub-appeal
                                 temsub-complexity
                                 temsub-extra-info
                                 temsub-deviations
                                 temsub-rights
                                 temsub-purpose
                                 temsub-statements
                                 temsub-buildings
                                 temsub-attachments
                                 temsub-verdict-giver-titles
                                 (temsub-verdict-givers {:bulletin-check?           true
                                                         :verdict-giver-validation? true})))

(def temsub-start-info
  (text-subschema :start-info :pate-start-info.text))

(def p-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict :p {:kuulutus? true})
                                 (temsub-bulletin {:kuulutus? true})
                                 (temsub-conditions :ryhti-data-2026)
                                 temsub-neighbors
                                 temsub-appeal
                                 temsub-start-info
                                 temsub-deviations
                                 temsub-rights
                                 temsub-purpose
                                 temsub-statements
                                 temsub-attachments
                                 temsub-verdict-giver-titles
                                 (temsub-verdict-givers {:bulletin-check?           true
                                                         :verdict-giver-validation? true})))

(def temsub-reviews-with-phrase
  (-> (settings-dependencies :reviews :pate-reviews)
      (assoc-in [:dictionary :review-info] {:phrase-text {:i18nkey  :pate.review-info
                                                          :category :yleinen}})
      (update-in [:section :grid :rows] conj [{:col   4
                                               :dict  :review-info}])
      (policy :include)))

(def temsub-inform-others
  (text-subschema :inform-others :pate-inform-others.text))

(def ya-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict :ya)
                                 (temsub-bulletin)
                                 temsub-reviews-with-phrase
                                 temsub-plans
                                 temsub-handler-titles
                                 (temsub-conditions)
                                 temsub-inform-others
                                 temsub-appeal
                                 temsub-statements
                                 temsub-attachments
                                 temsub-verdict-giver-titles
                                 (temsub-verdict-givers)))

(def tj-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict :tj)
                                 temsub-appeal
                                 temsub-attachments
                                 temsub-verdict-giver-titles
                                 (temsub-verdict-givers)))

(def oik-verdict-template-schema ;; verdict template here
  (build-verdict-template-schema temsub-rectification-verdict
                                 temsub-attachments))

(def temsub-contract
  {:dictionary {:language      helper/contract-language
                :title         {:text      {:i18nkey     :pate-contract.title
                                            :placeholder :pate-verdict.title.placeholder
                                            :items       :*ref.titles.titles}}
                :subtitle      {:text      {:i18nkey     :pate-contract.subtitle
                                            :placeholder :pate-verdict.title.placeholder
                                            :items       :*ref.titles.subtitles}}
                :contract-text {:phrase-text {:i18nkey  :verdict.contract.text
                                              :category :sopimus}}}
   :section    {:id         :contract
                :loc-prefix :pate.contract.template.info
                :grid       {:columns 12
                             :rows    [[{:col  3
                                         :dict :language}
                                        {:col   4
                                         :align :full
                                         :dict  :title}
                                        {}
                                        {:col   4
                                         :align :full
                                         :dict  :subtitle}]
                                       [{:col  12
                                         :dict :contract-text}]]}}})

(def temsub-contract-conditions ;; Sopimusehdot
  (-> (temsub-conditions)
      (assoc-in [:dictionary :conditions :repeating :condition :phrase-text :i18nkey]
                :pate.contract.condition)
      (assoc-in [:dictionary :add-condition :button :i18nkey]
                :pate.contract.add-condition)
      (assoc-in [:section :loc-prefix] :pate.contract.conditions)))

(def temsub-contract-attachments
  (assoc-in (text-subschema :attachments :pate.contract.attachments-text)
            [:section :loc-prefix] :verdict.contract.attachments))

(def contract-template-schema
  (build-verdict-template-schema temsub-contract
                                 temsub-contract-conditions
                                 temsub-contract-attachments))

(defn verdict-template-schema
  "Determines the contents of a PATE verdict template for a given verdict category.
  Unlike settings, verdict templates need to be published to be used which then
  causes the current settings values to be copied to that version of the template.

  This is one of _four_ definitions needed for a PATE verdict category:
  For the verdict layout, see `lupapalvelu.pate.pdf-layouts/pdf-layout`
  For the editor schema, see `lupapalvelu.pate.verdict-schemas/verdict-schema`
  For the verdict template settings, see `lupapalvelu.pate.settings-schemas/settings-schema`"
  [category]
  (case (keyword category)
    :r        r-verdict-template-schema
    :p        p-verdict-template-schema
    :ya       ya-verdict-template-schema
    :tj       tj-verdict-template-schema
    :oik      oik-verdict-template-schema
    :contract contract-template-schema))
