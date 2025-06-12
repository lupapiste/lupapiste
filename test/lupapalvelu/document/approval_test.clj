(ns lupapalvelu.document.approval-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.approval :as a]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [sade.util :as util]))

(def schema-without-approvals {:info {:name       "approval-model-without-approvals"
                                      :version    1
                                      :approvable false}
                               :body [{:name "single" :type :string}
                                      {:name "team" :type :group
                                       :body [{:name "logo" :type :string}]}
                                      {:name "single2" :type :string}
                                      {:name "repeats" :type :group :repeating true
                                       :body [{:name "single3" :type :string}]}]})

(def schema-with-approvals {:info {:name       "approval-model-with-approvals"
                                   :version    1
                                   :approvable true}
                            :body [{:name "single" :type :string :approvable true}
                                   {:name "team" :type :group :approvable true
                                    :body [{:name "logo" :type :string}]}
                                   {:name "single2" :type :string}
                                   {:name "repeats" :type :group :repeating true :approvable true
                                    :body [{:name "single3" :type :string}]}]})


(defn with-schemas []
  (schemas/defschema 1 schema-without-approvals)
  (schemas/defschema 1 schema-with-approvals))

(use-fixtures :once (fn [f]
                      (with-schemas)
                      (f)))

(deftest approvable?-test
  (let [document (model/new-document schema-with-approvals 12345)]
    (testing "Document"
      (is (true? (a/approvable? document)))
      (is (true? (a/approvable? {} schema-with-approvals nil)))
      (is (false? (a/approvable? (model/new-document schema-without-approvals 12345)))))
    (testing "Document part"
      (is (true? (a/approvable? document schema-with-approvals [:single])))
      (is (true? (a/approvable? document [:single])))
      (is (false? (a/approvable? document schema-without-approvals [:single])))
      (is (false? (a/approvable? document schema-with-approvals [:single2])))
      (is (true? (a/approvable? document schema-with-approvals [:repeats :1])))
      (is (false? (a/approvable? document schema-with-approvals [:repeats :0 :single3]))))))

(def map-paths #'a/map-paths)

(deftest map-paths-test
  (testing "Simple map"
    (is (nil? (map-paths {} identity identity)))
    (is (= [[[:foo] 4]] (map-paths {:foo 3 :bar "hello"}
                                   #(int? %3)
                                   #(inc %3))))
    (is (nil? (map-paths {:foo 3 :bar "hello"}
                         #(keyword? %3)
                         (constantly true)))))
  (testing "Submaps"
    (is (= [[[:one :two] "a"]
            [[:four :five] "c"]
            [[:six] "d"]]
           (map-paths {:one  {:two   {:flag true :value "a"}
                              :three {:value "b"}}
                       :four {:five {:flag true :value "c"}}
                       :six  {:flag true :value "d"}}
                      #(:flag %3)
                      #(:value %3))))
    (is (= [[[:a :b :c :ok] "Look: :one"]
            [[:a :b :ok] "Look: two"]
            [[:a :ok] "Look: three"]
            [[:ok] "Look: 0"]]
           (map-paths {:a  {:b  {:c  {:ok  :one
                                      :foo 2}
                                 :ok "two"}
                            :ok "three"}
                       :ok 0}
                      (fn [_ k _] (= k :ok))
                      #(str "Look: " %3))))))

(def base-groups #'a/base-groups)

(deftest base-groups-test
  (testing "No approvable data or schema"
    (is (nil? (base-groups {:schema-info {:name "approval-model-with-approvals"}})))
    (is (nil? (base-groups {:data        {:foo {:bar "hello"}}
                            :schema-info {:name "approval-model-with-approvals"}})))
    (is (nil? (base-groups {:data        {:single  "hello"
                                          :team    {:logo "yeah"}
                                          :repeats {:1 {:single3 "One"}
                                                    :2 {:single3 "Two"}}}
                            :schema-info {:name "approval-model-without-approvals"}}))))
  (testing "Approvable data"
    (is (= {[:single]     {:timestamp 0}
            [:team]       {:timestamp 0}
            [:repeats :1] {:timestamp 0}
            [:repeats :2] {:timestamp 0}}
           (base-groups {:data        {:single  "hello"
                                       :team    {:logo "yeah"}
                                       :repeats {:1 {:single3 "One"}
                                                 :2 {:single3 "Two"}}}
                         :schema-info {:name "approval-model-with-approvals"}})))))


(def prepare-document-approvals #'a/prepare-document-approvals)

(deftest prepare-document-approvals-test
  (testing "No approvals"
    (is (nil? (prepare-document-approvals {})))
    (is (nil? (prepare-document-approvals {:meta nil})))
    (is (nil? (prepare-document-approvals {:meta {}})))
    (is (nil? (prepare-document-approvals {:meta {:foo "bar"}}))))
  (testing "Approvals"
    (is (= [[[] {:timestamp 1
                 :user      {:firstName "Ruth" :lastName "Root"}
                 :value     "approved" :note "Hello"}]]
           (prepare-document-approvals {:schema-info {:name "approval-model-with-approvals"}
                                        :meta        {:_approved {:timestamp 1
                                                                  :user      {:firstName "Ruth"
                                                                              :lastName  "Root"}
                                                                  :value     "approved"
                                                                  :note      "Hello"
                                                                  :foo       "bar"}
                                                      :_blaah    1}})))
    (is (= [[[:one :three :four]  {:user {} :value "rejected"}]
            [[:one :two] {:timestamp 2 :user {:firstName "Bob"}}]
            [[:five] {:note "World" :user {}}]
            [[] {:timestamp 1 :user {:firstName "Ruth" :lastName "Root"}}]]
           (prepare-document-approvals
             {:schema-info {:name "approval-model-with-approvals"}
              :meta        {:_approved {:timestamp 1
                                        :user      {:firstName "Ruth"
                                                    :lastName  "Root"}}
                            :one       {:two   {:_approved {:timestamp 2
                                                            :user      {:firstName "Bob"}}}
                                        :three {:four {:_approved {:value "rejected"}}}}
                            :five      {:_approved {:note "World"}}}}))))
  (testing "With base groups"
    (is (= [[[:repeats :2] {:timestamp 0}]
            [[:repeats :1] {:timestamp 3
                            :user      {:firstName "B"}
                            :value     "approved"}]
            [[:team] {:timestamp 0}]
            [[:single] {:timestamp 2
                        :user      {:firstName "A"}
                        :value     "approved"}]]
           (prepare-document-approvals
             {:data        {:single  "hello"
                            :team    {:logo "yeah"}
                            :repeats {:1 {:single3 "One"}
                                      :2 {:single3 "Two"}}}
              :schema-info {:name "approval-model-with-approvals"}
              :meta        {:single  {:_approved {:timestamp 2
                                                  :user      {:firstName "A"}
                                                  :value     "approved"}}
                            :repeats {:1 {:_approved {:timestamp 3
                                                      :user      {:firstName "B"}
                                                      :value     "approved"}}}}})))))

(def prefixed? #'a/prefixed?)
(def latest-modification #'a/latest-modification)

(deftest minor-utils
  (testing "prefixed?"
    (is (false? (prefixed? nil [:foo :bar])))
    (is (true? (prefixed? [] [:foo :bar])))
    (is (true? (prefixed? [:foo] [:foo :bar])))
    (is (true? (prefixed? [:foo :bar] [:foo :bar])))
    (is (false? (prefixed? [:bar :foo] [:foo :bar])))
    (is (false? (prefixed? [:foo :bar :baz] [:foo :bar]))))
  (testing "latest-modification"
    (is (nil? (latest-modification [] [])))
    (is (= 1 (latest-modification [] [[[:foo] 1]])))
    (is (= 2 (latest-modification [] [[[:foo] 1]
                                      [[:bar] 2]])))
    (is (= 3 (latest-modification [] {[:foo] 1
                                      [:bar] 2
                                      [:foo :baz] 3
                                      [:foo :hii] nil})))
    (is (= 2 (latest-modification [:bar] {[:foo] 1
                                          [:bar] 2
                                          [:foo :baz] 3})))
    (is (nil? (latest-modification [:bar :baz] {[:foo] 1
                                                [:bar] 2
                                                [:foo :baz] 3})))
    (is (= 3 (latest-modification [:foo] {[:foo] 1
                                          [:bar] 2
                                          [:foo :baz] 3})))
    (is (= 3 (latest-modification [:foo :baz] {[:foo] 10
                                               [:bar] 2
                                               [:foo :baz] 3})))))


(def resolve--changed #'a/resolve--changed)

(deftest resolve--changed-test
  (testing "No changes"
    (is (nil? (resolve--changed {})))
    (is (nil? (resolve--changed {:data {:foo {:value "One" :modified 1}}})))
    (is (= [[[:bar] {:timestamp 2 :user {}}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:foo {:value "One" :modified 1}}
                              :meta        {:bar {:_approved {:timestamp 2}}}})))
    (is (= [[[:foo] {:timestamp 1 :user {}}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:foo {:value "One" :modified 1}}
                              :meta        {:foo {:_approved {:timestamp 1}}}})))
    (testing "... with base groups"
      (is (= [[[:foo] {:timestamp 1 :user {}}]
              [[:team] {:timestamp 2}]]
             (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                                :data        {:team {:logo {:value    "#"
                                                            :modified 2}}
                                              :foo  {:value "One" :modified 1}}
                                :meta        {:foo {:_approved {:timestamp 1}}}})))))
  (testing "Changes"
    (is (= [[[:foo] {:timestamp 1 :user {} :changed 2}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:foo {:value "One" :modified 2}}
                              :meta        {:foo {:_approved {:timestamp 1}}}})))
    (is (= [[[] {:timestamp 10 :user {:firstName "Root"} :changed 40}]
            [[:foo] {:timestamp 5 :user {} :value "approved" :changed 40}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:foo {:one {:value "One" :modified 20}
                                                  :two {:value "Two" :modified 40}}}
                              :meta        {:_approved {:timestamp 10 :user {:firstName "Root"}}
                                            :foo       {:_approved {:timestamp 5
                                                                    :value     "approved"}}}})))
    (is (= [[[] {:timestamp 10 :user {:firstName "Root"} :changed 50}]
            [[:foo] {:timestamp 5 :user {} :value "approved" :changed 40}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:foo {:one {:value "One" :modified 20}
                                                  :two {:value "Two" :modified 40}}
                                            :dum {:value "Three" :modified 50}}
                              :meta        {:_approved {:timestamp 10 :user {:firstName "Root"}}
                                            :foo       {:_approved {:timestamp 5
                                                                    :value     "approved"}}}})))
    (testing "... with base groups"
      (is (= [[[] {:timestamp 10 :user {:firstName "Root"} :changed 50}]
              [[:foo] {:timestamp 5 :user {} :value "approved" :changed 40}]
              [[:team] {:timestamp 2}]
              [[:repeats :0] {:timestamp 6 :changed 8 :value "rejected" :user {:lastName "N"} }]
              [[:repeats :1] {:timestamp 7}]]
             (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                                :data        {:foo     {:one {:value "One" :modified 20}
                                                        :two {:value "Two" :modified 40}}
                                              :dum     {:value "Three" :modified 50}
                                              :team    {:logo {:value    "#"
                                                               :modified 2}}
                                              :repeats {:0 {:single3 {:value    "?"
                                                                      :modified 8}}
                                                        :1 {:single3 {:value    "?!"
                                                                      :modified 7}}}}
                                :meta        {:_approved {:timestamp 10 :user {:firstName "Root"}}
                                              :foo       {:_approved {:timestamp 5
                                                                      :value     "approved"}}
                                              :repeats   {:0 {:_approved {:timestamp 6
                                                                          :value     "rejected"
                                                                          :user      {:lastName "N"}}}}}}))))
    (testing "Regression fix (approval without timestamp)."
      (is (= [[[] {:note "No timestamp" :user {}}]]
             (resolve--changed
               {:data        {}
                :schema-info {:name "approval-model-with-approvals"}
                :meta        {:_approved {:note "No timestamp"}}})))))

  (testing "Approval upwards propagation"
    (is (= [[[] {:timestamp 10 :user {:firstName "Root"} :changed 50}]
            [[:foo] {:timestamp 50 :user {} :value "rejected"}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {}
                              :meta        {:_approved {:timestamp 10 :user {:firstName "Root"}}
                                            :foo       {:_approved {:timestamp 50
                                                                    :value     "rejected"}}}})))
    (is (= [[[] {:timestamp 10 :user {:firstName "Root"}}]
            [[:foo] {:timestamp 50 :user {} :value "approved"}]
            [[:team] {:timestamp 7}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:team {:logo {:value "#" :modified 7}}}
                              :meta        {:_approved {:timestamp 10 :user {:firstName "Root"}}
                                            :foo       {:_approved {:timestamp 50
                                                                    :value     "approved"}}}})))
    (is (= [[[] {:timestamp 10 :user {:firstName "Root"} :changed 60}]
            [[:foo] {:timestamp 50 :user {} :value "approved" :changed 60}]]
           (resolve--changed {:schema-info {:name "approval-model-with-approvals"}
                              :data        {:foo {:modified 60}}
                              :meta        {:_approved {:timestamp 10 :user {:firstName "Root"}}
                                            :foo       {:_approved {:timestamp 50
                                                                    :value     "approved"}}}})))))

(def resolve--override #'a/resolve--override)

(deftest resolve--override-test
  (testing "No override"
    (is (= {[]     {:value "approved" :timestamp 10}
            [:foo] {:timestamp 10}}
           (resolve--override [[[] {:value "approved" :timestamp 10}]
                               [[:foo] {:timestamp 10}]])))
    (is (= {[]     {:value "approved" :timestamp 20 :changed 40}
            [:foo] {:timestamp 40}}
           (resolve--override [[[] {:value   "approved" :timestamp 20
                                    :changed 40}]
                               [[:foo] {:timestamp 40}]])))
    (is (= {[]     {:note "No value" :timestamp 20}
            [:foo] {:timestamp 10}}
           (resolve--override [[[] {:note "No value" :timestamp 20}]
                               [[:foo] {:timestamp 10}]]))))
  (testing "Overrides"
    (is (= {[]     {:value "approved" :timestamp 20 :changed 40}
            [:bar] {:timestamp 19 :override "approved"}
            [:foo] {:timestamp 40}}
           (resolve--override [[[] {:value   "approved" :timestamp 20
                                    :changed 40}]
                               [[:foo] {:timestamp 40}]
                               [[:bar] {:timestamp 19}]])))
    (is (= {[]     {:value "approved" :timestamp 20}
            [:foo] {:timestamp 10 :override "approved"}}
           (resolve--override [[[] {:value "approved" :timestamp 20}]
                               [[:foo] {:timestamp 10}]])))
    (is (= {[]     {:value "approved" :timestamp 20}
            [:foo] {:timestamp 10 :override "approved"}}
           (resolve--override [[[] {:value "approved" :timestamp 20}]
                               [[:foo] {:timestamp 10}]])))
    (is (= {[]     {:value "rejected" :timestamp 20}
            [:foo] {:timestamp 10 :override "rejected" :value "approved"}
            [:foo :bar] {:timestamp 5 :override "rejected" :value "rejected"}}
           (resolve--override [[[] {:value "rejected" :timestamp 20}]
                               [[:foo] {:timestamp 10 :value "approved"}]
                               [[:foo :bar] {:timestamp 5 :value "rejected"}]])))
    (is (= {[:foo] {:timestamp 10 :value "approved"}
            [:foo :bar] {:timestamp 5 :override "approved" :value "rejected"}}
           (resolve--override [[[:foo] {:timestamp 10 :value "approved"}]
                               [[:foo :bar] {:timestamp 5 :value "rejected"}]]))))
  (testing "Regression fix (approval without timestamp)"
    (is (= {[] {:note "No timestamp" :user {}}}
           (resolve--override [[[] {:note "No timestamp" :user {}}]])))))

(def check (juxt a/approved? a/resolve-approvals))

(defn add-a [meta-a kw-path ts approved? & {:keys [note] :as m}]
  (->> {:timestamp ts
        :value     (if approved? "approved" "rejected")
        :user      {:firstName (:first m) :lastName (:last m)}
        :note      note}
       util/strip-nils
       (assoc-in meta-a (util/split-kw-path (util/kw-path kw-path :_approved)))))

(defn add-d [data kw-path ts]
  (update-in data (util/split-kw-path kw-path) assoc :modified ts))

(deftest resolve-approvals-and-approved?-test
  (is (= [false nil] (check {})))
  (is (= [false nil] (check {:data {:foo {:value "One" :modified 1}}})))
  (is (= [false {[:bar] {:timestamp 2 :user {:firstName "A"}
                         :value     "approved"}}]
         (check {:schema-info {:name "approval-model-with-approvals"}
                 :data        (add-d {} :foo 1)
                 :meta        (add-a {} :bar 2 true :first "A")})))
  (is (= [false {[:foo] {:timestamp 1 :user {} :value "rejected"}}]
         (check {:schema-info {:name "approval-model-with-approvals"}
                 :data        (add-d {} :foo 1)
                 :meta        (add-a {} :foo 1 false)})))
  (is (= [false {[:foo] {:value   "approved" :timestamp 15
                         :changed 20
                         :user    {:firstName "B"}}}]
         (check {:schema-info {:name "approval-model-with-approvals"}
                 :data        (-> {}
                                  (add-d :foo.one 10)
                                  (add-d :foo.two 20)
                                  (add-d :bar 30))
                 :meta        (-> {}
                                  (add-a :foo 15 true :first "B"))})))
  (is (= [false {[]     {:value "rejected" :timestamp 10 :changed 30
                         :user  {:lastName "R"}
                         :note  "What?"}
                 [:foo] {:value   "approved" :timestamp 15
                         :changed 20
                         :user    {:firstName "B"}}}]
         (check {:schema-info {:name "approval-model-with-approvals"}
                 :data        (-> {}
                                  (add-d :foo.one 10)
                                  (add-d :foo.two 20)
                                  (add-d :bar 30))
                 :meta        (-> {}
                                  (add-a nil 10 false :last "R" :note "What?")
                                  (add-a :foo 15 true :first "B"))})))
  (is (= [true {[]       {:value "approved" :timestamp 50
                          :user  {:lastName "R"}
                          :note  "What?"}
                [:foo]   {:value    "approved" :timestamp 15
                          :override "approved"
                          :changed  20
                          :user     {:firstName "B"}}
                [:other] {:timestamp 40         :value "rejected"
                          :user      {:firstName "Ot" :lastName "Her"}
                          :override  "approved" :note  "!"}}]
         (check {:schema-info {:name "approval-model-with-approvals"}
                 :data        (-> {}
                                  (add-d :foo.one 10)
                                  (add-d :foo.two 20)
                                  (add-d :bar 30))
                 :meta        (-> {}
                                  (add-a nil 50 true :last "R" :note "What?")
                                  (add-a :foo 15 true :first "B")
                                  (add-a :other 40 false :first "Ot" :last "Her" :note "!"))})))
  (is (= [true {[]       {:value "approved" :timestamp 50
                          :user  {:lastName "R"}
                          :note  "What?"}
                [:foo]   {:value "approved" :timestamp 60
                          :user  {:firstName "B"}}
                [:other] {:timestamp 40         :value "rejected"
                          :user      {:firstName "Ot" :lastName "Her"}
                          :override  "approved" :note  "!"}
                [:team]  {:timestamp 25
                          :override "approved"}}]
         (check {:schema-info {:name "approval-model-with-approvals"}
                 :data        (-> {}
                                  (add-d :foo.one 10)
                                  (add-d :foo.two 20)
                                  (add-d :bar 30)
                                  (add-d :team.logo 25))
                 :meta        (-> {}
                                  (add-a nil 50 true :last "R" :note "What?")
                                  (add-a :foo 60 true :first "B")
                                  (add-a :other 40 false :first "Ot" :last "Her" :note "!"))}))))

(let [doc1        {:id          "doc1"
                   :schema-info {:name "approval-model-with-approvals"}
                   :data        (-> {}
                                    (add-d :team.logo 25)
                                    (add-d :foo.one 10)
                                    (add-d :repeats.5.single3 20)
                                    (add-d :repeats.7.single3 7))
                   :meta        (-> {}
                                    (add-a nil 11 true :last "R")
                                    (add-a :repeats.4 12 false :first "F" :note "!"))}
      doc2        {:id          "doc2"
                   :schema-info {:name "approval-model-without-approvals"}
                   :data        (-> {}
                                    (add-d :team.logo 25)
                                    (add-d :foo.one 10)
                                    (add-d :repeats.5.single3 20))
                   :meta        (-> {}
                                    (add-a nil 11 true :last "R")
                                    (add-a :repeats.4 12 false :first "F" :note "!"))}
      doc3        {:id          "doc3"
                   :schema-info {:name "approval-model-without-approvals"}
                   :data        (-> {}
                                    (add-d :team.logo 25)
                                    (add-d :foo.one 10)
                                    (add-d :repeats.5.single3 20))}
      application {:documents [doc1 doc2 doc3]}
      doc1-result {:_root     {:changed 25 :timestamp 11 :user {:lastName "R"} :value "approved"}
                   :repeats.4 {:note "!" :timestamp 12 :user {:firstName "F"} :value "rejected"}
                   :repeats.5 {:timestamp 20}
                   :repeats.7 {:timestamp 7 :override "approved"}
                   :team      {:timestamp 25}}
      doc2-resutl {:_root {:changed 25 :timestamp 11 :user {:lastName "R"} :value "approved"}
                   :repeats.4
                   {:note "!" :timestamp 12 :user {:firstName "F"} :value "rejected"}}]

  (deftest application-approvals-test
    (testing "applicationApprovals"
      (is (= {"doc1" doc1-result
              "doc2" doc2-resutl}
             (a/applicationApprovals application)))
      (is (= {"doc1" doc1-result}
             (a/applicationApprovals application "doc1")))
      (is (empty? (a/applicationApprovals application "doc0"))))
    (testing "approved?"
      (is (false? (a/approved? doc1)))
      (is (true? (a/approved? (assoc-in doc1 [:meta :_approved :timestamp] 100))))
      (is (false? (a/approved? doc2)))
      (is (false? (a/approved? doc3))))
    (testing "rejected?"
      (is (false? (a/rejected? doc1)))
      (is (true? (a/rejected? (assoc-in doc1 [:meta :_approved :value] "rejected"))))
      (is (false? (a/rejected? doc2)))
      (is (false? (a/rejected? doc3))))))
