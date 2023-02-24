(ns lupapalvelu.backing-system.krysp.reader
  "Read the Krysp from municipality Web Feature Service"
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.backing-system.krysp.common-reader :as common]
            [lupapalvelu.backing-system.krysp.verdict :as verdict]
            [lupapalvelu.document.schemas]
            [lupapalvelu.drawing :as drawing]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.json :as json]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.property :as prop]
            [lupapalvelu.proxy-services :as proxy-services]
            [lupapalvelu.wfs :as wfs]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.coordinate :as coordinate]
            [sade.core :refer [now def- fail]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.property :as sprop]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [sade.xml :refer :all]
            [taoensso.timbre :refer [trace warn error errorf]])
  (:import [clojure.lang APersistentVector]))

(defn- post-body-for-ya-application [ids id-path]
  (let [filter-content (->> (wfs/property-in id-path ids)
                            (element-to-string))]
    {:body (str "<wfs:GetFeature service=\"WFS\"
        version=\"1.1.0\"
        outputFormat=\"GML2\"
        xmlns:yak=\"http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus\"
        xmlns:wfs=\"http://www.opengis.net/wfs\"
        xmlns:ogc=\"http://www.opengis.net/ogc\"
        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">
        <wfs:Query typeName=\"yak:Sijoituslupa,yak:Kayttolupa,yak:Liikennejarjestelylupa,yak:Tyolupa\">
          <ogc:Filter>"
                filter-content
         "</ogc:Filter>
         </wfs:Query>
       </wfs:GetFeature>")}))

(defn- application-xml [type-name id-path server credentials ids raw?]
  (let [url (common/wfs-krysp-url-with-service server type-name (common/property-in id-path ids))
        xml-timeout (or (env/value :kuntagml :conn-timeout) 15000)]
    (trace "Get application: " url)
    (cr/get-xml url {:debug-event true :conn-timeout xml-timeout} credentials raw?)))

(defn rakval-application-xml [server credentials ids search-type raw?]
  (application-xml common/rakval-case-type (common/get-tunnus-path permit/R search-type)    server credentials ids raw?))

(defn poik-application-xml   [server credentials ids search-type raw?]
  (application-xml common/poik-case-type   (common/get-tunnus-path permit/P search-type)    server credentials ids raw?))

(defn yl-application-xml     [server credentials ids search-type raw?]
  (application-xml common/yl-case-type     (common/get-tunnus-path permit/YL search-type)   server credentials ids raw?))

(defn mal-application-xml    [server credentials ids search-type raw?]
  (application-xml common/mal-case-type    (common/get-tunnus-path permit/MAL search-type)  server credentials ids raw?))

(defn vvvl-application-xml   [server credentials ids search-type raw?]
  (application-xml common/vvvl-case-type   (common/get-tunnus-path permit/VVVL search-type) server credentials ids raw?))

(defn ya-application-xml     [server credentials ids search-type raw?]
  (let [options (post-body-for-ya-application ids (common/get-tunnus-path permit/YA search-type))]
    (trace "Get application: " server " with post body: " options )
    (cr/get-xml-with-post server options credentials raw?)))

(defn kt-application-xml   [server credentials ids search-type raw?]
  (application-xml common/kt-types (common/get-tunnus-path permit/KT search-type) server credentials ids raw?))

(defmethod permit/fetch-xml-from-krysp :R    [_ & args] (apply rakval-application-xml args))
(defmethod permit/fetch-xml-from-krysp :P    [_ & args] (apply poik-application-xml args))
(defmethod permit/fetch-xml-from-krysp :YA   [_ & args] (apply ya-application-xml args))
(defmethod permit/fetch-xml-from-krysp :YL   [_ & args] (apply yl-application-xml args))
(defmethod permit/fetch-xml-from-krysp :MAL  [_ & args] (apply mal-application-xml args))
(defmethod permit/fetch-xml-from-krysp :VVVL [_ & args] (apply vvvl-application-xml args))
(defmethod permit/fetch-xml-from-krysp :KT   [_ & args] (apply kt-application-xml args))

;;
;; Mappings from KRYSP to Lupapiste domain
;;

(defn- extract-vaadittuErityissuunnitelma-elements [lupamaaraykset]
  (let [vaaditut-erityissuunnitelmat-217 (->> (:vaadittuErityissuunnitelmatieto lupamaaraykset)
                                              (map (comp :vaadittuErityissuunnitelma :VaadittuErityissuunnitelma))
                                              (remove nil?)
                                              seq)
        vaaditut-erityissuunnitelmat-216 (->> (:vaadittuErityissuunnitelmatieto lupamaaraykset)
                                              (map :vaadittuErityissuunnitelma)
                                              seq)
        vaaditut-erityissuunnitelmat-215 (:vaadittuErityissuunnitelma lupamaaraykset)
        vaadittuErityissuunnitelma-array (->> (or vaaditut-erityissuunnitelmat-217
                                                  vaaditut-erityissuunnitelmat-216
                                                  vaaditut-erityissuunnitelmat-215)
                                              (map ss/trim)
                                              (remove ss/blank?))]

    ;; resolving Tekla way of giving vaadittuErityissuunnitelmas: one "vaadittuErityissuunnitelma" with line breaks is divided into multiple "vaadittuErityissuunnitelma"s
    (if (and
          (= 1 (count vaadittuErityissuunnitelma-array))
          (-> ^String (first vaadittuErityissuunnitelma-array) (.indexOf "\n") (>= 0)))
      (-> vaadittuErityissuunnitelma-array first (ss/split #"\n") ((partial remove ss/blank?)))
      vaadittuErityissuunnitelma-array)))

(defn- extract-maarays-elements [lupamaaraykset]
  (let [maaraykset (or
                     (->> lupamaaraykset :maaraystieto (map :Maarays) seq)  ;; Yhteiset Krysp 2.1.6 ->
                     (:maarays lupamaaraykset))]                            ;; Yhteiset Krysp -> 2.1.5
    (->> (cr/convert-keys-to-timestamps maaraykset [:maaraysaika :maaraysPvm :toteutusHetki])
      (map #(rename-keys % {:maaraysPvm :maaraysaika}))
      (remove nil?))))

(defn extract-muu-tunnus [muu-tunnus]
  {:muuTunnus (:tunnus muu-tunnus "")
   :muuTunnusSovellus (:sovellus muu-tunnus "")})

(defn ->lupamaaraykset [paatos-xml-without-ns]
  (-> (cr/all-of paatos-xml-without-ns :lupamaaraykset)
    (cr/cleanup)

    ;; KRYSP yhteiset 2.1.5+
    (util/ensure-sequential :vaadittuErityissuunnitelma)
    (util/ensure-sequential :vaadittuErityissuunnitelmatieto)
    (#(let [vaaditut-es (extract-vaadittuErityissuunnitelma-elements %)]
        (if (seq vaaditut-es) (assoc % :vaaditutErityissuunnitelmat vaaditut-es) %)))
    (dissoc :vaadittuErityissuunnitelma :vaadittuErityissuunnitelmatieto)

    (util/ensure-sequential :vaaditutKatselmukset)
    (#(let [kats (->> (map :Katselmus (:vaaditutKatselmukset %))
                      (map (fn [katselmus] (-> katselmus
                                               (merge (-> katselmus :muuTunnustieto :MuuTunnus extract-muu-tunnus))
                                               (dissoc :muuTunnustieto)))))]
        (if (seq kats)
          (assoc % :vaaditutKatselmukset kats)
          (dissoc % :vaaditutKatselmukset))))

    ; KRYSP yhteiset 2.1.1+
    (util/ensure-sequential :vaadittuTyonjohtajatieto)
    (#(let [tyonjohtajat (map
                           (comp (fn [tj] (util/some-key tj :tyonjohtajaLaji :tyonjohtajaRooliKoodi)) :VaadittuTyonjohtaja)  ;; "tyonjohtajaRooliKoodi" in KRYSP Yhteiset 2.1.6->
                           (:vaadittuTyonjohtajatieto %))]
        (if (seq tyonjohtajat)
          (-> %
            (assoc :vaadittuTyonjohtajatieto tyonjohtajat)
            ; KRYSP yhteiset 2.1.0 and below have vaaditutTyonjohtajat key that contains the same data in a single string.
            ; Convert the new format to the old.
            (assoc :vaaditutTyonjohtajat (ss/join ", " tyonjohtajat)))
          (dissoc % :vaadittuTyonjohtajatieto))))

    (util/ensure-sequential :maarays)
    (util/ensure-sequential :maaraystieto)
    (#(if-let [maaraykset (seq (extract-maarays-elements %))]
        (assoc % :maaraykset maaraykset)
        %))
    (dissoc :maarays :maaraystieto)

    (cr/convert-double-to-int :kokonaisala)
    (cr/convert-double-to-int :kerrosala)

    (cr/convert-keys-to-ints [:autopaikkojaEnintaan
                              :autopaikkojaVahintaan
                              :autopaikkojaRakennettava
                              :autopaikkojaRakennettu
                              :autopaikkojaKiinteistolla
                              :autopaikkojaUlkopuolella])))

(defn- ->lupamaaraykset-text [paatos-xml-without-ns]
  (let [lupaehdot (select paatos-xml-without-ns :lupaehdotJaMaaraykset)]
    (when (not-empty lupaehdot)
      (-> lupaehdot
        (cr/cleanup)
        ((fn [maar] (map #(get-text % :lupaehdotJaMaaraykset) maar)))
        (util/ensure-sequential :lupaehdotJaMaaraykset)))))

(defn- get-pvm-dates [paatos v]
  (into {} (map #(let [xml-kw (keyword (str (name %) "Pvm"))]
                   [% (date/timestamp (get-text paatos xml-kw))]) v)))

(defn- ->liite [{:keys [metatietotieto] :as liite}]
  (-> liite
    (assoc  :metadata (into {} (map
                                 (fn [{meta :metatieto}]
                                   [(keyword (:metatietoNimi meta)) (:metatietoArvo meta)])
                                 (if (sequential? metatietotieto) metatietotieto [metatietotieto]))))
    (dissoc :metatietotieto)
    (cr/convert-keys-to-timestamps [:muokkausHetki])))

(defn- ->paatospoytakirja [paatos-xml-without-ns]
  (-> (cr/all-of paatos-xml-without-ns :poytakirja)
    (cr/convert-keys-to-ints [:pykala])
    (cr/convert-keys-to-timestamps [:paatospvm])
    (#(assoc % :status (verdict/verdict-id (:paatoskoodi %))))
    (#(update-in % [:liite] ->liite))))

(defn- newest-poytakirja-with-attrs [poytakirjat attrs]
  (->> poytakirjat
       (sort-by :paatospvm)
       (reverse)
       (some #(when (every? (partial get %) attrs)
                %))))

(defn- poytakirja-with-paatos-data [poytakirjat]
  (newest-poytakirja-with-attrs poytakirjat [:paatoskoodi :paatoksentekija :paatospvm]))

(defn- poytakirja-with-paatos-data-for-ark [poytakirjat]
  (newest-poytakirja-with-attrs poytakirjat [:paatoskoodi :paatospvm]))

(defn- kuntagml-missing-date-ts?
  "Returns true, if given timestamp converts to XML date '1001-01-01.
  This is specified as 'missing date' by KuntaGML spec: https://www.paikkatietopalvelu.fi/gml/KuntaGML.html"
  [date-ts]
  (= (date/iso-date date-ts :local) "1001-01-01"))

(defn- valid-paatospvm? [paatos-pvm]
  (and
    (date/after? (date/today) paatos-pvm)
    (not (kuntagml-missing-date-ts? paatos-pvm))))

(defn- valid-antopvm? [anto-pvm]
  (or (not anto-pvm)
      (and (> (now) anto-pvm)
           (not (kuntagml-missing-date-ts? anto-pvm)))))

(def krysp-verdict-dates [:aloitettava :lainvoimainen :voimassaHetki :raukeamis :anto :viimeinenValitus :julkipano])

(defn- standard-verdicts-validator [xml {validate-verdict-given-date :validate-verdict-given-date}]
  (let [paatos-xml-without-ns (select (cr/strip-xml-namespaces xml) [:paatostieto :Paatos])
        poytakirjat (map ->paatospoytakirja (select paatos-xml-without-ns [:poytakirja]))
        poytakirja  (poytakirja-with-paatos-data poytakirjat)
        paivamaarat (map #(get-pvm-dates % krysp-verdict-dates) paatos-xml-without-ns)]
    (cond
      (not (seq poytakirjat))                               (fail :info.no-verdicts-found-from-backend)
      (not (seq poytakirja))                                (fail :info.paatos-details-missing)
      (not (valid-paatospvm? (:paatospvm poytakirja)))      (fail :info.paatos-future-date)
      (and validate-verdict-given-date
        (not-any? #(valid-antopvm? (:anto %)) paivamaarat)) (fail :info.paatos-future-date))))

(defn- ->standard-verdicts [xml-without-ns & [organization paatos-data-fn _]]
  (let [{validate-verdict-given-date :validate-verdict-given-date} organization]
    (map (fn [paatos-xml-without-ns]
           (let [paatos-data-fn   (or paatos-data-fn poytakirja-with-paatos-data)
                 poytakirjat      (map ->paatospoytakirja (select paatos-xml-without-ns [:poytakirja]))
                 poytakirja       (paatos-data-fn poytakirjat)
                 paivamaarat      (get-pvm-dates paatos-xml-without-ns krysp-verdict-dates)]
             (when (and poytakirja (valid-paatospvm? (:paatospvm poytakirja)) (or (not validate-verdict-given-date)
                                                                                  (valid-antopvm? (:anto paivamaarat))))
               {:lupamaaraykset (->lupamaaraykset paatos-xml-without-ns)
                :paivamaarat    paivamaarat
                :poytakirjat    (seq poytakirjat)})))
         (select xml-without-ns [:paatostieto :Paatos]))))

(defn- ->standard-ark-verdicts [xml-without-ns & [organization _]]
  (->standard-verdicts xml-without-ns organization poytakirja-with-paatos-data-for-ark))

;; TJ/Suunnittelija verdict

(def- tj-suunnittelija-verdict-statuses-to-loc-keys-mapping
  {"hyv\u00e4ksytty" "hyvaksytty"
   "hyl\u00e4tty" "hylatty"
   "hakemusvaiheessa" "hakemusvaiheessa"
   "ilmoitus hyv\u00e4ksytty" "ilmoitus-hyvaksytty"})

(def- tj-suunnittelija-verdict-statuses
  (-> tj-suunnittelija-verdict-statuses-to-loc-keys-mapping keys set))

(defn- ->paatos-osapuoli [path-key osapuoli-xml-without-ns]
  (-> (cr/all-of osapuoli-xml-without-ns path-key)
    (cr/convert-keys-to-timestamps [:paatosPvm])))

(defn- valid-sijaistustieto? [osapuoli sijaistus]
  (when osapuoli
    (or
     (empty? sijaistus) ; sijaistus only used with foreman roles
     (and ; sijaistettava must be empty in both, KRSYP and document
       (ss/blank? (:sijaistettavaHlo osapuoli))
       (and
         (ss/blank? (:sijaistettavaHloEtunimi sijaistus))
         (ss/blank? (:sijaistettavaHloSukunimi sijaistus))))
     (and ; .. or dates and input values of KRYSP xml must match document values
       (= (date/xml-date (:alkamisPvm osapuoli))
          (date/xml-date (:alkamisPvm sijaistus)))
       (= (date/xml-date (:paattymisPvm osapuoli))
          (date/xml-date (:paattymisPvm sijaistus)))
       (=
         (ss/trim (:sijaistettavaHlo osapuoli))
         (str ; original string build in canonical-common 'get-sijaistustieto'
           (ss/trim (:sijaistettavaHloEtunimi sijaistus))
           " "
           (ss/trim (:sijaistettavaHloSukunimi sijaistus))))))))

(defn- party-with-paatos-data [osapuolet sijaistus]
  (some
    #(when (and
             (:paatosPvm %)
             (tj-suunnittelija-verdict-statuses (:paatostyyppi %))
             (valid-sijaistustieto? % sijaistus))
       %)
    osapuolet))

(def- osapuoli-path-key-mapping
  {"tyonjohtaja"   {:path [:tyonjohtajatieto :Tyonjohtaja]
                    :key :tyonjohtajaRooliKoodi}
   "suunnittelija" {:path [:suunnittelijatieto :Suunnittelija]
                    :key :suunnittelijaRoolikoodi}})

(defn- get-tj-suunnittelija-osapuolet
  "Returns parties which match with given kuntaRoolikoodi and yhteystiedot, and have paatosPvm"
  [xml-without-ns osapuoli-path osapuoli-key kuntaRoolikoodi-key kuntaRoolikoodi yhteystiedot]
  (->> (select xml-without-ns osapuoli-path)
    (map (partial ->paatos-osapuoli osapuoli-key))
    (filter #(and
               (= kuntaRoolikoodi (get % kuntaRoolikoodi-key))
               (:paatosPvm %)
               (= (:email yhteystiedot) (get-in % [:henkilo :sahkopostiosoite]))))))

(defn tj-suunnittelija-verdicts-validator [{{:keys [yhteystiedot sijaistus]} :data} xml osapuoli-type kuntaRoolikoodi]
  {:pre [xml (#{"tyonjohtaja" "suunnittelija"} osapuoli-type) kuntaRoolikoodi]}
  (let [{osapuoli-path       :path
         kuntaRoolikoodi-key :key} (osapuoli-path-key-mapping osapuoli-type)
        osapuoli-key               (last osapuoli-path)
        xml-without-ns             (cr/strip-xml-namespaces xml)
        osapuolet                  (get-tj-suunnittelija-osapuolet xml-without-ns osapuoli-path osapuoli-key kuntaRoolikoodi-key kuntaRoolikoodi yhteystiedot)
        osapuoli                   (party-with-paatos-data osapuolet sijaistus)
        paatospvm                  (:paatosPvm osapuoli)]
    (cond
      (not (seq osapuolet))                       (fail :info.no-verdicts-found-from-backend)
      (not (seq osapuoli))                        (fail :info.tj-suunnittelija-paatos-details-missing)
      (not (date/before? paatospvm (date/today))) (fail :info.paatos-future-date))))

(defn ->tj-suunnittelija-verdicts [xml-without-ns {{:keys [yhteystiedot sijaistus]} :data} osapuoli-type kuntaRoolikoodi]
  (let [{osapuoli-path :path kuntaRoolikoodi-key :key} (osapuoli-path-key-mapping osapuoli-type)
        osapuoli-key (last osapuoli-path)]
    (map (fn [_]
           (let [osapuolet (get-tj-suunnittelija-osapuolet xml-without-ns osapuoli-path osapuoli-key kuntaRoolikoodi-key kuntaRoolikoodi yhteystiedot)
                 osapuoli (party-with-paatos-data osapuolet sijaistus)]
             (when (and osapuoli (> (now) (:paatosPvm osapuoli)))
               {:poytakirjat
                [{:status           (get tj-suunnittelija-verdict-statuses-to-loc-keys-mapping (:paatostyyppi osapuoli))
                  :paatoksentekija  (:paatoksentekija osapuoli)
                  :paatospvm        (:paatosPvm osapuoli)
                  :pykala           (:pykala osapuoli)
                  :liite            (->> osapuoli :tyonjohtajaPaatosLiitetieto :Liite)}]})))
         (select xml-without-ns [:osapuolettieto :Osapuolet]))))

(def ^APersistentVector krysp-state-sorting
  "Chronological ordering of application states from KuntaGML. Used when comparing states timestamped to same dates."
  ["rakennusty\u00f6t aloitettu"
   "rakennusty\u00f6t keskeytetty"
   "jatkoaika my\u00f6nnetty"
   "osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on k\u00e4ytt\u00f6\u00f6notettu"
   "p\u00e4\u00e4t\u00f6ksest\u00e4 valitettu, valitusprosessin tulosta ei ole"
   "lupa vanhentunut"
   "lupa rauennut"
   "luvalla ei loppukatselmusehtoa, lupa valmis"
   "lopullinen loppukatselmus tehty"])

(defn state-comparator [{pvm1 :pvm tila1 :tila} {pvm2 :pvm tila2 :tila}]
  (if (not= pvm1 pvm2)
    (compare pvm1 pvm2)                                     ; Compare by dates
    (compare (.indexOf krysp-state-sorting tila1)           ; If same date, sorting by state precedence defined by domain
             (.indexOf krysp-state-sorting tila2))))

(def krysp-state->application-state
  {"rakennusty\u00f6t aloitettu"                 :constructionStarted
   "rakennusty\u00f6t keskeytetty"               :onHold
   "p\u00e4\u00e4t\u00f6ksest\u00e4 valitettu, valitusprosessin tulosta ei ole" nil
   "lupa rauennut"                               :extinct
   "jatkoaika my\u00f6nnetty"                    nil
   "osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on k\u00e4ytt\u00f6\u00f6notettu" :inUse
   "lupa vanhentunut"                            nil
   "lopullinen loppukatselmus tehty"             :closed
   "luvalla ei loppukatselmusehtoa, lupa valmis" :closed})

(assert (= (set (keys krysp-state->application-state))
           (set krysp-state-sorting))
        "Ordering must be defined for state!")

(defmulti application-state
  "Get application state from xml."
  {:arglists '([xml-without-ns])}
  :tag)

(defn simple-application-state [xml-without-ns]
  (->> (select xml-without-ns [:Kasittelytieto])
    (map (fn [kasittelytieto] (-> (cr/all-of kasittelytieto) (cr/convert-keys-to-timestamps [:muutosHetki]))))
    (filter :hakemuksenTila) ;; this because hakemuksenTila is optional in Krysp, and can be nil
    (sort-by :muutosHetki)
    last
    :hakemuksenTila
    ss/lower-case))

(defmethod application-state :default [xml-without-ns] (simple-application-state xml-without-ns))

(defn get-sorted-tilamuutos-entries [xml-without-ns]
  (->> (select xml-without-ns [:kasittelynTilatieto :Tilamuutos])
       (map (fn-> cr/all-of (cr/convert-keys-to-timestamps [:pvm])))
       (sort state-comparator)))

(defn standard-application-state [xml-without-ns]
  (->> (get-sorted-tilamuutos-entries xml-without-ns)
       last
       :tila
       ss/lower-case))

(defmethod application-state :Rakennusvalvonta [xml-without-ns] (standard-application-state xml-without-ns))
(defmethod application-state :RakennusvalvontaAsia [xml-without-ns] (standard-application-state xml-without-ns))
(defmethod application-state :Popast [xml-without-ns] (standard-application-state xml-without-ns))
(defmethod application-state :FeatureCollection [xml-without-ns] (standard-application-state xml-without-ns))

(def backend-preverdict-state
  #{"" "luonnos" "hakemus" "valmistelussa" "vastaanotettu" "tarkastettu, t\u00e4ydennyspyynt\u00f6"})

(defn- simple-verdicts-validator [xml & verdict-date-path]
  (let [verdict-date-path (or verdict-date-path [:paatostieto :Paatos :paatosdokumentinPvm])
        xml-without-ns (cr/strip-xml-namespaces xml)
        app-state      (application-state xml-without-ns)
        paivamaarat    (filter number? (map (comp date/timestamp get-text) (select xml-without-ns verdict-date-path)))
        max-date       (when (seq paivamaarat) (apply max paivamaarat))
        pre-verdict?   (contains? backend-preverdict-state app-state)]
    (cond
      (nil? xml)         (fail :info.no-verdicts-found-from-backend)
      pre-verdict?       (fail :info.application-backend-preverdict-state)
      (nil? max-date)    (fail :info.paatos-date-missing)
      (< (now) max-date) (fail :info.paatos-future-date))))

(defn- ->simple-verdicts [xml-without-ns]
  ;; using the newest app state in the message
  (let [app-state (application-state xml-without-ns)]
    (when-not (contains? backend-preverdict-state app-state)
      (map (fn [paatos-xml-without-ns]
             (let [paatosdokumentinPvm-timestamp (date/timestamp (get-text paatos-xml-without-ns :paatosdokumentinPvm))]
               (when (and paatosdokumentinPvm-timestamp (> (now) paatosdokumentinPvm-timestamp))
                 {:lupamaaraykset {:takuuaikaPaivat (get-text paatos-xml-without-ns :takuuaikaPaivat)
                                   :muutMaaraykset (->lupamaaraykset-text paatos-xml-without-ns)}
                  :paivamaarat    {:paatosdokumentinPvm paatosdokumentinPvm-timestamp}
                  :poytakirjat    (when-let [liitetiedot (seq (select paatos-xml-without-ns [:liitetieto]))]
                                    (map ->liite
                                         (map #(-> %
                                                 (cr/as-is :Liite)
                                                 (rename-keys {:Liite :liite}))
                                              liitetiedot)))})))
        (select xml-without-ns [:paatostieto :Paatos])))))

(defn- outlier-verdicts-validator [xml]
  (simple-verdicts-validator xml :paatostieto :Paatos :pvmtieto :Pvm :pvm))

(defn ->outlier-verdicts
  "For some reason kiinteistotoimitus (at least) defines its own
  verdict schema, which is similar to but not the same as the common
  schema"
  [xml-no-ns]
  (let [app-state (application-state xml-no-ns)]
    (when-not (contains? backend-preverdict-state app-state)
      (map (fn [verdict]
             (let [timestamp (date/timestamp (get-text verdict [:pvmtieto :Pvm :pvm]))]
               (when (and timestamp (> (now) timestamp))
                 (let [poytakirjat (for [elem (select verdict [:poytakirjatieto])
                                         :let [pk (-> elem cr/as-is :poytakirjatieto :Poytakirja)
                                               fields (select-keys pk [:paatoksentekija :pykala])
                                               paatos (:paatos pk)
                                               liitteet (map #(-> % :Liite ->liite (dissoc :metadata))
                                                             (flatten [(:liitetieto pk)]))]]
                                     (assoc fields
                                            :paatoskoodi paatos
                                            :status (verdict/verdict-id paatos)
                                            :liite liitteet))]
                   {:paivamaarat    {:paatosdokumentinPvm timestamp}
                    :poytakirjat poytakirjat}))))
           (select xml-no-ns [:paatostieto :Paatos])))))

(defmethod permit/read-verdict-xml :R    [_ xml-without-ns & args] (apply ->standard-verdicts xml-without-ns args))
(defmethod permit/read-verdict-xml :P    [_ xml-without-ns & args] (apply ->standard-verdicts xml-without-ns args))
(defmethod permit/read-verdict-xml :YA   [_ xml-without-ns & _]    (->simple-verdicts xml-without-ns))
(defmethod permit/read-verdict-xml :YL   [_ xml-without-ns & _]    (->simple-verdicts xml-without-ns))
(defmethod permit/read-verdict-xml :MAL  [_ xml-without-ns & _]    (->simple-verdicts xml-without-ns))
(defmethod permit/read-verdict-xml :VVVL [_ xml-without-ns & _]    (->simple-verdicts xml-without-ns))
(defmethod permit/read-verdict-xml :KT   [_ xml-without-ns & _]    (->outlier-verdicts xml-without-ns))
(defmethod permit/read-verdict-xml :ARK  [_ xml-without-ns & args] (apply ->standard-ark-verdicts xml-without-ns args))

(defmethod permit/read-tj-suunnittelija-verdict-xml :R [_ xml-without-ns & args]
  (apply ->tj-suunnittelija-verdicts xml-without-ns args))

(defmethod permit/validate-verdict-xml :R    [_ xml organization] (standard-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :P    [_ xml organization] (standard-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :YA   [_ xml _] (simple-verdicts-validator xml))
(defmethod permit/validate-verdict-xml :YL   [_ xml _] (simple-verdicts-validator xml))
(defmethod permit/validate-verdict-xml :MAL  [_ xml _] (simple-verdicts-validator xml))
(defmethod permit/validate-verdict-xml :VVVL [_ xml _] (simple-verdicts-validator xml))
(defmethod permit/validate-verdict-xml :KT   [_ xml _] (outlier-verdicts-validator xml))

(defn- ->lp-tunnus [asia]
  (or (get-text asia [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto :tunnus])
      (get-text asia [:luvanTunnistetiedot :LupaTunnus :muuTunnustieto :tunnus])))

(defn ->kuntalupatunnus [asia]
  (or (get-text asia [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus])
      (get-text asia [:luvanTunnistetiedot :LupaTunnus :kuntalupatunnus])))

(defn xml->kuntalupatunnus [xml]
  (get-in (xml->edn xml) [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus]))

(defn ->backend-ids [xml]
  (->> (enlive/select (cr/strip-xml-namespaces xml) common/case-elem-selector)
       (map ->kuntalupatunnus)
       (remove ss/blank?)))

(defn ->app-descriptions [tag xml]
  (->> (enlive/select (cr/strip-xml-namespaces xml) common/case-elem-selector)
       (map (fn [asia] {:kuntalupatunnus (->kuntalupatunnus asia)
                        :kuvaus (get-text asia [tag])}))
       (remove #(ss/blank? (:kuvaus %)))))

(defn read-permit-descriptions-from-xml
  [permit-type xml]
  (some-> (case (keyword permit-type)
               :R :rakennusvalvontaasianKuvaus
               :P :poikkeamisasianKuvaus
               nil)
          (->app-descriptions xml)))

(defmulti ->verdicts
  "Reads the verdicts."
  {:arglists '([xml permit-type reader & reader-args])}
  (fn [_ permit-type & _] (keyword permit-type)))

(defmethod ->verdicts :default
  [xml permit-type reader & reader-args]
  (map
    (fn [asia]
      (let [verdict-model {:kuntalupatunnus (->kuntalupatunnus asia)}
            verdicts      (->> (apply reader permit-type asia reader-args)
                               (cr/cleanup)
                               (filter seq))]
        (util/assoc-when-pred verdict-model util/not-empty-or-nil? :paatokset verdicts)))
    (enlive/select (cr/strip-xml-namespaces xml) common/case-elem-selector)))

;; Outliers (KT) do not have kuntalupatunnus
(defmethod ->verdicts :KT
  [xml _ reader & _]
  (for [elem (enlive/select (cr/strip-xml-namespaces xml)
                            common/outlier-elem-selector)]
    {:kuntalupatunnus "-"
     :paatokset (->> (reader :KT elem) cr/cleanup (filter seq))}))

;; Coordinates

(defn- area-coordinates [alue-element]
  (-> (cr/all-of alue-element)
      :Polygon :outerBoundaryIs :LinearRing :coordinates
      (ss/split #"\s+")))

(defn- area-geometry-str [alue-element geometry-type target-projection]
  (let [area-coordinates           (area-coordinates alue-element)
        source-projection          (common/->polygon-source-projection alue-element)
        converted-coordinates      (map #(coordinate/convert source-projection target-projection 6 (ss/split % #",")) area-coordinates)
        converted-coordinates-str  (ss/join (mapv #(str (first %) " " (last %) ", ") converted-coordinates))
        first-coordinate           (first converted-coordinates)
        first-coordinate-str       (str (first first-coordinate) " " (last first-coordinate))]
    (str geometry-type "((" converted-coordinates-str first-coordinate-str "))")))

(defn- resolve-area-coordinates [alue-element]
  (try
    (let [area-geometry-str (area-geometry-str alue-element "POLYGON" "WGS84")
          interior-point (drawing/interior-point area-geometry-str)
          interior-point-coordinates (coordinate/convert "WGS84" common/to-projection 6 interior-point)]
      interior-point-coordinates)
    (catch Exception e (error e "Resolving area coordinates failed"))))

(defn- extract-osoitenimi [osoitenimi-elem lang]
  (let [osoitenimi-elem (or (select1 osoitenimi-elem [(enlive/attr= :xml:lang lang)])
                            (select1 osoitenimi-elem [(enlive/attr= :xml:lang "fi")])
                            (select1 osoitenimi-elem [(enlive/attr= :xml:lang "und")])
                            (first osoitenimi-elem))]
    (cr/all-of osoitenimi-elem)))

(def trim-and-blank-as-nil (comp ss/blank-as-nil ss/trim))

(defn- build-jakokirjain [jakokirjain jakokirjain2]
  (->> [jakokirjain jakokirjain2]
       (map ss/trim)
       (remove ss/blank?)
       (ss/join "-")
       (ss/blank-as-nil)))

(defn- build-osoitenumero [osoitenumero osoitenumero2]
  (-> (cond
        (and osoitenumero osoitenumero2) (str (ss/trim osoitenumero) "-" (ss/trim osoitenumero2))
        :else osoitenumero)
      (trim-and-blank-as-nil)))

(defn build-address
  "Format the address according to rules specified in http://docs.jhs-suositukset.fi/jhs-suositukset/JHS109/JHS109.html
   tyhjä rakennuksessa on vain yksi huoneisto
   A vain kirjainosa (pientalot)
   1 vain numero-osa (pientalot)
   A 1 kirjainosa (porras tai rakennus) ja numero-osa (huoneistonumero)
   A b kirjainosa ja jakokirjain
   1b numero-osa ja jakokirjain
   A 1b kirjainosa (porras), numero-osa (huoneisto- numero) ja jakokirjain
   Huoneiston tunnisteen käyttö osoitetiedoissa on määritelty suosituksessa JHS 106 Osoitetietojen perusrakenne."
  [osoite-elem lang]
  (let [osoitenimi              (str (ss/trim (extract-osoitenimi (select osoite-elem [:osoitenimi :teksti]) lang)) " ")
        osoite                  (cr/all-of osoite-elem)
        osoitenumero            (apply build-osoitenumero (util/select-values osoite [:osoitenumero :osoitenumero2]))
        kirjainosa              (trim-and-blank-as-nil (:porras osoite))
        huoneisto               (apply trim-and-blank-as-nil (util/select-values osoite [:huoneisto]))
        jakokirjain             (apply build-jakokirjain (util/select-values osoite [:jakokirjain :jakokirjain2]))
        jakokirjain-whitespace  (when (or (and kirjainosa jakokirjain)
                                          (and kirjainosa huoneisto)
                                          (and osoitenumero huoneisto (nil? kirjainosa)))
                                  " ")
        osoitenumero-whitespace (when (and osoitenumero kirjainosa) " ")]
    (-> (str osoitenimi
             osoitenumero
             osoitenumero-whitespace
             kirjainosa
             jakokirjain-whitespace
             huoneisto
             jakokirjain)
        (ss/trim))))

(def unknown-address "Tuntematon osoite")

(defn- rakennuspaikka-property-id [xml]
  (some-> xml
          (get-text [:rakennuspaikanKiinteistotieto :RakennuspaikanKiinteisto
                     :kiinteistotieto :Kiinteisto :kiinteistotunnus])
          ss/blank-as-nil))

(def ^:private rakennuspaikka-alue-selector
  [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :alue])

(defn- language  [xml]
  (case (get-text xml [:lisatiedot :Lisatiedot :asioimiskieli])
    "ruotsi" "sv"
    "fi"))

(defn- resolve-property-id-by-point [[x y]]
  (let [response (-> {:params {:x x :y y}}
                     proxy-services/lot-property-id-by-point-proxy)]
    (when-not (#{400 503 404} (:status response))
      (json/decode (:body response) true))))

(defn- resolve-address [lang elem]
  (ss/blank-as-nil (build-address (select elem [:osoite]) lang)))

(defn- select-rakennuspaikka [xml]
  (select1 xml [:rakennuspaikkatieto :Rakennuspaikka]))

(defn- good-point
  "Valid point (`:x`, `:y` map) with good coordinates or nil."
  ([x y]
   (when (and x y)
     (let [x (util/->double x)
           y (util/->double y)]
       (when-not (coordinate/validate-coordinates [x y])
         {:x x :y y}))))
  ([{x :x y :y}]
   (good-point x y)))

(defn- point
  "First valid point (x,y map) found under `elem` (or nil). `selector` is the prefix path."
  [elem & [selector]]
  (some->> (common/find-valid-point elem selector)
           (zipmap [:x :y])))

(defn- resolve-address-by-point [x y]
  (when (good-point x y)
    (let [{:keys [status body]} (proxy-services/address-by-point-proxy
                                  {:params {:x x :y y}})]
      ;; Status 200 is omitted in response, so any status is error
      (when-not status
        (let [{:keys [street number propertyId]} (json/decode body true)]
          (util/strip-nils {:address    (->> [street number]
                                             (map ss/trim)
                                             (ss/join-non-blanks " ")
                                             ss/blank-as-nil)
                            :x          x
                            :y          y
                            :propertyId (some-> propertyId ss/trim ss/blank-as-nil)}))))))

(defn- resolve-location-by-property-id [property-id]
  (some-> (find-address/search-property-id "fi" property-id)
          first
          :location
          good-point))

(defmulti ^:private locator (fn [k _] k))

(defmethod locator ::site
  [_ {:keys [lang xml]}]
  (let [elem (select-rakennuspaikka xml)]
    (some-> (point elem [:sijaintitieto :Sijainti])
            (util/assoc-when :address (resolve-address lang elem)
                             :propertyId (rakennuspaikka-property-id xml)))))

(defn- structure-helper
  "Location and address resolution for Rakennus/Rakennelma elements are loosely coupled:
    - Address can be part of location (:osoite under :sijaintitieto)
    - Location can be part of address (:pistesijainti under :osoite)
    - Address and location can be somewhat siblings (e.g., address
      under :rakennuksenTiedot, but location under :sijaintitieto)

  `tag` is either :Rakennus or :Rakennelma"
  [{:keys [lang xml]} tag]
  (let [subtags (cond-> [:sijaintitieto]
                  (= tag :Rakennus) (conj :rakennuksenTiedot))]
    (->> (select xml [tag])
         (some (fn [elem]
                 ;; Immediate child selector (:>) is used, since we want to ignore owner
                 ;; addresses/locations.
                 (some-> (some #(or (point elem [tag :> % :piste])
                                    (point elem [tag :> % :pistesijainti]))
                               subtags)
                         (util/assoc-when :address
                                          (some #(resolve-address lang (select elem [tag :> %]))
                                                subtags))))))))

(defmethod locator ::structure
  [_ options]
  (or (structure-helper options :Rakennus)
      (structure-helper options :Rakennelma)))

(defmethod locator ::area
  [_ {xml :xml}]
  (some->> (select1 xml rakennuspaikka-alue-selector)
           resolve-area-coordinates
           (apply good-point)))

(defmethod locator ::reference
  [_ {xml :xml}]
  (point xml [:referenssiPiste]))

(defmethod locator ::property-id
  [_ {:keys [xml property-id]}]
  (when-let [property-id (or property-id
                             (rakennuspaikka-property-id xml))]
    (some-> (resolve-location-by-property-id property-id)
            (assoc :propertyId property-id))))

(defmethod locator ::override
  [_ {:keys [default-location-fn]}]
  (default-location-fn :override))

(defmethod locator ::fallback
  [_ {:keys [default-location-fn]}]
  (default-location-fn :fallback))

(defn- wrap-fallback
  [{:keys [x y propertyId] :as location}]
  (fn [mode]
    (when (= mode :fallback)
      (cond
        (good-point x y) location
        propertyId       (locator ::property-id {:property-id propertyId})))))

(defn resolve-valid-location
  "Returns location map (`:address`, `:propertyId`, `:x`, `:y`) with valid coordinates.
  The `xml` parameter is KuntaGML message and the optional `default-location` can be
  function or map. As a function its takes one parameter: `override` or `fallback`. Map
  version denotes legacy implementation support, where partial location map is wrapped
  into a fallback function.

  The location resolution priority order:

  1. Default override location.
  2. Rakennuspaikka element (location by point)
  3. Rakennus element (the first with valid coordinates)
  4. Rakennelma element (the first with valid coordinates)
  5. Rakennuspaikka element (location within area)
  6. Location for referenssiPiste element
  7. Location for the property-id.
  8. Default fallback location."
  [xml & [default-location]]
  (let [lang    (language xml)
        options {:lang                lang
                 :xml                 xml
                 :default-location-fn (cond
                                        (fn? default-location)  default-location
                                        (map? default-location) (wrap-fallback default-location)
                                        :else                   (constantly nil))}]
    (some (fn [step]
            (try
              (let [{:keys [x y address propertyId]} (locator step options)]
                (when (and x y)
                  (let [{back-address     :address
                         back-property-id :propertyId
                         } (when (some ss/blank? [address propertyId])
                             (resolve-address-by-point x y))]
                    (-> {:x          x
                         :y          y
                         :address    (or address
                                         back-address
                                         (resolve-address lang (select-rakennuspaikka xml))
                                         unknown-address)
                         :propertyId (or propertyId
                                         back-property-id
                                         (resolve-property-id-by-point [x y])
                                         (rakennuspaikka-property-id xml))}
                        util/strip-nils
                        not-empty))))
              (catch Exception e
                (warn e "Location resolution step" (name step) "failed:" (ex-message e)))))
          [::override ::site ::structure ::area ::reference
           ::property-id ::fallback])))

(defn get-asiat-with-kuntalupatunnus [xml-no-ns kuntalupatunnus]
  (let [asiat (enlive/select xml-no-ns common/case-elem-selector)]
    (filter #(when (= kuntalupatunnus (->kuntalupatunnus %)) %) asiat)))

(defn ->rakennelmatiedot
  "Returns a sequence of rakennelmatieto-elements."
  [xml-no-ns]
  (->> (select xml-no-ns [:toimenpidetieto :Toimenpide :rakennelmatieto])
       (map cr/all-of)))

(defn ->vakuustieto [xml]
  (->> (select xml [:rakennusvalvontaAsiatieto :lisatiedot :vakuus])
       (map cr/all-of)
       first))

(defn ->viitelupatunnukset
  "Takes a parsed XML document, returns a list of viitelupatunnus -ids (in 'permit-id'-format) found therein."
  [xml]
  (->> (select xml [:rakennusvalvontaAsiatieto :viitelupatieto])
       (map (comp #(get-in % [:LupaTunnus :kuntalupatunnus]) cr/all-of))
       distinct))

(defn is-foreman-application? [xml]
  (let [permit-type (-> xml ->kuntalupatunnus (ss/split #"-") last)]
    (= "TJO" permit-type)))

(defn ->tyonjohtajat [xml]
  (when (is-foreman-application? xml)
    (as-> xml x
      (get-asiat-with-kuntalupatunnus x (->kuntalupatunnus xml))
      (first x)
      (select x [:osapuolettieto :Osapuolet :tyonjohtajatieto :Tyonjohtaja])
      (map cr/all-of x))))

(defn ->lausuntotiedot
  "Combines the two dates from the upper level lausuntotieto (statement request) to the lower ones (given statements)
   so a full picture can be preserved"
  [xml]
  (->> (select xml [:rakennusvalvontaAsiatieto :lausuntotieto :Lausunto])
       (map cr/all-of)
       (mapcat (fn [statement-request]
                 (->> (get-in statement-request [:lausuntotieto :Lausunto])
                      (util/sequentialize) ; In practice only one is used but the schema allows for multiple ones
                      (map #(merge % (select-keys statement-request [:pyyntoPvm :maaraPvm]))))))
       (remove empty?)))

;;
;; Information parsed from verdict xml message for application creation
;;
(defn get-app-info-from-message [xml kuntalupatunnus & [default-location-fn]]
  (let [xml-no-ns                  (ss/trimwalk (cr/strip-xml-namespaces xml))
        kuntakoodi                 (-> (select1 xml-no-ns
                                                [:toimituksenTiedot :kuntakoodi])
                                       cr/all-of)
        asiat-with-kuntalupatunnus (get-asiat-with-kuntalupatunnus xml-no-ns kuntalupatunnus)
        asiat-n                    (count asiat-with-kuntalupatunnus)]
    (if-not (= 1 asiat-n)
      (errorf "Creating application from KuntaGML for %s. Wrong number of elements found: %d "
              kuntalupatunnus asiat-n)
      (let [asia             (first asiat-with-kuntalupatunnus)
            {:keys [propertyId]
             :as   location} (resolve-valid-location asia default-location-fn)
            asianTiedot      (cr/all-of asia [:asianTiedot :Asiantiedot])

            operations (->> (select asia [:Toimenpide])
                            (map #(cr/all-of % [:Toimenpide])))

            osapuolet        (map cr/all-of (select asia [:osapuolettieto :Osapuolet
                                                          :osapuolitieto :Osapuoli]))
            suunnittelijat   (map cr/all-of (select asia [:osapuolettieto :Osapuolet
                                                          :suunnittelijatieto :Suunnittelija]))
            tyonjohtajat     (map cr/all-of (select asia [:osapuolettieto :Osapuolet
                                                          :tyonjohtajatieto :Tyonjohtaja]))
            [hakijat
             muut-osapuolet] ((juxt filter remove) #(= "hakija" (:VRKrooliKoodi %)) osapuolet)
            municipality     (if (and (string? propertyId)
                                      (re-matches sprop/db-property-id-pattern propertyId))
                               (prop/municipality-by-property-id propertyId)
                               kuntakoodi)]

        (-> (merge
              {:id                          (->lp-tunnus asia)
               :kuntalupatunnus             (->kuntalupatunnus asia)
               :municipality                municipality
               :rakennusvalvontaasianKuvaus (:rakennusvalvontaasianKuvaus asianTiedot)
               :vahainenPoikkeaminen        (:vahainenPoikkeaminen asianTiedot)
               :hakijat                     hakijat
               :muutOsapuolet               muut-osapuolet
               :toimenpiteet                operations
               :suunnittelijat              suunnittelijat
               :tyonjohtajat                tyonjohtajat
               :rakennuspaikka              location}

              (let [geometry-str (area-geometry-str (select1 asia rakennuspaikka-alue-selector)
                                                    "POLYGON" common/to-projection)]
                (when-let [wgs84-geometry (drawing/wgs84-geometry {:geometry geometry-str})]
                  {:drawings [{:geometry       geometry-str
                               :geometry-wgs84 wgs84-geometry}]})))

            cr/convert-booleans
            cr/cleanup)))))
