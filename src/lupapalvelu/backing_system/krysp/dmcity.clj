(ns lupapalvelu.backing-system.krysp.dmcity
  "Extended MM and YMP KuntaGML schemas for dmCity (Tiera City & Matti) integration.
  Schemas themselves are found in resources/krysp/dmcity and are the same
  as the official versions except with four added elements not normally supported by MM and YMP KuntaGML:
  <lp:viitelupatieto/>
  <lp:viitetieto/>
  <lp:lausuntotieto/>
  <lp:toimenpiteet/>
  <lp:kohteet/>
  These are defined in resources/krysp/dmcity/lupapiste-1.0.0.xsd"
  (:require [lupapalvelu.backing-system.krysp.mapping-common :as mapping-common]))

(def dmcity-viitelupatieto
  {:tag :viitelupatieto :ns "lp" :child [mapping-common/lupatunnus]})

(def dmcity-liitetieto
  {:tag   :liitetieto
   :ns    "lp"
   :child [{:tag :Liite :ns "lp" :child mapping-common/liite-children_219}]})

(def dmcity-lausuntotieto
  {:tag :lausuntotieto :ns "lp" :child [mapping-common/lausunto_219]})

(def dmcity-toimenpiteet
  {:tag :toimenpiteet :ns "lp" :child [{:tag   :Toimenpide
                                        :child [{:tag :ToimenpideTunnus}
                                                {:tag :ToimenpideTeksti}]}]})

(def dmcity-kohteet
  {:tag :kohteet :ns "lp" :child [{:tag :Kohde}]})

(defn add-dmcity-ns [mapping]
  (update mapping :attr assoc :xmlns:lp "http://www.lupapiste.fi/gml/dmcity"))
