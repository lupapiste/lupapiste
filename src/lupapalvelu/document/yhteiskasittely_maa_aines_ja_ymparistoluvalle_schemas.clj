(ns lupapalvelu.document.yhteiskasittely-maa-aines-ja-ymparistoluvalle-schemas
  (:require
    [lupapalvelu.document.schemas :refer :all]
    [lupapalvelu.document.tools :refer :all]
    [sade.strings :as ss]))

(defn ->checkbox-list
  "Converts a list of field name, options tuplest to a docgen node vector containig
   checkbox field for each field name and optionally field for details, date, or todo-checkbox"
  [fields & {:keys [with-tiedot-esitetty-ottamissuunnitelmassa]}]
  (->> (for [[fieldName & options] fields
             :let [options (set options)]]
         [(merge {:name fieldName
                  :size :l
                  :type :checkbox}
                 (when with-tiedot-esitetty-ottamissuunnitelmassa
                   {:show-when {:path   "tiedot-esitetty-ottamissuunnitelmassa"
                                :values #{false}
                                }}))
          (when (options :with-details)
            {:name      (str fieldName "-tarkenne")
             :type      :string
             :size      :l
             :show-when {:path fieldName :values #{true}}})
          (when (options :with-date)
            {:name      (str fieldName "-paivays")
             :type      :date
             :size      :l
             :show-when {:path fieldName :values #{true}}})
          (when (options :with-todo-check)
            {:name      (str fieldName "-todo-check")
             :type      :checkbox
             :size      :l
             :show-when {:path fieldName :values #{true}}})
          ])
       (mapcat identity)
       (concat
         [(when with-tiedot-esitetty-ottamissuunnitelmassa
            {:name "tiedot-esitetty-ottamissuunnitelmassa"
             :size :l
             :type :checkbox})])
       (keep identity)
       body))

(defn ->coordinates-fields [name]
  {:name name
   :type :group
   :body [{:name "pohjoiskoordinaatti"
           :type :string}
          {:name "itakoordinaatti"
           :type :string}]})

(defn ^:private clean-up-nils-from-body
  "Remove nils from docgens body vector"
  [node]
  (update node :body (partial keep identity)))

(defn ^:private apply-field-i18n-prefix
  "Set :i18nkey "
  [fields-i18n-prefix node]
  (if fields-i18n-prefix
    (assoc node :i18nkey (ss/join "." [fields-i18n-prefix (:name node)]))
    node))

(defn ^:private set-label-false-for-each-field-in-a-table-body
  "If group-node type is table set label false for each field in table, otherwise do nothing"
  [group-node]
  (if (= :table (:type group-node))
    (update group-node :body (partial mapv #(assoc % :label false)))
    group-node))

(defn ^:private apply-field-i18n-prefix-to-elements-in-body
  [fields-i18n-prefix group-node]
  (update group-node :body (partial mapv #(apply-field-i18n-prefix fields-i18n-prefix %))))

(defn with-tiedot-esitetty-ottamissuunnitelmassa
  "Adds tiedot-esitetty-ottamissuunnitelmassa in to body vector as the first field.
  If user chooses all other field in the body are hidden.

  Note: If there are non-nil show-when or hide-when attributes, function will throw.
  User of this function must organize docgen nodes so that are no conflicting visibility settings,
  because currently docgen does not support multiple fields in hide-when or show-when options."
  [body-content]
  {:pre [(no-nodes-has-visibility-condition-in-body? body-content)]}
  (->> body-content
       (map #(assoc % :show-when {:path   "tiedot-esitetty-ottamissuunnitelmassa"
                                  :values #{false}}))
       (concat [{:name "tiedot-esitetty-ottamissuunnitelmassa"
                 :type :checkbox
                 :css  [:full-width-component]
                 :size :l}])))

(defn ^:private parse-items-with-options-list
  [groups]
  (let [types (for [[type-name & option-tags] groups]
                [type-name (apply hash-set option-tags)])
        options-for-select (into [] (map (fn [[name _]] {:name name}) types))
        show-when-has-option (fn [option-key]
                               (->> types
                                    (filter (fn [[_ tags]] (tags option-key)))
                                    (map first)
                                    (into #{})))]
    {:types                             types
     :options-for-select                options-for-select
     :show-when-for-items-having-option show-when-has-option}))

(defn ->permit-status-group
  "Creates groups for the statuses of realated permits.

  Group has always following field
  - myontamispaivä
  - viranomainen

  and optionally:
  - :with-vireilla -tag: adds viraillä checkbox
  - :with-mista-luvasta tag adds field \"mistä luvasta\"
  - :with-mika-lupa tag adds field \"mikä?\")

  Other option
  :fields-i18n-prefix allows you to use the same resource keys for all similar fields in group.

  See tests for more examples on the usage.
  "
  [items
   & {:keys [fields-i18n-prefix]}]
  (let [{:keys [options-for-select show-when-for-items-having-option]} (parse-items-with-options-list items)]
    (->> [{:name                  "luvat"
           :type                  :group
           :repeating             true
           :repeating-init-empty  true
           :repeating-allow-empty true
           :body                  [{:name "luvan-tyyppi"
                                    :type :select
                                    :body options-for-select}
                                   {:name      "mika-lupa"
                                    :type      :string
                                    :show-when {:path   "luvan-tyyppi"
                                                :values (show-when-for-items-having-option :with-mika-lupa)}}
                                   {:name      "mista-luvasta"
                                    :type      :string
                                    :show-when {:path   "luvan-tyyppi"
                                                :values (show-when-for-items-having-option :with-mista-luvasta)}}
                                   {:name "myontamispaiva"
                                    :type :date}
                                   {:name "viranomainen"
                                    :type :string}
                                   {:name      "vireilla"
                                    :type      :checkbox
                                    :show-when {:path   "luvan-tyyppi"
                                                :values (show-when-for-items-having-option :with-vireilla)}}]}]
         (mapv (partial apply-field-i18n-prefix-to-elements-in-body fields-i18n-prefix)))))

(defn ->operating-times-group
  "Creates repetating groups for the operating time per operations (toiminta aika toiminnoittain).

  Group has always following field
  - vuotuinen toiminta-aika
  - viikottainen toimintaaika (viikonpäivät)
  - päivittäinen toimintaaika (kellon ajat)
  - mahdolliset poikkemat toimintaajoissa

  and optionally:
  - :with-details tag add field for details (e.g. \"mikä muu toiminto\")

  Other option
  :fields-i18n-prefix allows you to use the same resource keys for all similar fields in group.
  :details-field-name by default details field name is tarkenne. With this you can override it for :with-details group

  See tests for more examples on the usage.
  "
  [items
   & {:keys [fields-i18n-prefix details-field-name]
      :or   {details-field-name "tarkenne"}}]
  (let [{:keys [options-for-select show-when-for-items-having-option]} (parse-items-with-options-list items)]
    (->> [{:name                  "toiminnoittain"
           :type                  :group
           :repeating             true
           :body                  [{:name "toiminnon-tyyppi"
                                    :type :select
                                    :body options-for-select}
                                   {:name      details-field-name
                                    :type      :string
                                    :show-when {:path   "toiminnon-tyyppi"
                                                :values (show-when-for-items-having-option :with-details)}}
                                   {:name "vuotuinen-toiminta-aika"
                                    :type :string}
                                   {:name "viikoittainen-toiminta-aika"
                                    :type :string}
                                   {:name "paivittainen-toiminta-aika"
                                    :type :string}
                                   {:name "mahdolliset-poikkeamat"
                                    :type :string}]}]
         (mapv (partial apply-field-i18n-prefix-to-elements-in-body fields-i18n-prefix)))))

(defn ->substance-consumption-and-storing-group
  "Creates a repeating group for the fuel and other substance consumption and storing (Polttoaineiden ja muiden aineiden kulutus ja
  varastointi).

  Group has always following field
  - average consumption (keskimääräinen kulutus)
  - maximum consumption (maksimikulutus)
  - storage place (varastointipaikka)

  and optionally:
  - :with-laatu tag add field for laatu (quality/type)
  - :with-details tag add field for details (e.g. \"mikä muu aine\")

  Other option
  :fields-i18n-prefix allows you to use the same resource keys for all similar fields in group.
  :details-field-name by default details field name is tarkenne. With this you can override it for :with-details group

  See tests for more examples on the usage.
  "
  [items
   & {:keys [fields-i18n-prefix details-field-name]
      :or   {details-field-name "tarkenne"}}]
  (let [{:keys [options-for-select show-when-for-items-having-option]} (parse-items-with-options-list items)]
    (->> [{:name                  "listing"
           :type                  :group
           :repeating             true
           :body                  [{:name "tyyppi"
                                    :type :select
                                    :body options-for-select}
                                   {:name      details-field-name
                                    :type      :string
                                    :show-when {:path   "tyyppi"
                                                :values (show-when-for-items-having-option :with-details)}}
                                   {:name      "laatu"
                                    :type      :string
                                    :show-when {:path   "tyyppi"
                                                :values (show-when-for-items-having-option :with-laatu)}}
                                   {:name "keskimaarainen-kulutus"
                                    :type :string}
                                   {:name "maksimi-kulutus"
                                    :type :string}
                                   {:name "varastointipaikka"
                                    :type :string
                                    :size :l}]}]
         (mapv (partial apply-field-i18n-prefix-to-elements-in-body fields-i18n-prefix)))))

(defn ->groups-with-same-body
  "Creates a collection group with identical body.

  Other option
  :fields-i18n-prefix allows you to use the same resource keys for all similar fields in group.

  See tests for more examples on the usage."
  [groups
   & {:keys [fields-i18n-prefix group-body]}]
  (->> (for [group-name groups]
         {:name group-name
          :type :group
          :body group-body})
       (map clean-up-nils-from-body)
       (map (partial apply-field-i18n-prefix-to-elements-in-body fields-i18n-prefix))
       (into [])))

(def luvan-kohde
  (body {:name "toiminnan-luonne-ja-kesto"
         :type :group
         :body [{:name    "kuvaus"
                 :type    :text
                 :max-len 4000}
                {:name    "luvan-aikajanne"
                 :type    :string
                 :subtype :number}]}
        {:name "toiminnan-aloitus"
         :type :group
         :css  [:full-width-component-wrapper]
         :body [{:name "aloitus-ennen-lainvoimaisuutta"
                 :size :l
                 :type :checkbox}
                {:name      "aloitus-ennen-lainvoimaisuutta-perustelu"
                 :type      :text
                 :max-len   4000
                 :required  true
                 :show-when {:path   "aloitus-ennen-lainvoimaisuutta"
                             :values #{true}}}]}))

(def toiminta-alueen-sijainti
  (body
    {:name "perustiedot"
     :type :group
     :body [{:name "toiminta-alueen-nimi"
             :type :string
             :size :l}]}
    {:name        "kiinteisto-tiedot"
     :type        :table
     :uicomponent :docgen-property-list
     :validator   :property-list
     :repeating   true
     :approvable  true
     :body        [{:name     "kiinteistotunnus"
                    :required true
                    :i18nkey  "kiinteistoLista.kiinteistotunnus"
                    :type     :string
                    :subtype  :kiinteistotunnus}
                   ; tilanNimi is camelCase in :docgen-property-list componment,
                   ; therefore camalCase use used here instead of kebab-case
                   {:name    "tilanNimi"
                    :i18nkey "kiinteistoLista.tilanNimi"
                    :type    :string}]}
    {:name "ottamisalueen-keskipisteen-koordinaatit"
     :type :group
     :body [{:name "pohjoiskoordinaatti"
             :type :string}
            {:name "itakoordinaatti"
             :type :string}]}
    {:name "kiinteiston-hallintaoikeus"
     :type :group
     :body [{:name    "kuvaus"
             :type    :text
             :max-len 4000}]}
    {:name "toiminta-alueen-rajanaapurit"
     :type :group
     :css  [:full-width-component-wrapper]
     :body [{:name "tiedot-erillisella-lomakkeelle"
             :type :checkbox}]}
    {:name "kaavoitus-tilanne"
     :type :group
     :css  [:checkbox-list-component-wrapper]
     :body (->checkbox-list
             [["maakuntakaava" :with-details]
              ["yleiskaava" :with-details]
              ["asemakaava" :with-details]
              ["poikkeamispäätös"]
              ["kaavamuutos-vireillä"]
              ["ei-oikeusvaikutteista-kaavaa"]])}
    {:name "sijaitseeko-pohjavesi-alueella"
     :type :group
     :body [{:name     "kylla-ei-osittain"
             :type     :select
             :sortBy   :displayname
             :required true
             :body     (->select-options ["kylla" "ei" "osittain"])}
            {:name      "pohjavesi-alueen-nimi-ja-tunnus"
             :type      :string
             :size      :l
             :show-when {:path   "kylla-ei-osittain"
                         :values #{"kylla" "osittain"}}}]}
    {:name "sijaitseeko-rantavyohykkeella"
     :type :select
     :size :l
     :body (->select-options ["kylla" "ei"])}))

; OTETTAVA MAA-AINES JA OTTAMISEN JÄRJESTÄMINEN
(def otettava-maa-aines-ja-ottamisen-jarjestaminen
  (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
              {:name    "otettavan-aineksen-kokonaismaara"
               :type    :string
               :subtype :number
               :unit    :k-m3}
              {:name    "arvioitu-vuotuinen-ottamismaara"
               :type    :string
               :subtype :number
               :unit    :k-m3}
              {:name    "ottamisalueen-pinta-ala"
               :type    :string
               :subtype :number
               :unit    :hehtaaria}
              {:name    "alin-ottamistaso"
               :type    :string
               :subtype :number
               :unit    :m}
              {:name "pohjaveden-pinnan-ylin-korkeustaso"
               :type :group
               :body [{:name    "korkeustaso"
                       :type    :string
                       :subtype :number
                       :unit    :m}
                      {:name "havaintopiste"
                       :type :string}
                      {:name "havaintoaika"
                       :type :date}]}
              {:name "pohjaveden-pinnan-keskimaarainen-korkeustaso"
               :type :group
               :body [
                      {:name    "korkeustaso"
                       :type    :string
                       :subtype :number
                       :unit    :m}]}
              {:name "otettavan-aineksen-laatu"
               :type :group
               :body [{:name    "kalliokiviaines"
                       :type    :string
                       :subtype :number
                       :unit    :k-m3}
                      {:name    "sora-ja-hiekka"
                       :type    :string
                       :subtype :number
                       :unit    :k-m3}
                      {:name    "moreeni"
                       :type    :string
                       :subtype :number
                       :unit    :k-m3}
                      {:name    "siltti-ja-savi"
                       :type    :string
                       :subtype :number
                       :unit    :k-m3}
                      {:name    "eloperaiset-maa-ainekset"
                       :type    :string
                       :subtype :number
                       :unit    :k-m3}]}
              {:name       "otettavan-aineksen-kayttotarkoitus"
               :group-help "yhteiskasittelyn-otettava-maa-aines-ja-ottamisen-jarjestaminen.otettavan-aineksen-kayttotarkoitus._group-help"
               :type       :group
               :body       [{:name "asfalttituotanto"
                             :type :string}
                            {:name "betonituotanto"
                             :type :string}
                            {:name "rakennuskivituotanto"
                             :type :string}
                            {:name "raidesepeli"
                             :type :string}
                            {:name "teiden-rakentaminen-ja-tienpito"
                             :type :string}
                            {:name "taytot"
                             :type :string}
                            {:name "muut-kayttotarkoitus"
                             :type :string}]}
              {:name "esitys-vakuudeksi"
               :type :group
               :body [{:name "kuvaus"
                       :type :text}]}
              {:name "ottamistoiminnasta-syntyva-kaivannaisjate"
               :type :group
               :body [{:name "kuvaus"
                       :type :text}]}))

; 6. Kivenmurskaamoa ja -louhimoa koskevat tiedot

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-1-perustiedot
  {:name "perustiedot"
   :type :group
   :body [{:name "murskaamon-tyyppi"
           :type :select
           :body (->select-options ["kiintä" "siirrettävä"])}
          {:name "murskaamon-kayttovoima"
           :type :select
           :body (->select-options ["dieselmoottori" "sahkomoottir"])}
          (->coordinates-fields "murskaamon-sijainti")
          {:name "tiedot-toiminnan-laitteistosta"
           :type :text}]})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-2-hairiolle-alttiit-kohteet
  {:name       "hairiolle-alttiit-kohteet"
   :group-help "yhteiskasittelyn-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot.hairiolle-alttiit-kohteet._group_help"
   :type       :group
   :body       [{:name                  "kohteet"
                 :type                  :table
                 :repeating             true
                 :repeating-init-empty  true
                 :repeating-allow-empty true
                 :body                  [{:name  "kohde"
                                          :label false
                                          :type  :select
                                          :body  (->select-options ["asuinkiinteisto"
                                                                    "loma-asunto"
                                                                    "koulu-tai-paivokoti"
                                                                    "leikkikentta"
                                                                    "sairaala"
                                                                    "virkistysalue"
                                                                    "sairaala"
                                                                    "pohjavesialue"
                                                                    "pohjavedenottamo"
                                                                    "talousvesikaivo"
                                                                    "vesisto"
                                                                    "natura2000-alue"
                                                                    "muu-luonnonsuojelukohde"
                                                                    "muu-hairiolle-altis-kohde"])}
                                         {:name  "kohteen-nimi-kiinteistotunnus-tai-kayntiosoite"
                                          :label false
                                          :type  :string
                                          :size  :l}
                                         {:name    "etaisyys"
                                          :type    :string
                                          :label   false
                                          :subtype :number
                                          :unit    :m}
                                         {:name  "merkinta-kartalla"
                                          :type  :string
                                          :label false}]}]})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-3-aines-maarat
  {:name "maarat"
   :type :group
   :body [{:name "louhintamaara"
           :type :group
           :body [{:name "keskiarvo" :type :string :subtype :number}
                  {:name "maksimi" :type :string :subtype :number}]}
          {:name "murskattava-aines"
           :type :group
           :body [{:name "keskiarvo" :type :string :subtype :number}
                  {:name "maksimi" :type :string :subtype :number}]}
          ]})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-4-tuotteet-ja-varastointi
  {:name "tuotteet-ja-varastointi"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     {:name      "tuotanto"
                      :type      :table
                      :repeating true
                      :body      [{:name     "nimi"
                                   :required true
                                   :label    false
                                   :size     :l
                                   :type     :string}
                                  {:name     "keskiarvo"
                                   :required true
                                   :label    false
                                   :type     :string
                                   :subtype  :number}
                                  {:name     "maksimi"
                                   :required true
                                   :label    false
                                   :type     :string
                                   :subtype  :number}]}
                     {:name "varasto-kasojen-ainesmaarat"
                      :type :text})})

(defn build-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-5-toiminta-ajat
  [i18n-prefix]
  {:name "toiminta-ajat"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     {:name    "toiminta-ajat-vuodet-ja-kuukaudet"
                      :type    :text
                      :max-len 4000}
                     (->operating-times-group
                       [["murskaus"]
                        ["poraus"]
                        ["rikotus"]
                        ["rajautys"]
                        ["kuormaus-ja-kuljetus"]
                        ["muu" :with-details]]
                       :fields-i18n-prefix i18n-prefix
                       :details-field-name "muu-toiminta-aika"))})

(defn build-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-6part1-polttoaineiden-ja-muiden-aineiden-kulutus-ja-varastointi
  [i18n-prefix]
  {:name "polttoaineiden-ja-muiden-aineiden-kulutus-ja-varastointi"
   :type :group
   :body (build-body
           [with-tiedot-esitetty-ottamissuunnitelmassa]
           (->substance-consumption-and-storing-group
             [["öljyt"]
              ["voitelu-aineet"]
              ["polttoaine" :with-laatu]
              ["rajahdysaine" :with-laatu]
              ["polynsidonta-aine" :with-laatu]
              ["muu-aine" :with-details]]
             :fields-i18n-prefix i18n-prefix
             :details-field-name "mika-muu-aine"))})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-6part2-sahkonkulutus-ja-vedonkaytto
  {:name "sahkon-ja-vedenkaytto"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     {:name "vesi"
                      :type :group
                      :body [{:name "tiedot-vedenotosta-ja-kaytosta"
                              :type :text}]}
                     {:name "sahko"
                      :type :group
                      :body [{:name    "arvio-sahkon-kulutuksesta"
                              :type    :string
                              :subtype :number}
                             {:name "sahko-hankitaan"
                              :type :select
                              :body (->select-options ["verkosta" "aggregaatista"])}]})})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-7-ymparisto-asioiden-hallinta
  {:name "ymparisto-asioiden-hallinta"
   :type :group
   :css  [:checkbox-list-component-wrapper]
   :body (->checkbox-list
           [["laitokselle-on-ymparistoasioiden-hallintajarjestelma" :with-details]
            ["hallintajarjestelma-on-sertifioitu"]]
           :with-tiedot-esitetty-ottamissuunnitelmassa true
           )})

(defn build-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-8-paastot-ilmaan-ja-niiden-puhdistaminen
  [i18n-prefix]
  {:name "paastot-ilmaan-ja-niiden-puhdistaminen"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     (->groups-with-same-body ["hiukkaset"
                                               "typen-oksidit"
                                               "rikkidioksidi"
                                               "hiilidioksidi"]
                                              :fields-i18n-prefix i18n-prefix
                                              :group-body [{:name "paastolahde" :type :string}
                                                           {:name "paaston-maara" :type :string :subtype :number}])
                     {:name "paastojen-puhdistaminen-ja-minimoiminen"
                      :type :text})})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-9-melu-ja-tarina
  {:name "melu-ja-tarina"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     {:name                  "melulahteet"
                      :type                  :group
                      :repeating             true
                      :repeating-allow-empty true
                      :body                  [{:name "melulahde"
                                               :type :string}
                                              {:name    "aanitehotaso"
                                               :type    :string
                                               :subtype :number}
                                              {:name "melu-on-kapeakaistaista-tai-iskumaista"
                                               :type :checkbox}
                                              {:name "meluntorjuntatoimet"
                                               :type :text}]}
                     {:name "toimet-melun-vahentamiseksi"
                      :type :text}
                     {:name "onko-melu-mitattu-tai-arvioitu-laskelmilla"
                      :type :group
                      :css  [:checkbox-list-component-wrapper]
                      :body (->checkbox-list [["mitattu" :with-date :with-todo-check]
                                              ["arvioitu-laskelmin" :with-date :with-todo-check]])}
                     {:name "tarinavaikutukset-ja-toimet-niiden-vahentamiseksi"
                      :type :text})})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-10-maaperan-pohjavesien-ja-pintavesien-suojelutoimet
  {:name "maaperän-pohjavesien-ja-pintavesien-suojelutoimet"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     {:name "toimet-maaperan-ja-pohjavesien-pilaantumisen-ehkaisemiseksi"
                      :type :text}
                     {:name "hulevesijarjestelyt"
                      :type :text}
                     {:name "jatevesien-kasittely"
                      :type :text})})

(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-11-syntyvat-jatteeet-ja-niiden-kasittely
  {:name "syntyvat-jatteeet-ja-niiden-kasittely"
   :type :group
   :body (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
                     {:name      "jatelistaus"
                      :type      :table
                      :repeating true
                      :body      [{:name  "jatenimike"
                                   :label false
                                   :type  :string}
                                  {:name    "arvioitumaara"
                                   :label   false
                                   :type    :string
                                   :subtype :number}
                                  {:name  "kasittely-tai-hyodyntamistapa"
                                   :label false
                                   :type  :string
                                   :size  :l}
                                  {:name  "toimituspaikka"
                                   :label false
                                   :type  :string
                                   :size  :l}]}
                     {:name "tiedot-vaarallisista-jatteista"
                      :type :text})})



(def kivenmurskaamoa-ja-louhintoa-koskevat-tiedot
  (let [i18n-prefix "yhteiskasittelyn-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot._shared_field_label"]
    (body kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-1-perustiedot
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-2-hairiolle-alttiit-kohteet
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-3-aines-maarat
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-4-tuotteet-ja-varastointi
          (build-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-5-toiminta-ajat i18n-prefix)
          (build-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-6part1-polttoaineiden-ja-muiden-aineiden-kulutus-ja-varastointi i18n-prefix)
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-6part2-sahkonkulutus-ja-vedonkaytto
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-7-ymparisto-asioiden-hallinta
          (build-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-8-paastot-ilmaan-ja-niiden-puhdistaminen i18n-prefix)
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-9-melu-ja-tarina
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-10-maaperan-pohjavesien-ja-pintavesien-suojelutoimet
          kivenmurskaamoa-ja-louhintoa-koskevat-tiedot-11-syntyvat-jatteeet-ja-niiden-kasittely)))

; LIIKENNE JA LIIKENNEJÄRJESTELYT
(def liikenne-ja-liikennejarjestelyt
  (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
              {:name    "toiminnasta-aiheutuva-raskas-liikenne"
               :type    :string
               :subtype :number}
              {:name "selvitys-tieyhteyksista-ja-tieoikeuksista"
               :type :text}
              {:name "kuvaus-teiden-paallystamisesta-ja-polyntorjuntakeinoista"
               :type :text}))

; ARVIO TOIMINNAN VAIKUTUKSISTA YMPÄRISTÖÖN
(def arvio-toiminnan-vaikutuksista-ymparistoon
  (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
              {:name "yleiskuvaus-ymparistoolosuhteista-ja-vaikutuksista-ymparistoon"
               :type :text}
              {:name "vaikutukset-yleiseen-viihtyvyyteen-ja-ihmisten-terveyteen"
               :type :text}
              {:name "vaikutukset-lountoarvoihin"
               :type :text}
              {:name "vaikutukset-vesistoon"
               :type :text}
              {:name "vaikutukset-ilmanlaatuun"
               :type :text}
              {:name "vaikutukset-maaperaan-ja-pohjaveteen"
               :type :text}
              {:name "ympäristövaikutusten-arviointimenettely"
               :type :group
               :css  [:checkbox-list-component-wrapper]
               :body (->checkbox-list [["tehty" :with-date]
                                       ["kannanotto-ettei-arviointia-tarvita" :with-date]])}))

; TOIMINTAAN LIITTYVÄT YMPÄRISTÖRISKIT, ONNETTOMUUKSIEN ENNALTAEHKÄISY JA VARAUTUMINEN POIKKEUKSELLISIIN TILANTEISIIN
(def ymparistoriskit-onnettomuuksien-ehkaisy-ja-poikkeustilanteet
  (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
              {:name "kuvaus"
               :type :text}
              {:name "varautumissuunnitelma-tehty"
               :type :checkbox
               :size :l}))

; TOIMINNAN TARKKAILU
(def toiminnan-tarkkailu
  (build-body [with-tiedot-esitetty-ottamissuunnitelmassa]
              {:name "kayttotarkkailu"
               :type :text}
              {:name "paasto-ja-vaikutustarkkailu"
               :type :text}
              {:name "menetelmat-ja-laadunvarmistus"
               :type :text}
              {:name "raportointi-ja-tarkkailuohjelmat"
               :type :text}))

; 11. VOIMASSA TAI VIREILLÄ OLEVAT LUVAT, PÄÄTÖKSET JA SOPIMUKSET
(def voimassa-tai-vireilla-olevat-luvat
  (let [i18n-prefix "yhteiskasittelyn-voimassa-tai-vireilla-olevat-luvat._shared_field_label"]
    (build-body
      [with-tiedot-esitetty-ottamissuunnitelmassa]
      (->permit-status-group [["ymparistolupa"]
                              ["maa-aineslupa"]
                              ["vesilain-mukainen-lupa" :with-vireilla]
                              ["rakennuslupa" :with-vireilla]
                              ["toimenpidelupa" :with-vireilla]
                              ["paatos-kemikaalien-teollisesta-kasittelysta-ja-varastoinnista" :with-vireilla]
                              ["sopimus-yleiseen-tai-toisen-viemariin-liittymisesta" :with-vireilla]
                              ["jatevesien-johtamislupa-vesistoon" :with-vireilla]
                              ["lupa-jateveden-johtamiseksi-ojaan-tai-maahan" :with-vireilla]
                              ["maanomistajan-suostumus-jateveden-johtamiselle" :with-vireilla]
                              ["muu-lupa-tai-paatos" :with-vireilla :with-mika-lupa]
                              ["muutoksenhakutuomioistuimen-paatos-maa-ainesluvasta" :with-vireilla]
                              ["muutoksenhakutuomioistuimen-paatos-ymparistoluvasta" :with-vireilla]
                              ["muutoksenhakutuomioistuimen-paatos-muusta-luvasta" :with-vireilla :with-mista-luvasta]]
                             :fields-i18n-prefix i18n-prefix)

      {:name "muut-vaikuttavat-asiat"
       :type :group
       :css  [:full-width-component-wrapper]
       :body [{:name     "onko-muita-asioita"
               :type     :select
               :size     :l
               :body     (->select-options ["ei" "kylla"])
               :required true
               }
              {:name      "onko-muita-asioita-tarkenne"
               :type      :text
               :max-len   4000
               :required  true
               :show-when {:path   "onko-muita-asioita"
                           :values #{"kylla"}}}]})))


(defschemas
  1
  [{:info {:name       "yhteiskasittely-maa-aines-ja-ymparistoluvalle-luvan-kohde"
           :order      1
           :approvable true}
    :body luvan-kohde}
   {:info {:name       "yhteiskasittelyn-toiminta-alueen-sijainti"
           :order      2
           :approvable true}
    :body toiminta-alueen-sijainti}
   {:info {:name       "yhteiskasittelyn-otettava-maa-aines-ja-ottamisen-jarjestaminen"
           :order      3
           :approvable true}
    :body otettava-maa-aines-ja-ottamisen-jarjestaminen}
   {:info {:name       "yhteiskasittelyn-kivenmurskaamoa-ja-louhintoa-koskevat-tiedot"
           :order      4
           :approvable true}
    :body kivenmurskaamoa-ja-louhintoa-koskevat-tiedot}
   {:info {:name       "yhteiskasittelyn-liikenne-ja-liikenne-jarjestelyt"
           :order      5
           :approvable true}
    :body liikenne-ja-liikennejarjestelyt}
   {:info {:name       "yhteiskasittelyn-arvio-toiminnan-vaikutuksista-ymparistoon"
           :order      6
           :approvable true}
    :body arvio-toiminnan-vaikutuksista-ymparistoon}
   {:info {:name       "yhteiskasittelyn-ymparistoriskit-onnettomuuksien-ehkaisy-ja-poikkeustilanteet"
           :order      7
           :approvable true}
    :body ymparistoriskit-onnettomuuksien-ehkaisy-ja-poikkeustilanteet}
   {:info {:name       "yhteiskasittelyn-toiminnan-tarkkailu"
           :order      8
           :approvable true}
    :body toiminnan-tarkkailu}
   {:info {:name       "yhteiskasittelyn-voimassa-tai-vireilla-olevat-luvat"
           :order      9
           :approvable true}
    :body voimassa-tai-vireilla-olevat-luvat}])
