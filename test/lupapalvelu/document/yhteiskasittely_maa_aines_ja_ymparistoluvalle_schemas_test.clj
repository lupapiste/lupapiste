(ns lupapalvelu.document.yhteiskasittely-maa-aines-ja-ymparistoluvalle-schemas-test
  (:require [lupapalvelu.document.yhteiskasittely-maa-aines-ja-ymparistoluvalle-schemas :refer :all]
            [midje.sweet :refer :all]))

(facts with-tiedot-esitetty-ottamissuunnitelmassa
       (fact "with-tiedot-esitetty-ottamissuunnitelmassa Adds tiedot-esitetty-ottamissuunnitelmassa in to body-content
              vector as the first field and adds show-when setting to all root-level fields."
             (with-tiedot-esitetty-ottamissuunnitelmassa
               [{:name "foo" :type :checkbox}
                {:name "bar"
                 :type :group
                 :body [{:name :biz :type :string :show-when {:path "foo" :values #{true}}}]}])
             => [{:name "tiedot-esitetty-ottamissuunnitelmassa" :type :checkbox :css [:full-width-component] :size :l}
                 {:name      "foo"
                  :type      :checkbox
                  :show-when {:path "tiedot-esitetty-ottamissuunnitelmassa" :values #{false}}}
                 {:name      "bar"
                  :type      :group
                  :show-when {:path "tiedot-esitetty-ottamissuunnitelmassa" :values #{false}}
                  :body      [{:name :biz :type :string :show-when {:path "foo" :values #{true}}}]}])
       (doseq [visibility-key [:show-when :hide-when]]
         (fact
           (format "with-tiedot-esitetty-ottamissuunnitelmassa should throw if a root level component already contains a
                    %s key because visibility cannot depend on the multiple fields." visibility-key)
           (with-tiedot-esitetty-ottamissuunnitelmassa
             [{:name "foo" :type :string}
              {:name          "bar"
               visibility-key {:path "tiedot-esitetty-ottamissuunnitelmassa" :values #{false}}
               :type          :group
               :body          [{:name :biz :type :string}]}])
           => (throws AssertionError))))

(facts ->checkbox-list
       (fact "->checkbox-list converts list of string into a list of docgen checkobox compomponents
              with optional details or date fields."
             (->checkbox-list [["foo"]
                               ["bar" :with-details]
                               ["biz" :with-date]
                               ["bus" :with-date :with-todo-check]])
             => [{:name "foo" :size :l :type :checkbox}
                 {:name "bar" :size :l :type :checkbox}
                 {:name      "bar-tarkenne"
                  :show-when {:path "bar" :values #{true}}
                  :size      :l
                  :type      :string}
                 {:name "biz" :size :l :type :checkbox}
                 {:name      "biz-paivays"
                  :show-when {:path "biz" :values #{true}}
                  :size      :l
                  :type      :date}
                 {:name "bus" :size :l :type :checkbox}
                 {:name      "bus-paivays"
                  :show-when {:path "bus" :values #{true}}
                  :size      :l
                  :type      :date}
                 {:name      "bus-todo-check"
                  :show-when {:path "bus" :values #{true}}
                  :size      :l
                  :type      :checkbox}])
       (fact ":with-tiedot-esitetty-ottamissuunnitelmassa true option in ->checkbox-list adds
              tiedot esitetty-ottamissuunnitelmassa field to listing and adds show-when to 'main' field.
              Visibility setup of the other fields is not changed."
             (->checkbox-list [["foo"]
                               ["bar" :with-date :with-todo-check]]
                              :with-tiedot-esitetty-ottamissuunnitelmassa true)
             => [{:name "tiedot-esitetty-ottamissuunnitelmassa" :size :l :type :checkbox}
                 {:name      "foo"
                  :size      :l
                  :type      :checkbox
                  :show-when {:path   "tiedot-esitetty-ottamissuunnitelmassa"
                              :values #{false}}}
                 {:name      "bar"
                  :size      :l
                  :type      :checkbox
                  :show-when {:path   "tiedot-esitetty-ottamissuunnitelmassa"
                              :values #{false}}}
                 {:name      "bar-paivays"
                  :show-when {:path "bar" :values #{true}}
                  :size      :l
                  :type      :date}
                 {:name      "bar-todo-check"
                  :show-when {:path "bar" :values #{true}}
                  :size      :l
                  :type      :checkbox}]))

(facts ->permit-status-group
       (fact "->permit-status-group creates a docgen group with myontamispaiva and viranomainen fields,
              and optional vireilla checkbox and with-details field"
             (->permit-status-group
               [["f1"]
                ["f2" :with-vireilla]
                ["f3" :with-mika-lupa]
                ["f4" :with-vireilla :with-mika-lupa]
                ["f5" :with-vireilla :with-mista-luvasta]])
             => [{:name                  "luvat"
                  :type                  :group
                  :repeating             true
                  :repeating-init-empty  true
                  :repeating-allow-empty true
                  :body                  [{:name "luvan-tyyppi"
                                           :type :select
                                           :body [{:name "f1"}
                                                  {:name "f2"}
                                                  {:name "f3"}
                                                  {:name "f4"}
                                                  {:name "f5"}]}
                                          {:name      "mika-lupa"
                                           :type      :string
                                           :show-when {:path   "luvan-tyyppi"
                                                       :values #{"f3" "f4"}}}
                                          {:name      "mista-luvasta"
                                           :type      :string
                                           :show-when {:path   "luvan-tyyppi"
                                                       :values #{"f5"}}}
                                          {:name "myontamispaiva" :type :date}
                                          {:name "viranomainen" :type :string}
                                          {:name      "vireilla"
                                           :type      :checkbox
                                           :show-when {:path   "luvan-tyyppi"
                                                       :values #{"f2" "f4" "f5"}}}]}])

       (fact "Option ':fields-i18n-prefix prefix' in ->permit-status-group call allows you to use same resource key per
               unique field name. Intent of this is to minimize redundant resourse keys."
             (->>
               (->permit-status-group
                 [["foo" :with-vireilla :with-details]]
                 :fields-i18n-prefix "prefix"
                 :details-field-name "overridded-details-field-name")
               (mapcat :body)
               (map #(select-keys % [:name :i18nkey]))
               (into []))
             => [{:name "luvan-tyyppi" :i18nkey "prefix.luvan-tyyppi"}
                 {:name "mika-lupa" :i18nkey "prefix.mika-lupa"}
                 {:name "mista-luvasta" :i18nkey "prefix.mista-luvasta"}
                 {:name "myontamispaiva" :i18nkey "prefix.myontamispaiva"}
                 {:name "viranomainen" :i18nkey "prefix.viranomainen"}
                 {:name "vireilla" :i18nkey "prefix.vireilla"}]))


(facts ->operating-times-group
       (fact "->operating-times-group creates a docgen group with vuotuinen toiminta-aika, viikottainen toimintaaika,
              päivittäinen toimintaaika and mahdolliset poikkemat toimintaajoissa fields, with-details field."
             (->operating-times-group
               [["foo"]
                ["bar" :with-details]])
             => [{:name                  "toiminnoittain"
                  :type                  :group
                  :repeating             true
                  :body                  [{:name "toiminnon-tyyppi"
                                           :type :select
                                           :body [{:name "foo"} {:name "bar"}]}
                                          {:name "tarkenne"
                                           :type :string
                                           :show-when {:path "toiminnon-tyyppi"
                                                       :values #{"bar"}}}
                                          {:name "vuotuinen-toiminta-aika"
                                           :type :string}
                                          {:name "viikoittainen-toiminta-aika"
                                           :type :string}
                                          {:name "paivittainen-toiminta-aika"
                                           :type :string}
                                          {:name "mahdolliset-poikkeamat"
                                           :type :string}]}])

       (fact "Option ':details-field-name <details-field-name>' in ->operating-times-group call allows you to to
              override the name of the field generated with :with-details flag in group vector.
              The name is overridden for groups having :with-details flag in ->permit-status-group call.

              The intent for this solution is twofold:
              1) minimize redundant resource keys and
              2) minimize calling complexity of ->operating-times-group function.

              This option is intentent to be used with :fields-i18n-prefix. See test for :fields-i18n-prefix below."
             (get-in (->operating-times-group
                       [["foo" :with-details]]
                       :details-field-name "overridded-details-field-name") [0 :body 1 :name])
             => "overridded-details-field-name")

       (fact "Option ':fields-i18n-prefix prefix' in ->operating-times-group call allows you to use same resource key per
               unique field name. Intent of this is to minimize redundant resourse keys."
             (->>
               (->operating-times-group
                 [["foo" :with-details]]
                 :fields-i18n-prefix "prefix"
                 :details-field-name "overridded-details-field-name"
                 )
               (mapcat :body)
               (map #(select-keys % [:name :i18nkey]))
               (into []))
             => [{:name "toiminnon-tyyppi" :i18nkey "prefix.toiminnon-tyyppi"}
                 {:name "overridded-details-field-name" :i18nkey "prefix.overridded-details-field-name"}
                 {:name "vuotuinen-toiminta-aika" :i18nkey "prefix.vuotuinen-toiminta-aika"}
                 {:name "viikoittainen-toiminta-aika" :i18nkey "prefix.viikoittainen-toiminta-aika"}
                 {:name "paivittainen-toiminta-aika" :i18nkey "prefix.paivittainen-toiminta-aika"}
                 {:name "mahdolliset-poikkeamat" :i18nkey "prefix.mahdolliset-poikkeamat"}]))

(facts ->substance-consumption-and-storing-group
       (fact "->substance-consumption-and-storing-group creates a repeating docgen group with fields for cosumption and
              storage data."
             (->substance-consumption-and-storing-group
               [["foo"]
                ["bar" :with-details]
                ["biz" :with-laatu]])
             => [{:name                  "listing"
                  :type                  :group
                  :repeating             true
                  :body                  [{:name "tyyppi"
                                           :type :select
                                           :body [{:name "foo"} {:name "bar"} {:name "biz"}]}
                                          {:name      "tarkenne"
                                           :type      :string
                                           :show-when {:path "tyyppi" :values #{"bar"}}}
                                          {:name      "laatu"
                                           :type      :string
                                           :show-when {:path "tyyppi" :values #{"biz"}}}
                                          {:name "keskimaarainen-kulutus"
                                           :type :string}
                                          {:name "maksimi-kulutus"
                                           :type :string}
                                          {:name "varastointipaikka"
                                           :size :l
                                           :type :string}]}])

       (fact "Option ':details-field-name <details-field-name>' in ->substance-consumption-and-storing-group call allows
              you to to override the name of the field generated with :with-details flag in group vector.
              The name is overridden for groups having :with-details flag in ->permit-status-group call.

              This option is intentent to be used with :fields-i18n-prefix. See test for :fields-i18n-prefix below."
             (get-in (->substance-consumption-and-storing-group
                       [["foo" :with-details]]
                       :details-field-name "overridded-details-field-name") [0 :body 1 :name])
             => "overridded-details-field-name")

       (fact "Option ':fields-i18n-prefix prefix' in ->substance-consumption-and-storing-group call allows you to use
              same resource key per unique field name. Intent of this is to minimize redundant resourse keys."
             (->>
               (->substance-consumption-and-storing-group
                 [["foo" :with-details :with-laatu]]
                 :fields-i18n-prefix "prefix"
                 :details-field-name "overridded-details-field-name")
               (mapcat :body)
               (map #(select-keys % [:name :i18nkey]))
               (into []))
             => [{:name "tyyppi" :i18nkey "prefix.tyyppi"}
                 {:name "overridded-details-field-name" :i18nkey "prefix.overridded-details-field-name"}
                 {:name "laatu" :i18nkey "prefix.laatu"}
                 {:name "keskimaarainen-kulutus" :i18nkey "prefix.keskimaarainen-kulutus"}
                 {:name "maksimi-kulutus" :i18nkey "prefix.maksimi-kulutus"}
                 {:name "varastointipaikka" :i18nkey "prefix.varastointipaikka"}]))

(facts ->groups-with-same-body
       (fact "->groups-with-shared-body creates a collection of docgen groups with identical body"
             (->groups-with-same-body
               ["foo" "bar"]
               :group-body [{:name "biz" :type :string} {:name "bus" :type :checkbox}])
             => [{:name "foo"
                  :type :group
                  :body [{:name "biz" :type :string} {:name "bus" :type :checkbox}]}
                 {:name "bar"
                  :type :group
                  :body [{:name "biz" :type :string} {:name "bus" :type :checkbox}]}])

       (fact "Option ':fields-i18n-prefix prefix' in ->groups-with-same-body call allows you to use
              same resource key per unique field name to minimize redundant resourse keys."
             (->>
               (->groups-with-same-body
                 ["foo" "bar"]
                 :fields-i18n-prefix "prefix"
                 :group-body [{:name "biz" :type :string} {:name "bus" :type :checkbox}])
               (mapcat :body)
               (map #(select-keys % [:name :i18nkey]))
               (into []))
             => [;foo -group fields
                 {:name "biz" :i18nkey "prefix.biz"}
                 {:name "bus" :i18nkey "prefix.bus"}
                 ;bar -group fields
                 {:name "biz" :i18nkey "prefix.biz"}
                 {:name "bus" :i18nkey "prefix.bus"}]))
