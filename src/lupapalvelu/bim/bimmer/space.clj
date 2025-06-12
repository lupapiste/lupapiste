(ns lupapalvelu.bim.bimmer.space
  "Data extraction from IfcSpace elements. Elements with FI_Huoneisto property sets represent premises.
  Other IfcSpaces contain measures for floors etc."
  (:require [sade.core :refer :all]
            [sade.strings :as ss]))

(set! *warn-on-reflection* true)

(def keittio {"Keittiö"      "keittio"
              "Keittokomero" "keittokomero"
              "Keittotila"   "keittotila"
              "Tupakeittiö"  "tupakeittio"})

(def varuste {"WC"          :WCKytkin
              "Suihku"      :ammeTaiSuihkuKytkin
              "Sauna"       :saunaKytkin
              "Parveke"     :parvekeTaiTerassiKytkin
              "Lämmin vesi" :lamminvesiKytkin})

(def ifc-key->lp-key {"Käyttötarkoitus" :huoneistoTyyppi
                      "Porraskirjain"   :porras
                      "Numero"          :huoneistonumero
                      "Jakokirjain"     :jakokirjain
                      "Huoneluku"       :huoneluku
                      "Keittiötyyppi"   :keittionTyyppi
                      "Muutostapa"      :muutostapa
                      "Huoneistoala"    :huoneistoala})

(def measures {"Kokonaistilavuus"   :tilavuus
               "Kerrosala"          :kerrosala
               "Kokonaisala"        :kokonaisala
               "Kellarin pinta-ala" :kellarinpinta-ala})

(defn num-str [val]
  (some->> val float (format "%.0f")))

(defmulti ifc-val->lp-val (fn [ifc-key _if-val] ifc-key))

(defmethod ifc-val->lp-val "Käyttötarkoitus" [_ifc-key _ifc-val]
  "asuinhuoneisto")

(defmethod ifc-val->lp-val "Keittiötyyppi" [_ifc-key ifc-val]
  (keittio ifc-val))

(defmethod ifc-val->lp-val "Muutostapa" [_ifc-key _ifc-val]
  "lisäys") ;;TODO ???

(defmethod ifc-val->lp-val "Huoneistoala" [_ifc-key ifc-val]
  (num-str ifc-val))

(defmethod ifc-val->lp-val :default [_ ifc-val]
  (str ifc-val))

(defmulti ifc->lp (fn [ifc-key _ifc-val] ifc-key))

(defmethod ifc->lp "Varusteet" [_ifc-key ifc-val]
  (when ifc-val
    (->> (ss/split ifc-val #";")
         (keep (fn [v]
                 (when-let [v (varuste v)]
                   [v true]))))))

(defmethod ifc->lp :default [ifc-key ifc-val]
  (when-let [lp-key (ifc-key->lp-key ifc-key)]
    [[lp-key (ifc-val->lp-val ifc-key ifc-val)]]))

(defn premise->update [premises-number [ifc-key ifc-val]]
  (let [updates (ifc->lp ifc-key ifc-val)]
    (map (fn [[lp-key lp-val]]
           (when lp-val
             [[:huoneistot (-> premises-number str keyword) lp-key] lp-val]))
         updates)))

(defn premise->updates [premise premises-number]
  (remove #(= nil (second %)) (mapcat (partial premise->update premises-number) premise)))

(defn data->premise-updates
  "Process IfcSpaces with FI_Huoneisto property sets to generate items for huoneisto table. Also handle
  the property set FI_Laajuustieto to calculate area. "
  [data]
  (let [premise-data (->> (get data "IfcSpace")
                          (keep (fn [space]
                                  (when-let [premise (get space "FI_Huoneisto")]
                                    (-> premise
                                        (merge (get space "FI_Laajuustieto")))))))]
    (reduce #(concat %1 (premise->updates (nth premise-data %2) %2))
            []
            (range (count premise-data)))))

(defn sum-measures
  "The data contains floor and total area per building floor, so we want to sum those up."
  [data [ifc-key lp-key]]
  (when-let [measures (->> data
                           (keep #(get % ifc-key))
                           seq)]
    [[:mitat lp-key] (->> measures
                          (apply +)
                          num-str)]))

(defn data->measure-updates
  "Process IfcSpaces with specific measure property sets to generate measure data."
  [data]
  (let [measure-data (->> (get data "IfcSpace")
                          (keep #(get % "FI_Laajuustieto")))]
    (keep (partial sum-measures measure-data) measures)))
