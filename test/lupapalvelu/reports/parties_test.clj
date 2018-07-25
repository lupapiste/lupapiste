(ns lupapalvelu.reports.parties-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.reports.parties :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]
            [sade.schema-generators :as ssg]
            [sade.util :as util]
            [sade.util :as util]))

(fact "designer docs data pick"
  (let [paa-doc {:schema-info {:name "paasuunnittelija"}
                 :data (tools/create-document-data
                         (schemas/get-schema 1 "paasuunnittelija")
                         (partial tools/dummy-values (ssg/generate sade.schemas/ObjectIdStr)))}
        suunnittelija-doc (-> {:schema-info {:name "suunnittelija"}
                               :data (tools/create-document-data
                                       (schemas/get-schema 1 "suunnittelija")
                                       (partial tools/dummy-values (ssg/generate sade.schemas/ObjectIdStr)))}
                              (assoc-in [:data :kuntaRoolikoodi :value] "kantavien rakenteiden suunnittelija"))]
    (doseq [doc [paa-doc suunnittelija-doc]
            :let [result (pick-designer-data :fi doc)]]
      (fact {:midje/description (str (get-in doc [:schema-info :name]) " keys")}
        (keys result) => (just designers-fields :in-any-order))
      (fact {:midje/description (str (get-in doc [:schema-info :name]) " no blank values")}
        (vals result) => (partial not-any? util/empty-or-nil?)))))

(fact "foreman docs"
  (let [tj-doc {:schema-info {:name "tyonjohtaja-v2"}
                :data (tools/create-document-data
                        (schemas/get-schema 1 "tyonjohtaja-v2")
                        (partial tools/dummy-values (ssg/generate sade.schemas/ObjectIdStr)))}
        result (pick-foreman-data :fi tj-doc)]
    (fact "keys ok" (keys result) => (just foremen-fields :in-any-order))
    (fact "vals not blank" (vals result) => (partial not-any? util/empty-or-nil?))))

(fact "applicant docs"
  (let [applicant-doc {:schema-info {:name "hakija-r"}
                       :data (-> (tools/create-document-data
                                   (schemas/get-schema 1 "hakija-r")
                                   (partial tools/dummy-values (ssg/generate sade.schemas/ObjectIdStr)))
                                 (assoc-in [:_selected :value] "henkilo"))}
        company-doc {:schema-info {:name "hakija-r"}
                     :data (-> (tools/create-document-data
                                 (schemas/get-schema 1 "hakija-r")
                                 (partial tools/dummy-values (ssg/generate sade.schemas/ObjectIdStr)))
                               (assoc-in [:_selected :value] "yritys"))}
        applicant-result (pick-person-data applicant-doc)
        company-result   (pick-company-data company-doc)]
    (fact "persons"
      (fact "keys ok" (keys applicant-result) => (just private-applicants-fields :in-any-order))
      (fact "vals not blank" (vals applicant-result) => (partial not-any? util/empty-or-nil?)))
    (fact "companies"
      (fact "keys ok" (keys company-result) => (just company-applicants-fields :in-any-order))
      (fact "vals not blank" (vals company-result) => (partial not-any? util/empty-or-nil?)))))
