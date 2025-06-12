(ns lupapalvelu.fixture.dynasty-ymp-fixture
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]))

(def users
  [;; Solita admin:  admin / admin
   {:id        "777777777777777777000099"
    :email     "admin@solita.fi"
    :enabled   true
    :role      "admin"
    :language  "fi"
    :firstName "Admin"
    :lastName  "Admin"
    :phone     "03030303"
    :username  "admin"
    :private   {:password "$2a$10$WHPur/hjvaOTlm41VFjtjuPI5hBoIMm8Y1p2vL4KqRi7QUvHMS1Ie"
                :apikey   "5087ba34c2e667024fbd5992"}}

   ;; Kuopio

   ;; Kuopio YMP-paakayttaja:  kuopio / kuopio
   {:id        "297-YMP"
    :enabled   true
    :lastName  "Kuopio"
    :firstName "Paakayttaja-YMP"
    :city      "Kuopio"
    :language  "fi"
    :username  "kuopio-ymp"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "kuopio-ymp@kuopio.fi"
    :role      "authority"
    :zip       "92620"
    :orgAuthz  {:297-YMP #{:authorityAdmin}}
    :private   {:password "$2a$10$YceveAiQXbeUs65B4FZ6lez/itf0UEXooHcZlygI2WnQGhF0dJ1jO"
                :apikey   "lhIqT1YwOMH8HuiCGcjBtGggfeRaxZL5OUNd3r4u"}}


   ;; Hakijat

   ;; Hakija: pena / pena
   {:id                   "777777777777777777000020"
    :username             "pena"
    :enabled              true
    :language             "fi"
    :role                 "applicant"
    :personId             "010203-040A"
    :personIdSource       "identification-service"
    :firstName            "Pena"
    :lastName             "Panaani"
    :email                "pena@example.com"
    :street               "Paapankuja 12"
    :zip                  "92620"
    :city                 "Piippola"
    :phone                "0102030405"
    :allowDirectMarketing true
    :private              {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
                           :apikey   "502cb9e58426c613c8b85abd"}}])

(def default-keys-for-organizations {:app-required-fields-filling-obligatory false
                                     :validate-verdict-given-date            true
                                     :validate-verdict-bulletin-date         false
                                     :kopiolaitos-email                      nil
                                     :kopiolaitos-orderer-address            nil
                                     :kopiolaitos-orderer-email              nil
                                     :kopiolaitos-orderer-phone              nil
                                     :use-attachment-links-integration       false
                                     :inspection-summaries-enabled           false
                                     :docstore-info                          org/default-docstore-info})


(defn names [names-map]
  (i18n/with-default-localization names-map (:fi names-map)))

(defn link [link-names link-url]
  {:name (names link-names)
   :url  (i18n/with-default-localization {:fi link-url} link-url)})

(def ymp-permit-types #{"MAL" "VVVL" "YI" "YL" "YM"})

(def organizations (->> [
                         ;; Kuopio YMP
                         {:id                                   "297-YMP"
                          :name                                 (names {:fi "Kuopio YMP"
                                                                        :sv "Kuopio YMP"})
                          :scope                                (->> ymp-permit-types
                                                                     (mapv (fn [permit-type]
                                                                             {:open-inforequest-email  nil
                                                                              :open-inforequest        false
                                                                              :new-application-enabled true
                                                                              :inforequest-enabled     true
                                                                              :municipality            "297"
                                                                              :permitType              permit-type})))
                          :links                                [(link {:fi "Rakentamisen sähköiset lupapalvelut Kuopiossa"
                                                                        :sv "Rakentamisen sähköiset lupapalvelut Kuopiossa"}
                                                                       "http://www.kuopio.fi/web/tontit-ja-rakentaminen/rakentamisen-sahkoiset-lupapalvelut")
                                                                 (link {:fi "Kuopion alueellinen rakennusvalvonta"
                                                                        :sv "Kuopion alueellinen rakennusvalvonta"}
                                                                       "http://www.kuopio.fi/web/tontit-ja-rakentaminen/rakennusvalvonta")
                                                                 (link {:fi "Kuopion kaupunki"
                                                                        :sv "Kuopion kaupunki"}
                                                                       "http://www.kuopio.fi")]
                          :handler-roles                        [{:id      "abba1111111111111111b297"
                                                                  :name    {:fi "Käsittelijä"
                                                                            :sv "Handläggare"
                                                                            :en "Handler"}
                                                                  :general true}]
                          :operations-attachments               {}
                          :selected-operations                  (->> operations/operations
                                                                     (filter (fn [[_ v]] (contains? ymp-permit-types (name (:permit-type v)))))
                                                                     (mapv first))
                          :permanent-archive-enabled            false
                          :digitizer-tools-enabled              false
                          :automatic-review-fetch-enabled       true
                          :automatic-ok-for-attachments-enabled true
                          :multiple-operations-supported        false}]
                        (map (partial merge default-keys-for-organizations))
                        (map org/valid-organization!)
                        (into [])))

(defmethod fixture/fixture :dynasty-ymp-fixture
  [_ _]
  (log/info "Running dynasty fixture")
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :organizations organizations))

(defmethod fixture/users :dynasty-ymp-fixture
  [_]
  users)
