(ns lupapalvelu.pate-legacy-itest-util
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.date :refer [timestamp]]
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

(defn give-generic-legacy-verdict
  "Gives legacy verdict, polls for verdict PDF in application attachments.
  Server should create PDF asyncronously using queue."
  [apikey app-id {:keys [fields attachment]}]
  (let [{:keys [verdict-id]} (command apikey :new-legacy-verdict-draft
                                      :id app-id)]
    (facts "Create, fill and publish generic legacy verdict"
      (->> fields
           (into [])
           (apply concat)
           (apply (partial fill-verdict apikey app-id verdict-id)))
      (command apikey :publish-legacy-verdict
               :id app-id
               :verdict-id verdict-id) => ok?
      (verdict-pdf-queue-test apikey
                              (merge {:app-id     app-id
                                      :verdict-id verdict-id}
                                     attachment)))
    verdict-id))

(defn give-legacy-verdict
  [apikey app-id & kvs]
  (let [{:keys [verdict-id]} (command apikey :new-legacy-verdict-draft
                                      :id app-id)]
    (facts "Create, fill and publish legacy verdict"
      (->> (apply hash-map kvs)
           (merge {:kuntalupatunnus "888-10-12"
                   :verdict-code    "1" ;; Granted
                   :verdict-text    "Lorem ipsum"
                   :handler         "Decider"
                   :anto            (timestamp "21.5.2018")
                   :lainvoimainen   (timestamp "30.5.2018")})
           (into [])
           (apply concat)
           (apply (partial fill-verdict apikey app-id verdict-id)))
      (command apikey :publish-legacy-verdict
               :id app-id
               :verdict-id verdict-id) => ok?
      (verdict-pdf-queue-test apikey
                              {:app-id     app-id
                               :verdict-id verdict-id}))
    verdict-id))

(defn give-legacy-contract
  [apikey app-id & kvs]
  (let [{:keys [verdict-id]} (command apikey :new-legacy-verdict-draft
                                      :id app-id)]
    (facts "Create, fill and publish legacy contract"
      (->> (apply hash-map kvs)
           (merge {:kuntalupatunnus "888-10-12"
                   :contract-text   "Lorem ipsum"
                   :handler         "Decider"
                   :verdict-date    (timestamp "21.5.2018")})
           (into [])
           (apply concat)
           (apply (partial fill-verdict apikey app-id verdict-id)))
      (command apikey :publish-legacy-verdict
               :id app-id
               :verdict-id verdict-id) => ok?
      (verdict-pdf-queue-test apikey
                              {:app-id       app-id
                               :verdict-id   verdict-id
                               :state        "agreementPrepared"
                               :verdict-name "Sopimus"
                               :contents     "Sopimus"}))
    verdict-id))

(defn give-local-legacy-verdict
  [apikey app-id & kvs]
  (let [{:keys [verdict-id]} (local-command apikey :new-legacy-verdict-draft
                                            :id app-id)]
    (facts "Create, fill and publish local legacy verdict"
      (->> (apply hash-map kvs)
           (merge {:kuntalupatunnus "888-10-12"
                   :verdict-code    "1" ;; Granted
                   :verdict-text    "Lorem ipsum"
                   :handler         "Decider"
                   :anto            (timestamp "21.5.2018")
                   :lainvoimainen   (timestamp "30.5.2018")})
           (into [])
           (apply concat)
           (apply (partial local-fill-verdict apikey app-id verdict-id)))
      (local-command apikey :publish-legacy-verdict
                     :id app-id
                     :verdict-id verdict-id) => ok?
      ;; TODO: Do not know how to make this work locally.
      #_(local-verdict-pdf-queue-test apikey
                                    {:app-id     app-id
                                     :verdict-id verdict-id}))
    verdict-id))
