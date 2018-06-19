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
