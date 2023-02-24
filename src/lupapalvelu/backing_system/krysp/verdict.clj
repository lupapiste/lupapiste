(ns lupapalvelu.backing-system.krysp.verdict
  (:require [lupapalvelu.pate.schema-helper :as helper]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn verdict-id
  "Numerical status string that corresponds to `kuntagml-code`. Resolution is
  case-insensitive. For example, 'puollettu' -> '16'. Nil if no match is found."
  [kuntagml-code]
  (some-> (case (some-> kuntagml-code ss/trim ss/lower-case)
            "myönnetty"                                                               1
            "hyväksytty"                                                              2
            "osittain myönnetty"                                                      3
            ("pysytti osittain myönnettynä (luvat)"
             "pysytti osittain myönnettynä")                                          4
            "myönnetty aloitusoikeudella"                                             5
            "ehdollinen"                                                              6
            ("ei tutkittu (oik.vaatimus/lupa pysyy puollettuna)"
             "ei tutkittu (oikaisuvaatimusvaatimus tai lupa pysyy puollettuna)")      7
            "työhön liittyy ehto"                                                     8
            "tehty hallintopakkopäätös (asetettu velvoite)"                           9
            "pysytti määräyksen"                                                      10
            ("annettu lausunto (rlk)"
             "annettu lausunto")                                                      11
            ("valituks.luovuttu (oik.vaatimus/lupa pysyy puoll.)"
             "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)") 12
            ("muutti myönnetyksi (luvat/vak/rak.v/rasite/mak/ym)"
             "muutti myönnetyksi")                                                    13
            ("pysytti määräyksen/päätöksen (määr/kats.p/loppuk)"
             "pysytti määräyksen tai päätöksen")                                      14
            ("pysytti myönnettynä (luvat/vak/rak.v/rasite/mak/ym"
             "pysytti myönnettynä")                                                   15
            "puollettu"                                                               16
            "annettu lausunto (ent. selitys)"                                         17
            "siirretty maaoikeudelle"                                                 18
            "tehty uhkasakkopäätös"                                                   19
            "suunnitelmat tarkastettu"                                                20
            "evätty"                                                                  21
            ("ei tutkittu (oik.vaatimus/lupa pysyy evättynä)"
             "ei tutkittu (oikaisuvaatimus tai lupa pysyy evättynä)")                 22
            "tehty hallintopakkopäätös (ei velvoitetta)"                              23
            ("määräys peruutettu (määräys/kats.p/loppukats.p.)"
             "määräys peruutettu")                                                    24
            ("valituks.luovuttu (oik.vaatimus/lupa pysyy evätt.)"
             "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy evättynä)")    25
            ("muutti evätyksi (luvat/vak/rak.v/rasite/mak/ym)"
             "muutti evätyksi")                                                       26
            ("muutti määräystä/päätöstä (määr/kats.p/loppuk)"
             "muutti määräystä tai päätöstä")                                         27
            ("pysytti evättynä (luvat/vak/rak.v/rasite/mak/ym)"
             "pysytti evättynä")                                                      28
            "ei puollettu"                                                            29
            "muutettu toimenpideluvaksi (konversio)"                                  30
            "ei lausuntoa"                                                            31
            "ei tutkittu"                                                             32
            "lausunto"                                                                33
            "lausunto/päätös (muu kuin rlk)"                                          34
            ("hallintopakko/uhkasakkoasian käsittely lopetettu."
             "hallintopakon tai uhkasakkoasian käsittely lopetettu")                  35
            ("ltk palauttanut asian uudelleen valmisteltavaksi."
             "asia palautettu uudelleen valmisteltavaksi")                            36
            "peruutettu"                                                              37
            "asiakirjat palautettu korjauskehotuksin"                                 38
            ("ltk poistanut asian esityslistalta."
             "asia poistettu esityslistalta")                                         39
            ("ltk:n kokouksessa pöydälle pantu asia."
             "asia pantu pöydälle kokouksessa")                                       40
            "ilmoitus merkitty tiedoksi"                                              41
            "ei tiedossa"                                                             42
            ;; KT verdicts
            "kiinteistötoimitus"                                                      43
            "kiinteistörekisterin pitäjän päätös"                                     44
            nil)
          str))

(def ^:private status-code-map
  "Yhteiset code map extended with KT codes"
  (assoc helper/verdict-code-map
         :kiinteistotoimitus "Kiinteistötoimitus"
         :kiinteistorekisteri-paatos "Kiinteistörekisterin pitäjän päätös"))

(defn verdict-name
  "Backing system status (or legacy verdict code) number to KuntaGML. `code` can be number,
  string or keyword and it will be converted to integer. Nil of the `code` is not
  supported."
  [code]
  (get status-code-map
       (case (util/pcond-> code
               keyword? name
               string? (some-> ss/trim util/->int))
         1             :myonnetty
         2             :hyvaksytty
         3             :osittain-myonnetty
         4             :pysytti-osittain
         5             :myonnetty-aloitusoikeudella
         6             :ehdollinen
         7             :ei-tutkittu-2 8             :tyohon-ehto
         9             :tehty-hallintopakkopaatos-2
         (10 14)       :pysytti-maarayksen-2
         (11 17 33 34) :annettu-lausunto
         12            :valituksesta-luovuttu-1
         13            :muutti-myonnetyksi
         15            :pysytti-myonnettyna
         16            :puollettu
         18            :siirretty-maaoikeudelle
         19            :tehty-uhkasakkopaatos
         20            :suunnitelmat-tarkastettu
         21            :evatty
         22            :ei-tutkittu-3
         23            :tehty-hallintopakkopaatos-1
         24            :maarays-peruutettu
         25            :valituksesta-luovuttu-2
         26            :muutti-evatyksi
         27            :muutti-maaraysta
         28            :pysytti-evattyna
         29            :ei-puollettu
         30            :konversio
         31            :ei-lausuntoa
         32            :ei-tutkittu-1
         35            :hallintopakko
         36            :lautakunta-palauttanut
         37            :peruutettu
         38            :asiakirjat-palautettu
         39            :lautakunta-poistanut
         40            :lautakunta-poydalle
         41            :ilmoitus-tiedoksi
         42            :ei-tiedossa
         43            :kiinteistotoimitus
         44            :kiinteistorekisteri-paatos
         nil)))
