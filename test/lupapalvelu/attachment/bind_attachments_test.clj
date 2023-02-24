(ns lupapalvelu.attachment.bind-attachments-test
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :refer :all]
            [lupapalvelu.attachment.bind-attachments-api :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.operations :as op]
            [lupapalvelu.pate-itest-util :refer [err]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]))

(testable-privates lupapalvelu.attachment.bind-attachments-api validate-attachment-groups)

(facts validate-attachment-groups
  (fact "one file"
    (let [filedatas [{:group {:groupType "operation" :operations [{:id ..op-id.. :name ..op-name..}]}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => nil
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => nil)
    (provided (op/operation-id-exists? ..application.. ..op-id..) => true)
    (provided (sc/check [att/Operation] [{:id ..op-id.. :name ..op-name..}]) => nil))

  (fact "multiple files"
    (let [filedatas [{:group {:groupType "operation" :operations [{:id ..op-id.. :name ..op-name..}]}}
                     {:group {:groupType "parties"}}
                     {:group {:groupType "building-site" :operations nil}}
                     {:group nil}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => nil
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => nil)
    (provided (op/operation-id-exists? ..application.. ..op-id..) => true)
    (provided (sc/check [att/Operation] [{:id ..op-id.. :name ..op-name..}]) => nil))

  (fact "attachment op selection not allowed"
    (let [filedatas [{:group nil}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => nil)

  (fact "attachment op selection not allowed - trying to set"
    (let [filedatas [{:group {:groupType "building-site"}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => (partial expected-failure? :error.illegal-meta-type)
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => false))

  (fact "illegal group type"
    (let [filedatas [{:group {:groupType "invalid group type"}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => (partial expected-failure? :error.illegal-attachment-group-type))

  (fact "illegal operation"
    (let [filedatas [{:group {:groupType "parties"}}
                     {:group {:groupType "operation" :operations [{:id ..op-id.. :name ..op-name..} {}]}}]
          command  {:data {:filedatas filedatas} :application ..application..}]
      (validate-attachment-groups command)) => (partial expected-failure? :error.illegal-attachment-operation)
    (provided (op/get-primary-operation-metadata ..application.. :attachment-op-selector) => nil)
    (provided (sc/check [att/Operation] [{:id ..op-id.. :name ..op-name..} {}]) => :failure)))

(facts attachment-id-filedatas-pre-checks
  (let [locked     {:id     "att-locked"
                    :locked true}
        read-only  {:id       "att-read-only"
                    :readOnly true}
        not-needed {:id        "att-not-needed"
                    :notNeeded true}
        good       {:id               "att-good"
                    :applicationState "draft"}
        applicant  {:id   "user-id"
                    :role "applicant"}
        authority  {:id       "authority-id"
                    :orgAuthz {:123-R [:authority]}}
        cmd        (fn [user atts & filedatas]
                     {:application {:id           "app-id"
                                    :auth         [{:id   "user-id"
                                                    :role "writer"}]
                                    :attachments  atts
                                    :organization "123-R"}
                      :user        user
                      :data        {:id        "app-id"
                                    :filedatas filedatas}})
        tester     (comp attachment-id-filedatas-pre-checks cmd)]
    (fact "No matching filedatas"
      (attachment-id-filedatas-pre-checks {}) => nil
      (attachment-id-filedatas-pre-checks {:data {:filedatas [{:group  "hello"
                                                               :fileId "fid1"}]}})
      => nil)
    (fact "Good"
      (tester applicant [good] {:attachmentId "att-good"}) => nil)

    (fact "Misssing"
      (tester applicant [locked read-only] {:attachmentId "att-good"})
      => (err :error.attachment.id))

    (fact "Locked"
      (tester applicant [good locked]
              {:attachmentId "att-good"}
              {:attachmentId "att-locked"})
      => (err :error.attachment-is-locked))

    (fact "Read-only"
      (tester applicant [good read-only]
              {:attachmentId "att-good"}
              {:attachmentId "att-read-only"})
      => {:desc "Attachment is read only."
          :ok   false
          :text "error.unauthorized"})

    (fact "Not needed"
      (tester applicant [good not-needed]
              {:attachmentId "att-good"}
              {:attachmentId "att-not-needed"})
      => (err :error.attachment.not-needed))

    (facts "Attachment state is draft but application is verdictGiven."
      (let [command (assoc-in (cmd authority [good] {:attachmentId "att-good"})
                              [:application :state] "verdictGiven")]
        (fact "Authority can edit"
          (attachment-id-filedatas-pre-checks command) => nil)
        (fact "Applicant cannot edit"
          (attachment-id-filedatas-pre-checks (assoc command :user applicant))
          => (err :error.pre-verdict-attachment))))

    (fact "Included in a published bulletin. NOTE: currently only YMP bulletins supported!"
      (let [command (assoc (cmd authority [good] {:attachmentId "att-good"})
                           :application-bulletins [{:id "app-id"}])]
        (attachment-id-filedatas-pre-checks command)
        => (err :error.attachment-included-in-published-bulletin)
        (provided
          (att/included-in-published-bulletin? anything anything "att-good") => true)))

    (fact "Shortcuts on the first error"
      (tester applicant [good locked not-needed]
              {:attachmentId "att-good"}
              {:attachmentId "att-locked"}
              {:attachmentId "att-not-needed"})
      => (err :error.attachment-is-locked)
      (tester applicant [good locked not-needed]
              {:attachmentId "att-good"}
              {:attachmentId "att-not-needed"}
              {:attachmentId "att-locked"})
      => (err :error.attachment.not-needed))))

(testable-privates lupapalvelu.attachment.bind
                   uniq-filenames)

(facts uniq-filenames
  (uniq-filenames []) => #{}
  (uniq-filenames [{:filename "f1"}]) => #{"f1"}
  (uniq-filenames [{:filename "f1"}
                   {:filename "f2"}
                   {:filename "f1"}
                   {:filename "f3"}]) => #{"f2" "f3"}
  (uniq-filenames [{:filename "f1"}
                   {:filename "f2"}
                   {:filename "f1"}
                   {:filename "f2"}]) => #{})

(defn make-att [id filename]
  {:id id :latestVersion {:filename filename}})

(facts resolve-attachment-update-candidates
  (let [application {:attachments [(make-att "a1" "name1")
                                   (make-att "a2" "name2")
                                   (make-att "a3" "name3")
                                   (make-att "a4" "name3") ;; Duplicate
                                   (make-att "a5" "name5")
                                   (make-att "a6" "name6")
                                   ;; The last two match normalization
                                   (make-att "a7" "hämy")
                                   (make-att "a8" (str "mo" (char 776) "rko" (char 776)))
                                   ]}]
    (resolve-attachment-update-candidates application
                                          [{:fileId "f1" :filename "name1"}])
    => [{:attachmentId "a1" :fileId "f1"}]
    (resolve-attachment-update-candidates application
                                          [{:fileId "f1" :filename "name1"}
                                           {:fileId "f2" :filename "name2"}
                                           {:fileId "f3" :filename "name3"}
                                           {:fileId "f5" :filename "name5"}
                                           {:fileId "f55" :filename "name5"}
                                           {:fileId "f6" :filename "missing"}
                                           {:fileId "f7" :filename (str "ha" (char 776) "my")}
                                           {:fileId "f8" :filename "mörkö"}])
    => (just {:attachmentId "a1" :fileId "f1"}
             {:attachmentId "a2" :fileId "f2"}
             {:attachmentId "a7" :fileId "f7"}
             {:attachmentId "a8" :fileId "f8"}
             :in-any-order)
    (resolve-attachment-update-candidates application []) => []))
