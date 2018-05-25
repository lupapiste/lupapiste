(ns lupapalvelu.pate.shared
  "Schema instantiations for Pate schemas."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [sade.shared-util :as util]
            [schema.core :as sc]))

(def supported-languages [:fi :sv :en])

;; identifier - KuntaGML-paatoskoodi (yhteiset.xsd)
(def verdict-code-map
  {:annettu-lausunto            "annettu lausunto"
   :asiakirjat-palautettu       "asiakirjat palautettu korjauskehotuksin"
   :ehdollinen                  "ehdollinen"
   :ei-lausuntoa                "ei lausuntoa"
   :ei-puollettu                "ei puollettu"
   :ei-tiedossa                 "ei tiedossa"
   :ei-tutkittu-1               "ei tutkittu"
   :ei-tutkittu-2               "ei tutkittu (oikaisuvaatimusvaatimus tai lupa pysyy puollettuna)"
   :ei-tutkittu-3               "ei tutkittu (oikaisuvaatimus tai lupa pysyy ev\u00e4ttyn\u00e4)"
   :evatty                      "ev\u00e4tty"
   :hallintopakko               "hallintopakon tai uhkasakkoasian k\u00e4sittely lopetettu"
   :hyvaksytty                  "hyv\u00e4ksytty"
   :ilmoitus-tiedoksi           "ilmoitus merkitty tiedoksi"
   :konversio                   "muutettu toimenpideluvaksi (konversio)"
   :lautakunta-palauttanut      "asia palautettu uudelleen valmisteltavaksi"
   :lautakunta-poistanut        "asia poistettu esityslistalta"
   :lautakunta-poydalle         "asia pantu p\u00f6yd\u00e4lle kokouksessa"
   :maarays-peruutettu          "m\u00e4\u00e4r\u00e4ys peruutettu"
   :muutti-evatyksi             "muutti ev\u00e4tyksi"
   :muutti-maaraysta            "muutti m\u00e4\u00e4r\u00e4yst\u00e4 tai p\u00e4\u00e4t\u00f6st\u00e4"
   :muutti-myonnetyksi          "muutti my\u00f6nnetyksi"
   :myonnetty                   "my\u00f6nnetty"
   :myonnetty-aloitusoikeudella "my\u00f6nnetty aloitusoikeudella"
   :osittain-myonnetty          "osittain my\u00f6nnetty"
   :peruutettu                  "peruutettu"
   :puollettu                   "puollettu"
   :pysytti-evattyna            "pysytti ev\u00e4ttyn\u00e4"
   :pysytti-maarayksen-2        "pysytti m\u00e4\u00e4r\u00e4yksen tai p\u00e4\u00e4t\u00f6ksen"
   :pysytti-myonnettyna         "pysytti my\u00f6nnettyn\u00e4"
   :pysytti-osittain            "pysytti osittain my\u00f6nnettyn\u00e4"
   :siirretty-maaoikeudelle     "siirretty maaoikeudelle"
   :suunnitelmat-tarkastettu    "suunnitelmat tarkastettu"
   :tehty-hallintopakkopaatos-1 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (ei velvoitetta)"
   :tehty-hallintopakkopaatos-2 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (asetettu velvoite)"
   :tehty-uhkasakkopaatos       "tehty uhkasakkop\u00e4\u00e4t\u00f6s"
   :tyohon-ehto                 "ty\u00f6h\u00f6n liittyy ehto"
   :valituksesta-luovuttu-1     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)"
   :valituksesta-luovuttu-2     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy ev\u00e4ttyn\u00e4)"})

(def review-type-map
  {:muu-katselmus             "muu katselmus"
   :muu-tarkastus             "muu tarkastus"
   :aloituskokous             "aloituskokous"
   :paikan-merkitseminen      "rakennuksen paikan merkitseminen"
   :paikan-tarkastaminen      "rakennuksen paikan tarkastaminen"
   :pohjakatselmus            "pohjakatselmus"
   :rakennekatselmus          "rakennekatselmus"
   :lvi-katselmus             "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"
   :osittainen-loppukatselmus "osittainen loppukatselmus"
   :loppukatselmus            "loppukatselmus"
   :ei-tiedossa               "ei tiedossa"})

(def ya-review-type-map
  {:aloituskatselmus "Aloituskatselmus"
   :loppukatselmus   "Loppukatselmus"
   :valvonta         "Muu valvontak\u00e4ynti"})

;; Each type must be part of the corresponding operation names (e.g.,
;; ya-katulupa-maalampotyot -> katulupa)
(def ya-verdict-types [:sijoituslupa :kayttolupa :katulupa :jatkoaika])

(def foreman-codes [:vastaava-tj :vv-tj :iv-tj :erityis-tj :tj])

;; Verdict dates must include every possible date key.
(def verdict-dates [:julkipano :anto :muutoksenhaku :lainvoimainen
                    :aloitettava :voimassa])

(def ya-verdict-dates [:julkipano :anto :muutoksenhaku :lainvoimainen
                       :aloitettava])

(def tj-verdict-dates [:anto :lainvoimainen :muutoksenhaku])

(def review-types (keys review-type-map))

(def ya-review-types (keys ya-review-type-map))

(defn reference-list [path extra]
  {:reference-list (merge {:label? false
                           :type   :multi-select
                           :path   path}
                          extra)})

(defn  multi-section [id link-ref]
  {:loc-prefix (str "pate-" (name id))
   :id         id
   :grid       {:columns 1
                :rows    [[{:align      :full
                            :loc-prefix (str "pate-settings." (name id))
                            :hide?      link-ref
                            :dict       :link-to-settings-no-label}
                           {:show? link-ref
                            :dict  id }]]}})

(defn text-section [id]
  {:loc-prefix (str "pate-" (name id))
   :id         id
   :grid       {:columns 1
                :rows    [[{:dict id}]]}})

(defn date-delta-row [kws]
  (->> kws
       (reduce (fn [acc kw]
                 (conj acc
                       (when (seq acc)
                         {:dict :plus
                          :css [:date-delta-plus]})
                       {:col 2 :dict kw :id (name kw)}))
               [])
       (remove nil?)))

(def language-select {:select {:loc-prefix :pate-verdict.language
                               :items supported-languages}})
(def verdict-giver-select {:select {:loc-prefix :pate-verdict.giver
                                    :items      [:lautakunta :viranhaltija]
                                    :sort-by    :text}})
(def complexity-select {:select {:loc-prefix :pate.complexity
                                 :items      [:small :medium :large :extra-large]}})

(def collateral-type-select {:select {:loc-prefix :pate.collateral-type
                                      :items      [:shekki :panttaussitoumus]
                                      :sort-by    :text}})

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
  (let [date-dicts {:julkipano     (schema-util/required {:date-delta {:unit :days}})
                    :anto          (schema-util/required {:date-delta {:unit :days}})
                    :muutoksenhaku (schema-util/required {:date-delta {:unit :days}})
                    :lainvoimainen (schema-util/required {:date-delta {:unit :days}})
                    :aloitettava   (schema-util/required {:date-delta {:unit :years}})
                    :voimassa      (schema-util/required {:date-delta {:unit :years}})}]
    {:dictionary (merge {:verdict-dates {:loc-text :pate-verdict-dates-settings}
                         :plus          {:loc-text :plus}}
                        (select-keys date-dicts dates))
     :section    {:id         :verdict-dates
                  :loc-prefix :pate-verdict-dates-settings
                  :grid       {:columns    17
                               :loc-prefix :pate-verdict
                               :rows       [(date-delta-row dates)]}}}))

(def setsub-verdict-code
  {:dictionary {:verdict-code
                (schema-util/required {:multi-select {:label? false
                                          :items  (keys verdict-code-map)}})}
   :section    {:id         :verdict
                :required?  true
                :loc-prefix :pate-settings.verdict
                :grid       {:columns    1
                             :loc-prefix :pate-r.verdict-code
                             :rows       [[{:dict :verdict-code}]]}}})

(def setsub-board ;; Lautakunta
  {:dictionary {:lautakunta-muutoksenhaku (schema-util/required {:date-delta {:unit :days}})
                :boardname                (schema-util/required {:text {}})}
   :section {:id         :board
             :loc-prefix :pate-settings.boardname-title
             :grid       {:columns 4
                          :rows    [[{:loc-prefix :pate-verdict.muutoksenhaku
                                      :dict       :lautakunta-muutoksenhaku}]
                                    [{:col        1
                                      :align      :full
                                      :loc-prefix :pate-settings.boardname
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

(def r-settings (build-settings-schema "pate-r"
                                       (setsub-date-deltas verdict-dates)
                                       setsub-verdict-code
                                       setsub-board
                                       setsub-lang-titles
                                       setsub-plans
                                       (setsub-reviews review-types)))

(def p-settings (build-settings-schema "pate-p"
                                       (setsub-date-deltas verdict-dates)
                                       setsub-verdict-code
                                       setsub-board))

(def ya-settings (build-settings-schema "pate-ya"
                                        (setsub-date-deltas ya-verdict-dates)
                                        setsub-verdict-code
                                        setsub-board
                                        setsub-lang-titles
                                        setsub-plans
                                        (setsub-reviews ya-review-types)))

(def tj-settings (build-settings-schema "pate-tj"
                                        (setsub-date-deltas tj-verdict-dates)
                                        setsub-verdict-code
                                        setsub-board))

(defn settings-schema [category]
  (case (keyword category)
    :r r-settings
    :p p-settings
    :ya ya-settings
    :tj tj-settings))

;; -----------------------------
;; Verdict template subschemas
;; -----------------------------

(defn build-verdict-template-schema
  "In addition to `schema-util/combine-subschemas`, the dictionary is
  prefilled with settings-link dicts. If a subschema has a :removable?
  flag, then the section is added to :removed-sections. The resulting
  schema is validated."
  [& subschemas]
  (->> subschemas
       (cons {:dictionary
              {:link-to-settings          {:link {:text-loc :pate.settings-link
                                                  :click    :open-settings}}
               :link-to-settings-no-label {:link {:text-loc :pate.settings-link
                                                  :label?   false
                                                  :click    :open-settings}}
               :removed-sections
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
       (sc/validate schemas/PateVerdictTemplate)))


(defn text-subschema
  "Template subschema that only shows text. Used for selecting whether
  the section is included in the verdict. Text section is removable by
  default, but this can be overridden with removable? parameter."
  ([dict loc-text removable?]
   {:dictionary {dict {:loc-text loc-text}}
    :section    (text-section dict)
    :removable? removable?})
  ([dict loc-ext]
   (text-subschema dict loc-ext true)))

(defn temsub-verdict [dates]
  {:dictionary {:language         language-select
                :verdict-dates    {:multi-select {:items           dates
                                                  :sort?           false
                                                  :i18nkey         :pate-verdict-dates
                                                  :item-loc-prefix :pate-verdict}}
                :giver            (schema-util/required verdict-giver-select)
                :verdict-code     {:reference-list {:path       :settings.verdict-code
                                                    :type       :select
                                                    :loc-prefix :pate-r.verdict-code}}
                :paatosteksti     {:phrase-text {:category :paatosteksti}}}
   :section    {:id         :verdict
                :loc-prefix :pate-verdict-template.verdict-info
                :grid       {:columns 12
                             :rows    [[{:col  6
                                         :dict :language}]
                                       [{:col  12
                                         :dict :verdict-dates}]
                                       [{:col  3
                                         :dict :giver}
                                        {:align      :full
                                         :col        3
                                         :loc-prefix :pate-r.verdict-code
                                         :hide?      :*ref.settings.verdict-code
                                         :dict       :link-to-settings}
                                        {:align :full
                                         :col   3
                                         :show? :*ref.settings.verdict-code
                                         :dict  :verdict-code}]
                                       [{:col  12
                                         :loc-prefix :pate-verdict
                                         :dict :paatosteksti}]]}}})

(def temsub-bulletin
  {:dictionary {:bulletinOpDescription {:phrase-text {:category :toimenpide-julkipanoon
                                                      :i18nkey  :phrase.category.toimenpide-julkipanoon}}}
   :section {:id         :bulletin
             :loc-prefix :bulletin
             :grid       {:columns 1
                          :rows    [[{:col  1
                                      :dict :bulletinOpDescription}]]}}})

(def temsub-foremen
  (->> foreman-codes
       (map (fn [code]
              (let [codename     (name code)
                    included-key (keyword (str codename "-included"))]
                {:toggle-key code

                 :included     {:toggle {:i18nkey  (keyword (str "pate-r.foremen."
                                                               codename))
                                         :label?  false}}
                 :included-key included-key
                 :toggle     {:toggle {:i18nkey :pate.available-in-verdict
                                       :enabled? included-key
                                       :label?   false}}})))

       (reduce (fn [acc {:keys [toggle-key toggle included-key included]}]
                 (-> acc
                     (assoc-in [:dictionary toggle-key] toggle)
                     (assoc-in [:dictionary included-key] included)
                     (update-in [:section :grid :rows]
                                #(conj (vec %)
                                       {:css :row--extra-tight
                                        :row [{:col  2
                                               :dict included-key}
                                              {:col  2
                                               :dict toggle-key}]}))))
               {:section    {:id         :foremen
                             :loc-prefix :pate-r.foremen
                             :grid       {:columns 5
                                          :rows    []}}
                :removable? true})))

(defn settings-dependencies [dict loc-prefix]
  {:dictionary {dict {:repeating {:included {:toggle {:label?    false
                                                      :text-dict :text}}
                                  :text     {:text {:read-only? true}}
                                  :selected {:toggle {:enabled?  :-.included
                                                      :label?  false
                                                      :i18nkey :pate.available-in-verdict}}}
                      :sort-by :text}}
   :section    {:id         dict
                :loc-prefix loc-prefix
                :grid       {:columns 5
                             :rows    [{:hide? dict
                                        :css   :row--extra-tight
                                        :row   [{:col  4
                                                 :dict :link-to-settings-no-label}]}
                                       {:css :row--extra-tight
                                        :row [{:col   4
                                               :show? dict
                                               :grid  {:columns   4
                                                       :repeating dict
                                                       :rows      [{:css :row--extra-tight
                                                                    :row [{:col  2
                                                                           :dict :included}
                                                                          {:col  2
                                                                           :dict :selected}]}]}}]}]}}
   :removable? true})

(def temsub-reviews
  (settings-dependencies :reviews :pate-reviews))

(def temsub-plans
  (settings-dependencies :plans :pate.plans))

(def temsub-conditions ;; Muut lupaehdot
  {:dictionary    {:conditions    {:repeating {:condition        {:phrase-text {:i18nkey  :pate-condition
                                                                                :category :lupaehdot}}
                                               :remove-condition {:button {:i18nkey :remove
                                                                           :label?  false
                                                                           :icon    :lupicon-remove
                                                                           :css     [:primary :outline]
                                                                           :remove  :conditions}}}}
                   :add-condition {:button {:icon    :lupicon-circle-plus
                                            :i18nkey :pate-conditions.add
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
                                                                {:dict :remove-condition}]]}}]
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

(def temsub-collateral (text-subschema :collateral :pate-collateral.text))

(def temsub-complexity
  {:dictionary    {:complexity      complexity-select
                   :complexity-text {:phrase-text {:label?   false
                                                   :category :vaativuus}}}
   :section       {:id         :complexity
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
(def temsub-purpose ;; Kaavan k\u00e4ytt\u00f6tarkoitus
  (text-subschema :purpose :pate-purpose.text))
(def temsub-statements (text-subschema :statements :pate-statements.text))

(def temsub-buildings
  {:dictionary    {:autopaikat {:toggle {}}
                   :vss-luokka {:toggle {}}
                   :paloluokka {:toggle {}}}
   :section
   {:id         :buildings
    :loc-prefix :pate-buildings
    :grid       {:columns 1
                 :rows    [[{:loc-prefix :pate-buildings.info
                             :list       {:title   "pate-buildings.info"
                                          :labels? false
                                          :items   (mapv (fn [check]
                                                           {:dict check
                                                            :id   check
                                                            :css  [:pate-condition-box]})
                                                         [:autopaikat :vss-luokka :paloluokka])}}]]}}
   :removable? true})

(def temsub-attachments
  {:dictionary    {:upload {:toggle {}}}
   :section       {:id         :attachments
                   :loc-prefix :application.verdict-attachments
                   :grid       {:columns 1
                                :rows    [[{:loc-prefix :pate.attachments
                                            :list       {:labels? false
                                                         :items   [{:id   :upload
                                                                    :dict :upload}]}}]]}}
   :removable? true})


(def r-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict verdict-dates)
                                 temsub-bulletin
                                 temsub-foremen
                                 temsub-reviews
                                 temsub-plans
                                 temsub-conditions
                                 temsub-neighbors
                                 temsub-appeal
                                 temsub-complexity
                                 temsub-extra-info
                                 temsub-deviations
                                 temsub-rights
                                 temsub-purpose
                                 temsub-statements
                                 temsub-buildings
                                 temsub-attachments))

(def temsub-start-info
  (text-subschema :start-info :pate-start-info.text))

(def p-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict verdict-dates)
                                 temsub-bulletin
                                 temsub-conditions
                                 temsub-neighbors
                                 temsub-appeal
                                 temsub-start-info
                                 temsub-deviations
                                 temsub-rights
                                 temsub-purpose
                                 temsub-statements
                                 temsub-attachments))

(def temsub-reviews-with-phrase
  (-> (settings-dependencies :reviews :pate-reviews)
      (assoc-in [:dictionary :review-info] {:phrase-text {:i18nkey  :pate.review-info
                                                          :category :yleinen}})
      (update-in [:section :grid :rows] conj [{:col   4
                                               :dict  :review-info}])
      (assoc-in [:section :always-included?] true)))

(def temsub-inform-others
  (text-subschema :inform-others :pate-inform-others.text))

(def ya-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict ya-verdict-dates)
                                 temsub-bulletin
                                 temsub-reviews-with-phrase
                                 temsub-plans
                                 temsub-conditions
                                 temsub-inform-others
                                 temsub-appeal
                                 temsub-statements
                                 temsub-attachments))

(def tj-verdict-template-schema
  (build-verdict-template-schema (temsub-verdict tj-verdict-dates)
                                 temsub-appeal
                                 temsub-attachments))

(defn verdict-template-schema [category]
  (case (keyword category)
    :r  r-verdict-template-schema
    :p  p-verdict-template-schema
    :ya ya-verdict-template-schema
    :tj tj-verdict-template-schema))

;; -----------------------------
;; Verdict subschemas
;; -----------------------------

(defn build-verdict-schema
  "Combines subschemas, checks template references and validates the
  result."
  [category version & subschemas]
  (let [{comb-dic :dictionary
         :as     combined} (apply schema-util/combine-subschemas subschemas)
        temdic             (:dictionary (verdict-template-schema category))
        removable          (some-> temdic :removed-sections :keymap keys set)
        temdicts           (-> temdic keys set)
        sec-diff           (set/difference (->> (concat (:sections combined)
                                                        (vals comb-dic))
                                                (map :template-section)
                                                (remove nil?)
                                                set)
                                           removable)
        dict-diff          (set/difference (->> (vals comb-dic)
                                                (map :template-dict)
                                                (remove nil?)
                                                set)
                                           temdicts)]
    (schema-util/pate-assert (empty? sec-diff) "Bad template-sections:" sec-diff)
    (schema-util/pate-assert (empty? dict-diff) "Bad template-dicts:" dict-diff)
    (sc/validate schemas/PateVerdict (assoc combined :version version))))

(defn phrase-versub
  "Verdict subschema that contains only a phrase text. Additional keyword arguments:

  show?            Visibility rule
  template-section Inclusion dependency
  template-dict    Initialization dependency"
  [dict loc-text phrase-category & kvs]
  (let [{:keys [show? template-section template-dict]} (apply hash-map kvs)]
    {:dictionary {dict (cond-> {:phrase-text {:category   phrase-category
                                              :loc-prefix loc-text}}
                         template-section (assoc :template-section template-section)
                         template-dict    (assoc :template-dict template-dict))}
     :section     (cond-> {:id    dict
                           :grid {:columns 7
                                  :rows    [[{:col  6
                                              :dict dict}]]}}
                    show? (assoc :show? show?)) }))

(def required-in-verdict {:toggle {:i18nkey :pate.template-removed}})
(def app-id-placeholder {:placeholder {:loc-prefix :pate-verdict.application-id
                                       :type :application-id}})

(defn versub-dates
  "Verdict handler title could be specific to a category."
  [category dates]
  {:dictionary (assoc (->> dates
                           (map (fn [kw]
                                  [kw (schema-util/required {:date {:disabled?
                                                        :automatic-verdict-dates}})]))
                           (into {}))
                      :language                (schema-util/required (assoc language-select
                                                                :template-dict :language))
                      :verdict-date            (schema-util/required {:date {}})
                      :automatic-verdict-dates {:toggle {}}
                      :handler               (schema-util/required {:text {:loc-prefix
                                                               (case category
                                                                 :p :pate.prepper
                                                                 :pate-verdict.handler)}})
                      :handler-title         {:text {:loc-prefix :pate-verdict.handler.title}}
                      :application-id        app-id-placeholder)
   :section    {:id   :pate-dates
                :grid {:columns 7
                       :rows    [[{:col   7
                                   :dict  :language
                                   :hide? :_meta.published?}]
                                 [{:dict  :handler-title
                                   :show? :_meta.editing?}
                                  {:col   2
                                   :show? :_meta.editing?
                                   :align :full
                                   :dict  :handler}
                                  {:col   3
                                   :hide? :_meta.editing?
                                   :list  {:title   (if (= category :r)
                                                      :pate-verdict.handler
                                                      :pate.prepper)
                                           :labels? false
                                           :items   [{:dict :handler-title}
                                                     {:dict :handler}]}}
                                  {}
                                  {:col   2
                                   :hide? :_meta.editing?
                                   :dict  :application-id}]
                                 [{:id   "verdict-date"
                                   :dict :verdict-date}
                                  {:id    "automatic-verdict-dates"
                                   :col   2
                                   :show? :_meta.editing?
                                   :dict  :automatic-verdict-dates}]
                                 {:id         "deltas"
                                  :css        [:pate-date]
                                  :loc-prefix :pate-verdict
                                  :row        (map (fn [kw]
                                                     (let [id (name kw)]
                                                       {:disabled? :automatic-verdict-dates
                                                        :id        id
                                                        :dict      kw}))
                                                   dates)}]}}})

(def versub-operation
  {:dictionary {:operation {:text {:loc-prefix :pate.operation}}
                :address   {:text {:loc-prefix :pate.address}}}
   :section    {:id   :pate-operation
                :grid {:columns 2
                       :rows    [[{:dict  :operation
                                   :align :full}]
                                 [{:dict  :address
                                   :align :full}]]}}})

(defn versub-verdict
  "Collateral part (toggle, amount, type, date) included only when
  collateral? is true."
  [collateral?]
  (let [verdict {:dictionary (merge {:boardname        {:reference {:path :*ref.boardname}}
                                     :verdict-section  (schema-util/required {:text {:before :section}})
                                     :verdict-code     (schema-util/required {:reference-list {:path       :verdict-code
                                                                                   :type       :select
                                                                                   :loc-prefix :pate-r.verdict-code}
                                                                  :template-dict  :verdict-code})
                                     :verdict-text     (schema-util/required {:phrase-text   {:category :paatosteksti}
                                                                  :template-dict :paatosteksti})
                                     :verdict-text-ref (schema-util/required {:reference {:path :verdict-text}})}
                                    (when collateral? {:collateral      {:text {:after :eur}}
                                                       :collateral-flag {:toggle {:loc-prefix :pate-collateral.flag}}
                                                       :collateral-date {:date {}}
                                                       :collateral-type collateral-type-select}))
                 :section    {:id   :pate-verdict
                              :grid {:columns 7
                                     :rows    [[{:col        2
                                                 :loc-prefix :pate-verdict.giver
                                                 :hide?      :_meta.editing?
                                                 :dict       :boardname}
                                                {:col        1
                                                 :show?      [:OR :*ref.boardname :verdict-section]
                                                 :loc-prefix :pate-verdict.section
                                                 :dict       :verdict-section}
                                                {:show? [:AND :_meta.editing? :?.boardname]}
                                                {:col   2
                                                 :align :full
                                                 :dict  :verdict-code}]
                                               [{:col   5
                                                 :id    "paatosteksti"
                                                 :show? :_meta.editing?
                                                 :dict  :verdict-text}
                                                {:col   5
                                                 :hide? :_meta.editing?
                                                 :dict  :verdict-text-ref}]
                                               ]}}}]
    (cond-> verdict
      collateral? (update-in [:section :grid :rows]
                             concat
                             [{:show? :_meta.editing?
                               :css   [:row--tight]
                               :row   [{:col  3
                                        :dict :collateral-flag}]}
                              {:show?      [:AND :?.collateral :collateral-flag]
                               :loc-prefix :pate
                               :row        [{:col  2
                                             :id   :collateral-date
                                             :dict :collateral-date}
                                            {:col  2
                                             :id   :collateral
                                             :dict :collateral}
                                            {:col  2
                                             :dict :collateral-type}]}]))))

(def versub-bulletin
  {:dictionary
   {:bulletinOpDescription
    {:phrase-text {:category :toimenpide-julkipanoon
                   :i18nkey  :phrase.category.toimenpide-julkipanoon}
     :template-dict :bulletinOpDescription}}
   :section {:id         :bulletin
             :loc-prefix :bulletin
             :show?      :?.bulletin-op-description
             :grid       {:columns 1
                          :rows    [[{:col  1
                                      :id   "toimenpide-julkipanoon"
                                      :dict :bulletinOpDescription}]]}}})

(defn versub-requirements
  "Supported arguments: :foremen, :plans and :reviews."
  [& args]
  (let [{foremen? :foremen
         plans?   :plans
         reviews? :reviews} (zipmap args (repeat true))]
    {:dictionary
     (->> [(when foremen? [:pate-r.foremen :foremen false false])
           (when plans? [:pate.plans :plans true false])
           (when reviews? [:pate-reviews :reviews true true])]
          (remove nil?)
          (reduce (fn [acc [loc-prefix kw term? separator?]]
                    (let [included (keyword (str (name kw) "-included"))
                          path     kw]
                      (assoc acc
                             (util/kw-path kw)
                             {:reference-list
                              (merge {:enabled?   included
                                      :loc-prefix loc-prefix
                                      :path       path
                                      :type       :multi-select}
                                     (when term?
                                       {:item-key :id
                                        :term     {}})
                                     (when separator?
                                       {:separator " \u2013 "}))}
                             ;; Included toggle
                             included required-in-verdict)))
                  {}))
     :section
     {:id   :requirements
      :grid {:columns 7
             :rows    (map (fn [dict]
                             (let [check-path (keyword (str (name dict) "-included"))
                                   exist-path (keyword (str "?." (name dict)))]
                               {:show? [:AND exist-path [:OR :_meta.editing? check-path]]
                                :row   [{:col  4
                                         :dict dict}
                                        {:col   2
                                         :align :right
                                         :show? :_meta.editing?
                                         :id    "included"
                                         :dict  check-path}]}))
                           (remove nil? [(when foremen? :foremen)
                                         (when plans?   :plans)
                                         (when reviews? :reviews)]))}}}))

(def versub-conditions ;; Muut lupaehdot
  {:dictionary
   {:conditions-title {:loc-text :phrase.category.lupaehdot}
    :conditions       {:repeating {:condition        {:phrase-text {:label?   false
                                                                    :category :lupaehdot}}
                                   :remove-condition {:button {:i18nkey :remove
                                                               :label?  false
                                                               :icon    :lupicon-remove
                                                               :css     :secondary
                                                               :remove  :conditions}}}
                       :template-dict :conditions}
    :add-condition    {:button {:icon    :lupicon-circle-plus
                                :i18nkey :pate-conditions.add
                                :css     :positive
                                :add     :conditions}}}
   :section
   {:id   :conditions
    :template-section :conditions
    :grid {:columns 1
           :show?   :?.conditions
           :rows    [[{:css  :pate-label
                       :dict :conditions-title}]
                     [{:grid {:columns   9
                              :repeating :conditions
                              :rows      [[{:col  7
                                            :dict :condition}
                                           {:align :right
                                            :dict  :remove-condition}]]}}]
                     [{:show? :_meta.editing?
                       :dict  :add-condition}]]}}})

(def versub-appeal ;; Muutoksenhaku, vakuudet
  (phrase-versub :appeal :verdict.muutoksenhaku :muutoksenhaku
                 :show? :?.appeal
                 :template-dict :appeal))

(def versub-statements
  {:dictionary {:statements {:placeholder      {:type :statements}
                             :template-section :statements}}
   :section    {:id         :statements
                :show?      :?.statements
                :loc-prefix :pate-statements
                :buttons?   false
                :grid       {:columns 1
                             :rows    [[{:dict :statements}]]}}})

(def versub-neighbors
  {:dictionary
   {:neighbors       {:phrase-text {:i18nkey  :phrase.category.naapurit
                                    :category :naapurit}}
    :neighbor-states {:placeholder {:type :neighbors}}}
   :section {:id    :neighbors
             :template-section :neighbors
             :show? :?.neighbors
             :grid  {:columns 12
                     :rows    [[{:col  7
                                 :id   "neighbor-notes"
                                 :dict :neighbors}
                                {:col   4
                                 :hide? :_meta.published?
                                 :id    "neighbor-states"
                                 :align :right
                                 :dict  :neighbor-states}]
                               ]}}})

(def versub-complexity ;; Vaativuus, rakennusoikeus, kaava
  {:dictionary {:complexity      (assoc complexity-select
                                        :template-dict    :complexity)
                :complexity-text {:phrase-text      {:label?   false
                                                     :category :vaativuus}
                                  :template-dict    :complexity-text}
                :rights          {:phrase-text      {:category :rakennusoikeus}
                                  :template-section :rights}
                :purpose         {:phrase-text      {:category :kaava}
                                  :template-section :purpose}}
   :section
   {:id         :complexity
    :loc-prefix :pate-complexity
    :show?      [:OR :?.complexity :?.rights :?.purpose]
    :grid       {:columns 7
                 :rows    [{:show? :?.complexity
                            :row   [{:col  3
                                     :dict :complexity}]}
                           {:show? :?.complexity
                            :row   [{:col  6
                                     :id   "text"
                                     :dict :complexity-text}]}
                           {:show? :?.rights
                            :row   [{:col        6
                                     :loc-prefix :pate-rights
                                     :dict       :rights}]}
                           {:show? :?.purpose
                            :row   [{:col        6
                                     :loc-prefix :phrase.category.kaava
                                     :dict       :purpose}]}]}}})

(def versub-extra-info
  (phrase-versub :extra-info :pate-extra-info :yleinen
                 :show? :?.extra-info :template-section :extra-info))

(def versub-deviations ;; Poikkeamiset
  {:dictionary {:deviations {:phrase-text      {:category :yleinen}
                             :template-section :deviations}}
   :section    {:id         :deviations
                :loc-prefix :pate-deviations
                :show?      :?.deviations
                :grid       {:columns 7
                             :rows    [[{:col  6
                                         :dict :deviations}]]}}})

(def versub-buildings
  {:dictionary
   {:buildings
    {:repeating        {:building-name          {:placeholder {:label? false
                                                               :type   :building}}
                        :rakennetut-autopaikat  {:text {}}
                        :kiinteiston-autopaikat {:text {}}
                        :autopaikat-yhteensa    {:text {}}
                        :vss-luokka             {:text {}}
                        :paloluokka             {:text {}}
                        :show-building          required-in-verdict}
     :sort-by          :order
     :template-section :buildings}}
   :section
   {:id    :buildings
    :show? :?.buildings
    :grid  {:columns 7
            :rows    [[{:col  7
                        :grid {:columns    6
                               :loc-prefix :pate-buildings.info
                               :repeating  :buildings
                               :rows       [{:css   [:row--tight]
                                             :show? [:OR :_meta.editing? :+.show-building]
                                             :row   [{:col  6
                                                      :dict :building-name}]}
                                            {:show? [:OR :_meta.editing? :+.show-building]
                                             :css   [:row--indent]
                                             :row   [{:col  5
                                                      :list {:css   :list--sparse
                                                             :items (map #(hash-map :id %
                                                                                    :dict %
                                                                                    :enabled? :-.show-building)
                                                                         [:rakennetut-autopaikat
                                                                          :kiinteiston-autopaikat
                                                                          :autopaikat-yhteensa
                                                                          :vss-luokka
                                                                          :paloluokka])}}
                                                     {:dict  :show-building
                                                      :show? :_meta.editing?}]}
                                            ]}}]]}}})


(def versub-attachments
  {:dictionary
   {:attachments {:application-attachments {:i18nkey :application.verdict-attachments}}}
   :section {:id               :attachments
             :template-section :attachments
             :grid             {:columns 7
                                :rows    [[{:col  6
                                            :dict :attachments}]]}}})

(def versub-upload
  {:dictionary
   {:upload
    {:attachments {:i18nkey    :application.verdict-attachments
                   :label?     false
                   :type-group #"paatoksenteko"
                   :default    :paatoksenteko.paatosote
                   :dropzone   "#pate-verdict-page"
                   :multiple?  true}}}
   :section {:id       :upload
             :hide?    :_meta.published?
             :css      :pate-section--no-border
             :buttons? false
             :grid     {:columns 7
                        :rows    [[{:col  6
                                    :dict :upload}]]}}})

(def r-verdict-schema-1 (build-verdict-schema :r 1
                                              (versub-dates :r verdict-dates)
                                              (versub-verdict true)
                                              versub-bulletin
                                              versub-operation
                                              (versub-requirements :foremen :plans :reviews)
                                              versub-conditions
                                              versub-appeal
                                              versub-statements
                                              versub-neighbors
                                              versub-complexity
                                              versub-extra-info
                                              versub-deviations
                                              versub-buildings
                                              versub-attachments
                                              versub-upload))

(def versub-start-info ;; Lahtokohtatiedot
  (phrase-versub :start-info :pate-start-info :yleinen
                 :show? :?.start-info
                 :template-section :start-info))

(def versub-rationale  ;; Perustelut
  (phrase-versub :rationale :pate.verdict-rationale :yleinen))

(def versub-legalese   ;; Sovelletut oikeusohjeet
  (phrase-versub :legalese :pate.legalese :yleinen))

(def versub-giving
  (phrase-versub :giving :pate.verdict-giving :yleinen))

(def versub-next-steps ;; Jatkotoimenpiteet
  (phrase-versub :next-steps :pate.next-steps :yleinen))

(def versub-buyout ;; Lunastus
  (phrase-versub :buyout :pate.buyout :yleinen))

(def versub-fyi ;; Tiedoksi
  (phrase-versub :fyi :pate.fyi :yleinen))


(def p-verdict-schema-1 (build-verdict-schema :p 1
                                              (versub-dates :p verdict-dates)
                                              (versub-verdict true)
                                              versub-bulletin
                                              versub-operation
                                              versub-conditions
                                              versub-appeal
                                              versub-statements
                                              versub-neighbors
                                              versub-start-info
                                              versub-deviations
                                              versub-rationale
                                              versub-legalese
                                              versub-giving
                                              versub-next-steps
                                              versub-buyout
                                              versub-fyi
                                              versub-attachments
                                              versub-upload))

(def versub-dates-ya
  (-> (versub-dates :ya verdict-dates)
      (assoc-in [:dictionary :start-date]
                {:date      {:i18nkey :tyoaika.tyoaika-alkaa-ms}
                 :required? true})
      (assoc-in [:dictionary :end-date]
                {:date      {:i18nkey :tyoaika.tyoaika-paattyy-ms}
                 :required? true})
      (update-in [:section :grid :rows]
                 conj
                 [{:dict :start-date} {:dict :end-date}])))

(def versub-requirements-ya
  (-> (versub-requirements :plans :reviews)
      (assoc-in [:dictionary :review-info] {:phrase-text   {:i18nkey  :pate.review-info
                                                            :category :yleinen}
                                            :template-dict :review-info})
      (update-in [:section :grid :rows] concat [[{:col   4
                                                  :show? :reviews-included
                                                  :dict  :review-info}]])))

(def versub-inform-others ;; Lahiseudun asukkaita tiedotettava
  (phrase-versub :inform-others :pate-inform-others :yleinen))

(def versub-verdict-ya
  (-> (versub-verdict false)
      (assoc-in [:dictionary :verdict-type]
                {:select    {:loc-prefix :pate.verdict-type
                             :items      ya-verdict-types
                             :sort-by    :text}
                 :required? true})
      (update-in [:section :grid :rows 0]
                 #(cons {:dict :verdict-type} %))))

(def versub-verdict-tj
  (-> (versub-verdict false)
      (assoc-in [:dictionary :verdict-section :required?]
                false)
      (assoc-in [:section :grid :rows 0]
                [{:col        2
                  :loc-prefix :pate-verdict.giver
                  :hide?      :_meta.editing?
                  :show?      :*ref.boardname
                  :dict       :boardname}
                 {:col        1
                  :show?      [:OR :*ref.boardname :verdict-section]
                  :loc-prefix :pate-verdict.section
                  :dict       :verdict-section}
                 {:show? [:AND :_meta.editing? :?.boardname]}
                 {:col   2
                  :align :full
                  :dict  :verdict-code}])))

(def ya-verdict-schema-1 (build-verdict-schema :ya 1
                                               versub-dates-ya
                                               versub-verdict-ya
                                               versub-bulletin
                                               versub-operation
                                               versub-requirements-ya
                                               versub-conditions
                                               versub-appeal
                                               versub-statements
                                               versub-inform-others
                                               versub-attachments
                                               versub-upload))

;; TODO: FILL ME
(def tj-verdict-schema-1 (build-verdict-schema :tj 1
                                               (versub-dates :tj tj-verdict-dates)
                                               versub-verdict-tj
                                               versub-operation
                                               versub-appeal
                                               versub-attachments))

(defn verdict-schema
  "Nil version returns the latest version."
  ([category version]
   (let [schemas (case (keyword category)
                   :r  [r-verdict-schema-1]
                   :p  [p-verdict-schema-1]
                   :ya [ya-verdict-schema-1]
                   :tj [tj-verdict-schema-1]
                   (schema-util/pate-assert false "Invalid schema category:" category))]
     (cond
       (nil? version)                     (last schemas)
       (and (pos? version)
            (<= version (count schemas))) (nth schemas (dec version))
       :else                              (schema-util/pate-assert false "Invalid schema version:" version))))
  ([category]
   (verdict-schema category nil)))

(defn permit-type->category [permit-type]
  (when-let [kw (some-> permit-type
                        s/lower-case
                        keyword)]
    (cond
      (#{:r} kw)                     [:r :tj]
      (#{:p :ya} kw)                 kw
      (#{:kt :mm} kw)                :kt
      (#{:yi :yl :ym :vvvl :mal} kw) :ymp)))


(defn permit-subtype->category [permit-subtype]
  (when-let [kw (some-> permit-subtype
                        s/lower-case
                        keyword)]
    (cond
      (#{:tyonjohtaja-hakemus} kw) :tj)))

(defn application->category [{:keys [permitType permitSubtype]}]
  (let [by-subtype (permit-subtype->category permitSubtype)
        kw         (-> permitType s/lower-case keyword)]
    (if (some? by-subtype)
      by-subtype
      (cond
        (#{:r :p :ya} kw)              kw
        (#{:kt :mm} kw)                :kt
        (#{:yi :yl :ym :vvvl :mal} kw) :ymp))))

(defn dict-resolve
  "Path format: [repeating index repeating index ... value-dict].
 Repeating denotes :repeating schema, index is arbitrary repeating
  index (skipped during resolution) and value-dict is the final dict
  for the item schema.

  Returns map with :schema and :path keys. The path is
  the remaining path (e.g., [:delta] for pate-delta). Note: the
  result is empty map if the path resolves to the repeating schema.

  Returns nil when the resolution fails."
  [path dictionary]
  (loop [[x & xs]   (->> path
                         (remove nil?)
                         (map keyword))
         dictionary dictionary]
    (when dictionary
      (if x
        (when-let [schema (get dictionary x)]
          (if (:repeating schema)
            (recur (rest xs) (:repeating schema))
            {:schema schema :path xs}))
        {}))))

(defn repeating-subpath
  "Subpath that resolves to a repeating named repeating. Nil if not
  found. Note that the actual existence of the path within data is not
  checked."
  [repeating path dictionary]
  (loop [path path]
    (cond
      (empty? path)           nil
      (= (last path)
         repeating) (when (= (dict-resolve path dictionary)
                             {})
                      path)
      :else                   (recur (butlast path)))))
