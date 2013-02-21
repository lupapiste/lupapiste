(ns lupapalvelu.notifications  
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.strings :only [suffix]]
        [lupapalvelu.core])
  (:require [sade.security :as sadesecurity]
            [sade.client :as sadeclient]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.email :as email]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(defn resolve-host-name []
  "http://localhost:8000")

(defn message-for-new-application [application host]
  (let [permit-type (:permitType application)
        permit-type-name (if (= permit-type "infoRequest") {:fi (str "neuvontapyynt\u00F6") :sv (str "r\u00E5dbeg\u00E4ra")} {:fi (str "hakemus") :sv (str "ans\u00F6kan")})
        permit-type-path (if (= permit-type "infoRequest") (str "inforequest") (str "application"))]
    (format
      (str
        "Hei,\n\nUusi %s luotu. Katso hakemus osoitteesta %s/fi/applicant#!/%s/%s\n\n"
        "Yst\u00E4v\u00E4llisin terveisin,\n\nLupapiste.fi\n\n\n\n"
        "Hej,\n\n"
        "En ny %s har anl\u00E4ndat. Se ans\u00F6kan i addresset %s/sv/applicant#!/%s/%s\n\n"
        "\n\n")
      (:fi permit-type-name)
      host
      permit-type-path
      (:id application)
      (:sv permit-type-name)
      host
      permit-type-path
      (:id application))))

(defn send-notifications-on-new-application [application-id]
  (println "notifying on " application-id)
  
  (if-let [application (mongo/by-id :applications application-id)]
    (let [email "timo.lehtonen@solita.fi"
          msg (message-for-new-application application (resolve-host-name))]
      (println msg)
      (future
        (info "sending email to" email)
        (if (email/send-email email (:title application) msg)
          (info "email was sent successfully")) ((error "email could not be delivered."))))
    (do
      (debugf "application '%s' not found" application-id)
      (fail :error.application-not-found))))
