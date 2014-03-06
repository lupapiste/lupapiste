(ns lupapalvelu.xml.krysp.mapping-common
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.java.io :as io]
            [clojure.data.xml :refer [emit indent-str]]
            [me.raynes.fs :as fs]
            [sade.strings :as ss]
            [sade.util :refer :all]
            [lupapalvelu.core :refer [fail!]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.validator :as validator]))

(def schemalocation-yht-2.1.0
  "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
   http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd")

(def schemalocation-yht-2.1.1
  "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.1/yhteiset.xsd
   http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd")

(def common-namespaces
  {:xmlns:yht   "http://www.paikkatietopalvelu.fi/gml/yhteiset"
   :xmlns:gml   "http://www.opengis.net/gml"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:xsi   "http://www.w3.org/2001/XMLSchema-instance"})

(def tunnus-children [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}])

(def ^:private postiosoite-children [{:tag :kunta}
                                     {:tag :osoitenimi :child [{:tag :teksti}]}
                                     {:tag :postinumero}
                                     {:tag :postitoimipaikannimi}])

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def postiosoite-children-ns-yht (vec (map (fn [m] (assoc m :ns "yht")) postiosoite-children)))

(def ^:private osoite {:tag :osoite  :ns "yht"
                       :child postiosoite-children})

(def gml-point {:tag :Point :ns "gml" :child [{:tag :pos}]})

(def sijantiType {:tag :Sijainti
                   :child [{:tag :osoite :ns "yht"
                            :child [{:tag :yksilointitieto}
                                    {:tag :alkuHetki}
                                    {:tag :osoitenimi
                                     :child [{:tag :teksti}]}]}
                           {:tag :piste :ns "yht"
                            :child [gml-point]}
                           {:tag :viiva :ns "yht"
                            :child [{:tag :LineString :ns "gml"
                                     :child [{:tag :pos}]}]}
                           {:tag :alue :ns "yht"
                            :child [{:tag :Polygon :ns "gml"
                                     :child [{:tag :exterior
                                              :child [{:tag :LinearRing
                                                       :child [{:tag :pos}]}]} ]}]}
                           {:tag :tyhja :ns "yht"}]})

(defn sijaintitieto
  "Takes an optional xml namespace for Sijainti element"
  [& [xmlns]]
  {:tag :sijaintitieto
   :child [(merge
             sijantiType
             (when xmlns {:ns xmlns}))]})

(def ^:private rakennusoikeudet [:tag :rakennusoikeudet
                                 :child [{:tag :kayttotarkoitus
                                          :child [{:tag :pintaAla}
                                                  {:tag :kayttotarkoitusKoodi}]}]])

(def yksilointitieto {:tag :yksilointitieto :ns "yht"})

(def alkuHetki {:tag :alkuHetki :ns "yht"})

(def rakennuspaikka {:tag :Rakennuspaikka
                     :child [yksilointitieto
                             alkuHetki
                             {:tag :rakennuspaikanKiinteistotieto :ns "yht"
                              :child [{:tag :RakennuspaikanKiinteisto
                                       :child [{:tag :kiinteistotieto
                                                :child [{:tag :Kiinteisto
                                                         :child [{:tag :kylanimi}
                                                                 {:tag :tilannimi}
                                                                 {:tag :kiinteistotunnus}
                                                                 {:tag :maaraAlaTunnus}]}]}
                                               {:tag :palsta}
                                               {:tag :kokotilaKytkin}
                                               {:tag :hallintaperuste}
                                               {:tag :vuokraAluetunnus}]}]}
                             {:tag :kaavanaste :ns "yht"}
                             {:tag :kerrosala :ns "yht"}
                             {:tag :tasosijainti :ns "yht" }
                             {:tag :rakennusoikeudet  :ns "yht"
                              :child [{:tag :kayttotarkoitus
                                       :child [{:tag :pintaAla}
                                               {:tag :kayttotarkoitusKoodi}]}]}
                             {:tag :rakennusoikeusYhteensa :ns "yht" }
                             {:tag :uusiKytkin :ns "yht"}]})


(def ^:private henkilo-child [{:tag :nimi
                               :child [{:tag :etunimi}
                                       {:tag :sukunimi}]}
                              {:tag :osoite :child postiosoite-children}
                              {:tag :sahkopostiosoite}
                              {:tag :faksinumero}
                              {:tag :puhelin}
                              {:tag :henkilotunnus}])

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def henkilo-child-ns-yht (vec (map (fn [m] (assoc m :ns "yht")) henkilo-child)))

(def yritys-child [{:tag :nimi}
                   {:tag :liikeJaYhteisotunnus}
                   {:tag :kayntiosoite :child postiosoite-children}
                   {:tag :kotipaikka}
                   {:tag :postiosoite :child postiosoite-children}
                   {:tag :faksinumero}
                   {:tag :puhelin}
                   {:tag :www}
                   {:tag :sahkopostiosoite}])

(def yritys-child-ns-yht [{:tag :nimi}
                          {:tag :liikeJaYhteisotunnus}
                          {:tag :kayntiosoite :child postiosoite-children-ns-yht}
                          {:tag :kotipaikka}
                          {:tag :postiosoite :child postiosoite-children-ns-yht}
                          {:tag :faksinumero}
                          {:tag :puhelin}
                          {:tag :www}
                          {:tag :sahkopostiosoite}])

(def henkilo {:tag :henkilo :ns "yht"
              :child henkilo-child})

(def yritys {:tag :yritys :ns "yht"
             :child yritys-child})

(def osapuoli-body {:tag :Osapuoli
                    :child [{:tag :kuntaRooliKoodi}
                            {:tag :VRKrooliKoodi}
                            henkilo
                            yritys
                            {:tag :turvakieltoKytkin}]})

(def ^:private naapuri {:tag :naapuritieto
                        :child [{:tag :Naapuri
                                 :child [{:tag :henkilo}
                                         {:tag :kiinteistotunnus}
                                         {:tag :hallintasuhde}]}]})

(def osapuolet
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto
            :child [osapuoli-body]}
           {:tag :suunnittelijatieto
            :child [{:tag :Suunnittelija
                     :child [{:tag :suunnittelijaRoolikoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             ;{:tag :kokemusvuodet}               ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             ]}]}
           {:tag :tyonjohtajatieto
            :child [{:tag :Tyonjohtaja
                     :child [{:tag :tyonjohtajaRooliKoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             {:tag :valmistumisvuosi}
                             ;{:tag :alkamisPvm}
                             ;{:tag :paattymisPvm}
                             ;{:tag :vastattavatTyotehtavat}      ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             ;{:tag :valvottavienKohteidenMaara}  ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             ;{:tag :kokemusvuodet}               ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             {:tag :tyonjohtajaHakemusKytkin}]}]}
           naapuri]})

(def osapuolet_211
  {:tag :Osapuolet :ns "yht"
   :child [{:tag :osapuolitieto
            :child [osapuoli-body]}
           {:tag :suunnittelijatieto
            :child [{:tag :Suunnittelija
                     :child [{:tag :suunnittelijaRoolikoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             {:tag :valmistumisvuosi}
                             {:tag :kokemusvuodet}]}]}
           {:tag :tyonjohtajatieto
            :child [{:tag :Tyonjohtaja
                     :child [{:tag :tyonjohtajaRooliKoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             {:tag :valmistumisvuosi}
                             {:tag :alkamisPvm}
                             {:tag :paattymisPvm}
                             ;{:tag :vastattavatTyotehtavat}      ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             ;{:tag :valvottavienKohteidenMaara}  ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             {:tag :tyonjohtajaHakemusKytkin}
                             {:tag :kokemusvuodet}
                             {:tag :sijaistustieto
                              :child [{:tag :Sijaistus
                                       :child [{:tag :sijaistettavaHlo}
                                               {:tag :sijaistettavaRooli}
                                               {:tag :alkamisPvm}
                                               {:tag :paattymisPvm}]}]}]}]}
           naapuri]})

(def tilamuutos
  {:tag :Tilamuutos :ns "yht"
   :child [{:tag :pvm}
           {:tag :tila}
           {:tag :kasittelija :child [henkilo]}]})

(def lupatunnus {:tag :LupaTunnus :ns "yht" :child [{:tag :kuntalupatunnus}
                                                    {:tag :muuTunnustieto :child [{:tag :MuuTunnus :child [{:tag :tunnus} {:tag :sovellus}]}]}
                                                    {:tag :saapumisPvm}
                                                    {:tag :viittaus}]})

(def toimituksenTiedot [{:tag :aineistonnimi :ns "yht"}
                        {:tag :aineistotoimittaja :ns "yht"}
                        {:tag :tila :ns "yht"}
                        {:tag :toimitusPvm :ns "yht"}
                        {:tag :kuntakoodi :ns "yht"}
                        {:tag :kielitieto :ns "yht"}])

(def liite-children [{:tag :kuvaus :ns "yht"}
                     {:tag :linkkiliitteeseen :ns "yht"}
                     {:tag :muokkausHetki :ns "yht"}
                     {:tag :versionumero :ns "yht"}
                     {:tag :tekija :ns "yht"
                      :child [{:tag :kuntaRooliKoodi}
                              {:tag :VRKrooliKoodi}
                              henkilo
                              yritys]}
                     {:tag :tyyppi :ns "yht"}])

(def lausunto {:tag :Lausunto
               :child [{:tag :viranomainen :ns "yht"}
                       {:tag :pyyntoPvm :ns "yht"}
                       {:tag :lausuntotieto :ns "yht"
                        :child [{:tag :Lausunto
                                 :child [{:tag :viranomainen}
                                         {:tag :lausunto}
                                         {:tag :liitetieto ; FIXME lausunnonliitetieto?
                                          :child [{:tag :Liite :child liite-children}]}
                                         {:tag :lausuntoPvm}
                                         {:tag :puoltotieto
                                          :child [{:tag :Puolto
                                                   :child [{:tag :puolto}]}]}]}]}]})


(def ymp-kasittelytieto-children [{:tag :muutosHetki :ns "yht"}
                                  {:tag :asiatunnus :ns "yht"}
                                  {:tag :paivaysPvm :ns "yht"}
                                  {:tag :kasittelija :ns "yht"
                                   :child [{:tag :henkilo
                                            :child [{:tag :nimi
                                                     :child [{:tag :etunimi}
                                                             {:tag :sukunimi}]}]}]}])

(def ymp-osapuoli-children
  [{:tag :nimi}
   {:tag :postiosoite :child postiosoite-children-ns-yht}
   {:tag :sahkopostiosoite}
   {:tag :yhteyshenkilo :child henkilo-child-ns-yht}
   {:tag :liikeJaYhteisotunnus}])

(defn update-child-element
  "Utility for updating mappings: replace child in a given path with v.
     children: sequence of :tag, :child maps
     path: keyword sequence
     v: the new value or a function that produces the new value from the old"
  [children path v]
  (map
    #(if (= (:tag %) (first path))
      (if (seq (rest path))
        (update-in % [:child] update-child-element (rest path) v)
        (if (fn? v)
          (v %)
          v))
      %)
    children))

(defn get-child-element [mapping path]
  (let [children (if (map? mapping) (:child mapping) mapping)]
    (some
      #(when (= (:tag %) (first path))
         (if (seq (rest path))
           (get-child-element % (rest path))
           %))
      children)))

(defn get-file-name-on-server [file-id file-name]
  (str file-id "_" (ss/encode-filename file-name)))

(defn get-submitted-filename [application-id]
  (str  application-id "_submitted_application.pdf"))

(defn get-current-filename [application-id]
  (str application-id "_current_application.pdf"))

(defn statements-ids-with-status [lausuntotieto]
  (reduce
    (fn [r l]
      (if (get-in l [:Lausunto :lausuntotieto :Lausunto :puoltotieto :Puolto :puolto])
        (conj r (get-in l [:Lausunto :id]))
        r))
    #{} lausuntotieto))

(defn get-Liite [title link attachment type file-id filename]
   {:kuvaus title
    :linkkiliitteeseen link
    :muokkausHetki (to-xml-datetime (:modified attachment))
    :versionumero 1
    :tyyppi type
    :fileId file-id
    :filename filename})

(defn get-liite-for-lausunto [attachment application begin-of-link]
  (let [type "Lausunto"
        title (str (:title application) ": " type "-" (:id attachment))
        file-id (get-in attachment [:latestVersion :fileId])
        attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
        link (str begin-of-link attachment-file-name)]
    {:Liite (get-Liite title link attachment type file-id attachment-file-name)}))

(defn get-statement-attachments-as-canonical [application begin-of-link allowed-statement-ids]
  (let [statement-attachments-by-id (group-by
                                      (fn-> :target :id keyword)
                                      (filter
                                        (fn-> :target :type (= "statement"))
                                        (:attachments application)))
        canonical-attachments (for [id allowed-statement-ids]
                                {(keyword id) (for [attachment ((keyword id) statement-attachments-by-id)]
                                                (get-liite-for-lausunto attachment application begin-of-link))})]
    (not-empty canonical-attachments)))

(defn get-attachments-as-canonical [{:keys [attachments title]} begin-of-link & [target]]
  (not-empty (for [attachment attachments
                   :when (and (:latestVersion attachment)
                           (not= "statement" (-> attachment :target :type))
                           (not= "verdict" (-> attachment :target :type))
                           (or (nil? target) (= target (:target attachment))))
                   :let [type (get-in attachment [:type :type-id])
                         attachment-title (str title ": " type "-" (:id attachment))
                         file-id (get-in attachment [:latestVersion :fileId])
                         attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
                         link (str begin-of-link attachment-file-name)]]
               {:Liite (get-Liite attachment-title link attachment type file-id attachment-file-name)})))

(defn write-attachments [attachments output-dir]
  (doseq [attachment attachments]
    (let [file-id (get-in attachment [:Liite :fileId])
          filename (get-in attachment [:Liite :filename])
          attachment-file (mongo/download file-id)
          content (:content attachment-file)
          attachment-file-name (str output-dir "/" filename)
          attachment-file (io/file attachment-file-name)]
      (with-open [out (io/output-stream attachment-file)
                  in (content)]
        (io/copy in out)))))

(defn- flatten-statement-attachments [statement-attachments]
  (let [attachments (for [statement statement-attachments] (vals statement))]
    (reduce concat (reduce concat attachments))))

(defn write-statement-attachments [statement-attachments output-dir]
  (let [attachments (flatten-statement-attachments statement-attachments)]
    (write-attachments attachments output-dir)))

(defn add-statement-attachments [canonical statement-attachments lausunto-path]
  (if (empty? statement-attachments)
    canonical
    (reduce
      (fn [c a]
        (let [lausuntotieto (get-in c lausunto-path)
              lausunto-id (name (first (keys a)))
              paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id)%) lausuntotieto)
              index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
              paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausuntotieto :Lausunto :liitetieto] ((keyword lausunto-id) a))
              paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
          (assoc-in c lausunto-path paivitetty)))
      canonical
      statement-attachments)))

(defn write-to-disk
  "Writes XML string to disk and copies attachments from database. XML is validated before writing.
   Returns a sequence of attachment fileIds that were written to disk."
  [application attachments statement-attachments xml krysp-version output-dir & [extra-emitter]]
  {:pre [(string? output-dir)]
   :post [%]}

  (let [file-name  (str output-dir "/" (:id application) "_" (lupapalvelu.core/now))
        tempfile   (io/file (str file-name ".tmp"))
        outfile    (io/file (str file-name ".xml"))
        xml-s      (indent-str xml)]

    (try
      (validator/validate xml-s (permit/permit-type application) krysp-version)
      (catch org.xml.sax.SAXParseException e
       (info e "Invalid KRYSP XML message")
       (fail! :error.integration.send :details (.getMessage e))))

    (fs/mkdirs output-dir)
    (try
      (with-open [out-file-stream (io/writer tempfile)]
        (emit xml out-file-stream))
      ;; this has to be called before calling "with-open" below)
      (catch java.io.FileNotFoundException e
        (error e (.getMessage e))
        (fail! :error.sftp.user.does.not.exist :details (.getMessage e))))


    (write-attachments attachments output-dir)
    (write-statement-attachments statement-attachments output-dir)

    (when (fn? extra-emitter) (extra-emitter))

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile))

  (->>
    (concat attachments (flatten-statement-attachments statement-attachments))
    (map #(get-in % [:Liite :fileId]))
    (filter identity)))
