(ns lupapalvelu.conversion.state-itest
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.util :as util]
            [sade.xml :as xml]))

(def jarvenpaa-xml
  "Attachments are irrelevant for this test, so let's remove them and make the test
  quicker."
  (-> (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")
      slurp
      (xml/parse-string "utf8")
      (enlive/at [:yht:linkkiliitteeseen] nil)
      xml/element-to-string))

(def jarvenpaa-id "14-0241-R 3")

(def app-id "LP-186-2014-90001")

(defn exec-conversion [cmd filepath]
  (conv/execute-conversion-command {:cmd      cmd
                                    :cfgfile  filepath
                                    :cmd-help (constantly nil)
                                    :system   (test-system/get-common-components)}))

(defn store-and-convert [test-dir xml & kvs]
  (let [filepath     (conv-test/tmp-edn-file test-dir)
        xml-filename (format "%s.xml" (random-uuid))]
    (-> (fs/path test-dir xml-filename)
        str
        (spit (util/pcond-> xml
                sequential? xml/element-to-string)))
    (apply conv-cfg/write-configuration filepath "186-R"
           :overwrite? true :glob xml-filename kvs)
    (exec-conversion :store filepath)
    (exec-conversion :convert filepath)
    (mongo/update-by-query :conversion {:LP-id app-id}
                           {$set {:history []}})))

(defn cfg-and-exec [test-dir cmd & kvs]
  (let [filepath (conv-test/tmp-edn-file test-dir)]
    (apply conv-cfg/write-configuration filepath "186-R" kvs)
    (exec-conversion cmd filepath)))

(defn check-history [& states]
  (let [{:keys [history] :as app} (mongo/by-id :applications app-id)]
    (facts "history check"
      (count states) => (count history)
      (doseq [[state item] (zipmap states history)
              :let         [ts-key (app-state/timestamp-key state)]]
        (fact {:midje/description state}
          (:state item) => (name state)
          (when ts-key
            (fact {:midje/description ts-key}
              (app ts-key) => (:ts item))))))
    (fact "Application state"
      (:state (app-state/last-history-item app)) => (:state app)
      (name (last states)) => (:state app))))

(defn push-state [user-id]
  (mongo/update-by-id :applications app-id
                      {$push {:history {:state :hello
                                        :ts    (now)
                                        :user  {:id user-id}}}
                       $set  {:state :hello}}))

(def push-user-state (partial push-state "rando"))
(def push-batchrun-state (partial push-state "batchrun-user"))
(def push-conversion-state (partial push-state "conversion-user"))

(defn pop-state [user-id]
  (let [state (->> (mongo/by-id :applications app-id)
                   :history butlast last :state)]
    (mongo/update-by-id :applications app-id
                        {$pull {:history {:user.id user-id}}
                         $set  {:state state}})))

(def pop-user-state (partial pop-state "rando"))

(defn doc-ops []
  (->> (conv-doc/document "186-R" jarvenpaa-id)
       :history
       (map :op)))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components)
  (let [test-dir          (conv-test/mk-test-dir)
        store-and-convert (partial store-and-convert test-dir jarvenpaa-xml)
        exec-fix-history  (partial cfg-and-exec test-dir :fix-history)
        fix-states        (partial cfg-and-exec test-dir :fix-states)
        store-and-fix     (fn [& kvs]
                            (store-and-convert)
                            (apply fix-states kvs))]
    (against-background
        [(after :contents (conv-test/rm-test-dir test-dir))]
      (with-local-actions
        (facts "Conversion"
          (fact "Initial conversion, all defaults"
            (store-and-convert)
            (check-history :submitted :verdictGiven :constructionStarted))

          (fact "Force terminal state"
            (store-and-convert :force-terminal-state? true)
            (check-history :submitted :verdictGiven :constructionStarted :closed))

          (fact "Authority can change the closed converted application location"
            (command raktark-jarvenpaa :change-location :id app-id
                     :x 394858 :y 6705161
                     :address "Somewhere"
                     :propertyId "18600101540012") => ok?
            (command raktark-jarvenpaa :change-address :id app-id
                     :address " School Road ") => ok?
            (mongo/any? :applications {:_id        app-id
                                       :state      :closed
                                       :address    "School Road"
                                       :location   [394858 6705161]
                                       :propertyId "18600101540012"})
            => true)
          (fact "Operation can be replaced"
            (query raktark-jarvenpaa :addable-operations :id app-id)
            => ok?
            (let [{op :primaryOperation} (mongo/by-id :applications app-id [:primaryOperation])]
              (:name op) => "pientalo"
              (command raktark-jarvenpaa :replace-operation :id app-id
                       :opId (:id op)
                       :operation "kerrostalo-rivitalo") => ok?
              (mongo/any? :applications {:_id                   app-id
                                         :state                 :closed
                                         :address               "School Road"
                                         :location              [394858 6705161]
                                         :propertyId            "18600101540012"
                                         :primaryOperation.name :kerrostalo-rivitalo})
              => true
              ;; Attachments must be explicitly removed since operation replacement adds
              ;; new (empty) user attachments.
              (mongo/update-by-id :applications app-id {$set {:attachments []}})))

          (fact "State override"
            (store-and-convert :state-overrides {jarvenpaa-id :inUse})
            (check-history :submitted :verdictGiven :constructionStarted :inUse))
          (fact "State override with good date"
            (store-and-convert :state-overrides {jarvenpaa-id {:state :inUse :ts "5.2.2024"}})
            (check-history :submitted :verdictGiven :constructionStarted :inUse)
            (-> (mongo/by-id :applications app-id [:history]) :history last)
            => (just {:state "inUse" :ts (date/timestamp "5.2.2024")
                      :user  (contains {:id "conversion-user"})}))
          (fact "State override with bad date is ignored"
            (store-and-convert :state-overrides
                               {jarvenpaa-id {:state :inUse
                                              :ts    (date/timestamp (date/plus (date/now) :day))}})
            (check-history :submitted :verdictGiven :constructionStarted))
          (fact "State override vs. force terminal state"
            (store-and-convert :state-overrides {jarvenpaa-id :inUse}
                               :force-terminal-state? true)
            (check-history :submitted :verdictGiven :constructionStarted :inUse))
          (fact "State override vs. implicit verdict state"
            (store-and-convert :state-overrides {jarvenpaa-id :sent})
            (check-history :submitted :verdictGiven :constructionStarted :sent :verdictGiven))

          (fact "State mapping"
            (store-and-convert :state-mapping {"rakennustyöt aloitettu" :appealed})
            (check-history :submitted :verdictGiven :appealed))
          (fact "State mapping vs. force terminal state"
            (store-and-convert :state-mapping {"rakennustyöt aloitettu" :appealed}
                               :force-terminal-state? true)
            (check-history :submitted :verdictGiven :appealed :closed))
          (fact "State mapping vs. implicit verdict state"
            (store-and-convert :state-mapping {"rakennustyöt aloitettu" :complementNeeded
                                               "lupa hyväksytty"        :sent})
            (check-history :submitted :sent :complementNeeded :verdictGiven))

          (fact "State mapping and override"
            (store-and-convert :state-overrides {jarvenpaa-id :inUse}
                               :state-mapping {"rakennustyöt aloitettu" :complementNeeded
                                               "lupa hyväksytty"        :sent})
            (check-history :submitted :sent :complementNeeded :inUse))))

      (facts "States (the main tests are in `conversion-runner-itest` namespace)"

        (fact "User states -> do nothing"
          (push-user-state)
          (exec-fix-history)
          (doc-ops) => []
          (pop-user-state))

        (fact "Default"
          (check-history :submitted :sent :complementNeeded :inUse)
          (exec-fix-history)
          (check-history :submitted :verdictGiven :constructionStarted)
          (doc-ops) => [:fix-history]
          (exec-fix-history)
          (check-history :submitted :verdictGiven :constructionStarted)
          (doc-ops) => [:fix-history])

        (fact "Force terminal state"
          (exec-fix-history :force-terminal-state? true)
          (check-history :submitted :verdictGiven :constructionStarted :closed)
          (doc-ops) => [:fix-history :fix-history])

        (fact "State override"
          (exec-fix-history :state-overrides {jarvenpaa-id :inUse})
          (check-history :submitted :verdictGiven :constructionStarted :inUse))
        (fact "State override vs. force terminal state"
          (exec-fix-history :state-overrides {jarvenpaa-id :inUse}
                            :force-terminal-state? true)
          (check-history :submitted :verdictGiven :constructionStarted :inUse))
        (fact "State override vs. implicit verdict state"
          (exec-fix-history :state-overrides {jarvenpaa-id :sent})
          (check-history :submitted :verdictGiven :constructionStarted :sent :verdictGiven))

        (fact "State mapping"
          (exec-fix-history :state-mapping {"rakennustyöt aloitettu" :appealed})
          (check-history :submitted :verdictGiven :appealed))
        (fact "State mapping vs. force terminal state"
          (exec-fix-history :state-mapping {"rakennustyöt aloitettu" :appealed}
                            :force-terminal-state? true)
          (check-history :submitted :verdictGiven :appealed :closed))
        (fact "State mapping vs. implicit verdict state"
          (exec-fix-history :state-mapping {"rakennustyöt aloitettu" :complementNeeded
                                            "lupa hyväksytty"        :sent})
          (check-history :submitted :sent :complementNeeded :verdictGiven))

        (fact "State mapping and override"
          (exec-fix-history :state-overrides {jarvenpaa-id :inUse}
                            :state-mapping {"rakennustyöt aloitettu" :complementNeeded
                                            "lupa hyväksytty"        :sent})
          (check-history :submitted :sent :complementNeeded :inUse)))

      (facts "Fix states"
        (let [check-last (fn [& [state]]
                           (apply check-history
                                  (cond-> [:submitted :verdictGiven :constructionStarted]
                                    state (conj state))))]
          (fact "Default, no changes"
            (store-and-fix)
            (check-last)
            (doc-ops) => []
            (:reset-state (conv-doc/document "186-R" jarvenpaa-id))
            => :constructionStarted)

          (fact "User states -> do nothing"
            (push-user-state)
            (fix-states :force-terminal-state? true)
            (check-last :hello)
            (pop-user-state)
            (doc-ops) => [])

          (fact "Batchrun state that is not reset-state -> do nothing"
            (check-last)
            (push-batchrun-state)
            (fix-states)
            (check-last :hello)
            (doc-ops) => [])

          (fact "Batchrun state that is reset-state"
            (check-last :hello)
            (mongo/update-by-query :conversion {:LP-id app-id}
                                   {$set {:reset-state :hello}}) => 1
            (fact "Backend id not in targets"
              (fix-states :ids ["99-4321-R"])
              (check-last :hello)
              (doc-ops) => [])

            (fix-states)
            (let [{:keys [history state]} (mongo/by-id :applications app-id
                                                       [:history :state])]
              (last history) => (contains {:state "constructionStarted"
                                           :user  (contains {:id "conversion-user"})})
              state => "constructionStarted")
            (doc-ops) => [:fix-states])

          (fact "Conversion state"
            (push-conversion-state)
            (fix-states :force-terminal-state? true)
            (let [{:keys [history state]} (mongo/by-id :applications app-id
                                                       [:history :state])]
              (last history) => (contains {:state "closed"
                                           :user  (contains {:id "conversion-user"})})
              state => "closed")
            (doc-ops) => [:fix-states :fix-states])

          (fact "Force terminal state"
            (store-and-fix :force-terminal-state? true)
            (check-last :closed)
            (doc-ops) => [:fix-states])

          (fact "State override"
            (store-and-fix :state-overrides {jarvenpaa-id :inUse})
            (check-last :inUse))
          (fact "State override vs. force terminal state"
            (store-and-fix :state-overrides {jarvenpaa-id :inUse}
                           :force-terminal-state? true)
            (check-last :inUse))

          (fact "State override vs. implicit verdict state"
            (store-and-fix :state-overrides {jarvenpaa-id :sent})
            (check-last :verdictGiven))

          (fact "State override vs. illegal state"
            (store-and-fix :state-overrides {jarvenpaa-id :ready})
            (check-last)

            (store-and-fix :state-overrides {jarvenpaa-id #{:foremanVerdictGiven :answered}})
            (check-last))

          (fact "State mapping"
            (store-and-fix :state-mapping {"rakennustyöt aloitettu" :appealed})
            (check-last :appealed))
          (fact "State mapping vs. force terminal state"
            (store-and-fix :state-mapping {"rakennustyöt aloitettu" :appealed}
                           :force-terminal-state? true)
            (check-last :closed))
          (fact "State mapping vs. implicit verdict state"
            (store-and-fix :state-mapping {"rakennustyöt aloitettu" :complementNeeded
                                           "lupa hyväksytty"        :sent})
            (check-last :verdictGiven))

          (fact "State mapping and override"
            (store-and-fix :state-overrides {jarvenpaa-id :inUse}
                           :state-mapping {"rakennustyöt aloitettu" :complementNeeded
                                           "lupa hyväksytty"        :sent})
            (check-last :inUse))

          (fact "Bad application data or mongo mishap, does not throw"
            (store-and-convert)
            (mongo/remove-by-id :applications app-id)
            (fix-states) => nil
            (mongo/by-id :applications app-id) => nil))))))
