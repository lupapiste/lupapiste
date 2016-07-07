(ns lupapalvelu.attachment-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [lupapalvelu.tiedonohjaus-api :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer [execute]]
            [sade.util :as util]))


(facts "about attachment api"

  (let [base-command {:application {:organization "753-R"
                                    :id           "ABC123"
                                    :state        "submitted"
                                    :permitType   "R"
                                    :attachments  [{:id "5234" :readOnly true}]}
                      :created     1000
                      :user        {:orgAuthz      {:753-R #{:authority}}
                                    :organizations ["753-R"]
                                    :role          :authority}
                      :data        {:id           "ABC123"
                                    :attachmentId "5234"}}]

    (fact "attachment data cannot be modified if the attachment is read-only"
      (let [command (util/deep-merge base-command {:action "set-attachment-meta"
                                                   :data   {:meta {"contents" "foobar"}}})]
        (execute command) => {:ok   false
                              :text "error.unauthorized"
                              :desc "Read-only attachments cannot be modified."}))

    (fact "new attachment version cannot be uploaded if the attachment is read-only"
      (let [command (util/deep-merge base-command {:action "upload-attachment"
                                                   ;attachmentType op filename tempfile size
                                                   :data   {:attachmentType {:type-group "paapiirustus"
                                                                             :type-id    "asemapiirros"}
                                                            :group          {:group-type "parties"}
                                                            :size           500
                                                            :filename       "foobar.pdf"
                                                            :tempfile       ""}})]
        (execute command) => {:ok   false
                              :text "error.unauthorized"
                              :desc "Read-only attachments cannot be modified."}))))
