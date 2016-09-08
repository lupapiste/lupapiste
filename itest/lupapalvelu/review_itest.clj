(ns lupapalvelu.review-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(defn create-review [application-id taskname]
  (let [{task-id :taskId} (command sonja :create-task :id application-id
                                   :taskName taskname :schemaName "task-katselmus"
                                   :taskSubtype "muu tarkastus")]
    (fact "Upload review attachment"
          (upload-attachment-to-target sonja application-id nil true task-id "task") => truthy)
    task-id))

(defn task-deleted-check
  ([application-id task-id check]
   (let [{:keys [tasks attachments]} (query-application sonja application-id)]
     (fact "Task existence check"
           (some #(= task-id (:id %)) tasks) => check)
     (fact "Task attachment existence check"
           (some #(= task-id (get-in % [:target :id])) attachments) => check)))
  ([application-id task-id]
   (task-deleted-check application-id task-id falsey)))

(let [{application-id :id} (create-and-submit-application pena :municilapity sonja-muni)]
  (fact "Fetch verdict"
        (command sonja :check-for-verdict :id application-id) => ok?)
  (facts "Task deletion"
         (let [task-id (create-review application-id "to be deleted")]
           (fact "Delete task"
                 (command sonja :delete-task :id application-id :taskId task-id) => ok?)
           (task-deleted-check application-id task-id)))

  (facts "Verdict deletion, sent and other tasks."
         (let [{:keys [tasks]} (query-application sonja application-id)
               root (some #(and (= "Aloituskokous" (:taskname %)) %)
                          tasks)
               root-id (:id root)
               _ (command sonja :update-task :id application-id :doc root-id
                          :updates [["katselmus.tila" "osittainen"]
                                    ["katselmus.pitoPvm" "22.4.2016"]
                                    ["katselmus.pitaja" "Ron Reviewer"]])
               _ (upload-attachment-to-target sonja application-id nil true root-id "task")
               _ (command sonja :review-done :id application-id :taskId root-id :lang "fi")
               {:keys [tasks]} (query-application sonja application-id)
               task-id (some #(and (= "Aloituskokous" (:taskname %))
                                   (not= root-id (:id %))
                                   (:id %)) tasks)
               verdict-id (-> root :source :id)]

           (fact "Delete verdict"
                 (command sonja :delete-verdict :id application-id :verdictId (-> root :source :id)) => ok?)

           (fact "Sent review still exists"
                 (task-deleted-check application-id root-id truthy))
           (fact "New review deleted"
             (task-deleted-check application-id task-id)))))
