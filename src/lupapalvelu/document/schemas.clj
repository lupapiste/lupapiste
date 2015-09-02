(ns lupapalvelu.document.schemas
  (:require [clojure.set :as set]
            [lupapalvelu.document.tools :refer :all]
            [lupapiste-commons.usage-types :as usages]))

;;
;; Register schemas
;;

(defonce ^:private registered-schemas (atom {}))

(defn get-all-schemas [] @registered-schemas)
(defn get-schemas [version] (get @registered-schemas version))

(def info-keys #{:name :type :subtype :version
                 :i18name :i18nprefix
                 :approvable :removable :deny-removing-last-document
                 :group-help :section-help
                 :after-update
                 :repeating :order})

(def updateable-keys #{:removable})
(def immutable-keys (set/difference info-keys updateable-keys) )

(defn defschema [version data]
  (let [schema-name (name (get-in data [:info :name]))]
    (assert (every? info-keys (keys (:info data))))
    (swap! registered-schemas
      assoc-in
      [version schema-name]
      (-> data
        (assoc-in [:info :name] schema-name)
        (assoc-in [:info :version] version)))))

(defn defschemas [version schemas]
  (doseq [schema schemas]
    (defschema version schema)))

(defn get-schema
  ([{:keys [version name] :or {version 1}}] (get-schema version name))
  ([schema-version schema-name]
    {:pre [schema-version schema-name]}
    (get-in @registered-schemas [schema-version (name schema-name)])))



(defn get-latest-schema-version []
  (->> @registered-schemas keys (sort >) first))

;;
;; helpers
;;

(defn body
  "Shallow merges stuff into vector"
  [& rest]
  (reduce
    (fn [a x]
      (let [v (if (sequential? x)
                x
                (vector x))]
        (concat a v)))
    [] rest))

(defn repeatable
  "Created repeatable element."
  [name childs]
  [{:name      name
    :type      :group
    :repeating true
    :body      (body childs)}])

;;
;; schema sniplets
;;

(def select-one-of-key "_selected")

(def turvakielto "turvakieltoKytkin")

(def kuvaus {:name "kuvaus" :type :text :max-len 4000 :required true :layout :full-width})

(def henkilo-valitsin [{:name "userId" :type :personSelector :blacklist [:neighbor]}])

(def yritys-valitsin [{:name "companyId" :type :companySelector :blacklist [:neighbor]}])

(def rakennuksen-valitsin [{:name "buildingId" :type :buildingSelector :required true :i18nkey "rakennusnro" :other-key "manuaalinen_rakennusnro"}
                           {:name "rakennusnro" :type :string :subtype :rakennusnumero :hidden true}
                           {:name "manuaalinen_rakennusnro" :type :string :subtype :rakennusnumero :i18nkey "manuaalinen_rakennusnro" :labelclass "really-long"}
                           {:name "valtakunnallinenNumero" :type :string  :subtype :rakennustunnus :hidden true}
                           {:name "kunnanSisainenPysyvaRakennusnumero" :type :string :hidden true}])

(def uusi-rakennuksen-valitsin [{:name "jarjestysnumero" :type :newBuildingSelector :i18nkey "rakennusnro" :required true}
                                {:name "valtakunnallinenNumero" :type :string  :subtype :rakennustunnus :hidden true}
                                {:name "rakennusnro" :type :string :subtype :rakennusnumero :hidden true}
                                {:name "kiinttun" :type :string :subtype :kiinteistotunnus :hidden true}
                                {:name "kunnanSisainenPysyvaRakennusnumero" :type :string :hidden true}])

(def simple-osoite [{:name "osoite"
                     :type :group
                     :blacklist [turvakielto]
                     :body [{:name "katu" :type :string :subtype :vrk-address :required true}
                            {:name "postinumero" :type :string :subtype :zip :size "s" :required true}
                            {:name "postitoimipaikannimi" :type :string :subtype :vrk-address :size "m" :required true}]}])

(def simple-osoite-maksaja [{:name "osoite"
                             :i18nkey "osoite-maksaja"
                             :type :group
                             :blacklist [turvakielto]
                             :body [{:name "katu" :type :string :subtype :vrk-address :required true}
                                    {:name "postinumero" :type :string :subtype :zip :size "s" :required true}
                                    {:name "postitoimipaikannimi" :type :string :subtype :vrk-address :size "m" :required true}]}])

(def rakennuksen-osoite [{:name "osoite"
                   :type :group
                   :body [{:name "kunta" :type :string}
                          {:name "lahiosoite" :type :string}
                          {:name "osoitenumero" :type :string :subtype :number :min 0 :max 9999}
                          {:name "osoitenumero2" :type :string}
                          {:name "jakokirjain" :type :string :subtype :letter :case :lower :max-len 1 :size "s" :hidden true :readonly true}
                          {:name "jakokirjain2" :type :string :size "s" :hidden true :readonly true}
                          {:name "porras" :type :string :subtype :letter :case :upper :max-len 1 :size "s" :hidden true :readonly true}
                          {:name "huoneisto" :type :string :size "s" :hidden true :readonly true}
                          {:name "postinumero" :type :string :subtype :zip :size "s"}
                          {:name "postitoimipaikannimi" :type :string :size "m"}]}])

(def yhteystiedot [{:name "yhteystiedot"
                    :type :group
                    :blacklist [:neighbor turvakielto]
                    :body [{:name "puhelin" :type :string :subtype :tel :required true}
                           {:name "email" :type :string :subtype :email :required true}]}])

(def henkilotiedot-minimal {:name "henkilotiedot"
                            :type :group
                            :body [{:name "etunimi" :type :string :subtype :vrk-name :required true}
                                   {:name "sukunimi" :type :string :subtype :vrk-name :required true}
                                   {:name turvakielto :type :checkbox :blacklist [turvakielto]}]})

(def henkilotiedot {:name "henkilotiedot"
                            :type :group
                            :body [{:name "etunimi" :type :string :subtype :vrk-name :required true}
                                   {:name "sukunimi" :type :string :subtype :vrk-name :required true}
                                   {:name "hetu" :type :hetu :max-len 11 :required true :blacklist [:neighbor turvakielto] :emit [:hetuChanged]}
                                   {:name turvakielto :type :checkbox :blacklist [turvakielto]}]})

(def henkilo (body
               henkilo-valitsin
               [henkilotiedot]
               simple-osoite
               yhteystiedot))

(def henkilo-maksaja (body
                       henkilo-valitsin
                       [henkilotiedot]
                       simple-osoite-maksaja
                       yhteystiedot))

(def henkilo-with-required-hetu (body
                                  henkilo-valitsin
                                  [(assoc henkilotiedot
                                     :body
                                     (map (fn [ht] (if (= (:name ht) "hetu") (merge ht {:required true}) ht))
                                       (:body henkilotiedot)))]
                                  simple-osoite
                                  yhteystiedot))

(def yritys-minimal [{:name "yritysnimi" :type :string :required true :size "l"}
                     {:name "liikeJaYhteisoTunnus" :type :string :subtype :y-tunnus :required true}])

(def yritys (body
              yritys-valitsin
              yritys-minimal
              simple-osoite
              {:name "yhteyshenkilo"
               :type :group
               :body (body
                       [henkilotiedot-minimal]
                       yhteystiedot)}))

(def yritys-maksaja (body
                      yritys-valitsin
                      yritys-minimal
                      simple-osoite-maksaja
                      {:name "yhteyshenkilo"
                       :type :group
                       :body (body
                               [henkilotiedot-minimal]
                               yhteystiedot)}))

(def e-invoice-operators
  [{:name "BAWCFI22"} ; Basware Oyj
   {:name "003714377140"} ; Enfo Zender Oy
   {:name "003708599126"} ; Liaison Technologies Oy
   {:name "HELSFIHH"} ; Aktia S\u00e4\u00e4st\u00f6pankki Oyj
   {:name "POPFFI22"} ; Paikallisosuuspankit
   {:name "HANDFIHH"} ; Handelsbanken
   {:name "003721291126"} ; Maventa
   {:name "003723327487"} ; Apix Messaging Oy
   {:name "003717203971"} ; Notebeat Oy
   {:name "003723609900"} ; (tai PAGERO) Pagero
   {:name "003701150617"} ; Str\u00e5lfors Oy
   {:name "FIYAPSOL"} ; YAP Solutions Oy
   {:name "00885060259470028"} ; Tradeshift
   {:name "TAPIFI22"} ; S-Pankki Oy (vanha, ent L\u00e4hiTapiola)
   {:name "INEXCHANGE"} ; InExchange Factorum AB
   {:name "DNBAFIHX"} ; DNB Bank ASA
   {:name "ITELFIHH"} ; S\u00e4\u00e4st\u00f6pankit
   {:name "003710948874"} ; OpusCapita Group Oy
   {:name "00885790000000418"} ; HighJump AS
   {:name "NDEAFIHH"} ; Nordea
   {:name "OKOYFIHH"} ; OP-Pohjola-ryhm\u00e4
   {:name "003701011385"} ; Tieto Oyj
   {:name "DABAFIHH"} ; Danske Bank Oyj
   {:name "003703575029"} ; CGI / TeliaSonera Finland Oyj
   {:name "AABAFI22"} ; \u00c5landsbanken Abp
   {:name "SBANFIHH"} ; S-Pankki Oy (uusi)
   ])

(def verkkolaskutustieto [{:name "ovtTunnus" :type :string :subtype :ovt :min-len 12 :max-len 17}
                          {:name "verkkolaskuTunnus" :type :string}
                          {:name "valittajaTunnus"
                           :type :select
                           :i18nkey "osapuoli.yritys.verkkolaskutustieto.valittajaTunnus"
                           :size "l"
                           :body e-invoice-operators}])

(def yritys-with-verkkolaskutustieto (body
                                       yritys-maksaja
                                       {:name "verkkolaskutustieto"
                                        :type :group
                                        :body (body
                                                verkkolaskutustieto)}))

(defn- henkilo-yritys-select-group
  [& {:keys [default henkilo-body yritys-body] :or {default "henkilo" henkilo-body henkilo yritys-body yritys}}]
  (body
    {:name select-one-of-key :type :radioGroup :body [{:name "henkilo"} {:name "yritys"}] :default default}
    {:name "henkilo" :type :group :body henkilo-body}
    {:name "yritys" :type :group :body yritys-body}))

(def party (henkilo-yritys-select-group))
(def ya-party (henkilo-yritys-select-group :default "yritys"))
(def party-with-required-hetu (henkilo-yritys-select-group :henkilo-body henkilo-with-required-hetu))

(def koulutusvalinta {:name "koulutusvalinta" :type :select :sortBy :displayname :i18nkey "koulutus" :other-key "koulutus" :required true
                      :body [{:name "arkkitehti"}
                             {:name "arkkitehtiylioppilas"}
                             {:name "diplomi-insin\u00f6\u00f6ri"}
                             {:name "insin\u00f6\u00f6ri"}
                             {:name "IV-asentaja"}
                             {:name "kirvesmies"}
                             {:name "LV-asentaja"}
                             {:name "LVI-asentaja"}
                             {:name "LVI-insin\u00f6\u00f6ri"}
                             {:name "LVI-teknikko"}
                             {:name "LVI-ty\u00f6teknikko"}
                             {:name "maisema-arkkitehti"}
                             {:name "rakennusammattity\u00f6mies"}
                             {:name "rakennusarkkitehti"}
                             {:name "rakennusinsin\u00f6\u00f6ri"}
                             {:name "rakennusmestari"}
                             {:name "rakennuspiirt\u00e4j\u00e4"}
                             {:name "rakennusteknikko"}
                             {:name "rakennusty\u00f6teknikko"}
                             {:name "sisustusarkkitehti"}
                             {:name "talonrakennusinsin\u00f6\u00f6ri"}
                             {:name "talonrakennusteknikko"}
                             {:name "tekniikan kandidaatti"}
                             {:name "teknikko"}]})

(def patevyys [koulutusvalinta
               {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
               {:name "valmistumisvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size "s" :required false}
               {:name "fise" :type :string :required false}
               {:name "patevyys" :type :string :required false}
               {:name "patevyysluokka" :type :select :sortBy nil :required false
                :body [{:name "AA"}
                       {:name "A"}
                       {:name "B"}
                       {:name "C"}
                       {:name "ei tiedossa"}]}
               {:name "kokemus" :type :string :subtype :number :min-len 1 :max-len 2 :size "s" :required false}])

(def designer-basic (body
                      (schema-body-without-element-by-name henkilotiedot turvakielto)
                      {:name "yritys" :type :group
                       :body (clojure.walk/postwalk (fn [c] (if (and (map? c) (contains? c :required))
                                                              (assoc c :required false)
                                                              c)) yritys-minimal)}
                      simple-osoite
                      yhteystiedot))

(def paasuunnittelija (body
                        henkilo-valitsin
                        designer-basic
                        {:name "patevyys" :type :group :body patevyys}))

(def kuntaroolikoodi [{:name "kuntaRoolikoodi"
                       :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi._group_label"
                       :type :select :sortBy :displayname
                       :body [{:name "GEO-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.GEO-suunnittelija"}
                              {:name "LVI-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.LVI-suunnittelija"}
                              {:name "IV-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.IV-suunnittelija"}
                              {:name "KVV-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.KVV-suunnittelija"}
                              {:name "RAK-rakennesuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.RAK-rakennesuunnittelija"}
                              {:name "ARK-rakennussuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.ARK-rakennussuunnittelija"}
                              {:name "Vaikeiden t\u00F6iden suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.Vaikeiden t\u00f6iden suunnittelija"}
                              {:name "ei tiedossa" :i18nkey "osapuoli.kuntaRoolikoodi.ei tiedossa"}]}])

(def suunnittelija (body
                     kuntaroolikoodi
                     henkilo-valitsin
                     designer-basic
                     {:name "patevyys" :type :group :body patevyys}))

(def vastattavat-tyotehtavat-tyonjohtaja [{:name "vastattavatTyotehtavat"
                                           :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"
                                           :type :group
                                           :layout :vertical
                                           :body [{:name "rakennuksenRakentaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenRakentaminen" :type :checkbox}
                                                  {:name "rakennuksenMuutosJaKorjaustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenMuutosJaKorjaustyo"  :type :checkbox}
                                                  {:name "rakennuksenPurkaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenPurkaminen"  :type :checkbox}
                                                  {:name "maanrakennustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.maanrakennustyo"  :type :checkbox}
                                                  {:name "rakennelmaTaiLaitos" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennelmaTaiLaitos"  :type :checkbox}
                                                  {:name "elementtienAsennus" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.elementtienAsennus"  :type :checkbox}
                                                  {:name "terasRakenteet_tiilirakenteet" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.terasRakenteet_tiilirakenteet"  :type :checkbox}
                                                  {:name "kiinteistonVesiJaViemarilaitteistonRakentaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.kiinteistonVesiJaViemarilaitteistonRakentaminen"  :type :checkbox}
                                                  {:name "kiinteistonilmanvaihtolaitteistonRakentaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.kiinteistonilmanvaihtolaitteistonRakentaminen"  :type :checkbox}
                                                  {:name "muuMika" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMika"  :type :string}]}])

(def kuntaroolikoodi-tyonjohtaja [{:name "kuntaRoolikoodi"
                                   :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label"
                                   :type :select
                                   :sortBy :displayname
                                   :required true
                                   :body [{:name "KVV-ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.KVV-ty\u00f6njohtaja"}
                                          {:name "IV-ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.IV-ty\u00f6njohtaja"}
                                          {:name "erityisalojen ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.erityisalojen ty\u00f6njohtaja"}
                                          {:name "vastaava ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.vastaava ty\u00f6njohtaja"}
                                          {:name "ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.ty\u00f6njohtaja"}
                                          {:name "ei tiedossa" :i18nkey "osapuoli.kuntaRoolikoodi.ei tiedossa"}]}])

(def patevyysvaatimusluokka
  {:name "patevyysvaatimusluokka" :type :select :sortBy nil :required true
   :body [{:name "AA"}
          {:name "A"}
          {:name "B"}
          {:name "C"}
          {:name "ei tiedossa"}]})

(def patevyys-tyonjohtaja [koulutusvalinta
                           {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
                           patevyysvaatimusluokka ; Actually vaadittuPatevyysluokka in KRYSP
                           {:name "valmistumisvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size "s" :required true}
                           {:name "kokemusvuodet" :type :string :subtype :number :min-len 1 :max-len 2 :size "s" :required true}
                           {:name "valvottavienKohteidenMaara" :i18nkey "tyonjohtaja.patevyys.valvottavienKohteidenMaara" :type :string :subtype :number :size "s" :required true}
                           {:name "tyonjohtajaHakemusKytkin" :i18nkey "tyonjohtaja.patevyys.tyonjohtajaHakemusKytkin._group_label" :required true :type :select :sortBy :displayname :blacklist [:applicant]
                            :body [{:name "nimeaminen" :i18nkey "tyonjohtaja.patevyys.tyonjohtajaHakemusKytkin.nimeaminen"}
                                   {:name "hakemus" :i18nkey "tyonjohtaja.patevyys.tyonjohtajaHakemusKytkin.hakemus"}]}])

(def patevyys-tyonjohtaja-v2 [koulutusvalinta
                              {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
                              {:name "valmistumisvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size "s" :required true}
                              {:name "kokemusvuodet" :type :string :subtype :number :min-len 1 :max-len 2 :size "s" :required true}
                              {:name "valvottavienKohteidenMaara" :i18nkey "tyonjohtaja.patevyys.valvottavienKohteidenMaara" :type :string :subtype :number :size "s" :required true}])

(def sijaisuus-tyonjohtaja [{:name "sijaistus" :i18nkey "tyonjohtaja.sijaistus._group_label"
                             :type :group
                             :body [{:name "sijaistettavaHloEtunimi" :i18nkey "tyonjohtaja.sijaistus.sijaistettavaHloEtunimi" :type :string}
                                    {:name "sijaistettavaHloSukunimi" :i18nkey "tyonjohtaja.sijaistus.sijaistettavaHloSukunimi" :type :string}
                                    {:name "alkamisPvm" :i18nkey "tyonjohtaja.sijaistus.alkamisPvm" :type :date}
                                    {:name "paattymisPvm" :i18nkey "tyonjohtaja.sijaistus.paattymisPvm" :type :date}]}])

(def tyonjohtaja (body
                   kuntaroolikoodi-tyonjohtaja
                   vastattavat-tyotehtavat-tyonjohtaja
                   henkilo-valitsin
                   designer-basic
                   {:name "patevyys-tyonjohtaja" :type :group :body patevyys-tyonjohtaja}
                   sijaisuus-tyonjohtaja))

(def ilmoitus-hakemus-valitsin {:name "ilmoitusHakemusValitsin" :i18nkey "tyonjohtaja.ilmoitusHakemusValitsin._group_label" :type :select :sortBy :displayname :required true :blacklist [:applicant] :layout :single-line
                                :body [{:name "ilmoitus" :i18nkey "tyonjohtaja.ilmoitusHakemusValitsin.ilmoitus"}
                                       {:name "hakemus" :i18nkey "tyonjohtaja.ilmoitusHakemusValitsin.hakemus"}]})

(def kuntaroolikoodi-tyonjohtaja-v2 [{:name "kuntaRoolikoodi"
                                      :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label"
                                      :type :select
                                      :emit [:filterByCode]
                                      :sortBy :displayname
                                      :required true
                                      :body [{:name "vastaava ty\u00F6njohtaja" :code :vtj :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.vastaava ty\u00f6njohtaja"}
                                             {:name "KVV-ty\u00F6njohtaja" :code :kvv :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.KVV-ty\u00f6njohtaja"}
                                             {:name "IV-ty\u00F6njohtaja" :code :ivt :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.IV-ty\u00f6njohtaja"}
                                             {:name "erityisalojen ty\u00F6njohtaja" :code :vrt :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.erityisalojen ty\u00f6njohtaja"}]}])

(def vastattavat-tyotehtavat-tyonjohtaja-v2 [{:name "vastattavatTyotehtavat"
                                              :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"
                                              :type :group
                                              :required false
                                              :listen [:filterByCode]
                                              :layout :vertical
                                              :body [{:name "ivLaitoksenAsennustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.ivLaitoksenAsennustyo" :codes [:ivt] :type :checkbox}
                                                     {:name "ivLaitoksenKorjausJaMuutostyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.ivLaitoksenKorjausJaMuutostyo" :codes [:ivt] :type :checkbox}
                                                     {:name "sisapuolinenKvvTyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.sisapuolinenKvvTyo" :codes [:kvv] :type :checkbox}
                                                     {:name "ulkopuolinenKvvTyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.ulkopuolinenKvvTyo" :codes [:kvv] :type :checkbox}
                                                     {:name "rakennuksenMuutosJaKorjaustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenMuutosJaKorjaustyo" :codes [:vtj] :type :checkbox}
                                                     {:name "uudisrakennustyoMaanrakennustoineen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.uudisrakennustyoMaanrakennustoineen" :codes [:vtj] :type :checkbox}
                                                     {:name "uudisrakennustyoIlmanMaanrakennustoita" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.uudisrakennustyoIlmanMaanrakennustoita" :codes [:vtj] :type :checkbox}
                                                     {:name "linjasaneeraus" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.linjasaneeraus" :codes [:vtj] :type :checkbox}
                                                     {:name "maanrakennustyot" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.maanrakennustyot" :codes [:vtj] :type :checkbox}
                                                     {:name "rakennuksenPurkaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenPurkaminen" :codes [:vtj] :type :checkbox}
                                                     {:name "muuMika" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMika" :codes [:vtj :kvv :ivt :vrt] :type :string}]}])

(def tyonjohtaja-hanketieto {:name "tyonjohtajaHanketieto" :type :group
                             :body [{:name "taysiaikainenOsaaikainen" :type :radioGroup :body [{:name "taysiaikainen"} {:name "osaaikainen"}] :default "taysiaikainen"}
                                    {:name "hankeKesto" :type :string :size "s" :unit "kuukautta" :subtype :number :min 0 :max 9999999}
                                    {:name "kaytettavaAika" :type :string :size "s" :unit "tuntiaviikko" :subtype :number :min 0 :max 168} ; 7*24 = 168h :)
                                    {:name "kayntienMaara" :type :string :size "s" :unit "kpl" :subtype :number :min 0 :max 9999999}]})

(def hanke-row
  [{:name "luvanNumero" :type :string :size "m" :label false :uicomponent :string :i18nkey "muutHankkeet.luvanNumero"}
   {:name "katuosoite" :type :string :size "m" :label false :uicomponent :string :i18nkey "muutHankkeet.katuosoite"}
   {:name "rakennustoimenpide" :type :string :size "l" :label false :uicomponent :string :i18nkey "muutHankkeet.rakennustoimenpide"}
   {:name "kokonaisala" :type :string :subtype :number :size "s" :label false :uicomponent :string :i18nkey "muutHankkeet.kokonaisala"}
   {:name "vaihe" :type :select :size "t" :label false :uicomponent :select-component :i18nkey "muutHankkeet.vaihe"
    :body [{:name "R" :i18nkey "muutHankkeet.R"}
           {:name "A" :i18nkey "muutHankkeet.A"}
           {:name "K" :i18nkey "muutHankkeet.K"}]}
   {:name "3kk" :type :string :subtype :number :size "s" :label false :uicomponent :string :i18nkey "muutHankkeet.3kk"}
   {:name "6kk" :type :string :subtype :number :size "s" :label false :uicomponent :string :i18nkey "muutHankkeet.6kk"}
   {:name "9kk" :type :string :subtype :number :size "s" :label false :uicomponent :string :i18nkey "muutHankkeet.9kk"}
   {:name "12kk" :type :string :subtype :number  :size "s" :label false :uicomponent :string :i18nkey "muutHankkeet.12kk"}
   {:name "autoupdated" :type :checkbox :hidden true :i18nkey "muutHankkeet.autoupdated" :uicomponent :checkbox :whitelist {:roles [:none]
                                                                                                                            :otherwise :disabled}}])

(def muut-rakennushankkeet-table {:name "muutHankkeet"
                                  :type :foremanOtherApplications
                                  :uicomponent :hanke-table
                                  :repeating true
                                  :approvable true
                                  :copybutton false
                                  :listen [:hetuChanged]
                                  :body hanke-row})

(def tayta-omat-tiedot-button {:name "fillMyInfo" :type :fillMyInfoButton :whitelist {:roles [:applicant] :otherwise :disabled}})

(def tyonjohtajan-historia {:name "foremanHistory" :type :foremanHistory})

(def tyonjohtajan-hyvaksynta [{:name "tyonjohtajanHyvaksynta"
                               :type :group
                               :whitelist {:roles [:authority]
                                           :otherwise :hidden}
                               :body [tyonjohtajan-historia
                                      {:name "tyonjohtajanHyvaksynta" :type :checkbox :i18nkey "tyonjohtaja.historia.hyvaksynta"}]}])

(def tyonjohtaja-v2 (body
                      tayta-omat-tiedot-button
                      designer-basic
                      ilmoitus-hakemus-valitsin
                      kuntaroolikoodi-tyonjohtaja-v2
                      patevyysvaatimusluokka ; Actually vaadittuPatevyysluokka in KRYSP
                      vastattavat-tyotehtavat-tyonjohtaja-v2
                      tyonjohtaja-hanketieto
                      sijaisuus-tyonjohtaja
                      {:name "patevyys-tyonjohtaja" :type :group :body patevyys-tyonjohtaja-v2}
                      muut-rakennushankkeet-table
                      tyonjohtajan-hyvaksynta))

(def maksaja (body
               (henkilo-yritys-select-group
                 :yritys-body yritys-with-verkkolaskutustieto
                 :henkilo-body henkilo-maksaja)
               {:name "laskuviite" :type :string :max-len 30 :layout :full-width}))

(def muutostapa {:name "muutostapa" :type :select :sortBy :displayname :label false :i18nkey "huoneistot.muutostapa" :emit [:muutostapaChanged]
                 :body [{:name "poisto"}
                        {:name "lis\u00e4ys" :i18nkey "huoneistot.muutostapa.lisays"}
                        {:name "muutos"}]})

(def huoneistoRow [{:name "huoneistoTyyppi" :type :select :sortBy :displayname :label false :i18nkey "huoneistot.huoneistoTyyppi" :listen [:muutostapaChanged]
                   :body [{:name "asuinhuoneisto"}
                          {:name "toimitila"}
                          {:name "ei tiedossa" :i18nkey "huoneistot.huoneistoTyyppi.eiTiedossa"}]}
                   {:name "porras" :type :string :subtype :letter :case :upper :max-len 1 :size "t" :label false :i18nkey "huoneistot.porras" :listen [:muutostapaChanged]}
                   {:name "huoneistonumero" :type :string :subtype :number :min-len 1 :max-len 3 :size "s" :required true :label false :i18nkey "huoneistot.huoneistonumero" :listen [:muutostapaChanged]}
                   {:name "jakokirjain" :type :string :subtype :letter :case :lower :max-len 1 :size "t" :label false :i18nkey "huoneistot.jakokirjain" :listen [:muutostapaChanged]}
                   {:name "huoneluku" :type :string :subtype :number :min 1 :max 99 :required true :size "t" :label false :i18nkey "huoneistot.huoneluku" :listen [:muutostapaChanged]}
                   {:name "keittionTyyppi" :type :select :sortBy :displayname :required true :label false :i18nkey "huoneistot.keittionTyyppi" :listen [:muutostapaChanged]
                    :body [{:name "keittio"}
                           {:name "keittokomero"}
                           {:name "keittotila"}
                           {:name "tupakeittio"}
                           {:name "ei tiedossa" :i18nkey "huoneistot.keittionTyyppi.eiTiedossa"}]}
                   {:name "huoneistoala" :type :string :subtype :decimal :size "s" :min 1 :max 9999999 :required true :label false :i18nkey "huoneistot.huoneistoala" :listen [:muutostapaChanged]}
                   {:name "WCKytkin" :type :checkbox :label false :i18nkey "huoneistot.WCKytkin" :listen [:muutostapaChanged]}
                   {:name "ammeTaiSuihkuKytkin" :type :checkbox :label false :i18nkey "huoneistot.ammeTaiSuihkuKytkin" :listen [:muutostapaChanged]}
                   {:name "saunaKytkin" :type :checkbox :label false :i18nkey "huoneistot.saunaKytkin" :listen [:muutostapaChanged]}
                   {:name "parvekeTaiTerassiKytkin" :type :checkbox :label false :i18nkey "huoneistot.parvekeTaiTerassiKytkin" :listen [:muutostapaChanged]}
                   {:name "lamminvesiKytkin" :type :checkbox :label false :i18nkey "huoneistot.lamminvesiKytkin" :listen [:muutostapaChanged]}
                   muutostapa])

(def huoneistotTable {:name "huoneistot"
                      :i18nkey "huoneistot"
                      :type :table
                      :group-help "huoneistot.groupHelpText"
                      :repeating true
                      :approvable true
                      :copybutton true
                      :body huoneistoRow})

;; Usage type definitions have moved to lupapiste-commons.usage-types

(def kaytto {:name "kaytto"
             :type :group
             :body [{:name "rakentajaTyyppi" :type :select :sortBy :displayname :required true
                     :body [{:name "liiketaloudellinen"}
                            {:name "muu"}
                            {:name "ei tiedossa"}]}
                    {:name "kayttotarkoitus" :type :select :sortBy :displayname :size "l"
                     :body usages/rakennuksen-kayttotarkoitus}]})

(def mitat {:name "mitat"
            :type :group
            :body [{:name "tilavuus" :type :string :size "s" :unit "m3" :subtype :number :min 1 :max 9999999}
                   {:name "kerrosala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                   {:name "kokonaisala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                   {:name "kerrosluku" :type :string :size "s" :subtype :number :min 0 :max 50}
                   {:name "kellarinpinta-ala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}]})

(def mitat-muutos {:name "mitat"
                   :type :group
                   :group-help "mitat-muutos.help"
                   :whitelist {:roles [:authority]
                               :otherwise :disabled}
                   :body [{:name "tilavuus" :type :string :size "s" :unit "m3" :subtype :number :min 1 :max 9999999}
                          {:name "kerrosala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                          {:name "kokonaisala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                          {:name "kerrosluku" :type :string :size "s" :subtype :number :min 0 :max 50}
                          {:name "kellarinpinta-ala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}]})

(def rakenne {:name "rakenne"
              :type :group
              :body [{:name "rakentamistapa" :type :select :sortBy :displayname :required true
                      :body [{:name "elementti"}
                             {:name "paikalla"}
                             {:name "ei tiedossa"}]}
                     {:name "kantavaRakennusaine" :type :select :sortBy :displayname :required true :other-key "muuRakennusaine"
                      :body [{:name "betoni"}
                             {:name "tiili"}
                             {:name "ter\u00e4s"}
                             {:name "puu"}
                             {:name "ei tiedossa"}]}
                     {:name "muuRakennusaine" :type :string}
                     {:name "julkisivu" :type :select :sortBy :displayname :other-key "muuMateriaali"
                      :body [{:name "betoni"}
                             {:name "tiili"}
                             {:name "metallilevy"}
                             {:name "kivi"}
                             {:name "puu"}
                             {:name "lasi"}
                             {:name "ei tiedossa"}]}
                     {:name "muuMateriaali" :type :string}]})

(def lammitys {:name "lammitys"
               :type :group
               :body [{:name "lammitystapa" :type :select :sortBy :displayname
                       :body [{:name "vesikeskus"}
                              {:name "ilmakeskus"}
                              {:name "suora s\u00e4hk\u00f6"}
                              {:name "uuni"}
                              {:name "ei l\u00e4mmityst\u00e4"}
                              {:name "ei tiedossa"}]}
                      {:name "lammonlahde" :type :select :sortBy :displayname :other-key "muu-lammonlahde"
                       :body [{:name "kauko tai aluel\u00e4mp\u00f6"}
                              {:name "kevyt poltto\u00f6ljy"}
                              {:name "raskas poltto\u00f6ljy"}
                              {:name "s\u00e4hk\u00f6"}
                              {:name "kaasu"}
                              {:name "kiviihiili koksi tms"}
                              {:name "turve"}
                              {:name "maal\u00e4mp\u00f6"}
                              {:name "puu"}
                              {:name "ei tiedossa"}]}
                      {:name "muu-lammonlahde" :type :string}]})

(def verkostoliittymat {:name "verkostoliittymat" :type :group :layout :vertical
                        :body [{:name "viemariKytkin" :type :checkbox}
                               {:name "vesijohtoKytkin" :type :checkbox}
                               {:name "sahkoKytkin" :type :checkbox}
                               {:name "maakaasuKytkin" :type :checkbox}
                               {:name "kaapeliKytkin" :type :checkbox}]})
(def varusteet {:name "varusteet" :type :group :layout :vertical
                                                     :body [{:name "sahkoKytkin" :type :checkbox}
                                                            {:name "kaasuKytkin" :type :checkbox}
                                                            {:name "viemariKytkin" :type :checkbox}
                                                            {:name "vesijohtoKytkin" :type :checkbox}
                                                            {:name "hissiKytkin" :type :checkbox}
                                                            {:name "koneellinenilmastointiKytkin" :type :checkbox}
                                                            {:name "lamminvesiKytkin" :type :checkbox}
                                                            {:name "aurinkopaneeliKytkin" :type :checkbox}
                                                            {:name "saunoja" :type :string :subtype :number :min 1 :max 99 :size "s" :unit "kpl"}
                                                            {:name "vaestonsuoja" :type :string :subtype :number :min 1 :max 99999 :size "s" :unit "hengelle"}
                                                            {:name "liitettyJatevesijarjestelmaanKytkin" :type :checkbox}]})

(def luokitus {:name "luokitus"
               :type :group
               :body [{:name "energialuokka" :type :select :sortBy :displayname
                       :body [{:name "A"}
                              {:name "B"}
                              {:name "C"}
                              {:name "D"}
                              {:name "E"}
                              {:name "F"}
                              {:name "G"}]}
                      {:name "energiatehokkuusluku" :type :string :size "s" :subtype :number}
                      {:name "energiatehokkuusluvunYksikko" :type :select, :sortBy :displayname, :default "kWh/m2"
                       :body [{:name "kWh/m2"}
                              {:name "kWh/brm2/vuosi"}]}
                      {:name "paloluokka" :type :select :sortBy :displayname
                       :body [{:name "palonkest\u00e4v\u00e4"}
                               {:name "paloapid\u00e4tt\u00e4v\u00e4"}
                               {:name "paloahidastava"}
                               {:name "l\u00e4hinn\u00e4 paloakest\u00e4v\u00e4"}
                               {:name "l\u00e4hinn\u00e4 paloapid\u00e4tt\u00e4v\u00e4"}
                               {:name "l\u00e4hinn\u00e4 paloahidastava"}
                               {:name "P1"}
                               {:name "P2"}
                               {:name "P3"}
                               {:name "P1/P2"}
                               {:name "P1/P3"}
                               {:name "P2/P3"}
                               {:name "P1/P2/P3"}]}]})

(def rakennuksen-tiedot-ilman-huoneistoa [kaytto
                                          mitat
                                          rakenne
                                          lammitys
                                          verkostoliittymat
                                          varusteet
                                          luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-muutos [kaytto
                                                 mitat-muutos
                                                 rakenne
                                                 lammitys
                                                 verkostoliittymat
                                                 varusteet
                                                 luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja [kaytto
                                                                  rakenne
                                                                  mitat])

(def rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja-muutos [kaytto
                                                                         rakenne
                                                                         mitat-muutos])

(def rakennuksen-tiedot (conj rakennuksen-tiedot-ilman-huoneistoa huoneistotTable))

(def rakennuksen-tiedot-muutos (conj rakennuksen-tiedot-ilman-huoneistoa-muutos huoneistotTable))

(def alle-yli-radiogroup
  {:name "alleYli" :type :radioGroup :body [{:name "alle"} {:name "yli"}] :default "alle" :required true})

(defn etaisyys-row [name min-default]
  {:name name
   :type :group
   :body [{:name "minimietaisyys" :type :string :size "s" :unit "m" :readonly true :default min-default :required true}
          alle-yli-radiogroup
          {:name "huomautukset" :type :string :size "l"}]})

(def maalampokaivon-etaisyydet {:name "kaivo-etaisyydet"
                                :i18nkey "kaivo-etaisyydet"
                                :type :group
                                :group-help "kaivo-etaisyydet.groupHelpText"
                                :approvable true
                                :body (body
                                        (etaisyys-row "lampokaivo" "15")
                                        (etaisyys-row "porakaivo" "40")
                                        (etaisyys-row "rengaskaivo" "20")
                                        (etaisyys-row "rakennus" "3")
                                        (etaisyys-row "tontin-raja" "7.5")
                                        (etaisyys-row "omat-vv-johdot" "3") ; vesi- ja viemarijohdot
                                        (etaisyys-row "muut-vv-johdot" "5") ; vesi- ja viemarijohdot
                                        (etaisyys-row "omat-lampojohdot" "3")
                                        (etaisyys-row "muut-lampojohdot" "5")
                                        (etaisyys-row "wc-jatevedet-purkupaikka" "30")
                                        (etaisyys-row "harmaat-jatevedet-purkupaikka" "20"))})

(def maalampokaivo-rakennelma (body
                                kuvaus
                                maalampokaivon-etaisyydet))

(def rakennelma (body
                  [{:name "kokonaisala" :type :string :size "s" :unit "m2" :subtype :number}]
                  kuvaus))
(def maisematyo (body kuvaus))

(def rakennuksen-omistajat [{:name "rakennuksenOmistajat"
                             :type :group
                             :repeating true
                             :approvable true
                             :body (body party-with-required-hetu
                                     [{:name "omistajalaji" :type :select :sortBy :displayname :other-key "muu-omistajalaji" :required true :size "l"
                                       :body [{:name "yksityinen maatalousyritt\u00e4j\u00e4"}
                                              {:name "muu yksityinen henkil\u00f6 tai perikunta"}
                                              {:name "asunto-oy tai asunto-osuuskunta"}
                                              {:name "kiinteist\u00f6 oy"}
                                              {:name "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"}
                                              {:name "valtio- tai kuntaenemmist\u00f6inen yritys"}
                                              {:name "kunnan liikelaitos"}
                                              {:name "valtion liikelaitos"}
                                              {:name "pankki tai vakuutuslaitos"}
                                              {:name "kunta tai kuntainliitto"}
                                              {:name "valtio"}
                                              {:name "sosiaaliturvarahasto"}
                                              {:name "uskonnollinen yhteis\u00f6, s\u00e4\u00e4ti\u00f6, puolue tai yhdistys"}
                                              {:name "ei tiedossa"}]}
                                      {:name "muu-omistajalaji" :type :string}])}])

(def muumuutostyo "muut muutosty\u00f6t")
(def perustusten-korjaus "perustusten ja kantavien rakenteiden muutos- ja korjausty\u00f6t")
(def kayttotarkotuksen-muutos "rakennukse p\u00e4\u00e4asiallinen k\u00e4ytt\u00f6tarkoitusmuutos")

(def muutostyonlaji [{:name "perusparannuskytkin" :type :checkbox}
                     {:name "muutostyolaji" :type :select :sortBy :displayname :required true
                      :body
                      [{:name perustusten-korjaus}
                       {:name kayttotarkotuksen-muutos}
                       {:name muumuutostyo}]}])

(def olemassaoleva-rakennus (body
                              rakennuksen-valitsin
                              rakennuksen-omistajat
                              rakennuksen-osoite
                              rakennuksen-tiedot))

(def olemassaoleva-rakennus-muutos (body
                                     rakennuksen-valitsin
                                     rakennuksen-omistajat
                                     rakennuksen-osoite
                                     rakennuksen-tiedot-muutos))

(def olemassaoleva-rakennus-ei-huoneistoja (body
                                             rakennuksen-valitsin
                                             rakennuksen-omistajat
                                             rakennuksen-osoite
                                             rakennuksen-tiedot-ilman-huoneistoa))

(def olemassaoleva-rakennus-ei-huoneistoja-muutos (body
                                                    rakennuksen-valitsin
                                                    rakennuksen-omistajat
                                                    rakennuksen-osoite
                                                    rakennuksen-tiedot-ilman-huoneistoa-muutos))

(def olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja
  (body rakennuksen-valitsin
        rakennuksen-omistajat
        rakennuksen-osoite
        rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja))

(def olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja-muutos
  (body rakennuksen-valitsin
        rakennuksen-omistajat
        rakennuksen-osoite
        rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja-muutos))

(def rakennuksen-muuttaminen-ei-huoneistoja (body
                                              muutostyonlaji
                                              olemassaoleva-rakennus-ei-huoneistoja))

(def rakennuksen-muuttaminen-ei-huoneistoja-muutos (body
                                                     muutostyonlaji
                                                     olemassaoleva-rakennus-ei-huoneistoja-muutos))

(def rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja (body
                                                                    muutostyonlaji
                                                                    olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja))

(def rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja-muutos (body
                                                                    muutostyonlaji
                                                                    olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja-muutos))

(def rakennuksen-muuttaminen (body
                               muutostyonlaji
                               olemassaoleva-rakennus))

(def rakennuksen-muuttaminen-muutos (body
                               muutostyonlaji
                               olemassaoleva-rakennus-muutos))

(def rakennuksen-laajentaminen (body [{:name "laajennuksen-tiedot"
                                       :type :group
                                       :body [{:name "perusparannuskytkin" :type :checkbox}
                                              {:name "mitat"
                                               :type :group
                                               :body [{:name "tilavuus" :type :string :size "s" :unit "m3" :subtype :number :min 1 :max 9999999}
                                                      {:name "kerrosala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                                                      {:name "kokonaisala" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                                                      {:name "huoneistoala" :type :group :repeating true :removable true
                                                       :body [{:name "pintaAla" :type :string :size "s" :unit "m2" :subtype :number :min 1 :max 9999999}
                                                              {:name "kayttotarkoitusKoodi" :type :select :sortBy :displayname
                                                               :body [{:name "asuntotilaa(ei vapaa-ajan asunnoista)"}
                                                                      {:name "myym\u00e4l\u00e4, majoitus- ja ravitsemustilaa"}
                                                                      {:name "hoitotilaa"}
                                                                      {:name "toimisto- ja hallintotilaa"}
                                                                      {:name "kokoontumistilaa"}
                                                                      {:name "opetustilaa"}
                                                                      {:name "tuotantotilaa(teollisuus)"}
                                                                      {:name "varastotilaa"}
                                                                      {:name "muuta huoneistoalaan kuuluvaa tilaa"}
                                                                      {:name "ei tiedossa"}]}]}]}]}]
                                     olemassaoleva-rakennus))

(def purku (body
             {:name "poistumanSyy" :type :select :sortBy :displayname
              :body [{:name "purettu uudisrakentamisen vuoksi"}
                     {:name "purettu muusta syyst\u00e4"}
                     {:name "tuhoutunut"}
                     {:name "r\u00e4nsistymisen vuoksi hyl\u00e4tty"}
                     {:name "poistaminen"}]}
             {:name "poistumanAjankohta" :type :date}
             olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja))

(def rakennuspaikka [{:name "kiinteisto"
                      :type :group
                      :body [{:name "maaraalaTunnus" :type :string :subtype :maaraala-tunnus :size "s"}
                             {:name "tilanNimi" :type :string :readonly true}
                             {:name "rekisterointipvm" :type :string :readonly true}
                             {:name "maapintaala" :type :string :readonly true :unit "hehtaaria"}
                             {:name "vesipintaala" :type :string :readonly true :unit "hehtaaria"}
                             {:name "rantaKytkin" :type :checkbox}]}
                     {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                      :body [{:name "oma"}
                             {:name "vuokra"}
                             {:name "ei tiedossa"}]}
                     {:name "kaavanaste" :type :select :sortBy :displayname :hidden true
                      :body [{:name "asema"}
                             {:name "ranta"}
                             {:name "rakennus"}
                             {:name "yleis"}
                             {:name "ei kaavaa"}
                             {:name "ei tiedossa"}]}
                     {:name "kaavatilanne" :type :select :sortBy :displayname
                      :body [{:name "maakuntakaava"}
                             {:name "oikeusvaikutteinen yleiskaava"}
                             {:name "oikeusvaikutukseton yleiskaava"}
                             {:name "asemakaava"}
                             {:name "ranta-asemakaava"}
                             {:name "ei kaavaa"}]}])


(defn- approvable-top-level-groups [v]
  (map #(if (= (:type %) :group) (assoc % :approvable true) %) v))

;;
;; schemas
;;

(defschemas
  1
  [{:info {:name "hankkeen-kuvaus-minimum"
           :approvable true
           :order 1}
    :body [kuvaus]}

   {:info {:name "hankkeen-kuvaus"
           :approvable true
           :order 1}
    :body [kuvaus
           {:name "poikkeamat" :type :text :max-len 5400 :layout :full-width}]} ; Longest value in Helsinki production data

   {:info {:name "uusiRakennus" :approvable true}
    :body (body rakennuksen-omistajat (approvable-top-level-groups rakennuksen-tiedot))}

   {:info {:name "uusi-rakennus-ei-huoneistoa" :i18name "uusiRakennus" :approvable true}
    :body (body rakennuksen-omistajat (approvable-top-level-groups rakennuksen-tiedot-ilman-huoneistoa))}

   {:info {:name "rakennuksen-muuttaminen-ei-huoneistoja" :i18name "rakennuksen-muuttaminen" :approvable true}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-ei-huoneistoja-muutos)}

   {:info {:name "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia" :i18name "rakennuksen-muuttaminen" :approvable true}
     :body (approvable-top-level-groups rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja-muutos)}

   {:info {:name "rakennuksen-muuttaminen" :approvable true}
     :body (approvable-top-level-groups rakennuksen-muuttaminen-muutos)}

    {:info {:name "rakennuksen-laajentaminen" :approvable true}
     :body (approvable-top-level-groups rakennuksen-laajentaminen)}

    {:info {:name "purkaminen" :i18name "purku" :approvable true}
     :body (approvable-top-level-groups purku)}

    {:info {:name "kaupunkikuvatoimenpide" :approvable true}
     :body (approvable-top-level-groups rakennelma)}

    {:info {:name "maalampokaivo"
            :approvable true
            :i18name "maalampokaivo"}
     :body (approvable-top-level-groups maalampokaivo-rakennelma)}

    {:info {:name "maisematyo" :approvable true}
     :body (approvable-top-level-groups maisematyo)}
    {:info {:name "kiinteistotoimitus" :approvable true}
     :body (approvable-top-level-groups (body kuvaus))}

    {:info {:name "maankayton-muutos" :approvable true}
     :body (approvable-top-level-groups (body kuvaus))}

    {:info {:name "hakija"
            :i18name "osapuoli"
            :order 4
            :removable true
            :repeating true
            :approvable true
            :type :party
            :subtype "hakija"
            :group-help nil
            :section-help nil
            :after-update 'lupapalvelu.application-meta-fields/applicant-index-update
            }
     :body party}

    {:info {:name "hakija-r"
            :i18name "osapuoli"
            :order 4
            :removable true
            :repeating true
            :approvable true
            :type :party
            :subtype "hakija"
            :group-help "hakija.group.help"
            :section-help "party.section.help"
            :after-update 'lupapalvelu.application-meta-fields/applicant-index-update
            }
     :body party}

    {:info {:name "hakija-ya"
            :i18name "osapuoli"
            :order 4
            :removable false
            :repeating false
            :approvable true
            :type :party
            :subtype "hakija"
            :group-help nil
            :section-help nil
            :after-update 'lupapalvelu.application-meta-fields/applicant-index-update}
     :body (schema-body-without-element-by-name ya-party turvakielto)}

    {:info {:name "paasuunnittelija"
            :i18name "osapuoli"
            :order 5
            :removable false
            :approvable true
            :type :party}
     :body paasuunnittelija}

    {:info {:name "suunnittelija"
            :i18name "osapuoli"
            :repeating true
            :order 6
            :removable true
            :approvable true
            :type :party}
     :body suunnittelija}

    {:info {:name "tyonjohtaja"
            :i18name "osapuoli"
            :order 6
            :removable true
            :repeating true
            :approvable true
            :type :party}
     :body tyonjohtaja}

    {:info {:name "tyonjohtaja-v2"
            :i18name "osapuoli"
            :order 6
            :removable false
            :repeating false
            :approvable true
            :type :party}
     :body tyonjohtaja-v2}

    {:info {:name "maksaja"
            :i18name "osapuoli"
            :repeating true
            :order 7
            :removable true
            :approvable true
            :subtype :maksaja
            :type :party}
     :body maksaja}

    {:info {:name "rakennuspaikka"
            :approvable true
            :order 2
            :type :location}
     :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin")}

    {:info {:name "kiinteisto"
            :approvable true
            :order 2
            :type :location}
     :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin" "hallintaperuste" "kaavanaste" "kaavatilanne")}

    {:info {:name "secondary-kiinteistot"
            :i18name "kiinteisto"
            :approvable true
            :order 3
            :repeating true
            :removable true
            :type :location}
     :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin" "hallintaperuste" "kaavanaste" "kaavatilanne")}

    {:info {:name "paatoksen-toimitus-rakval"
            :removable false
            :approvable true
            :order 10}
     :body (body
             [(update-in henkilotiedot-minimal [:body] (partial remove #(= turvakielto (:name %))))]
             simple-osoite
             [{:name "yritys" :type :group
               :body [{:name "yritysnimi" :type :string}]}]
             tayta-omat-tiedot-button)}

    {:info {:name "aloitusoikeus" :removable false :approvable true}
     :body (body kuvaus)}

    {:info {:name "lisatiedot"
            :order 100}
     :body [{:name "suoramarkkinointikielto" ;THIS IS DEPRECATED!
             :type :checkbox
             :layout :full-width}]}])
