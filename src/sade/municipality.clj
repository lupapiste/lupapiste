(ns sade.municipality)


(def municipality-mapping
    "Ajantasaiset kuntaliitokset"
    {"476" "297" ; Maaninka -> Kuopio (2015)
     "413" "609" ; Lavia -> Pori (2015)
     "838" "423" ; Tarvasjoki -> Lieto (2015)
     "319" "783" ; Koylio -> Sakyla (2016)
     "283" "098" ; Hameenkoski -> Hollola (2016)
     "532" "398" ; Nastola -> Lahti (2016)
     "164" "301" ; Jalasjarvi -> Kurikka (2016)
     "174" "297" ; Juankoski -> Kuopio (2017)
     "442" "051" ; Luvia -> Eurajoki (2017)
     })

(def municipality-codes-2015
     "Kunnat 2015"
     #{"020"
       "005"
       "009"
       "010"
       "016"
       "018"
       "019"
       "035"
       "043"
       "046"
       "047"
       "049"
       "050"
       "051"
       "052"
       "060"
       "061"
       "062"
       "065"
       "069"
       "071"
       "072"
       "074"
       "075"
       "076"
       "077"
       "078"
       "079"
       "081"
       "082"
       "086"
       "111"
       "090"
       "091"
       "097"
       "098"
       "099"
       "102"
       "103"
       "105"
       "106"
       "283"
       "108"
       "109"
       "139"
       "140"
       "142"
       "143"
       "145"
       "146"
       "153"
       "148"
       "149"
       "151"
       "152"
       "164"
       "165"
       "167"
       "169"
       "170"
       "171"
       "172"
       "174"
       "176"
       "177"
       "178"
       "179"
       "181"
       "182"
       "186"
       "202"
       "204"
       "205"
       "208"
       "211"
       "213"
       "214"
       "216"
       "217"
       "218"
       "224"
       "226"
       "230"
       "231"
       "232"
       "233"
       "235"
       "236"
       "239"
       "240"
       "320"
       "241"
       "322"
       "244"
       "245"
       "249"
       "250"
       "256"
       "257"
       "260"
       "261"
       "263"
       "265"
       "271"
       "272"
       "273"
       "275"
       "276"
       "280"
       "284"
       "285"
       "286"
       "287"
       "288"
       "290"
       "291"
       "295"
       "297"
       "300"
       "301"
       "304"
       "305"
       "312"
       "316"
       "317"
       "318"
       "319"
       "398"
       "399"
       "400"
       "407"
       "402"
       "403"
       "405"
       "408"
       "410"
       "416"
       "417"
       "418"
       "420"
       "421"
       "422"
       "423"
       "425"
       "426"
       "444"
       "430"
       "433"
       "434"
       "435"
       "436"
       "438"
       "440"
       "441"
       "442"
       "475"
       "478"
       "480"
       "481"
       "483"
       "484"
       "489"
       "491"
       "494"
       "495"
       "498"
       "499"
       "500"
       "503"
       "504"
       "505"
       "508"
       "507"
       "529"
       "531"
       "532"
       "535"
       "536"
       "538"
       "541"
       "543"
       "545"
       "560"
       "561"
       "562"
       "563"
       "564"
       "309"
       "576"
       "577"
       "578"
       "445"
       "580"
       "581"
       "599"
       "583"
       "854"
       "584"
       "588"
       "592"
       "593"
       "595"
       "598"
       "601"
       "604"
       "607"
       "608"
       "609"
       "611"
       "638"
       "614"
       "615"
       "616"
       "619"
       "620"
       "623"
       "624"
       "625"
       "626"
       "630"
       "631"
       "635"
       "636"
       "678"
       "710"
       "680"
       "681"
       "683"
       "684"
       "686"
       "687"
       "689"
       "691"
       "694"
       "697"
       "698"
       "700"
       "702"
       "704"
       "707"
       "729"
       "732"
       "734"
       "736"
       "790"
       "738"
       "739"
       "740"
       "742"
       "743"
       "746"
       "747"
       "748"
       "791"
       "749"
       "751"
       "753"
       "755"
       "758"
       "759"
       "761"
       "762"
       "765"
       "766"
       "768"
       "771"
       "777"
       "778"
       "781"
       "783"
       "831"
       "832"
       "833"
       "834"
       "837"
       "844"
       "845"
       "846"
       "848"
       "849"
       "850"
       "851"
       "853"
       "857"
       "858"
       "859"
       "886"
       "887"
       "889"
       "890"
       "892"
       "893"
       "895"
       "785"
       "905"
       "908"
       "911"
       "092"
       "915"
       "918"
       "921"
       "922"
       "924"
       "925"
       "927"
       "931"
       "934"
       "935"
       "936"
       "941"
       "946"
       "976"
       "977"
       "980"
       "981"
       "989"
       "992"})

(def municipality-codes-2016
  (disj municipality-codes-2015 "319" "283" "532" "164"))

(def municipality-codes-2017
  (disj municipality-codes-2016 "174" "442"))

(def municipality-codes
     "Ajantasaiset kuntanumerot"
     municipality-codes-2017)

(defn resolve-municipality [municipality]
  (municipality-codes (get municipality-mapping municipality municipality)))
