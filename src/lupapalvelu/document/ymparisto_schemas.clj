(ns lupapalvelu.document.ymparisto-schemas
  (:require [lupapalvelu.document.schemas :refer :all]
            [lupapalvelu.document.tools :refer :all]))

#_(def sijainti (body simple-osoite
                 {:name "karttapiirto" :type :text :max-len 4000}))

(def kesto (body {:name "kesto" :type :group
                  :body [{:name "alku" :type :date}
                         {:name "loppu" :type :date}
                         {:name "arki", :type :group, :body [{:name "arkiAlkuAika" :type :time} {:name "arkiLoppuAika" :type :time}]}
                         {:name "lauantai", :type :group, :body [{:name "lauantaiAlkuAika" :type :time} {:name "lauantaiLoppuAika" :type :time}]}
                         {:name "sunnuntai", :type :group, :body [{:name "sunnuntaiAlkuAika" :type :time} {:name "sunnuntaiLoppuAika" :type :time}]}]}))


(def kesto-mini
  (schema-body-without-element-by-name kesto "arki" "lauantai" "sunnuntai"))

(def meluilmoitus (body
                    {:name "rakentaminen" :type :group
                     :body [{:name "melua-aihettava-toiminta" :type :select :sortBy :displayname
                             :body [{:name "louhinta"}
                                    {:name "murskaus"}
                                    {:name "paalutus"}]}
                            {:name "muu-rakentaminen" :type :string :size "m"}
                            {:name "kuvaus" :type :text :max-len 4000}
                            {:name "koneet" :type :text :max-len 4000}]}

                    {:name "tapahtuma" :type :group
                     :body [{:name "nimi" :type :string :size "m"}
                            {:name "ulkoilmakonsertti" :type :checkbox}
                            {:name "kuvaus" :type :text}] }
                    {:name "melu" :type :group
                     :body [{:name "melu10mdBa" :type :string :size "s"}
                            {:name "paivalla" :type :string :size "s"}
                            {:name "yolla" :type :string :size "s"}
                            {:name "mittaus" :type :string :size "m"}]}))

(def pima (body {:name "kuvaus" :type :text :max-len 4000}))

(def ottamismaara (body
                    {:name "kokonaismaara" :type :string :unit "m3" :size "m"}
                    {:name "vuotuinenOtto" :type :string :unit "m3" :size "m"}
                    {:name "ottamisaika" :type :string :unit "y" :size "m"}))

(def ottamis-suunnitelma (body
                           {:name "selvitykset" :type :group
                            :body [{:name "toimenpiteet" :type :text :max-len 4000}
                                   {:name "tutkimukset" :type :text :max-len 4000}
                                   {:name "ainesLaatu" :type :text :max-len 4000}
                                   {:name "ainesMaara" :type :text :max-len 4000}]}
                           {:name "Luonnonolot" :type :group
                            :body [{:name "maisemakuva" :type :text :max-len 4000}
                                   {:name "kasvillisuusJaElaimmisto" :type :text :max-len 4000}
                                   {:name "kaavoitustilanne" :type :text :max-len 4000}]}
                           {:name "pohjavesiolot" :type :group
                            :body [{:name "luokitus" :type :text :max-len 4000}
                                   {:name "suojavyohykkeet" :type :text :max-len 4000}]}
                           {:name "vedenottamot" :type :text :max-len 4000}
                           {:name "vakuus" :type :select :sortBy :displayname
                            :body [{:name "EiAloiteta"}
                                   {:name "Rahaa"}
                                   {:name "Pankkitakaus"}]}))

(def luonnonmuistomerkin-rauhoittaminen
  (body
    {:name "muistomerkki-perustelut-rauhoitukselle" :type :group
     :body [{:name "kohteen-nimi" :type :string :size "l" :required true :group-help "muistomerkki-perustelut-rauhoitukselle.help"}
            kuvaus
            {:name "muita-tietoja" :type :text :max-len 4000 :required false :layout :full-width}]}

   {:name "muistomerkki-kaytto-ja-hoito" :type :group
    :body [{:name "nahtavyyskohde" :type :checkbox :required true :layout :full-width
            :group-help "muistomerkki-kaytto-ja-hoito.help"}]}))


(defschemas
  1
  [{:info {:name "meluilmoitus"
           :order 50}
    :body meluilmoitus}
   {:info {:name "pima"
           :order 51}
    :body pima}
   {:info {:name "ymp-ilm-kesto-mini"
           :order 60}
    :body kesto-mini}
   {:info {:name "ymp-ilm-kesto"
           :order 60}
    :body kesto}
   {:info {:name "ottamismaara"
           :order 50}
    :body ottamismaara}
   {:info {:name "ottamis-suunnitelma"
           :order 51}
    :body ottamis-suunnitelma}
   {:info {:name "maa-ainesluvan-omistaja"
           :i18name "osapuoli"
           :order 3
           :type :party}
    :body party}
   {:info {:name "ottamis-suunnitelman-laatija"
           :i18name "osapuoli"
           :order 4
           :type :party}
    :body party}
   {:info {:name "ymp-maksaja"
           :i18name "osapuoli"
           :repeating false
           :order 6
           :removable false
           :approvable true
           :subtype :maksaja
           :type :party}
     :body maksaja}
   {:info {:name "yl-hankkeen-kuvaus"
           :order 1}
    :body [kuvaus
           {:name "peruste" :type :text :max-len 4000 :required false :layout :full-width}]}
   {:info {:name "maa-aineslupa-kuvaus"
           :order 1}
    :body [kuvaus]}
   {:info {:name "luonnonmuistomerkin-rauhoittaminen"
           :order 1}
    :body luonnonmuistomerkin-rauhoittaminen}
   {:info {:name "paatoksen-toimitus"
           :order 9999}
    :body [{:name "paatoksenToimittaminen" :type :select :sortBy :displayname
            :body [{:name "Noudetaan"}
                   {:name "Postitetaan"}]}]}])
