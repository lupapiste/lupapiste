(ns lupapalvelu.matti.verdict-canonical
  (:require [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.matti.shared :as matti-shared]))

(defn- vaadittu-katselmus-canonical [lang {{reviews :reviews} :references :as verdict} review-id]
  (let [review (util/find-by-id review-id reviews)]
    {:Katselmus {:katselmuksenLaji (matti-shared/review-type-map (or (keyword (:type review)) :ei-tiedossa))
                 :tarkastuksenTaiKatselmuksenNimi (get-in review [:name (keyword lang)])
                 :muuTunnustieto [#_{:MuuTunnus "yht:MuuTunnusType"}]}})) ; TODO: find out if reviews should be initialized and pass id here

(defn- maarays-canonical [lang {data :data :as verdict}]
  {:Maarays {:sisalto (:conditions data)
             :maaraysPvm (util/to-xml-date-from-string (:verdict-date data)) ; TODO: is this ok?
             :toteutusHetki nil}}) ; TODO: should this be empty

(defn- vaadittu-erityissuunnitelma-canonical [lang {{plans :plans} :references :as verdict} plan-id]
  (let [plan (util/find-by-id plan-id plans)]
    {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma (get-in plan [:name (keyword lang)])
                                  :toteutumisPvm nil}})) ; TODO: should this be empty

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
   :autopaikkojaVahintaan nil ; TODO: where to put this (:autopaikkoja-yhteensa building)
   :autopaikkojaRakennettava nil
   :autopaikkojaRakennettu (:rakennetut-autopaikat (first (vals buildings))) ; TODO: how this should work for multiple buildings
   :autopaikkojaKiinteistolla (:kiinteiston-autopaikat (first (vals buildings))) ; TODO: how this should work for multiple buildings
   :autopaikkojaUlkopuolella nil
   :kerrosala nil
   :kokonaisala nil
   :rakennusoikeudellinenKerrosala nil
   :vaaditutKatselmukset (map (partial vaadittu-katselmus-canonical lang verdict) (:reviews data))
   :maaraystieto [(maarays-canonical lang verdict)]
   :vaadittuErityissuunnitelmatieto (map (partial vaadittu-erityissuunnitelma-canonical lang verdict) (:plans data))
   :vaadittuTyonjohtajatieto (map vaadittu-tyonjohtaja-canonical (:foremen data))})

(defn- paivamaarat-type-canonical [lang {data :data :as verdict}]
  {:aloitettavaPvm (util/to-xml-date-from-string (:aloitettava data))
   :lainvoimainenPvm (util/to-xml-date-from-string (:lainvoimainen data))
   :voimassaHetkiPvm (util/to-xml-date-from-string (:voimassa data))
   :raukeamisPvm nil ; TODO: find out if this sould be based on :voimassa
   :antoPvm (util/to-xml-date-from-string (:anto data))
   :viimeinenValitusPvm (util/to-xml-date-from-string (:valitus data))
   :julkipanoPvm (util/to-xml-date-from-string (:julkipano data))})

(defn- paatoksentekija [lang {{:keys [contact giver]} :data :as verdict}]
  (cond
    (ss/blank? giver) contact
    (ss/blank? contact) (i18n/localize lang "matti-verdict.giver" giver)
    :else (format "%s (%s)" contact (i18n/localize lang "matti-verdict.giver" giver))))

(defn- paatospoytakirja-type-canonical [lang {data :data :as verdict}]
  {:paatos (:verdict-text data)
   :paatoskoodi (matti-shared/verdict-code-map (or (keyword (:verdict-code data)) :ei-tiedossa))
   :paatoksentekija (paatoksentekija lang verdict)
   :paatospvm (util/to-xml-date-from-string (:verdict-date data))
   :pykala (:verdict-section data)
   :liite nil #_"yht:RakennusvalvontaLiiteType"}) ; TODO: add attachments

(defn verdict-canonical [application lang verdict]
  {:Paatos {:lupamaaraykset (lupamaaraykset-type-canonical lang verdict)
            :paivamaarat (paivamaarat-type-canonical lang verdict)
            :poytakirja [(paatospoytakirja-type-canonical lang verdict)]}})
