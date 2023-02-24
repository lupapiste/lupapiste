(ns lupapalvelu.pate.schema-helper
  "Definitions and other helpers for Pate schema building."
  (:require [sade.shared-util :as util]))

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
   :ei-tutkittu-3               "ei tutkittu (oikaisuvaatimus tai lupa pysyy evättynä)"
   :evatty                      "evätty"
   :hallintopakko               "hallintopakon tai uhkasakkoasian käsittely lopetettu"
   :hyvaksytty                  "hyväksytty"
   :ilmoitus-tiedoksi           "ilmoitus merkitty tiedoksi"
   :konversio                   "muutettu toimenpideluvaksi (konversio)"
   :lautakunta-palauttanut      "asia palautettu uudelleen valmisteltavaksi"
   :lautakunta-poistanut        "asia poistettu esityslistalta"
   :lautakunta-poydalle         "asia pantu pöydälle kokouksessa"
   :maarays-peruutettu          "määräys peruutettu"
   :muutti-evatyksi             "muutti evätyksi"
   :muutti-maaraysta            "muutti määräystä tai päätöstä"
   :muutti-myonnetyksi          "muutti myönnetyksi"
   :myonnetty                   "myönnetty"
   :myonnetty-aloitusoikeudella "myönnetty aloitusoikeudella"
   :osittain-myonnetty          "osittain myönnetty"
   :peruutettu                  "peruutettu"
   :puollettu                   "puollettu"
   :pysytti-evattyna            "pysytti evättynä"
   :pysytti-maarayksen-2        "pysytti määräyksen tai päätöksen"
   :pysytti-myonnettyna         "pysytti myönnettynä"
   :pysytti-osittain            "pysytti osittain myönnettynä"
   :siirretty-maaoikeudelle     "siirretty maaoikeudelle"
   :suunnitelmat-tarkastettu    "suunnitelmat tarkastettu"
   :tehty-hallintopakkopaatos-1 "tehty hallintopakkopäätös (ei velvoitetta)"
   :tehty-hallintopakkopaatos-2 "tehty hallintopakkopäätös (asetettu velvoite)"
   :tehty-uhkasakkopaatos       "tehty uhkasakkopäätös"
   :tyohon-ehto                 "työhön liittyy ehto"
   :valituksesta-luovuttu-1     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)"
   :valituksesta-luovuttu-2     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy evättynä)"})

;; Map values are backing system verdict status codes
(def negative-verdict-codes {:asiakirjat-palautettu   38
                             :ei-puollettu            29
                             :ei-tutkittu-3           22
                             :evatty                  21
                             :lautakunta-palauttanut  36
                             :lautakunta-poistanut    39
                             :lautakunta-poydalle     40
                             :muutti-evatyksi         26
                             :peruutettu              37
                             :pysytti-evattyna        28
                             :valituksesta-luovuttu-2 25})

(defn verdict-code-negative?
  "The code might be given as a number, a number string, a string or a keyword"
  [code]
  (let [coerced-code (cond
                       (number? code)           code
                       (util/int-string? code)  (util/->int code)
                       (string? code)           (keyword code)
                       :else                    code)]
    (if (number? coerced-code)
      (-> negative-verdict-codes vals set (contains? coerced-code))
      (boolean (some-> coerced-code negative-verdict-codes)))))

(def review-type-map
  {:muu-katselmus             "muu katselmus"
   :muu-tarkastus             "muu tarkastus"
   :aloituskokous             "aloituskokous"
   :paikan-merkitseminen      "rakennuksen paikan merkitseminen"
   :paikan-tarkastaminen      "rakennuksen paikan tarkastaminen"
   :pohjakatselmus            "pohjakatselmus"
   :rakennekatselmus          "rakennekatselmus"
   :lvi-katselmus             "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus"
   :osittainen-loppukatselmus "osittainen loppukatselmus"
   :loppukatselmus            "loppukatselmus"
   :ei-tiedossa               "ei tiedossa"})

(def ya-review-type-map
  {:aloituskatselmus "Aloituskatselmus"
   :loppukatselmus   "Loppukatselmus"
   :valvonta         "Muu valvontakäynti"})

;; Each type must be part of the corresponding operation names (e.g.,
;; ya-katulupa-maalampotyot -> katulupa)
(def ya-verdict-types [:sijoituslupa :kayttolupa :katulupa :jatkoaika])

(def foreman-codes [:vastaava-tj :vv-tj :iv-tj :erityis-tj :tj])

(def foreman-roles {:vv-tj        "KVV-työnjohtaja"
                    :iv-tj        "IV-työnjohtaja"
                    :erityis-tj   "erityisalojen työnjohtaja"
                    :vastaava-tj  "vastaava työnjohtaja"
                    :tj           "työnjohtaja"})

(defn foreman-role [k]
  (get foreman-roles (keyword k) "ei tiedossa"))

(defn verdict-dates
  ([category]
   ;; The date key ordering is significant! It determines the automatic date calculation. See
   ;; `lupapalvelu.pate.verdict/update-automatic-verdict-dates`.
   (case (keyword category)
     :p  [:julkipano :anto :muutoksenhaku :lainvoimainen :voimassa]
     :ya [:julkipano :anto :muutoksenhaku :lainvoimainen :aloitettava]
     :tj [:anto :muutoksenhaku :lainvoimainen]
     [:julkipano :anto :muutoksenhaku :lainvoimainen
      :aloitettava :voimassa]))
  ([]
   (verdict-dates :r)))

(def review-types (keys review-type-map))

(def ya-review-types (keys ya-review-type-map))

(defn reference-list [path extra]
  {:reference-list (merge {:label? false
                           :type   :multi-select
                           :path   path}
                          extra)})

(defn text-section [id]
  {:loc-prefix (str "pate-" (name id))
   :id         id
   :grid       {:columns 1
                :rows    [[{:dict id}]]}})

(def language-select {:select {:loc-prefix :pate-verdict.language
                               :items supported-languages}})
(def verdict-giver-select {:select {:loc-prefix :pate-verdict.giver
                                    :items      [:lautakunta :viranhaltija]
                                    :sort-by    :text}})
(def complexity-select {:select {:loc-prefix :pate.complexity
                                 :items      [:small :medium :large :extra-large]}})

(def collateral-type {:text {:loc-prefix :pate.collateral-type
                             :items      [:pate.collateral-type.shekki
                                          :pate.collateral-type.panttaussitoumus
                                          :pate.collateral-type.pankkitakaus]}})

(def contract-language
  {:select {:loc-prefix :pate.contract.language
            :item-loc-prefix :pate-verdict.language
            :items      supported-languages}})

(def publishing-verdict
  "publishing-verdict")

(def publishing-proposal
  "publishing-proposal")

(def signing-contract
  "signing-contract")

(def publishing-states
  #{publishing-verdict publishing-proposal signing-contract})

(def publishing-states-as-keywords
  (set (map #(keyword %) publishing-states)))

(defn- date-delta-row [kws]
  (->> kws
       (map #(hash-map :col 2 :dict %))
       (interpose {:dict :plus
                   :css [:date-delta-plus]})))

(defn date-deltas
  "Return date-deltas' `:dictionary` and `:row`. The dictionary includes `dates` and `:plus`."
  [dates required?]
  {:dictionary (reduce-kv (fn [acc k v]
                            (cond-> acc
                              (util/includes-as-kw? dates k)
                              (assoc k {:required?  required?
                                        :date-delta {:unit    v
                                                     :i18nkey (util/kw-path :pate-verdict k)}})))
                          {:plus {:loc-text :plus}}
                          {:julkipano     :days
                           :anto          :days
                           :muutoksenhaku :days
                           :lainvoimainen :days
                           :aloitettava   :years
                           :voimassa      :years})
   :row (date-delta-row dates)})
