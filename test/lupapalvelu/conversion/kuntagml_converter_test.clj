(ns lupapalvelu.conversion.kuntagml-converter-test
  (:require [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.conversion-test-util :refer [->xml]]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.kuntagml-converter :as conv-k]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.document.tools :refer [unwrapped]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(testable-privates lupapalvelu.conversion.kuntagml-converter
                   make-r-application-skeleton)

(def base-xml
  (-> {:kuntagml
       {:xml (slurp (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))}}
      conv-doc/read-xml
      (enlive/at [:toimenpidetieto] nil)))

(defn make-xml [backend-id description & operations]
  (enlive/at base-xml
             [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus] (enlive/content backend-id)
             [:rakennusvalvontaasianKuvaus] (enlive/content description)
             [:referenssiPiste] (enlive/append operations)))

(fact "->xml"
  (->xml {:hello "world"
          :foo   [{:bar 1 :baz "10"} {:bar 2 :baz "20"}]
          :one   {:two {:three [{:four "five"}]}}})
  => [{:tag :hello :content ["world"]}
      {:tag :foo :content [{:tag :bar :content ["1"]}
                           {:tag :baz :content ["10"]}]}
      {:tag :foo :content [{:tag :bar :content ["2"]}
                           {:tag :baz :content ["20"]}]}
      {:tag :one :content [{:tag     :two
                            :content [{:tag     :three
                                       :content [{:tag     :four
                                                  :content ["five"]}]}]}]}])

(defn make-op [m]
  (->xml {:toimenpidetieto {:Toimenpide m}}))

(def backend-id "14-0241-R 3")

(defn make-skeleton
  ([xml backend-id]
   (let [{:keys [rakennuspaikka]
          :as   ai} (krysp-reader/get-app-info-from-message xml backend-id nil)]
     (make-r-application-skeleton {:organization   (util/find-by-id "753-R" (minimal/organizations))
                                   :municipality   "753"
                                   :created        12345678
                                   :user           (usr/batchrun-user ["753-R"])
                                   :conversion-doc {:LP-id      "LP-753-2023-00123"
                                                    :backend-id backend-id
                                                    :suffix     (conv-sc/backend-id->suffix backend-id)}}
                                  ai
                                  xml
                                  rakennuspaikka)))
  ([xml]
   (make-skeleton xml backend-id)))

(defn make-building [usecase address surface]
  {:rakennustieto
   {:Rakennus
    {:rakennuksenTiedot
     {:kayttotarkoitus usecase
      :osoite          {:osoitenimi {:teksti address}}
      :julkisivu       {:julkisivumateriaali surface}}}}})

(defn make-structure [usecase area description n]
  {:rakennelmatieto
   {:Rakennelma
    {:kayttotarkoitus usecase
     :kokonaisala area
     :kuvaus {:kuvaus description}
     :tunnus {:rakennusnro n}}}})

(defn op-and-doc [operations documents op-name]
  (let [op  (util/find-by-key :name op-name operations)
        doc (util/find-first (util/fn-> :schema-info :op :id (= (:id op)))
                             documents)]
    [op doc]))

(defn check-primary-op [{:keys [primaryOperation documents]} op-name description]
  (let [[op doc] (op-and-doc [primaryOperation] documents op-name)]
    (fact {:midje/description (str "Primary operation:" op-name)}
      op => truthy
      doc => truthy
      (some-> doc :schema-info :op :id) => (:id op)
      (:description op) => description)
    [op (unwrapped doc)]))

(defn check-secondary-op [{:keys [secondaryOperations documents]} op-name]
  (let [[op doc] (op-and-doc secondaryOperations documents op-name)]
    (fact {:midje/description (str "Secondary operation:" op-name)}
      op => truthy
      doc => truthy
      (some-> doc :schema-info :op :id) => (:id op))
    [op (unwrapped doc)]))

(env/with-feature-value :disable-ktj-on-create true
  (facts "make-r-application-skeleton"
    (fact "Purkaminen without operations"
      (-> (make-xml "16-0753-P" "Saunan purkaminen")
          (make-skeleton "16-0753-P")
          (check-primary-op "purkaminen" "Saunan purkaminen")))
    (fact "Purkaminen with operation"
      (let [[_ doc] (-> (make-xml backend-id "Wrecking ball"
                                  (make-op {:purkaminen {:kuvaus        "Hello"
                                                         :purkamisenSyy "Boredom"
                                                         :poistumaPvm   "2023-11-14Z"}}))
                        make-skeleton
                        (check-primary-op "purkaminen" "Wrecking ball"))]
        (:data doc) => (contains {:poistumanAjankohta "14.11.2023"
                                  :poistumanSyy       "Boredom"})))
    (fact "Purkaminen with operation and house"
      (let [[_ doc] (-> (make-xml backend-id "Wrecking ball"
                                  (make-op (merge {:purkaminen {:kuvaus        "Hello"
                                                                :purkamisenSyy "Boredom"
                                                                :poistumaPvm   "2023-11-14Z"}}
                                                  (make-building "712 kauppavarastot"
                                                                 "Somewhere 17"
                                                                 "tiili"))))
                        make-skeleton
                        (check-primary-op "purkaminen" "Wrecking ball"))]
        (:data doc) => (contains {:poistumanAjankohta "14.11.2023"
                                  :poistumanSyy       "Boredom"
                                  :kaytto             (contains {:kayttotarkoitus "712 kauppavarastot"})
                                  :osoite             (contains {:lahiosoite "Somewhere 17"})
                                  :rakenne            (contains {:julkisivu "tiili"})})))
    (fact "Laajentaminen"
      (let [[_ doc] (-> (make-xml backend-id "West Wing"
                                  (make-op (merge {:laajennus
                                                   {:laajennuksentiedot
                                                    {:tilavuus                       3000
                                                     :kerrosala                      200
                                                     :rakennusoikeudellinenKerrosala 100
                                                     :kokonaisala                    1200
                                                     :huoneistoala
                                                     {:pintaAla             567
                                                      :kayttotarkoitusKoodi "opetustilaa"}}
                                                    :perusparannusKytkin true}}
                                                  (make-building "041 vapaa-ajan asuinrakennukset"
                                                                 "Elsewhere 18"
                                                                 "puu"))))
                        make-skeleton
                        (check-primary-op "vapaa-ajan-rakennus-laaj" "West Wing"))]
        (:data doc) => (contains {:laajennuksen-tiedot
                                  {:mitat               {:tilavuus                       "3000"
                                                         :kerrosala                      "200"
                                                         :rakennusoikeusValinta          "kerrosala"
                                                         :rakennusoikeudellinenKerrosala "100"
                                                         :rakennusoikeusKuutiometreina   ""
                                                         :harjanKorkeusrakennusalalla    ""
                                                         :kokonaisala                    "1200"
                                                         :huoneistoala
                                                         {:0 {:pintaAla             "567"
                                                              :kayttotarkoitusKoodi "opetustilaa"}}}
                                   :perusparannuskytkin true}
                                  :kaytto  (contains {:kayttotarkoitus "041 vapaa-ajan asuinrakennukset"})
                                  :osoite  (contains {:lahiosoite "Elsewhere 18"})
                                  :rakenne (contains {:julkisivu "puu"})})))

    (fact "Laajentaminen with multiple huoneistoala"
      (let [[_ doc] (-> (make-xml backend-id "Beer Garden"
                                  (make-op (merge {:laajennus
                                                   {:laajennuksentiedot
                                                    {:tilavuus                       3000
                                                     :kerrosala                      200
                                                     :rakennusoikeudellinenKerrosala 100
                                                     :kokonaisala                    1200
                                                     :huoneistoala
                                                     [{:pintaAla             567
                                                       :kayttotarkoitusKoodi "opetustilaa"}
                                                      {:pintaAla             678
                                                       :kayttotarkoitusKoodi "myymälä, majoitus- ja ravitsemustilaa"}
                                                      {:pintaAla             789
                                                       :kayttotarkoitusKoodi "muuta huoneistoalaan kuuluvaa tilaa"}]}}}
                                                  (make-building "892 kasvihuoneet"
                                                                 "Veggie Vägen 10"
                                                                 "puu"))))
                        make-skeleton
                        (check-primary-op "muu-rakennus-laaj" "Beer Garden"))]
        (:data doc) => (contains {:laajennuksen-tiedot
                                  {:mitat               {:tilavuus                       "3000"
                                                         :kerrosala                      "200"
                                                         :rakennusoikeusValinta          "kerrosala"
                                                         :rakennusoikeudellinenKerrosala "100"
                                                         :rakennusoikeusKuutiometreina   ""
                                                         :harjanKorkeusrakennusalalla    ""
                                                         :kokonaisala                    "1200"
                                                         :huoneistoala
                                                         {:0 {:pintaAla             "567"
                                                              :kayttotarkoitusKoodi "opetustilaa"}
                                                          :1 {:pintaAla             "678"
                                                              :kayttotarkoitusKoodi "myymälä, majoitus- ja ravitsemustilaa"}
                                                          :2 {:pintaAla             "789"
                                                              :kayttotarkoitusKoodi "muuta huoneistoalaan kuuluvaa tilaa"}}}
                                   :perusparannuskytkin false}
                                  :kaytto  (contains {:kayttotarkoitus "892 kasvihuoneet"})
                                  :osoite  (contains {:lahiosoite "Veggie Vägen 10"})
                                  :rakenne (contains {:julkisivu "puu"})})))

    (fact "Structure: aita"
      (let [[_ doc] (-> (make-xml backend-id "On the fence"
                                  (make-op (make-structure "Aita"
                                                           100
                                                           "Picket Fences"
                                                           123)))
                        make-skeleton
                        (check-primary-op "aita" "On the fence"))]
        (-> doc :schema-info :name) => "kaupunkikuvatoimenpide-ei-tunnusta"
        (:data doc) => (contains {:kayttotarkoitus "Aita"
                                  :kokonaisala     "100"
                                  :kuvaus          "Picket Fences"})))

    (fact "House, structure and without"
      (let [skeleton (-> (make-xml backend-id "Julkisivun ja katon korjaaminen"
                                   (make-op (merge {:muuMuutosTyo
                                                    {:muutostyonLaji                 "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt"
                                                     :rakennustietojaEimuutetaKytkin true
                                                     :perusparannusKytkin            true}}
                                                   (make-building "021 rivitalot"
                                                                  "Veggie Vägen 10"
                                                                  "puu")))
                                   (make-op (make-structure "Savupiippu" 100 "Smoking hot" 123))
                                   (make-op {}))
                         make-skeleton)]
        (-> (check-primary-op skeleton "julkisivu-muutos"  "Julkisivun ja katon korjaaminen")
            second :data)
        => (contains {;; Overridden by operation schema-data
                      :muutostyolaji                  "muut muutostyöt"
                      :rakennustietojaEimuutetaKytkin true
                      :perusparannuskytkin            true
                      :kaytto                         (contains {:kayttotarkoitus "021 rivitalot"})
                      :osoite                         (contains {:lahiosoite "Veggie Vägen 10"})
                      :rakenne                        (contains {:julkisivu "puu"})})
        (-> (check-secondary-op skeleton "masto-tms") second :data)
        => (contains {:kokonaisala     "100"
                      :kayttotarkoitus "Savupiippu"
                      :kuvaus          "Smoking hot"
                      :tunnus          "123"})
        (-> (check-secondary-op skeleton "konversio") second :schema-info :name)
        => "hankkeen-kuvaus"))))

(def runeberg (date/timestamp "5.2.2024"))
(def mayday   (date/timestamp "1.5.2024"))
(def oldie "10.6.1006") ; Too ancient for a Timestamp

(facts "foreman-termination"
  (facts "nothing"
    (doseq [args [[nil] [nil nil] [""] ["  " nil] [{}]
                  [{:hello "world"}] [{:started "  " :ended nil}]]]
      (fact {:midje/description (str args)}
        (apply conv-k/foreman-termination args) => nil)))

  (facts "started"
    (doseq [args [[runeberg nil] [(date/iso-date runeberg) "  "]
                  [{:alkamisPvm (date/finnish-date runeberg)}]
                  [(str " " (date/xml-date runeberg) " ") oldie]]]
      (fact {:midje/description (str args)}
        (apply conv-k/foreman-termination args) => {:started runeberg})))

  (facts "ended"
    (doseq [args [[nil runeberg] ["  " (date/iso-date runeberg)]
                  [{:paattymisPvm (date/finnish-date runeberg)}]
                  [oldie (str " " (date/xml-date runeberg) " ")]]]
      (fact {:midje/description (str args)}
        (apply conv-k/foreman-termination args) => {:ended runeberg})))

  (facts "sanity check"
    (doseq [args [[mayday runeberg] [(date/finnish-date mayday) (date/iso-date runeberg)]
                  [{:paattymisPvm (date/finnish-date runeberg)
                    :alkamisPvm   mayday}]]]
      (fact {:midje/description (str args)}
        (apply conv-k/foreman-termination args) => nil)))

  (facts "both ok"
    (doseq [args [[runeberg mayday] [(date/iso-date runeberg) (date/finnish-date mayday)]
                  [{:alkamisPvm   (date/finnish-date runeberg)
                    :paattymisPvm mayday}]]]
      (fact {:midje/description (str args)}
        (apply conv-k/foreman-termination args) => {:started runeberg
                                                    :ended   mayday}))))
