(ns lupapalvelu.file-storage-test
  (:require [lupapalvelu.storage.file-storage :refer [fix-file-links link-files-to-application
                                                      unlinked-file-exists? application-file-exists?]]
            [midje.sweet :refer :all]))

(defn make-version
  ([user-id file-id original-id onkalo-id]
   (cond-> {:user           {:id user-id}
           :fileId         file-id
            :originalFileId original-id}
     onkalo-id (assoc :onkaloFileId onkalo-id)))
  ([user-id file-id original-id]
   (make-version user-id file-id original-id nil))
  ([user-id file-id]
   (make-version user-id file-id file-id nil)))

(facts "Fix file links"
  (fact "No unlinked files"
    (fix-file-links {:id "app-id"
                     :attachments [{:versions []}
                                   {:versions [{}]}
                                   {:versions [(make-version "andy" "f1" "orig1")]}
                                   {:versions [(make-version "eduardo" "f3" "orig3" "o3")
                                               (make-version "fiona" nil "o4")
                                               (make-version "fiona" "f4" nil)]}]}) => nil
    (provided
      (application-file-exists? "app-id" "f1") => true :times 1
      (unlinked-file-exists? "andy" "f1") => false :times 0
      (application-file-exists? "app-id" "orig1") => false :times 1
      (unlinked-file-exists? "andy" "orig1") => false :times 1
      (application-file-exists? "app-id" "o3") => true :times 0
      (application-file-exists? "app-id" nil) => false :times 0
      (application-file-exists? "app-id" "o4") => true :times 1
      (application-file-exists? "app-id" "f4") => true :times 1
      (link-files-to-application anything anything anything) => nil :times 0))

  (fact "Some unlinked files"
    (fix-file-links {:id "app-id"
                     :attachments [{:versions []}
                                   {:versions [(make-version "andy" "f1" "orig1")]}
                                   {:versions [(make-version "brenda" "u1")
                                               (make-version "carl" "u2" "orig2")]}
                                   {:versions [(make-version "diana" "u3" "u4")]}]}) => nil
    (provided
      (application-file-exists? "app-id" "f1") => true :times 1
      (unlinked-file-exists? "andy" "f1") => false :times 0
      (application-file-exists? "app-id" "orig1") => false :times 1
      (unlinked-file-exists? "andy" "orig1") => false :times 1
      (application-file-exists? "app-id" "u1") => false :times 1
      (unlinked-file-exists? "brenda" "u1") => true :times 1
      (application-file-exists? "app-id" "u2") => false :times 1
      (unlinked-file-exists? "carl" "u2") => true :times 1
      (application-file-exists? "app-id" "orig2") => true :times 1
      (unlinked-file-exists? "carl" "orig2") => false :times 0
      (application-file-exists? "app-id" "u3") => false :times 1
      (unlinked-file-exists? "diana" "u3") => true :times 1
      (application-file-exists? "app-id" "u4") => false :times 1
      (unlinked-file-exists? "diana" "u4") => true :times 1
      (link-files-to-application "brenda" "app-id" ["u1"]) => nil :times 1
      (link-files-to-application "carl" "app-id" ["u2"]) => nil :times 1
      (link-files-to-application "diana" "app-id" ["u3" "u4"]) => nil :times 1)))
