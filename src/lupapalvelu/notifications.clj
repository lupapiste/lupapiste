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

;TODO where to find out this?
(defn resolve-host-name []
  "http://localhost:8000")

(defn message-for-new-comment-in-application [application host]
  (let [permit-type (:permitType application)
        permit-type-name (if (= permit-type "infoRequest") {:fi (str "Neuvontapyynt\u00F6\u00F6n") :sv (str "R\u00E5dbeg\u00E4ra")} {:fi (str "Hakemukseen") :sv (str "Ans\u00F6kan")})
        permit-type-path (if (= permit-type "infoRequest") (str "inforequest") (str "application"))]
    (format
      (str
        "Hei,\n\n%s on lis\u00E4tty kommentti. Katso lis\u00E4tietoja osoitteesta %s/fi/applicant#!/%s/%s\n\n"
        "Yst\u00E4v\u00E4llisin terveisin,\n\nLupapiste.fi\n\n\n\n"
        "Hej,\n\n"
        "%s har nya commenter. Se mera information i addresset %s/sv/applicant#!/%s/%s\n\n"
        "\n\n"
        "Grattis,\n\nLupapiste.fi\n\n\n\n")
      (:fi permit-type-name)
      host
      permit-type-path
      (:id application)
      (:sv permit-type-name)
      host
      permit-type-path
      (:id application))))

(defn user-is-in-authority-role [user]
  (= "authority" (:role user)))

(defn send-notifications-on-new-comment [user-commenting application]
  (if (user-is-in-authority-role user-commenting)
    (do
      (println "notification on new comment for " application)
      (let [email "timo.lehtonen@solita.fi"
            msg (message-for-new-comment-in-application application (resolve-host-name))]
        (println msg)
        (future
          (info "sending email to" email)
          (if (email/send-email email (:title application) msg)
            (info "email was sent successfully")) ((error "email could not be delivered."))) 
        nil))))
