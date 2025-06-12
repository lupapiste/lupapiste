(ns lupapalvelu.conversion.review-fix-i-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp]
            [lupapalvelu.conversion.other :as other]
            [lupapalvelu.conversion.review-fix :as review-fix]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [local-fixture-factory midje?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review-fix-test-util :refer :all]
            [midje.sweet :refer [just]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.xml :as xml]))

(use-fixtures :each (local-fixture-factory :db-name "test_conversion-review-fix"))

(def reviews-updates #'review-fix/reviews-updates)

(def end-reviews-updates (partial reviews-updates :end-review))

(deftest fix-end-reviews-test
  (let [mid1 (mongo/create-id)
        mid2 (mongo/create-id)
        mid3 (mongo/create-id)
        b1   {:vtj-prt "house-1" :kspr "city-1"}
        b2   {:vtj-prt "house-2" :kspr "city-2"}
        b3   {:vtj-prt "house-3" :kspr "city-3"}
        app1 (make-application "LP-001"
                               (make-mongo-review mid1 "One" "1.10.2023" "aloituskokous"
                                                  "lopullinen" "one-id"
                                                  b1 b2 b3)
                               (make-mongo-review mid2 "Two" "1.11.2023" "osittainen loppukatselmus"
                                                  "osittainen" "two-id"
                                                  (assoc b1 :scope "osittainen" :in-use? true)
                                                  b2 b3)
                               (make-mongo-review mid3 "Three" "1.12.2023" "loppukatselmus"
                                                  "lopullinen" "three-id"
                                                  b1
                                                  (assoc b2 :in-use? true :scope "lopullinen")
                                                  b3))
        xml1 (make-kuntagml (make-xml-review "ONE" "1.10.2023" "ALOITUSKOKOUS"
                                             "LOPULLINEN" nil
                                             (assoc b1 :scope "osittainen" :in-use true)
                                             b2 b3)
                            (make-xml-review "TWO" "1.11.2023" "OSITTAINEN LOPPUKATSELMUS"
                                             "OSITTAINEN" [mid3 "Lupapiste"]
                                             (assoc b1 :scope "osittainen" :in-use true)
                                             b2 b3)
                            (make-xml-review "THREE" "1.12.2023" "LOPPUKATSELMUS"
                                             "LOPULLINEN" nil
                                             b1
                                             (assoc b2 :scope "osittainen" :in-use false)
                                             b3))]
    (testing "reviews-updates"
      (is (= {mid3 {:tasks.$.data.rakennus.0.tila.kayttoonottava.value true
                    :tasks.$.data.rakennus.0.tila.tila.value           "osittainen"}}
             (end-reviews-updates app1 xml1))))

    (testing "other fix-reviews"
      (mongo/insert :applications (-> app1
                                      (assoc :state :closed
                                             :organization "753-R"
                                             :permitType :R)
                                      (update :tasks (fn [tasks]
                                                       (map #(update % :data tools/wrapped) tasks)))))
      (with-redefs [krysp/get-application-xml-by-application-id (constantly xml1)]
        (is (nil? (other/execute-other-command {:cmd             :fix-end-reviews
                                                :organization-id "753-R"
                                                :cmd-help        (fn [_ msg] msg)
                                                :system          (test-system/get-common-components)}))))

      (let [{notes :_sheriff-notes
             tasks :tasks} (mongo/by-id :applications (:id app1))]
        (is (midje? (just (just {:note    (format "End review fix: Task %s." mid3)
                                 :created pos?}))
                    notes))

        (is (= {:kayttoonottava {:value true}
                :tila           {:value "osittainen"}}
               (->> (util/find-by-id mid3 tasks) :data :rakennus :0 :tila)))))))

;; -------------------------
;; Fix review notes
;; -------------------------

(let [mid1       (mongo/create-id)
      mid2       (mongo/create-id)
      mid3       (mongo/create-id)
      xr1        (make-xml-note-review "One" "30.11.2023" "loppukatselmus"
                                       "osittainen" ["my-id" "foobar"] " Howdy ")
      xr2        (make-xml-note-review "Two" "1.12.2023" " aloituskokous "
                                       "lopullinen" nil "Ni hao")
      xr3        (make-xml-note-review "Three" "1.12.2023" "osittainen loppukatselmus"
                                       " lopullinen " nil "Yeah")
      xml        (make-kuntagml xr1 xr2 xr3)
      mr1        (make-mongo-note-review mid1 " one "
                                         "30.11.2023"
                                         "loppukatselmus" "osittainen"
                                         "" "Howdy")
      mr2        (make-mongo-note-review mid2 " two " "1.12.2023"
                                         " ALOITUSkokous " "lopullinen"
                                         nil "")
      mr3        (make-mongo-note-review mid3 " three " "1.12.2023"
                                         "osittainen loppukatselmus"
                                         "lopullinen"
                                         "" "All good")
      app        (assoc (make-application "LP-002" mr1 mr2 mr3)
                        :verdicts [{:kuntalupatunnus "stamp"}])
      insert-app (fn []
                   (mongo/insert :applications
                                 (-> app
                                     (assoc :organization "753-R"
                                            :permitType :R)
                                     (update :tasks (fn [tasks]
                                                      (map #(update % :data tools/wrapped)
                                                           tasks))))))
      get-note   (fn [{:keys [tasks]} task-id]
                   (->> (util/find-by-id task-id tasks)
                        :data :katselmus :huomautukset :kuvaus :value))
      exec-test  (fn [fetch?]
                   (is (nil? (other/execute-other-command
                               (util/assoc-when {:cmd             :fix-review-notes
                                                 :organization-id "753-R"
                                                 :cmd-help        (fn [_ msg] msg)
                                                 :system          (test-system/get-common-components)}
                                                :fetch fetch?))))
                   (let [{notes :_sheriff-notes :as app} (mongo/by-id :applications (:id app))]
                     (is (midje? (just (just {:note    (format "Note fix: Tasks %s, %s." mid2 mid3)
                                              :created pos?}))
                                 notes))
                     (is (= "Ni hao" (get-note app mid2)))
                     (is (= "All good

------------------------

Yeah" (get-note app mid3)))))]
  (deftest fix-review-notes-test
    (insert-app)
    (mongo/insert :conversion {:id           (mongo/create-id)
                               :backend-id   "stamp"
                               :permit-type  "R"
                               :organization "753-R"
                               :year         2025
                               :kuntagml     {:xml (xml/element-to-string xml)}})
    (exec-test nil))

  (deftest fix-review-notes-fetch-test
    (insert-app)
    (with-redefs [krysp/get-application-xml-by-application-id (constantly xml)]
      (exec-test true))))

;; -------------------------
;; Fix  missing reviews
;; -------------------------

(let [mid1        (mongo/create-id)
      xr1         (make-xml-done-review "One" "30.11.2023" "loppukatselmus"
                                        "osittainen" ["my-id" "foobar"] "Alice")
      xr2         (make-xml-done-review "Two" "1.12.2023" " aloituskokous "
                                        "lopullinen" nil "Bob")
      xml         (make-kuntagml xr1 xr2)
      mr1         (make-mongo-done-review mid1 " one "
                                          "30.11.2023"
                                          "loppukatselmus" "osittainen"
                                          "" "Alice")
      app         (assoc (make-application "LP-003" mr1)
                         :verdicts [{:kuntalupatunnus "stomp"}])
      insert-app  (fn []
                    (mongo/insert :applications
                                  (-> app
                                      (assoc :organization "753-R"
                                             :permitType :R
                                             :state :closed)
                                      (update :tasks (fn [tasks]
                                                       (map #(update % :data tools/wrapped)
                                                            tasks))))))
      exec-test   (fn [note? fetch? states]
                    (is (nil? (other/execute-other-command
                                (util/assoc-when {:cmd             :fix-missing-reviews
                                                  :organization-id "753-R"
                                                  :cmd-help        (fn [_ msg] msg)
                                                  :system          (test-system/get-common-components)}
                                                 :fetch fetch?
                                                 :states states))))
                    (let [{notes :_sheriff-notes
                           tasks :tasks} (mongo/by-id :applications (:id app))]
                      (if note?
                        (is (midje? (just (just {:note    (format "Review fix: Task %s."
                                                                  (-> tasks last :id))
                                                 :created pos?}))
                                    notes))
                        (is (nil? notes)))))
      clear-notes #(mongo/update-by-id :applications (:id app)
                                       {$unset {:_sheriff-notes true}})]
  (deftest fix-missing-reviews-test
    (insert-app)
    ;; No corresponding conversion document
    (exec-test false false nil)
    (mongo/insert :conversion {:id           (mongo/create-id)
                               :backend-id   "stomp"
                               :permit-type  "R"
                               :organization "753-R"
                               :year         2025
                               :kuntagml     {:xml (xml/element-to-string xml)}})
    ;; Non-matching states
    (exec-test false false [:inUse :constructionStarted])
    ;; Default state (`closed`) matches
    (exec-test true false nil)
    (clear-notes)
    ;; Nothing to do
    (exec-test false false nil))

  (deftest fix-missing-reviews-fetch-test
    (insert-app)
    (with-redefs [krysp/get-application-xml-by-application-id (constantly xml)]
      (exec-test false true [:inUse :constructionStarted])
      (exec-test true true nil)
      (clear-notes)
      (exec-test false true nil))))
