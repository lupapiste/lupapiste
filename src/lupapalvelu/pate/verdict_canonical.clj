(ns lupapalvelu.pate.verdict-canonical
  (:require [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.schema-helper :as helper]))

(defn- vaadittu-katselmus-canonical [lang {{reviews :reviews} :references} review-id]
  (let [review (util/find-by-id review-id reviews)]
    {:Katselmus {:katselmuksenLaji (helper/review-type-map (or (keyword (:type review)) :ei-tiedossa))
                 :tarkastuksenTaiKatselmuksenNimi (get review (keyword lang))
                 :muuTunnustieto [#_{:MuuTunnus "yht:MuuTunnusType"}]}})) ; TODO: initialize review tasks and pass ids here

(defn- maarays-seq-canonical [{:keys [data]}]
  (some->> data :conditions vals
           (map :condition)
           (remove ss/blank?)
           (map #(assoc-in {} [:Maarays :sisalto] %))
           not-empty))

(defn- vaadittu-erityissuunnitelma-canonical [lang {{plans :plans} :references} plan-id]
  (let [plan (util/find-by-id plan-id plans)]
    {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma (get plan (keyword lang))
                                  :toteutumisPvm nil}}))

(def ^:private foreman-role-mapping {:vv-tj "KVV-ty\u00f6njohtaja"
                                     :iv-tj "IV-ty\u00f6njohtaja"
                                     :erityis-tj "erityisalojen ty\u00f6njohtaja"
                                     :vastaava-tj "vastaava ty\u00f6njohtaja"
                                     :tj "ty\u00f6njohtaja"
                                     nil "ei tiedossa"})

(defn- vaadittu-tyonjohtaja-canonical [foreman]
  {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi (get foreman-role-mapping (keyword foreman) "ei tiedossa")}})

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
   :vaaditutKatselmukset (map (partial vaadittu-katselmus-canonical lang verdict) (:reviews data))
   :maaraystieto (maarays-seq-canonical verdict)
   :vaadittuErityissuunnitelmatieto (map (partial vaadittu-erityissuunnitelma-canonical lang verdict) (:plans data))
   :vaadittuTyonjohtajatieto (map vaadittu-tyonjohtaja-canonical (:foremen data))})

(defn- paivamaarat-type-canonical [{:keys [data]}]
  {:aloitettavaPvm (util/to-xml-date (:aloitettava data))
   :lainvoimainenPvm (util/to-xml-date (:lainvoimainen data))
   :voimassaHetkiPvm (util/to-xml-date (:voimassa data))
   :raukeamisPvm nil
   :antoPvm (util/to-xml-date (:anto data))
   :viimeinenValitusPvm (util/to-xml-date (:valitus data))
   :julkipanoPvm (util/to-xml-date (:julkipano data))})

(defn- paatoksentekija [lang {{handler :handler} :data {giver :giver} :template}]
  (->> [handler
        (when-not (ss/blank? giver)
          (format "(%s)" (i18n/localize lang :pate-verdict.giver giver)))]
       (remove ss/blank?)
       (ss/join " "))
  #_(cond
    (ss/blank? giver) handler
    (ss/blank? handler) (i18n/localize lang "pate-verdict.giver" giver)
    :else (format "%s (%s)" contact (i18n/localize lang "pate-verdict.giver" giver))))

(defn- paatospoytakirja-type-canonical [lang {data :data :as verdict}]
  {:paatos (:verdict-text data)
   :paatoskoodi (helper/verdict-code-map (or (keyword (:verdict-code data)) :ei-tiedossa))
   :paatoksentekija (paatoksentekija lang verdict)
   :paatospvm (util/to-xml-date (:verdict-date data))
   :pykala (:verdict-section data)})

(defn verdict-canonical [lang verdict]
  {:Paatos {:lupamaaraykset (lupamaaraykset-type-canonical lang verdict)
            :paivamaarat (paivamaarat-type-canonical verdict)
            :poytakirja (paatospoytakirja-type-canonical lang verdict)}})
