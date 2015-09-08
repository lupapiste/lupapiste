(ns lupapalvelu.xml.krysp.maankayton-muutos-mapping
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [lupapalvelu.document.maankayton-muutos-canonical :as maankayton-muutos-canonical]
            [lupapalvelu.permit :as permit]
            lupapalvelu.xml.disk-writer
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]))

(defn sorter
  "Sorter functions for the immediate children of the tag.
   Used with sort-by. This is needed because typical XML schemas have
   fixed order of the child elements"
  [tag]
  ;; Sorting is case-insensitive. We list all the fields (child elements) although
  ;; not all of them are used.
  (if tag
    (let [fields {"toimituksentiedot" ["aineistonnimi" "aineistotoimittaja" "tila" "toimituspvm" "kuntakoodi"
                                       "kielitieto" "metatietotunniste" "metatietoxmlurl" "metatietourl"
                                       "tietotuoteurl"]
                  "tonttijako" ["toimituksentiedottieto" "hakemustieto" "paatostieto" "referenssisijaintitieto"
                                "toimituksentila" "toimitusnumero" "liitetieto" "kiinteisto" "maaraala"
                                "uusikytkin" "kuvaus"]
                  "hakemus" ["hakemustunnustieto" "osapuolitieto" "sijaintitieto" "liitetieto"
                             "kohdekiinteisto" "maaraala" "tilatieto" "kuvaus"]
                  "osapuoli" ["roolikoodi" "turvakieltokytkin" "asioimiskieli" "henkilotieto"
                              "yritystieto" "vainsahkoinenasiointikytkin"]
                  "henkilo" ["nimi" "osoite" "sahkopostiosoite" "faksinumero" "puhelin" "henkilotunnus"]
                  "yritys" ["nimi" "liikejayhteisotunnus" "kayntiosoitetieto" "postiosoitetieto"
                            "kotipaikka" "faksinumero" "puhelin" "www" "sahkopostiosoite" "verkkolaskutustieto"]
                  }

          tag-str    (fn [t] (-> t name str/lower-case))]
      (if-let [xs (get fields (tag-str tag))]
        #(.indexOf xs (tag-str %))
        identity))
    identity))

(defn make-seq [a]
  (if (sequential? a)
    a
    [a]))

(defn tag-ns [path]
  (when (> (count  path) 4)
    (let [ns-paths { [:toimituksenTiedottieto :ToimituksenTiedot] "yht"
                     [:hakemustieto :Hakemus :osapuolitieto :Osapuoli :henkilotieto :Henkilo] "yht"
                     [:hakemustieto :Hakemus :sijaintitieto :Sijainti] "yht"}
          popped (pop (subvec path 2))]

      (defn popper [p]
        (when (not-empty p)
          (if-let [ns (get ns-paths p)]
            ns
            (popper (pop p)))))
      (popper popped))))

(defn unwrap [xs]
  (if (and (sequential? xs) (= (count xs) 1))
    (first xs)
    xs))

(defn tagger
  ([k v path]
   (let [ns (tag-ns path)
         m-ns (when ns
                {:ns ns})
         m (merge  {:tag k} m-ns)]
     (if (coll? v)
       (assoc m :child (-> v (tagger path) make-seq))
       m)))
  ([arg path]
   (if (map? arg)
     (let [tags (sort-by (sorter (last path)) (keys arg))
           m  (map (fn [k] (tagger k (k arg) (conj path k))) tags)]
       (unwrap m))
     (when (coll? arg)
       (unwrap (map #(tagger % path) arg))))))


(defn taggy
  "Returns list [map ns] where the map contains :tag and :ns (if given).
   The tag name is defined by argument k. The format is
   :tagname/ns where the namespace part is optional.
   Note: namespace is returned but not used on this element.
   The namespace for this element is the ns argument or nothing."
  [k & [ns]]
  (let [[tag new-ns] (-> k str rest str/join (str/split #"/"))]
    [(merge (when ns {:ns ns})
            {:tag (keyword tag)}) (or new-ns ns)]))

(defmulti mapper (fn [& args]
                   (let [arg (first args)]
                     (if (map? arg)
                      :map
                      (if (keyword? arg)
                        :keyword
                        (if (sequential? arg)
                          :sequential))))))

(defmethod mapper :map [m & [ns]]
  (let [k (-> m keys first)
        [tag ns] (taggy k ns)
        v (k m)]
    ;; Mapping sanity check
    (assert (= (count m) 1))
    (assoc tag :child (make-seq (mapper v ns)))))

(defmethod mapper :keyword [kw & [ns]]
  (first (taggy kw ns)))

(defmethod mapper :sequential [xs & [ns]]
  (map #(mapper % ns) xs))

(defn ->mapping [muutos]
  (let [osoite [{:osoitenimi :teksti} :postinumero :postitoimipaikannimi]]
    {:tag :Maankaytonmuutos :ns "mkmu"
     :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MM "1.0.1")
                   :xmlns:mkmu "http://www.paikkatietopalvelu.fi/gml/maankaytonmuutos"}
                  mapping-common/common-namespaces)
     :child [(mapper {:maankayttomuutosTieto
                      {muutos
                       [{:toimituksenTiedottieto
                         {:ToimituksenTiedot/yht [:aineistonnimi :aineistotoimittaja :tila :toimitusPvm :kuntakoodi
                                                  :kielitieto]}}
                        {:hakemustieto
                         [{:Hakemus
                           {:osapuolitieto
                            {:Osapuoli
                             [:roolikoodi :turvakieltokytkin :asioimiskieli
                              {:henkilotieto
                               {:Henkilo/yht [{:nimi [:etunimi :sukunimi]}
                                              {:osoite osoite}
                                              :sahkopostiosoite
                                              :faksinumero
                                              :puhelin
                                              :henkilotunnus]}}
                              {:yritystieto
                               {:Yritys/yht [:nimi :liikeJaYhteisotunnus
                                             {:postiosoitetieto {:postiosoite osoite}}
                                             :sahkopostiosoite
                                             :puhelin
                                             :sahkopostiosoite]}}
                              :vainsahkoinenAsiointiKytkin]}}}
                          {:sijaintitieto {:Sijainti/yht [{:osoite [:yksilointitieto :alkuHetki {:osoitenimi :teksti}]}
                                                          {:piste {:Point :pos}}]}}
                          :kohdekiinteisto
                          :maaraAla
                          {:tilatieto {:Tila/yht [:pvm :kasittelija :hakemuksenTila]}}]}
                        :toimituksenTila
                        :uusiKytkin
                        :kuvaus]}})]}))




#_(defn ->krysp [{mm :Maankaytonmuutos}]
  (merge {:tag :Maankaytonmuutos :ns "mkmu"
          :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation :MM "1.0.1")
                        :xmlns:mkmu "http://www.paikkatietopalvelu.fi/gml/maankaytonmuutos"}
                       mapping-common/common-namespaces) }
         {:child [(tagger mm [])]}))



(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on MM applications."
  [application lang submitted-application krysp-version output-dir begin-of-link]
  (let [canonical-without-attachments  (maankayton-muutos-canonical/maankayton-muutos-canonical application lang)
        attachments-canonical (mapping-common/get-attachments-as-canonical application begin-of-link)
        muutos (-> canonical-without-attachments :Maankaytonmuutos :maankayttomuutosTieto first key)
        canonical (assoc-in
                    canonical-without-attachments
                    [:Maankaytonmuutos :maankayttomuutosTieto muutos :liitetieto ]
                    attachments-canonical)
        _ (>pprint canonical-without-attachments)
        mapping (->mapping muutos)
        _ (>pprint mapping)
        xml (element-to-xml canonical-without-attachments mapping)
        _ (>pprint xml)
        attachments-for-write (mapping-common/attachment-details-from-canonical attachments-canonical)]
    (writer/write-to-disk
      application
      attachments-for-write
      xml
      krysp-version
      output-dir
      submitted-application
      lang)))

(permit/register-function permit/MM :app-krysp-mapper save-application-as-krysp)
