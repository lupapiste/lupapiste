(ns lupapalvelu.conversion.other-itest
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.conversion.config :as conv-cfg]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.conversion.other :as other]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer [give-local-legacy-verdict]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(set! *warn-on-reflection* true)

(defn test-xml []
  (conv-test/read-xml "dev-resources/krysp/verdict-r.xml"))

(testable-privates lupapalvelu.conversion.other
                   make-query applications-to-send
                   other--config)

(defn with-vrk-id
  ([vrk-id backend-id]
   (cond-> (enlive/at (test-xml)
                      [:luvanTunnisteTiedot :LupaTunnus :VRKLupatunnus]
                      (enlive/content vrk-id))
     backend-id (enlive/at [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus]
                           (enlive/content backend-id))))
  ([vrk-id] (with-vrk-id vrk-id nil)))

(defn run-backend-ids-update [app-id & kvs]
  (other/execute-other-command (merge {:cmd             :backend-ids
                                       :organization-id "753-R"
                                       :system          (test-system/get-common-components)}
                                      (apply hash-map kvs)))
  (mongo/by-id :applications app-id))

(defn vrk-id [{:keys [verdicts]}]
  (some :vrk-lupatunnus verdicts))

(def no-vrk-id (comp nil? vrk-id))

(defn backend-ids [backend-id vrk-id]
  (fn [{:keys [verdicts]}]
    (->> verdicts first ((juxt :kuntalupatunnus :vrk-lupatunnus))
         (= [backend-id vrk-id]))))

(defn create-paper-permit
  "We use the known working KuntaGML message and transfer the created application to `organization-id`."
  [organization-id]
  (let [xml (-> "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"
                io/resource
                conv-test/read-xml)
        bid (xml/get-text xml [:kuntalupatunnus])]
    (with-redefs [krysp/get-application-xml-by-backend-id (constantly xml)]
      (let [{app-id :id} (command raktark-jarvenpaa :create-application-from-previous-permit
                                  :lang "fi"
                                  :propertyId nil
                                  :authorizeApplicants false
                                  :x 0 :y 0
                                  :address "Paper Park"
                                  :organizationId "186-R"
                                  :kuntalupatunnus bid)]
        (fact "Paper permit created"
          app-id => truthy)
        (mongo/update-by-id :applications app-id {$set {:organization organization-id}})
        app-id))))

(facts "make-query"
  (make-query {:organization-id "123-R" :permit-type "R"})
  => {:organization   "123-R" :permitType "R"
      :facta-imported {$ne true}
      :infoRequest    {$ne true}}
  (make-query {:organization-id "123-R" :permit-type "R"
               :application-ids ["LP-1" "LP-2"]
               :created         ["1.5.2023" "2.5.2023"]
               :sent            1684329205024
               :verdict-date    ["5.5.2023"]
               :closed          ["2023-05-09" 1683666000000]}
              {:hello :world})
  => {:_id            {$in ["LP-1" "LP-2"]}
      :closed         {$gte 1683579600000 $lte 1683752399999}
      :created        {$gte 1682888400000 $lte 1683061199999}
      :facta-imported {$ne true}
      :infoRequest    {$ne true}
      :hello          :world
      :organization   "123-R"
      :permitType     "R"
      $or             [{:sent {$gte 1684270800000 $lte 1684357199999}}
                       {:transfers {$elemMatch {:timestamp {$gte 1684270800000 $lte 1684357199999}
                                                :type      :exported-to-backing-system}}}]
      :verdictDate    {$gte 1683234000000 $lte 1683320399999}}
  (make-query {:organization-id    "123-R" :permit-type     "R"
               :include-converted? true    :application-ids ["hello" "world"]})
  => {:organization "123-R" :permitType "R"
      :infoRequest  {$ne true}
      :_id          {$in ["hello" "world"]}})

(facts "other--config"
  (other--config {:cmd :backend-ids :organization-id "123-R" :system {}})
  => {:organization-id "123-R" :permit-type "R" :system {}}
  (other--config {:cmd :send-states :cfgfile "config.edn" :system {}})
  => {:organization-id "123-R" :permit-type "P"
      :application-ids ["LP-123-2023-00001" "LP-123-2023-00002"
                        "LP-123-2023-00003" "LP-123-2023-00004"]
      :system          {}}
  (provided
    (util/read-edn-file "config.edn")
    => {:organization-id "123-R "
        :permit-type     "P "
        :application-ids [" LP-123-2023-00001" "LP-123-2023-00002"
                          "LP-123-2023-00002 " "LP-123-2023-00003"
                          " LP-123-2023-00003 " "LP-123-2023-00004"]})
  (other--config {:cmd             :send-states
                  :cfgfile         "config.edn"
                  :permit-type     "P"
                  :application-ids [" LP-123-2022-00123 "
                                    " LP-123-2022-00124 "
                                    " LP-123-2022-00123 "]
                  :system          {}})
  => {:organization-id "123-R" :permit-type "P"
      :application-ids ["LP-123-2022-00123" "LP-123-2022-00124"]
      :system          {}}
  (provided
    (util/read-edn-file "config.edn")
    => {:organization-id "123-R "
        :permit-type     "P "
        :application-ids [" LP-123-2023-00001" "LP-123-2023-00002"
                          "LP-123-2023-00002 " "LP-123-2023-00003"
                          " LP-123-2023-00003 " "LP-123-2023-00004"]}))

(defn acked-messages [target-count]
  (reduce (fn [_ _]
            (let [q [:integration-messages {:acknowledged {$ne nil}}]]
              (if (= target-count (apply mongo/count q))
                (reduced (doall (apply mongo/select q)))
                (pubsub-wait 2))))
          nil
          (range 20)))

(defn dmcity-url
  []
  (str (server-address) "/dev/dummy-dmcity"))

(defn make-url [path]
  (format "%s/%s" (dmcity-url) (name path)))

(defn kuntagml-msg?
  [kuntagml-re application-id msg-type path]
  (fn [{:keys [application messageType url data]}]
    (and
      (= application {:id           application-id
                      :organization "753-R"})
      (= messageType (str "KuntaGML " (name msg-type)))
      (= url (make-url path))
      (re-find kuntagml-re data))))

(def toimenpide? (partial kuntagml-msg? #"(?i)toimenpidetieto"))
(def paatos? (partial kuntagml-msg? #"(?i)paatostieto"))
(def katselmus? (partial kuntagml-msg? #"(?i)katselmustieto"))
(def lausunto? (partial kuntagml-msg? #"(?i)lausuntotieto"))

(defn application-msg? [& args]
  (fn [msg]
    (and ((apply toimenpide? args) msg)
         (not ((apply paatos? args) msg))
         (not ((apply katselmus? args) msg)))))

(defn verdict-msg? [& args]
  (fn [msg]
    (and ((apply toimenpide? args) msg)
         ((apply paatos? args) msg)
         (not ((apply katselmus? args) msg)))))

(defn with-statement? [msg-pred & args]
  (fn [msg]
    (and ((apply msg-pred args) msg)
         ((apply lausunto? args) msg))))

(defn with-operation-count [n msg-pred & args]
  (fn [msg]
    (and (some-> (:data msg)
                 (xml/parse-string "utf8")
                 (xml/select :rakval:toimenpidetieto)
                 count (= n))
         ((apply msg-pred args) msg))))

(defn review-msg? [application-id msg-type path kuntagml-re]
  (fn [msg]
    (and ((kuntagml-msg? kuntagml-re application-id msg-type path) msg)
         (not ((toimenpide? application-id msg-type path) msg))
         (not ((paatos? application-id msg-type path) msg)))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (with-local-actions
    (let [test-dir      (conv-test/mk-test-dir)
          testfile-name (fs/file-name conv-test/testfile)
          testfile-path (str (fs/path test-dir testfile-name))]
      (against-background
          [(after :contents (conv-test/rm-test-dir test-dir))]
        (facts "backend-ids: vrk-id"
          (let [{app-id :id} (create-and-submit-application pena
                                                            :operation "pientalo"
                                                            :address "Other Offsite 10")]
            (fact "No sent timestamp, no kuntalupatunnus, is not updated"
              (run-backend-ids-update app-id :application-ids [app-id])
              => no-vrk-id)
            (fact "Fetched verdict contains vrk-id"
              (command sonja :check-for-verdict :id app-id) => ok?
              (provided
                (krysp/get-application-xml-by-application-id anything)
                => (with-vrk-id "FIRST"))
              (mongo/any? :applications {:_id                     app-id
                                         :verdicts.vrk-lupatunnus "FIRST"})
              => true)
            (fact "No need to update"
              (vrk-id (run-backend-ids-update app-id :application-ids [app-id]))
              => "FIRST"
              (provided
                (krysp/get-application-xml-by-backend-id anything anything false)
                => (with-vrk-id "SECOND") :times 0))
            (fact "Backend-id mismatch, no update"
              (mongo/update-by-id :applications
                                  app-id
                                  {$unset {:verdicts.0.vrk-lupatunnus true}})
              (run-backend-ids-update app-id :application-ids [app-id])
              => no-vrk-id
              (provided
                (krysp/get-application-xml-by-backend-id anything anything false)
                => (with-vrk-id "THIRD" "different")))
            (facts "Filter does not match the the application"
              (doseq [args [[:organization-id "123-R"]
                            [:permit-type "P"]
                            [:application-ids ["LP-321-2022-00022"]]
                            [:created "1.5.2023"]
                            [:sent [1300000000000 1500000000000]]
                            [:verdict-date "17.5.2023"]
                            [:closed "2010-12-04"]]]
                (fact {:midje/description (str args)}
                  (apply run-backend-ids-update app-id args) => no-vrk-id)))
            (fact "Successful update"
              (run-backend-ids-update app-id :application-ids [app-id])
              => (contains {:_sheriff-notes (just (just {:note    "Other backend-ids: VRK-lupatunnus FOURTH"
                                                         :created pos?}))
                            :verdicts       (just (contains {:vrk-lupatunnus "FOURTH"}))})
              (provided
                (krysp/get-application-xml-by-backend-id anything anything false)
                => (with-vrk-id "FOURTH")))

            (fact "Successful update with config file. Command line has higher priority."
              (util/write-edn-file testfile-path {:organization-id "753-R"
                                                  :permit-type     "P"})
              (mongo/update-by-id :applications app-id
                                  {$unset {:_sheriff-notes            true
                                           :verdicts.0.vrk-lupatunnus true}})
              (run-backend-ids-update app-id
                                      :cfgfile testfile-path
                                      :permit-type "R")
              => (contains {:_sheriff-notes (just (just {:note    "Other backend-ids: VRK-lupatunnus FIFTH"
                                                         :created pos?}))
                            :verdicts       (just (contains {:vrk-lupatunnus "FIFTH"}))})
              (provided
                (krysp/get-application-xml-by-backend-id anything anything false)
                => (with-vrk-id "FIFTH")))
            (mongo/remove-by-id :applications app-id)))

        (facts "backend-ids: backend-id"
          (let [{app-id :id} (create-and-submit-application pena
                                                            :operation "pientalo"
                                                            :address "Backend Boulevard 2")]
            (fact "Add sent timestamp and run update"
              (mongo/update-by-id :applications app-id {$set {:sent (date/timestamp (date/now))}})
              (run-backend-ids-update app-id :application-ids [app-id])
              => (backend-ids "SIXTH" "VRK-SIXTH")
              (provided
                (krysp/get-application-xml-by-application-id anything false)
                => (with-vrk-id "VRK-SIXTH" "SIXTH")))

            (fact "No need to update"
              (run-backend-ids-update app-id :application-ids [app-id])
              => (backend-ids "SIXTH" "VRK-SIXTH")
              (provided
                (krysp/get-application-xml-by-backend-id anything anything false)
                => nil :times 0
                (krysp/get-application-xml-by-application-id anything false)
                => nil :times 0))

            (fact "Successful update via exported"
              (mongo/update-by-id :applications app-id
                                  {$unset {:_sheriff-notes true
                                           :sent           true
                                           :verdicts       true}
                                   $set   {:transfers [{:hii :hoo}
                                                       {:type :exported-to-backing-system}]}})
              (run-backend-ids-update app-id :application-ids [app-id])
              => (backend-ids "RARE" "VRK-RARE")
              (provided
                (krysp/get-application-xml-by-application-id anything false)
                => (with-vrk-id "VRK-RARE" "RARE")))

            (let [app-id (create-app-id pena :operation "aita"
                                        :address "Workless Way 10")]
              (facts "Filter does not match the the application"
                (doseq [args [[:organization-id "123-R"]
                              [:permit-type "P"]
                              [:application-ids ["LP-321-2022-00022"]]
                              [:created "1.5.2023"]
                              [:sent [1300000000000 1500000000000]]
                              [:verdict-date "17.5.2023"]
                              [:closed "2010-12-04"]]]
                  (fact {:midje/description (str args)}
                    (apply run-backend-ids-update app-id args) => no-vrk-id)))
              (mongo/remove-by-id :applications app-id))

            (mongo/remove-by-id :applications app-id)))

        (let [edn-path        (str (fs/path test-dir "other-send.edn"))
              _               (conv-cfg/write-other-configuration edn-path "753-R")
              cfg             (-> (util/read-edn-file edn-path)
                                  (assoc :system (test-system/get-common-components)))
              {app-id1 :id}   (create-and-submit-application pena
                                                             :address "Send Strasse 1")
              {app-id2 :id}   (create-and-submit-application pena
                                                             :address "Send Strasse 2")
              {app-id3 :id}   (create-and-submit-application pena
                                                             :address "Send Strasse 3")
              {app-id4 :id}   (create-and-submit-application pena
                                                             :address "Send Strasse 4")
              draft-id        (create-app-id pena :address "Draft Drive")
              info-id         (create-app-id pena :address "Information Inn"
                                             :infoRequest true)
              paper-id        (create-paper-permit "753-R")
              {cancel-id :id} (create-and-submit-application pena
                                                             :address "Cancel Canyon")]
          (facts "Prepare organization and applications"
            (command pena :add-operation :id app-id4 :operation "aita") => ok?
            (command pena :update-doc :id app-id4 :collection "documents"
                     :doc (->> (mongo/by-id :applications app-id4 [:documents])
                               :documents
                               (util/find-first (util/fn-> :schema-info :op :name (= "aita")))
                               :id)
                     :updates [["kuvaus" "No trespassing!"]]) => ok?
            (give-local-legacy-verdict sonja app-id4 :R true) => truthy

            (mongo/update-by-id :organizations
                                "753-R"
                                {$set {:krysp.R.url (str (server-address) "/dev/krysp")}})
            (command sonja :request-for-statement :id app-id3
                     :functionCode nil
                     :dueDate (-> (date/today) (date/plus :month) date/timestamp)
                     :selectedPersons [{:email (min-util/email-for-key ronja)
                                        :name  "Ronja"
                                        :text  "Check this out"}]) => ok?
            (command ronja :give-statement :id app-id3
                     :statementId (-> (mongo/by-id :applications app-id3)
                                      :statements first :id)
                     :status "puollettu"
                     :text "Loihen lausumahan."
                     :lang "fi") => ok?

            (command sonja :check-for-verdict :id app-id3) => ok?
            (command admin :set-organization-scope-pate-value
                     :permitType "R"
                     :municipality "753"
                     :value true) => ok?

            (command sonja :update-app-bulletin-op-description :id app-id2
                     :description "Bulletin Point") => ok?
            (command sonja :approve-application :id app-id2
                     :lang "fi") => ok?
            (command sonja :cancel-application :id cancel-id
                     :lang "fi"
                     :text "Nope") => ok?

            (let [{:keys [tasks]} (mongo/by-id :applications app-id3 [:tasks])]
              (doseq [{task-id :id} (filter (util/fn-> :schema-info :name (= "task-katselmus"))
                                            tasks)]
                (command sonja :update-task :id app-id3
                         :doc task-id
                         :updates [["katselmus.tila" "lopullinen"]
                                   ["katselmus.pitoPvm" "14.06.2023"]
                                   ["katselmus.pitaja" "Auburn Authority"]]) => ok?
                (command sonja :review-done :id app-id3
                         :lang "fi"
                         :taskId task-id) => ok?)
              (command sonja :create-task :id app-id3
                       :taskName "Final Frontier"
                       :schemaName "task-katselmus"
                       :taskSubtype "loppukatselmus") => ok?
              (mongo/update-by-query :applications
                                     {:_id   app-id3
                                      :tasks {$elemMatch {:taskname :Aloituskokous
                                                          :state    :sent}}}
                                     {$set {:tasks.$.schema-info.name    :task-katselmus-backend
                                            :tasks.$.schema-info.subtype :review-backend}})
              => 1
              (:state (mongo/by-id :applications app-id3 [:state]))
              => "constructionStarted"

              (let [foreman-task    (util/find-first (util/fn-> :schema-info :subtype (= "foreman"))
                                                     tasks)
                    {tj-app-id :id} (command sonja :create-foreman-application :id app-id3
                                             :taskId (:id foreman-task)
                                             :foremanRole (get-in foreman-task
                                                                  [:data :kuntaRoolikoodi :value])
                                             :foremanEmail (min-util/email-for-key mikko))]
                (command sonja :change-permit-sub-type :id tj-app-id
                         :permitSubtype "tyonjohtaja-hakemus") => ok?
                (command sonja :submit-application :id tj-app-id) => ok?))

            (command admin :upsert-dmcity-configuration
                     :organizationId "753-R"
                     :configuration {:url      (dmcity-url) :vault    "test-vault"
                                     :username "test-user"  :password "test-secret"})
            => ok?
            (mongo/update-by-id :organizations "753-R"
                                {$set {:dmcity.targets {:state      {:state :MyState}
                                                        :R          {:application :MyApplication
                                                                     :review      :MyReview}
                                                        :conversion {:enabled true
                                                                     :R       :MyConversion}}}})

            (fact "Configuration valid"
              (:configuration (query admin :dmcity-configuration :organizationId "753-R"))
              => truthy))

          (facts "applications-to-send"
            (map :id (applications-to-send cfg))
            => (just app-id1 app-id2 app-id3 app-id4 paper-id cancel-id
                     :in-any-order)
            (map :id (applications-to-send (assoc cfg :application-ids [])))
            => (just app-id1 app-id2 app-id3 app-id4 paper-id cancel-id :in-any-order)
            (map :id (applications-to-send
                       (assoc cfg :application-ids [app-id3 app-id1 "bad-id" app-id3])))
            => [app-id3 app-id1]
            (fact "Drafts, inforequests and foreman applications are never included"
              (let [{tj-app-id :id
                     :as       tj-app} (mongo/select-one :applications {:permitSubtype :tyonjohtaja-hakemus})]
                (-> tj-app :primaryOperation :name)
                => "tyonjohtajan-nimeaminen-v2"
                (:infoRequest (mongo/by-id :applications info-id)) => true
                (:state (mongo/by-id :applications draft-id)) => "draft"
                (applications-to-send (assoc cfg :application-ids [tj-app-id info-id draft-id]))
                => empty?)))

          (let [good-foo? (just {:client-cfg truthy
                                 :option     :foo})]
            (facts "process-options"
              (other/process-options cfg :foo) => (throws)
              (other/process-options cfg) => (throws)
              (other/process-options (assoc cfg :foo true) :foo) => good-foo?
              (other/process-options (assoc cfg :foo true) :foo :bar) => good-foo?
              (other/process-options (assoc cfg :foo true) :foo :foo) => good-foo?
              (other/process-options (assoc cfg :foo true :bar true) :foo :bar)
              => (throws)))

          (let [exec-other      (fn [cmd & opts]
                                  (other/execute-other-command (merge {:cmd      cmd
                                                                       :cfgfile  edn-path
                                                                       :cmd-help (fn [_ msg] msg)
                                                                       :system   (test-system/get-common-components)}
                                                                      (apply hash-map opts))))
                clear-messages  (fn []
                                  (mongo/remove-many :integration-messages {}))
                all-messages    (fn [n & [purge?]]
                                  (let [msgs (acked-messages n)]
                                    (when-not (false? purge?) (clear-messages))
                                    msgs))
                state-msg?      (fn [application-id state conversion-path?]
                                  (fn [{:keys [messageType url] {:keys [id toState]} :data}]
                                    (and (= id application-id)
                                         (= messageType "state-change")
                                         (= url (make-url (if conversion-path?
                                                            :HakemuksenTilapaivitysKonversio
                                                            :MyState)))
                                         (util/=as-kw (:name toState) state))))
                {tj-app-id :id} (mongo/select-one :applications {:permitSubtype :tyonjohtaja-hakemus})]
            (clear-messages)
            (facts "Bad calls"
              (exec-other :bad) => "No matching clause: :bad"
              (exec-other :send-states :all true :last true)
              => "Mutually exclusive options: all, last"
              (exec-other :send-states) => "No option given."
              (exec-other :send-kuntagml :application true :verdict true :review false)
              => "Mutually exclusive options: application, verdict"
              (exec-other :send-kuntagml) => "No option given.")
            (facts "send-states"
              (fact "Submitted application: all prudent states, only one sent"
                (exec-other :send-states :all true :application-ids [app-id1])
                (all-messages 1) => (just (state-msg? app-id1 :submitted false)))
              (fact "Submitted application: last state"
                (exec-other :send-states :last true :application-ids [app-id1])
                (all-messages 1) => (just (state-msg? app-id1 :submitted false)))
              (fact "Two applications, all states"
                (exec-other :send-states :all true :application-ids [app-id3 app-id2])
                (sort-by :created (all-messages 5))
                => (just (state-msg? app-id3 :submitted true)
                         (state-msg? app-id3 :verdictGiven true)
                         (state-msg? app-id3 :constructionStarted true)
                         (state-msg? app-id2 :submitted false)
                         (state-msg? app-id2 :sent false)))

              (fact "Cancelled application: two states"
                (exec-other :send-states :all true :application-ids [cancel-id])
                (all-messages 2)
                => (just (state-msg? cancel-id :submitted false)
                         (state-msg? cancel-id :canceled false)
                         :in-any-order))

              (fact "Draft and foreman applications, no all nor last states"
                (exec-other :send-states :all true :application-ids [draft-id tj-app-id])
                (exec-other :send-states :last true :application-ids [draft-id tj-app-id])
                (all-messages 0) => empty?))

            (facts "send-kuntagml"
              (facts "Application state is submitted"
                (fact "Application message"
                  (exec-other :send-kuntagml :application true
                              :application-ids [app-id1])
                  (all-messages 1)
                  => (just (application-msg? app-id1 :application :MyApplication)))
                (fact "Verdict message: nope"
                  (exec-other :send-kuntagml :verdict true
                              :application-ids [app-id1])
                  (all-messages 0) => empty?)
                (fact "Review message: nope"
                  (exec-other :send-kuntagml :review true
                              :application-ids [app-id1])
                  (all-messages 0) => empty?)

                (fact "Auto: Application message"
                  (exec-other :send-kuntagml :auto true
                              :application-ids [app-id1])
                  (all-messages 1)
                  => (just (application-msg? app-id1 :application :MyApplication))))

              (facts "Application state is verdictGiven"
                (fact "Application message"
                  (exec-other :send-kuntagml :application true
                              :application-ids [app-id4])
                  (all-messages 1)
                  => (just (application-msg? app-id4 :application :MyApplication)))
                (fact "Verdict message"
                  (exec-other :send-kuntagml :verdict true
                              :application-ids [app-id4])
                  (all-messages 1)
                  => (just (verdict-msg? app-id4 :verdict :MyApplication)))
                (fact "Review message: nope"
                  (exec-other :send-kuntagml :review true
                              :application-ids [app-id4])
                  (all-messages 0) => empty?)

                (fact "Auto: Verdict message"
                  (exec-other :send-kuntagml :auto true
                              :application-ids [app-id4])
                  (all-messages 1)
                  => (just (verdict-msg? app-id4 :verdict :MyApplication))))

              (facts "Application state is constructionStarted, with two backing system verdicts."
                (fact "Application message"
                  (exec-other :send-kuntagml :application true
                              :application-ids [app-id3])
                  (all-messages 1)
                  => (just (with-operation-count 1 with-statement? application-msg?
                             app-id3 :application :MyConversion)))
                (fact "Verdict message"
                  (exec-other :send-kuntagml :verdict true
                              :application-ids [app-id3])
                  (all-messages 2)
                  => (just (with-operation-count 1 with-statement? verdict-msg?
                             app-id3 :verdict :MyConversion)
                           (with-operation-count 1 with-statement? verdict-msg?
                             app-id3 :verdict :MyConversion)))
                (fact "Review messages:"
                  (exec-other :send-kuntagml :review true
                              :application-ids [app-id3])
                  (all-messages 3)
                  => (just (review-msg? app-id3 :review :MyReview
                                        #"(?i)pitaja.*Auburn Authority.*Aloituskokous")
                           (review-msg? app-id3 :review :MyReview
                                        #"(?i)pitaja.*Auburn Authority.*Käyttöönottotarkastus")
                           (review-msg? app-id3 :review :MyReview
                                        #"(?i)pitaja.*Auburn Authority.*loppukatselmus")
                           :in-any-order))

                (fact "Auto: Verdict and review messages"
                  (exec-other :send-kuntagml :auto true
                              :application-ids [app-id3])
                  (all-messages 5)
                  => (just (with-operation-count 1 with-statement? verdict-msg? app-id3 :verdict :MyConversion)
                           (with-statement? verdict-msg? app-id3 :verdict :MyConversion)
                           (review-msg? app-id3 :review :MyReview
                                        #"(?i)pitaja.*Auburn Authority.*Aloituskokous")
                           (review-msg? app-id3 :review :MyReview
                                        #"(?i)pitaja.*Auburn Authority.*Käyttöönottotarkastus")
                           (review-msg? app-id3 :review :MyReview
                                        #"(?i)pitaja.*Auburn Authority.*loppukatselmus")
                           :in-any-order)))

              (fact "Send paper permit KuntaGML"
                (exec-other :send-kuntagml :application true
                            :application-ids [paper-id])
                (all-messages 1)
                => (just (kuntagml-msg? (re-pattern paper-id) paper-id
                                        :application :MyConversion)))
              (fact "Auto: paper permit KuntaGML. One verdict, three reviews"
                (exec-other :send-kuntagml :auto true
                            :application-ids [paper-id])
                (all-messages 4)
                => (just (verdict-msg? paper-id :verdict :MyConversion)
                         (review-msg? paper-id :review :MyReview #"Aloitusilmoitus" )
                         (review-msg? paper-id :review :MyReview #"Sijainnin merkintä" )
                         (review-msg? paper-id :review :MyReview #"aloituskokous")
                         :in-any-order))

              (fact "Send cancelled application: not sent"
                (exec-other :send-kuntagml :application true
                            :application-ids [cancel-id])
                (all-messages 0) => empty?)

              (fact "Draft and foreman applications, nothing sent ever"
                (exec-other :send-kuntagml :application true :application-ids [draft-id tj-app-id])
                (exec-other :send-kuntagml :verdict true :application-ids [draft-id tj-app-id])
                (exec-other :send-kuntagml :review true :application-ids [draft-id tj-app-id])
                (all-messages 0) => empty?)

              (facts "force-path: conversion"
                (exec-other :send-kuntagml :application true
                            :application-ids [app-id4])
                (all-messages 1)
                => (just (with-operation-count 2 application-msg? app-id4
                           :application :MyApplication))
                (exec-other :send-kuntagml :application true :force-path :conversion
                            :application-ids [app-id4])
                (all-messages 1)
                => (just (with-operation-count 2 application-msg? app-id4
                           :application :MyConversion))
                (exec-other :send-kuntagml :application true :force-path :c
                            :application-ids [app-id4])
                (all-messages 1)
                => (just (application-msg? app-id4 :application :MyConversion)))

              (facts "Include converted"
                (clear-messages)
                (mongo/update-by-id :applications app-id4 {$set {:facta-imported true}})
                (exec-other :send-kuntagml :application true
                            :application-ids [app-id4])
                (all-messages 0) => empty?

                (exec-other :send-kuntagml :application true :include-converted true
                            :application-ids [app-id4])
                (all-messages 1)
                => (just (with-operation-count 2 application-msg? app-id4
                           :application :MyConversion))
                (mongo/update-by-id :applications app-id4 {$unset {:facta-imported true}}))

              (facts "force-path: application"
                (clear-messages)
                (mongo/update-by-id :applications app-id4 {$set {:facta-imported true}})
                (exec-other :send-kuntagml :application true :include-converted true
                            :force-path :application
                            :application-ids [app-id4])
                (all-messages 1)
                => (just (with-operation-count 2 application-msg?
                           app-id4 :application :MyApplication))
                (exec-other :send-kuntagml :application true :include-converted true
                            :force-path :a
                            :application-ids [app-id4])
                (all-messages 1)
                => (just (with-operation-count 2 application-msg? app-id4
                           :application :MyApplication))
                (mongo/update-by-id :applications app-id4 {$unset {:facta-imported true}})))

            (facts "Send via dmcity-api"
              (clear-messages)
              (facts "Bad parameters"
                (command admin :send-messages-to-dmcity
                         :organizationId "404-R"
                         :applicationId "bad-id"
                         :ops [:last-state])
                => (err :error.dmcity.not-configured)
                (command admin :send-messages-to-dmcity
                         :organizationId "753-R"
                         :applicationId "bad-id"
                         :ops [:last-state])
                => (err :error.application-not-found)
                (command admin :send-messages-to-dmcity
                         :organizationId "837-R"
                         :applicationId app-id4
                         :ops [:last-state])
                => (err :error.application-not-found)
                (command admin :send-messages-to-dmcity
                         :organizationId "753-R"
                         :applicationId app-id4
                         :ops [])
                => (contains {:text       "error.missing-parameters"
                              :parameters [:ops]})
                (command admin :send-messages-to-dmcity
                         :organizationId "753-R"
                         :applicationId app-id4
                         :ops [:last-state :bad :verdicts])
                => (err :error.dmcity.bad-operations)
                (command admin :send-messages-to-dmcity
                         :organizationId "753-R"
                         :applicationId app-id4
                         :ops "hello")
                => (err :error.dmcity.bad-operations)
                (command admin :send-messages-to-dmcity
                         :organizationId "753-R"
                         :applicationId app-id4
                         :ops [:application/verdicts]
                         :channel "bad")
                => (err :error.dmcity.bad-channel))
              (facts "Success"
                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id4
                                   :ops [:last-state :last-state :last-state]))
                => (just (just {:application-id app-id4
                                :ts             pos?
                                :state          "verdictGiven"}))
                (all-messages 1)
                => (just (state-msg? app-id4 :verdictGiven false))

                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id4
                                   :ops [:verdicts :reviews]))
                => (just (just {:application-id app-id4
                                :ts             pos?
                                :verdict-id     truthy}))
                ;; app-id4 does not have reviews
                (all-messages 1)
                => (just (verdict-msg? app-id4 :verdict :MyApplication))
                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id3
                                   :ops [:last-state :application :reviews]))
                => (just (just {:application-id app-id3
                                :ts             pos?
                                :state          "constructionStarted"})
                         (just {:application-id app-id3
                                :ts             pos?})
                         (just {:application-id app-id3
                                :ts             pos?
                                :review         (just {:id   truthy
                                                       :name "Aloituskokous"})})
                         (just {:application-id app-id3
                                :ts             pos?
                                :review         (just {:id   truthy
                                                       :name "Käyttöönottotarkastus"})})
                         (just {:application-id app-id3
                                :ts             pos?
                                :review         (just {:id   truthy
                                                       :name "loppukatselmus"})}))
                (all-messages 5)
                => (just (state-msg? app-id3 :constructionStarted true)
                         (application-msg? app-id3 :application :MyConversion)
                         (review-msg? app-id3 :review :MyReview
                                      #"Aloituskokous")
                         (review-msg? app-id3 :review :MyReview
                                      #"Käyttöönottotarkastus")
                         (review-msg? app-id3 :review :MyReview
                                      #"loppukatselmus")
                         :in-any-order))
              (facts "Application/verdicts operation: application"
                (clear-messages)
                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id1
                                   :ops [:application/verdicts]))
                => (just (just {:application-id app-id1 :ts pos?}))
                (all-messages 1)
                => (just (application-msg? app-id1 :application :MyApplication)))
              (facts "Application/verdict operations: verdicts"
                (clear-messages)
                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id3
                                   :ops [:application/verdicts]))
                => (just (just {:application-id app-id3
                                :ts             pos?
                                :verdict-id     truthy})
                         (just {:application-id app-id3
                                :ts             pos?
                                :verdict-id     truthy}))
                (all-messages 2)
                => (just (verdict-msg? app-id3 :verdict :MyConversion)
                         (verdict-msg? app-id3 :verdict :MyConversion)))

              (facts "Channel: conversion"
                (clear-messages)
                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id1
                                   :ops [:last-state :application/verdicts]
                                   :channel "conversion"))
                => (just (just {:application-id app-id1 :state "submitted" :ts pos?})
                         (just {:application-id app-id1 :ts pos?}))
                (all-messages 2)
                => (just (state-msg? app-id1 :submitted true)
                         (application-msg? app-id1 :application :MyConversion)
                         :in-any-order))

              (facts "Channel: production"
                (clear-messages)
                (:results (command admin :send-messages-to-dmcity
                                   :organizationId "753-R"
                                   :applicationId app-id3
                                   :ops ["last-state" "application/verdicts"]
                                   :channel "production"))
                => (just (just {:application-id app-id3
                                :ts             pos?
                                :state          "constructionStarted"})
                         (just {:application-id app-id3
                                :ts             pos?
                                :verdict-id     truthy})
                         (just {:application-id app-id3
                                :ts             pos?
                                :verdict-id     truthy}))
                (all-messages 3)
                => (just (state-msg? app-id3 :constructionStarted false)
                         (verdict-msg? app-id3 :verdict :MyApplication)
                         (verdict-msg? app-id3 :verdict :MyApplication)
                         :in-any-order))

              (facts "Draft and open applications are not sent"
                (let [{app-id :id
                       :as    app} (create-application pena
                                                       :address "Draft Dungueon"
                                                       :operation "pientalo")]
                  app-id => truthy
                  (:state app) => "draft"
                  (:results (command admin :send-messages-to-dmcity
                                     :organizationId "753-R"
                                     :applicationId app-id
                                     :ops [:last-state :application/verdicts]))
                  => (just (contains {:application-id app-id
                                      :ts             pos?
                                      :skipped        {:state "draft"}}))
                  (command pena :add-comment :id app-id
                           :openApplication true
                           :roles ["applicant" "authority"]
                           :target {:type "application"}
                           :text "Hello inspector") => ok?
                  (:results (command admin :send-messages-to-dmcity
                                     :organizationId "753-R"
                                     :applicationId app-id
                                     :ops ["last-state" "application"]))
                  => (just (contains {:application-id app-id
                                      :ts             pos?
                                      :skipped        {:state "open"}}))

                  (facts "Weird history checks"
                    (mongo/update-by-id :applications app-id {$set {:state "submitted"}})
                    (doseq [op ["all-states" "last-state"]]
                      (fact {:midje/description op}
                        (command admin :send-messages-to-dmcity
                                 :organizationId "753-R"
                                 :applicationId app-id
                                 :ops [op])
                        => {:ok true :results []})))))
              (facts "Foreman application is not sent"
                (let [{app-id :id
                       :as    app} (mongo/select-one :applications {:permitSubtype :tyonjohtaja-hakemus})]
                  app-id => truthy
                  (:state app) => "submitted"
                  (:results (command admin :send-messages-to-dmcity
                                     :organizationId "753-R"
                                     :applicationId app-id
                                     :ops [:last-state :application/verdicts]))
                  => (just (contains {:application-id app-id
                                      :ts             pos?
                                      :skipped        {:operation "tyonjohtajan-nimeaminen-v2"}}))))

              (facts "Inforequests and digitized applications are not sent"
                (let [{app-id :id
                       :as    app} (create-application pena
                                                       :address "Request Railway"
                                                       :operation "aita"
                                                       :infoRequest true)]
                  (fact "Inforequest"
                    app-id => truthy
                    (:state app) => "info"
                    (:results (command admin :send-messages-to-dmcity
                                       :organizationId "753-R"
                                       :applicationId app-id
                                       :ops [:last-state :application/verdicts]))
                    => (just (contains {:application-id app-id
                                        :ts             pos?
                                        :skipped        {:infoRequest true}})))
                  (fact "Digitized application"
                    (mongo/update-by-id :applications app-id {$set {:permitType :ARK
                                                                    :state      :underReview
                                                                    :address    "Digital Drive"}})
                    (:results (command admin :send-messages-to-dmcity
                                       :organizationId "753-R"
                                       :applicationId app-id
                                       :ops [:last-state :application/verdicts]))
                    => (just (contains {:application-id app-id
                                        :ts             pos?
                                        :skipped        {:permitType "ARK"}}))))))

            (fact "Paper permits  can be sent via dmcity-api, even when the xml is not included"
              (clear-messages)
              (:results (command admin :send-messages-to-dmcity
                                 :organizationId "753-R"
                                 :applicationId paper-id
                                 :ops [:last-state :application/verdicts]))
              => (just (just {:application-id paper-id :state "constructionStarted" :ts pos?})
                       (just {:application-id paper-id :ts pos?
                              :verdict-id     truthy}))
              (all-messages 2)
              => (just (state-msg? paper-id :constructionStarted  true)
                       (verdict-msg? paper-id :verdict :MyConversion)))

            (facts "Conversion not enabled: fallback to regular"
              (command admin :toggle-dmcity-configuration-target
                       :organizationId "753-R"
                       :target "conversion"
                       :enabled false) => ok?
              (clear-messages)
              (exec-other :send-kuntagml :application true
                          :application-ids [app-id3])
              (exec-other :send-states :last true
                          :application-ids [app-id3])
              (all-messages 2)
              => (just (application-msg? app-id3 :application :MyApplication)
                       (state-msg? app-id3 :constructionStarted false))

              (exec-other :send-kuntagml :application true
                          :force-path :conversion
                          :application-ids [app-id3])
              (exec-other :send-states :last true
                          :force-path :conversion
                          :application-ids [app-id3])
              (all-messages 2)
              => (just (application-msg? app-id3 :application :MyApplication)
                       (state-msg? app-id3 :constructionStarted false)))

            (facts "Authority can send application verdict messages"
              (clear-messages)
              (command sonja :send-verdict-messages
                       :id app-id4) => (err :error.dmcity.enabled)
              (command admin :toggle-dmcity-configuration-target
                       :organizationId "753-R"
                       :target "R"
                       :enabled true) => ok?
              (command pena :send-verdict-messages
                       :id app-id4) => (err :error.unauthorized)
              (command sonja :send-verdict-messages
                       :id app-id4) => ok?
              (all-messages 1)
              => (just (verdict-msg? app-id4 :verdict :MyApplication))

              (fact "Foreman applications are not sent"
                (mongo/update-by-id :applications app-id4 {$set {:primaryOperation.name :tyonjohtajan-nimeaminen-v2}})
                (command sonja :send-verdict-messages
                         :id app-id4) => (err :error.not-found))

              (fact "Sending fails"
                (mongo/update-by-id :applications app-id3 {$set {:verdicts.0.paatokset.0.poytakirjat []}})
                (command sonja :send-verdict-messages
                         :id app-id3)
                => (just {:errors (just [#"'yht:poytakirja' is not complete"] )
                          :ok     false
                          :text   "error.dmcity.kuntagml"})))))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions
    (let [test-dir (conv-test/mk-test-dir)]
      (against-background
          [(after :contents (conv-test/rm-test-dir test-dir))]
        (facts "vtj-prts"
          (let [build-id      "199887766E" ; From dev resource
                {app-id :id}  (create-local-app pena :operation "pientalo"
                                                :propertyId "18640200010067")
                exec-batchrun #(other/execute-other-command
                                 {:cmd             :vtj-prts
                                  :cmd-help        (fn [_ msg] msg)
                                  :organization-id "186-R"
                                  :system          (test-system/get-common-components)})]
            (command jarvenpaa :set-krysp-endpoint
                     :organizationId "186-R"
                     :permitType "R"
                     :password ""
                     :username ""
                     :url (str (server-address) "/dev/krysp")
                     :version "2.2.2") => ok?
            (command pena :add-operation :id app-id
                     :operation "purkaminen") => ok?
            (command pena :add-operation :id app-id
                     :operation "talousrakennus-laaj") => ok?
            (command pena :submit-application :id app-id) => ok?
            (let [get-docs        #(:documents (mongo/by-id :applications
                                                            app-id
                                                            [:documents]))
                  find-by-op      #(let [doc (util/find-first
                                               (util/fn-> :schema-info :op :name (= %))
                                               (get-docs))]
                                     {:id (:id doc)
                                      :op (-> doc :schema-info :op :id)})
                  {talo-doc :id
                   talo-op  :op}  (find-by-op "pientalo")
                  {purku-doc :id
                   purku-op  :op} (find-by-op "purkaminen")
                  {laaj-doc :id
                   laaj-op  :op}  (find-by-op "talousrakennus-laaj")
                  talo-use        "011 yhden asunnon talot"
                  purku-use       "241 vankilat"
                  laaj-use        "021 rivitalot"]
              (every? ss/not-blank? [talo-doc talo-op purku-doc
                                     purku-op laaj-doc laaj-op])
              => true
              (fact "Select building for laajennus"
                (command pena :merge-details-from-krysp :id app-id
                         :buildingId build-id
                         :documentId laaj-doc
                         :overwrite true) => ok?)
              (fact "Initially, there is only one good vtj-prt"
                (keep #(some-> % :data :valtakunnallinenNumero :value)
                      (get-docs))
                => ["" "" build-id])
              (fact "Update usages"
                (command pena :update-doc :id app-id
                         :collection "documents"
                         :doc talo-doc
                         :updates [["kaytto.kayttotarkoitus" talo-use]])
                => ok?
                (command pena :update-doc :id app-id
                         :collection "documents"
                         :doc purku-doc
                         :updates [["kaytto.kayttotarkoitus" purku-use]])
                => ok?
                (keep #(some-> % :data :kaytto :kayttotarkoitus :value ss/blank-as-nil)
                      (get-docs))
                => [talo-use purku-use laaj-use])
              (fact "Add buildings and execute batchrun"
                (mongo/update-by-id :applications app-id
                                    {$set {:buildings [{:nationalId  "op-match"
                                                        :usage       purku-use
                                                        :operationId talo-op}
                                                       {:nationalId  build-id ; purged
                                                        :usage       purku-use
                                                        :operationId talo-op}
                                                       {:nationalId  "" ; ignored
                                                        :usage       purku-use
                                                        :operationId ""}
                                                       {:nationalId  "usage-match"
                                                        :usage       purku-use
                                                        :operationId ""}]}})
                (exec-batchrun) => nil)
              (fact "Application updated"
                (let [app  (mongo/by-id :applications app-id)
                      note (-> app :_sheriff-notes last :note)]
                  (keep #(some-> % :data :valtakunnallinenNumero :value ss/blank-as-nil)
                        (:documents app))
                  => ["op-match" "usage-match" build-id]

                  (ss/includes? note (str talo-doc " (op-match)")) => true
                  (ss/includes? note (str purku-doc " (usage-match)")) => true
                  (:reportingDbOverrideTs app) => pos?

                  (fact "Rerun does not change anything"
                    (exec-batchrun)
                    (mongo/by-id :applications app-id)
                    => app)
                  (facts "Clear some vtj-prts and try again"
                    (mongo/update-by-query :applications
                                           {:_id       app-id
                                            :documents {$elemMatch {:id talo-doc}}}
                                           {$set {:documents.$.data.valtakunnallinenNumero.value nil}})
                    => 1
                    (mongo/update-by-query :applications
                                           {:_id       app-id
                                            :documents {$elemMatch {:id purku-doc}}}
                                           {$unset {:documents.$.data.valtakunnallinenNumero true}})
                    => 1
                    (keep #(some-> % :data :valtakunnallinenNumero :value)
                          (get-docs))
                    => [build-id]
                    (exec-batchrun)
                    (let [app2 (mongo/by-id :applications app-id)]
                      (dissoc app2 :_sheriff-notes :reportingDbOverrideTs)
                      => (dissoc app :_sheriff-notes :reportingDbOverrideTs)
                      (map :note (:_sheriff-notes app2))
                      => [note note]
                      (> (:reportingDbOverrideTs app2)
                         (:reportingDbOverrideTs app)) => true))
                  (fact "One shot"
                    (mongo/update-by-id :applications app-id
                                        {$set   {:buildings [{:nationalId "one-id"}]}
                                         $unset {:_sheriff-notes        true
                                                 :reportingDbOverrideTs true}
                                         $pull  {:documents {:id purku-doc}}})
                    (mongo/update-by-id :applications app-id
                                        {$pull {:documents {:id laaj-doc}}})
                    (mongo/update-by-query :applications
                                           {:_id       app-id
                                            :documents {$elemMatch {:id talo-doc}}}
                                           {$set {:documents.$.data.valtakunnallinenNumero.value nil}})
                    => 1
                    (exec-batchrun)
                    (mongo/any? :applications
                                {:_id                                         app-id
                                 :documents.data.valtakunnallinenNumero.value "one-id"})
                    => true))))))))))
