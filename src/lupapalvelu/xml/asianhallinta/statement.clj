(ns lupapalvelu.xml.asianhallinta.statement
  (:require [taoensso.timbre :refer [infof]]
            [sade.core :refer :all]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.xml :as xml]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.xml.asianhallinta.attachment :as ah-att]))

(def path-to-required-keys {[:Lausunto :LausuntoTunnus] :id
                            [:HakemusTunnus] :application-id
                            [:Lausunto :Puolto] :status
                            [:Lausunto :LausuntoPvm] :given})

(defn get-required-data!
  "Returns values in map. If value not found throws error."
  [xml-edn]
  (reduce
    (fn [map path]
      (if-let [val (get-in xml-edn path)]
        (assoc map (get path-to-required-keys (vec path)) val)
        (error-and-fail!
          (str "ah-statement-reader - no value for required path: " (pr-str path))
          :error.integration.asianhallinta.statement-no-value)))
    {}
    (keys path-to-required-keys)))

(defn statement-response-handler [parsed-xml unzipped-path ftp-user system-user created]
  (let [xml-edn   (xml/xml->edn parsed-xml)
        ely-user  (assoc system-user :firstName "ELY-keskus")
        lausunto-vastaus (:LausuntoVastaus xml-edn)
        ; external-id (:AsianTunnus lausunto-vastaus)
        ; external-id-statement (get-in lausunto-vastaus [:Lausunto :AsianTunnus])
        text (get-in lausunto-vastaus [:Lausunto :LausuntoTeksti])
        giver (get-in lausunto-vastaus [:Lausunto :Lausunnonantaja])
        statement-required-data (get-required-data! lausunto-vastaus)
        statement-data (-> statement-required-data
                           (update :given cr/to-timestamp)
                           (util/assoc-when :text text :giver giver))
        attachments (-> (:Liitteet lausunto-vastaus)
                        (util/ensure-sequential :Liite)
                        :Liite)
        application (domain/get-application-no-access-checking
                      (:application-id statement-data))]
    (logging/with-logging-context
      {:applicationId (:id application) :userId ftp-user}
      (when-let [statement (statement/save-ah-statement-response
                             application
                             ftp-user
                             statement-data
                             created)]
        (infof "ah-statement-response successfully updated statement %s, now saving %d attachments"
               (:id statement)
               (count attachments))
        (doseq [attachment attachments]
          (ah-att/insert-attachment!
            unzipped-path
            application
            attachment
            {:type-group "muut" :type-id "muu"}
            {:type "statement" :id (:id statement)}
            "Lausunnon liite (ELY-keskus)"
            created
            ely-user
            {:read-only true}))
        (child-to-attachment/create-attachment-from-children
          ely-user
          (domain/get-application-no-access-checking (:id application))
          :statements
          (:id statement)
          "fi"))
      (ok))))
