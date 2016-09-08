(ns lupapalvelu.attachment-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.action :refer [execute] :as action]
            [lupapalvelu.attachment-api :as api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.tiedonohjaus-api :refer :all]
            [sade.env :as env]
            [sade.util :as util]))

(facts "attachment-not-readOnly"

  (let [base-command {:application {:organization "753-R"
                                    :id           "ABC123"
                                    :state        "submitted"
                                    :permitType   "R"
                                    :attachments  [{:id "5234" :readOnly true}]}
                      :created     1000
                      :user        {:orgAuthz      {:753-R #{:authority}}
                                    :role          :authority}
                      :data        {:id           "ABC123"
                                    :attachmentId "5234"}}]

    (fact "pre-check on its own"
      (api/attachment-not-readOnly base-command) => itest/fail?)

    (fact "attachment data cannot be modified if the attachment is read-only"
      (let [command (util/deep-merge base-command {:action "set-attachment-meta"
                                                   :data   {:meta {"contents" "foobar"}}})]
        (execute command) => {:ok   false
                              :text "error.unauthorized"
                              :desc "Attachment is read only."}))

    (fact "new attachment version cannot be uploaded if the attachment is read-only"
      (let [command (util/deep-merge base-command {:action "upload-attachment"
                                                   ;attachmentType op filename tempfile size
                                                   :data   {:attachmentType {:type-group "paapiirustus"
                                                                             :type-id    "asemapiirros"}
                                                            :group          {:groupType "parties"}
                                                            :size           500
                                                            :filename       "foobar.pdf"
                                                            :tempfile       ""}})]
        (execute command) => {:ok   false
                              :text "error.unauthorized"
                              :desc "Attachment is read only."}))

    (fact "forman application attachments are readonly after sent"
      (let [authority-tj-command (util/deep-merge base-command {:application {:attachments [{:id "5234" :readOnly false}]
                                                                              :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                                                              :state "submitted"}})
            applicant-tj-command (assoc-in authority-tj-command [:user :role] :applicant)]

        (fact "initially not read only"
          (api/attachment-not-readOnly applicant-tj-command) => nil)

        (fact "read only in sent state"
          (api/attachment-not-readOnly (assoc-in applicant-tj-command [:application :state] "sent")) => itest/fail?)

        (fact "read only in verdict given state"
          (api/attachment-not-readOnly (assoc-in applicant-tj-command [:application :state] "foremanVerdictGiven")) => itest/fail?)

        (fact "not read only in verdict given state for authority"
          (api/attachment-not-readOnly (assoc-in authority-tj-command [:application :state] "foremanVerdictGiven")) => nil)))))

(facts "readonly pre-checks are in place"
  (->> (action/get-actions)
       (filter (fn [[k v]] (and (= (:type v) :command) (-> v :user-roles :applicant) (-> v :categories :attachments))))
       (map (fn [[k v]] [k (remove nil? (map (comp #(re-matches #".*readOnly.*" %) str class) (:pre-checks v)))]))
       (filter (comp empty? second))
       (map first)
       set) => #{:create-ram-attachment ; does not alter the original
                 :rotate-pdf ; original is left in db, this is fine
                 :set-attachment-not-needed ; does not alter content or meta data
                 :sign-attachments ; allow signing attachments always
                 }
  )
