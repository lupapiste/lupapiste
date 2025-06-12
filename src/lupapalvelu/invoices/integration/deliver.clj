(ns lupapalvelu.invoices.integration.deliver
  (:require [lupapalvelu.invoices.transfer-batch :as tb]
            [lupapalvelu.sftp.client :as sftp-client]
            [lupapalvelu.sftp.core :as sftp]
            [sade.core :refer [fail!]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.net URI]))

(set! *warn-on-reflection* true)

;; sending via HTTP

(defn- send-file-to-invoicing-system-via-http! [{:keys [integration-url credentials]} content content-type]
  (let [options (cond-> {:body             content
                         :throw-exceptions false
                         :throw-fail!      true
                         :content-type     content-type
                         :socket-timeout   10000
                         :conn-timeout     10000}
                        credentials (assoc :basic-auth [(:username credentials) (:password credentials)]))
        resp    (http/post integration-url options)]
    (:status resp)))

;; sending via SFTP

(defn- sftp-connect-info [{:keys [integration-url credentials ssh-authentication?]} filename]
  (let [{:keys [host path port]} (bean (URI. integration-url))]
    {:host                  host
     :remote-sftp-file-path (ss/join-non-blanks "/" (concat (ss/split path #"/") [filename]))
     :auth                  (util/assoc-when (if ssh-authentication?
                                               {:username         (:username credentials)
                                                :private-key-path (env/value :invoices :sftp :private-key-path)}
                                               (select-keys credentials [:username :password]))
                                             :port (when (pos? port) port))}))

(defn- send-file-to-invoicing-system-via-sftp! [invoicing-config filename content]
  (let [{:keys [host remote-sftp-file-path auth]} (sftp-connect-info invoicing-config filename)]
    (sftp-client/upload-file! host content remote-sftp-file-path auth)))

(defn- do-deliver!
  [cfg organization-id delivery-type content fn-fn content-type]
  (case delivery-type
    :http                      (send-file-to-invoicing-system-via-http! cfg content content-type)
    :local-sftp                (sftp/write-invoicing-file organization-id (fn-fn) content)
    (:sftp-ssh :sftp-password) (send-file-to-invoicing-system-via-sftp! cfg (fn-fn) content)))

(defn deliver-transfer-batch-to-invoicing-system!
  [command {:keys [delivery-type] :as cfg} organization-id batch invoices
   {:keys [content filename integration-message-action content-type]}]
  (if delivery-type
    (let [action  (or integration-message-action
                      (str "send-to-invoicing-via-" (name delivery-type)))
          fn-fn   (constantly filename)
          message (tb/save-integration-message command batch content action)]

      (do-deliver! cfg organization-id delivery-type content fn-fn content-type)
      (tb/mark-sent command (:id batch) message (map :id invoices)))
    (fail! :error.invalid-configuration)))
