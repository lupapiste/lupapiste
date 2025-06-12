(ns lupapalvelu.conversion.review-fix-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.review-fix :as review-fix]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review-fix-test-util :refer :all]
            [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer [just]]
            [monger.operators :refer :all]
            [sade.date :as date]
            [schema.test :as st]))

(use-fixtures :once st/validate-schemas)

(def make-end-review #'review-fix/make-end-review)
(def find-matches #'review-fix/find-matches)
(def application-updates #'review-fix/application-updates)
(def building-state-updates #'review-fix/building-state-updates)
(def end-review? #'review-fix/end-review?)
(def source-reviews #'review-fix/source-reviews)
(def target-tasks #'review-fix/target-tasks)
(def reviews-updates #'review-fix/reviews-updates)

(deftest end-review?-test
  (testing "nil input should return false"
    (is (false? (end-review? nil))))

  (testing "empty katselmuksenLaji should return false"
    (is (false? (end-review? {:katselmuksenLaji ""}))))

  (testing "muu katselmus should return false"
    (is (false? (end-review? {:katselmuksenLaji "muu katselmus"}))))

  (testing "loppukatselmus (case-insensitive) should return true"
    (is (true? (end-review? {:katselmuksenLaji "LoPPuKatselMUs"}))))

  (testing "loppukatselmus with prefix should return false"
    (is (false? (end-review? {:katselmuksenLaji "weird LoPPuKatselMUs"}))))

  (testing "osittainen loppukatselmus should return true"
    (is (true? (end-review? {:katselmuksenLaji "osiTTaiNeN LoPPuKatselMUs"})))))

(deftest make-end-review-test
  (let [mid       (mongo/create-id)
        date      (date/zoned-date-time "27.11.2023")
        buildings [{:index-key                          :1
                    :kunnanSisainenPysyvaRakennusnumero "municipality-id"
                    :valtakunnallinenNumero             "VTJ-PRT"
                    :in-use?                            false
                    :scope                              "osittainen"}
                   {:index-key                          :2
                    :kunnanSisainenPysyvaRakennusnumero "town-id"
                    :in-use?                            true
                    :scope                              ""}
                   {:kunnanSisainenPysyvaRakennusnumero ""
                    :in-use?                            nil
                    :scope                              "  "}]]

    (testing "Empty parameters should return nil"
      (is (nil? (make-end-review {} []))))

    (testing "Valid parameters but empty buildings list should return nil"
      (is (nil? (make-end-review
                  {:name     " Inspection " :scope    "osittainen"
                   :other-id "helloworld"   :mongo-id mid
                   :date     date}
                  []))))

    (testing "Valid parameters with buildings should transform data correctly"
      (is (= {:name      "inspection"
              :scope     "osittainen"
              :other-id  "helloworld"
              :mongo-id  mid
              :date      date
              :buildings [{:index-key :1
                           :vtj-prt   "vtj-prt"
                           :kspr      "municipality-id"
                           :in-use?   false
                           :scope     "osittainen"}
                          {:index-key :2
                           :kspr      "town-id"
                           :in-use?   true}]}
             (make-end-review
               {:name     " Inspection "    :scope    "osittainen"
                :type     "does not matter" :date     date
                :other-id "helloworld"      :mongo-id mid}
               buildings))))

    (testing "Unsupported scope should return nil"
      (is (nil? (make-end-review
                  {:name     " Inspection "
                   ;; Supported by KuntaGML, but not by batchrun
                   :scope    "ei tiedossa"
                   :other-id "helloworld"
                   :type     "does not matter"
                   :mongo-id mid
                   :date     (date/zoned-date-time "27.11.2023")}
                  buildings))))))

(deftest xml-and-application-end-reviews-test
  (let [xb1 {:vtj-prt "vtj1" :kspr "kspr1"}
        xr1 (make-xml-review "One" "30.11.2023" "loppukatselmus"
                             "osittainen" ["my-id" "foobar"] xb1)
        xb2 {:vtj-prt "vtj2" :kspr  "kspr2"
             :in-use  true   :scope "osittainen"}
        xr2 (make-xml-review "Two" "1.12.2023" " aloituskokous "
                             "lopullinen" nil xb1 xb2)
        xr3 (make-xml-review "Three" "1.12.2023" "osittainen loppukatselmus"
                             " lopullinen " nil xb1 xb2)

        mr1                     (make-mongo-review "6569e52ddc2b7938fe07bb8d" "Five" "2.12.2023"
                                                   "loppukatselmus" "lopullinen" "foreign-id1"
                                                   xb1)
        mr2                     (make-mongo-review "6569e96edc2b7938fe07bb8e" "Six" "3.12.2023"
                                                   "OsiTTAINEN LOPpukatselmus" "lopullinen" "foreign-id1"
                                                   xb1 xb2)
        xml-end-reviews         (partial source-reviews :end-review)
        application-end-reviews (partial target-tasks :end-review)]

    (testing "XML end reviews with a single review"
      (is (= [{:buildings [{:kspr "kspr1" :vtj-prt "vtj1"}]
               :date      (date/zoned-date-time "30.11.2023")
               :name      "one"
               :other-id  "my-id"
               :scope      "osittainen"}]
             (xml-end-reviews (make-kuntagml xr1)))))

    (testing "Duplicates are purged"
      (is (= [{:buildings [{:kspr "kspr1" :vtj-prt "vtj1"}]
               :date      (date/zoned-date-time "30.11.2023")
               :name      "one"
               :other-id  "my-id"
               :scope      "osittainen"}]
             (xml-end-reviews (make-kuntagml xr1 xr1 xr1)))))

    (testing "XML end reviews with multiple reviews"
      (is (midje? (just {:buildings [{:kspr "kspr1" :vtj-prt "vtj1"}]
                         :date      (date/zoned-date-time "30.11.2023")
                         :name      "one"
                         :other-id  "my-id"
                         :scope      "osittainen"}
                        {:buildings [{:kspr "kspr1" :vtj-prt "vtj1"}
                                     {:kspr    "kspr2" :vtj-prt "vtj2"
                                      :in-use? true    :scope   "osittainen"}]
                         :date      (date/zoned-date-time "1.12.2023")
                         :name      "three"
                         :scope      "lopullinen"}
                        :in-any-order)
                  (xml-end-reviews (make-kuntagml xr1 xr2 xr3)))))

    (testing "End reviews without buildings are ignored"
      (is (nil? (xml-end-reviews (make-kuntagml (make-xml-review "Houseless" "1.12.2023"
                                                                 "loppukatselmus" "lopullinen"
                                                                 nil))))))

    (testing "Other ids must be unique or all are omitted"
      (is (midje? (just {:buildings [{:kspr "kspr1" :vtj-prt "vtj1"}]
                         :date      (date/zoned-date-time "30.11.2023")
                         :name      "one"
                         :scope      "osittainen"}
                        {:buildings [{:kspr    "kspr2" :vtj-prt "vtj2"
                                      :in-use? true    :scope   "osittainen"}]
                         :date      (date/zoned-date-time "29.11.2023")
                         :name      "four"
                         :scope      "lopullinen"}
                        :in-any-order)
                  (xml-end-reviews (make-kuntagml xr1
                                                  (make-xml-review "Four" "29.11.2023"
                                                                   "osittainen loppukatselmus"
                                                                   "lopullinen" ["my-id" "hello"]
                                                                   xb2))))))

    (testing "Application end reviews with single review"
      (is (= [{:buildings [{:index-key :0 :kspr "kspr1" :vtj-prt "vtj1"}]
               :date      (date/zoned-date-time "2.12.2023")
               :mongo-id  "6569e52ddc2b7938fe07bb8d"
               :name      "five"
               :other-id  "foreign-id1"
               :scope      "lopullinen"}]
             (application-end-reviews (make-application "LP-1" mr1)))))

    (testing "Non-end reviews and end reviews without buildings are ignored"
      (is (= [{:buildings [{:index-key :0 :kspr "kspr1" :vtj-prt "vtj1"}]
               :date      (date/zoned-date-time "2.12.2023")
               :mongo-id  "6569e52ddc2b7938fe07bb8d"
               :name      "five"
               :other-id  "foreign-id1"
               :scope      "lopullinen"}]
             (application-end-reviews
               (make-application "LP-3"
                                 (make-mongo-review (mongo/create-id)
                                                    "Start" "30.11.2023" "aloituskokous"
                                                    "lopullinen" "start-id"
                                                    xb2)
                                 mr1
                                 (make-mongo-review (mongo/create-id)
                                                    "Deadend" "29.11.2023" "loppukatselmus"
                                                    "osittainen" nil))))))

    (testing "Other ids must be unique or all are omitted (application end reviews)"
      (is (midje? (just {:buildings [{:index-key :0 :kspr "kspr1" :vtj-prt "vtj1"}]
                         :date      (date/zoned-date-time "2.12.2023")
                         :mongo-id  "6569e52ddc2b7938fe07bb8d"
                         :name      "five"
                         :scope      "lopullinen"}
                        {:buildings [{:index-key :0 :kspr "kspr1" :vtj-prt "vtj1"}
                                     {:index-key :1   :kspr  "kspr2" :vtj-prt "vtj2"
                                      :in-use?   true :scope "osittainen"}]
                         :date      (date/zoned-date-time "3.12.2023")
                         :mongo-id  "6569e96edc2b7938fe07bb8e"
                         :name      "six"
                         :scope      "lopullinen"}
                        :in-any-order)
                  (application-end-reviews (make-application "LP-2" mr1 mr2)))))))

(defn finder [fix-type reviews & targets]
  (->> (apply make-kuntagml targets)
       (source-reviews fix-type)
       (find-matches fix-type reviews)))

(deftest find-matches-test
  (let [b           {:vtj-prt "house1"}
        mid1        (mongo/create-id)
        mr1         (make-mongo-review mid1 "End of line" "1.12.2023" "loppukatselmus" "lopullinen"
                                       "extra-id" b)
        mid2        (mongo/create-id)
        mr2         (make-mongo-review mid2 "End of line" "1.12.2023" "loppukatselmus" "lopullinen"
                                       nil b)
        [r1 r2]     (->> (target-tasks :end-review (make-application "LP-1" mr1 mr2)))
        t-id-match1 (make-xml-review "Something" "2.12.2023" "LoPPuKatselMUs" "osittainen"
                                     [" EXTRA-ID " "Extra"] b)
        t-id-match2 (make-xml-review "Else" "3.12.2023" "Osittainen LoPPuKatselMUs" "osittainen"
                                     [mid1 "Mongoose"] b)
        t-match     (make-xml-review " END OF LINE " "1.12.2023" "osittainen LoPPuKatselMUs" "lopullinen"
                                     nil b)
        ;; Not matching
        t1          (make-xml-review "Out of line" "1.12.2023" "LoPPuKatselMUs" "lopullinen"
                                     nil b)
        t2          (make-xml-review "End of line" "2.12.2023" "LoPPuKatselMUs" "lopullinen"
                                     nil b)
        t3          (make-xml-review "End of line" "1.12.2023" "LoPPuKatselMUs" "osittainen"
                                     nil b)
        t4          (make-xml-review "Orphan id1" "1.12.2023" "osittainen LoPPuKatselMUs" "lopullinen"
                                     [mid1] b)
        t5          (make-xml-review "Orphan id2" "1.12.2023" "osittainen LoPPuKatselMUs" "lopullinen"
                                     ["extra-id"] b)
        finder      (partial finder :end-review)]
    (testing "No matches"
      (is (nil? (finder [r1 r2] t1 t2 t3 t4 t5))))

    (testing "Match"
      (is (= [[r1 {:name      "something"  :date     (date/zoned-date-time "2.12.2023")
                   :scope     "osittainen" :other-id "extra-id"
                   :buildings [{:vtj-prt "house1"}]}]]
             (finder [r1 r2] t1 t2 t-id-match1 t3 t4 t5)))

      (is (= [[r1 {:name      "else"       :date     (date/zoned-date-time "3.12.2023")
                   :scope     "osittainen" :other-id mid1
                   :buildings [{:vtj-prt "house1"}]}]]
             (finder [r2 r1] t1 t2 t-id-match2 t3 t4 t5)))

      (is (= [[r1 {:name  "end of line" :date      (date/zoned-date-time "1.12.2023")
                   :scope "lopullinen"  :buildings [{:vtj-prt "house1"}]}]]
             (finder [r1] t1 t2 t-match t3 t4 t5)))

      (is (= [[r2 {:name  "end of line" :date      (date/zoned-date-time "1.12.2023")
                   :scope "lopullinen"  :buildings [{:vtj-prt "house1"}]}]]
             (finder [r2] t1 t2 t-match t3 t4 t5)))

      (is (midje? (just [r1 {:name      "something"  :date     (date/zoned-date-time "2.12.2023")
                             :scope     "osittainen" :other-id "extra-id"
                             :buildings [{:vtj-prt "house1"}]}]
                        [r2 {:name  "end of line" :date      (date/zoned-date-time "1.12.2023")
                             :scope "lopullinen"  :buildings [{:vtj-prt "house1"}]}]
                        :in-any-order)
                  (finder [r1 r2] t1 t2 t-match t3 t-id-match1 t4 t5))))

    (testing "Id match is preferred"
      (is (= [[r1 {:name      "something"  :date     (date/zoned-date-time "2.12.2023")
                   :scope     "osittainen" :other-id "extra-id"
                   :buildings [{:vtj-prt "house1"}]}]]
             (finder [r1] t-match t-id-match1))))))

(deftest application-updates-test
  (let [zdt (date/zoned-date-time "4.12.2023 13.05")
        ts  (date/timestamp zdt)]

    (with-redefs [date/now (constantly zdt)]
      (testing "application-updates: one task"
        (is (= {$push {:_sheriff-notes {:note    "End review fix: Task my-id."
                                        :created ts}}
                $set  {:reportingDbOverrideTs ts}}
               (application-updates :end-review ["my-id"]))))

      (testing "application-updates: multiple tasks"
        (is (= {$push {:_sheriff-notes {:note    "Note fix: Tasks one-id, three-id, two-id."
                                        :created ts}}
                $set  {:reportingDbOverrideTs ts}}
               (application-updates :note ["one-id" "two-id" "three-id"])))))))

(deftest building-state-updates-test
  (testing "No updates"
    (is (nil? (building-state-updates {:index-key 0          :in-use? false
                                       :vtj-prt   "house-id" :kspr    "city-id"}
                                      [{:vtj-prt "foo"}])))
    (is (nil? (building-state-updates {:index-key 0          :in-use? false
                                       :vtj-prt   "house-id" :kspr    "city-id"}
                                      [{:vtj-prt "house-id"}])))
    (is (nil? (building-state-updates {:index-key 0          :in-use? false
                                       :vtj-prt   "house-id" :kspr    "city-id"}
                                      [{:vtj-prt "house-id" :in-use? false}]))))
  (testing "Updates"
    (is (= {:tasks.$.data.rakennus.0.tila.kayttoonottava.value true}
           (building-state-updates {:index-key 0          :in-use? false
                                    :vtj-prt   "house-id" :kspr    "city-id"}
                                   [{:vtj-prt "foo"}
                                    {:vtj-prt "house-id" :in-use? true}])))

    (is (= {:tasks.$.data.rakennus.0.tila.kayttoonottava.value true}
           (building-state-updates {:index-key 0          :in-use? false
                                    :vtj-prt   "house-id" :kspr    "city-id"}
                                   [{:vtj-prt "foo"}
                                    {:vtj-prt "house-id" :in-use? true}])))

    (is (= {:tasks.$.data.rakennus.0.tila.kayttoonottava.value true
            :tasks.$.data.rakennus.0.tila.tila.value           "osittainen"}
           (building-state-updates {:index-key 0          :in-use? false
                                    :vtj-prt   "house-id" :kspr    "city-id"}
                                   [{:vtj-prt "foo"}
                                    {:vtj-prt "house-id" :in-use? true
                                     :scope   "osittainen"}])))

    (is (= {:tasks.$.data.rakennus.2.tila.kayttoonottava.value false}
           (building-state-updates {:index-key 2          :in-use? true
                                    :scope     "osittainen"
                                    :vtj-prt   "house-id" :kspr    "city-id"}
                                   [{:vtj-prt "foo"}
                                    {:vtj-prt "bar-id" :in-use? true
                                     :scope   "osittainen"}
                                    {:vtj-prt "baz-id"     :kspr    "city-id"
                                     :scope   "osittainen" :in-use? false}])))

    (is (= {:tasks.$.data.rakennus.2.tila.tila.value "lopullinen"}
           (building-state-updates {:index-key 2          :in-use? true
                                    :scope     "osittainen"
                                    :vtj-prt   "house-id" :kspr    "city-id"}
                                   [{:vtj-prt "foo"}
                                    {:vtj-prt "baz-id"     :kspr    "city-id"
                                     :scope   "osittainen" :in-use? false}
                                    {:vtj-prt "house-id" :in-use? true
                                     :scope   "lopullinen"}])))))

;; ----------------------------------
;; Fix review notes
;; ----------------------------------

(def resolve-note #'review-fix/resolve-note)
(def make-note-review #'review-fix/make-note-review)

(deftest resolve-note-test
  (testing "Identical notes return nil"
    (is (nil? (resolve-note "Same note" "Same note")))
    (is (nil? (resolve-note "  with spaces  " "with spaces")))
    (is (nil? (resolve-note "UPPERCASE" "uppercase"))))

  (testing "When old note contains new note, return nil"
    (is (nil? (resolve-note "This is a longer note with details" "longer note")))
    (is (nil? (resolve-note "Complete sentence here" "sentence"))))

  (testing "When new note contains old note, return new note"
    (is (= "Extended version with more details"
           (resolve-note "version" "Extended version with more details")))
    (is (= "This contains the old note and more"
           (resolve-note "the old note" "This contains the old note and more"))))

  (testing "When notes are different, concatenate with separator"
    (let [old-note "Original note text"
          new-note "Completely different information"
          expected (str old-note "\n\n------------------------\n\n" new-note)]
      (is (= expected (resolve-note old-note new-note))))

    (testing "Spaces are trimmed when concatenating"
      (let [old-note "  Original with spaces  "
            new-note "  New with spaces too  "
            expected (str "Original with spaces"
                          "\n\n------------------------\n\n"
                          "New with spaces too")]
        (is (= expected (resolve-note old-note new-note)))))))

(deftest make-note-review-test
  (let [mid (mongo/create-id)]
    (testing "Empty parameters should return nil."
      (is (nil? (make-note-review {}))))

    (testing "Valid parameters should transform data correctly"
      (is (= {:name     "inspection"
              :scope    "osittainen"
              :other-id "helloworld"
              :type     "onpahan vaan katselmus"
              :mongo-id mid
              :date     (date/zoned-date-time "27.11.2023")
              :note     "Hello world."}
             (make-note-review
               {:name     " Inspection " :scope    "osittainen"
                :other-id "helloworld"   :mongo-id mid
                :type     "onpahan vaan katselmus"
                :date     (date/zoned-date-time "27.11.2023")
                :note     "   Hello world.  "}))))

    (testing "Unsupported scope should return nil"
      (is (nil? (make-note-review
                  {:name     " Inspection "
                   ;; Supported by KuntaGML, but not by batchrun
                   :scope    "ei tiedossa"
                   :type     "onpahan vaan katselmus"
                   :other-id "helloworld"
                   :mongo-id mid
                   :date     (date/zoned-date-time "27.11.2023")
                   :note     "hii"}))))))

(deftest xml-and-application-note-reviews-test
  (let [mid1                     (mongo/create-id)
        mid2                     (mongo/create-id)
        mid3                     (mongo/create-id)
        xr1                      (make-xml-note-review "One" "30.11.2023" "loppukatselmus"
                                                       "osittainen" ["my-id" "foobar"] " Howdy ")
        xr2                      (make-xml-note-review "Two" "1.12.2023" " aloituskokous "
                                                       "lopullinen" nil "")
        xr3                      (make-xml-note-review "Three" "1.12.2023" "osittainen loppukatselmus"
                                                       " lopullinen " nil "Yeah")
        mr1                      (make-mongo-note-review "6569e52ddc2b7938fe07bb8d" "Five" "2.12.2023"
                                                         "loppukatselmus" "lopullinen" "foreign-id1"
                                                         "Howdy")
        mr2                      (make-mongo-note-review "6569e96edc2b7938fe07bb8e" "Six" "3.12.2023"
                                                         "OsiTTAINEN LOPpukatselmus" "lopullinen" "foreign-id1"
                                                         "hello")
        xml-note-reviews         (partial source-reviews :note)
        application-note-reviews (partial target-tasks :note)]

    (testing "XML note reviews with a single review"
      (is (= [{:note     "Howdy"
               :date     (date/zoned-date-time "30.11.2023")
               :name     "one"
               :other-id "my-id"
               :type     "loppukatselmus"
               :scope    "osittainen"}]
             (xml-note-reviews (make-kuntagml xr1)))))

    (testing "XML note reviews, no duplicates"
      (is (= [{:note     "Howdy"
               :date     (date/zoned-date-time "30.11.2023")
               :name     "one"
               :other-id "my-id"
               :type     "loppukatselmus"
               :scope    "osittainen"}]
             (xml-note-reviews (make-kuntagml xr1 xr1 xr1)))))

    (testing "XML note reviews with multiple reviews"
      (is (midje? (just {:date     (date/zoned-date-time "30.11.2023")
                         :name     "one"
                         :note     "Howdy"
                         :other-id "my-id"
                         :type     "loppukatselmus"
                         :scope    "osittainen"}
                        {:note  "Yeah"
                         :date  (date/zoned-date-time "1.12.2023")
                         :name  "three"
                         :type  "osittainen loppukatselmus"
                         :scope "lopullinen"}
                        :in-any-order)
                  (xml-note-reviews (make-kuntagml xr1 xr2 xr3)))))

    (testing "Reviews without notes are ignored"
      (is (nil? (xml-note-reviews (make-kuntagml (make-xml-note-review "Houseless" "1.12.2023"
                                                                       "loppukatselmus" "lopullinen"
                                                                       nil "   "))))))

    (testing "Other ids must be unique or all are omitted"
      (is (midje? (just {:note  "Howdy"
                         :date  (date/zoned-date-time "30.11.2023")
                         :name  "one"
                         :type  "loppukatselmus"
                         :scope "osittainen"}
                        {:note  "Ni hao"
                         :date  (date/zoned-date-time "29.11.2023")
                         :name  "four"
                         :type  "osittainen loppukatselmus"
                         :scope "lopullinen"}
                        :in-any-order)
                  (xml-note-reviews (make-kuntagml xr1
                                                   (make-xml-note-review "Four" "29.11.2023"
                                                                         "osittainen loppukatselmus"
                                                                         "lopullinen" ["my-id" "hello"]
                                                                         " Ni hao "))))))

    (testing "Application note reviews with single review"
      (is (= [{:note     "Howdy"
               :date     (date/zoned-date-time "2.12.2023")
               :mongo-id "6569e52ddc2b7938fe07bb8d"
               :type     "loppukatselmus"
               :name     "five"
               :other-id "foreign-id1"
               :scope    "lopullinen"}]
             (application-note-reviews (make-application "LP-1" mr1)))))

    (testing "Other ids must be unique or all are omitted (application note reviews)"
      (is (midje? (just {:note     "Howdy"
                         :date     (date/zoned-date-time "2.12.2023")
                         :mongo-id "6569e52ddc2b7938fe07bb8d"
                         :name     "five"
                         :type     "loppukatselmus"
                         :scope    "lopullinen"}
                        {:note     "hello"
                         :date     (date/zoned-date-time "3.12.2023")
                         :mongo-id "6569e96edc2b7938fe07bb8e"
                         :name     "six"
                         :type     "osittainen loppukatselmus"
                         :scope    "lopullinen"}
                        :in-any-order)
                  (application-note-reviews (make-application "LP-2" mr1 mr2)))))

    (testing "Reviews-updates :note"
      (is (= {mid2 {:tasks.$.data.katselmus.huomautukset.kuvaus.value "Ni hao"}
              mid3 {:tasks.$.data.katselmus.huomautukset.kuvaus.value
                    "All good

------------------------

Yeah"}}
             (reviews-updates :note
                              (make-application "LP-123"
                                                (make-mongo-note-review mid1 " one "
                                                                        "30.11.2023"
                                                                        "loppukatselmus" "osittainen"
                                                                        "" "Howdy")
                                                (make-mongo-note-review mid2 " two " "1.12.2023"
                                                                        " ALOITUSkokous " "lopullinen"
                                                                        nil "")
                                                (make-mongo-note-review mid3 " three " "1.12.2023"
                                                                        "osittainen loppukatselmus"
                                                                        "lopullinen"
                                                                        "" "All good"))
                              (make-kuntagml xr1
                                             (make-xml-note-review "Two" "1.12.2023" " aloituskokous "
                                                                   "lopullinen" nil "Ni hao")
                                             xr3)))))))

(deftest find-note-matches-test
  (let [mid1        (mongo/create-id)
        mr1         (make-mongo-note-review mid1 "End of line" "1.12.2023" "loppukatselmus" "lopullinen"
                                            "extra-id" "Hello world.")
        mid2        (mongo/create-id)
        mr2         (make-mongo-note-review mid2 "End of line" "1.12.2023" "loppukatselmus" "lopullinen"
                                            nil "Foobar")
        [r1 r2]     (->> (target-tasks :note (make-application "LP-1" mr1 mr2)))
        t-id-match1 (make-xml-note-review "Something" "2.12.2023" "LoPPuKatselMUs" "osittainen"
                                          [" EXTRA-ID " "Extra"] " One ")
        t-id-match2 (make-xml-note-review "Else" "3.12.2023" "LoPPuKatselMUs" "osittainen"
                                          [mid1 "Mongoose"] " Two ")
        t-match     (make-xml-note-review " END OF LINE " "1.12.2023" "LoPPuKatselMUs" "lopullinen"
                                          nil " Three ")
        ;; Not matching
        t1          (make-xml-note-review "Out of line" "1.12.2023" "LoPPuKatselMUs" "lopullinen"
                                          nil "Four")
        t2          (make-xml-note-review "End of line" "2.12.2023" "LoPPuKatselMUs" "lopullinen"
                                          nil "Five")
        t3          (make-xml-note-review "End of line" "1.12.2023" "LoPPuKatselMUs" "osittainen"
                                          nil "Six")
        t4          (make-xml-note-review "Orphan id1" "1.12.2023" "osittainen LoPPuKatselMUs" "lopullinen"
                                          [mid1] "Seven")
        t5          (make-xml-note-review "Orphan id2" "1.12.2023" "osittainen LoPPuKatselMUs" "lopullinen"
                                          ["extra-id"] "Eight")
        note-finder (partial finder :note)]
    (testing "No matches"
      (is (nil? (note-finder [r1 r2] t1 t2 t3 t4 t5))))

    (testing "Match"
      (is (= [[r1 {:name  "something"  :date     (date/zoned-date-time "2.12.2023")
                   :scope "osittainen" :other-id "extra-id"
                   :note  "One"        :type     "loppukatselmus"}]]
             (note-finder [r1 r2] t1 t2 t-id-match1 t3 t4 t5)))

      (is (= [[r1 {:name  "else"       :date     (date/zoned-date-time "3.12.2023")
                   :scope "osittainen" :other-id mid1
                   :note  "Two"        :type     "loppukatselmus"}]]
             (note-finder [r2 r1] t1 t2 t-id-match2 t3 t4 t5)))

      (is (= [[r1 {:name "end of line" :date  (date/zoned-date-time "1.12.2023")
                   :note "Three"       :scope "lopullinen"
                   :type "loppukatselmus"}]]
             (note-finder [r1] t1 t2 t-match t3 t4 t5)))

      (is (= [[r2 {:name "end of line" :date  (date/zoned-date-time "1.12.2023")
                   :note "Three"       :scope "lopullinen"
                   :type "loppukatselmus"}]]
             (note-finder [r2] t1 t2 t-match t3 t4 t5)))

      (is (midje? (just [r1 {:name  "something"  :date     (date/zoned-date-time "2.12.2023")
                             :scope "osittainen" :other-id "extra-id"
                             :note  "One"        :type     "loppukatselmus"}]
                        [r2 {:name "end of line" :date  (date/zoned-date-time "1.12.2023")
                             :note "Three"       :scope "lopullinen"
                             :type "loppukatselmus"}]
                        :in-any-order)
                  (note-finder [r1 r2] t1 t2 t-match t3 t-id-match1 t4 t5))))

    (testing "Id match is preferred"
      (is (= [[r1 {:name  "something"  :date     (date/zoned-date-time "2.12.2023")
                   :scope "osittainen" :other-id "extra-id"
                   :note  "One"        :type     "loppukatselmus"}]]
             (note-finder [r1] t-match t-id-match1))))))

(def review-matcher #'review-fix/review-matcher)

(deftest review-matcher-test
  (let [mid           (mongo/create-id)
        runeberg      (date/zoned-date-time "5.2.2025")
        sample-review {:other-id "rev-123"
                       :mongo-id mid
                       :date     runeberg
                       :scope    "lopullinen"
                       :type     "muu katselmus"
                       :name     "main building review"}]

    (testing "matching by :other-id"
      (let [matcher (review-matcher sample-review :other-id)]
        ;; should match when other-id matches exactly
        (is (true? (matcher {:other-id "rev-123"})))

        ;; should match when other-id matches mongo-id
        (is (true? (matcher {:other-id mid})))

        ;; should not match on different id
        (is (false? (matcher {:other-id "different-id"})))))

    (testing "matching by :date"
      (let [matcher (review-matcher sample-review :date)]
        ;; should match when dates are equal
        (is (true? (matcher {:date runeberg})))

        ;; should not match with different date
        (is (false? (matcher {:date (date/zoned-date-time "2022-12-01")})))))

    (testing "Matching by multiple criteria"
      (let [matcher (review-matcher sample-review :other-id :type :name)]
        ;; Should match when all criteria match
        (is (true? (matcher {:other-id "rev-123"
                             :type     "muu katselmus"
                             :name     "main building review"})))

        ;; Should not match when any criteria mismatches
        (is (false? (matcher {:other-id "rev-123"
                              :type     "loppukatselmus"
                              :name     "main building review"})))))

    (testing "String equality edge cases"
      (let [blank-review {:other-id ""
                          :mongo-id mid
                          :scope    "  "
                          :type     nil}
            matcher      (review-matcher blank-review :scope :type)]

        ;; Both blank strings should not match (per str-eq? logic)
        (is (false? (matcher {:scope "  "})))

        ;; nil and blank should not match
        (is (false? (matcher {:type ""})))))

    (testing "Unknown key criteria"
      ;; Using an unsupported key should always return false
      (let [matcher (review-matcher sample-review :unknown-key)]
        (is (false? (matcher {:unknown-key "anything"})))))

    (testing "Multiple matches combined with AND logic"
      (let [matcher (review-matcher sample-review :other-id :name)]
        ;; Both criteria must match
        (is (true? (matcher {:other-id "rev-123"
                             :name     "main building review"})))

        ;; If any fails, result is false
        (is (false? (matcher {:other-id "rev-123"
                              :name     "different name"})))))))
