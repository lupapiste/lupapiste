(ns lupapalvelu.document.schemas)

(defn to-map-by-name
  "Take list of schema maps, return a map of schemas keyed by :name under :info"
  [docs]
  (reduce (fn [docs doc] (assoc docs (get-in doc [:info :name]) doc))
          {}
          docs))

(def simple-osoite-body [{:name "katu" :type :string}
                         {:name "postinumero" :type :string}
                         {:name "postitoimipaikka" :type :string}])

(def full-osoite-body [{:name "kunta" :type :string}
                       {:name "lahiosoite" :type :string}
                       {:name "osoitenumero" :type :string}
                       {:name "osoitenumero2" :type :string}
                       {:name "jakokirjain" :type :string}
                       {:name "jakokirjain2" :type :string}
                       {:name "porras" :type :string}
                       {:name "huoneisto" :type :string}
                       {:name "postinumero" :type :string}
                       {:name "postitoimipaikka" :type :string}
                       {:name "pistesijanti" :type :string}])

(def henkilo-body [{:name "etunimi" :type :string}
                   {:name "sukunimi" :type :string}
                   {:name "osoite" :type :group :body simple-osoite-body}
                   {:name "puhelin" :type :string :subtype :tel}
                   {:name "email" :type :string :subtype :email}
                   {:name "fax" :type :string}
                   {:name "hetu" :type :string}])

(def yritys-body [{:name "yritysnimi" :type :string}
                   {:name "liikeJaYhteisoTunnus" :type :string}
                   {:name "osoite" :type :group :body simple-osoite-body}
                   {:name "puhelin" :type :string :subtype :tel}
                   {:name "email" :type :string :subtype :email}
                   {:name "fax" :type :string}])


; TODO: Yritys?
(def suunnittelija-body (concat
                         henkilo-body
                         [{:name "koulutus" :type :string}
                          {:name "patevyysluokka" :type :select
                           :body [{:name "aa"}
                                  {:name "a"}
                                  {:name "b"}
                                  {:name "c"}]}
                          {:name "kokemus" :type :string}
                          {:name "Liiteet" :type :string}])) ; TODO miten liitteet hanskataan

(def schemas
  (to-map-by-name
    [{:info {:name "uusiRakennus"}
      :body [
             {:name "rakennuksenOmistajat" 
              :type :group 
              :body henkilo-body} ;TODO yritys ja monta
             {:name "rakentajaTyyppi" :type "select"
              :body [{:name "liiketaloudellinen"}
                     {:name "muu"}
                     {:name "eiTiedossa"}]}
             
             {:name "kayttotarkoitus" :type :string}
             {:name "tilavuus" :type :string}
             {:name "kokonaisala" :type :string}
             {:name "kellaripinta-ala" :type :string}
             {:name "kerrosluku" :type :string}
             {:name "kerrosala" :type :string :unit "m2" :subtype :number}
             {:name "rakentamistapa" :type :select
              :body [{:name "elementti" :type :checkbox}
                     {:name "paikalla" :type :checkbox}
                     {:name "eiTiedossa" :type :checkbox}]}
             {:name "kantavarakennusaine" :type :select
              :body [{:name "betoni" :type :checkbox}
                     {:name "tiili" :type :checkbox}
                     {:name "teras" :type :checkbox}
                     {:name "puu" :type :checkbox}
                     {:name "muurakennusaine" :type :string :size "s"}
                     {:name "eiTiedossa" :type :checkbox}]} 
             {:name "julkisivu" :type :select
              :body [{:name "betoni" :type :checkbox}
                     {:name "tiili" :type :checkbox}
                     {:name "metallilevy" :type :checkbox}
                     {:name "kivi" :type :checkbox}
                     {:name "puu" :type :checkbox}
                     {:name "lasi" :type :checkbox}
                     {:name "muumateriaali" :type :string :size "s"}
                     {:name "eiTiedossa" :type :checkbox}]}
             {:name "verkostoliittymat" :type :choice
              :body [{:name "viemariKytkin" :type :checkbox}
                     {:name "vesijohtoKytkin" :type :checkbox}
                     {:name "sahkokytkin" :type :checkbox}
                     {:name "maakaasuKytkin" :type :checkbox}
                     {:name "kaapeliKytkin" :type :checkbox}]}
             {:name "energialuokka" :type :string}
             {:name "paloluokka" :type :string}
             {:name "lammitystapa" :type :select
              :body [{:name "vesikeskus" :type :checkbox}
                     {:name "ilmakeskus" :type :checkbox}
                     {:name "suorasahko" :type :checkbox}
                     {:name "uuni" :type :checkbox}
                     {:name "eiLammitysta" :type :checkbox}
                     {:name "eiTiedossa" :type :checkbox}]}
             {:name "lammonlahde" :type :select
              :body [{:name "kaukotaialuelampo" :type :checkbox}
                     {:name "kevytpolttooljy" :type :checkbox}
                     {:name "raskaspolttooljy" :type :checkbox}
                     {:name "sahko" :type :checkbox}
                     {:name "kaasu" :type :checkbox}
                     {:name "kivihiilikoksitms" :type :checkbox}
                     {:name "turve" :type :checkbox}
                     {:name "maalampo" :type :checkbox}
                     {:name "puu" :type :checkbox}
                     {:name "muu" :type :string :size "s"}
                     {:name "eiTiedossa" :type :checkbox}]}
             {:name "varusteet" :type :choice
              :body [{:name "sahkoKytkin" :type :checkbox}
                     {:name "kaasuKytkin" :type :checkbox}
                     {:name "viemariKytkin" :type :checkbox}
                     {:name "vesijohtoKytkin" :type :checkbox}
                     {:name "hissiKytkin" :type :checkbox}
                     {:name "koneellinenilmastointiKytkin" :type :checkbox}
                     {:name "saunoja" :type :string}
                     {:name "vaestonsuojia" :type :string}]}
             {:name "poikkeamiset" :type :string}]}
     
     {:info {:name "huoneisto"}
      :body [{:name "huoneluku" :type :string}
             {:name "keittionTyyppi" :type :select
              :body [{:name "keittio"}
                     {:name "keittoKomero"}
                     {:name "keittoTila"}
                     {:name "tupakeittio"}
                     {:name "eiTiedossa"}]}
             {:name "huoneistoala" :type :string}
             {:name "huoneistoTyyppi" :type :select
              :body [{:name "asuinhuoneisto"}
                     {:name "toimitila"}
                     {:name "eiTiedossa"}]}
             {:name "huoneistoTunnus" :type :string}
             {:name "varusteet" :type :choice 
              :body [{:name "wc" :type :checkbox}
                     {:name "ammeTaiSuihku" :type :checkbox}
                     {:name "sauna" :type :checkbox}
                     {:name "parvekeTaiTerassi" :type :checkbox}]}]}
     
     {:info {:name "hakija"} 
      :body [{:name "henkilo" :type :group :body henkilo-body}
             {:name "yritys" :type :group :body yritys-body}]}
     
     {:info {:name "paasuunnittelija"}
      :body suunnittelija-body}
     
     {:info {:name "suunnittelija"}
      :body suunnittelija-body}
     
     {:info {:name "maksaja"} ; TODO yritys ja suunnittelijatyypin valinta
      :body henkilo-body}
     
     {:info {:name "rakennuspaikka"} ; TODO sijainti(kios?/ jo kartalta osositettu)
      :body [{:name "kiinteistotunnus" :type :string}
             {:name "maaraalaTunnus" :type :string}
             {:name "kylaNimi" :type :string}
             {:name "tilanNimi" :type :string}
             {:name "kokotilaKytkin" :type :checkbox} ; TODO jos maara-ala tunnus kokotilakytkin pitaisi olla false. Tarvitaanko ollenkaan?
             {:name "hallintaperuste" :type :select
              :body [{:name "oma"}
                     {:name "vuokra"}
                     {:name "eiTiedossa"}]}
             {:name "kaavanaste" :type "select"
              :body [{:name "asema"}
                     {:name "ranta"}
                     {:name "rakennus"}
                     {:name "yleis"}
                     {:name "eiKaavaa"}
                     {:name "eiTiedossa"}]}]}
     
     {:info {:name "osoite"}
      :body full-osoite-body}
     
     {:info {:name "lisatiedot"}
      :body [{:name "suoramarkkinointikielto" :type :checkbox}]}]))
