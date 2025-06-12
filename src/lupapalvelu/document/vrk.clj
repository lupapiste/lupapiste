(ns lupapalvelu.document.vrk
  (:require [lupapalvelu.document.validator :refer :all]
            [sade.strings :as ss]
            [sade.util :refer [->double ->int]]))

(defn exists? [x] (-> x ss/blank? not))

;;Sivulla https://pxnet2.stat.fi/fi/luokitukset/rakennusluokitin.html
;; on vastaavuudet rakennusluokkien ja käyttötarkoituksien välillä.

(defn asuinrakennukset-rl [int]
  (< 0 int 200))

(defn pientalot-rl [int]
  (<= 110 int 119))

(defn maatalousrakennukset-ja-muut-rakennukset [int]
  (>= int 1400))

(def rakennus-schemas ["uusiRakennus"
                       "uusi-rakennus-ei-huoneistoa"
                       "rakennuksen-muuttaminen-ei-huoneistoja"
                       "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                       "rakennuksen-muuttaminen"
                       "rakennuksen-laajentaminen"
                       "rakennuksen-laajentaminen-ei-huoneistoja"
                       "purkaminen"])

(def ei-lammitysta "ei lämmitystä")

;;
;; helpers
;;

(defn get-digits-prefix [x]
  (some->> x (re-matches #"(\d+) .*") last keyword ))

(defn ->huoneistoala
  "Removed apartments are not included."
  [huoneistot]
  (->> huoneistot
       (keep (fn [[_ {:keys [huoneistoala muutostapa]}]]
               (when-not (= muutostapa "poisto")
                 (some->> (ss/replace huoneistoala "," ".")
                          ss/blank-as-nil
                          ->double))))
       (apply +)))

(defn ->count [m]
  (-> m keys count))

(defn repeating
  ([n]
    (repeating n {:any :any}))
  ([n body]
    (for [i (take n (range))]
      {(-> i str keyword) body})))

;;
;; Validators
;;

(defvalidator :vrk:CR335
  {:doc "Jos lammitystapa ei ole 5 (ei kiinteaa lammitystapaa), on polttoaine ilmoitettava"
   :schemas ["uusiRakennus"]
   :fields [lammitystapa [:lammitys :lammitystapa]
            polttoaine   [:lammitys :lammonlahde]]
   :facts {:ok   [["ei lämmitystä" nil]
                  ["ei lämmitystä" ""]
                  ["suora sähkö" "sähkö"]]
           :fail [["suora sähkö" ""]
                  ["suora sähkö" nil]]}}
  (and lammitystapa (not= lammitystapa ei-lammitysta) (ss/blank? polttoaine)))

(defvalidator :vrk:CR336
 {:doc "Jos lammitystapa on 5 (ei kiinteaa lammitystapaa), ei saa olla polttoainetta"
  :schemas ["uusiRakennus"]
  :fields [lammitystapa [:lammitys :lammitystapa]
           polttoaine   [:lammitys :lammonlahde]]
  :facts {:ok   [["ei lämmitystä" nil]
                 ["ei lämmitystä" ""]
                 ["suora sähkö" "sähkö"]]
          :fail [["ei lämmitystä" "sähkö"]
                 ["ei lämmitystä" "ei tiedossa"]]}}
 (and (= lammitystapa ei-lammitysta) (exists? polttoaine)))

(defvalidator :vrk:CR326
  {:doc    "Kokonaisalan oltava vahintaan kerrosala"
   :schemas ["uusiRakennus"]
   :fields [kokonaisala [:mitat :kokonaisala ->int]
            kerrosala   [:mitat :kerrosala ->int]]
   :facts  {:ok   [[10 10]]
            :fail [[10 11]]}}
  (and kokonaisala kerrosala (< kokonaisala kerrosala)))

(defvalidator :vrk:CR324
  {:doc    "Sahko polttoaineena vaatii varusteeksi sahkon"
   :schemas ["uusiRakennus"]
   :fields [polttoaine [:lammitys :lammonlahde]
            sahko      [:varusteet :sahkoKytkin]]
   :facts {:ok   [["sähkö" true]
                  ["raskas polttoöljy" false]]
           :fail [["sähkö" false]]}}
  (and (= polttoaine "sähkö") (not= sahko true)))

(defvalidator :vrk:CR322
  {:doc     "Uuden rakennuksen kokonaisalan oltava vahintaan huoneistoala"
   :schemas ["uusiRakennus"]
   :fields  [kokonaisala  [:mitat :kokonaisala ->int]
             huoneistoala [:huoneistot ->huoneistoala]]
   :facts   {:ok   [[100 {:0 {:huoneistoala 60
                              :muutostapa   "lisays"}
                          :1 {:huoneistoala 40}}]
                    [100 {:0 {:huoneistoala 60
                              :muutostapa   "foo"}
                          :1 {:huoneistoala "40.0"}
                          :2 {:huoneistoala "19,5"
                              :muutostapa   "poisto"}}]]
             :fail [[100 {:0 {:huoneistoala 60
                              :muutostapa   "muutos"}
                          :1 {:huoneistoala 50}}]
                    [100 {:0 {:huoneistoala 60
                              :muutostapa   "poisto"}
                          :1 {:huoneistoala 50}
                          :2 {:huoneistoala "56,78"}
                          :3 {:huoneistoala "bad"}}]]}}
  (and kokonaisala huoneistoala (< kokonaisala huoneistoala)))

(defvalidator :vrk:BR113
  {:doc    "Pien- tai rivitalossa saa olla korkeintaan 3 kerrosta"
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix]
            kerrosluku      [:mitat :kerrosluku ->int]]
   :facts  {:ok   [["011 yhden asunnon talot" 3]]
            :fail [["011 yhden asunnon talot" 4]]}}
  (and (#{:011 :012 :013 :021 :022} kayttotarkoitus) (> kerrosluku 3)))

(defvalidator :vrk:BR113:rakennusluokka
  {:doc    "Pien- tai rivitalossa saa olla korkeintaan 3 kerrosta"
    :schemas ["uusiRakennus"]
    :fields [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]
             kerrosluku     [:mitat :kerrosluku ->int]]
    :facts  {:ok   [["0110 omakotitalot" 3]
                    ["0112 rivitalot" 3]]
             :fail [["0110 omakotitalot" 4]
                    ["0112 rivitalot" 4]]}}
  (and (pientalot-rl rakennusluokka) (> kerrosluku 3)))

(defvalidator :vrk:CR328:sahko
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Sahko"
   :schemas ["uusiRakennus"]
   :fields [liittyma [:verkostoliittymat :sahkoKytkin]
            varuste  [:varusteet         :sahkoKytkin]]
   :facts   {:ok   [[true true]]
             :fail [[true false]]}}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR328:viemari
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Viemari"
   :schemas rakennus-schemas
   :level :tip
   :fields [liittyma [:verkostoliittymat :viemariKytkin]
            varuste  [:varusteet         :viemariKytkin]]
   :facts  {:ok   [[true true]]
            :fail [[true false]]}}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR328:vesijohto
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Vesijohto"
   :schemas rakennus-schemas
   :level  :tip
   :fields [liittyma [:verkostoliittymat :vesijohtoKytkin]
            varuste  [:varusteet         :vesijohtoKytkin]]
   :facts   {:ok   [[true true]]
             :fail [[true false]]}}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR312
  {:doc     "Jos rakentamistoimenpide on 691 tai 111, on kerrosluvun oltava 1"
   :schemas rakennus-schemas
   :fields  [toimenpide [:kaytto :kayttotarkoitus get-digits-prefix]
             kerrosluku [:mitat :kerrosluku ->int]]
   :facts   {:ok   [["111 myymälähallit" 1]]
             :fail [["111 myymälähallit" 2]]}}
  (and (#{:691 :111} toimenpide) (not= kerrosluku 1)))

(defvalidator :vrk:CR313
  {:doc     "Jos rakentamistoimenpide on 1, niin tilavuuden on oltava 1,5 kertaa kerrosala."
   :schemas ["uusiRakennus"]
   :fields  [tilavuusRaw     [:mitat :tilavuus]  ; eliminate this validation if not a number
                                                 ;    - there will be illegal-number warning from elsewhere in the code
             tilavuus        [:mitat :tilavuus ->int]
             kerrosala       [:mitat :kerrosala ->int]]
   :facts   {:ok   [[6 6 4] ["6,5" 6 4]]
             :fail [[5 5 4]]}}
  (and (number? tilavuusRaw) tilavuus (< tilavuus (* 1.5 kerrosala))))

(defvalidator :vrk:BR407
  {:doc     "Jos rakentamistoimenpide on 1, niin tilavuuden on oltava 1,5 kertaa kokonaisala jos kayttotapaus on ..."
   :schemas ["uusiRakennus"]
   :fields  [tilavuus        [:mitat :tilavuus ->int]
             kokonaisala     [:mitat :kokonaisala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix]]
   :facts   {:ok   [[5 4 "611 voimalaitosrakennukset"]
                    [5 4 "811 navetat, sikalat, kanalat yms"]
                    [5 4 ""]
                    [6 4 "032 luhtitalot"]]
             :fail [[5 4 "032 luhtitalot"]]}}
  (and
    tilavuus
    (< tilavuus (* 1.5 kokonaisala))
    (and
      kayttotarkoitus
      (not
        (or
          (> (->int kayttotarkoitus) 799)
          (#{:162 :163 :169 :611 :613 :699 :712 :719 :722} kayttotarkoitus))))))

(defvalidator :vrk:BR407:rakennusluokka
  {:doc     "Jos rakentamistoimenpide on 1, niin tilavuuden on oltava 1,5 kertaa kokonaisala jos rakennusluokka on ..."
   :schemas ["uusiRakennus"]
   :fields  [tilavuus        [:mitat :tilavuus ->int]
             kokonaisala     [:mitat :kokonaisala ->int]
             rakennusluokka  [:kaytto :rakennusluokka get-digits-prefix]]
   :facts   {:ok   [[5 4 "1010 sähköenergian tuotantorakennukset"]
                    [5 4 "1414 hevostallit"]
                    [6 4 "0120 pienkerrostalot"]
                    [5 4 ""]]
             :fail [[5 4 "0120 pienkerrostalot"]]}}
  (and
    tilavuus
    (< tilavuus (* 1.5 kokonaisala))
    (and
      rakennusluokka
      (not
        (or
          (maatalousrakennukset-ja-muut-rakennukset (->int rakennusluokka))
          (#{:0310 :0319 :0511 :0512 :0513 :0514 :0590 :0911 :0912 :0919 :0930 :0939
             :1010 :1019 :1011 :1090 :1091 :1110 :1120 :1130
             :1210 :1211 :1212 :1213 :1214 :1215} rakennusluokka))))))

(defvalidator :vrk:CR314
  {:doc     "Asuinrakennuksessa pitaa olla lammitys"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]
             lammitystapa    [:lammitys :lammitystapa]]
   :facts   {:ok   [["032 luhtitalot" "ilmakeskus"]
                    ["011 yhden asunnon talot" "suora sähkö"]
                    ["" nil]]
             :fail [["032 luhtitalot" ei-lammitysta]
                    ["032 luhtitalot" nil]]}}
  (and
    (<= 11 kayttotarkoitus 39)
    (not (#{"vesikeskus" "ilmakeskus" "suora sähkö" "uuni"} lammitystapa))))

(defvalidator :vrk:CR314:rakennusluokka
  {:doc     "Asuinrakennuksessa pitaa olla lammitys"
   :schemas ["uusiRakennus"]
   :fields  [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]
             lammitystapa    [:lammitys :lammitystapa]]
   :facts   {:ok   [["0110 omakotitalot" "ilmakeskus"]
                    ["0112 rivitalot" "suora sähkö"]
                    ["" nil]]
             :fail [["0110 omakotitalot" ei-lammitysta]
                    ["0112 rivitalot" nil]]}}
  (and
    (asuinrakennukset-rl rakennusluokka)
    (not (#{"vesikeskus" "ilmakeskus" "suora sähkö" "uuni"} lammitystapa))))

(defvalidator :vrk:CR315
  {:doc     "Omakotitalossa pitaa olla huoneisto"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["011 yhden asunnon talot" (repeating 1)]]
             :fail [["011 yhden asunnon talot" (repeating 2)]]}}
  (and (= :011 kayttotarkoitus) (not= 1 huoneistot)))

(defvalidator :vrk:CR315:rakennusluokka
  {:doc     "Omakotitalossa pitaa olla huoneisto"
   :schemas ["uusiRakennus"]
   :fields  [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix]
             huoneistot     [:huoneistot ->count]]
   :facts   {:ok   [["0110 omakotitalot" (repeating 1)]]
             :fail [["0110 omakotitalot" (repeating 2)]]}}
  (and (= :0110 rakennusluokka) (not= 1 huoneistot)))

(defvalidator :vrk:CR316
  {:doc     "Paritalossa pitaa olla kaksi uutta huoneistoa"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["012 kahden asunnon talot" (repeating 2)]]
             :fail [["012 kahden asunnon talot" (repeating 1)]]}}
  (and (= :012 kayttotarkoitus) (not= 2 huoneistot)))

(defvalidator :vrk:CR316:rakennusluokka
  {:doc     "Paritalossa pitaa olla kaksi uutta huoneistoa"
   :schemas ["uusiRakennus"]
   :fields  [rakennusluokka  [:kaytto :rakennusluokka get-digits-prefix]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["0111 paritalot" (repeating 2)]]
             :fail [["0111 paritalot" (repeating 1)]]}}
  (and (= :0111 rakennusluokka) (not= 2 huoneistot)))

(defvalidator :vrk:CR317
  {:doc     "Rivi- tai kerrostaloissa tulee olla vahintaan kolme uutta huoneistoa"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["032 luhtitalot" (repeating 3)]]
             :fail [["032 luhtitalot" (repeating 1)]]}}
  (and (<= 13 kayttotarkoitus 39) (< huoneistot 3)))

(defvalidator :vrk:CR317:rakennusluokka
  {:doc     "Rivi- tai kerrostaloissa tulee olla vahintaan kolme uutta huoneistoa"
   :schemas ["uusiRakennus"]
   :fields  [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]
             huoneistot     [:huoneistot ->count]]
   :facts   {:ok   [["0110 omakotitalot" (repeating 1)]
                    ["0121 asuinkerrostalot" (repeating 3)]]
             :fail [["0112 rivitalot" (repeating 1)]
                    ["0121 asuinkerrostalot" (repeating 1)]]}}
  (and (<= 112 rakennusluokka 121) (< huoneistot 3)))

(defvalidator :vrk:CR319
  {:doc     "Jos rakentamistoimenpide on 1 ja kayttotarkoitus on 032 - 039, on kerrosluvun oltava vahintaan 2"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]
             kerrosluku      [:mitat :kerrosluku ->int]]
   :facts   {:ok   [["032 luhtitalot" 2]]
             :fail [["032 luhtitalot" 1]]}}
  (and (<= 32 kayttotarkoitus 39) (< kerrosluku 2)))

(defvalidator :vrk:CR319:rakennusluokka
  {:doc     "Jos rakentamistoimenpide on 1 ja rakennusluokka on 0120 tai 0121, on kerrosluvun oltava vahintaan 2"
   :schemas ["uusiRakennus"]
   :fields  [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]
             kerrosluku      [:mitat :kerrosluku ->int]]
   :facts   {:ok   [["0120 pienkerrostalot" 2]]
             :fail [["0120 pienkerrostalot" 1]]}}
  (and (<= 120 rakennusluokka 121) (< kerrosluku 2)))

(defvalidator :vrk:CR340
  {:doc     "Asuinrakennuksessa kerrosalan on oltava vahintaaan 7 neliota"
   :schemas ["uusiRakennus"]
   :fields  [kerrosala       [:mitat :kerrosala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]]
   :facts   {:ok   [[7 "032 luhtitalot"]
                    [6 "611 voimalaitosrakennukset"]]
             :fail [[6 "032 luhtitalot"]]}}
  (and (<= 11 kayttotarkoitus 39) (< kerrosala 7)))

(defvalidator :vrk:CR340:rakennusluokka
  {:doc     "Asuinrakennuksessa kerrosalan on oltava vahintaaan 7 neliota"
   :schemas ["uusiRakennus"]
   :fields  [kerrosala      [:mitat :kerrosala ->int]
             rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]]
   :facts   {:ok   [[7 "0112 rivitalot"]
                    [6 "0712 kirjastot ja arkistot"]]
             :fail [[6 "0112 rivitalot"]]}}
  (and (asuinrakennukset-rl rakennusluokka) (< kerrosala 7)))

(defvalidator :vrk:CR333:tilavuus
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia"
   :schemas ["uusiRakennus"]
   :level   :tip
   :fields  [tilavuus [:mitat :tilavuus ->int]]
   :facts   {:ok   [[10]]
             :fail [[0]]}}
  (zero? tilavuus))

(defvalidator :vrk:CR333:kerrosala
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             - Kerrosala voi olla 0, jos kayttotarkoitus on 162, 163, 169, 611, 613, 712, 719, 722 tai 941."
   :schemas rakennus-schemas
   :level   :tip
   :fields  [kerrosala       [:mitat :kerrosala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]]
   :facts   {:ok   [[9 "032 luhtitalot"]
                    [0 "611 voimalaitosrakennukset"]]
             :fail [[0 "032 luhtitalot"]]}}
  (and
    (zero? kerrosala)
    (not (#{0 162 163 169 611 613 712 719 722 941} kayttotarkoitus))))

(defvalidator :vrk:CR333:kerrosala:rakennusluokka
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             - Kerrosala voi olla 0, jos rakennusluokka on 0310, 0319, 0511, 0512, 0513, 0514, 0590, 1010,
             1019, 1011, 1090, 1091, 1110, 1120, 1130, 1210, 1211, 1212, 1213, 1214, 1215 tai 1911"
   :schemas rakennus-schemas
   :level   :tip
   :fields  [kerrosala      [:mitat :kerrosala ->int]
             rakennusluokka [:kaytto :rakennusluokka get-digits-prefix]]
   :facts   {:ok   [[9 "0111 paritalot"]
                    [0 "1010 sähköenergian tuotantorakennukset"]
                    [0 ""]]
             :fail [[0 "0111 paritalot"]]}}
  (and
    (zero? kerrosala)
    rakennusluokka
    (not (#{:0310 :0319 :0511 :0512 :0513 :0514 :0590
            :1010 :1019 :1011 :1090 :1091 :1110 :1120 :1130
            :1210 :1211 :1212 :1213 :1214 :1215 :1911} rakennusluokka))))

(defvalidator :vrk:CR333:kokonaisala
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             Kokonaisala voi olla 0 , jos kayttotarkoitus >729."
   :schemas ["uusiRakennus"]
   :level   :tip
   :fields  [kokonaisala     [:mitat :kokonaisala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]]
   :facts   {:ok   [[9 "032 luhtitalot"]
                    [0 "892 kasvihuoneet"]
                    [0 ""]]
             :fail [[0 "032 luhtitalot"]
                    [0 "729 muut palo- ja pelastustoimen rakennukset"]]}}
  (and
    (zero? kokonaisala)
    (< 0 kayttotarkoitus 730)))

(defvalidator :vrk:CR333:kokonaisala:rakennusluokka
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             Kokonaisala voi olla 0 , jos rakennusluokka > 1399."
   :schemas ["uusiRakennus"]
   :level   :tip
   :fields  [kokonaisala    [:mitat :kokonaisala ->int]
             rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]]
   :facts   {:ok   [[9 "0110 omakotitalot"]
                    [0 "1490 kasvihuoneet"]
                    [0 ""]]
             :fail [[0 "0110 omakotitalot"]
                    [0 "1319 muut pelastustoimen rakennukset"]]}}
  (and
    (zero? kokonaisala)
    (> rakennusluokka 0)
    (not (maatalousrakennukset-ja-muut-rakennukset rakennusluokka))))

(defvalidator :vrk:CR333:kerrosluku
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             Kerrosluku voi olla 0, jos kayttotarkoitus = 162, 163, 169, 611, 613, 712, 719, 722 tai >729."
   :schemas rakennus-schemas
   :level   :tip
   :fields  [kerrosluku      [:mitat :kerrosluku ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]]
   :facts   {:ok   [[9 "032 luhtitalot"]
                    [0 "892 kasvihuoneet"]
                    [0 "611 voimalaitosrakennukset"]
                    [0 ""]]
             :fail [[0 "729 muut palo- ja pelastustoimen rakennukset"]]}}
  (and
    (zero? kerrosluku)
    (not (#{0 162 163 169 611 613 712 719 722} kayttotarkoitus))
    (<= kayttotarkoitus 729)))

(defvalidator :vrk:CR333:kerrosluku:rakennusluokka
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             Kyseisellä rakennusluokalla kerrosluku ei voi olla 0"
   :schemas rakennus-schemas
   :level   :tip
   :fields  [kerrosluku      [:mitat :kerrosluku ->int]
             rakennusluokka [:kaytto :rakennusluokka get-digits-prefix ->int]]
   :facts   {:ok   [[9 "0110 omakotitalot"]
                    [0 "1490 kasvihuoneet"]]
             :fail [[0 "0110 omakotitalot"]
                    [0 "1319 muut pelastustoimen rakennukset"]]}}
  (and
    (zero? kerrosluku)
    rakennusluokka
    (not (#{:0310 :0319 :0511 :0512 :0513 :0514 :0590
            :1010 :1019 :1011 :1090 :1091 :1110 :1120 :1130
            :1210 :1211 :1212 :1213 :1214 :1215} rakennusluokka))
    (not (maatalousrakennukset-ja-muut-rakennukset rakennusluokka))))

(defvalidator :vrk:CR330
  {:doc    "Rakennus ei saa olla yli 150 metria korkea"
   :schemas ["uusiRakennus"]
   :fields [tilavuus        [:mitat :tilavuus ->int]
            kerrosala       [:mitat :kerrosala ->int]
            kerrosluku      [:mitat :kerrosluku ->int]
            kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]]
   :facts  {:ok   [[ 570 145  1 "032 luhtitalot"] ; 3.7m (sample case)
                   [ 200   0  0 "032 luhtitalot"] ; kerrosala/kerrosluku not set
                   [3000 800 40 "342 seurakuntatalot"] ; 150m
                   [3300 800 40 "611 voimalaitosrakennukset"] ; 165m
                   [3300 800 40 "931 saunarakennukset"]] ; 165m
            :fail [[3300 800 40 "342 seurakuntatalot"]]}} ; 165m
  (and
    (pos? kerrosluku)
    (pos? kerrosala)
    (not (#{0 162 163 169 722 611 613 699 712 719} kayttotarkoitus))
    (<= kayttotarkoitus 799)
    (> (/ tilavuus (/ kerrosala kerrosluku)) 150)))

(defvalidator :vrk:CR330:rakennusluokka
  {:doc    "Rakennus ei saa olla yli 150 metria korkea"
   :schemas ["uusiRakennus"]
   :fields [tilavuus        [:mitat :tilavuus ->int]
            kerrosala       [:mitat :kerrosala ->int]
            kerrosluku      [:mitat :kerrosluku ->int]
            rakennusluokka  [:kaytto :rakennusluokka get-digits-prefix]]
   :facts  {:ok   [[ 570 145  1 "0110 omakotitalot"] ; 3.7m (sample case)
                   [ 200   0  0 "0110 omakotitalot"] ; kerrosala/kerrosluku not set
                   [3000 800 40 "0741 uimahallit"] ; 150m
                   [3300 800 40 "0513 pysäköintitalot ja -hallit"] ; 165m
                   [3300 800 40 "1910 saunarakennukset"]] ; 165m
            :fail [[3300 800 40 "0741 uimahallit"]]}} ; 165m
  (and
    (pos? kerrosluku)
    (pos? kerrosala)
    rakennusluokka
    (not (#{:0310 :0319 :0511 :0512 :0513 :0514 :0590 :0911 :0912 :0919 :0930 :0939
            :1010 :1019 :1011 :1090 :1091 :1110 :1120 :1130
            :1210 :1211 :1212 :1213 :1214 :1215} rakennusluokka))
    (not (maatalousrakennukset-ja-muut-rakennukset (->int rakennusluokka)))
    (> (/ tilavuus (/ kerrosala kerrosluku)) 150)))

(defvalidator :vrk:BR319:julkisivu
  {:doc "Jos rakentamistoimenpide on 1, ovat kantavien rakenteiden rakennusaine,
         rakennuksen rakentamistapa, julkisivumateriaali ja lammitystapa pakollisia Huom!
         Kuitenkin, jos kayttotarkoitus on > 729, saavat paaasiallinen julkisivumateriaali ja lammitystapa puuttua."
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]
            julkisivu       [:rakenne :julkisivu]]
   :facts  {:ok    [["032 luhtitalot"       "tiili"]
                    ["032 luhtitalot"       "ei tiedossa"]
                    ["931 saunarakennukset" nil]]
            :fail  [["032 luhtitalot"       nil]]}}
  (and (< 0 kayttotarkoitus 730) (not julkisivu)))

(defvalidator :vrk:BR319:julkisivu:rakennusluokka
  {:doc "Jos rakentamistoimenpide on 1, ovat kantavien rakenteiden rakennusaine,
         rakennuksen rakentamistapa, julkisivumateriaali ja lammitystapa pakollisia Huom!
         Kuitenkin, jos rakennusluokka on > 1399, saavat paaasiallinen julkisivumateriaali ja lammitystapa puuttua."
   :schemas ["uusiRakennus"]
   :fields [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix]
            julkisivu      [:rakenne :julkisivu]]
   :facts  {:ok    [["0120 pienkerrostalot" "tiili"]
                    ["0121 asuinkerrostalot" "ei tiedossa"]
                    ["1910 saunarakennukset" nil]
                    ["" nil]]
            :fail  [["0120 pienkerrostalot" nil]]}}

  (and (not julkisivu)
       rakennusluokka
       (not (maatalousrakennukset-ja-muut-rakennukset (->int rakennusluokka)))))

(defvalidator :vrk:BR319:lammitystapa
  {:doc "Jos rakentamistoimenpide on 1, ovat kantavien rakenteiden rakennusaine,
         rakennuksen rakentamistapa, julkisivumateriaali ja lammitystapa pakollisia Huom!
         Kuitenkin, jos kayttotarkoitus on > 729, saavat paaasiallinen julkisivumateriaali ja lammitystapa puuttua."
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus get-digits-prefix ->int]
            lammitystapa    [:lammitys :lammitystapa]]
   :facts  {:ok    [["141 ravintolat yms."  "uuni"]
                    ["141 ravintolat yms."  ei-lammitysta]
                    ["141 ravintolat yms."  "ei tiedossa"]
                    ["931 saunarakennukset" ei-lammitysta]
                    ["931 saunarakennukset" nil]
                    ["931 saunarakennukset" " "]]
            :fail  [["141 ravintolat yms."  nil]]}}
  (and (< 0 kayttotarkoitus 729) (not lammitystapa)))

(defvalidator :vrk:BR319:lammitystapa:rakennusluokka
  {:doc "Jos rakentamistoimenpide on 1, ovat kantavien rakenteiden rakennusaine,
         rakennuksen rakentamistapa, julkisivumateriaali ja lammitystapa pakollisia Huom!
         Kuitenkin, jos rakennusluokka on > 1319, saavat paaasiallinen julkisivumateriaali ja lammitystapa puuttua."
   :schemas ["uusiRakennus"]
   :fields [rakennusluokka [:kaytto :rakennusluokka get-digits-prefix]
            lammitystapa    [:lammitys :lammitystapa]]
   :facts  {:ok    [["0330 ravintolarakennukset ja vastaavat liikerakennukset"  "uuni"]
                    ["0330 ravintolarakennukset ja vastaavat liikerakennukset"  ei-lammitysta]
                    ["0330 ravintolarakennukset ja vastaavat liikerakennukset"  "ei tiedossa"]
                    ["1910 saunarakennukset" ei-lammitysta]
                    ["1910 saunarakennukset" nil]
                    ["1910 saunarakennukset" " "]
                    ["" nil]]
            :fail  [["0330 ravintolarakennukset ja vastaavat liikerakennukset"  nil]]}}
  (and (not lammitystapa)
       rakennusluokka
       (not (maatalousrakennukset-ja-muut-rakennukset (->int rakennusluokka)))))

(defvalidator :vrk:CR343
  {:doc "Jos lammitystapa on 3 (sahkolammitys), on polttoaineen oltava 4 (sahko)"
   :schemas rakennus-schemas
   :fields [lammitystapa [:lammitys :lammitystapa]
            lammonlahde  [:lammitys :lammonlahde]]
   :facts {:ok [["suora sähkö" "sähkö"]]
           :fail [["suora sähkö" "kaasu"]]}}
  (when (= lammitystapa "suora sähkö") (not= "sähkö" lammonlahde)))

(defvalidator :vrk:CR342
  {:doc "Sahko polttoaineena vaatii sahkoliittyman"
   :schemas rakennus-schemas
   :fields [lammonlahde [:lammitys :lammonlahde]
            sahkoliittyma?  [:verkostoliittymat :sahkoKytkin]]
   :facts {:ok [["sähkö" true]]
           :fail [["sähkö" false]]}}
  (when (= lammonlahde "sähkö") (not sahkoliittyma?)))

(defvalidator :vrk:CR341
  {:doc "Sahkolammitys vaatii sahkoliittyman"
   :schemas rakennus-schemas
   :fields [lammitystapa [:lammitys :lammitystapa]
            sahkoliittyma?  [:verkostoliittymat :sahkoKytkin]]
   :facts {:ok [["suora sähkö" true]]
           :fail [["suora sähkö" false]]}}
  (when (= lammitystapa "suora sähkö") (not sahkoliittyma?)))
