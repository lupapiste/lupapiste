(ns lupapalvelu.pate.verdict-canonical
  (:require [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.verdict-common :as vc]))

(defn- vaadittu-katselmus-canonical [lang review]
  {:Katselmus {:katselmuksenLaji (:type review)
               :tarkastuksenTaiKatselmuksenNimi (get review (keyword lang))
               :muuTunnustieto [#_{:MuuTunnus "yht:MuuTunnusType"}]}}) ; TODO: initialize review tasks and pass ids here

(defn- maarays-seq-canonical [{:keys [data]}]
  (some->> data :conditions vals
           (map :condition)
           (remove ss/blank?)
           (map #(assoc-in {} [:Maarays :sisalto] %))
           not-empty))

(defn- vaadittu-erityissuunnitelma-canonical [lang plan]
  {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma (get plan (keyword lang))
                                :toteutumisPvm nil}})

(defn- vaadittu-tyonjohtaja-canonical [foreman]
  {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi foreman}})

(defn- lupamaaraykset-type-canonical [lang {{buildings :buildings :as data} :data :as verdict}]
  {:autopaikkojaEnintaan nil
   :autopaikkojaVahintaan nil
   :autopaikkojaRakennettava (->> (map :autopaikat-yhteensa (vals buildings)) (map util/->int) (apply +))
   :autopaikkojaRakennettu (->> (map :rakennetut-autopaikat (vals buildings)) (map util/->int) (apply +))
   :autopaikkojaKiinteistolla (->> (map :kiinteiston-autopaikat (vals buildings)) (map util/->int) (apply +))
   :autopaikkojaUlkopuolella nil
   :kerrosala nil
   :kokonaisala nil
   :rakennusoikeudellinenKerrosala nil
   :vaaditutKatselmukset (mapv (partial vaadittu-katselmus-canonical lang)
                               (vc/verdict-required-reviews verdict))
   :maaraystieto (maarays-seq-canonical verdict)
   :vaadittuErityissuunnitelmatieto (mapv (partial vaadittu-erityissuunnitelma-canonical lang)
                                          (vc/verdict-required-plans verdict))
   :vaadittuTyonjohtajatieto (map vaadittu-tyonjohtaja-canonical
                                  (vc/verdict-required-foremen verdict))})

(defn- paivamaarat-type-canonical [verdict]
  (let [data (vc/verdict-dates verdict)]
    {:aloitettavaPvm (util/to-xml-date (:aloitettava data))
     :lainvoimainenPvm (util/to-xml-date (:lainvoimainen data))
     :voimassaHetkiPvm (util/to-xml-date (:voimassa data))
     :raukeamisPvm (util/to-xml-date (:raukeamis data))
     :antoPvm (util/to-xml-date (:anto data))
     :viimeinenValitusPvm (util/to-xml-date (:valitus data))
     :julkipanoPvm (util/to-xml-date (:julkipano data))}))

(defn- paatoksentekija [lang {{giver :giver} :template :as verdict}]
  (->> [(vc/verdict-giver verdict)
        (when-not (ss/blank? giver)
          (format "(%s)" (i18n/localize lang :pate-verdict.giver giver)))]
       (remove ss/blank?)
       (ss/join " ")))

(defn- paatospoytakirja-type-canonical [lang verdict]
  {:paatos (vc/verdict-text verdict)
   :paatoskoodi (helper/verdict-code-map (or (keyword (vc/verdict-code verdict))
                                             :ei-tiedossa))
   :paatoksentekija (paatoksentekija lang verdict)
   :paatospvm (util/to-xml-date (vc/verdict-date verdict))
   :pykala (vc/verdict-section verdict)})

(defn verdict-canonical [lang verdict]
  {:Paatos {:lupamaaraykset (lupamaaraykset-type-canonical lang verdict)
            :paivamaarat (paivamaarat-type-canonical verdict)
            :poytakirja (paatospoytakirja-type-canonical lang verdict)}})
