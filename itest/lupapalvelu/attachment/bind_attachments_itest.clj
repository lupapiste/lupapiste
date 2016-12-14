(ns lupapalvelu.attachment.bind-attachments-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "placholder bind"
  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
        operation      (:primaryOperation application)
        application-id (:id application)
        attachments    (:attachments application)
        resp1 (upload-file pena "dev-resources/test-attachment.txt")
        file-id-1 (get-in resp1 [:files 0 :fileId])
        resp2 (upload-file pena "dev-resources/invalid-pdfa.pdf")
        file-id-2 (get-in resp2 [:files 0 :fileId])]
    (fact "attachment types - for clarity"
      (map :type attachments) => (just [{:type-group "paapiirustus", :type-id "asemapiirros"}
                                        {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                                        {:type-group "hakija", :type-id "valtakirja"}
                                        {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat", :type-id "vaestonsuojasuunnitelma"}]))

    (let [{job :job :as resp} (command
                                pena
                                :bind-attachments
                                :id application-id
                                :filedatas [{:fileId file-id-1 :type (:type (first attachments))
                                             :group {:groupType "operation" :id (:id operation) :name (:name operation)}
                                             :contents "eka"}
                                            {:fileId file-id-2 :type (:type (second attachments))
                                             :group {:groupType nil}
                                             :contents "toka"}])]
      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (when-not (= "done" (:status job))
        (poll-job sonja :bind-attachments-job (:id job) (:version job) 25) => truthy)

      (facts "attachments status"
        (let [attachments (:attachments (query-application pena application-id))
              att1 (first attachments)
              att2 (second attachments)]
          (fact "now new attachments created, as placeholders were empty"
            (count attachments) => 4)
          (fact "versions exists"
            (count (:versions att1)) => 1
            (count (:versions att2)) => 1)
          (fact "contents are set"
            (:contents att1) => "eka"
            (:contents att2) => "toka")
          (when libre/enabled?
            (fact "txt converted"
              (:autoConversion (:latestVersion att1)) => true
              (:fileId (:latestVersion att1))
              (:originalFileId (:latestVersion att1)) => file-id-1))
          (fact "groups are set"
            (:op att1) => {:id (:id operation) :name (:name operation)}
            (:groupType att1) => "operation"))))))

(facts "new attachment bind"
  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
        application-id (:id application)
        attachments    (:attachments application)
        resp1 (upload-file pena "dev-resources/test-attachment.txt")
        file-id-1 (get-in resp1 [:files 0 :fileId])
        resp2 (upload-file pena "dev-resources/invalid-pdfa.pdf")
        file-id-2 (get-in resp2 [:files 0 :fileId])]
    (fact "attachment types - for clarity"
      (map :type attachments) => (just [{:type-group "paapiirustus", :type-id "asemapiirros"}
                                        {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                                        {:type-group "hakija", :type-id "valtakirja"}
                                        {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat", :type-id "vaestonsuojasuunnitelma"}]))

    (let [{job :job :as resp} (command
                                pena
                                :bind-attachments
                                :id application-id
                                :filedatas [{:fileId file-id-1 :type (:type (get attachments 2)) ; hakija
                                             :group {:groupType "parties"}
                                             :contents "hakija"}
                                            {:fileId file-id-2 :type {:type-group "osapuolet" :type-id "tutkintotodistus"}
                                             :group {:groupType nil}
                                             :contents "todistus"}])]
      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (when-not (= "done" (:status job))
        (poll-job sonja :bind-attachments-job (:id job) (:version job) 25) => truthy)

      (facts "attachments status"
        (let [attachments (:attachments (query-application pena application-id))
              hakija      (util/find-first #(= "hakija" (-> % :type :type-group)) attachments)
              todistus    (util/find-first #(= "osapuolet" (-> % :type :type-group)) attachments)]
          (count (:versions hakija)) => 1
          (count (:versions todistus)) => 1
          (fact "one new attachment was created"
            (count attachments) => 5)
          (fact "contents are set"
            (:contents hakija) => "hakija"
            (:contents todistus) => "todistus")
          (fact "groups are set"
            (:groupType hakija) => "parties"
            (:groupType todistus) => nil))))))

#_(facts "inforequest bind")
