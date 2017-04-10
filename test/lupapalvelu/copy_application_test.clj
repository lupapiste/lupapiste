(ns lupapalvelu.copy-application-test
  (:require [clojure.data :refer [diff]]
            [clojure.walk :as walk]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.copy-application :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]
            [sade.schema-generators :as ssg]))

(defn dissoc-ids-and-timestamps [application]
  (walk/postwalk #(if (map? %)
                    (dissoc % :id :created :modified :ts)
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
        organization {:id "753-R"
                      :operations-attachments
                      {:kerrostalo-rivitalo
                      [["paapiirustus"
                        "asemapiirros"]
                       ["paapiirustus"
                        "pohjapiirustus"]]}}
        raw-new-app (app/make-application "LP-123" "kerrostalo-rivitalo" 0 0
                                          "address" "01234567891234" municipality
                                          organization false false ["message1" "message2"] source-user
                                          source-created nil)
        source-app (-> raw-new-app
                       (assoc :attachments (attachment/make-attachments 999 :draft
                                                                        [{:type (ssg/generate attachment/Type)}
                                                                         {:type (ssg/generate attachment/Type)}]
                                                                        nil false true true)))]
    (facts new-application-copy
      (facts "No options specified"
        (let [new-app (new-application-copy source-app user organization created {})
              [new old _] (diff new-app source-app)]

          (fact "the application is copied almost verbatim"
            (let [[only-new only-old _] (diff (dissoc-ids-and-timestamps new-app)
                                              (dissoc-ids-and-timestamps source-app))]
              (keys only-new) => (just [:comments :attachments] :in-any-order)
              (keys only-old) => (just [:comments :attachments] :in-any-order))
            (keys new) ; gives the same result as (keys old)
            => (just [:attachments :comments :created :documents :id :modified :history :primaryOperation]
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
            (:comments new-app) => empty?)

          (fact "attachments are overridden with those of a normal new application"
            (= (dissoc-ids-and-timestamps (select-keys new-app [:attachments]))
               (dissoc-ids-and-timestamps (select-keys raw-new-app [:attachments])))  => true?)))

      (against-background
       (app/make-application-id anything) => "application-id-753"
       (org/get-organization (:id organization)) => organization))))
