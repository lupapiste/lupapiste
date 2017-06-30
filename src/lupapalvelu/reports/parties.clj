(ns lupapalvelu.reports.parties
  (:require [sade.env :as env]
            [sade.strings :as ss]
            [monger.operators :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n :refer [with-lang loc]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]))


(defn make-app-link [id lang]
  (str (env/value :host) "/app/" lang "/authority" "#!/application/" id))

(defn basic-info-localized [app lang]
  (-> (select-keys app [:id :address :primaryOperation])
      (assoc :id-link (make-app-link (:id app) lang))
      (update :primaryOperation #(i18n/localize lang (str "operations." (:name %))))))

(def basic-info-localization-mapping
  {:id-link "application.id"
   :id "application.id"
   :address "applications.location"
   :primaryOperation "applications.operation"})

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
  [lang doc]
  (with-lang lang
    (let [data (tools/unwrapped (get doc :data))]
      {:etunimi               (get-in data [:henkilotiedot :etunimi])
       :sukunimi              (get-in data [:henkilotiedot :sukunimi])
       :rooli                 (case (get-in doc [:schema-info :name])
                                "paasuunnittelija" (loc "osapuoli.suunnittelija.kuntaRoolikoodi.p\u00e4\u00e4suunnittelija")
                                "suunnittelija" (i18n/localize-fallback
                                                  lang [(str "osapuoli.suunnittelija.kuntaRoolikoodi." (get data :kuntaRoolikoodi))
                                                        (str "osapuoli.kuntaRoolikoodi." (get data :kuntaRoolikoodi))]))
       :suunnittelu-vaativuus (loc "osapuoli.suunnittelutehtavanVaativuusluokka" (get-in data [:suunnittelutehtavanVaativuusluokka]))
       :patevyysluokka        (loc "osapuoli.patevyys.patevyysluokka" (get-in data [:patevyys :patevyysluokka]))
       :patevyys              (get-in data [:patevyys :patevyys])
       :osoite                (get-osoite data)
       :puhelin               (get-in data [:yhteystiedot :puhelin])
       :sahkoposti            (get-in data [:yhteystiedot :email])
       :fise                  (get-in data [:patevyys :fise])
       :tutkinto              (loc "koulutus" (get-in data [:patevyys :koulutusvalinta]))
       :valmistumisvuosi      (get-in data [:patevyys :valmistumisvuosi])})))

(defn designers [app lang]
  (map
    (partial merge (basic-info-localized app lang))
    (->> (domain/get-documents-by-subtype (:documents app) "suunnittelija")
         (map (partial pick-designer-data lang)))))

(def designer-fields-localization-mapping
  (merge
    basic-info-localization-mapping
    {:etunimi "etunimi" :sukunimi "sukunimi" :osoite  "osoite" :puhelin "userinfo.phone" :sahkoposti "email"
     :rooli "osapuoli.suunnittelija.kuntaRoolikoodi._group_label"
     :suunnittelu-vaativuus "osapuoli.suunnittelutehtavanVaativuusluokka._group_label"
     :patevyysluokka "osapuoli.patevyys.patevyysluokka._group_label" :patevyys "osapuoli.patevyys.patevyys"
     :tutkinto "osapuoli.patevyys.koulutus" :fise "osapuoli.patevyys.fise"
     :valmistumisvuosi "osapuoli.patevyys.valmistumisvuosi"
     :vastattavat-tyotehtavat "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"}))

(def designers-fields
  [:id-link :id :address :primaryOperation
   :etunimi :sukunimi :rooli
   :suunnittelu-vaativuus :patevyysluokka :patevyys
   :osoite :puhelin :sahkoposti
   :fise :tutkinto :valmistumisvuosi])

(defn designer-fields-localized [lang]
  (map #(i18n/localize lang (get designer-fields-localization-mapping %)) designers-fields))

(def designers-row-fn
  (apply juxt designers-fields))

;;
;; Foremen
;;


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


(defn- get-sijaistettava-nimi [doc-data]
  (str (get-in doc-data [:sijaistus :sijaistettavaHloEtunimi])
       " "
       (get-in doc-data [:sijaistus :sijaistettavaHloSukunimi])))

(defn- get-localized-taysiaikainen-osaaikainen [doc-data lang]
  (i18n/localize lang
                 "osapuoli.tyonjohtajaHanketieto.taysiaikainenOsaaikainen"
                 (get-in doc-data [:tyonjohtajaHanketieto :taysiaikainenOsaaikainen])))

(defn- get-foreman-koulutus [doc-data lang]
  (let [koulutusvalinta (get-in doc-data [:patevyys-tyonjohtaja :koulutusvalinta])]
    (if (or (ss/blank? koulutusvalinta) (= "muu" koulutusvalinta))
      (get-in doc-data [:patevyys-tyonjohtaja :koulutus])
      (i18n/localize lang "koulutus" koulutusvalinta))))

(defn pick-foreman-data
  "Doc: tyonjohtaja-v2"
  [doc lang]
  (let [data (tools/unwrapped (get doc :data))]
    {:yritys   (get-in data [:yritys :yritysnimi])
     :etunimi (get-in data [:henkilotiedot :etunimi])
     :sukunimi (get-in data [:henkilotiedot :sukunimi])
     :osoite   (get-osoite data)
     :puhelin  (get-in data [:yhteystiedot :puhelin])
     :sahkoposti (get-in data [:yhteystiedot :email])
     :rooli    (i18n/localize lang "osapuoli.tyonjohtaja.kuntaRoolikoodi" (get-in data [:kuntaRoolikoodi]))
     :vaativuus (get-in data [:patevyysvaatimusluokka])
     :taysiaikainenOsaaikainen (get-localized-taysiaikainen-osaaikainen data lang)
     :tutkinto        (get-foreman-koulutus data lang)
     :sijaistettava   (get-sijaistettava-nimi data)
     :sijaisuus-alkaa (get-in data [:sijaistus :alkamisPvm])
     :sijaisuus-paattyy (get-in data [:sijaistus :paatymisPvm])
     :vastattavat-tyotehtavat (vastattavat-tyotehtavat-as-string doc lang)}))


(defn foremen [app lang]
  (merge (basic-info-localized app lang)
         (pick-foreman-data (foreman/get-foreman-document app) lang)))

(def foreman-fields-localization-mapping
  (merge
    basic-info-localization-mapping
    {:yritys "userinfo.architect.company.name"
     :etunimi "etunimi" :sukunimi "sukunimi" :osoite  "osoite" :puhelin "userinfo.phone" :sahkoposti "email"
     :rooli "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label" :vaativuus "osapuoli.patevyys-tyonjohtaja.patevyysvaatimusluokka._group_label"
     :taysiaikainenOsaaikainen "osapuoli.tyonjohtajaHanketieto.taysiaikainenOsaaikainen" :tutkinto "osapuoli.patevyys.koulutus"
     :sijaistettava "tyonjohtaja.sijaistus._group_label"  :sijaisuus-alkaa "tyonjohtaja.sijaistus.alkamisPvm"
     :sijaisuus-paattyy "tyonjohtaja.sijaistus.paattymisPvm"
     :vastattavat-tyotehtavat "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"}))

(def foremen-fields
  [:id-link :id :address :primaryOperation
   :yritys :etunimi :sukunimi
   :osoite :puhelin :sahkoposti
   :rooli :vaativuus :taysiaikainenOsaaikainen :tutkinto
   :sijaistettava :sijaisuus-alkaa :sijaisuus-paattyy
   :vastattavat-tyotehtavat])

(defn foreman-fields-lozalized [lang]
  (map #(i18n/localize lang (get foreman-fields-localization-mapping %)) foremen-fields))

(def foremen-row-fn
  (apply juxt foremen-fields))
