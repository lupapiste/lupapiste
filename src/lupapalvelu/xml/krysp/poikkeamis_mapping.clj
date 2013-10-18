(ns lupapalvelu.xml.krysp.poikkeamis-mapping
  (:require [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [me.raynes.fs :as fs]
            [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [sade.util :refer :all]
            [lupapalvelu.document.canonical-common :refer [to-xml-datetime]]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.krysp.validator :refer [validate]]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]))


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
                                                                    mapping-common/henkilo
                                                                    mapping-common/yritys]}
                                                           {:tag :tyyppi :ns "yht"}]}]}
                                         {:tag :lausuntoPvm}
                                         {:tag :puoltotieto
                                          :child [{:tag :Puolto
                                                   :child [{:tag :puolto}]}]}]}]}]})

(def abstractPoikkeamisType [{:tag :kasittelynTilatieto :child [mapping-common/tilamuutos]}
                             {:tag :luvanTunnistetiedot
                              :child [mapping-common/lupatunnus]}
                             {:tag :osapuolettieto
                              :child [mapping-common/osapuolet]}
                             {:tag :rakennuspaikkatieto
                              :child [mapping-common/rakennuspaikka]}])

(def poikkeamis_to_krysp
  {:tag :Popast
   :ns "ppst"
   :attr {:xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/yhteiset
                               http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd
                               http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu
                               http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu/2.1.2/poikkeamispaatos_ja_suunnittelutarveratkaisu.xsd"
          :xmlns:ppst "http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu"
          :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
          :xmlns:xlink "http://www.w3.org/1999/xlink"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :poikkeamisasiatieto :child [{:tag :Poikkeamisasia :child abstractPoikkeamisType}]}
           {:tag :suunnittelutarveasiatieto :child [abstractPoikkeamisType]}
           ]})


(defn- add-statement-attachments [canonical statement-attachments]
  (if (empty? statement-attachments)
    canonical
    (reduce (fn [c a]
              (let [lausuntotieto (get-in c [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto])
                    lausunto-id (name (first (keys a)))
                    paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id)%) lausuntotieto)
                    index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
                    paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausuntotieto :Lausunto :liitetieto] ((keyword lausunto-id) a))
                    paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
                (assoc-in c [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :lausuntotieto] paivitetty))
              ) canonical statement-attachments)))

(defn save-application-as-krysp [application lang submitted-application output-dir begin-of-link]
  (let [file-name  (str output-dir "/" (:id application))
        tempfile   (file (str file-name ".tmp"))
        outfile    (file (str file-name ".xml"))
        canonical  (poikkeus-application-to-canonical application lang)
        xml (element-to-xml canonical poikkeamis_to_krysp)
        xml-s (indent-str xml)]
    ;(clojure.pprint/pprint (:attachments application))
    ;(clojure.pprint/pprint canonical-with-statement-attachments)
    (println xml-s)
    (validate xml-s)
    (fs/mkdirs output-dir)  ;; this has to be called before calling with-open below
    ))
