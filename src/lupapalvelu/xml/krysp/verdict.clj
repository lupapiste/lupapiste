(ns lupapalvelu.xml.krysp.verdict)

(def ^:private verdict-map {:1 "myönnetty"
                            :2 "hyväksytty"
                            :3 "osittain myönnetty"
                            :4 "pysytti osittain myönnettynä (luvat)"
                            :5 "Myönnetty aloitusoikeudella"
                            :6 "ehdollinen"
                            :7 "ei tutkittu (oik.vaatimus/lupa pysyy puollettuna)"
                            :8 "työhön liittyy ehto"
                            :9 "tehty hallintopakkopäätös (asetettu velvoite)"
                            :10 "pysytti määräyksen"
                            :11 "annettu lausunto (rlk)"
                            :12 "valituks.luovuttu (oik.vaatimus/lupa pysyy puoll.)"
                            :13 "muutti myönnetyksi (luvat/vak/rak.v/rasite/mak/ym)"
                            :14 "pysytti määräyksen/päätöksen (määr/kats.p/loppuk)"
                            :15 "pysytti myönnettynä (luvat/vak/rak.v/rasite/mak/ym"
                            :16 "puollettu"
                            :17 "annettu lausunto (ent. selitys)"
                            :18 "siirretty maaoikeudelle"
                            :19 "tehty uhkasakkopäätös"
                            :20 "suunnitelmat tarkastettu"
                            :21 "evätty"
                            :22 "ei tutkittu (oik.vaatimus/lupa pysyy evättynä)"
                            :23 "tehty hallintopakkopäätös (ei velvoitetta)"
                            :24 "määräys peruutettu (määräys/kats.p/loppukats.p.)"
                            :25 "valituks.luovuttu (oik.vaatimus/lupa pysyy evätt.)"
                            :26 "muutti evätyksi (luvat/vak/rak.v/rasite/mak/ym)"
                            :27 "muutti määräystä/päätöstä (määr/kats.p/loppuk)"
                            :28 "pysytti evättynä (luvat/vak/rak.v/rasite/mak/ym)"
                            :29 "ei puollettu"
                            :30 "muutettu toimenpideluvaksi (konverssio)"
                            :31 "ei lausuntoa"
                            :32 "ei tutkittu"
                            :33 "lausunto"
                            :34 "lausunto/päätös (muu kuin rlk)"
                            :35 "Hallintopakko/uhkasakkoasian käsittely lopetettu."
                            :36 "Ltk palauttanut asian uudelleen valmisteltavaksi."
                            :37 "Peruutettu"
                            :38 "asiakirjat palautettu korjauskehotuksin"
                            :39 "Ltk poistanut asian esityslistalta."
                            :40 "Ltk:n kokouksessa pöydälle pantu asia."
                            :41 "ilmoitus merkitty tiedoksi"
                            :42 "ei tiedossa"})

(defn verdict-id [name]
  (some (fn [[k v]] (when (= v name) (name k))) verdict-map))

(defn verdict-name [id]
  (verdict-map (-> id str keyword)))

