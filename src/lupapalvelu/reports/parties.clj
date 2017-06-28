(ns lupapalvelu.reports.parties
  (:require [sade.env :as env]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [lupapalvelu.document.tools :as tools]))


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

(defn pick-henkilo-data
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

(defn pick-yritys-data
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

(defn applicants [filter-fn map-fn app lang]
  (let [info (basic-info-localized app lang)
        applicant-schema-name (op/get-applicant-doc-schema-name app)]
    (->> (:documents app)
         (filter (partial applicant-doc? applicant-schema-name))
         (filter filter-fn)
         (map map-fn)
         (map (partial merge info)))))

(defn private-applicants [app lang]
  (applicants henkilo-doc? pick-henkilo-data app lang))

(def private-applicants-row-fn
  (juxt :id-link :id :address :primaryOperation
        :etunimi :sukunimi :osoite :puhelin :sahkoposti
        :suoramarkkinointilupa :turvakielto))

(defn company-applicants [app lang]
  (applicants yritys-doc? pick-yritys-data app lang ))

(def company-applicants-row-fn
  (juxt :id-link :id :address :primaryOperation
        :yritysnimi :osoite :yhteyshenkiloetunimi :yhteyshenkilosukunimi
        :yhteyshenkilopuhelin :yhteyshenkilosahkoposti :suoramarkkinointilupa
        :turvakielto))

(defn designers [app lang] [])
(defn foremen [app lang] [])
