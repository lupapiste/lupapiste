(ns lupapalvelu.backing-system.dmcity.core-itest
  (:require [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn make-xml [app-id]
  (format "<?xml version='1.0' encoding='UTF-8'?><foo>%s</foo>"
          app-id))

(defn make-url [target]
  (format "%s/dev/dummy-dmcity/endpoint/%s"
          (server-address) (name target)))

(defn clear-messages []
  (mongo/remove-many :integration-messages {})
  (clear-collection "integration-messages"))

(defn out-message [app-id]
  (reduce (fn [_ _]
            (if-let [msg (mongo/select-one :integration-messages
                                           {:direction      :out
                                            :application.id app-id
                                            :acknowledged   {"$ne" nil}})]
              (reduced msg)
              (pubsub-wait 2)))
          nil
          (range 20)))

(defn in-message [app-id]
  (reduce (fn [_ _]
            (if-let [msg (util/find-by-key :direction "in"
                                           (integration-messages app-id))]
              (reduced msg)
              (pubsub-wait 2)))
          nil
          (range 20)))

(defn check-messages [app-id out-check in-check]
  (let [out-msg (out-message app-id)
        in-msg  (in-message app-id)]
    (facts "Messages"
      (fact "Out"
        out-msg => (contains out-check))
      (fact "In"
        in-msg => (contains in-check)))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      (test-system/with-pubsub-subscriber :dmcity/state-change-queue)
                      (test-system/with-pubsub-publisher :dmcity/state-change-queue))
  (with-local-actions
    (let [system               {}
          tampere-r            (mongo/by-id :organizations "837-R")
          tampere-r-conversion (assoc-in tampere-r
                                         [:dmcity :targets :conversion :enabled]
                                         true)
          user                 (mongo/select-one :users {:username "veikko"})
          {app-id :id}         (create-and-submit-local-application
                                 pena :operation "aita"
                                 :address "Kissanmaa"
                                 :propertyId "83713308610003"
                                 :x 330722 :y 6822587)
          application          (mongo/by-id :applications app-id)
          p-application        (assoc application :permitType "P")
          xml                  (make-xml app-id)]
      (fact "Application created"
        application => truthy)

      (fact "Sent to and received by (dummy) dmcity"
        (check-messages app-id
                        {:messageType "state-change"
                         :url         (make-url "HakemuksenTilapaivitys")}
                        {:data (contains {:target "HakemuksenTilapaivitys"
                                          :body   truthy})}))


      (facts "send-xml"
        (clear-messages)
        (dmcity/send-xml system tampere-r application user :application xml) => nil
        (check-messages app-id
                        {:messageType "KuntaGML application"
                         :url         (make-url "hakemus-path")}
                        {:data (contains {:target "hakemus-path"
                                          :body   (contains app-id)})})
        (facts "Fail and report"
          (clear-messages)
          (dmcity/send-xml system tampere-r application user :foobar xml) => nil
          (mongo/any? :integration-messages {:application.id app-id}) => false
          (dmcity/send-xml system tampere-r application user :foobar
                           xml {:report? true})
          => "No such path for R: foobar")

        (facts "No conversion, fallback to application"
          (clear-messages)
          (dmcity/send-xml system tampere-r application user :conversion xml) => nil
          (check-messages app-id
                          {:messageType "KuntaGML conversion"
                           :url         (make-url "hakemus-path")}
                          {:data (contains {:target "hakemus-path"
                                            :body   (contains app-id)})}))

        (facts "Force enabled"
          (clear-messages)
          (dmcity/send-xml system tampere-r p-application user :application
                           xml {:report? true})
          => "Permit type P not supported or not enabled in 837-R."
          (mongo/any? :integration-messages {:application.id app-id})
          => false
          (dmcity/send-xml system tampere-r p-application user :application
                           xml {:report?        true
                                :force-enabled? true}) => nil
          (check-messages app-id
                          {:messageType "KuntaGML application"
                           :url         (make-url "Poikkeamispaatos")}
                          {:data (contains {:target "Poikkeamispaatos"
                                            :body   (contains app-id)})}))

        (facts "Force conversion has no effect if conversion is not enabled"
          (clear-messages)
          (dmcity/send-xml system tampere-r application user :application
                           xml {:force-path :conversion}) => nil
          (check-messages app-id
                          {:messageType "KuntaGML application"
                           :url         (make-url "hakemus-path")}
                          {:data (contains {:target "hakemus-path"
                                            :body   (contains app-id)})}))

        (facts "Enable conversion and force it"
          (clear-messages)
          (dmcity/send-xml system tampere-r-conversion
                           application user :application
                           xml {:force-path :conversion}) => nil
          (check-messages app-id
                          {:messageType "KuntaGML application"
                           :url         (make-url "RakennuslupienKonversio")}
                          {:data (contains {:target "RakennuslupienKonversio"
                                            :body   (contains app-id)})}))

        (facts "Force application path"
          (clear-messages)
          (dmcity/send-xml system tampere-r-conversion
                           application user :conversion
                           xml) => nil
          (check-messages app-id
                          {:messageType "KuntaGML conversion"
                           :url         (make-url "RakennuslupienKonversio")}
                          {:data (contains {:target "RakennuslupienKonversio"
                                            :body   (contains app-id)})})
          (clear-messages)
          (dmcity/send-xml system tampere-r-conversion
                           application user :conversion
                           xml {:force-path :application}) => nil
          (check-messages app-id
                          {:messageType "KuntaGML conversion"
                           :url         (make-url "hakemus-path")}
                          {:data (contains {:target "hakemus-path"
                                            :body   (contains app-id)})}))

        (facts "Message type suffix"
          (clear-messages)
          (dmcity/send-xml system tampere-r application user :application
                           xml {:type-suffix :hello}) => nil
          (check-messages app-id
                          {:messageType "KuntaGML hello"
                           :url         (make-url "hakemus-path")}
                          {:data (contains {:target "hakemus-path"
                                            :body   (contains app-id)})})))

      (facts "send-state-change"
        (clear-messages)
        (dmcity/send-state-change system tampere-r application user :sent) => nil
        (check-messages app-id
                        {:messageType "state-change"
                         :url         (make-url "HakemuksenTilapaivitys")}
                        {:data (contains {:target "HakemuksenTilapaivitys"
                                          :body   (contains app-id)})})
        (facts "Fail and report"
          (clear-messages)
          (dmcity/send-state-change system tampere-r application user :bad) => nil
          (dmcity/send-state-change system tampere-r p-application user :sent) => nil
          (mongo/any? :integration-messages {:application.id app-id}) => false
          (dmcity/send-state-change system tampere-r application user :bad
                                    {:report? true})
          => "Not a valid state change: submitted -> bad."
          (dmcity/send-state-change system tampere-r p-application user :sent
                                    {:report? true})
          => "Permit type P not supported or not enabled in 837-R.")

        (facts "Force enabled"
          (clear-messages)
          (dmcity/send-state-change system tampere-r p-application user :sent
                                    {:report?        true
                                     :force-enabled? true}) => nil
          (check-messages app-id
                          {:messageType "state-change"
                           :url         (make-url "HakemuksenTilapaivitys")}
                          {:data (contains {:target "HakemuksenTilapaivitys"
                                            :body   (contains app-id)})}))

        (facts "Force conversion: not enabled"
          (clear-messages)
          (dmcity/send-state-change system tampere-r application user :verdictGiven
                                    {:force-path :conversion}) => nil
          (check-messages app-id
                          {:messageType "state-change"
                           :url         (make-url "HakemuksenTilapaivitys")}
                          {:data (contains {:target "HakemuksenTilapaivitys"
                                            :body   (contains app-id)})}))

        (facts "Force conversion: enabled"
          (clear-messages)
          (dmcity/send-state-change system tampere-r-conversion
                                    application user :verdictGiven
                                    {:force-path :conversion}) => nil
          (check-messages app-id
                          {:messageType "state-change"
                           :url         (make-url "HakemuksenTilapaivitysKonversio")}
                          {:data (contains {:target "HakemuksenTilapaivitysKonversio"
                                            :body   (contains app-id)})}))

        (let [converted (assoc application :facta-imported true)]
          (facts "Force application path"
            (clear-messages)
            (dmcity/send-state-change system tampere-r-conversion
                                      converted user :verdictGiven) => nil
            (check-messages app-id
                            {:messageType "state-change"
                             :url         (make-url "HakemuksenTilapaivitysKonversio")}
                            {:data (contains {:target "HakemuksenTilapaivitysKonversio"
                                              :body   (contains app-id)})})
            (clear-messages)
            (dmcity/send-state-change system tampere-r-conversion
                                      converted user :verdictGiven
                                      {:force-path :application}) => nil
            (check-messages app-id
                            {:messageType "state-change"
                             :url         (make-url "HakemuksenTilapaivitys")}
                            {:data (contains {:target "HakemuksenTilapaivitys"
                                              :body   (contains app-id)})})))))))
