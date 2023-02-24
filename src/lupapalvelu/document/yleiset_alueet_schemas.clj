(ns lupapalvelu.document.yleiset-alueet-schemas
  (:require [lupapalvelu.document.tools :refer :all]
            [lupapalvelu.document.schemas :refer :all]
            [lupapalvelu.document.validator :as validator]))

;;
;; Kayttolupa
;;

(def hankkeen-kuvaus-kayttolupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width :required true}     ;; LupaAsianKuvaus
    {:name "varattava-pinta-ala" :type :string :subtype :number :unit :m2 :min-len 1 :max-len 5 :size :s :required true}))

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kayttolupa"
           :type :group
           :subtype :hankkeen-kuvaus
           :removable-by :none
           :repeating false
           :approvable true
           :order 60
           :after-update 'lupapalvelu.application-meta-fields/update-project-description-index}
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
  (-> ya-party-tyomaasta-vastaava
      (schema-body-without-element-by-name "turvakieltoKytkin")
      (schema-body-without-element-by-name "hetu")
      (schema-body-without-element-by-name "ulkomainenHenkilotunnus")))

(def yleiset-alueet-maksaja
  (body
    (schema-body-without-element-by-name ya-maksaja "turvakieltoKytkin")))

(def tyo-aika
  (body
    {:name "tyoaika-alkaa-pvm" :type :date :hidden true} ; alkuPvm / loppuPvm
    {:name "tyoaika-paattyy-pvm" :type :date :hidden true}
    {:name "tyoaika-alkaa-ms" :type :msDate  :required true}
    {:name "tyoaika-paattyy-ms" :type :msDate :required true :hide-when {:path "voimassa-toistaiseksi" :values #{true}}}
    {:name "voimassa-toistaiseksi" :type :checkbox})) ; loppuPvm -> "2999-01-01"

(validator/defvalidator :tyoaika-timestamps
  {:doc     "Tyoaika ei voi alkaa ennen sen loppua"
   :schemas ["tyoaika"]
   :fields  [alku [:tyoaika-alkaa-ms]
             loppu [:tyoaika-paattyy-ms]]
   :facts   {:ok   [[1 2]
                    [2 2]]
             :fail [[2 1]]}}

  (when (every? number? [alku loppu])
    (< loppu alku)))

(def tyo-aika-for-jatkoaika
  (body
    {:name "tyoaika-alkaa-pvm" :type :date :required true}  ;; alkuPvm / loppuPvm
    {:name "tyoaika-paattyy-pvm" :type :date :required true}))

(def hankkeen-kuvaus-jatkoaika
  (body
    {:name "kuvaus" :type :text :max-len 4000 :required true :layout :full-width}))

(def work-period-fields [{:type :workPeriod
                          :paths [["tyoaika-alkaa-ms"]
                                  ["tyoaika-paattyy-ms"]]
                          :format "%s \u2013 %s"}])
(def continuation-fields [{:type :date
                           :paths [["tyoaika-alkaa-pvm"]
                                   ["tyoaika-paattyy-pvm"]]
                           :format "%s \u2013 %s"}])

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
           :type :group
           :subtype :hankkeen-kuvaus
           :removable-by :none
           :repeating false
           :approvable true
           :order 60
           :after-update 'lupapalvelu.application-meta-fields/update-project-description-index}
    :body hankkeen-kuvaus-kaivulupa}
   {:info {:name "tyomaastaVastaava"                                       ;; vastuuhenkilotietoType
           :i18name "osapuoli"
           :type :party
           :subtype :tyomaasta-vastaava
           :removable-by :none
           :repeating false
           :approvable true
           :accordion-fields hakija-accordion-paths
           :order 61}
    :body tyomaasta-vastaava}
   {:info {:name "tyomaasta-vastaava-optional"                             ;; vastuuhenkilotietoType
           :i18name "osapuoli"
           :type :party
           :subtype :tyomaasta-vastaava
           :removable-by :all
           :repeating false
           :approvable true
           :accordion-fields hakija-accordion-paths
           :order 70}
    :body tyomaasta-vastaava}
   {:info {:name "yleiset-alueet-maksaja"                                  ;; maksajaTietoType
           :i18name "osapuoli"
           :type :party
           :removable-by :none
           :repeating false
           :approvable true
           :order 62
           :accordion-fields hakija-accordion-paths
           :subtype :maksaja}
    :body yleiset-alueet-maksaja}
   {:info {:name "tyoaika"                                                 ;; alkuPvm / loppuPvm
           :type :group
           :removable-by :none
           :repeating false
           :approvable true
           :order 63
           :accordion-fields work-period-fields}
    :body tyo-aika}
   {:info {:name "tyo-aika-for-jatkoaika"                                  ;; (alkuPvm /) loppuPvm
           :type :group
           :removable-by :none
           :repeating false
           :approvable true
           :order 63
           :accordion-fields continuation-fields}
    :body tyo-aika-for-jatkoaika}
   {:info {:name "hankkeen-kuvaus-jatkoaika"
           :subtype :hankkeen-kuvaus
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
          :removable-by :all
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
           :removable-by :none
           :approvable true
           :repeating false
           :order 64}
    :body mainostus-tai-viitoitus-tapahtuma-valinta}])


(def hankkeen-kuvaus-sijoituslupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width :required true}))   ;; LupaAsianKuvaus

(defschemas
  1
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
           :subtype :hankkeen-kuvaus
           :removable-by :none
           :approvable true
           :repeating false
           :order 65
           :after-update 'lupapalvelu.application-meta-fields/update-project-description-index}
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
