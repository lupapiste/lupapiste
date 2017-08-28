(ns lupapalvelu.xml.asianhallinta.statement
  (:require [taoensso.timbre :refer [infof]]
            [sade.core :refer :all]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.xml :as xml]
            [clojure.java.io :as io]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.statement :as statement]))

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

(defn statement-response-handler [parsed-xml unzipped-path ftp-user system-user]
  (let [xml-edn   (xml/xml->edn parsed-xml)
        lausunto-vastaus (:LausuntoVastaus xml-edn)
        application-id (:HakemusTunnus lausunto-vastaus)
        ; external-id (:AsianTunnus lausunto-vastaus)
        ; external-id-statement (get-in lausunto-vastaus [:Lausunto :AsianTunnus])
        text (get-in lausunto-vastaus [:Lausunto :LausuntoTeksti])
        giver (get-in lausunto-vastaus [:Lausunto :Lausunnonantaja])
        statement-required-data (get-required-data! lausunto-vastaus)
        statement-data (-> statement-required-data
                           (update :given cr/to-timestamp)
                           (util/assoc-when :text text :giver giver))
        attachments (map xml/xml->edn (xml/select tes [:LausuntoVastaus :Liitteet :Liite]))]
    (logging/with-logging-context
      {:applicationId application-id :userId ftp-user}
      (when-let [statement (statement/save-ah-statement-response
                             application-id
                             ftp-user
                             statement-data)]
        (infof "ah-statement-response successfully updated statement, now saving %d attachments" (count attachments))
        ;TODO attachments
        )
      (ok))))
