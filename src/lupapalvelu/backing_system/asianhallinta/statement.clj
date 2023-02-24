(ns lupapalvelu.backing-system.asianhallinta.statement
  (:require [lupapalvelu.backing-system.asianhallinta.attachment :as ah-att]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.statement :as statement]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [taoensso.timbre :refer [infof]])
  (:import (com.mongodb WriteConcern)))

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
        message-id (imessages/create-id)
        lausunto-vastaus (:LausuntoVastaus xml-edn)
        ; external-id (:AsianTunnus lausunto-vastaus)
        ; external-id-statement (get-in lausunto-vastaus [:Lausunto :AsianTunnus])
        text (get-in lausunto-vastaus [:Lausunto :LausuntoTeksti])
        giver (get-in lausunto-vastaus [:Lausunto :Lausunnonantaja])
        external-id (get-in lausunto-vastaus [:Lausunto :AsianTunnus])
        statement-required-data (get-required-data! lausunto-vastaus)
        statement-data (-> statement-required-data
                           (update :status ss/lower-case)
                           (update :given date/timestamp)
                           (util/assoc-when :text text
                                            :giver giver
                                            :externalId (when-not (ss/blank? external-id) external-id)))
        attachments (-> (:Liitteet lausunto-vastaus)
                        (util/ensure-sequential :Liite)
                        :Liite)
        application (domain/get-application-no-access-checking
                      (:application-id statement-data))]
    (logging/with-logging-context
      {:applicationId (:id application) :userId ftp-user}
      (imessages/save {:id message-id :direction "in" :messageType "ah-statement-response" :partner "ely"
                       :transferType "sftp" :format "xml" :created (now)
                       :status "processing" :external-reference (or external-id "")
                       :application (select-keys application [:id :organization])
                       :target {:type "statement" :id (:id statement-data)} :action "ah-batchrun"
                       :data xml-edn})
      (if-let [statement (statement/save-ah-statement-response
                           application
                           ftp-user
                           statement-data
                           created)]
        (do
          (infof "ah-statement-response successfully updated statement %s, now saving %d attachments"
                 (:id statement)
                 (count attachments))
          (doseq [attachment attachments]
            (ah-att/insert-attachment!
              unzipped-path
              application
              attachment
              {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"}
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
            "fi")
          (imessages/update-message message-id
                                    {$set {:status "processed" :attachmentsCount (count attachments)}}
                                    WriteConcern/UNACKNOWLEDGED)
          (ok))
        (error-and-fail!
          (format "No statement found for ah statement response, statement id: %s" (:id statement-data))
          :error.integration.asianhallinta.statement-not-found)))))
