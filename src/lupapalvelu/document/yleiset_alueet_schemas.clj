(ns lupapalvelu.document.yleiset-alueet-schemas
  (:use [lupapalvelu.document.schemas]))


(def yleiset-alueet-maksaja (body
                              yritys-minimal
                              simple-osoite
                              {:name "laskuviite" :type :string :subtype :number :max-len 30 :layout :full-width}))  ;;TODO: Mikä :max-len tälle kentälle?

(def hankeesta-vastaava (body
                          {:name "userId" :type :personSelector} ;henkilo-valitsin
                          designer-basic
                          [{:name "patevyys" :type :group
                            :body [{:name "ammattipatevyys" :type :text :max-len 4000 :layout :full-width}
                                   {:name "voimassa-pvm" :type :date}]}]))

(def tyomaasta-vastaava (body
                          {:name "userId" :type :personSelector} ;henkilo-valitsin
                          designer-basic
                          [{:name "patevyys" :type :group
                            :body [{:name "ammattipatevyys" :type :text :max-len 4000 :layout :full-width}
                                   {:name "voimassa-pvm" :type :date}]}]))

(def kohteen-tiedot (body
                      [{:name "kaupunginosa" :type :string}
                       {:name "kortteli" :type :string}]
                      simple-osoite))

(def vuokra-ja-tyo-aika (body
                          [{:name "vuokra-aika-alkaa-pvm" :type :date}
                           {:name "vuokra-aika-paattyy-pvm" :type :date}]
                          [{:name "tyoaika-alkaa-pvm" :type :date}
                           {:name "tyoaika-paattyy-pvm" :type :date}]))

(def yleiset-alueet-kaivuulupa
  (to-map-by-name
    [{:info {:name "yleiset-alueet-hankkeen-kuvaus"
             :order 60}
      :body [{:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}
             {:name "luvanTunniste" :type :string}]}
     {:info {:name "tyomaastaVastaava"
             :type :party
             :order 61}
      :body tyomaasta-vastaava}
     {:info {:name "yleiset-alueet-maksaja"
             :type :party
             :order 62}
      :body yleiset-alueet-maksaja}
     {:info {:name "kohteenTiedot"
             :type :group
             :order 63}
      :body kohteen-tiedot}
     {:info {:name "tyo-/vuokra-aika"
             :type :group
             :order 64}
      :body vuokra-ja-tyo-aika}]))

;;
;; TODO: Liikennettä haittavan työn lupa
;;
#_(def liikennetta-haittaavan-tyon-lupa
  {:info {:name "yleisetAlueetLiikennettaHaittaava" :order 65}
   :body [{:name "ilmoituksenAihe"
           :type :group
           :body [{:name "ensimmainenIlmoitusTyosta" :type :checkbox}
                  {:name "ilmoitusTyonPaattymisesta" :type :checkbox}
                  {:name "korjaus/muutosAiempaanIlmoitukseen" :type :checkbox}
                  {:name "Muu" :type :checkbox}]}
          {:name "kohteenTiedot"
           :type :group
           :body []}
          {:name "tyonTyyppi"
           :type :group
           :body [{:name "muu" :type :string :size "s"}]}
          {:name "tyoaika"
           :type :group
           :body [#_{:name "alkaa-pvm" :type :date}
                  #_{:name "paattyy-pvm" :type :date}]}
          {:name "vaikutuksetLiikenteelle"
           :type :group
           :body [{:name "kaistajarjestelyt"
                   :type :group
                   :body [{:name "ajokaistaKavennettu" :type :checkbox}
                          {:name "ajokaistaSuljettu" :type :checkbox}
                          {:name "korjaus/muutosAiempaanIlmoitukseen" :type :checkbox}
                          {:name "Muu" :type :checkbox}]}
                  {:name "pysaytyksia"
                   :type :group
                   :body [{:name "tyonAikaisetLiikennevalot" :type :checkbox}
                          {:name "liikenteenOhjaaja" :type :checkbox}]}
                  {:name "tienPintaTyomaalla"
                   :type :group
                   :body [{:name "paallystetty" :type :checkbox}
                          {:name "jyrsitty" :type :checkbox}
                          {:name "murske" :type :checkbox}]}
                  {:name "rajoituksia"
                   :type :group
                   :body [{:name "poikkeavaNopeusRajoitus" :type :checkbox}
                          {:name "kiertotie" :type :checkbox}
                          {:name "painorajoitus" :type :checkbox}
                          {:name "ulottumarajoituksia" :type :checkbox}
                          {:name "tyokoneitaLiikenteenSeassa" :type :checkbox}]}]}]})





