(ns lupapalvelu.document.poikkeamis-schemas
  (:require [lupapalvelu.document.schemas :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapiste-commons.usage-types :as usages]))

(def rakennushanke {:info {:name "rakennushanke"
                           :order 50
                           :removable true
                           :approvable true
                           :deny-removing-last-document true
                           :repeating false}
                    :body [{:name "kaytettykerrosala" :type :group
                            :validator :poikkeus-olemassa-olevat-rakennukset
                            :body [{:name "pintaAla" :type :string :size :s :unit :m2 :subtype :number}
                                   {:name "kayttotarkoitusKoodi" :type :select :sortBy :displayname :size :l :i18nkey "kayttotarkoitus"
                                    :body usages/rakennuksen-kayttotarkoitus}]}
                           {:name "toimenpiteet"
                            :type :group
                            :repeating false
                            :approvable true
                            :body [{:name "kayttotarkoitus" :type :select :sortBy :displayname :size :l :i18nkey "kayttotarkoitus"
                                    :required true
                                    :body usages/rakennuksen-kayttotarkoitus}
                                   {:name "Toimenpide" :type :select :sortBy :displayname :size :l :required true
                                    :body [{:name "uusi"}
                                           {:name "laajennus"}
                                           {:name "perustus"}
                                           {:name "perusparannus"}
                                           {:name "uudelleenrakentaminen"}
                                           {:name "purkaminen"}
                                           {:name "muu muutosty\u00f6"}
                                           {:name "k\u00e4ytt\u00f6tarkoitusmuutos"}
                                           {:name "loma-asunnon muuttaminen vakituiseksi"}]}
                                   {:name "huoneistoja" :type :string :subtype :number :size :s}
                                   {:name "kerroksia" :type :string :subtype :number :size :s}
                                   {:name "kerrosala" :type :string :subtype :number :unit :m2 :size :s}
                                   {:name "kokonaisala" :type :string :subtype :number :unit :m2 :size :s}]}]})

(def suunnittelutarveratkaisun-lisaosa {:info {:name "suunnittelutarveratkaisun-lisaosa"
                                               :approvable true
                                               :order 52}
                                        :body [{:name "kaavoituksen_ja_alueiden_tilanne":type :group :layout :vertical
                                                :body [{:name "asemakaavaluonnos" :type :checkbox}
                                                       {:name "yleiskaavaa" :type :checkbox}
                                                       {:name "rajoittuuko_tiehen" :type :checkbox}
                                                       {:name "tienkayttooikeus" :type :checkbox}
                                                       {:name "vesijohto" :type :checkbox}
                                                       {:name "viemarijohto" :type :checkbox}]}

                                               {:name "vaikutukset_yhdyskuntakehykselle":type :group :layout :vertical
                                                :body [{:name "etaisyys_alakouluun" :type :string :subtype :number :unit :km :size :s}
                                                       {:name "etaisyys_ylakouluun" :type :string :subtype :number :unit :km :size :s}
                                                       {:name "etaisyys_kauppaan" :type :string :subtype :number :unit :km :size :s}
                                                       {:name "etaisyys_paivakotiin" :type :string :subtype :number :unit :km :size :s}
                                                       {:name "etaisyys_kuntakeskuksen_palveluihin" :type :string :subtype :number :unit :km :size :s}
                                                       {:name "turvallinen_polkupyoratie_kouluun" :type :checkbox}
                                                       {:name "muita_vaikutuksia" :type :text :max-len 4000 :layout :full-width}]}

                                               {:name "maisema":type :group :layout :vertical
                                                :body [{:name "pellolla" :type :checkbox}
                                                       {:name "metsassa" :type :checkbox}
                                                       {:name "metsan_reunassa" :type :checkbox}
                                                       {:name "nykyisen_rakennuspaikan_vieressa" :type :checkbox}
                                                       {:name "vanhalla_rakennuspaikalla" :type :checkbox}]}

                                               {:name "luonto_ja_kulttuuri":type :group :layout :vertical
                                                :body [{:name "kulttuurisesti_merkittava" :type :checkbox}
                                                       {:name "suojelukohteita" :type :checkbox}]}

                                               {:name "virkistys_tarpeet":type :group :layout :vertical
                                                :body [{:name "virkistysalueella" :type :checkbox}
                                                       {:name "vaikeuttaako_ulkoilureittia" :type :checkbox}
                                                       {:name "ulkoilu_ja_virkistysaluetta_varattu" :type :checkbox}]}

                                               {:name "muut_vaikutukset":type :group :layout :vertical
                                                :body [{:name "etaisyys_viemariverkosta"  :type :string :subtype :number :unit :m :size :s}
                                                       {:name "liitytaanko_viemariverkostoon" :type :checkbox}
                                                       {:name "pohjavesialuetta" :type :checkbox}]}

                                               {:name "merkittavyys":type :group
                                                :body [{:name "rakentamisen_vaikutusten_merkittavyys" :type :text :max-len 4000 :layout :full-width}]}]})

(def poikkeusasian-rakennuspaikka {:info {:name "poikkeusasian-rakennuspaikka"
                                          :i18name "rakennuspaikka"
                                          :approvable true
                                          :order 2
                                          :type :location}
                                   :body (tools/schema-body-without-element-by-name rakennuspaikka "hankkeestaIlmoitettu")})

(defschemas
  1
  [rakennushanke
   suunnittelutarveratkaisun-lisaosa
   poikkeusasian-rakennuspaikka])
