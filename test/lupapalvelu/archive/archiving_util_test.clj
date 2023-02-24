(ns lupapalvelu.archive.archiving-util-test
  (:require [lupapalvelu.archive.archiving-util :as archiving-util]
            [monger.operators :refer :all]
            [midje.sweet :refer :all]))

(defn check-mark [archived ts ks set-map]
  (fact {:midje/description (str ts " " ks " " set-map)}
    (archiving-util/mark-application-archived {:archived archived} ts ks) => nil
    (provided (lupapalvelu.action/update-application
                anything
                {$set set-map}) => nil)))

(facts mark-application-archived
  (let [key-fail?    (throws #"No archived timestamp keys")
        schema-fail? (throws #"does not match schema")]
    (facts "failure"
      (archiving-util/mark-application-archived {} 12345) => key-fail?
      (archiving-util/mark-application-archived {} 12345 []) => key-fail?
      (archiving-util/mark-application-archived {} 12345 [[]]) => key-fail?
      (archiving-util/mark-application-archived {} 12345 nil) => schema-fail?
      (archiving-util/mark-application-archived {} 12345 [nil]) => schema-fail?
      (archiving-util/mark-application-archived {} 12345 :bad) => schema-fail?
      (archiving-util/mark-application-archived {} 12345 [:bad]) => schema-fail?
      (archiving-util/mark-application-archived {} 12345 :bad :application)
      => schema-fail?
      (archiving-util/mark-application-archived {} 12345 [:completed :bad])
      => schema-fail?
      (archiving-util/mark-application-archived {} 12345 :completed nil)
      => schema-fail?
      (archiving-util/mark-application-archived {} "bad" :completed)
      => schema-fail?
      (archiving-util/mark-application-archived {} 123.45 :completed)
      => schema-fail?))

  (facts "success"
    (check-mark nil 12345 :initial {:archived.initial 12345})
    (check-mark nil nil :application {:archived.application nil
                                      :archived.initial     nil})
    (check-mark {:initial nil} 12345 :application
                {:archived.application 12345
                 :archived.initial     12345})
    (check-mark {:initial 12345} 34567 :application
                {:archived.application 34567})
    (check-mark {} 987 [:application :completed]
                {:archived.application 987
                 :archived.completed   987
                 :archived.initial     987})))
