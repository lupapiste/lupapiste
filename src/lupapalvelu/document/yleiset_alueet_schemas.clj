(ns lupapalvelu.document.yleiset-alueet-schemas
  (:require [lupapalvelu.document.tools :refer :all])
  (:use [lupapalvelu.document.schemas]))


(def hankkeen-kuvaus-kaivulupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "sijoitusLuvanTunniste" :type :string :size "l"}))                   ;; sijoituslupaviitetietoType

(def hankkeen-kuvaus-sijoituslupa
  (body
    {:name "kayttotarkoitus" :type :text :max-len 4000 :layout :full-width}     ;; LupaAsianKuvaus
    {:name "kaivuLuvanTunniste" :type :string :size "l"}))                      ;; sijoituslupaviitetietoType??  TODO: Mika tahan?

(def yleiset-alueet-maksaja
  (body
    (schema-body-without-element-by-name party "turvakieltoKytkin")
    {:name "laskuviite" :type :string :max-len 30 :layout :full-width}))

(def tyo-aika
  (body
    {:name "tyoaika-alkaa-pvm" :type :date}                                   ;; toimintajaksotietoType
    {:name "tyoaika-paattyy-pvm" :type :date}))

(def tapahtuman-tiedot
  (body
    {:name "tapahtuman-nimi" :type :text :max-len 4000 :layout :full-width}
    {:name "tapahtumapaikka" :type :string :size "l"}
    {:name "tapahtuma-aika-alkaa-pvm" :type :date}                            ;; kayttojaksotietoType
    {:name "tapahtuma-aika-paattyy-pvm" :type :date}))

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

(def sijoituslupa-sijoituksen-tarkoitus
  (body
    [{:name "sijoituksen-tarkoitus" :type :select
      :body [{:name "sahko"}
             {:name "tele"}
             {:name "kaivo-(tele/sahko)"}
             {:name "jakokaappi-(tele/sahko)"}
             {:name "kaukolampo"}
             {:name "kaivo-(kaukolampo)"}
             {:name "liikennevalo"}
             {:name "katuvalo"}
             {:name "jate--tai-sadevesi"}
             {:name "kaivo-(vesi,-jate--tai-sadevesi)"}
             {:name "vesijohto"}
             {:name "muu"}]}
     {:name "muu-sijoituksen-tarkoitus" :type :string :size "l" :layout :full-width} ;; TODO: Saako taman enabloiduksi vain jos edellisesta dropdownista on valittu "Muu"?
     {:name "lisatietoja-sijoituskohteesta" :type :text :max-len 4000 :layout :full-width}]))


(defschemas
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-kaivulupa"
           :order 60}
    :body hankkeen-kuvaus-kaivulupa}
   {:info {:name "tyomaastaVastaava"                                       ;; vastuuhenkilotietoType
           :type :party
           :order 61}
    :body (schema-body-without-element-by-name party "turvakieltoKytkin")}
   {:info {:name "yleiset-alueet-maksaja"                                  ;; maksajaTietoType
           :type :party
           :order 62}
    :body yleiset-alueet-maksaja}
   {:info {:name "tyoaika"                                                 ;; kayttojaksotietoType ja toimintajaksotietoType (kts. ylla)
           :type :group
           :order 63}
    :body tyo-aika}])

(defschemas
  [{:info {:name "mainosten-tai-viitoitusten-sijoittaminen"
           :type :group
           ;            :removable false  ;; TODO: Miten voi poistaa raksin?
           :order 64}
    :body mainostus-tai-viitoitus-tapahtuma-valinta}])

(defschemas
  [{:info {:name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
           :order 65}
    :body hankkeen-kuvaus-sijoituslupa}
   {:info {:name "sijoituslupa-sijoituksen-tarkoitus"
           :order 66}
    :body sijoituslupa-sijoituksen-tarkoitus}])

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





