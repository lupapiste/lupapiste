(ns lupapalvelu.document.yleiset-alueet-schemas
  (:require [lupapalvelu.document.tools :refer :all]
            [lupapalvelu.document.schemas :refer :all]))

;;
;; Kayttolupa
;;

(def hankkeen-kuvaus-kayttolupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "varattava-pinta-ala" :type :string :subtype :number :unit :m2 :min-len 1 :max-len 5 :size :s}))

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kayttolupa"
           :type :group
           :removable false
           :repeating false
           :approvable true
           :order 60}
    :body hankkeen-kuvaus-kayttolupa}])


;;
;; Kaivulupa
;;

(def hankkeen-kuvaus-kaivulupa
  (body
    hankkeen-kuvaus-kayttolupa
    {:name "sijoitusLuvanTunniste" ; sijoituslupaviitetietoType
     :size :l
     :type :linkPermitSelector
     :operationsPath ["yleisten-alueiden-luvat" "sijoituslupa"]}))

(def tyomaasta-vastaava
  (-> ya-party
      (schema-body-without-element-by-name "turvakieltoKytkin")
      (schema-body-without-element-by-name "hetu")))

(def yleiset-alueet-maksaja
  (body
    (schema-body-without-element-by-name ya-maksaja "turvakieltoKytkin")))

(def tyo-aika
  (body
    {:name "tyoaika-alkaa-pvm" :type :date :required true} ;; alkuPvm / loppuPvm
    {:name "tyoaika-paattyy-pvm" :type :date :required true}))

(def tyo-aika-for-jatkoaika
  (body
    {:name "tyoaika-alkaa-pvm" :type :date :required true}  ;; alkuPvm / loppuPvm
    {:name "tyoaika-paattyy-pvm" :type :date :required true}))

(def hankkeen-kuvaus-jatkoaika
  (body
    {:name "kuvaus" :type :text :max-len 4000 :required true :layout :full-width}))

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
           :type :group
           :removable false
           :repeating false
           :approvable true
           :order 60}
    :body hankkeen-kuvaus-kaivulupa}
   {:info {:name "tyomaastaVastaava"                                       ;; vastuuhenkilotietoType
           :i18name "osapuoli"
           :type :party
           :removable false
           :repeating false
           :approvable true
           :accordion-fields hakija-accordion-paths
           :order 61}
    :body tyomaasta-vastaava}
   {:info {:name "yleiset-alueet-maksaja"                                  ;; maksajaTietoType
           :i18name "osapuoli"
           :type :party
           :removable false
           :repeating false
           :approvable true
           :order 62
           :accordion-fields hakija-accordion-paths
           :subtype :maksaja}
    :body yleiset-alueet-maksaja}
   {:info {:name "tyoaika"                                                 ;; alkuPvm / loppuPvm
           :type :group
           :removable false
           :repeating false
           :approvable true
           :order 63}
    :body tyo-aika}
   {:info {:name "tyo-aika-for-jatkoaika"                                  ;; (alkuPvm /) loppuPvm
           :type :group
           :removable false
           :repeating false
           :approvable true
           :order 63}
    :body tyo-aika-for-jatkoaika}
   {:info {:name "hankkeen-kuvaus-jatkoaika"
           :approvable true
           :order 1}
    :body hankkeen-kuvaus-jatkoaika}])


;;
;; Sijoituslupa
;;

(def tapahtuman-tiedot
  (body
    {:name "tapahtuman-nimi" :type :text :max-len 4000 :layout :full-width}  ;; lupakohtainenLisatietoType
    {:name "tapahtumapaikka" :type :string :size :l}                        ;; lupaAsianKuvaus
    {:name "tapahtuma-aika-alkaa-pvm" :type :date}                           ;; alkuPvm
    {:name "tapahtuma-aika-paattyy-pvm" :type :date}))                       ;; loppuPvm

#_(def tapahtumien-syotto                                                       ;; merkinnatJaPiirroksettietoType
  {:info {:name "tapahtumien-syotto"
          :order 68
          :removable true
          :repeating true}
   :body <kartalta valitut paikat>})                                            ;; sijainninSelitysteksti, sijaintitieto

(def mainostus-tapahtuma
  (body
    tapahtuman-tiedot
    [{:name "mainostus-alkaa-pvm" :type :date}                   ;; toimintajaksotietoType
     {:name "mainostus-paattyy-pvm" :type :date}]
    {:name "haetaan-kausilupaa" :type :checkbox}                 ;; lupakohtainenLisatietoType
    #_tapahtumien-syotto))

(def viitoitus-tapahtuma
  (body
    tapahtuman-tiedot
    #_tapahtumien-syotto))

(def mainostus-tai-viitoitus-tapahtuma-valinta
  (body
    [{:name "_selected" :type :radioGroup :default "mainostus-tapahtuma-valinta"
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
           :approvable true
           :repeating false
           :order 64}
    :body mainostus-tai-viitoitus-tapahtuma-valinta}])


(def hankkeen-kuvaus-sijoituslupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}))   ;; LupaAsianKuvaus

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
           :removable false
           :approvable true
           :repeating false
           :order 65}
    :body hankkeen-kuvaus-sijoituslupa}])



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
           :body [{:name "muu" :type :string :size :s}]}
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
