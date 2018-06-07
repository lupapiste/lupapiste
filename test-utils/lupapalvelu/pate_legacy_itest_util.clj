(ns lupapalvelu.pate-legacy-itest-util
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]))

(defn edit-legacy-verdict [app-id verdict-id path value]
  (let [result (command sonja :edit-pate-verdict :id app-id
                        :verdict-id verdict-id
                        :path (flatten [path])
                        :value value)]
    (fact {:midje/description (format "Edit verdict: %s -> %s" path value)}
      result => no-errors?)
    result))

(defn fill-verdict [app-id verdict-id & kvs]
  (doseq [[k v] (apply hash-map kvs)]
    (edit-legacy-verdict app-id verdict-id k v)))

(defn open-verdict [app-id verdict-id]
  (query sonja :pate-verdict :id app-id :verdict-id verdict-id))

(defn add-review [app-id verdict-id review-name review-type]
  (facts {:midje/description (format "Add review: %s (%s)"
                                     review-name (name review-type))}
    (let [review-id (-> (edit-legacy-verdict app-id verdict-id
                                             :add-review true)
                        :changes flatten second)]
      (fact "Add review name"
        (edit-legacy-verdict app-id verdict-id
                             [:reviews review-id :name] review-name)
        => (contains {:filled false}))
      (fact "Add review type"
        (edit-legacy-verdict app-id verdict-id
                             [:reviews review-id :type] review-type)
        => (contains {:filled true})))))
