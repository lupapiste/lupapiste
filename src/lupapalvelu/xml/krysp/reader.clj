(ns lupapalvelu.xml.krysp.reader
  "Read the Krysp from municipality Web Feature Service"
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error warnf]]
            [clojure.set :refer [rename-keys]]
            [net.cgrand.enlive-html :as enlive]
            [sade.env :as env]
            [sade.xml :refer :all]
            [sade.util :refer [fn-> fn->>] :as util]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.coordinate :as coordinate]
            [sade.core :refer [now def- fail]]
            [lupapalvelu.drawing :as drawing]
            [lupapalvelu.document.schemas]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.property :as prop]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.xml.krysp.verdict :as verdict]
            [lupapalvelu.xml.krysp.common-reader :as common]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.proxy-services :as proxy-services]
            [cheshire.core :as json]))

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
          (-> vaadittuErityissuunnitelma-array first (.indexOf "\n") (>= 0)))
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

(defn ->lupamaaraukset [paatos-xml-without-ns]
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

(defn- ->lupamaaraukset-text [paatos-xml-without-ns]
  (let [lupaehdot (select paatos-xml-without-ns :lupaehdotJaMaaraykset)]
    (when (not-empty lupaehdot)
      (-> lupaehdot
        (cr/cleanup)
        ((fn [maar] (map #(get-text % :lupaehdotJaMaaraykset) maar)))
        (util/ensure-sequential :lupaehdotJaMaaraykset)))))

(defn- get-pvm-dates [paatos v]
  (into {} (map #(let [xml-kw (keyword (str (name %) "Pvm"))]
                   [% (cr/to-timestamp (get-text paatos xml-kw))]) v)))

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

(defn- poytakirja-with-paatos-data [poytakirjat]
  (some #(when (and (:paatoskoodi %) (:paatoksentekija %) (:paatospvm %)) %) poytakirjat))

(defn- valid-paatospvm? [paatos-pvm]
  (> (util/get-timestamp-ago :day 1) paatos-pvm))

(defn- valid-antopvm? [anto-pvm]
  (or (not anto-pvm) (> (now) anto-pvm)))

(defn- standard-verdicts-validator [xml {validate-verdict-given-date :validate-verdict-given-date}]
  (let [paatos-xml-without-ns (select (cr/strip-xml-namespaces xml) [:paatostieto :Paatos])
        poytakirjat (map ->paatospoytakirja (select paatos-xml-without-ns [:poytakirja]))
        poytakirja  (poytakirja-with-paatos-data poytakirjat)
        paivamaarat (map #(get-pvm-dates % [:aloitettava :lainvoimainen :voimassaHetki :raukeamis :anto :viimeinenValitus :julkipano]) paatos-xml-without-ns)]
    (cond
      (not (seq poytakirjat))                               (fail :info.no-verdicts-found-from-backend)
      (not (seq poytakirja))                                (fail :info.paatos-details-missing)
      (not (valid-paatospvm? (:paatospvm poytakirja)))      (fail :info.paatos-future-date)
      (and validate-verdict-given-date
        (not-any? #(valid-antopvm? (:anto %)) paivamaarat)) (fail :info.paatos-future-date))))

(defn- ->standard-verdicts [xml-without-ns & [organization _]]
  (let [{validate-verdict-given-date :validate-verdict-given-date} organization]
    (map (fn [paatos-xml-without-ns]
           (let [poytakirjat      (map ->paatospoytakirja (select paatos-xml-without-ns [:poytakirja]))
                 poytakirja       (poytakirja-with-paatos-data poytakirjat)
                 paivamaarat      (get-pvm-dates paatos-xml-without-ns [:aloitettava :lainvoimainen :voimassaHetki :raukeamis :anto :viimeinenValitus :julkipano])]
             (when (and poytakirja (valid-paatospvm? (:paatospvm poytakirja)) (or (not validate-verdict-given-date)
                                                                                  (valid-antopvm? (:anto paivamaarat))))
               {:lupamaaraykset (->lupamaaraukset paatos-xml-without-ns)
                :paivamaarat    paivamaarat
                :poytakirjat    (seq poytakirjat)})))
         (select xml-without-ns [:paatostieto :Paatos]))))

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
       (= (:alkamisPvm osapuoli) (util/to-xml-date-from-string (:alkamisPvm sijaistus)))
       (= (:paattymisPvm osapuoli) (util/to-xml-date-from-string (:paattymisPvm sijaistus)))
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
  (let [{osapuoli-path :path kuntaRoolikoodi-key :key} (osapuoli-path-key-mapping osapuoli-type)
        osapuoli-key (last osapuoli-path)
        xml-without-ns (cr/strip-xml-namespaces xml)
        osapuolet (get-tj-suunnittelija-osapuolet xml-without-ns osapuoli-path osapuoli-key kuntaRoolikoodi-key kuntaRoolikoodi yhteystiedot)
        osapuoli (party-with-paatos-data osapuolet sijaistus)
        paatospvm  (:paatosPvm osapuoli)
        timestamp-1-day-ago (util/get-timestamp-ago :day 1)]
    (cond
      (not (seq osapuolet))                  (fail :info.no-verdicts-found-from-backend)
      (not (seq osapuoli))                   (fail :info.tj-suunnittelija-paatos-details-missing)
      (< timestamp-1-day-ago paatospvm)      (fail :info.paatos-future-date))))

(defn ->tj-suunnittelija-verdicts [xml-without-ns {{:keys [yhteystiedot sijaistus]} :data} osapuoli-type kuntaRoolikoodi]
  (let [{osapuoli-path :path kuntaRoolikoodi-key :key} (osapuoli-path-key-mapping osapuoli-type)
        osapuoli-key (last osapuoli-path)]
    (map (fn [osapuolet-xml-without-ns]
           (let [osapuolet (get-tj-suunnittelija-osapuolet xml-without-ns osapuoli-path osapuoli-key kuntaRoolikoodi-key kuntaRoolikoodi yhteystiedot)
                 osapuoli (party-with-paatos-data osapuolet sijaistus)]
           (when (and osapuoli (> (now) (:paatosPvm osapuoli)))
             {:poytakirjat
              [{:status (get tj-suunnittelija-verdict-statuses-to-loc-keys-mapping (:paatostyyppi osapuoli))
                :paatospvm (:paatosPvm osapuoli)
                :liite (:liite osapuoli)
                }]
              })))
  (select xml-without-ns [:osapuolettieto :Osapuolet]))))

(def krysp-state-sorting
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
(defmethod application-state :Popast [xml-without-ns] (standard-application-state xml-without-ns))
(defmethod application-state :FeatureCollection [xml-without-ns] (standard-application-state xml-without-ns))

(def backend-preverdict-state
  #{"" "luonnos" "hakemus" "valmistelussa" "vastaanotettu" "tarkastettu, t\u00e4ydennyspyynt\u00f6"})

(defn- simple-verdicts-validator [xml organization & verdict-date-path]
  (let [verdict-date-path (or verdict-date-path [:paatostieto :Paatos :paatosdokumentinPvm])
        xml-without-ns (cr/strip-xml-namespaces xml)
        app-state      (application-state xml-without-ns)
        paivamaarat    (filter number? (map (comp cr/to-timestamp get-text) (select xml-without-ns verdict-date-path)))
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
             (let [paatosdokumentinPvm-timestamp (cr/to-timestamp (get-text paatos-xml-without-ns :paatosdokumentinPvm))]
               (when (and paatosdokumentinPvm-timestamp (> (now) paatosdokumentinPvm-timestamp))
                 {:lupamaaraykset {:takuuaikaPaivat (get-text paatos-xml-without-ns :takuuaikaPaivat)
                                   :muutMaaraykset (->lupamaaraukset-text paatos-xml-without-ns)}
                  :paivamaarat    {:paatosdokumentinPvm paatosdokumentinPvm-timestamp}
                  :poytakirjat    (when-let [liitetiedot (seq (select paatos-xml-without-ns [:liitetieto]))]
                                    (map ->liite
                                         (map #(-> %
                                                 (cr/as-is :Liite)
                                                 (rename-keys {:Liite :liite}))
                                              liitetiedot)))})))
        (select xml-without-ns [:paatostieto :Paatos])))))

(defn- outlier-verdicts-validator [xml organization]
  (simple-verdicts-validator xml organization :paatostieto :Paatos :pvmtieto :Pvm :pvm))

(defn ->outlier-verdicts
  "For some reason kiinteistotoimitus (at least) defines its own
  verdict schema, which is similar to but not the same as the common
  schema"
  [xml-no-ns]
  (let [app-state (application-state xml-no-ns)]
    (when-not (contains? backend-preverdict-state app-state)
      (map (fn [verdict]
             (let [timestamp (cr/to-timestamp (get-text verdict [:pvmtieto :Pvm :pvm]))]
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
(defmethod permit/read-verdict-xml :ARK  [_ xml-without-ns & args] (apply ->standard-verdicts xml-without-ns args))

(defmethod permit/read-tj-suunnittelija-verdict-xml :R [_ xml-without-ns & args] (apply ->tj-suunnittelija-verdicts xml-without-ns args))

(defmethod permit/validate-verdict-xml :R    [_ xml organization] (standard-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :P    [_ xml organization] (standard-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :YA   [_ xml organization] (simple-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :YL   [_ xml organization] (simple-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :MAL  [_ xml organization] (simple-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :VVVL [_ xml organization] (simple-verdicts-validator xml organization))
(defmethod permit/validate-verdict-xml :KT   [_ xml organization] (outlier-verdicts-validator xml organization))

(defn- ->lp-tunnus [asia]
  (or (get-text asia [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto :tunnus])
      (get-text asia [:luvanTunnistetiedot :LupaTunnus :muuTunnustieto :tunnus])))

(defn ->kuntalupatunnus [asia]
  (or (get-text asia [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus])
      (get-text asia [:luvanTunnistetiedot :LupaTunnus :kuntalupatunnus])))

(defn ->backend-ids [xml]
  (->> (enlive/select (cr/strip-xml-namespaces xml) common/case-elem-selector)
       (map ->kuntalupatunnus)
       (remove ss/blank?)))

(defn ->rakval-app-descriptions [xml]
  (->> (enlive/select (cr/strip-xml-namespaces xml) common/case-elem-selector)
       (map (fn [asia] {:kuntalupatunnus (->kuntalupatunnus asia)
                        :kuvaus (get-text asia [:rakennusvalvontaasianKuvaus])}))
       (remove #(ss/blank? (:kuvaus %)))))

(defn read-permit-descriptions-from-xml
  [permit-type xml]
  (when (#{:R} (keyword permit-type))
    (->rakval-app-descriptions xml)))

(defmulti ->verdicts
  "Reads the verdicts."
  {:arglists '([xml permit-type reader & reader-args])}
  (fn [xml permit-type & _] (keyword permit-type)))

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

(defn select1-non-zero-point
  "Select first element of matching selector, which doesn't have '0.0' as gml:Point/gml:pos value."
  [xml selector]
  (->> (select xml selector)
       (remove #(some #{"0.0"} (-> (get-text % [:Point :pos]) (ss/split #"\s+"))))
       first))

(defn- resolve-point-coordinates [point-xml-with-ns point-str]
  (try
    (when-let [source-projection (common/->source-projection point-xml-with-ns [:Point])]
      (let [coords (ss/split point-str #" ")]
        (when-not (contains? coordinate/known-bad-coordinates coords)
          (coordinate/convert source-projection common/to-projection 3 coords))))
    (catch Exception e (error e "Coordinate conversion failed for point-str" point-str))))

(defn- resolve-building-coordinates [xml]
  (try
    (let [building-location-xml (select1-non-zero-point xml [:Rakennus :sijaintitieto :Sijainti])
          building-coordinates-str (->> (cr/all-of building-location-xml) :piste :Point :pos)
          building-coordinates (ss/split building-coordinates-str #"\s+")
          source-projection (common/->source-projection building-location-xml [:Point])]
      (when-not (contains? coordinate/known-bad-coordinates building-coordinates)
        (coordinate/convert source-projection common/to-projection 3 building-coordinates)))
    (catch Exception e (error e "Reolving building coordinates failed"))))

(defn- area-coordinates [xml]
  (let [coordinates-array-xml (select1 xml [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :alue])
        coordinates-array     (->> (cr/all-of coordinates-array-xml) :Polygon :outerBoundaryIs :LinearRing :coordinates)]
    (ss/split coordinates-array #" ")))

(defn- area-geometry-str [xml geometry-type target-projection]
  (let [area-coordinates           (area-coordinates xml)
        source-projection          (common/->polygon-source-projection xml)
        converted-coordinates      (map #(coordinate/convert source-projection target-projection 6 (ss/split % #",")) area-coordinates)
        converted-coordinates-str  (apply str (mapv #(str (first %) " " (last %) ", ") converted-coordinates))
        first-coordinate           (first converted-coordinates)
        first-coordinate-str       (str (first first-coordinate) " " (last first-coordinate))]
    (str geometry-type "((" converted-coordinates-str first-coordinate-str "))")))

(defn- resolve-area-coordinates [xml]
  (try
    (let [area-geometry-str (area-geometry-str xml "POLYGON" "WGS84")
          interior-point (drawing/interior-point area-geometry-str)
          interior-point-coordinates (coordinate/convert "WGS84" common/to-projection 6 interior-point)]
      interior-point-coordinates)
    (catch Exception e (error e "Reolving area coordinates failed"))))

(defn- extract-osoitenimi [osoitenimi-elem lang]
  (let [osoitenimi-elem (or (select1 osoitenimi-elem [(enlive/attr= :xml:lang lang)])
                            (select1 osoitenimi-elem [(enlive/attr= :xml:lang "fi")])
                            (select1 osoitenimi-elem [(enlive/attr= :xml:lang "und")]))]
    (cr/all-of osoitenimi-elem)))

(defn- build-huoneisto [huoneisto jakokirjain jakokirjain2]
  (when huoneisto
    (str huoneisto
         (cond
           (and jakokirjain jakokirjain2) (str jakokirjain "-" jakokirjain2)
           :else jakokirjain))))

(defn- build-osoitenumero [osoitenumero osoitenumero2]
  (cond
    (and osoitenumero osoitenumero2) (str osoitenumero "-" osoitenumero2)
    :else osoitenumero))

(defn- build-address [osoite-elem lang]
  (let [osoitenimi        (extract-osoitenimi (select osoite-elem [:osoitenimi :teksti]) lang)
        osoite            (cr/all-of osoite-elem)
        osoite-components [osoitenimi
                           (apply build-osoitenumero (util/select-values osoite [:osoitenumero :osoitenumero2]))
                           (:porras osoite)
                           (apply build-huoneisto (util/select-values osoite [:huoneisto :jakokirjain :jakokirjain2]))]]
    (ss/join " " (remove nil? osoite-components))))

(def unknown-address "Tuntematon osoite")

(defn- resolve-address-by-point [x y]
  (let [resp (-> {:params {:x x :y y :lang "fi"}}
                 proxy-services/address-by-point-proxy
                 :body)
        {:keys [street number]} (when (string? resp)
                                  (try
                                    (json/parse-string resp true)
                                    (catch Exception _
                                      (warnf "Failed to resolve address for point x: %s y: %s" x y))))]
    (when street
      (str street " " number))))

(defn rakennuspaikka-property-id [rakennuspaikka-element]
  (-> rakennuspaikka-element :rakennuspaikanKiinteistotieto :RakennuspaikanKiinteisto :kiinteistotieto :Kiinteisto :kiinteistotunnus))

(defn- resolve-location-by-property-id [property-id kuntalupatunnus]
  (warn "Falling back to resolve location for kuntalupatunnus" kuntalupatunnus "by property id" property-id)
  (if-let [location (-> (find-address/search-property-id "fi" property-id)
                        first
                        :location)]
    (let [{:keys [x y]} location
          street (resolve-address-by-point x y)]
      {:rakennuspaikka {:x          x
                        :y          y
                        :address    (if street
                                      street
                                      unknown-address)
                        :propertyId property-id}})
    (warn "Could not resolve location for kuntalupatunnus" kuntalupatunnus "by property id" property-id)))

(defn resolve-property-id-by-point [coordinates]
  (let [x (first coordinates)
        y (last coordinates)
        response (-> {:params {:x x :y y}}
                     proxy-services/property-id-by-point-proxy)]
    (if-not (#{400 503} (:status response))
      (json/parse-string (:body response) true))))

(defn resolve-coordinate-type [xml]
  (cond
    (some? (select1 xml [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :piste])) :point
    (some? (select1-non-zero-point xml [:Rakennus :sijaintitieto :Sijainti :piste :Point])) :building
    (some? (select1 xml [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :alue])) :area))

(defn resolve-coordinates
  "Primarily uses rakennuspaikka coordinates,
   if not found then takes coordinates from first building if exists,
   if still not found then checks if location is area like and calculates interior point
   finally returns nil and location is resolved later with kiinteistotunnus and kuntalupatunnus"
  [xml]
  (let [coordinate-type (resolve-coordinate-type xml)
        Rakennuspaikka  (cr/all-of xml [:rakennuspaikkatieto :Rakennuspaikka])]
    (case coordinate-type
      :point    (resolve-point-coordinates
                  (select1 xml [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :piste])
                  (-> Rakennuspaikka :sijaintitieto :Sijainti :piste :Point :pos))
      :building (resolve-building-coordinates xml)
      :area     (resolve-area-coordinates xml)
      nil)))

(defn get-asiat-with-kuntalupatunnus [xml-no-ns kuntalupatunnus]
  (let [asiat (enlive/select xml-no-ns common/case-elem-selector)]
    (filter #(when (= kuntalupatunnus (->kuntalupatunnus %)) %) asiat)))

(defn ->rakennelmatieto [xml-no-ns]
  (-> xml-no-ns
    (select [:toimenpidetieto :Toimenpide :rakennelmatieto])
    cr/all-of))

(defn ->viitelupatunnukset
  "Takes a parsed XML document, returns a list of viitelupatunnus -ids (in 'permit-id'-format) found therein."
  [xml]
  (->> (select xml [:rakennusvalvontaAsiatieto :viitelupatieto])
       (map (comp conv-util/normalize-permit-id #(get-in % [:LupaTunnus :kuntalupatunnus]) cr/all-of))))

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

;;
;; Information parsed from verdict xml message for application creation
;;
(defn get-app-info-from-message [xml kuntalupatunnus]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)
        kuntakoodi (-> (select1 xml-no-ns [:toimituksenTiedot :kuntakoodi]) cr/all-of)
        ;; Take first asia with given kuntalupatunnus. There should be only one. If there are many throw error.
        asiat-with-kuntalupatunnus (get-asiat-with-kuntalupatunnus xml-no-ns kuntalupatunnus)]
    (when (pos? (count asiat-with-kuntalupatunnus))
      ;; There should be only one RakennusvalvontaAsia element in the message, even though Krysp makes multiple elements possible.
      ;; Log an error if there were many. Use the first one anyway.
      (when (> (count asiat-with-kuntalupatunnus) 1)
        (error "Creating application from previous permit. More than one RakennusvalvontaAsia element were received in the xml message with kuntalupatunnus " kuntalupatunnus "."))

      (let [asia (first asiat-with-kuntalupatunnus)
            asioimiskieli (cr/all-of asia [:lisatiedot :Lisatiedot :asioimiskieli])
            asioimiskieli-code (case asioimiskieli
                                 "ruotsi" "sv"
                                 "fi")
            asianTiedot (cr/all-of asia [:asianTiedot :Asiantiedot])

            operations (->> (select asia [:Toimenpide])
                            (map #(cr/all-of % [:Toimenpide])))

            ;;
            ;; _Kvintus 5.11.2014_: Rakennuspaikka osoitteen ja sijainnin oikea lahde.
            ;;

            ;; Rakennuspaikka
            Rakennuspaikka (cr/all-of asia [:rakennuspaikkatieto :Rakennuspaikka])

            osoite-xml     (select asia [:rakennuspaikkatieto :Rakennuspaikka :osoite])
            osoite-Rakennuspaikka (build-address osoite-xml asioimiskieli-code)

            coordinates-type (resolve-coordinate-type asia)
            [x y :as coord-array-Rakennuspaikka] (resolve-coordinates asia)
            osapuolet (map cr/all-of (select asia [:osapuolettieto :Osapuolet :osapuolitieto :Osapuoli]))
            suunnittelijat (map cr/all-of (select asia [:osapuolettieto :Osapuolet :suunnittelijatieto :Suunnittelija]))
            tyonjohtajat (map cr/all-of (select asia [:osapuolettieto :Osapuolet :tyonjohtajatieto :Tyonjohtaja]))
            [hakijat muut-osapuolet] ((juxt filter remove) #(= "hakija" (:VRKrooliKoodi %)) osapuolet)
            kiinteistotunnus (if (and (seq coord-array-Rakennuspaikka) (#{:building :area} coordinates-type))
                               (or (resolve-property-id-by-point coord-array-Rakennuspaikka)
                                   (rakennuspaikka-property-id Rakennuspaikka))
                               (rakennuspaikka-property-id Rakennuspaikka))
            municipality (or (prop/municipality-by-property-id kiinteistotunnus) kuntakoodi)]

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
               :tyonjohtajat                tyonjohtajat}

              (cond
                (and (seq coord-array-Rakennuspaikka) (not-any? ss/blank? [osoite-Rakennuspaikka kiinteistotunnus]))
                {:rakennuspaikka {:x x :y y
                                  :address    osoite-Rakennuspaikka
                                  :propertyId kiinteistotunnus}}

                (and (seq coord-array-Rakennuspaikka) (ss/not-blank? kiinteistotunnus))
                {:rakennuspaikka {:x x :y y
                                  :propertyId kiinteistotunnus
                                  :address (or (resolve-address-by-point x y) unknown-address)}}

                (and (nil? coord-array-Rakennuspaikka) (not (ss/blank? kiinteistotunnus)))
                (resolve-location-by-property-id kiinteistotunnus kuntalupatunnus))

              (let [geometry-str (area-geometry-str asia "POLYGON" common/to-projection)]
                (when-let [wgs84-geometry (drawing/wgs84-geometry {:geometry geometry-str})]
                  {:drawings [{:geometry       geometry-str
                               :geometry-wgs84 wgs84-geometry}]})))

            cr/convert-booleans
            cr/cleanup)))))
