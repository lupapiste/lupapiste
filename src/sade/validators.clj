(ns sade.validators
  (:require [sade.dns :as dns]
            [sade.env :as env]
            [sade.strings :as ss]))


(defn valid-email? [email]
  (try
    (javax.mail.internet.InternetAddress. email)
    (boolean (re-matches #".+@.+\..+" email))
    (catch Exception _
      false)))

(defn email-and-domain-valid? [email]
  (or (ss/blank? email)
    (and
      (valid-email? email)
      (or (env/value :email :skip-mx-validation) (dns/valid-mx-domain? email)))))

(defn finnish-y? [y]
  (if y
    (if-let [[_ number check] (re-matches #"(\d{7})-(\d)" y)]
      (let [cn (mod (reduce + (map * [7 9 10 5 8 4 2] (map #(Long/parseLong (str %)) number))) 11)
            cn (if (zero? cn) 0 (- 11 cn))]
        (= (Long/parseLong check) cn)))
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
