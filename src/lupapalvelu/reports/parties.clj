(ns lupapalvelu.reports.parties
  (:require [sade.env :as env]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]))


(defn make-app-link [id lang]
  (str (env/value :host) "/app/" lang "/authority" "#!/application/" id))

(defn basic-info-localized [app lang]
  (-> (select-keys app [:id :address :primaryOperation])
      (assoc :id-link (make-app-link (:id app) lang))
      (update :primaryOperation #(i18n/localize lang (str "operations." (:name %))))))

(defn applicant-doc? [applicant-schema-name doc]
  (= (get-in doc [:schema-info :name])
     applicant-schema-name))

(defn henkilo-doc? [doc]
  (= (get-in doc [:data :_selected :value])
     "henkilo"))

(defn yritys-doc? [doc]
  (= (get-in doc [:data :_selected :value])
     "yritys"))

(defn get-osoite [party-data]
  (str (get-in party-data [:osoite :katu])
       ", "
       (get-in party-data [:osoite :postinumero])
       " "
       (get-in party-data [:osoite :postitoimipaikannimi])
       " "
       (get-in party-data [:osoite :maa])))

(defn pick-person-data
  "Nimi, tietojani saa luovuttaa, turvakielto, yhteysosoite, puhelin, sahkoposti"
  [doc]
  (let [data (tools/unwrapped (get-in doc [:data :henkilo]))]
    {:etunimi (get-in data [:henkilotiedot :etunimi])
     :sukunimi (get-in data [:henkilotiedot :sukunimi])
     :suoramarkkinointilupa (get-in data [:kytkimet :suoramarkkinointilupa])
     :turvakielto (get-in data [:henkilotiedot :turvakieltoKytkin])
     :osoite (get-osoite data)
     :puhelin (get-in data [:yhteystiedot :puhelin])
     :sahkoposti (get-in data [:yhteystiedot :email])}))

(defn pick-company-data
  "henkilo + nimi, yhteysosoiteet ja kaikki yhteyshenkilon tiedot"
  [doc]
  (let [data (tools/unwrapped (get-in doc [:data :yritys]))]
    {:yritysnimi (get-in data [:yritysnimi])
     :osoite (get-osoite data)
     :yhteyshenkiloetunimi (get-in data [:yhteyshenkilo :henkilotiedot :etunimi])
     :yhteyshenkilosukunimi (get-in data [:yhteyshenkilo :henkilotiedot :sukunimi])
     :suoramarkkinointilupa (get-in data [:yhteyshenkilo :kytkimet :suoramarkkinointilupa])
     :turvakielto (get-in data [:yhteyshenkilo :henkilotiedot :turvakieltoKytkin])
     :yhteyshenkilopuhelin (get-in data [:yhteyshenkilo :yhteystiedot :puhelin])
     :yhteyshenkilosahkoposti (get-in data [:yhteyshenkilo :yhteystiedot :email])}))


;;
;; Private applicants
;;

(defn applicants [filter-fn map-fn app]
  (let [applicant-schema-name (op/get-applicant-doc-schema-name app)]
    (->> (:documents app)
         (filter (partial applicant-doc? applicant-schema-name))
         (filter filter-fn)
         (map map-fn))))

(defn private-applicants [app lang]
  (map
    (partial merge (basic-info-localized app lang))
    (applicants henkilo-doc? pick-person-data app)))

(def private-applicants-row-fn
  (juxt :id-link :id :address :primaryOperation
        :etunimi :sukunimi :osoite :puhelin :sahkoposti
        :suoramarkkinointilupa :turvakielto))

;;
;; Company applicants
;;

(defn company-applicants [app lang]
  (map
    (partial merge (basic-info-localized app lang))
    (applicants yritys-doc? pick-company-data app)))

(def company-applicants-row-fn
  (juxt :id-link :id :address :primaryOperation
        :yritysnimi :osoite :yhteyshenkiloetunimi :yhteyshenkilosukunimi
        :yhteyshenkilopuhelin :yhteyshenkilosahkoposti :suoramarkkinointilupa
        :turvakielto))

;;
;; Designers
;;

(defn pick-designer-data
  "Vaativuusluokka, Nimi, Rooli(huom pääsunnitelija dokumentti tyyppi), pätevyys, yhteysosoite, puh, sähköposti,tutkinto, valmistumisvuosi, fisekortti(linkkinä)\n"
  [doc]
  (let [data (tools/unwrapped (get doc :data))]
    {:etunimi          (get-in data [:henkilotiedot :etunimi])
     :sukunimi         (get-in data [:henkilotiedot :sukunimi])
     :rooli            (case (get-in doc [:schema-info :name])
                         "paasuunnittelija" "P\u00e4\u00e4suunnittelija"
                         "suunnittelija"    (get data :kuntaRoolikoodi))
     :suunnittelu-vaativuus (get-in data [:suunnittelutehtavanVaativuusluokka])
     :patevyysluokka   (get-in data [:patevyys :patevyysluokka])
     :patevyys         (get-in data [:patevyys :patevyys])
     :osoite           (get-osoite data)
     :puhelin          (get-in data [:yhteystiedot :puhelin])
     :sahkoposti       (get-in data [:yhteystiedot :email])
     :fise             (get-in data [:patevyys :fise])
     :tutkinto         (get-in data [:patevyys :koulutusvalinta])
     :valmistumisvuosi (get-in data [:patevyys :valmistumisvuosi])}))

(defn designers [app lang]
  (map
    (partial merge (basic-info-localized app lang))
    (->> (domain/get-documents-by-subtype (:documents app) "suunnittelija")
         (map pick-designer-data))))

(def designers-fields
  [:id-link :id :address :primaryOperation
   :etunimi :sukunimi :rooli
   :suunnittelu-vaativuus :patevyysluokka :patevyys
   :osoite :puhelin :sahkoposti
   :fise :tutkinto :valmistumisvuosi])

(def designers-row-fn
  (apply juxt designers-fields))

;;
;; Foremen
;;
; Yritys, tj nimi, yhtestiedot, puhelin, sähköposti,
; rooli, vaativuus, täysi/osa-aikainen, tutkinto,
; sijaistettavan nimi, sijaistettavan aika, vastattavat työtehtävät pilkulla eroteltuna.


(defn enrich-foreman-apps
  "Enrich foreman-apps suitable for excel report. Replace primary operation with project application's operation."
  [foreman-apps]
  (let [link-map (->> (mongo/select :app-links {:link {$in (map :id foreman-apps)}})
                      (reduce (fn [result link]
                                (let [fore-id (keyword (first (:link link))) ; target
                                      project-id (keyword (second (:link link)))] ; source
                                  (assoc result fore-id (assoc
                                                          (get link project-id)
                                                          :id
                                                          (name project-id))))) {}))]
    (->> foreman-apps
         (map #(assoc-in %
                         [:primaryOperation :name]
                         (get-in link-map [(keyword (:id %)) :apptype]))))))

(defn vastattavat-tyotehtavat-as-string [doc lang]
  (let [vastattavat-data (tools/unwrapped (get-in doc [:data :vastattavatTyotehtavat]))
        vastattavat-loc-keys (reduce (fn [m {:keys [name i18nkey]}]
                                       (assoc m (keyword name) i18nkey))
                                     {}
                                     (get-in schemas/vastattavat-tyotehtavat-tyonjohtaja-v2 [0 :body]))]
    (->> vastattavat-data
         (reduce-kv (fn [result key val]
                      (cond
                        (and (= :muuMika (keyword key))
                             (not (ss/blank? val))) (conj result val)
                        (true? val) (conj result (i18n/localize lang (get vastattavat-loc-keys key)))
                        :else result))
                    [])
         (ss/join ","))))


(defn pick-foreman-data
  "Doc: tyonjohtaja-v2"
  [doc lang]
  (let [data (tools/unwrapped (get doc :data))]
    {:etunimi (get-in data [:henkilotiedot :etunimi])
     :sukunimi (get-in data [:henkilotiedot :sukunimi])
     :rooli    (get-in data [:kuntaRoolikoodi])
     :vastattavat-tyotehtavat (vastattavat-tyotehtavat-as-string doc lang)}))


(defn foremen [app lang]
  (merge (basic-info-localized app lang)
         (pick-foreman-data (foreman/get-foreman-document app) lang)))

(def foremen-row-fn
  (juxt :id-link :id :address :primaryOperation
        :etunimi :sukunimi :rooli :vastattavat-tyotehtavat))
