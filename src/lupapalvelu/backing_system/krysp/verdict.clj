(ns lupapalvelu.backing-system.krysp.verdict
  (:require [sade.core :refer :all]))

; TODO Enumeration has changed in new KRYSP. Fuck. Sync with ui and prod data
(def- verdict-map {:1 "my\u00f6nnetty"
                   :2 "hyv\u00e4ksytty"
                   :3 "osittain my\u00f6nnetty"
                   :4 "pysytti osittain my\u00f6nnettyn\u00e4 (luvat)"
                   :5 "My\u00f6nnetty aloitusoikeudella"
                   :6 "ehdollinen"
                   :7 "ei tutkittu (oik.vaatimus/lupa pysyy puollettuna)"
                   :8 "ty\u00f6h\u00f6n liittyy ehto"
                   :9 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (asetettu velvoite)"
                   :10 "pysytti m\u00e4\u00e4r\u00e4yksen"
                   :11 "annettu lausunto (rlk)"
                   :12 "valituks.luovuttu (oik.vaatimus/lupa pysyy puoll.)"
                   :13 "muutti my\u00f6nnetyksi (luvat/vak/rak.v/rasite/mak/ym)"
                   :14 "pysytti m\u00e4\u00e4r\u00e4yksen/p\u00e4\u00e4t\u00f6ksen (m\u00e4\u00e4r/kats.p/loppuk)"
                   :15 "pysytti my\u00f6nnettyn\u00e4 (luvat/vak/rak.v/rasite/mak/ym"
                   :16 "puollettu"
                   :17 "annettu lausunto (ent. selitys)"
                   :18 "siirretty maaoikeudelle"
                   :19 "tehty uhkasakkop\u00e4\u00e4t\u00f6s"
                   :20 "suunnitelmat tarkastettu"
                   :21 "ev\u00e4tty"
                   :22 "ei tutkittu (oik.vaatimus/lupa pysyy ev\u00e4ttyn\u00e4)"
                   :23 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (ei velvoitetta)"
                   :24 "m\u00e4\u00e4r\u00e4ys peruutettu (m\u00e4\u00e4r\u00e4ys/kats.p/loppukats.p.)"
                   :25 "valituks.luovuttu (oik.vaatimus/lupa pysyy ev\u00e4tt.)"
                   :26 "muutti ev\u00e4tyksi (luvat/vak/rak.v/rasite/mak/ym)"
                   :27 "muutti m\u00e4\u00e4r\u00e4yst\u00e4/p\u00e4\u00e4t\u00f6st\u00e4 (m\u00e4\u00e4r/kats.p/loppuk)"
                   :28 "pysytti ev\u00e4ttyn\u00e4 (luvat/vak/rak.v/rasite/mak/ym)"
                   :29 "ei puollettu"
                   :30 "muutettu toimenpideluvaksi (konverssio)"
                   :31 "ei lausuntoa"
                   :32 "ei tutkittu"
                   :33 "lausunto"
                   :34 "lausunto/p\u00e4\u00e4t\u00f6s (muu kuin rlk)"
                   :35 "Hallintopakko/uhkasakkoasian k\u00e4sittely lopetettu."
                   :36 "Ltk palauttanut asian uudelleen valmisteltavaksi."
                   :37 "Peruutettu"
                   :38 "asiakirjat palautettu korjauskehotuksin"
                   :39 "Ltk poistanut asian esityslistalta."
                   :40 "Ltk:n kokouksessa p\u00f6yd\u00e4lle pantu asia."
                   :41 "ilmoitus merkitty tiedoksi"
                   :42 "ei tiedossa"
                   ;; KT verdicts
                   :43 "Kiinteist\u00f6toimitus"
                   :44 "Kiinteist\u00f6rekisterin pit\u00e4j\u00e4n p\u00e4\u00e4t\u00f6s"})

(defn verdict-id [verdict-name]
  (some (fn [[k v]] (when (= v verdict-name) (name k))) verdict-map))

(defn verdict-name [id]
  (let [key (keyword (if (number? id) (str id) id))]
    (verdict-map key)))

