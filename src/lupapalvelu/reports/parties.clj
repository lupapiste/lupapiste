(ns lupapalvelu.reports.parties
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n :refer [with-lang loc]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.reports.excel :as excel]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.validators :as v]))


(defn make-app-link
  ([id lang]
   (ss/join "/" [(env/value :host)
                 "app"
                 (name (or lang "fi"))
                 "authority#!/application" id]))
  ([id]
   (make-app-link id :fi)))

(defn basic-info-localized [{id :id :as app} lang]
  (-> (select-keys app [:id :address :primaryOperation])
      (assoc :id (excel/hyperlink-str (make-app-link id lang) id))
      (update :primaryOperation #(i18n/localize lang (str "operations." (:name %))))))

(def basic-info-localization-mapping
  {:id "application.id"
   :address "applications.location"
   :primaryOperation "applications.operation"})

(def basic-info-keys [:id :address :primaryOperation])

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


(def applicants-fields-localization-mapping
  (merge
    basic-info-localization-mapping
    {:etunimi "etunimi" :sukunimi "sukunimi" :osoite  "osoite" :puhelin "userinfo.phone" :sahkoposti "email"
     :suoramarkkinointilupa "suoramarkkinointilupa" :turvakielto "turvakielto"
     :yritysnimi "company"  :yhteyshenkiloetunimi "yhteyshenkilo.etunimi" :yhteyshenkilosukunimi "yhteyshenkilo.sukunimi"
     :yhteyshenkilopuhelin "yhteyshenkilo.puhelin" :yhteyshenkilosahkoposti "yhteyshenkilo.sahkoposti"}))

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

(def private-applicants-fields
  [:etunimi :sukunimi :osoite :puhelin :sahkoposti
   :suoramarkkinointilupa :turvakielto])

(def private-applicants-row-fn
  (apply juxt (concat basic-info-keys private-applicants-fields)))

;;
;; Company applicants
;;

(defn company-applicants [app lang]
  (map
    (partial merge (basic-info-localized app lang))
    (applicants yritys-doc? pick-company-data app)))

(def company-applicants-fields
  [:yritysnimi :osoite :yhteyshenkiloetunimi :yhteyshenkilosukunimi
   :yhteyshenkilopuhelin :yhteyshenkilosahkoposti :suoramarkkinointilupa
   :turvakielto])

(def company-applicants-row-fn
  (apply juxt (concat basic-info-keys company-applicants-fields)))

(defn applicants-field-localization [type lang]
  (map
    #(i18n/localize lang (get applicants-fields-localization-mapping %))
    (concat
      basic-info-keys
      (case (keyword type)
        :private private-applicants-fields
        :company company-applicants-fields))))

;;
;; Designers
;;

(defn- get-fise-link [data]
  (let [fise-val (get-in data [:patevyys :fise])]
    (if (and (not (ss/blank? fise-val)) (v/http-url? fise-val))
      (str "=HYPERLINK(\"" fise-val \" ",\"" fise-val "\")")
      fise-val)))

(defn- select-with-ei-tiedossa-loc [key-path loc-prefix doc-data]
  (when-let [value (get-in doc-data key-path)]
    (case value
      "" ""
      "ei tiedossa" (loc "ei-tiedossa")
      (loc loc-prefix value))))

(defn- get-koulutus-valinta [doc-data]
  (let [value (get-in doc-data [:patevyys :koulutusvalinta])]
    (when-not (ss/blank? value)
      (loc "koulutus" value))))

(defn- get-suunnittelija-rooli-loc [doc-data]
  (let [kuntaRoolikoodi-schema (-> schemas/kuntaroolikoodi
                                   first
                                   :body)
        loc-keys-mapping (zipmap (map :name kuntaRoolikoodi-schema) (map :i18nkey kuntaRoolikoodi-schema))
        kuntaroolikoodi (get doc-data :kuntaRoolikoodi)]
    (when-not (ss/blank? kuntaroolikoodi)
      (loc (get loc-keys-mapping kuntaroolikoodi)))))

(defn pick-designer-data
  [lang doc]
  (with-lang lang
    (let [data (tools/unwrapped (get doc :data))]
      {:etunimi               (get-in data [:henkilotiedot :etunimi])
       :sukunimi              (get-in data [:henkilotiedot :sukunimi])
       :rooli                 (case (get-in doc [:schema-info :name])
                                "paasuunnittelija" (loc "osapuoli.suunnittelija.kuntaRoolikoodi.p\u00e4\u00e4suunnittelija")
                                "suunnittelija" (get-suunnittelija-rooli-loc data))
       :suunnittelu-vaativuus (select-with-ei-tiedossa-loc
                                [:suunnittelutehtavanVaativuusluokka]
                                "osapuoli.suunnittelutehtavanVaativuusluokka"
                                data)
       :patevyysluokka        (select-with-ei-tiedossa-loc [:patevyys :patevyysluokka] "osapuoli.patevyys.patevyysluokka" data)
       :patevyys              (get-in data [:patevyys :patevyys])
       :osoite                (get-osoite data)
       :puhelin               (get-in data [:yhteystiedot :puhelin])
       :sahkoposti            (get-in data [:yhteystiedot :email])
       :fise                  (get-fise-link data)
       :tutkinto              (get-koulutus-valinta data)
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
  [:etunimi :sukunimi :rooli
   :suunnittelu-vaativuus :patevyysluokka :patevyys
   :osoite :puhelin :sahkoposti
   :fise :tutkinto :valmistumisvuosi])

(defn designer-fields-localized [lang]
  (map #(i18n/localize lang (get designer-fields-localization-mapping %)) (concat basic-info-keys designers-fields)))

(def designers-row-fn
  (apply juxt (concat basic-info-keys designers-fields)))

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
  [lang doc]
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
     :sijaisuus-paattyy (get-in data [:sijaistus :paattymisPvm])
     :vastattavat-tyotehtavat (ss/join "," (foreman/vastattavat-tyotehtavat doc lang))}))


(defn foremen [app lang]
  (merge (basic-info-localized app lang)
         (pick-foreman-data lang (foreman/get-foreman-document app) )))

(def foreman-fields-localization-mapping
  (merge
    basic-info-localization-mapping
    {:yritys "company"
     :etunimi "etunimi" :sukunimi "sukunimi" :osoite  "osoite" :puhelin "userinfo.phone" :sahkoposti "email"
     :rooli "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label"
     :vaativuus "osapuoli.patevyys-tyonjohtaja.patevyysvaatimusluokka._group_label"
     :taysiaikainenOsaaikainen "osapuoli.tyonjohtajaHanketieto.taysiaikainenOsaaikainen"
     :tutkinto "osapuoli.patevyys.koulutus"
     :sijaistettava "tyonjohtaja.sijaistus._group_label"  :sijaisuus-alkaa "tyonjohtaja.sijaistus.alkamisPvm"
     :sijaisuus-paattyy "tyonjohtaja.sijaistus.paattymisPvm"
     :vastattavat-tyotehtavat "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"}))

(def foremen-fields
  [:yritys :etunimi :sukunimi
   :osoite :puhelin :sahkoposti
   :rooli :vaativuus :taysiaikainenOsaaikainen :tutkinto
   :sijaistettava :sijaisuus-alkaa :sijaisuus-paattyy
   :vastattavat-tyotehtavat])

(defn foreman-fields-lozalized [lang]
  (map #(i18n/localize lang (get foreman-fields-localization-mapping %)) (concat basic-info-keys foremen-fields)))

(def foremen-row-fn
  (apply juxt (concat basic-info-keys foremen-fields)))
