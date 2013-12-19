(ns lupapalvelu.xml.krysp.mapping-common
  (:require [clojure.java.io :as io]
            [clojure.data.xml :refer [emit indent-str]]
            [me.raynes.fs :as fs]
            [sade.strings :as ss]
            [sade.util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.xml.krysp.validator :as validator]))


(def tunnus-children [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}])

(def ^:private piste {:tag :piste  :ns "yht"
                      :child [{:tag :Point
                               :child [{:tag :pos}]}]})

(def ^:private postiosoite-children [{:tag :kunta}
                                     {:tag :osoitenimi :child [{:tag :teksti}]}
                                     {:tag :postinumero}
                                     {:tag :postitoimipaikannimi}])

;; henkilo-child is used also in "yleiset alueet" but it needs the namespace to be defined again to "yht")
(def postiosoite-children-ns-yht (into [] (map (fn [m] (assoc m :ns "yht")) postiosoite-children)))

(def ^:private osoite {:tag :osoite  :ns "yht"
                       :child postiosoite-children})

(def sijantitieto {:tag :sijaintitieto
                   :child [{:tag :Sijainti
                            :child [{:tag :tyhja  :ns "yht"}
                                     osoite
                                     piste
                                     {:tag :sijaintiepavarmuus  :ns "yht"}
                                     {:tag :luontitapa  :ns "yht"}]}]})

(def ^:private rakennusoikeudet [:tag :rakennusoikeudet
                                 :child [{:tag :kayttotarkoitus
                                          :child [{:tag :pintaAla}
                                                  {:tag :kayttotarkoitusKoodi}]}]])

(def ^:private kiinteisto [{:tag :kiinteisto
                            :child (conj [{:tag :kiinteisto
                                           :child [{:tag :kylanimi}
                                                   {:tag :tilannimi}
                                                   {:tag :kiinteistotunnus}
                                                   {:tag :maaraAlaTunnus}]}
                                          {:tag :palsta}
                                          {:tag :kokotilaKytkin}
                                          {:tag :hallintaperuste}
                                          {:tag :vuokraAluetunnus}
                                          {:tag :kaavanaste}
                                          {:tag :kerrosala}
                                          {:tag :tasosijainti}
                                          {:tag :rakennusoikeusYhteensa}
                                          {:tag :uusiKytkin}]
                                     osoite
                                     sijantitieto
                                     rakennusoikeudet)}])

(def rakennuspaikka {:tag :Rakennuspaikka
                     :child [{:tag :yksilointitieto :ns "yht"}
                             {:tag :alkuHetki :ns "yht"}
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
(def henkilo-child-ns-yht (into [] (map (fn [m] (assoc m :ns "yht")) henkilo-child)))

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
                             {:tag :valmistumisvuosi}]}]}
           {:tag :tyonjohtajatieto
            :child [{:tag :Tyonjohtaja
                     :child [{:tag :tyonjohtajaRooliKoodi}
                             {:tag :VRKrooliKoodi}
                             henkilo
                             yritys
                             {:tag :patevyysvaatimusluokka}
                             {:tag :koulutus}
                             {:tag :valmistumisvuosi}
                             ;{:tag :vastattavatTyotehtavat}      ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             ;{:tag :valvottavienKohteidenMaara}  ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             ;{:tag :kokemusvuodet}               ;; Tama tulossa kryspiin -> TODO: Ota sitten kayttoon!
                             {:tag :tyonjohtajaHakemusKytkin}]}]}
           {:tag :naapuritieto}]})

(def tilamuutos
  {:tag :Tilamuutos :ns "yht"
   :child [{:tag :pvm}
           {:tag :tila}
           {:tag :kasittelija :child [henkilo]}]})

(def lupatunnus {:tag :LupaTunnus :ns "yht" :child [{:tag :kuntalupatunnus}
                                                    {:tag :muuTunnustieto
                                                     :child [{:tag :MuuTunnus :child [{:tag :tunnus}
                                                                                      {:tag :sovellus}]}]}
                                                    {:tag :saapumisPvm}
                                                    {:tag :viittaus}]})

(def toimituksenTiedot [{:tag :aineistonnimi :ns "yht"}
                        {:tag :aineistotoimittaja :ns "yht"}
                        {:tag :tila :ns "yht"}
                        {:tag :toimitusPvm :ns "yht"}
                        {:tag :kuntakoodi :ns "yht"}
                        {:tag :kielitieto :ns "yht"}])

(def lausunto {:tag :Lausunto
               :child [{:tag :viranomainen :ns "yht"}
                       {:tag :pyyntoPvm :ns "yht"}
                       {:tag :lausuntotieto :ns "yht"
                        :child [{:tag :Lausunto
                                 :child [{:tag :viranomainen}
                                         {:tag :lausunto}
                                         {:tag :liitetieto
                                          :child [{:tag :Liite
                                                   :child [{:tag :kuvaus :ns "yht"}
                                                           {:tag :linkkiliitteeseen :ns "yht"}
                                                           {:tag :muokkausHetki :ns "yht"}
                                                           {:tag :versionumero :ns "yht"}
                                                           {:tag :tekija :ns "yht"
                                                            :child [{:tag :kuntaRooliKoodi}
                                                                    {:tag :VRKrooliKoodi}
                                                                    henkilo
                                                                    yritys]}
                                                           {:tag :tyyppi :ns "yht"}]}]}
                                         {:tag :lausuntoPvm}
                                         {:tag :puoltotieto
                                          :child [{:tag :Puolto
                                                   :child [{:tag :puolto}]}]}]}]}]})


(defn get-file-name-on-server [file-id file-name]
  (str file-id "_" (ss/encode-filename file-name)))

(defn get-submitted-filename [application-id]
  (str application-id "_submitted_application.pdf"))

(defn get-current-filename [application-id]
  (str application-id "_current_application.pdf"))

(defn statements-ids-with-status [lausuntotieto]
  (reduce
    (fn [r l]
      (if (get-in l [:Lausunto :lausuntotieto :Lausunto :puoltotieto :Puolto :puolto])
        (conj r (get-in l [:Lausunto :id]))
        r))
    #{} lausuntotieto))

(defn get-Liite [title link attachment type file-id]
   {:kuvaus title
    :linkkiliitteeseen link
    :muokkausHetki (to-xml-datetime (:modified attachment))
    :versionumero 1
    :tyyppi type
    :fileId file-id})

(defn get-liite-for-lausunto [attachment application begin-of-link]
  (let [type "Lausunto"
        title (str (:title application) ": " type "-" (:id attachment))
        file-id (get-in attachment [:latestVersion :fileId])
        attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
        link (str begin-of-link attachment-file-name)]
    {:Liite (get-Liite title link attachment type file-id)}))

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

(defn get-attachments-as-canonical [application begin-of-link & [target]]
  (let [attachments (:attachments application)
        canonical-attachments (for [attachment attachments
                                    :when (and (:latestVersion attachment)
                                            (not= "statement" (-> attachment :target :type))
                                            (not= "verdict" (-> attachment :target :type))
                                            (or (nil? target) (= target (:target attachment))))
                                    :let [type (get-in attachment [:type :type-id])
                                          title (str (:title application) ": " type "-" (:id attachment))
                                          file-id (get-in attachment [:latestVersion :fileId])
                                          attachment-file-name (get-file-name-on-server file-id (get-in attachment [:latestVersion :filename]))
                                          link (str begin-of-link attachment-file-name)]]
                                {:Liite (get-Liite title link attachment type file-id)})]
    (not-empty canonical-attachments)))

(defn write-attachments [attachments output-dir]
  (doseq [attachment attachments]
    (let [file-id (get-in attachment [:Liite :fileId])
          attachment-file (mongo/download file-id)
          content (:content attachment-file)
          attachment-file-name (str output-dir "/" (get-file-name-on-server file-id (:file-name attachment-file)))
          attachment-file (io/file attachment-file-name)
          ]
      (with-open [out (io/output-stream attachment-file)
                  in (content)]
        (io/copy in out)))))

(defn write-statement-attachments [attachments output-dir]
  (let [f (for [fi attachments]
            (vals fi))
        files (reduce concat (reduce concat f))]
    (write-attachments files output-dir)))

(defn add-statement-attachments [canonical statement-attachments]
  (if (empty? statement-attachments)
    canonical
    (reduce
      (fn [c a]
        (let [lausuntotieto (get-in c [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto])
              lausunto-id (name (first (keys a)))
              paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id)%) lausuntotieto)
              index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
              paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausuntotieto :Lausunto :liitetieto] ((keyword lausunto-id) a))
              paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
          (assoc-in c [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto] paivitetty)))
      canonical
      statement-attachments)))

(defn write-to-disk
  "Writes XML string to disk and copies attachments from database. XML is validated before writing."
  [application attachments statement-attachments xml output-dir & [extra-emitter]]
  (let [file-name  (str output-dir "/" (:id application))
        tempfile   (io/file (str file-name ".tmp"))
        outfile    (io/file (str file-name ".xml"))
        xml-s      (indent-str xml)]

    (validator/validate xml-s)

    (fs/mkdirs output-dir)  ;; this has to be called before calling "with-open" below)
    (with-open [out-file-stream (io/writer tempfile)]
      (emit xml out-file-stream))

    (write-attachments attachments output-dir)
    (write-statement-attachments statement-attachments output-dir)

    (when (fn? extra-emitter) (extra-emitter))

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile)))
