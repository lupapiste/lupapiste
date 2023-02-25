(ns lupapalvelu.pate.verdict-schemas
  (:require [clojure.set :as set]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [sade.shared-util :as util]
            [schema.core :as sc]))

;; -----------------------------
;; Verdict subschemas
;; -----------------------------

(defn build-verdict-schema
  "Combines subschemas, checks template references and validates the
  result."
  [category version & subschemas]
  (let [{comb-dic :dictionary
         :as     combined} (apply schema-util/combine-subschemas subschemas)
        temdic             (:dictionary (template-schemas/verdict-template-schema category))
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
  [category]
  (let [dates (helper/verdict-dates category)]
    {:dictionary (assoc (->> dates
                             (map (fn [kw]
                                    [kw (schema-util/required
                                          {:date {:disabled? :automatic-verdict-dates
                                                  :i18nkey   (util/kw-path :pate-verdict kw)}})]))
                             (into {}))
                        :language (schema-util/required (assoc helper/language-select
                                                               :template-dict :language))
                        :verdict-date (schema-util/required {:date {}})
                        :automatic-verdict-dates {:toggle {}}
                        :handler (schema-util/required {:text {:loc-prefix
                                                               (case category
                                                                 :p :pate.prepper
                                                                 :pate-verdict.handler)}})
                        :handler-title (case category
                                         :ya {:reference-list {:path       :handler-titles
                                                               :type       :select
                                                               :loc-prefix :pate-verdict.handler.title.ya
                                                               :item-key   :id
                                                               :term       {}}
                                              :template-dict  :handler-titles}
                                         {:text {:loc-prefix :pate-verdict.handler.title}})
                        :giver {:text {:loc-prefix :pate-verdict.giver}}
                        :giver-title {:text {:loc-prefix :pate-verdict.handler.title}}
                        :application-id app-id-placeholder)
     :section    {:id   :pate-dates
                  :grid {:columns 7
                         :rows    [[{:col   7
                                     :dict  :language
                                     :hide? :_meta.published?}]
                                   [{:col   2
                                     :dict  :handler-title
                                     :align :full
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
                                   ;; :giver is a later addition and not
                                   ;; necessary included in the older
                                   ;; verdicts.
                                   {:show? :?.giver
                                    :row   [{:col   2
                                             :dict  :giver-title
                                             :align :full
                                             :show? :_meta.editing?}
                                            {:col   2
                                             :show? :_meta.editing?
                                             :align :full
                                             :dict  :giver}
                                            {:col   3
                                             :hide? :_meta.editing?
                                             :list  {:title   :pate-verdict.giver
                                                     :labels? false
                                                     :items   [{:dict :giver-title}
                                                               {:dict :giver}]}}]}
                                   [{:id   "verdict-date"
                                     :dict :verdict-date}
                                    {:id    "automatic-verdict-dates"
                                     :col   3
                                     :show? :_meta.editing?
                                     :dict  :automatic-verdict-dates}]
                                   {:id  "deltas"
                                    :css [:pate-date]
                                    :row (map (fn [kw]
                                                {:disabled? :automatic-verdict-dates
                                                 :dict      kw})
                                              dates)}]}}}))

(def versub-operation-ya
  {:dictionary {:operation       {:text {:loc-prefix :pate.operation
                                         :lines      20}}
                :address         {:text {:loc-prefix :pate.location}}
                :property-title  {:loc-text :pate.property-id}
                :propertyIds     {:repeating {:property-id        {:text {:label?     false
                                                                          :read-only? false}}
                                              :remove-property-id {:button {:i18nkey :remove
                                                                            :label?  false
                                                                            :icon    :lupicon-remove
                                                                            :css     :secondary
                                                                            :remove  :propertyIds}}}}
                :add-property-id {:button {:icon    :lupicon-circle-plus
                                           :i18nkey :pate.property-id.add
                                           :css     :positive
                                           :add     :propertyIds}}}
   :section    {:id   :pate-operation
                :grid {:columns 2
                       :rows    [[{:dict  :operation
                                   :align :full}]
                                 [{:dict  :address
                                   :align :full}]
                                 [{:css  :pate-label
                                   :dict :property-title}]
                                 [{:grid {:columns   9
                                          :repeating :propertyIds
                                          :rows      [[{:col  8
                                                        :dict :property-id}
                                                       {:align :right
                                                        :dict  :remove-property-id}]]}}]
                                 [{:show? :_meta.editing?
                                   :dict  :add-property-id}]]}}})

(defn versub-verdict
  "Collateral part (toggle, amount, type, date) included only when
  collateral? is true."
  [collateral?]
  (let [verdict {:dictionary (merge {:boardname           {:reference {:loc-prefix :pate-verdict.giver.lautakunta
                                                                       :path       :*ref.boardname}}
                                     :verdict-section     (schema-util/required {:text {:loc-prefix :pate-verdict.section
                                                                                        :before     :section}})
                                     :verdict-code        (schema-util/required {:reference-list {:path       :verdict-code
                                                                                                  :type       :select
                                                                                                  :loc-prefix :pate-r.verdict-code}
                                                                                 :template-dict  :verdict-code})
                                     :verdict-flag        {:toggle {:loc-prefix :pate-verdict.flag
                                                                    :label? false}}
                                     :verdict-text        {:phrase-text   {:category :paatosteksti}
                                                           :template-dict :paatosteksti}
                                     :verdict-text-ref    {:reference {:path :verdict-text}}
                                     :proposal-text       {:phrase-text   {:category   :paatosteksti
                                                                           :loc-prefix :pate-verdict-proposal}
                                                           :template-dict :proposaltext}
                                     :proposal-text-ref   {:reference {:path       :proposal-text
                                                                       :loc-prefix :pate-verdict-proposal}}}
                                    (when collateral? {:collateral      {:text {:loc-prefix :pate.collateral
                                                                                :after      :eur}}
                                                       :collateral-flag {:toggle {:loc-prefix :pate-collateral.flag}}
                                                       :collateral-date {:date {:loc-prefix :pate.collateral-date}}
                                                       :collateral-type helper/collateral-type}))
                 :section    {:id   :pate-verdict
                              :grid {:columns 7
                                     :rows    [[{:col   2
                                                 :hide? :_meta.editing?
                                                 :dict  :boardname}
                                                {:col   1
                                                 :show? [:AND
                                                         [:NOT :_meta.editing?]
                                                         [:OR :verdict-flag [:NOT :?.boardname]]]
                                                 :dict  :verdict-section}
                                                {:col   2
                                                 :align :full
                                                 :show? [:AND
                                                         [:NOT :_meta.editing?]
                                                         [:OR :verdict-flag [:NOT :?.boardname]]]
                                                 :dict  :verdict-code}]
                                               [{:col   3
                                                 :show? :?.boardname
                                                 :dict  :verdict-flag}]
                                               [{:col   5
                                                 :show? [:AND :_meta.editing? :?.boardname [:NOT :verdict-flag]]
                                                 :dict  :proposal-text}
                                                {:col   5
                                                 :show? :*ref.boardname
                                                 :hide? :_meta.editing?
                                                 :dict  :proposal-text-ref}]
                                               [{:col   1
                                                 :show? [:AND :_meta.editing? [:OR :verdict-flag [:NOT :?.boardname]]]
                                                 :dict  :verdict-section}
                                                {:col   2
                                                 :align :full
                                                 :show? [:AND :_meta.editing? [:OR :verdict-flag [:NOT :?.boardname]]]
                                                 :dict  :verdict-code}]
                                               [{:col   5
                                                 :show? [:AND :_meta.editing? [:OR :verdict-flag [:NOT :?.boardname]]]
                                                 :dict  :verdict-text}
                                                {:col   5
                                                 :hide? [:OR :_meta.editing?
                                                         [:AND [:NOT :verdict-flag] :?.boardname]]
                                                 :dict  :verdict-text-ref}]]}}}]
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
                                             :dict :collateral-date}
                                            {:col  2
                                             :dict :collateral}
                                            {:col  2
                                             :dict :collateral-type}]}]))))

(def versub-bulletin
  {:dictionary {:bulletin-op-description    {:phrase-text   {:category :toimenpide-julkipanoon
                                                             :i18nkey  :phrase.category.toimenpide-julkipanoon}
                                             :template-dict :bulletinOpDescription}
                :bulletin-desc-as-operation {:toggle        {:loc-prefix :pate-bulletin-as-operation.flag}}}
   :section    {:id         :bulletin
                :loc-prefix :bulletin
                :show?      :?.bulletin-op-description
                :grid       {:columns 1
                             :rows    [[{:col  1
                                         :id   "toimenpide-julkipanoon"
                                         :dict :bulletin-op-description}]
                                       [{:col 1
                                         :show? :_meta.editing?
                                         :dict :bulletin-desc-as-operation}]]}}})

(def versub-operation
  {:dictionary {:operation    {:text {:loc-prefix :pate.operation
                                      :lines      20}}
                :bulletin-ref {:reference {:loc-prefix :pate.operation
                                           :path       :bulletin-op-description}}
                :address      {:text {:loc-prefix :pate.address}}}
   :section    {:id   :pate-operation
                :grid {:columns 2
                       :rows    [[{:dict  :operation
                                   :align :full
                                   :hide? :bulletin-desc-as-operation}
                                  {:dict  :bulletin-ref
                                   :align :full
                                   :show? :bulletin-desc-as-operation}]
                                 [{:dict  :address
                                   :align :full}]]}}})

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
      :show? [:OR :?.foremen :?.plans :?.reviews]
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
    :conditions       {:repeating     {:condition        {:phrase-text {:label?   false
                                                                        :i18nkey  :pdf.condition
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
   {:id               :conditions
    :show?            :?.conditions
    :template-section :conditions
    :grid             {:columns 1
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

(def versub-rationale--conditional ;; Päätöksen perustelut
  (phrase-versub :rationale :pate.rationale :yleinen
                 :show? :?.rationale
                 :template-dict :rationale))

(def versub-legalese--conditional ;; Sovelletut oikeusohjeet
  (phrase-versub :legalese :pate.legalese :yleinen
                 :show? :?.legalese
                 :template-dict :legalese))

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
  {:dictionary {:complexity      (assoc helper/complexity-select
                                        :template-dict    :complexity)
                :complexity-text {:phrase-text   {:label?   false
                                                  :category :vaativuus}
                                  :template-dict :complexity-text}
                :rights          {:phrase-text      {:category   :rakennusoikeus
                                                     :loc-prefix :pate-rights}
                                  :template-section :rights}
                :purpose         {:phrase-text      {:category   :kaava
                                                     :loc-prefix :phrase.category.kaava}
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
                            :row   [{:col  6
                                     :dict :rights}]}
                           {:show? :?.purpose
                            :row   [{:col  6
                                     :dict :purpose}]}]}}})

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

(def autopaikat
  [:?.buildings..kiinteiston-autopaikat
   :?.buildings..rakennetut-autopaikat
   :?.buildings..autopaikat-yhteensa])

(defn flatten-to-vector [coll]
  (vec (flatten coll)))

(def versub-buildings
  {:dictionary
   {:buildings-title {:css      :pate-label
                      :loc-text :pate-buildings}
    :buildings
    {:repeating (reduce (fn [acc k]
                          (assoc acc k {:text {:i18nkey (util/kw-path :pate-buildings.info
                                                                      k)}}))
                        {:building-name {:placeholder {:label? false
                                                       :type   :building}}
                         :show-building required-in-verdict}
                        [:rakennetut-autopaikat
                         :kiinteiston-autopaikat
                         :autopaikat-yhteensa
                         :vss-luokka
                         :paloluokka
                         :kokoontumistilanHenkilomaara])
     :sort-by   :order}}
   :section
   {:id               :buildings
    :show?            :buildings
    :template-section :buildings
    :grid             {:columns 7
                       :rows    [[{:col  7
                                   :dict :buildings-title}]
                                 [{:col  7
                                   :grid {:columns   6
                                          :repeating :buildings
                                          :rows      [{:css   [:row--tight]
                                                       :show? [:OR :_meta.editing? :+.show-building]
                                                       :row   [{:col  6
                                                                :dict :building-name}]}
                                                      {:show? [:OR :_meta.editing? :+.show-building]
                                                       :css   [:row--indent]
                                                       :row   [{:col  5
                                                                :list {:css   :list--sparse
                                                                       :items (map #(hash-map :dict %
                                                                                              :enabled? :-.show-building)
                                                                                   [:rakennetut-autopaikat
                                                                                    :kiinteiston-autopaikat
                                                                                    :autopaikat-yhteensa])}}
                                                               {:dict  :show-building
                                                                :show? [:AND :_meta.editing? (flatten-to-vector [:OR autopaikat])]
                                                                }]}
                                                      {:css   [:row--indent]
                                                       :show? [:OR :_meta.editing? :+.show-building]
                                                       :row   [{:col  5
                                                                :list {:css   :list--sparse
                                                                       :items (map #(hash-map :dict %
                                                                                              :enabled? :-.show-building)
                                                                                   [:vss-luokka
                                                                                    :paloluokka
                                                                                    :kokoontumistilanHenkilomaara])}}
                                                               {:dict  :show-building
                                                                :show? [:AND :_meta.editing? [:NOT (flatten-to-vector [:OR autopaikat])]]
                                                                }]}
                                                      ]}}]]}}})


(def versub-attachments
  {:dictionary
   {:attachments {:application-attachments {:i18nkey :application.verdict-attachments}}}
   :section {:id               :attachments
             :template-section :attachments
             :grid             {:columns 7
                                :rows    [[{:col  6
                                            :dict :attachments}]]}}})

(defn versub-upload
  ([{:keys [type-group default title]}]
   {:dictionary
    {:upload
     {:attachments {:i18nkey    (or title :application.verdict-attachments)
                    :label?     false
                    :type-group (or type-group #"paatoksenteko")
                    :default    (or default :paatoksenteko.paatosote)
                    :dropzone   "#pate-verdict-page"
                    :multiple?  true
                    :draft?     true}}}
    :section {:id       :upload
              :hide?    :_meta.published?
              :css      :pate-section--no-border
              :buttons? false
              :grid     {:columns 7
                         :rows    [[{:col  6
                                     :dict :upload}]]}}})
  ([] (versub-upload {})))

(def r-verdict-schema-1 (build-verdict-schema :r 1
                                              (versub-dates :r)
                                              (versub-verdict true)
                                              versub-rationale--conditional
                                              versub-legalese--conditional
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
                                              (versub-upload)))

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
                                              (versub-dates :p)
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
                                              (versub-upload)))

(def versub-dates-ya
  (-> (versub-dates :ya)
      (assoc-in [:dictionary :start-date]
                {:date      {:i18nkey :tyoaika.tyoaika-alkaa-ms}
                 :required? true})
      (assoc-in [:dictionary :no-end-date]
                {:toggle {:i18nkey :tyoaika.voimassa-toistaiseksi}})
      (assoc-in [:dictionary :end-date]
                {:date      {:i18nkey :tyoaika.tyoaika-paattyy-ms}
                 :required? (complement :no-end-date)})
      (update-in [:section :grid :rows]
                 conj
                 [{:dict :start-date
                   :col  1}
                  {:dict  :end-date
                   :col   1
                   :hide? :no-end-date}
                  {:id    "no-end-date"
                   :dict  :no-end-date
                   :col   4
                   :show? :_meta.editing?}])))

(def versub-requirements-ya
  (-> (versub-requirements :plans :reviews)
      (assoc-in [:dictionary :review-info] {:phrase-text   {:i18nkey  :pate.review-info
                                                            :category :yleinen}
                                            :template-dict :review-info})
      (update-in [:section :grid :rows] concat [[{:col   4
                                                  :show? :reviews-included
                                                  :dict  :review-info}]])))

(def versub-foreman-responsibilities
  {:dictionary {:responsibilities-start-date {:date      {:i18nkey :verdict.responsibilities}
                                              :required? :_computed.positive-verdict?}}
   :section    {:id     :foreman-responsibilities
                :show?  :?.responsibilities-start-date
                :grid   {:columns 1
                         :rows    [[{:col       1
                                     :disabled? :_computed.negative-verdict?
                                     :dict      :responsibilities-start-date}]]}}})

(def versub-inform-others ;; Lahiseudun asukkaita tiedotettava
  (phrase-versub :inform-others :pate-inform-others :yleinen))

(def versub-verdict-ya
  (-> (versub-verdict false)
      (assoc-in [:dictionary :verdict-type]
                {:select    {:loc-prefix :pate.verdict-type
                             :items      helper/ya-verdict-types
                             :sort-by    :text}
                 :required? true})
      (update-in [:section :grid :rows 0 ]
                 ;; Between section and verdict code.
                 (fn [cells]
                   (concat (butlast cells)
                           [{:col   1
                             :align :full
                             :dict  :verdict-type}
                            (last cells)])))))

(def ya-verdict-schema-1 (build-verdict-schema :ya 1
                                               versub-dates-ya
                                               versub-verdict-ya
                                               versub-bulletin
                                               versub-operation-ya
                                               versub-requirements-ya
                                               versub-conditions
                                               versub-appeal
                                               versub-statements
                                               versub-inform-others
                                               versub-attachments
                                               (versub-upload {:type-group #"muut" :default :muut.paatosote})))

(def versub-verdict-tj
  (-> (versub-verdict false)
      (assoc-in [:dictionary :verdict-section :required?]
                false)))

(def tj-verdict-schema-1 (build-verdict-schema :tj 1
                                               (versub-dates :tj)
                                               versub-foreman-responsibilities
                                               versub-verdict-tj
                                               versub-operation
                                               versub-appeal
                                               versub-attachments
                                               (versub-upload)))

(def versub-contract
  {:dictionary {:language       (assoc helper/contract-language
                                       :template-dict :language
                                       :required?     true)
                :application-id app-id-placeholder
                :handler        {:text      {:i18nkey :verdict.name.sijoitussopimus}
                                 :required? true}
                :giver          {:text {:i18nkey :pate-contract.signer}}
                :verdict-date   {:date      {:i18nkey :verdict.contract.date}
                                 :required? true}
                :contract-text  {:phrase-text   {:i18nkey  :verdict.contract.text
                                                 :category :sopimus}
                                 :required?     true
                                 :template-dict :contract-text}}
   :section    {:id   :verdict
                :grid {:columns 12
                       :rows    [[{:col  2
                                   :dict :language}]
                                 [{:col   2
                                   :align :full
                                   :dict  :application-id}
                                  {}]
                                 [{:col  2
                                   :dict :verdict-date}
                                  {}
                                  {:col   4
                                   :align :full
                                   :dict  :handler}]
                                 ;; :giver is a later addition and not
                                 ;; necessary included in the older
                                 ;; verdicts.
                                 {:show? :?.giver
                                  :row [{:col 3}
                                        {:col   4
                                         :align :full
                                         :dict  :giver}]}
                                 [{:col   10
                                   :align :full
                                   :dict  :contract-text}]]}}})

(def versub-signatures
  {:dictionary {:signatures-title {:css      :pate-label
                                   :loc-text :verdict.signatures}
                :signatures       {:repeating {:name       {:text {:label?     false
                                                                   :read-only? true}}
                                               :user-id    {:text {:read-only? true}}
                                               :company-id {:text {:read-only? true}}
                                               :date       {:date {:label?     false
                                                                   :read-only? true}}}
                                   :sort-by   :date}}
   :section    {:id       :signatures
                :buttons? false
                :show?    :signatures
                :grid     {:columns 8
                           :rows    [[{:col  8
                                       :dict :signatures-title}]
                                     {:css :row--extra-tight
                                      :row [{:col  7
                                             :grid {:columns   16
                                                    :repeating :signatures
                                                    :rows      [{:css :row--extra-tight
                                                                 :row [{}
                                                                       {:col  4
                                                                        :dict :name}
                                                                       {:col  2
                                                                        :align :right
                                                                        :dict :date}]}]}}]}]}}})

(def versub-contract-reviews
  (assoc-in (versub-requirements :reviews)
            [:section :show?] :?.reviews))

(def versub-contract-conditions
  (-> template-schemas/temsub-contract-conditions
      (assoc-in [:dictionary :conditions :template-dict]
                :conditions)
      (update :section assoc
              :show? :?.conditions
              :template-section :conditions)
      (assoc-in [:dictionary :conditions-title]
                {:loc-text :pate.contract.conditions
                 :css      :pate-label})
      (assoc-in [:dictionary :conditions :repeating :condition :phrase-text :label?]
                false)
      (update-in [:section :grid :rows] #(cons [{:dict :conditions-title}] %))))

(def versub-contract-attachments
  (-> versub-attachments
      (assoc-in [:dictionary :attachments :application-attachments :read-only?]
                true)
      (assoc-in [:dictionary :attachments :application-attachments :i18nkey]
                :verdict.contract.attachments)
      (assoc-in [:section :buttons?] false)))

(def versub-contract-upload
  (assoc-in (versub-upload {:type-group #".*"
                            :default    :muut.paatosote
                            :title      :verdict.contract.attachments})
            [:dictionary :upload :template-section]
            :attachments))

(def contract-schema-1 (build-verdict-schema :contract 1
                                             versub-contract
                                             versub-contract-reviews
                                             versub-contract-conditions
                                             versub-contract-attachments
                                             versub-contract-upload))


(defn verdict-schema
  "Nil version returns the latest version."
  ([category version]
   (let [schemas (case (keyword category)
                   :r        [r-verdict-schema-1]
                   :p        [p-verdict-schema-1]
                   :ya       [ya-verdict-schema-1]
                   :tj       [tj-verdict-schema-1]
                   :contract [contract-schema-1]
                   (schema-util/pate-assert false "Invalid schema category:" category))]
     (cond
       (nil? version)                     (last schemas)
       (and (pos? version)
            (<= version (count schemas))) (nth schemas (dec version))
       :else                              (schema-util/pate-assert false "Invalid schema version:" version))))
  ([category]
   (verdict-schema category nil)))
