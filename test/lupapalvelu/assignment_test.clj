(ns lupapalvelu.assignment-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]
            [lupapalvelu.assignment :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.data-schema :as dds]
            [sade.schema-generators :as ssg]
            [sade.util :as util]
            ;; Ensure assignment targets are registered
            [lupapalvelu.document.document]
            ;; Ensure all document schemas are registered
            [lupapalvelu.document.poikkeamis-schemas]
            [lupapalvelu.document.vesihuolto-schemas]
            [lupapalvelu.document.waste-schemas]
            [lupapalvelu.document.yleiset-alueet-schemas]
            [lupapalvelu.document.ymparisto-schemas]))

(defn validate-target [doc-schema-name]
  (fact {:midje/description (str doc-schema-name " is valid assignment target")}
    (let [target-groups (assignment-targets {:documents [(ssg/generate (dds/doc-data-schema doc-schema-name))]})]
      (sc/check [TargetGroup] target-groups) => nil)))

(facts "Validate assignment targets to all documents"
  (doseq [doc-schema-name (->> (schemas/get-all-schemas) vals (apply merge) keys)]
    (validate-target doc-schema-name)))
