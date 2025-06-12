(ns lupapalvelu.conversion.state-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.state :as conv-st]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.date :as date]
            [sade.xml :as xml]
            [schema.core :as sc]))

(use-fixtures :once (fn [f]
                      (sc/with-fn-validation
                        (f))))

(defn make-change
  ([state date]
   (format "<rakval:kasittelynTilatieto><yht:Tilamuutos>%s<yht:tila>%s</yht:tila></yht:Tilamuutos></rakval:kasittelynTilatieto>"
           (if date (str "<yht:pvm>" date "</yht:pvm>") "")
           state))
  ([state] (make-change state nil)))

(defn make-xml-str
  [& changes]
  (->> (shuffle changes)
       (map (partial apply make-change))
       (apply str)
       (format "<rakval:RakennusvalvontaAsia>%s</rakval:RakennusvalvontaAsia>")))

(defn make-xml
  [& changes]
  (-> (apply make-xml-str changes)
      (xml/parse-string "utf8")
      strip-xml-namespaces ))

(def user (usr/summary conv-util/conversion-user-data))

(def kw-set-or-nil #'conv-st/kw-set-or-nil)

(deftest kw-set-or-nil-test
  (testing "nope"
    (doseq [a [nil #{}]]
      (is (nil? (kw-set-or-nil a)))))
  (testing "nope"
    (doseq [[a b] [[:foo #{:foo} [#{:hello :world} #{:hello :world}]]]]
      (is (= b (kw-set-or-nil a))))))

(def states->map #'conv-st/states->map)

(deftest states->map-test
  (testing "nope"
    (doseq [a [nil {} [] [[] []]
               {"  " :bar "hii" nil "hoo" #{}}]]
      (is (nil? (states->map a)))))
  (testing "map"
    ;; The values are processed in the default order.
    (is (= {"hello" {:order 0 :states #{:open}}
            "world" {:order 1 :states #{:ready :closed}}}
           (states->map {"hello" :open "world" #{:ready :closed}})))
    (is (= {"hello" {:order 900 :states #{:open}}
            "world" {:order 901 :states #{:ready :closed}}}
           (states->map {"hello" :open
                         "world" #{:ready :closed}} 900))))
  (testing "sequence of pairs"
    (is (= {"hello" {:order 0 :states #{:open}}
            "world" {:order 1 :states #{:ready :closed}}}
           (states->map [["hello" :open] ["world" #{:ready :closed}]])))
    (is (= {"hello" {:order 901 :states #{:open}}
            "world" {:order 900 :states #{:ready :closed}}}
           (states->map [["world" #{:ready :closed}]
                         ["hello" :open]] 900)))))

(def make-state-map #'conv-st/make-state-map)
(def supported-state-map @#'conv-st/supported-state-map)

(deftest make-state-map-test
  (testing "supported state map only"
    (is (= supported-state-map
           (make-state-map {}))))
  (testing "replace"
    (is (= (-> supported-state-map
               (assoc-in ["vireillä" :states] #{:open})
               (assoc-in ["uusi lupa, ei käsittelyssä" :states]
                         #{:verdictGiven :ready}))
           (make-state-map {:state-mapping
                            {"vireillä"                   :open
                             "uusi lupa, ei käsittelyssä" #{:verdictGiven :ready}}}))))
  (testing "add"
    (is (= (assoc supported-state-map
                  ;; Default order caveat applies here, as well.
                  "foo" {:order 100 :states #{:sent}}
                  "bar" {:order 101 :states #{:closed :canceled}})
           (make-state-map {:state-mapping
                            {"foo" :sent
                             "bar" #{:closed :canceled}}}))))
  (testing "both"
    (is (= (-> supported-state-map
               ;; Default order caveat applies here, as well.
               (assoc "foo" {:order 102 :states #{:sent}}
                      "bar" {:order 103 :states #{:closed :canceled}})
               (assoc-in ["vireillä" :states] #{:open})
               (assoc-in ["uusi lupa, ei käsittelyssä" :states]
                         #{:verdictGiven :ready}))
           (make-state-map {:state-mapping
                            {"vireillä"                   :open
                             "uusi lupa, ei käsittelyssä" #{:verdictGiven :ready}
                             "foo"                        :sent
                             "bar"                        #{:closed :canceled}}})))))

(def now-ts (date/timestamp "11.11.2024"))

(def soon #'conv-st/state-override-or-nil)

(deftest state-override-or-nil-test
  (let [cfg {:state-overrides {"one"   :ready
                               "two"   #{:verdictGiven :foremanVerdictGiven}
                               "three" {:state :sent :ts "5.2.2024"}
                               "four"  {:state #{:closed :ready}
                                        :ts    "1.5.2024"}
                               "five" {:state :closed :ts "1.1.2025"}}}
        app {:permitType "R"}]
    (with-redefs [date/now (constantly (date/zoned-date-time now-ts))]
      (testing "No override"
        (is (nil? (soon {} "hello" app)))
        (is (nil? (soon cfg "hello" app))))
      (testing "Bad overrides"
        (is (nil? (soon cfg "one" app)))
        (is (nil? (soon cfg "two" (assoc-in app [:primaryOperation :name]
                                            "jatkoaika"))))
        (is (nil? (soon cfg "five" app))))
      (testing "Good overrides"
        (is (= {:state :verdictGiven :ts now-ts}
               (soon cfg "two" app)))
        (is (= {:state :sent :ts (date/timestamp "5.2.2024")}
               (soon cfg "three" app)))
        (is (= {:state :closed :ts (date/timestamp "1.5.2024")}
               (soon cfg "four" app)))
        (is (= {:state :ready :ts (date/timestamp "1.5.2024")}
               (soon cfg "four" (assoc-in app [:primaryOperation :name]
                                            "jatkoaika"))))))))

(deftest generate-history-array-test
  (with-redefs [date/now (constantly (date/zoned-date-time now-ts))]
    (testing "Zero history"
      (is (= [{:state :closed :ts now-ts :user user}]
             (conv-st/generate-history-array nil nil nil {:permitType "R"})))
      (is (= [{:state :closed :ts now-ts :user user}]
             (conv-st/generate-history-array nil nil
                                             (make-xml ["Blue Ant" "2023-05-10Z"])
                                             {:permitType "R"}))))
    (testing "Full history"
      (is (= [{:state :submitted :ts (date/timestamp "1.5.2023") :user user}
              {:state :sent :ts (date/timestamp "2.5.2023") :user user}
              {:state :verdictGiven :ts (date/timestamp "3.5.2023") :user user}
              {:state :appealed :ts (date/timestamp "4.5.2023") :user user}
              {:state :constructionStarted :ts (date/timestamp "5.5.2023") :user user}
              {:state :onHold :ts (date/timestamp "6.5.2023") :user user}
              {:state :inUse :ts (date/timestamp "7.5.2023") :user user}
              {:state :inUse :ts (date/timestamp "8.5.2023") :user user}
              {:state :closed :ts (date/timestamp "9.5.2023") :user user}
              {:state :closed :ts (date/timestamp "10.5.2023") :user user}
              {:state :extinct :ts (date/timestamp "11.5.2023") :user user}
              {:state :open :ts (date/timestamp "12.5.2023") :user user}]
             (conv-st/generate-history-array
               nil nil
               (make-xml ["vireillä" "2023-05-01"]
                         ["lupa käsitelty, siirretty päättäjälle" "2023-05-02"]
                         ["lupa hyväksytty" "2023-05-03"]
                         ["lupa hyväksytty" "2025-05-03"] ; In the future, removed
                         ["päätöksestä valitettu, valitusprosessin tulosta ei ole" "2023-05-04"]
                         ["rakennustyöt aloitettu" "2023-05-05"] ;; Duplicates are removed
                         ["rakennustyöt aloitettu" "2023-05-05"]
                         ["rakennustyöt aloitettu" "2023-05-05"]
                         ["rakennustyöt keskeytetty" "2023-05-06"]
                         ["unknown state" "2023-05-20"] ;; Removed
                         ["osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on käyttöönotettu" "2023-05-07"]
                         ["yksi tai useampia luvan rakennuksista on käyttöönotettu" "2023-05-08"]
                         ["lopullinen loppukatselmus tehty" "2023-05-09"]
                         ["luvalla ei loppukatselmusehtoa, lupa valmis" "2023-05-10"]
                         ["lupa rauennut" "2023-05-11"]
                         ["uusi lupa, ei käsittelyssä" "2023-05-12"])
               {:permitType "R"}))))
    (testing "Full history with conflicts"
      (is (= [{:state :open :ts 0 :user user}
              {:state :submitted :ts 0 :user user}
              {:state :sent :ts 0 :user user}
              {:state :verdictGiven :ts 0 :user user}
              {:state :appealed :ts 0 :user user}
              {:state :constructionStarted :ts 0 :user user}
              {:state :onHold :ts 0 :user user}
              {:state :inUse :ts 0 :user user}
              {:state :closed :ts 0 :user user}
              {:state :extinct :ts 0 :user user}]
             (conv-st/generate-history-array
               nil nil
               (make-xml ["vireillä"]
                         ["lupa käsitelty, siirretty päättäjälle"]
                         ["lupa hyväksytty"]
                         ["päätöksestä valitettu, valitusprosessin tulosta ei ole"]
                         ["rakennustyöt aloitettu"]
                         ["rakennustyöt keskeytetty"]
                         ["osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on käyttöönotettu"]
                         ["lopullinen loppukatselmus tehty"]
                         ["luvalla ei loppukatselmusehtoa, lupa valmis"]
                         ["lupa rauennut"]
                         ["uusi lupa, ei käsittelyssä"]
                         ["lupa evätty"])
               {:permitType "R"}))))
    (testing "Date order"
      (is (= [{:state :open :ts (date/timestamp "3.3.2022") :user user}
              {:state :verdictGiven :ts (date/timestamp "4.4.2022") :user user}
              {:state :extinct :ts (date/timestamp "12.12.2022") :user user}
              {:state :submitted :ts (date/timestamp "1.1.2023") :user user}]
             (conv-st/generate-history-array
               nil nil
               (make-xml ["uusi lupa, ei käsittelyssä" "2022-03-03"]
                         ["lupa rauennut" "2022-12-12Z"]
                         ["vireillä" "2023-01-01+02:00"]
                         ["lupa evätty" "2022-04-04"])
               {:permitType "R"}))))
    (testing "Different states for different applications"
      (let [from-states    [["vireillä"]
                            ["lupa käsitelty, siirretty päättäjälle"]
                            ["lupa hyväksytty"]
                            ["päätöksestä valitettu, valitusprosessin tulosta ei ole"]
                            ["rakennustyöt aloitettu"]
                            ["rakennustyöt keskeytetty"]
                            ["osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on käyttöönotettu"]
                            ["lopullinen loppukatselmus tehty"]
                            ["luvalla ei loppukatselmusehtoa, lupa valmis"]
                            ["lupa rauennut"]
                            ["uusi lupa, ei käsittelyssä"]
                            ["lupa evätty"]]
            history-states #(map :state
                                 (conv-st/generate-history-array
                                   nil nil
                                   (apply make-xml from-states) %))]
        (testing "Foreman application"
          (is (= [:open :submitted :sent :foremanVerdictGiven :appealed]
                 (history-states {:permitType       "R"
                                  :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}})))

          (is (= [:open :submitted :sent :foremanVerdictGiven :appealed]
                 (history-states {:permitType       "R"
                                  :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                  :permitSubtype    "tyonjohtaja-hakemus"})))

          (is (= [:open :submitted :acknowledged]
                 (history-states {:permitType       "R"
                                  :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                  :permitSubtype    "tyonjohtaja-ilmoitus"}))))
        (testing "Extension application"
          (is (= [:open :submitted :sent :ready :appealed :extinct]
                 (history-states {:permitType       "R"
                                  :primaryOperation {:name "jatkoaika"}}))))
        (testing "P application"
          (is (= [:open :submitted :sent :verdictGiven :appealed :constructionStarted
                  :onHold :inUse :closed :extinct]
                 (history-states {:permitType       "P"
                                  :primaryOperation {:name "poikkeamis"}}))))
        (testing "YA application: katulupa"
          (is (= [:open :submitted :sent :verdictGiven :appealed :constructionStarted
                  :closed :extinct]
                 (history-states {:permitType       "YA"
                                  :primaryOperation {:name "ya-katulupa-kaapelityot"}}))))
        (testing "YA application: sijoituslupa"
          (is (= [:open :submitted :sent :verdictGiven :appealed :extinct]
                 (history-states {:permitType       "YA"
                                  :primaryOperation {:name "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"}}))))
        (testing "YA application: käyttölupa"
          (is (= [ :open :submitted :sent :verdictGiven :appealed :constructionStarted
                  :closed :extinct]
                 (history-states {:permitType       "YA"
                                  :primaryOperation {:name "ya-katulupa-kaukolampotyot"}}))))
        (testing "Inforequest. Empty history fallbacks to terminal state"
          (is (= [:answered]
                 (history-states {:permitType       "R"
                                  :infoRequest      true
                                  :primaryOperation {:name "pientalo"}}))))))
    (testing "Force terminal state"
      (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}]
             (conv-st/generate-history-array {:force-terminal-state? false}
                                             nil
                                             (make-xml ["vireillä" "2023-04-04"])
                                             {:permitType "R"})))

      (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}
              {:state :closed :ts now-ts :user user}]
             (conv-st/generate-history-array {:force-terminal-state? true}
                                             nil
                                             (make-xml ["vireillä" "2023-04-04"])
                                             {:permitType "R"})))

      (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}
              {:state :extinct :ts now-ts :user user}]
             (conv-st/generate-history-array {:force-terminal-state? true}
                                             nil
                                             (make-xml ["vireillä" "2023-04-04"])
                                             {:permitType       "R"
                                              :primaryOperation {:name "jatkoaika"}})))

      (is (= [{:state :extinct :ts (date/timestamp "4.4.2023") :user user}]
             (conv-st/generate-history-array {:force-terminal-state? true}
                                             nil
                                             (make-xml ["lupa rauennut" "2023-04-04"])
                                             {:permitType "R"}))))
    (testing "state-overrides"
      (let [backend-id "12-1234-R"
            doc        {:backend-id backend-id}]
        (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}]
               (conv-st/generate-history-array {:state-overrides {"09-1234-R" :verdictGiven}}
                                               doc
                                               (make-xml ["vireillä" "2023-04-04"])
                                               {:permitType "R"})))

        (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}
                {:state :open :ts now-ts :user user}]
               (conv-st/generate-history-array {:state-overrides {backend-id :open}}
                                               doc
                                               (make-xml ["vireillä" "2023-04-04"])
                                               {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}
                {:state :inUse :ts now-ts :user user}]
               (conv-st/generate-history-array
                 {:state-overrides {backend-id #{:answered :ready :inUse}}}
                 doc
                 (make-xml ["vireillä" "2023-04-04"])
                 {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}]
               (conv-st/generate-history-array
                 {:state-overrides {backend-id #{:answered :ready}}}
                 doc
                 (make-xml ["vireillä" "2023-04-04"])
                 {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}
                {:state :sent :ts (date/timestamp "3.3.2023") :user user}]
               (conv-st/generate-history-array {:state-overrides {backend-id {:state #{:ready :sent}
                                                                              :ts    "3.3.2023"}}}
                                               doc
                                               (make-xml ["vireillä" "2023-04-04"])
                                               {:permitType "R"})))))

    (testing "state-mapping"
      (let [backend-id "12-1234-R"
            doc        {:backend-id backend-id}]
        (is (= [{:state :submitted :ts (date/timestamp "4.4.2023") :user user}]
               (conv-st/generate-history-array {:state-mapping {"foo" :open}}
                                               doc
                                               (make-xml ["vireillä" "2023-04-04"])
                                               {:permitType "R"})))

        (is (= [{:state :open :ts (date/timestamp "4.4.2023") :user user}]
               (conv-st/generate-history-array {:state-mapping {"vireillä" :open}}
                                               doc
                                               (make-xml ["vireillä" "2023-04-04"])
                                               {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "3.4.2023") :user user}
                {:state :inUse :ts (date/timestamp "4.4.2023") :user user}
                {:state :verdictGiven :ts (date/timestamp "5.4.2023") :user user}]
               (conv-st/generate-history-array
                 {:state-mapping {"foobar" #{:answered :ready :inUse}}}
                 doc
                 (make-xml ["vireillä" "2023-04-03"]
                           ["lupa evätty" "2023-04-05"]
                           ["foobar" "2023-04-04"])
                 {:permitType "R"})))))

    (testing "state-overrides, state-mapping and terminal-state"
      (let [backend-id "12-1234-R"
            doc        {:backend-id backend-id}]
        (is (= [{:state :submitted :ts (date/timestamp "3.4.2023") :user user}
                {:state :inUse :ts (date/timestamp "4.4.2023") :user user}
                {:state :verdictGiven :ts (date/timestamp "5.4.2023") :user user}
                {:state :closed :ts now-ts :user user}]
               (conv-st/generate-history-array
                 {:state-mapping         {"foobar" #{:answered :ready :inUse}}
                  :force-terminal-state? true}
                 doc
                 (make-xml ["vireillä" "2023-04-03"]
                           ["lupa evätty" "2023-04-05"]
                           ["foobar" "2023-04-04"])
                 {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "3.4.2023") :user user}
                {:state :inUse :ts (date/timestamp "4.4.2023") :user user}
                {:state :extinct :ts (date/timestamp "5.4.2023") :user user}]
               (conv-st/generate-history-array
                 {:state-mapping         {"foobar"      #{:answered :ready :inUse}
                                          "lupa evätty" #{:extinct}}
                  :force-terminal-state? true}
                 doc
                 (make-xml ["vireillä" "2023-04-03"]
                           ["lupa evätty" "2023-04-05"]
                           ["foobar" "2023-04-04"])
                 {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "3.4.2023") :user user}
                {:state :inUse :ts (date/timestamp "4.4.2023") :user user}
                {:state :verdictGiven :ts (date/timestamp "5.4.2023") :user user}
                {:state :sent :ts now-ts :user user}]
               (conv-st/generate-history-array
                 {:state-mapping         {"foobar" #{:answered :ready :inUse}}
                  :state-overrides       {backend-id :sent}
                  :force-terminal-state? true}
                 doc
                 (make-xml ["vireillä" "2023-04-03"]
                           ["lupa evätty" "2023-04-05"]
                           ["foobar" "2023-04-04"])
                 {:permitType "R"})))
        (is (= [{:state :submitted :ts (date/timestamp "3.4.2023") :user user}
                {:state :inUse :ts (date/timestamp "4.4.2023") :user user}
                {:state :verdictGiven :ts (date/timestamp "5.4.2023") :user user}]
               (conv-st/generate-history-array
                 {:state-mapping         {"foobar" #{:answered :ready :inUse}}
                  :state-overrides       {backend-id #{:foremanVerdictGiven :verdictGiven
                                                       :agreementSigned}}
                  :force-terminal-state? true}
                 doc
                 (make-xml ["vireillä" "2023-04-03"]
                           ["lupa evätty" "2023-04-05"]
                           ["foobar" "2023-04-04"])
                 {:permitType "R"})))))))

(deftest history-timestamps-test
  (doseq [h [nil [] [{:state :verdictGiven :ts 1234}] [{:foo "bar"}]]]
    (is (nil? (conv-st/history-timestamps h))))

  (is (= {:created 0 :opened 1 :submitted 2 :sent 3 :canceled 7}
         (->> [:draft :open :submitted :sent :final :inUse :onHold :canceled]
              (map-indexed (fn [i state] {:state state :ts i}))
              conv-st/history-timestamps))))

(def forward-verdict-state #'conv-st/forward-verdict-state)

(deftest forward-verdict-state-test
  (testing "nope"
    (is (nil? (forward-verdict-state {:permitType "R" :state "open"})))
    (is (nil? (forward-verdict-state {:permitType  "R" :state :draft
                                      :verdictDate now-ts})))
    (is (nil? (forward-verdict-state {:permitType  "R" :state :closed
                                      :verdictDate now-ts})))
    (is (nil? (forward-verdict-state {:permitType       "R" :state :foremanVerdictGiven
                                      :verdictDate      now-ts
                                      :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}))))
  (testing "state"
    (is (= :verdictGiven
           (forward-verdict-state {:permitType  "R" :state :open
                                   :verdictDate now-ts})))
    (is (= :foremanVerdictGiven
           (forward-verdict-state {:permitType       "R" :state :sent
                                   :verdictDate      now-ts
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}})))))

(def calculate-state #'conv-st/calculate-state)

(def doc {:id           "pleasework"
          :backend-id   "hello"
          :organization "123-R"
          :permit-type  "R"
          :converted    true
          :year         2024
          :LP-id        "LP-123-2024-00001"
          :kuntagml     {:xml (make-xml-str ["vireillä" "2023-04-03"]
                                            ["lupa käsitelty, siirretty päättäjälle" "2023-04-05"])}})
(def cfg {:organization "123-R"})
(def sent-ts (date/timestamp "5.4.2023"))
(def submitted-ts (date/timestamp "3.4.2023"))

(deftest  calculate-state-test
  (with-redefs [date/now (constantly (date/zoned-date-time now-ts))]
    (testing "No overrides, no mapping, no change"
      (is (= {:state :sent :ts sent-ts}
             (calculate-state cfg doc {:history    [{:state "sent"}]
                                       :permitType "R"}) )))
    (testing "No overrides, no mapping, change"
      (is (= {:state :sent :ts sent-ts}
             (calculate-state cfg doc {:history    [{:state "open"}]
                                       :permitType "R"})))
      (is (= {:state :verdictGiven}
             (calculate-state cfg doc {:history     [{:state "sent"}]
                                       :permitType  "R"
                                       :verdictDate 1234})))
      (is (= {:state :closed :ts now-ts}
             (calculate-state (assoc cfg :force-terminal-state? true)
                              doc {:history     [{:state "sent"}]
                                   :permitType  "R"
                                   :verdictDate 1234}))))
    (testing "No overrides, mapping, no change"
      (is (= {:state :closed :ts sent-ts}
             (calculate-state (assoc cfg :state-mapping {"lupa käsitelty, siirretty päättäjälle" :closed})
                              doc {:history    [{:state "closed"}]
                                   :permitType "R"}))))
    (testing "No overrides, mapping, change"
      (is (= {:state :open :ts sent-ts}
             (calculate-state (assoc cfg :state-mapping {"lupa käsitelty, siirretty päättäjälle" :open})
                              doc {:history    [{:state "submitted"}]
                                   :permitType "R"})))
      (is (= {:state :foremanVerdictGiven}
             (calculate-state (assoc cfg :state-mapping {"lupa käsitelty, siirretty päättäjälle" :open})
                              doc {:history          [{:state "submitted"}]
                                   :permitType       "R"
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                   :verdictDate      1234})))
      (is (= {:state :closed :ts now-ts}
             (calculate-state (merge cfg
                                     {:force-terminal-state? true
                                      :state-mapping         {"lupa käsitelty, siirretty päättäjälle" :open}})
                              doc {:history     [{:state "submitted"}]
                                   :permitType  "R"
                                   :verdictDate 1234})))
      (is (= {:state :canceled :ts sent-ts}
             (calculate-state (merge cfg
                                     {:force-terminal-state? true
                                      :state-mapping         {"lupa käsitelty, siirretty päättäjälle" #{:canceled}}})
                              doc {:history          [{:state "submitted"}]
                                   :permitType       "R"
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                   :verdictDate      1234}))))
    (testing "Overrides, no change"
      (is (= {:state :inUse :ts now-ts}
             (calculate-state {:state-overrides {"hello" :inUse
                                                 "world" :extinct}
                               :state-mapping   {"lupa evätty" :closed}}
                              doc {:history    [{:state "inUse" :ts submitted-ts}]
                                   :permitType "R"})))
      (is (= {:state :inUse :ts now-ts}
             (calculate-state {:state-overrides {"hello" :inUse
                                                 "world" :extinct}}
                              doc {:history    [{:state "inUse"}]
                                   :permitType "R"}))))
    (testing "Overrides, illegal state is ignored"
      (is (= {:state :sent :ts sent-ts}
             (calculate-state {:state-overrides {"hello" :inUse
                                                 "world" :extinct}}
                              doc {:history          [{:state "submitted"}]
                                   :permitType       "R"
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}))))
    (testing "Overrides, future date is ignored"
      (is (= {:state :sent :ts sent-ts}
             (calculate-state {:state-overrides {"hello" {:state :inUse :ts "1.1.2025"}
                                                 "world" :extinct}}
                              doc {:history          [{:state "submitted"}]
                                   :permitType       "R"}))))
    (testing "Overrides, change"
      (is (= {:state :inUse :ts now-ts}
             (calculate-state {:state-overrides {"hello" :inUse
                                                 "world" :extinct}
                               :state-mapping   {"lupa evätty" :closed}}
                              doc {:history    [{:state "submitted"}]
                                   :permitType "R"})))
      (is (= {:state :inUse :ts (date/timestamp "5.2.2024")}
             (calculate-state {:state-overrides {"hello" {:state :inUse :ts "5.2.2024"}
                                                 "world" :extinct}}
                              doc {:history    [{:state "verdictGiven"}]
                                   :permitType "R"})))
      (is (= {:state :verdictGiven}
             (calculate-state {:state-overrides {"hello" :submitted
                                                 "world" :extinct}}
                              doc {:history     [{:state "submitted"}]
                                   :permitType  "R"
                                   :verdictDate 1234})))
      (is (= {:state :foremanVerdictGiven :ts now-ts}
             (calculate-state {:state-overrides {"hello" #{:inUse :verdictGiven :foremanVerdictGiven}}}
                              doc {:history          [{:state "submitted"}]
                                   :permitType       "R"
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}})))
      (is (= {:state :foremanVerdictGiven}
             (calculate-state {:state-overrides {"hello" #{:inUse :sent}}}
                              doc {:history          [{:state "submitted"}]
                                   :permitType       "R"
                                   :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                   :verdictDate      1234})))

      (testing "Overrides override `force-terminal-state?`"
        (is (= {:state :verdictGiven}
               (calculate-state {:force-terminal-state? true
                                 :state-overrides       {"hello" :submitted
                                                         "world" :extinct}}
                                doc {:history     [{:state "submitted"}]
                                     :permitType  "R"
                                     :verdictDate 1234}))))
      (is (= {:state :extinct :ts now-ts}
             (calculate-state {:force-terminal-state? true
                               :state-overrides       {"hello" :extinct
                                                       "world" :submitted}}
                              doc {:history     [{:state "submitted"}]
                                   :permitType  "R"
                                   :verdictDate 1234}))))))

(def merge-histories #'conv-st/merge-histories)

(deftest merge-histories-test
  (testing "Simple merges"
    (is (= [] (merge-histories [] [])))
    (is (= [] (merge-histories nil nil)))
    (is (= [{:state "two" :ts 10}]
           (merge-histories [{:state "one" :ts 1}] [{:state "two" :ts 10}]))))
  (testing "With non-states"
    (is (= [{:spot "first" :ts 10} {:state "one" :ts 100}
            {:state "two" :ts 200} {:spot "fourth" :ts 250}
            {:state "three" :ts 300} {:state "four" :ts 400}
            {:spot "last" :ts 500}]
           (merge-histories [{:spot "first" :ts 10} {:spot "fourth" :ts 250}
                             {:spot "last" :ts 500}]
                            [{:state "one" :ts 100} {:state "two" :ts 200}
                             {:state "three" :ts 300} {:state "four" :ts 400}])))
    (is (= [{:hello "world"} {:state "one" :ts 10}]
           (merge-histories [{:state "foo"} {:hello "world"}]
                            [{:state "one" :ts 10}])))
    (is (= [{:spot "first" :ts 10} {:spot "second" :ts 11}
            {:state "one" :ts 100} {:state "two" :ts 200}
            {:spot "third" :ts 250} {:spot "fourth" :ts 260}
            {:state "three" :ts 300} {:state "four" :ts 400}
            {:spot "fifth" :ts 400} {:spot "sixth" :ts 500}]
           (merge-histories (shuffle [{:spot "first" :ts 10} {:spot "second" :ts 11}
                                      {:state "foo" :ts 120} {} {:state "bar" :ts 500}
                                      {:spot "third" :ts 250} nil {:spot "fourth" :ts 260}
                                      {:spot "fifth" :ts 400} {:spot "sixth" :ts 500}])
                            [{:state "one" :ts 100} {:state "two" :ts 200}
                             {:state "three" :ts 300} {:state "four" :ts 400}])))))

(def fh-updates #'conv-st/fix-history--updates)

(deftest fix-history--updates-test
  (testing "No updates"
    (is (nil? (fh-updates {:history [{:state "submitted" :ts 1} {:hello "world"}]
                           :state   "submitted"}
                          [{:state :submitted :ts 20}]
                          :submitted))))
  (testing "Updates"
    (is (= {$unset {:submitted true}
            $set   {:history [{:hello "world" :ts 10}
                              {:state :sent :ts 20}]
                    :state   :sent
                    :sent    20}}
           (fh-updates {:history [{:state "created" :ts 1}
                                  {:state "open" :ts 2}
                                  {:state "submitted" :ts 3}
                                  {:hello "world" :ts 10}]
                        :state   "submitted"}
                       [{:state :sent :ts 20}]
                       :sent)))
    (is (= {$unset {:submitted true}
            $set   {:history [{:hello "world" :ts 10}
                              {:state :sent :ts 20}
                              {:state :canceled :ts 30}
                              {:state :closed :ts 40}]
                    :state   :closed
                    :sent    20 :closed 40}}
           (fh-updates {:history [{:state "created" :ts 1}
                                  {:state "open" :ts 2}
                                  {:state "submitted" :ts 3}
                                  {:hello "world" :ts 10}]
                        :state   "submitted"}
                       [{:state :sent :ts 20}
                        {:state :canceled :ts 30}
                        {:state :closed :ts 40}]
                       :closed)))
    (is (= {$unset {:submitted true}
            $set   {:history [{:hello "world" :ts 10}
                              {:state :sent :ts 20}
                              {:state :canceled :ts 30}]
                    :state   :canceled
                    :sent    20 :canceled 30}}
           (fh-updates {:history [{:state "created" :ts 1}
                                  {:state "open" :ts 2}
                                  {:state "submitted" :ts 3}
                                  {:hello "world" :ts 10}]
                        :state   "submitted"}
                       [{:state :sent :ts 20}
                        {:state :canceled :ts 30}]
                       :canceled)))))
