(ns lupapalvelu.document.schemas)

(defn to-map-by-name
  "Take list of schema maps, return a map of schemas keyed by :name under :info"
  [docs]
  (reduce (fn [docs doc] (assoc docs (get-in doc [:info :name]) doc))
          {}
          docs))

(def simple-osoite {:name "osoite" :type :group
                    :body [{:name "katu" :type :string}
                           {:name "postinumero" :type :string}
                           {:name "postitoimipaikka" :type :string}]})

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

(def schemas
  (to-map-by-name
    [{:info {:name "uusi-rakennus"}
      :body [{:name "kayttotarkoitus" :type :string}
             {:name "tilavuus" :type :string}
             {:name "kokonaisala" :type :string}
             {:name "kellaripinta-ala" :type :string}
             {:name "osoite" :type :group :body full-osoite-body}
             {:name "rinnakkaisosoite" :type :group :body full-osoite-body}
             {:name "kerrosluku" :type :string}
             {:name "kerrosala" :type :string :unit "m2" :subtype :number}
             {:name "rakentamistapa" :type :select
              :body [{:name "alementti" :type :checkbox}
                     {:name "paikkalla" :type :checkbox}
                     {:name "eiTiedossa" :type :checkbox}]}
             {:name "kantavarakennusaine" :type :select
              :body [{:name "betoni" :type :checkbox}
                     {:name "tiili" :type :checkbox}
                     {:name "teras" :type :checkbox}
                     {:name "eitiedossa" :type :checkbox}
                     {:name "muurakennusaine" :type :string :size "s"}]}                  
             {:name "julkisivu" :type :choice
              :body [{:name "betoni" :type :checkbox}
                     {:name "tiili" :type :checkbox}
                     {:name "metallilevy" :type :checkbox}
                     {:name "kivi" :type :checkbox}
                     {:name "puu" :type :checkbox}
                     {:name "eitiedossa" :type :checkbox}
                     {:name "muumateriaali" :type :string :size "s"}]}
             {:name "verkostoliittymat" :type :choice
              :body [{:name "viemariKytkin" :type :checkbox}
                     {:name "vesijohtoKytkin" :type :checkbox}
                     {:name "sahkokytkin" :type :checkbox}
                     {:name "maakasuKytkin" :type :checkbox}
                     {:name "kaapeliKytkin" :type :checkbox}]}
             {:name "energialuokka" :type :string}
             {:name "paloluokka" :type :string}
             {:name "lammitystapa" :type :choice
              :body [{:name "vesikeskus" :type :checkbox}
                     {:name "ilamkeskus" :type :checkbox}
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
                     {:name "eitiedossa" :type :checkbox}]}
             {:name "varusteet" :type :choice
              :body [{:name "sahkoKytkin" :type :checkbox}
                     {:name "kaasuKytkin" :type :checkbox}
                     {:name "viemariKytkin" :type :checkbox}
                     {:name "vesijohtoKytkin" :type :checkbox}
                     {:name "hissiKytkin" :type :checkbox}
                     {:name "koneellinenilmanstointiKytkin" :type :checkbox}
                     {:name "saunoja" :type :string}
                     {:name "uimala-ataita" :type :string}
                     {:name "vaestonsuojia" :type :string}]}
             {:name "materiaali" :type "select"
              :body [{:name "puu"}
                     {:name "purkka"}
                     {:name "betoni"}]}
             {:name "story" :type :text}]}
     {:info {:name "hakija"}
      :body [{:name "etunimi" :type :string}
             {:name "sukunimi" :type :string}
             simple-osoite
             {:name "puhelin" :type :string :subtype :tel}
             {:name "email" :type :string :subtype :email}]}
     {:info {:name "paasuunnittelija"}
      :body [{:name "etunimi" :type :string}
             {:name "sukunimi" :type :string}
             {:name "email" :type :string}
             {:name "puhelin" :type :string}
             {:name "koulutus" :type :string}
             {:name "patevyysluokka" :type :select
              :body [{:name "aa"}
                     {:name "a"}
                     {:name "b"}
                     {:name "c"}]}
             {:name "kokemus" :type :string}]}]))
