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
  "Nimi, Tietojani saa luovuttaa, turvakielto, yhteysosoite, puhelin, sähköposti"
  [doc]
  (let [data (tools/unwrapped (get-in doc [:data :henkilo]))]
    {:etunimi (get-in data [:henkilotiedot :etunimi])
     :sukunimi (get-in data [:henkilotiedot :sukunimi])
     :suoramarkkinointilupa (get-in data [:kytkimet :suoramarkkinointilupa])
     :turvakielto (get-in data [:henkilotiedot :turvakieltoKytkin])
     :osoite (get-osoite data)
     :puhelin (get-in data [:yhteystiedot :puhelin])
     :sahkoposti (get-in data [:yhteystiedot :email])}))

(defn private-applicants [app lang]
  (let [info (basic-info-localized app lang)
        applicant-schema-name (op/get-applicant-doc-schema-name app)]
    (->> (:documents app)
         (filter (partial applicant-doc? applicant-schema-name))
         (filter henkilo-doc?)
         (map pick-henkilo-data)
         (map (partial merge info)))))

(def private-applicants-row-fn
  (juxt :id-link :id :address :primaryOperation :etunimi :sukunimi :osoite :puhelin :suoramarkkinointilupa :sahkoposti :turvakielto))

(defn company-applicants [app lang] [])
(defn designers [app lang] [])
(defn foremen [app lang] [])
