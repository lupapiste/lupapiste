 (ns lupapalvelu.merge-review-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [clojure.data :refer [diff]]
             [lupapalvelu.verdict :refer :all]
             [lupapalvelu.tasks :as tasks]
             [sade.util :as util]))

(testable-privates lupapalvelu.verdict merge-review-tasks)

(def rakennustieto-fixture [{:KatselmuksenRakennus {:kiinttun "54300601900001",
                                                    :rakennusnro "001",
                                                    :jarjestysnumero "1",
                                                    :aanestysalue "010",
                                                    :muuTunnustieto {:MuuTunnus {:sovellus "LP-543-2016-94999",
                                                                                 :tunnus "LP-543-2016-94999"}},
                                                    :valtakunnallinenNumero "103571943D",
                                                    :rakennuksenSelite "Omakotitalo"}}] )

(def reviews-fixture
  (map #(tasks/katselmus->task {:state :sent} {:type "background"} {:buildings nil} (assoc % :katselmuksenRakennustieto rakennustieto-fixture))
       [{:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "rakennuksen paikan merkitseminen",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Paikan merkitseminen",
         :pitoPvm 1462838400000}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "rakennuksen paikan tarkastaminen",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Sijaintikatselmus",
         :pitoPvm 1464739200000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "aloituskokous",
         :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "pohjakatselmus",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Pohjakatselmus",
         :pitoPvm 1463961600000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "rakennekatselmus",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Rakennekatselmus",
         :pitoPvm 1466467200000}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "Oli kesken."}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Vesi- ja viem\u00e4rilaitteiden katselmus",
         :pitoPvm 1476748800000}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "Ulkoviem\u00e4rit ok."}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Vesi- ja viem\u00e4rilaitteiden katselmus",
         :pitoPvm 1463011200000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "osittainen loppukatselmus",
         :huomautukset [{:huomautus {:kuvaus "kiukaan kaide"}}],
         :osittainen "osittainen",
         :tarkastuksenTaiKatselmuksenNimi "K\u00e4ytt\u00f6\u00f6nottokatselmus",
         :pitoPvm 1477526400000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "loppukatselmus",
         :osittainen "lopullinen",
         :tarkastuksenTaiKatselmuksenNimi "Loppukatselmus"}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "Rakennusty\u00f6n aikana on pidett\u00e4v\u00e4 rakennusty\u00f6n tarkastusasiakirjaa sek\u00e4 laadittava rakennuksen k\u00e4ytt\u00f6- ja huolto-ohje"}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta"}
        {:pitaja "E",
         :vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "S\u00e4hk\u00f6tarkastusp\u00f6yt\u00e4kirja esitett\u00e4v\u00e4 rakennusvalvontaviranomaiselle ennen k\u00e4ytt\u00f6\u00f6nottoa"}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta"}
        {:pitaja "T",
         :vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "p\u00e4ivitetty energiaselvitys"}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta",
         :pitoPvm 1463097600000}]))

(fact "merging the review vector with itself leaves everything unchanged"
  (let [[unchanged added-or-updated] (merge-review-tasks reviews-fixture reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "review is updated on top of empty review"
  (let [mutated (-> (into [] reviews-fixture)
                    (assoc-in [2 :data :katselmus :pitoPvm :value] "13.05.2016"))
        [unchanged added-or-updated] (merge-review-tasks mutated reviews-fixture)]
    unchanged => (just (util/drop-nth 2 mutated) :in-any-order)
    added-or-updated => (just (nth mutated 2))))

(fact "illegal review type causes failure"
      (let [mutated (-> (into [] reviews-fixture)
                        (assoc-in [2 :data :katselmus :pitoPvm :value] "2")
                        (assoc-in [2 :data :katselmuksenLaji :value] "13.05.2016"))
            [_ added-or-updated] (merge-review-tasks mutated reviews-fixture)
            errors (doall (map #(tasks/task-doc-validation (-> % :schema-info :name) %) added-or-updated))]
        added-or-updated => (just (nth mutated 2))
        (count errors) => 1
        (-> errors flatten first) => (contains {:result [:warn "illegal-value:select"]})))