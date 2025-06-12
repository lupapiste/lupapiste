(ns lupapalvelu.bulletin-rss-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer [fact facts just]]
            [sade.date :as date]
            [sade.xml :as xml]))

(defn content-in [m tag]
  (->> m
       (some #(when (= (:tag %) tag) %))
       :content))

(defn get-all-tags [m tag]
  (filter #(= (:tag %) tag) m))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "rss feed test"
    (fact "Add additional municipality scope"
      (command admin :add-scope
               :organization "753-R"
               :permitType "R"
               :municipality "992"
               :inforequestEnabled true
               :applicationEnabled true
               :openInforequestEnabled false
               :openInforequestEmail ""
               :opening nil) => ok?)
    (fact "Should return 404 if no organization found"
      (http-get (str (server-address) "/rss/kuulutus?organization=42-Q&lang=fi") {:throw-exceptions false}) => http404?)
    (fact "Should return 200 and an empty rss feed if no bulletins are found"
      (let [res   (http-get (str (server-address) "/rss/kuulutus?organization=753-R&lang=fi") {:throw-exceptions false})
            _     (fact res => http200?)
            items (-> (xml/parse (:body res)) :content (content-in :channel) (get-all-tags :item))]
        (count items)             => 1
        (-> items first :content) => [{:attrs   {:isPermaLink "false"}
                                       :content ["no-data"]
                                       :tag     :guid}
                                      {:attrs   nil
                                       :content ["Ei kuulutuksia"]
                                       :tag     :title}]))
    (fact "Should enable Pate for Sipoo"
      (toggle-pate "753-R" true) => true)
    (fact "Should create a verdict and generate rss-feed"
      (set-default-r-verdict-template-settings sipoo "753-R")
      (fact "kuulutus"
        (let [{app-id :id}  (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
              r-template-id (init-and-publish-verdict-template sipoo "753-R" :r
                                                               :language       "fi"
                                                               :giver          :viranhaltija
                                                               :verdict-code   :ehdollinen
                                                               :kuulutus-dates [:k-lainvoimainen :k-muutoksenhaku :k-kuulutus]
                                                               :paatosteksti   "Verdict text.")
              _             (command sonja :update-app-bulletin-op-description
                                     :id app-id
                                     :description "Bulletin description")
              verdict-id    (create-and-publish-verdict sonja app-id r-template-id
                                                        :verdict-code     "hyvaksytty"
                                                        :k-kuulutus       (date/timestamp "04.02.2025 22:00")
                                                        :k-muutoksenhaku  (date/timestamp "04.02.2225 22:00")
                                                        :k-lainvoimainen  (date/timestamp "03.02.2025 22:00")
                                                        :verdict-text     "Verdict given"
                                                        :verdict-date     (date/timestamp "02.02.2025 22:00")
                                                        :giver-first-name "Pekka"
                                                        :giver-last-name  "Päättäjä")]
          (fact "should clear cache"
            (http-get (str (server-address) "/rss/bulletin-clear-cache") {:throw-exceptions false}) => http200?)
          (fact "Should return 200 and generated RSS-feed"
            (let [res      (http-get (str (server-address) "/rss/kuulutus?organization=753-R&lang=fi") {:throw-exceptions false})
                  _        (fact res => http200?)
                  rss-feed (xml/parse (:body res))
                  _        (fact (:tag rss-feed) => :rss)
                  channel  (-> rss-feed :content (content-in :channel))]
              (content-in channel :copyright)   => ["Cloudpermit Oy"]
              (content-in channel :language)    => ["fi"]
              (content-in channel :title)       => ["Sipoo - Sipoo R kuulutukset"]
              (content-in channel :description) => ["Sipoo - Sipoo R kuulutukset"]
              (content-in channel :link)        => [(str (server-address) "/rss/kuulutus?organization=753-R&lang=fi")]
              (content-in channel :pubDate)     => (just string?)
              (let [items (get-all-tags channel :item)
                    _     (fact (count items) => 1)
                    item  (:content (first items))]
                (content-in item :lupapiste:toimenpideteksti)     => ["Asuinkerrostalon tai rivitalon rakentaminen"]
                (content-in item :lupapiste:lupatunnus)           => [app-id]
                (content-in item :lupapiste:rakennuspaikka)       => ["foo 42, bar"]
                (content-in item :lupapiste:julkaisuAlkaa)        => ["Tue, 4 Feb 2025 00:00:00 +0200"]
                (content-in item :lupapiste:julkaisuPaattyy)      => ["Fri, 4 Feb 2225 23:59:59 +0200"]
                (content-in item :lupapiste:kiinteistotunnus)     => ["75300000000000"]
                (content-in item :lupapiste:paatosPvm)            => ["Sun, 2 Feb 2025 22:00:00 +0200"]
                (content-in item :lupapiste:paatoksenPykala)      => ["1"]
                (content-in item :guid)                           => [(str app-id "_" verdict-id)]
                (content-in item :pubDate)                        => (just string?)
                (content-in item :lupapiste:asiakirjaLink)        => [(format "%s/api/raw/download-bulletin-doc?bulletinId=%s_%s"
                                                                              (server-address)
                                                                              app-id
                                                                              verdict-id)]
                (content-in item :lupapiste:kuntanumero)          => ["753"]
                (content-in item :description)                    => ["Bulletin description"]
                (content-in item :link)
                => [(str (server-address) "/app/fi/local-bulletins?organization=753-R#!/r-bulletin/" app-id "_" verdict-id)])))
          (fact "Municipality code filter"
            (fact "Invalid municipality for org"
              (let [res (http-get (str (server-address) "/rss/kuulutus?organization=753-R&lang=fi&municipality=222") {:throw-exceptions false})]
                res => http404?))
            (fact "Valid municipality for org, no results"
              (let [res      (http-get (str (server-address) "/rss/kuulutus?organization=753-R&lang=fi&municipality=992") {:throw-exceptions false})
                    _        (fact res => http200?)
                    rss-feed (xml/parse (:body res))
                    _        (fact (:tag rss-feed) => :rss)
                    channel  (-> rss-feed :content (content-in :channel))]
                (content-in channel :copyright)   => ["Cloudpermit Oy"]
                (content-in channel :language)    => ["fi"]
                (content-in channel :title)       => ["Sipoo - Sipoo R kuulutukset"]
                (content-in channel :description) => ["Sipoo - Sipoo R kuulutukset"]
                (content-in channel :link)        => [(str (server-address) "/rss/kuulutus?organization=753-R&lang=fi&municipality=992")]
                (let [items (get-all-tags channel :item)
                      item  (:content (first items))]
                  (count items) => 1
                  (content-in item :guid) => ["no-data"]))))))
      (fact "julkipano"
        (let [{app-id :id}        (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
              change-process-resp (command sonja :change-bulletin-process :id app-id :process "julkipano")
              r-template-id       (init-and-publish-verdict-template sipoo "753-R" :r
                                                                     :language       "fi"
                                                                     :giver          :viranhaltija
                                                                     :verdict-code   :ehdollinen
                                                                     :verdict-dates [:lainvoimainen :muutoksenhaku :julkipano]
                                                                     :paatosteksti   "Verdict text.")
              _                   (command sonja :update-app-bulletin-op-description
                                           :id app-id
                                           :description "Bulletin description")
              verdict-id          (create-and-publish-verdict sonja app-id r-template-id
                                                              :verdict-code     "hyvaksytty"
                                                              :julkipano        (date/timestamp "04.02.2025 22:00")
                                                              :muutoksenhaku    (date/timestamp "04.02.2225 22:00")
                                                              :lainvoimainen    (date/timestamp "03.02.2025 22:00")
                                                              :verdict-text     "Verdict given"
                                                              :verdict-date     (date/timestamp "02.02.2025 22:00")
                                                              :giver-first-name "Pekka"
                                                              :giver-last-name  "Päättäjä")]
          change-process-resp => ok?
          (fact "should clear cache"
            (http-get (str (server-address) "/rss/bulletin-clear-cache") {:throw-exceptions false}) => http200?)
          (fact "Should return 200 and generated RSS-feed"
            (let [res      (http-get (str (server-address) "/rss/julkipano?organization=753-R&lang=fi") {:throw-exceptions false})
                  _        (fact res => http200?)
                  rss-feed (xml/parse (:body res))
                  _        (fact (:tag rss-feed) => :rss)
                  channel  (-> rss-feed :content (content-in :channel))]
              (content-in channel :copyright)   => ["Cloudpermit Oy"]
              (content-in channel :language)    => ["fi"]
              (content-in channel :title)       => ["Sipoo - Sipoo R julkipanot"]
              (content-in channel :description) => ["Sipoo - Sipoo R julkipanot"]
              (content-in channel :link)        => [(str (server-address) "/rss/julkipano?organization=753-R&lang=fi")]
              (content-in channel :pubDate)     => (just string?)
              (let [items (get-all-tags channel :item)
                    _     (fact (count items) => 1)
                    item  (:content (first items))]
                (content-in item :lupapiste:toimenpideteksti)     => ["Asuinkerrostalon tai rivitalon rakentaminen"]
                (content-in item :lupapiste:lupatunnus)           => [app-id]
                (content-in item :lupapiste:rakennuspaikka)       => ["foo 42, bar"]
                (content-in item :lupapiste:julkaisuAlkaa)        => ["Tue, 4 Feb 2025 00:00:00 +0200"]
                (content-in item :lupapiste:julkaisuPaattyy)      => ["Fri, 4 Feb 2225 23:59:59 +0200"]
                (content-in item :lupapiste:kiinteistotunnus)     => ["75300000000000"]
                (content-in item :lupapiste:paatosPvm)            => ["Sun, 2 Feb 2025 22:00:00 +0200"]
                (content-in item :lupapiste:paatoksenPykala)      => ["2"]
                (content-in item :guid)                           => [(str app-id "_" verdict-id)]
                (content-in item :pubDate)                        => (just string?)
                (content-in item :lupapiste:asiakirjaLink)        => nil ;; No asiakirja for julkipano
                (content-in item :lupapiste:kuntanumero)          => ["753"]
                (content-in item :description)                    => ["Bulletin description"]
                (content-in item :link)
                => [(str (server-address) "/app/fi/local-bulletins?organization=753-R#!/r-bulletin/" app-id "_" verdict-id)]))))))))
