(ns lupapalvelu.pate-legacy-itest-util
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]))


(defn add-legacy-review [apikey app-id verdict-id review-name review-type]
  (facts {:midje/description (format "Add review: %s (%s)"
                                     review-name (name review-type))}
    (let [review-id (-> (edit-verdict apikey app-id verdict-id
                                      :add-review true)
                        :changes flatten second)]
      (fact "Add review name"
        (edit-verdict apikey app-id verdict-id
                      [:reviews review-id :name] review-name)
        => (contains {:filled false}))
      (fact "Add review type"
        (edit-verdict apikey app-id verdict-id
                      [:reviews review-id :type] review-type)
        => (contains {:filled true})))))

(defn give-legacy-r-verdict
  [apikey app-id]
  (let [{:keys [verdict-id]} (command apikey :new-legacy-verdict-draft
                                      :id app-id)]
    (facts "Create, fill and publish R legacy verdict"
      (fill-verdict apikey app-id verdict-id
                    :kuntalupatunnus "888-10-12"
                    :verdict-code "1" ;; Granted
                    :verdict-text "Lorem ipsum"
                    :handler "Decider"
                    :anto (timestamp "21.5.2018")
                    :lainvoimainen (timestamp "30.5.2018"))
      (command apikey :publish-legacy-verdict
               :id app-id
               :verdict-id verdict-id) => ok?
      (verdict-pdf-queue-test apikey
                              {:app-id     app-id
                               :verdict-id verdict-id}))
    verdict-id))
