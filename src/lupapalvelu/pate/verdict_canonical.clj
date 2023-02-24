(ns lupapalvelu.pate.verdict-canonical
  (:require [lupapalvelu.backing-system.krysp.verdict :as krysp]
            [lupapalvelu.migration.foreman-role-mapping :as frm]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.task-util :refer [task-is-review?]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- vaadittu-katselmus-canonical [lang review]
  {:Katselmus {:katselmuksenLaji                (:type review)
               :tarkastuksenTaiKatselmuksenNimi (get review (keyword lang))
               :muuTunnustieto                  [{:MuuTunnus {:tunnus   (or (:task-id review) "")
                                                              :sovellus "Lupapiste"}}]}})

(defn- maarays-seq-canonical [verdict]
  (some->> (vc/verdict-required-conditions verdict)
           (map #(assoc-in {} [:Maarays :sisalto] %))
           not-empty))

(defn- vaadittu-erityissuunnitelma-canonical [lang plan]
  {:VaadittuErityissuunnitelma {:vaadittuErityissuunnitelma (get plan (keyword lang))
                                :toteutumisPvm              nil}})

(defn- vaadittu-tyonjohtaja-canonical [verdict foreman]
  (when-let [code (cond
                    (ss/blank? foreman) nil

                    (vc/lupapiste-verdict? verdict) foreman

                    :else (or (frm/coerce-str foreman) "ei tiedossa"))]
    {:VaadittuTyonjohtaja {:tyonjohtajaRooliKoodi code}}))

(defn- verdict-reviews
  "Returns `vc/verdict-required-reviews` result enriched with optional `:task-id`
  keys. `:task-id` added for review if

  1: review and task have the same katselmuksenLaji.
  2: source-id of the task is same as the verdict-id"
  [verdict application]
  (let [task-map (->> (:tasks application)
                      (filter (fn [task]
                                (and (task-is-review? task)
                                     (some-> task :source :id (= (:id verdict))))))
                      (map (fn [task]
                             [(get-in task [:data :katselmuksenLaji :value])
                              (:id task)]))
                      (into {}))]
    (map (fn [{r-type :type :as review}]
           (util/assoc-when review :task-id (get task-map r-type)))
         (vc/verdict-required-reviews verdict))))

(defn- lupamaaraykset-type-canonical [lang verdict application]
  (merge (vc/verdict-parking-space-requirements verdict)
         (vc/verdict-area-requirements verdict)
         {:vaaditutKatselmukset            (seq (mapv (partial vaadittu-katselmus-canonical lang)
                                                      (verdict-reviews verdict application)))
          :maaraystieto                    (maarays-seq-canonical verdict)
          :vaadittuErityissuunnitelmatieto (seq (map (partial vaadittu-erityissuunnitelma-canonical lang)
                                                      (vc/verdict-required-plans verdict)))
          :vaadittuTyonjohtajatieto        (seq (keep (partial vaadittu-tyonjohtaja-canonical verdict)
                                                      (vc/verdict-required-foremen verdict)))
          :kokoontumistilanHenkilomaara    (vc/verdict-kokoontumistilan-henkilomaara verdict)}))

(defn- paivamaarat-type-canonical [verdict]
  (let [data (vc/verdict-dates verdict)]
    {:aloitettavaPvm      (date/xml-date (:aloitettava data))
     :lainvoimainenPvm    (date/xml-date (:lainvoimainen data))
     :voimassaHetkiPvm    (date/xml-date (:voimassa data))
     :raukeamisPvm        (date/xml-date (:raukeamis data))
     :antoPvm             (date/xml-date (:anto data))
     :viimeinenValitusPvm (date/xml-date (:muutoksenhaku data))
     :julkipanoPvm        (date/xml-date (:julkipano data))}))

(defn- verdict-code
  "KuntaGMl verdict code string (e.g., annettu lausunto) for the `verdict`. Different
  verdicts (Pate, legacy, backing system) store their status/code information in different
  ways."
  [verdict]
  (or (cond
        ;; Pate verdict code is a status code (e.g. "12")
        (vc/legacy? verdict)
        (krysp/verdict-name (vc/verdict-code verdict))

        ;; Pate verdict code is a shorthand (e.g., "myonnetty")
        (vc/lupapiste-verdict? verdict)
        (some-> (vc/verdict-code verdict) keyword helper/verdict-code-map)

        ;; Status code (e.g. "40") from the latest verdict pöytäkirja
        :else
        (some-> (vc/latest-pk verdict) :status krysp/verdict-name))
      (:ei-tiedossa helper/verdict-code-map)))

(defn- paatospoytakirja-type-canonical [verdict]
  {:paatos          (vc/verdict-text verdict)
   :paatoskoodi     (verdict-code verdict)
   :paatoksentekija (vc/verdict-giver verdict)
   :paatospvm       (date/xml-date (vc/verdict-date verdict))
   :pykala          (vc/verdict-section verdict)})

(defn verdict-canonical
  [lang verdict application]
  (let [lupamaaraykset (lupamaaraykset-type-canonical lang verdict application)
        paivamaarat    (paivamaarat-type-canonical verdict)
        poytakirja     (paatospoytakirja-type-canonical verdict)]
    {:Paatos {:lupamaaraykset      lupamaaraykset
              :paivamaarat         paivamaarat
              :poytakirja          poytakirja
              :paatosdokumentinPvm (:paatospvm poytakirja)  ;; for YA verdicts
              }}))
