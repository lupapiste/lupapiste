(ns lupapalvelu.pate.tasks-test
  (:require [lupapalvelu.pate.tasks :refer :all]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.strings :as ss]))

(def test-pate-verdict verdict-test/verdict)

(def ts 1522223223639)

(testable-privates lupapalvelu.pate.tasks
                   reviews->tasks conditions->tasks
                   plans->tasks foremen->tasks
                   legacy-reviews->tasks legacy-conditions->tasks
                   legacy-foremen->tasks
                   group-tasks
                   combine-subreviews
                   update-order
                   application-task-order
                   pate-foreman-order
                   pate-plan-order
                   pate-review-order
                   pate-task-order)

(facts "Plans"
  (fact "Create tasks from plans"
    (let [tasks              (plans->tasks test-pate-verdict ts)
          validation-results (->> tasks
                                  (map (partial tasks/task-doc-validation "task-lupamaarays"))
                                  (filter seq))]
      tasks => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :version 1}
                                 :closed      nil
                                 :created     ts
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Suunnitelmat"
                                 :data        {:maarays                     {:value    "Suunnitelmat"
                                                                             :modified ts}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})
                      (contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :version 1}
                                 :closed      nil
                                 :created     ts
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Suunnitelmat2"
                                 :data        {:maarays                     {:value    "Suunnitelmat2"
                                                                             :modified ts}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})])
      validation-results => empty?))

  (fact "Plans not included"
    (plans->tasks (assoc-in test-pate-verdict [:data :plans-included] false) ts)
    => nil)

  (fact "No plan selected"
    (plans->tasks (assoc-in test-pate-verdict [:data :plans] nil) ts) => []
    (plans->tasks (assoc-in test-pate-verdict [:data :plans] []) ts) => [])

  (fact "Only one plan selected, language is English"
    (plans->tasks (-> test-pate-verdict
                      (assoc-in [:data :language] "en")
                      (update-in [:data :plans] (partial take 1)))
                  ts)
    => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                       :type    :task
                                       :version 1}
                         :closed      nil
                         :created     ts
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "1a156dd40e40adc8ee064463"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Plans"
                         :data        {:maarays                     {:value    "Plans"
                                                                     :modified ts}
                                       :kuvaus                      {:value ""}
                                       :vaaditutErityissuunnitelmat {:value ""}}})])))

(facts "Conditions"
  (fact "Create tasks from conditions"
    (let [tasks              (conditions->tasks test-pate-verdict ts)
          validation-results (->> tasks
                                  (map (partial tasks/task-doc-validation "task-lupamaarays"))
                                  (filter seq))]
      tasks => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :version 1}
                                 :closed      nil
                                 :created     ts
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "muut lupaehdot - teksti"
                                 :data        {:maarays                     {:value    "muut lupaehdot - teksti"
                                                                             :modified ts}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})
                      (contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :version 1}
                                 :closed      nil
                                 :created     ts
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "toinen teksti"
                                 :data        {:maarays                     {:value    "toinen teksti"
                                                                             :modified ts}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})])
      validation-results => empty?))

  (fact "Only one condition"
    (conditions->tasks (assoc-in test-pate-verdict [:data :conditions]
                                 {:foo {:condition "Hello world!"}})
                       ts)
    => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                       :type    :task
                                       :version 1}
                         :closed      nil
                         :created     ts
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "1a156dd40e40adc8ee064463"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Hello world!"
                         :data        {:maarays                     {:value    "Hello world!"
                                                                     :modified ts}
                                       :kuvaus                      {:value ""}
                                       :vaaditutErityissuunnitelmat {:value ""}}})])))

(facts "Foremen"
  (fact "Creates tasks from foremen"
    (let [tasks              (foremen->tasks (update-in test-pate-verdict
                                                        [:data :foremen]
                                                        (partial take 2))
                                             ts)
          validation-results (->> tasks
                                  (map (partial tasks/task-doc-validation
                                                "task-vaadittu-tyonjohtaja"))
                                  (filter seq))]
      tasks => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :version 1}
                                 :closed      nil
                                 :created     ts
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Erityisalojen ty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :kuntaRoolikoodi {:value "erityisalojen ty\u00f6njohtaja"
                                                                 :modified ts}
                                               :osapuolena     {:value false}}})
                      (contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :version 1}
                                 :closed      nil
                                 :created     ts
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Ilmanvaihtoty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :kuntaRoolikoodi {:value "IV-ty\u00f6njohtaja"
                                                                 :modified ts}
                                               :osapuolena     {:value false}}})])
      validation-results => empty?))

  (fact "Foremen not included"
    (foremen->tasks (assoc-in test-pate-verdict [:data :foremen-included] false) ts)
    => nil)

  (fact "No foreman selected"
    (foremen->tasks (assoc-in test-pate-verdict [:data :foremen] nil) ts) => []
    (foremen->tasks (assoc-in test-pate-verdict [:data :foremen] []) ts) => [])

  (fact "Only one foreman selected, language is Swedish"
    (foremen->tasks (-> test-pate-verdict
                        (assoc-in [:data :language] "sv")
                        (update-in [:data :foremen] (partial drop 3)))
                    ts)
    => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                       :type    :task
                                       :subtype :foreman
                                       :version 1}
                         :closed      nil
                         :created     ts
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "1a156dd40e40adc8ee064463"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Arbetsledare inom vatten och avlopp"
                         :data        {:asiointitunnus {:value ""}
                                       :kuntaRoolikoodi {:value "KVV-ty\u00f6njohtaja"
                                                         :modified ts}
                                       :osapuolena     {:value false}}})])))

(def legacy-verdict {:legacy? true
                     :id      "legacy-id"
                     :data    {:conditions {:id1 {:name "First condition"}
                                            :id2 {:name "Second condition"}}
                               :foremen {:id3 {:role "vastaava ty\u00f6njohtaja"}
                                         :id4 {:role "ty\u00f6njohtaja"}}}})

(facts "Legacy conditions"
  (fact "Two conditions"
    (let [tasks (legacy-conditions->tasks legacy-verdict
                                          12345)]
      (fact "Valid tasks"
        (->> tasks
             (map (partial tasks/task-doc-validation "task-lupamaarays"))
             (filter seq)) => empty?)

      tasks => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :version 1}
                                 :closed       nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "First condition"
                                 :data        {:maarays                     {:value    "First condition"
                                                                             :modified 12345}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})
                      (contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Second condition"
                                 :data        {:maarays                     {:value "Second condition"
                                                                             :modified 12345}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})])))
  (fact "Only one condition"
    (legacy-conditions->tasks (assoc-in legacy-verdict
                                        [:data :conditions] {:foo {:name "Hello world!"}})
                              12345)
    => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                       :type    :task
                                       :version 1}
                         :closed      nil
                         :created     12345
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "legacy-id"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Hello world!"
                         :data        {:maarays                     {:value "Hello world!"
                                                                     :modified 12345}
                                       :kuvaus                      {:value ""}
                                       :vaaditutErityissuunnitelmat {:value ""}}})]))
  (fact "No conditions"
    (legacy-conditions->tasks (assoc-in legacy-verdict
                                        [:data :conditions] nil)
                              12345)
    => empty?))

(facts "Legacy foremen"
  (fact "Two foremen"
    (let [tasks (legacy-foremen->tasks legacy-verdict 12345)]
      (fact "Valid tasks"
        (->> tasks
             (map (partial tasks/task-doc-validation "task-vaadittu-tyonjohtaja"))
             (filter seq))=> empty?)
      tasks => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Vastaava ty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"
                                                                 :modified 12345}
                                               :osapuolena     {:value false}}})
                      (contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Ty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :kuntaRoolikoodi {:value "ty\u00f6njohtaja"
                                                                 :modified 12345}
                                               :osapuolena     {:value false}}})])))
  (fact "Only one foreman"
    (let [tasks (legacy-foremen->tasks (assoc-in legacy-verdict
                                                 [:data :foremen]
                                                 {:bar {:role "ty\u00f6njohtaja"}})
                                       12345)]
      tasks => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Ty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :kuntaRoolikoodi {:value "ty\u00f6njohtaja"
                                                                 :modified 12345}
                                               :osapuolena     {:value false}}})])))

  (fact "No foremen"
    (legacy-foremen->tasks (assoc-in legacy-verdict
                                     [:data :foremen] nil)
                           12345)
    => empty?))

(defn make-task [id taskname & {:keys [type role sub]}]
  (let [type (cond
               type type
               role "task-vaadittu-tyonjohtaja"
               :else "task-katselmus")]
    (cond-> {:id id :taskname taskname
             :schema-info {:name "task-katselmus"}}
      type (assoc-in [:schema-info :name] type)
      role (assoc-in [:data :kuntaRoolikoodi :value] role)
      sub (assoc :source {:type "task" :id sub}))))

(defn created [task ts]
  (assoc task :created ts))

(defn mini [{:keys [id taskname]}]
  {:id       id
   :taskname (ss/lower-case taskname)})

(defn minisub [{:keys [source] :as task}]
  (assoc (mini task)
         :source-review (:id source)))

(defn minitj [{:keys [data] :as task}]
  (assoc (mini task)
         :kuntaroolikoodi (-> data :kuntaRoolikoodi :value)))

(defn order [xs]
  (zipmap xs (range)))

(facts "task-order"
  (let [r1      (make-task "r1" "Review One")
        r2      (make-task "r2" "Review Two")
        sub2    (make-task "sub2" "Sub for R2" :sub "r2")
        subsub2 (make-task "subsub2" "Sub for sub for R2" :sub "sub2")
        r3      (make-task "r3" "Review Three" :type "task-katselmus-ya")
        r4      (make-task "r4" "Review Four" :type "task-katselmus-backend")
        tj1     (make-task "tj1" "Työnjohtaja" :role "työnjohtaja")
        tj2     (make-task "tj2" "Työnjohtaja" :role "työnjohtaja")
        tj3     (make-task "tj3" "Vastaava työnjohtaja" :role "vastaava työnjohtaja")
        tj4     (make-task "tj4" "Erityisalojen työnjohtaja" :role "erityisalojen työnjohtaja")
        p1      (make-task "p1" "Plan One" :type "task-lupamaarays")
        p2      (make-task "p2" "Plan Two" :type "task-lupamaarays")
        p3      (make-task "p3" "Plan Three" :type "task-lupamaarays")
        c1      (make-task "c1" "Condition One" :type "task-lupamaarays")
        c2      (make-task "c2" "Condition Two" :type "task-lupamaarays")
        c3      (make-task "c3" "Condition Three" :type "task-lupamaarays")]
    (facts "group-tasks"
      (group-tasks [r1 r2 r3 tj2])
      => {:reviews [(mini r1) (mini r2) (mini r3)]
          :foremen [(minitj tj2)]}
      (group-tasks [(created r1 10) (created r2 8) sub2 subsub2 (created r3 2) r4
                    (created tj1 1) (created tj2 2) tj3
                    (created p1 2) p2 p3])
      => {:reviews    [(mini r4) (mini r3) (mini r2) (mini r1)]
          :subreviews [(minisub sub2) (minisub subsub2)]
          :foremen    [(minitj tj3) (minitj tj1) (minitj tj2)]
          :plans      [(mini p2) (mini p3) (mini p1)]})

    (facts "update-order"
      (update-order {}) => {}
      (update-order {} nil) => {nil 0}
      (update-order {} "zero" "one" "two")
      => {"zero" 0 "one" 1 "two" 2}
      (update-order {:foo :bar} "zero" "one" "two")
      => {"zero" 1 "one" 2 "two" 3 :foo :bar}
      (update-order {"one" 4 "two" 8} "zero" "one" "two")
      => {"zero" 2 "one" 4 "two" 8}
      (update-order {"one" 1 "two" 2} "zero" "one" "two" "one" "two" "one" "two")
      => {"zero" 2 "one" 1 "two" 2})

    (facts "combine-subreviews"
      (combine-subreviews ["r1" "r2" "r3"] nil)
      => ["r1" "r2" "r3"]
      (combine-subreviews ["r1" "r2" "r3"] [(minisub sub2) (minisub subsub2)])
      => ["r1" "r2" "sub2" "subsub2" "r3"]
      (combine-subreviews ["r1" "r3"] [(minisub sub2) (minisub subsub2)])
      => ["r1" "r3" "sub2" "subsub2"])

    (facts "pate-foreman-order"
      (let [tasks (group-tasks [tj1 tj2 tj3 tj4])]
        (pate-foreman-order tasks {}) => {"työnjohtaja"               0
                                          "vastaava työnjohtaja"      1
                                          "erityisalojen työnjohtaja" 2}
        (pate-foreman-order tasks {:references {:foremen ["vastaava-tj" "erityis-tj"
                                                          "tj" "vv-tj" "iv-tj"]}})
        => (order ["vastaava työnjohtaja"
                   "erityisalojen työnjohtaja"
                   "työnjohtaja"
                   "kvv-työnjohtaja"
                   "iv-työnjohtaja"])
        (pate-foreman-order tasks {:references {:foremen ["vastaava-tj"
                                                          "tj" "vv-tj"]}})
        => (order ["vastaava työnjohtaja"
                   "työnjohtaja"
                   "kvv-työnjohtaja"
                   "erityisalojen työnjohtaja"])
        (pate-foreman-order (update tasks :foremen concat [{:kuntaroolikoodi "ei tiedossa"}])
                            {:references {:foremen ["vastaava-tj" "tj" "vv-tj"]}})
        => (order ["vastaava työnjohtaja"
                   "työnjohtaja"
                   "kvv-työnjohtaja"
                   "erityisalojen työnjohtaja"
                   "ei tiedossa"])
        (pate-foreman-order (update tasks :foremen conj {:kuntaroolikoodi "ei tiedossa"})
                            {:references {:foremen ["vastaava-tj" "tj" "vv-tj"]}})
        => (order ["vastaava työnjohtaja"
                   "työnjohtaja"
                   "kvv-työnjohtaja"
                   "ei tiedossa"
                   "erityisalojen työnjohtaja"])))

    (facts "pate-plan-order"
      (let [tasks (group-tasks [p1 c1 p2 c2 p3 c3])]
        (fact "Plain order"
          (pate-plan-order tasks {})
          => (order ["plan one"      "condition one" "plan two"
                     "condition two" "plan three"    "condition three" ]))
        (fact "Default language (mismatch between tasks and verdict)"
          (pate-plan-order tasks {:references {:plans [{:en "Plan One"
                                                        :fi "Suunnitelma Yksi"}
                                                       {:en "Plan Three"
                                                        :fi "Suunnitelma Kolme"}
                                                       {:en "Plan Two"
                                                        :fi "Suunnitelma Kaksi"}]}
                                  :data       {:conditions {:id1 {:condition "Condition Two"}
                                                            :id2 {:condition "Condition Three"}
                                                            :id3 {:condition "Condition One"}}}})
          => (order ["suunnitelma yksi" "suunnitelma kolme" "suunnitelma kaksi"
                     "condition two" "condition three" "condition one"
                     "plan one" "plan two" "plan three"]))
        (fact "English (tasks and verdicts aligned)"
          (pate-plan-order tasks {:references {:plans [{:en "Plan One"
                                                        :fi "Suunnitelma Yksi"}
                                                       {:en "Plan Three"
                                                        :fi "Suunnitelma Kolme"}
                                                       {:en "Plan Two"
                                                        :fi "Suunnitelma Kaksi"}]}
                                  :data       {:language   "en"
                                               :conditions {:id1 {:condition "Condition Two"}
                                                            :id2 {:condition "Condition Three"}
                                                            :id3 {:condition "Condition One"}}}})
          => (order ["plan one" "plan three" "plan two"
                     "condition two" "condition three" "condition one"]))
        (fact "Extra tasks"
          (pate-plan-order tasks {:references {:plans [{:en "Plan One"
                                                        :fi "Suunnitelma Yksi"}
                                                       {:en "Plan Two"
                                                        :fi "Suunnitelma Kaksi"}]}
                                  :data       {:language   "en"
                                               :conditions {:id1 {:condition "Condition Two"}
                                                            :id3 {:condition "Condition One"}}}})
          => (order ["plan one" "plan two"
                     "condition two" "condition one"
                     "plan three" "condition three"]))))

    (facts "pate-review-order"
      (let [tasks (group-tasks [r1 r2 r3])]
        (fact "Plain order"
          (pate-review-order tasks {})
          => (order ["review one" "review two" "review three"]))
        (fact "Default language (mismatch between tasks and verdict)"
          (pate-review-order tasks {:references {:reviews [{:en "Review One"
                                                            :fi "Katselmus Yksi"}
                                                           {:en "Review Three"
                                                            :fi "Katselmus Kolme"}
                                                           {:en "Review Two"
                                                            :fi "Katselmus Kaksi"}]}})
          => (order ["katselmus yksi" "katselmus kolme" "katselmus kaksi"
                     "review one" "review two" "review three"]))
        (fact "English (no mismatch)"
          (pate-review-order tasks {:references {:reviews [{:en "Review One"
                                                            :fi "Katselmus Yksi"}
                                                           {:en "Review Three"
                                                            :fi "Katselmus Kolme"}
                                                           {:en "Review Two"
                                                            :fi "Katselmus Kaksi"}]}
                                    :data       {:language "en"}})
          => (order ["review one" "review three" "review two"]))
        (fact "Extra task"
          (pate-review-order tasks {:references {:reviews [{:en "Review One"
                                                            :fi "Katselmus Yksi"}
                                                           {:en "Review Two"
                                                            :fi "Katselmus Kaksi"}
                                                           {:en "Review Foo"
                                                            :fi "Katselmus Kääk"}]}
                                    :data       {:language "en"}})
          => (order ["review one" "review two" "review foo" "review three"]))))

    (facts "task-order"
      (let [tasks   [r1 r2 sub2 subsub2 r3 r4 tj1 tj2 tj3 tj4
                     p1 p2 p3 c1 c2 c3]
            verdict {:category   "r"
                     :references {:foremen ["vastaava-tj" "erityis-tj"
                                            "tj" "vv-tj" "iv-tj"]
                                  :plans   [{:en "Plan One"
                                             :fi "Suunnitelma Yksi"}
                                            {:en "Plan Three"
                                             :fi "Suunnitelma Kolme"}
                                            {:en "Plan Two"
                                             :fi "Suunnitelma Kaksi"}]
                                  :reviews [{:en "Review One"
                                             :fi "Katselmus Yksi"}
                                            {:en "Review Three"
                                             :fi "Katselmus Kolme"}
                                            {:en "Review Two"
                                             :fi "Katselmus Kaksi"}]}
                     :data       {:language   "en"
                                  :conditions {:id1 {:condition "Condition Two"}
                                               :id2 {:condition "Condition Three"}
                                               :id3 {:condition "Condition One"}}}}]

        (fact "Modern Pate verdict"
          (task-order {:tasks (shuffle tasks)} verdict)
          => {:foremen ["vastaava työnjohtaja" "erityisalojen työnjohtaja"
                        "työnjohtaja"]
              :plans   ["p1" "p3" "p2" "c2" "c3" "c1"]
              :reviews ["r1" "r3" "r2" "sub2" "subsub2" "r4"]})
        (fact "Legacy Pate verdict"
          (task-order {:tasks tasks} (assoc verdict :legacy? true))
          => {:foremen ["työnjohtaja" "vastaava työnjohtaja" "erityisalojen työnjohtaja"]
              :plans   ["p1" "p2" "p3" "c1" "c2" "c3"]
              :reviews ["r1" "r2" "sub2" "subsub2" "r3" "r4"]})
        (fact "No Pate verdict"
          (task-order {:tasks tasks} nil)
          => {:foremen ["työnjohtaja" "vastaava työnjohtaja" "erityisalojen työnjohtaja"]
              :plans   ["p1" "p2" "p3" "c1" "c2" "c3"]
              :reviews ["r1" "r2" "sub2" "subsub2" "r3" "r4"]})))))
