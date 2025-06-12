(ns lupapalvelu.bim.bimmer.building
  "Data extraction from IfcBuilding elements."
  (:require [sade.core :refer :all]
            [sade.strings :as ss]))

(def rakentamistapa {"01 - Elementti"      "elementti"
                     "02 - Paikalla tehty" "paikalla"})

(def liittyma {"01 - Viemäriliittymä" :viemariKytkin
               "02 - Vesijohto"       :vesijohtoKytkin
               "03 - Sähkö"           :sahkoKytkin
               "04 - Maakaasu"        :maakaasuKytkin
               "05 - Kaapeli"         :kaapeliKytkin})

(def varuste {"01 - Sähkö"                   :sahkoKytkin
              "02 - Kaasu"                   :kaasuKytkin
              "03 - Viemäri"                 :viemariKytkin
              "04 - Vesijohto"               :vesijohtoKytkin
              "05 - Lämmin vesi"             :lamminvesiKytkin
              "06 - Aurinkopaneeli"          :aurinkopaneeliKytkin
              "07 - Hissi"                   :hissiKytkin
              "08 - Koneellinen ilmastointi" :koneellinenilmastointiKytkin})

(def lammonlahde {"01 - Kaukolämpö"        "kauko tai aluelämpö"
                  "02 - Kevyt polttoöljy"  "kevyt polttoöljy"
                  "03 - Raskas polttoöljy" "raskas polttoöljy"
                  "04 - Sähkö"             "sähkö"
                  "05 - Kaasu"             "kaasu"
                  "06 - Kivihiili"         "kiviihiili koksi tms"
                  "07 - Puu"               "puu"
                  "08 - Turve"             "turve"
                  "09 - Maalämpö"          "maalämpö"
                  "10 - Aurinkolämpö"      "muu"
                  "11 - Vesilämpö"         "muu"})

(def ifc-key->lp-path {"PääasiallinenRakentaja"               [:kaytto :rakentajatyyppi]
                       "RakennuksenKerrosluku"                [:mitat :kerrosluku]
                       "PääasiallinenKäyttötarkoitus"         [:kaytto :kayttotarkoitus]
                       "RakenteidenPääasiallinenRakennusaine" [:rakenne :kantavaRakennusaine]
                       "Rakentamistapa"                       [:rakenne :rakentamistapa]
                       "Julkisivumateriaali"                  [:rakenne :julkisivu]
                       "Lämmönlähde"                          [:lammitys :lammonlahde]
                       "TalokohtaisiaSaunoja"                 [:varusteet :saunoja]
                       "VäestönsuojanKoko"                    [:varusteet :vaestonsuoja]
                       "Omistajalaji"                         [:rakennuksenOmistajat :omistajalaji]
                       "Paloluokka"                           [:luokitus :paloluokka]})

(defn koodi [val]
  (-> val
      (ss/split #"\s+")
      last
      ss/lower-case))

(defmulti ifc-val->lp-val (fn [ifc-key _if-val] ifc-key))

(defmethod ifc-val->lp-val "PääasiallinenRakentaja" [_ifc-key _ifc-val]
  nil) ;;TODO what are the ifc values?

(defmethod ifc-val->lp-val "Omistajalaji" [_ifc-key _ifc-val]
  nil) ;;TODO what are the ifc values?

(defmethod ifc-val->lp-val "RakenteidenPääasiallinenRakennusaine" [_ifc-key ifc-val]
  (koodi ifc-val))

(defmethod ifc-val->lp-val "Julkisivumateriaali" [_ifc-key ifc-val]
  (koodi ifc-val))

(defmethod ifc-val->lp-val "Rakentamistapa" [_ifc-key ifc-val]
  (or (rakentamistapa ifc-val)
      "ei tiedossa"))

(defmethod ifc-val->lp-val "Lämmönlähde" [_ifc-key ifc-val]
  (or (lammonlahde ifc-val)
      "ei tiedossa"))

(defmethod ifc-val->lp-val :default [_ ifc-val]
  (str ifc-val))

(defmulti ifc->lp (fn [ifc-key _ifc-val] ifc-key))

(defmethod ifc->lp "RakennuksenVarusteet" [_ifc-key ifc-val]
  (->> (ss/split ifc-val #";")
       (keep (fn [v] (when-let [v (varuste v)]
                       [[:varusteet v] true])))))

(defmethod ifc->lp "LiittymätVerkostoihin" [_ifc-key ifc-val]
  (->> (ss/split ifc-val #";")
       (keep (fn [v] (when-let [l (liittyma v)]
                       [[:verkostoliittymat l] true])))))

(defmethod ifc->lp :default [ifc-key ifc-val]
  (when-let [lp-path (ifc-key->lp-path ifc-key)]
    [[lp-path (ifc-val->lp-val ifc-key ifc-val)]]))

(defn data->rh-updates [data property-set-name]
  (let [rh-data (-> data
                    (get "IfcBuilding")
                    first
                    (get property-set-name))
        updates (mapcat (fn [[k v]]
                          (ifc->lp k v))
                        rh-data)]
    (filter second updates)))

(defn data->building-updates [data]
  (concat (data->rh-updates data "FI_RH1")
          (data->rh-updates data "FI_Kohde")))

(defn data->location [data]
  (when-let [location-data (-> data
                               (get "IfcBuilding")
                               first
                               (get "FI_RakennuksenSijainti"))]
    {:x (get location-data "Itäkoordinaatti")
     :y (get location-data "Pohjoiskoordinaatti")}))
