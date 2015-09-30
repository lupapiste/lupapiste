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
