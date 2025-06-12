(ns lupapalvelu.pate.verdict-canonical
  (:require [lupapalvelu.backing-system.krysp.verdict :as krysp]
            [lupapalvelu.migration.foreman-role-mapping :as frm]
            [lupapalvelu.pate.metadata :as metadata]
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
           (map (fn [{:keys [content deadline]}]
                  (cond-> (assoc-in {} [:Maarays :sisalto] content)
                    deadline (assoc-in [:Maarays :maaraysPvm] (date/xml-date deadline)))))
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

  1. review and task have the same katselmuksenLaji
  2. source-id of the task is same as the verdict-id
  3. review and task names match"
  [verdict application]
  (let [lang      (or (some-> verdict :data :language keyword) :fi)
        task-list (->> (:tasks application)
                       (filter (fn [task]
                                 (and (task-is-review? task)
                                      (some-> task :source :id (= (:id verdict))))))
                       (map (fn [{:keys [id taskname data]}]
                              {:id   id
                               :name taskname
                               :type (get-in data [:katselmuksenLaji :value])})))]
    (loop [[r & rs]  (vc/verdict-required-reviews verdict)
           task-list task-list
           result    []]
      (if r
        (if-let [task (util/find-by-keys {:type (:type r) :name (lang r)}
                                         task-list)]
          (recur rs
                 (remove #(= task %) task-list)
                 (conj result (assoc r :task-id (:id task))))
          (recur rs task-list (conj result r)))
        result))))

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
    {:aloitettavaPvm      (date/xml-date (or (:aloitettava data) (:k-aloitettava data)))
     :lainvoimainenPvm    (date/xml-date (or (:lainvoimainen data) (:k-lainvoimainen data)))
     :voimassaHetkiPvm    (date/xml-date (or (:voimassa data) (:k-voimassa data)))
     :raukeamisPvm        (date/xml-date (:raukeamis data))
     :antoPvm             (date/xml-date (or (:anto data)
                                             (some-> (:k-kuulutus data) (date/plus :days 7))))
     :viimeinenValitusPvm (date/xml-date (or (:muutoksenhaku data) (:k-muutoksenhaku data)))
     ;; Use `julkipanoPvm` also for kuulutus as KuntaGML has no specific field for kuulutuspvm
     :julkipanoPvm        (date/xml-date (or (:julkipano data) (:k-kuulutus data)))}))

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
  "Note also `add-pate-verdict-pairs` below for information that can't be included in the verdict canonical
  but should be included when sending the verdict to the backend"
  [lang verdict application]
  (let [lupamaaraykset (lupamaaraykset-type-canonical lang verdict application)
        paivamaarat    (paivamaarat-type-canonical verdict)
        poytakirja     (paatospoytakirja-type-canonical verdict)]
    {:Paatos {:lupamaaraykset      lupamaaraykset
              :paivamaarat         paivamaarat
              :poytakirja          poytakirja
              :paatosdokumentinPvm (:paatospvm poytakirja)  ;; for YA verdicts
              }}))

(defn add-pate-verdict-pairs
  "Adds to the given application canonical model the avain-arvo pairs that include extra info
  related to the PATE verdict that we want to send to the backend"
  [app-canonical verdict canonical-path]
  (let [->pair     (fn [[k v]] {:AvainArvoPari {:avain k :arvo v}})
        ;; Paloluokka is given for each building so store the identifier (yksilointitieto) as well
        paloluokat (reduce (fn [acc [idx [id building]]]
                             (merge acc
                                    {(str "paloluokkaYksilointitieto_" idx) id
                                     (str "paloluokkaArvo_" idx)            (-> building :paloluokka metadata/unwrap)}))
                           {}
                           (-> verdict :data :buildings util/indexed))
        pairs      (->> {"paatosId"               (-> verdict :id)
                         "sovelletutOikeusohjeet" (-> verdict :data :legalese)
                         "paatoksenPerustelut"    (-> verdict :data :rationale)
                         "paatoksenKasittelija"   (-> verdict :data :handler)
                         "naapurienKuuleminen"    (-> verdict :data :neighbors)
                         "lisaselvitykset"        (-> verdict :data :extra-info)
                         "paatoksenRatkaisija"    (vc/verdict-giver-name verdict)}
                        (merge paloluokat)
                        (util/map-values str)
                        (util/filter-map-by-val ss/not-blank?)
                        (map ->pair)
                        seq)]
    (cond-> app-canonical
      pairs (update-in canonical-path
                       concat
                       pairs))))

(defn update-asiantiedot-with-pate-verdict-data
  "Update asiantiedot (e.g. rakennusvalvontaasianKuvaus and vahainenPoikkeaminen) in the
  given canonical model of application with the PATE verdict data."
  [app-canonical verdict asiantiedot-description-path asiantiedot-deviation-path]
  (let [operation  (some-> verdict :data :operation ss/trim ss/blank-as-nil)
        deviations (some-> verdict :data :deviations ss/trim ss/blank-as-nil)]
    ;; KuntaGML should have the verdict deviations or be empty
    (cond-> (assoc-in app-canonical asiantiedot-deviation-path deviations)
      operation (assoc-in asiantiedot-description-path operation))))
