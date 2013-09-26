(ns lupapalvelu.document.canonical-common
  (:require [clj-time.format :as timeformat]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [lupapalvelu.core :refer [now]]))


; Empty String will be rendered as empty XML element
(def empty-tag "")

; State of the content when it is send over KRYSP
; NOT the same as the state of the application!
(def toimituksenTiedot-tila "keskener\u00e4inen")

(def application-state-to-krysp-state
  {:draft "uusi lupa, ei k\u00e4sittelyss\u00e4"
   :open "vireill\u00e4"
   :sent "vireill\u00e4"
   :submitted "vireill\u00e4"
   :complement-needed "vireill\u00e4"})

(def state-timestamps
  {:draft :created
   :open :opened
   :complement-needed :opened
   ; Application state in KRYSP will be "vireill\u00e4" -> use :opened date
   :submitted :opened
   ; Enables XML to be formed from sent applications
   :sent :opened})

(defn to-xml-date [timestamp]
  (let [dt (tc/from-long timestamp)]
    (if-not (nil? timestamp)
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd") dt))))

(defn to-xml-datetime [timestamp]
  (let [dt (tc/from-long timestamp)]
    (if-not (nil? timestamp)
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") dt))))

(defn to-xml-date-from-string [date-as-string]
  (let [d (timeformat/parse-local-date (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
    (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd") d)))

(defn to-xml-datetime-from-string [date-as-string]
  (let [d (timeformat/parse-local (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
    (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") d)))

(defn by-type [documents]
  (group-by (comp keyword :name :schema-info) documents))


(def ^:private puolto-mapping {:condition "ehdoilla"
                               :no "ei puolla"
                               :yes "puoltaa"})

(defn- get-statement [statement]
  (let [lausunto {:Lausunto
                  {:id (:id statement)
                   :viranomainen (get-in statement [:person :text])
                   :pyyntoPvm (to-xml-date (:requested statement))}}]
    (if-not (:status statement)
      lausunto
      (assoc-in lausunto [:Lausunto :lausuntotieto] {:Lausunto
                                                     {:viranomainen (get-in statement [:person :text])
                                                      :lausunto (:text statement)
                                                      :lausuntoPvm (to-xml-date (:given statement))
                                                      :puoltotieto
                                                      {:Puolto
                                                       {:puolto ((keyword (:status statement)) puolto-mapping)}}}}))))

(defn get-statements [statements]
  ;Returing vector because this element to be Associative
  (vec (map get-statement statements)))

(defn empty-strings-to-nil [v]
  (if (and (string? v) (s/blank? v)) nil v))

(defn muu-select-map
  "If 'sel-val' is \"other\" considers 'muu-key' and 'muu-val', else considers 'sel-key' and 'sel-val'.
   If value (either 'muu-val' or 'sel-val' is blank, return nil, else return map with
   considered key mapped to considered value."
  [muu-key muu-val sel-key sel-val]
  (let [muu (= "other" sel-val)
        k   (if muu muu-key sel-key)
        v   (if muu muu-val sel-val)]
    (when-not (s/blank? v)
      {k v})))

(def ya-operation-type-to-usage-description
  {:ya-kaivuulupa "kaivu- tai katuty\u00f6lupa"
   :ya-kayttolupa-tyomaasuojat-ja-muut-rakennelmat "ty\u00f6maasuojien ja muiden rakennelmien sijoittaminen yleiselle alueelle"
   :ya-kayttolupa-mainostus-ja-viitoitus "mainoslaitteiden ja opasteviittojen sijoittaminen"
   :ya-kayttolupa-muut-yleisten-alueiden-tilojen-kaytot "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-messujen-ja-tapahtumien-alueiden-kaytot "erilaiset messujen ja tapahtumien aikaiset alueiden k\u00e4yt\u00f6t"
   :ya-kayttolupa-kadulta-tapahtuvat-nostot "kadulta tapahtuvat nostot"
   :ya-kayttolupa-kiinteistojen-tyot-jotka-varaavat-yleisen-alueen-tyomaaksi "kadulle pystytett\u00e4v\u00e4t rakennustelineet"
   :ya-kayttolupa-rakennustelineet-kadulla "kiinteist\u00f6n rakentamis- ja korjaamisty\u00f6t, joiden suorittamiseksi rajataan osa kadusta tai yleisest\u00e4 alueesta ty\u00f6maaksi (ei kaivut\u00f6it\u00e4)"
   :ya-kayttolupa-muu-kayttolupa "muu kaytt\u00f6lupa"
   :ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-pysyvien-maanpaallisten-rakenteiden-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muu-sijoituslupa "muu sijoituslupa"})

(def ya-operation-type-to-schema-name-key
  {:ya-kaivuulupa :Tyolupa
   :ya-kayttolupa-tyomaasuojat-ja-muut-rakennelmat :Kayttolupa
   :ya-kayttolupa-mainostus-ja-viitoitus :Kayttolupa
   :ya-kayttolupa-muut-yleisten-alueiden-tilojen-kaytot :Kayttolupa
   :ya-kayttolupa-messujen-ja-tapahtumien-alueiden-kaytot :Kayttolupa
   :ya-kayttolupa-kadulta-tapahtuvat-nostot :Kayttolupa
   :ya-kayttolupa-kiinteistojen-tyot-jotka-varaavat-yleisen-alueen-tyomaaksi :Kayttolupa
   :ya-kayttolupa-rakennustelineet-kadulla :Kayttolupa
   :ya-kayttolupa-muu-kayttolupa :Kayttolupa
   :ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-pysyvien-maanpaallisten-rakenteiden-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-muu-sijoituslupa :Sijoituslupa})

(defn toimituksen-tiedot [application lang]
  {:aineistonnimi (:title application)
   :aineistotoimittaja "lupapiste@solita.fi"
   :tila toimituksenTiedot-tila
   :toimitusPvm (to-xml-date (now))
   :kuntakoodi (:municipality application)
   :kielitieto lang})

(defn- get-handler [application]
  (if-let [handler (:authority application)]
    {:henkilo {:nimi {:etunimi  (:firstName handler)
                      :sukunimi (:lastName handler)}}}
    empty-tag))


(defn- get-state [application]
  (let [state (keyword (:state application))]
    {:Tilamuutos
     {:tila (application-state-to-krysp-state state)
      :pvm (to-xml-date ((state-timestamps state) application))
      :kasittelija (get-handler application)}}))


