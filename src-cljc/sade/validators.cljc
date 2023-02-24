(ns sade.validators
  (:require #?@(:clj [[clj-time.format :as timeformat]
                      [sade.env :as env]]
                :cljs [[cljs-time.format :as timeformat]])))

(defn matches? [re s] (boolean (when (string? s) (re-matches re s))))

#?(:clj (defn parse-long [s] (Long/parseLong s)))

(defn valid-email? [email]
  (try
    #?(:clj (javax.mail.internet.InternetAddress. email))
    (boolean (re-matches #"\S+@\S+\.\S+" email))
    (catch #?(:clj Exception
              :cljs js/Object) _
      false)))

; Regex derived from @stephenhay's at https://mathiasbynens.be/demo/url-regex
(def http-url? (partial matches? #"^(https?)://[^\s/$.?#].[^\s]*$"))

(defn finnish-y? [y]
  (if y
    (if-let [[_ number check] (re-matches #"(\d{7})-(\d)" y)]
      (let [cn (mod (reduce + (map * [7 9 10 5 8 4 2] (map #(parse-long (str %)) number))) 11)
            cn (if (zero? cn) 0 (- 11 cn))]
        (= (parse-long check) cn)))
    false))

(defn finnish-ovt?
  "OVT-tunnus SFS 5748 standardin mukainen OVT-tunnus rakentuu ISO6523 -standardin
   mukaisesta Suomen verohallinnon tunnuksesta 0037, Y-tunnuksesta
   (8 merkki\u00e4 ilman v\u00e4liviivaa) sek\u00e4 vapaamuotoisesta 5 merkist\u00e4,
   jolla voidaan antaa organisaation alataso tai kustannuspaikka.
   http://www.tieke.fi/pages/viewpage.action?pageId=17104927"
  [ovt]
  (if ovt
    (if-let [[_ y c] (re-matches #"0037(\d{7})(\d)\w{0,5}" ovt)]
      (finnish-y? (str y \- c)))
    false))

(def rakennusnumero? (partial matches? #"^\d{3}$"))

(def vrk-checksum-chars ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "A" "B" "C" "D" "E" "F" "H" "J" "K" "L" "M" "N" "P" "R" "S" "T" "U" "V" "W" "X" "Y"])

(def finnish-hetu-str "(0[1-9]|[12]\\d|3[01])(0[1-9]|1[0-2])([5-9]\\d\\+|\\d\\d-|\\d\\dA)\\d{3}[\\dA-Y]")

(def finnish-hetu-regex (re-pattern (str "^" finnish-hetu-str "$")))

(defn vrk-checksum [l]
  (nth vrk-checksum-chars (mod l 31)))

(defn hetu-checksum [^String hetu]
  (vrk-checksum (parse-long (str (subs hetu 0 6) (subs hetu 7 10)))))

(defn- validate-hetu-date [hetu]
  (let [dateparts (rest (re-find #"^(\d{2})(\d{2})(\d{2})([aA+-]).*" hetu))
        yy (last (butlast dateparts))
        yyyy (str (case (last dateparts) "+" "18" "-" "19" "20") yy)
        basic-date (str yyyy (second dateparts) (first dateparts))]
    (try
      (timeformat/parse (timeformat/formatters :basic-date) basic-date)
      true
      (catch #?(:clj Exception
                :cljs :default) _
        false))))

(defn- validate-hetu-checksum [hetu]
  (= (subs hetu 10 11) (hetu-checksum hetu)))

(defn valid-hetu? [^String hetu]
  (if hetu
    (and (validate-hetu-date hetu) (validate-hetu-checksum hetu))
    false))

(defn- rakennustunnus-checksum [^String prt]
  (vrk-checksum (parse-long  (subs prt 0 9))))

(defn- rakennustunnus-checksum-matches? [^String prt]
  (= (subs prt 9 10) (rakennustunnus-checksum prt)))

(def rakennustunnus-pattern
  "VRK pysyva rakennustunnus. KRYSP-skeemassa: ([1][0-9]{8})[0-9ABCDEFHJKLMNPRSTUVWXY]"
  #?(:clj (re-pattern (or (env/value :rakennustunnus-pattern) "^1\\d{8}[0-9A-FHJ-NPR-Y]$"))
     :cljs #"^1\{8}[0-9A-FHJ-NPR-Y]$"))

(defn rakennustunnus? [^String prt]
  (and (matches? rakennustunnus-pattern prt) (rakennustunnus-checksum-matches? prt)))

(def finnish-zip? (partial matches? #"^\d{5}$"))

(def maara-alatunnus-pattern #"^M?([0-9]{1,4})$")

(def kiinteistotunnus? (partial matches? #"^[0-9]{14}$"))

;; accepts either property id or property id and maara-ala
(def kiinteisto-ja-maara-alatunnus? (partial matches? #"^([0-9]{14})(M?([0-9]{1,4}))?$"))

(def ipv4-address? (partial matches? #"^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))$"))

(def ipv6-address-long-form? (partial matches? #"([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}"))

(def ip-address? (some-fn ipv4-address? ipv6-address-long-form?))

(def hex-string? (partial matches? #"^[0-9a-f]*$"))

;; Some of the very first applications have mongoid as applicationId. But LPK-2914 cleaned them up.
(def application-id-pattern #"^L[PX]-\d{3}-\d{4}-\d{5}$")
(def application-id? (partial matches? application-id-pattern))
