(ns lupapalvelu.document.yleiset-alueet-schemas
  (:use [lupapalvelu.document.schemas]))


(def hankkeen-kuvaus-kaivulupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "sijoitusLuvanTunniste" :type :string}))                             ;; sijoituslupaviitetietoType

(def hankkeen-kuvaus-sijoituslupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "kaivuLuvanTunniste" :type :string}))                                ;; sijoituslupaviitetietoType??  TODO: Mikä tähän?

(def yleiset-alueet-maksaja
  (body
    {:name "yritys" :type :group :body yritys-minimal}
    simple-osoite
    yhteystiedot
    {:name "laskuviite" :type :string :max-len 30 :layout :full-width}))

(def tyomaasta-vastaava
  (body
    party-public-area))

(def tyo-aika
  (body
    [{:name "tyoaika-alkaa-pvm" :type :date}                                   ;; toimintajaksotietoType
     {:name "tyoaika-paattyy-pvm" :type :date}]))

(def tapahtuman-tiedot
  (body
    {:name "tapahtuman-nimi" :type :text :max-len 4000 :layout :full-width}
    {:name "tapahtumapaikka" :type :string}
    [{:name "tapahtuma-aika-alkaa-pvm" :type :date}                            ;; kayttojaksotietoType
     {:name "tapahtuma-aika-paattyy-pvm" :type :date}]))

#_(def party [{:name "_selected" :type :radioGroup :body [{:name "henkilo"} {:name "yritys"}]}
            {:name "henkilo" :type :group :body henkilo}
            {:name "yritys" :type :group :body yritys}])

#_{:info {:name "hakija"
          :order 3
          :removable true
          :repeating true
          :type :party}
   :body party}

#_(def tapahtumien-syotto                                                      ;; merkinnatJaPiirroksettietoType
  {:info {:name "tapahtumien-syotto"
          :order 68
          :removable true
          :repeating true}
   :body <kartalta valitut paikat>})                                            ;; sijainninSelitysteksti, sijaintitieto

(def mainostus-tapahtuma
  (body
    tapahtuman-tiedot
    [{:name "mainostus-alkaa-pvm" :type :date}                                 ;; toimintajaksotietoType
     {:name "mainostus-paattyy-pvm" :type :date}]
    {:name "haetaan-kausilupaa" :type :checkbox}                               ;; lupakohtainenLisatietoType ?
    #_tapahtumien-syotto))

(def viitoitus-tapahtuma
  (body
    tapahtuman-tiedot
    #_tapahtumien-syotto))

(def mainostus-tai-viitoitus-tapahtuma-valinta
  (body
    [{:name "_selected" :type :radioGroup
      :body [{:name "mainostus-tapahtuma-valinta"} {:name "viitoitus-tapahtuma-valinta"}]}
     {:name "mainostus-tapahtuma-valinta" :type :group
      :body mainostus-tapahtuma}
     {:name "viitoitus-tapahtuma-valinta" :type :group
      :body viitoitus-tapahtuma}]))


(def kaivuulupa
  (to-map-by-name
    [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
             :order 60}
      :body hankkeen-kuvaus-kaivulupa}
     {:info {:name "tyomaastaVastaava"                                       ;; vastuuhenkilotietoType
             :type :party
             :order 61}
      :body tyomaasta-vastaava}
     {:info {:name "yleiset-alueet-maksaja"                                  ;; maksajaTietoType
             :type :party
             :order 62}
      :body yleiset-alueet-maksaja}
     {:info {:name "tyoaika"                                                 ;; kayttojaksotietoType ja toimintajaksotietoType (kts. ylla)
             :type :group
             :order 63}
            :body tyo-aika}]))

(def kayttolupa-mainoslaitteet-ja-opasteviitat
  (to-map-by-name
    [{:info {:name "mainosten-tai-viitoitusten-sijoittaminen"
            :type :group
;            :removable false  ;; TODO: Miten voi poistaa raksin?
            :order 64}
     :body mainostus-tai-viitoitus-tapahtuma-valinta}]))

(def sijoituslupa
  (to-map-by-name
    [{:info {:name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
             :order 65}
      :body hankkeen-kuvaus-sijoituslupa}]))

;;
;; TODO: Liikennetta haittavan tyon lupa
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





