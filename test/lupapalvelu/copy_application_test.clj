(ns lupapalvelu.copy-application-test
  (:require [clojure.data :refer [diff]]
            [clojure.walk :as walk]
            [lupapalvelu.application :as app]
            [lupapalvelu.copy-application :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]))

(defn dissoc-ids-comments-and-timestamps [application]
  (walk/postwalk #(if (map? %)
                    (dissoc % :id :comments :created :modified :ts)
                    %)
                 application))


(with-redefs [coord/convert (fn [_ _ _ coords] (str "converted " coords))]
  (let [source-user {:id        "source"
                     :firstName "Source"
                     :lastName  "User"
                     :role      :applicant}
        user {:id        "new"
              :firstName "New"
              :lastName  "User"
              :role      :applicant}
        source-created 12345
        created 23456
        municipality "753"
        organization {:id "753-R"}
        source-app (app/make-application "LP-123" "kerrostalo-rivitalo" 0 0
                                         "address" "01234567891234" municipality
                                         organization false false ["message1" "message2"] source-user
                                         source-created nil)]
    (facts new-application-copy
      (facts "No options specified"
        (let [new-app (new-application-copy source-app user created {})
              [new old _] (diff new-app source-app)]

          (fact "the application is copied almost verbatim"
            (let [[only-new only-old _] (diff (dissoc-ids-comments-and-timestamps new-app)
                                              (dissoc-ids-comments-and-timestamps source-app))]
              only-new => nil?
              only-old => nil?)
            (keys new) ; gives the same result as (keys old)
            => (just [:comments :created :documents #_:attachments :id :modified :history :primaryOperation]
                     :in-any-order))

          (fact "application is created and modified now"
            (:created new-app) => created
            (:modified new-app) => created)

          (fact "application has new history"
            (map :ts (:history new-app)) =>  (has every? #(= created %)))

          (fact "id references are updated"
            (->> new :documents (map (comp :id :op :schema-info)) (remove nil?))
            => (has every? #(= % (-> new :primaryOperation :id))))

          (fact "comments are not copied by default"
            (:comments new-app) => empty?)))

           (against-background
            (app/make-application-id anything) => "application-id-753"
            (org/get-organization (:id organization)) => organization))))
