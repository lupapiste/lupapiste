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

(testable-privates lupapalvelu.assignment enrich-targets enrich-assignment-target get-targets-for-applications)

(defn validate-target [doc-schema-name]
  (fact {:midje/description (str doc-schema-name " is valid assignment target")}
    (let [target-groups (assignment-targets {:documents [(ssg/generate (dds/doc-data-schema doc-schema-name))]})]
      (sc/check [TargetGroup] target-groups) => nil)))

(facts "Validate assignment targets to all documents"
  (doseq [doc-schema-name (->> (schemas/get-all-schemas) vals (apply merge) keys)]
    (validate-target doc-schema-name)))


(facts get-targets-for-applications
  (fact "one application - one target"
    (get-targets-for-applications [..app-id1..]) => {..app-id1.. {..group1.. ..targets1..}}

    (provided (lupapalvelu.mongo/select :applications {:_id {"$in" #{..app-id1..}}} anything) => [{:id ..app-id1.. :documents [..doc1..]}])
    (provided (assignment-targets {:id ..app-id1.. :documents [..doc1..]}) => [[..group1.. ..targets1..]]))


  (fact "many applications"
    (get-targets-for-applications ..ids..) => {..app-id1.. {..group11.. ..targets11..}
                                               ..app-id2.. {}
                                               ..app-id3.. {..group31.. ..targets31.. ..group32.. ..targets32..}}

    (provided (lupapalvelu.mongo/select :applications anything anything)
              => [{:id ..app-id1.. :documents [..doc11.. ..doc12.. ..doc13..]}
                  {:id ..app-id2.. :documents []}
                  {:id ..app-id3.. :documents [..doc31.. ..doc32.. ..doc33..]}])
    (provided (assignment-targets {:id ..app-id1.. :documents [..doc11.. ..doc12.. ..doc13..]})
              => [[..group11.. ..targets11..]])
    (provided (assignment-targets {:id ..app-id2.. :documents []})
              => [])
    (provided (assignment-targets {:id ..app-id3.. :documents [..doc31.. ..doc32.. ..doc33..]})
              => [[..group31.. ..targets31..] [..group32.. ..targets32..]])))

(facts enrich-assignment-target
  (fact "one target"
    (enrich-assignment-target {:group1 [{:id ..target-id.. :type ..type..}]} {:target {:id ..target-id.. :group "group1"}})
    => {:target {:id ..target-id.. :group "group1" :type ..type..}})

  (fact "many targets"
    (enrich-assignment-target {:group1 [{:id ..target-id1.. :type ..type1..} {:id ..target-id2.. :type ..type2..}]
                               :group2 [{:id ..target-id3.. :type ..type3..}]}
                              {:target {:id ..target-id2.. :group "group1"}})
    => {:target {:id ..target-id2.. :group "group1" :type ..type2..}})

  (fact "target not found - assignment is returned unenriched"
    (enrich-assignment-target {:group1 [{:id ..target-id1.. :type ..type..} {:id ..target-id2.. :type ..type..}]
                               :group2 [{:id ..target-id2.. :type ..type..}]}
                              {:target {:id ..not-found-target-id.. :group "group1"}})
    => {:target {:id ..not-found-target-id.. :group "group1"}}))

(facts enrich-targets
  (fact "one assignment"
    (enrich-targets [{:application {:id ..app-id1..} :target {:id ..target-id..}}])
    => [{:application {:id ..app-id1..} :target {:id ..target-id.. :type ..type..}}]

    (provided (#'lupapalvelu.assignment/get-targets-for-applications [..app-id1..])
              => {..app-id1.. {..group11.. ..targets11..}})
    (provided (#'lupapalvelu.assignment/enrich-assignment-target {..group11.. ..targets11..}
                                                                 {:application {:id ..app-id1..} :target {:id ..target-id..}})
              => {:application {:id ..app-id1..} :target {:id ..target-id.. :type ..type..}}))

  (fact "many assignments"
    (enrich-targets [{:application {:id ..app-id1..} :target {:id ..target-id12.. :group "group11"}}
                     {:application {:id ..app-id2..} :target {:id ..target-id20.. :group "group20"}}
                     {:application {:id ..app-id3..} :target {:id ..target-id32.. :group "group32"}}])
    => [{:application {:id ..app-id1..} :target {:id ..target-id12.. :group "group11" :type ..type12..}}
        {:application {:id ..app-id2..} :target {:id ..target-id20.. :group "group20"}}
        {:application {:id ..app-id3..} :target {:id ..target-id32.. :group "group32" :type ..type32..}}]

    (provided (#'lupapalvelu.assignment/get-targets-for-applications [..app-id1.. ..app-id2.. ..app-id3..])
              => {..app-id1.. {:group11 [{:id ..target-id11.. :type ..type11..} {:id ..target-id12.. :type ..type12..}]}
                  ..app-id2.. {}
                  ..app-id3.. {:group31 [{:id ..target-id31.. :type ..type31..} {:id ..target-id32.. :type ..type32..}]
                               :group32 [{:id ..target-id32.. :type ..type32..}]}})))
