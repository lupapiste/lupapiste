(ns lupapalvelu.document.yleiset-alueet-schemas
  (:require [lupapalvelu.document.tools :refer :all]
            [lupapalvelu.document.schemas :refer :all]))

;;
;; Kaivulupa
;;

(def sijoituksen-tarkoitus-dropdown
  [{:name "sijoituksen-tarkoitus" :type :select :other-key "muu-sijoituksen-tarkoitus"
   :body [{:name "jakokaappi-(tele/sahko)"}
          {:name "jate--tai-sadevesi"}
          {:name "kaivo-(kaukolampo)"}
          {:name "kaivo-(tele/sahko)"}
          {:name "kaivo-(vesi,-jate--tai-sadevesi)"}
          {:name "katuvalo"}
          {:name "kaukolampo"}
          {:name "liikennevalo"}
          {:name "sahko"}
          {:name "tele"}
          {:name "vesijohto"}]}
   {:name "muu-sijoituksen-tarkoitus" :type :string :size "l"}])

(def hankkeen-kuvaus-kaivulupa
  (body
    sijoituksen-tarkoitus-dropdown
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "sijoitusLuvanTunniste" :type :string :size "l"}                     ;; sijoituslupaviitetietoType
    {:name "varattava-pinta-ala" :type :string :subtype :number :min-len 1 :max-len 3 :size "s"}))

(def tyomaasta-vastaava
  (schema-body-without-element-by-name
    (schema-body-without-element-by-name party "turvakieltoKytkin")
    "hetu"))

(def yleiset-alueet-maksaja
  (body
    (schema-body-without-element-by-name party "turvakieltoKytkin")
    {:name "laskuviite" :type :string :max-len 30 :layout :full-width}))

(def tyo-aika
  (body
    {:name "tyoaika-alkaa-pvm" :type :date}                                     ;; alkuPvm / loppuPvm
    {:name "tyoaika-paattyy-pvm" :type :date}))


(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
           :type :group
           :removable false
           :repeating false
           :order 60}
    :body hankkeen-kuvaus-kaivulupa}
   {:info {:name "tyomaastaVastaava"                                       ;; vastuuhenkilotietoType
           :type :party
           :removable false
           :repeating false
           :order 61}
    :body tyomaasta-vastaava}
   {:info {:name "yleiset-alueet-maksaja"                                  ;; maksajaTietoType
           :type :party
           :removable false
           :repeating false
           :order 62}
    :body yleiset-alueet-maksaja}
   {:info {:name "tyoaika"                                                 ;; kayttojaksotietoType ja toimintajaksotietoType (kts. ylla)
           :type :group
           :removable false
           :repeating false
           :order 63}
    :body tyo-aika}])


;;
;; Kayttolupa
;;

(def hankkeen-kuvaus-kayttolupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "sijoitusLuvanTunniste" :type :string :size "l"}))                   ;; sijoituslupaviitetietoType

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kayttolupa"
           :type :group
           :removable false
           :repeating false
           :order 60}
    :body hankkeen-kuvaus-kayttolupa}])


;;
;; Sijoituslupa
;;

(def tapahtuman-tiedot
  (body
    {:name "tapahtuman-nimi" :type :text :max-len 4000 :layout :full-width}     ;; lupakohtainenLisatietoType
    {:name "tapahtumapaikka" :type :string :size "l"}                           ;; lupaAsianKuvaus
    {:name "tapahtuma-aika-alkaa-pvm" :type :date}                              ;; alkuPvm
    {:name "tapahtuma-aika-paattyy-pvm" :type :date}))                          ;; loppuPvm

#_(def tapahtumien-syotto                                                       ;; merkinnatJaPiirroksettietoType
  {:info {:name "tapahtumien-syotto"
          :order 68
          :removable true
          :repeating true}
   :body <kartalta valitut paikat>})                                            ;; sijainninSelitysteksti, sijaintitieto

(def mainostus-tapahtuma
  (body
    tapahtuman-tiedot
    [{:name "mainostus-alkaa-pvm" :type :date}                                  ;; toimintajaksotietoType
     {:name "mainostus-paattyy-pvm" :type :date}]
    {:name "haetaan-kausilupaa" :type :checkbox}                                ;; lupakohtainenLisatietoType
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

(defschemas
  1
  [{:info {:name "mainosten-tai-viitoitusten-sijoittaminen"
           :type :group
           :removable false
           :repeating false
           :order 64}
    :body mainostus-tai-viitoitus-tapahtuma-valinta}])


(def hankkeen-kuvaus-sijoituslupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "kaivuLuvanTunniste" :type :string :size "l"}))                      ;; sijoituslupaviitetietoType??  TODO: Mika tahan?

(def sijoituslupa-sijoituksen-tarkoitus
  (body
    sijoituksen-tarkoitus-dropdown                                                          ;; lupakohtainenLisatietotieto
    {:name "lisatietoja-sijoituskohteesta" :type :text :max-len 4000 :layout :full-width})) ;; lupakohtainenLisatietotieto

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
           :removable false
           :repeating false
           :order 65}
    :body hankkeen-kuvaus-sijoituslupa}
   {:info {:name "sijoituslupa-sijoituksen-tarkoitus"
           :removable false
           :repeating false
           :order 66}
    :body sijoituslupa-sijoituksen-tarkoitus}])



;;
;; Liikennetta haittavan tyon lupa
;;

#_(def liikennetta-haittaavan-tyon-lupa
  {:info {:name "yleisetAlueetLiikennettaHaittaava" :order 67}
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





