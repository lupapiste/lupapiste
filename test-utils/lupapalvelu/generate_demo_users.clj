(ns lupapalvelu.generate-demo-users
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [slingshot.slingshot :refer [try+]]))


;luo testikayttajia

(defn- generate-users-for-organization [{id :id}]
  (let [username-prefix (clojure.string/lower-case (subs id 4))
        kuntano (subs id 0 3)
        email (str username-prefix "-koulutus-" )]
    (if (lupapalvelu.user/get-user-by-email (str email "20" "@" kuntano ".fi"))
      (println (str "skip " id))
      (pmap
        #(let [full-email (str email % "@" kuntano ".fi")]
           (if (lupapalvelu.user/get-user-by-email full-email)
             (println "user exits " full-email)
             (user/create-new-user
               {:role "authority"
                :orgAuthz {(keyword id) ["authorityAdmin"]}}
               {:email full-email
                :username full-email
                :role "authority"
                :firstName (str "Koulutus " %)
                :lastName (str "Kayttaja " %)
                :enabled true
                :orgAuthz {(keyword id) ["authority"]}
                :password "koulutus"}
               :send-email false)))
        (range 1 21)))))


(defn generate-users []
  (let [organizations (mongo/select :organizations)]
    (println "!!!!!!!!!!!!!!!!!!")
    (println (count organizations))
    (doall (for [o organizations]
             (when (< (count (:id o)) 7) (generate-users-for-organization o))))))


; from excel
(def kuntakoodit
  ["005"
   "009"
   "010"
   "016"
   "018"
   "019"
   "020"
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
   "090"
   "091"
   "092"
   "097"
   "098"
   "099"
   "102"
   "103"
   "105"
   "106"
   "108"
   "109"
   "111"
   "139"
   "140"
   "142"
   "143"
   "145"
   "146"
   "148"
   "149"
   "151"
   "152"
   "153"
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
   "241"
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
   "283"
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
   "309"
   "312"
   "316"
   "317"
   "318"
   "319"
   "320"
   "322"
   "398"
   "399"
   "400"
   "402"
   "403"
   "405"
   "407"
   "408"
   "410"
   "413"
   "416"
   "417"
   "418"
   "420"
   "421"
   "422"
   "423"
   "425"
   "426"
   "430"
   "433"
   "434"
   "435"
   "436"
   "438"
   "440"
   "441"
   "442"
   "444"
   "445"
   "475"
   "476"
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
   "507"
   "508"
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
   "576"
   "577"
   "578"
   "580"
   "581"
   "583"
   "584"
   "588"
   "592"
   "593"
   "595"
   "598"
   "599"
   "601"
   "604"
   "607"
   "608"
   "609"
   "611"
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
   "638"
   "678"
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
   "710"
   "729"
   "732"
   "734"
   "736"
   "738"
   "739"
   "740"
   "742"
   "743"
   "746"
   "747"
   "748"
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
   "785"
   "790"
   "791"
   "831"
   "832"
   "833"
   "834"
   "837"
   "838"
   "844"
   "845"
   "846"
   "848"
   "849"
   "850"
   "851"
   "853"
   "854"
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
   "905"
   "908"
   "911"
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
   "992"])

(defn ymp-org [kuntano base-scope]
  (let [org-id (str kuntano "-YMP")]
    {:_id org-id
     :name {:fi (str (lupapalvelu.i18n/localize "fi" (str "municipality." kuntano)) " ymp\u00e4rist\u00f6toimi")
            :sv (str (lupapalvelu.i18n/localize "sv" (str "municipality." kuntano)) " ymp\u00e4rist\u00f6toimi")}
     :scope [(merge base-scope {:municipality kuntano :permitType "YI"})
             (merge base-scope {:municipality kuntano :permitType "YL"})
             (merge base-scope {:municipality kuntano :permitType "VVVL"})
             (merge base-scope {:municipality kuntano :permitType "MAL"})]
     :links []}))

(defn generate-ymp! []
  (doseq [kuntano kuntakoodit]
    (let [org-id (str kuntano "-YMP")
          email  (str "ymp-admin@" kuntano ".fi")
          org (assoc (ymp-org kuntano {:inforequest-enabled true :new-application-enabled true})
                :krysp {:YI {:url "http://localhost:8000/dev/krysp" :version "2.1.1" :ftpUser nil}
                        :YL {:url "http://localhost:8000/dev/krysp" :version "2.1.1" :ftpUser nil}
                        :VVVL {:url "http://localhost:8000/dev/krysp" :version "2.1.1" :ftpUser nil}
                        :MAL {:url "http://localhost:8000/dev/krysp" :version "2.1.1" :ftpUser nil}})]
      (mongo/insert :organizations org)
      (user/create-new-user
        {:role "admin"}
        {:email email
         :username email
         :role "authority"
         :firstName (str "Ymp\u00e4rist\u00f6toimi")
         :lastName (str "P\u00e4\u00e4k\u00e4ytt\u00e4j\u00e4 " kuntano)
         :enabled true
         :orgAuthz {(keyword org-id) ["authorityAdmin"]}
         :password "koulutus"}
        :send-email false)
      (generate-users-for-organization {:id org-id}))))

(defn generate-applicants! []
  (doseq [kuntano kuntakoodit
          i (range 1 (inc 25))]
    (let [full-email (str "hakija-" i "@" kuntano ".fi")]
      (try+
        (let [user (user/create-new-user nil
                     {:email full-email
                      :username full-email
                      :role "applicant"
                      :firstName (str "Koulutus " kuntano)
                      :lastName (str "Hakija " i)
                      :enabled false
                      :password "koulutus"})]
          (mongo/update-by-id :users (:id user) {$set {:enabled true}}))
        (catch [:sade.core/type :sade.core/fail] {:keys [text desc] :as all}
          (println text (or desc "")))))))
