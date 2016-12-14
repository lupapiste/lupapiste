(ns lupapalvelu.attachment.bind-attachments-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.attachment.bind :as bind]
            [sade.schema-generators :as ssg]))

(apply-remote-minimal)

(facts "application bind"
  (let [application-id (:id (create-and-submit-application pena :propertyId sipoo-property-id))
        resp1 (upload-file pena "dev-resources/test-attachment.txt")
        file-id-1 (get-in resp1 [:files 0 :fileId])
        resp2 (upload-file pena "dev-resources/invalid-pdfa.pdf")
        file-id-2 (get-in resp2 [:files 0 :fileId])
        {job :job :as resp} (command
                              pena
                              :bind-attachments
                              :id application-id
                              :filedatas [(-> (ssg/generate bind/BindableFile)
                                              (assoc :fileId file-id-1))
                                          (-> (ssg/generate bind/BindableFile)
                                              (assoc :fileId file-id-2))])]
    resp => ok?
    (fact "Job id is returned" (:id job) => truthy)
    (when-not (= "done" (:status job)) (poll-job sonja :bind-attachments-job (:id job) (:version job) 25))))

#_(facts "inforequest bind")
