(ns lupapalvelu.attachment-itest
  (:use [lupapalvelu.attachment]
        [lupapalvelu.itest-util]
        [midje.sweet]))

;;
;; Integration tests:
;;

(fact
  (let [resp (command pena :create-application :x 408048 :y 6693225 :street "s" :city "c" :zip "z" :schemas ["hakija"])
        application-id (:id resp)
        application (:application (query pena :application :id application-id))]
    (nil? (:attachments application)) => falsey
    (empty? (:attachments application)) => truthy
    (let [resp (command veikko :create-attachment
                 :id application-id
                 :attachmentType {:type-group "tg"
                                  :type-id "tid"})
          attachment-id (:attachmentId resp)
          application (:application (query pena :application :id application-id))]
      (nil? attachment-id) => falsey
      (count (:attachments application)) => 1
      (first (:attachments application)) => (contains
                                              {:type {:type-group "tg"
                                                      :type-id "tid"}
                                               :state "requires_user_action"
                                               :latestVersion {:version {:minor 0 :major 0}}
                                               :versions []}))))
