(ns lupapalvelu.tasks-api-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.tasks-api :as tasks-api]))

(def base-task
  {:id   "task-id"
   :data {:katselmuksenLaji {:value "aloituskokous"}
          :katselmus        {:pitoPvm      {:value "1.1.2025"}
                             :tila         {:value "lopullinen"}
                             :pitaja       {:value {:code "ABC" :name "Teppo Tarkastaja"}}
                             :huomautukset {:kuvaus {:value "Notes"}}}}})

(def base-application
  {:permitType "R"
   :tasks      [base-task]})

(def base-organization
  {:review-officers-list-enabled false
   :reviewOfficers               []})

(defn make-command
  [& {:keys [application organization lang taskId]
      :or   {application  base-application
             organization base-organization
             lang         "fi"
             taskId       "task-id"}}]
  {:application  application
   :organization organization
   :data         {:lang   lang
                  :taskId taskId}})

(defn with-task-data [task path value]
  (assoc-in task (concat [:data] path [:value]) value))

(defn with-task [application task]
  (assoc application :tasks [task]))

(deftest validate-required-review-fields-test
  (testing "skips validation when no :lang in data (auth model building)"
    (is (nil? (tasks-api/validate-required-review-fields (make-command :lang nil)))))

  (testing "skips validation when task not found"
    (is (nil? (tasks-api/validate-required-review-fields (make-command :taskId "non-existent")))))

  (testing "missing katselmuksenLaji"
    (let [task (with-task-data base-task [:katselmuksenLaji] "")
          app  (with-task base-application task)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.missing-parameters"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "missing pitoPvm"
    (let [task (with-task-data base-task [:katselmus :pitoPvm] "")
          app  (with-task base-application task)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.missing-parameters"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "R permit: missing tila"
    (let [task (with-task-data base-task [:katselmus :tila] "")
          app  (with-task base-application task)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.missing-parameters"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "YA permit: missing huomautukset kuvaus"
    (let [task (with-task-data base-task [:katselmus :huomautukset :kuvaus] "")
          app  (-> base-application
                   (assoc :permitType "YA")
                   (with-task task))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.missing-parameters"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "YA permit: tila is not required"
    (let [task (with-task-data base-task [:katselmus :tila] "")
          app  (-> base-application
                   (assoc :permitType "YA")
                   (with-task task))]
      (is (nil? (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "missing reviewer (non-ryhti)"
    (let [task (with-task-data base-task [:katselmus :pitaja] nil)
          app  (with-task base-application task)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.reviewer-missing"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "missing reviewer (ryhti-data-2026) - missing etunimi"
    (let [task (-> base-task
                   (assoc-in [:data :katselmus :pitajaDetails :etunimi :value] "")
                   (assoc-in [:data :katselmus :pitajaDetails :sukunimi :value] "Tarkastaja"))
          app  (-> base-application
                   (assoc :ryhti-data-2026 true)
                   (with-task task))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.reviewer-missing"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "missing reviewer (ryhti-data-2026) - missing sukunimi"
    (let [task (-> base-task
                   (assoc-in [:data :katselmus :pitajaDetails :etunimi :value] "Teppo")
                   (assoc-in [:data :katselmus :pitajaDetails :sukunimi :value] ""))
          app  (-> base-application
                   (assoc :ryhti-data-2026 true)
                   (with-task task))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.reviewer-missing"
                            (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "valid ryhti-data-2026 reviewer passes"
    (let [task (-> base-task
                   (assoc-in [:data :katselmus :pitajaDetails :etunimi :value] "Teppo")
                   (assoc-in [:data :katselmus :pitajaDetails :sukunimi :value] "Tarkastaja"))
          app  (-> base-application
                   (assoc :ryhti-data-2026 true)
                   (with-task task))]
      (is (nil? (tasks-api/validate-required-review-fields (make-command :application app))))))

  (testing "invalid reviewer code"
    (let [org {:review-officers-list-enabled true
               :reviewOfficers               [{:code "XYZ"
                                               :name "Other"}]}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"error.invalid-reviewer-code"
                            (tasks-api/validate-required-review-fields (make-command :organization org))))))

  (testing "valid reviewer code passes"
    (let [org {:review-officers-list-enabled true
               :reviewOfficers               [{:code "ABC"
                                               :name "Teppo Tarkastaja"}]}]
      (is (nil? (tasks-api/validate-required-review-fields (make-command :organization org))))))

  (testing "reviewer code not checked when list disabled"
    (let [org {:review-officers-list-enabled false
               :reviewOfficers               [{:code "XYZ"
                                               :name "Other"}]}]
      (is (nil? (tasks-api/validate-required-review-fields (make-command :organization org))))))

  (testing "all fields valid - passes"
    (is (nil? (tasks-api/validate-required-review-fields (make-command))))))
