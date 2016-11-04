(ns lupapalvelu.assignment-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]
            [lupapalvelu.assignment :refer :all]
            [lupapalvelu.document.data-schema :as dds]
            [sade.schema-generators :as ssg]
            [sade.util :as util]
            ;; Ensure assignment targets are registered
            [lupapalvelu.document.document]))

(facts assignment-targets
  (fact "Hakija-r assignment is valid target"
    (let [target-groups (assignment-targets {:documents [(ssg/generate (dds/doc-data-schema "hakija-r"))]})]
      (sc/check [TargetGroup] target-groups) => nil))

  (fact "Maksaja assignment is valid target"
    (let [target-groups (assignment-targets {:documents [(ssg/generate (dds/doc-data-schema "maksaja"))]})]
      (sc/check [TargetGroup] target-groups) => nil))

  (fact "Paasuunnittelija assignment is valid target"
    (let [target-groups (assignment-targets {:documents [(ssg/generate (dds/doc-data-schema "paasuunnittelija"))]})]
      (sc/check [TargetGroup] target-groups) => nil))

  (fact "Tyonjohtaja assignment is valid target"
    (let [target-groups (assignment-targets {:documents [(ssg/generate (dds/doc-data-schema "tyonjohtaja-v2"))]})]
      (sc/check [TargetGroup] target-groups) => nil)))
