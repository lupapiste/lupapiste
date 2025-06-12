(ns lupapalvelu.ui.docgen.field.huoneistot.muutostapa-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.ui.docgen.field.huoneistot.muutostapa :as muutostapa]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [sade.util :as util]))

(use-fixtures :once test-utils/js-services-fixture)

(deftest resolve-muutostapa-test
  ;; Not sure if all these permutations are actually possible
  (testing "is change permit"
    (let [change-permit? true]
      (testing "is edited"
        (let [change-type :edited]
          (testing "is new row => always ADDED"
            (let [new-row? true]
              (doseq [source-value [muutostapa/added
                                    muutostapa/edited
                                    muutostapa/removed]]
                (is (= muutostapa/added
                       (muutostapa/resolve-muutostapa
                         (util/mmap source-value change-permit? change-type new-row?)))))))
          (testing "not new row"
            (let [new-row? false]
              (is (= muutostapa/added
                     (muutostapa/resolve-muutostapa
                       (util/mmap {:source-value muutostapa/added}
                                  change-permit? change-type new-row?))))
              (is (= muutostapa/edited
                     (muutostapa/resolve-muutostapa
                       (util/mmap {:source-value muutostapa/edited}
                                  change-permit? change-type new-row?))
                     (muutostapa/resolve-muutostapa
                       (util/mmap {:source-value muutostapa/removed}
                                  change-permit? change-type new-row?))))))))
      ;; `:edited` and `:reverted` are identical? it seems so.
      (testing "is reverted"
        (let [change-type :reverted]
          (testing "is new row => always ADDED"
            (let [new-row? true]
              (doseq [source-value [muutostapa/added
                                    muutostapa/edited
                                    muutostapa/removed]]
                (is (= muutostapa/added
                       (muutostapa/resolve-muutostapa
                         (util/mmap source-value change-permit? change-type new-row?)))))))
          (testing "not new row"
            (let [new-row? false]
              (is (= muutostapa/added
                     (muutostapa/resolve-muutostapa
                       (util/mmap {:source-value muutostapa/added}
                                  change-permit? change-type new-row?))))
              (is (= muutostapa/edited
                     (muutostapa/resolve-muutostapa
                       (util/mmap {:source-value muutostapa/edited}
                                  change-permit? change-type new-row?))
                     (muutostapa/resolve-muutostapa
                       (util/mmap {:source-value muutostapa/removed}
                                  change-permit? change-type new-row?))))))))
      (testing "neither edited nor reverted"
        (let [change-type nil]
          (testing "is new row => always ADDED"
            (let [new-row? true]
              (doseq [source-value [muutostapa/added
                                    muutostapa/edited
                                    muutostapa/removed]]
                (is (= muutostapa/added
                       (muutostapa/resolve-muutostapa
                         (util/mmap source-value change-permit? change-type new-row?)))))))
          (testing "not new row => reset to source value"
            (let [new-row? false]
              (doseq [source-value [muutostapa/added
                                    muutostapa/edited
                                    muutostapa/removed]]
                (is (= source-value
                       (muutostapa/resolve-muutostapa
                         (util/mmap source-value change-permit? change-type new-row?)))))))))))
  (testing "is not change permit"
    (let [change-permit? false]
      (testing "no current-value"
        (testing "is edited"
          (let [change-type :edited]
            (testing "is new row => always ADDED"
              (let [new-row? true]
                (doseq [source-value [muutostapa/added
                                      muutostapa/edited
                                      muutostapa/removed]]
                  (is (= muutostapa/added
                         (muutostapa/resolve-muutostapa
                           (util/mmap source-value change-permit? change-type new-row?)))))))
            (testing "not new row"
              (let [new-row? false]
                (is (= muutostapa/edited
                       (muutostapa/resolve-muutostapa
                         (util/mmap {:source-value muutostapa/added}
                                    change-permit? change-type new-row?))))
                (is (= muutostapa/edited
                       (muutostapa/resolve-muutostapa
                         (util/mmap {:source-value muutostapa/edited}
                                    change-permit? change-type new-row?))
                       (muutostapa/resolve-muutostapa
                         (util/mmap {:source-value muutostapa/removed}
                                    change-permit? change-type new-row?))))))))
        ;; `:edited` and `:reverted` are identical? it seems so.
        (testing "is reverted"
          (let [change-type :reverted]
            (testing "is new row => always ADDED"
              (let [new-row? true]
                (doseq [source-value [muutostapa/added
                                      muutostapa/edited
                                      muutostapa/removed]]
                  (is (= muutostapa/added
                         (muutostapa/resolve-muutostapa
                           (util/mmap source-value change-permit? change-type new-row?)))))))
            (testing "not new row"
              (let [new-row? false]
                (is (nil? (muutostapa/resolve-muutostapa
                            (util/mmap {:source-value muutostapa/added}
                                       change-permit? change-type new-row?))))
                (is (= muutostapa/edited
                       (muutostapa/resolve-muutostapa
                         (util/mmap {:source-value muutostapa/edited}
                                    change-permit? change-type new-row?))))
                (is (= muutostapa/removed
                       (muutostapa/resolve-muutostapa
                         (util/mmap {:source-value muutostapa/removed}
                                    change-permit? change-type new-row?))))))))
        (testing "neither edited nor reverted"
          (let [change-type nil]
            (testing "is new row => always ADDED"
              (let [new-row? true]
                (doseq [source-value [muutostapa/added
                                      muutostapa/edited
                                      muutostapa/removed]]
                  (is (= muutostapa/added
                         (muutostapa/resolve-muutostapa
                           (util/mmap source-value change-permit? change-type new-row?)))))))
            (testing "not new row"
              (let [new-row? false]
                (is (nil? (muutostapa/resolve-muutostapa
                            (util/mmap {:source-value muutostapa/added}
                                       change-permit? change-type new-row?))))
                (is (= muutostapa/edited
                       (muutostapa/resolve-muutostapa
                         (util/mmap {:source-value muutostapa/edited}
                                    change-permit? change-type new-row?))))
                (is (nil? (muutostapa/resolve-muutostapa
                            (util/mmap {:source-value muutostapa/removed}
                                       change-permit? change-type new-row?)))))))))
      (testing "current-value ADDED"
        (let [current-value muutostapa/added]
          (testing "is edited"
            (let [change-type :edited]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/added}
                                      current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/removed}
                                      current-value change-permit? change-type new-row?))))))))
          ;; `:edited` and `:reverted` are identical? it seems so.
          (testing "is reverted"
            (let [change-type :reverted]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/added}
                                         current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/removed
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/removed}
                                      current-value change-permit? change-type new-row?))))))))
          (testing "neither edited nor reverted"
            (let [change-type nil]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/added}
                                         current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))))
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/removed}
                                         current-value change-permit? change-type new-row?))))))))))
      (testing "current-value EDITED"
        (let [current-value muutostapa/edited]
          (testing "is edited"
            (let [change-type :edited]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/added}
                                      current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/removed}
                                      current-value change-permit? change-type new-row?))))))))
          ;; `:edited` and `:reverted` are identical? it seems so.
          (testing "is reverted"
            (let [change-type :reverted]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/added}
                                         current-value change-permit? change-type new-row?))))
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/edited}
                                         current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/removed
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/removed}
                                      current-value change-permit? change-type new-row?))))))))
          (testing "neither edited nor reverted"
            (let [change-type nil]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/added}
                                         current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))))
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/removed}
                                         current-value change-permit? change-type new-row?))))))))))
      (testing "current-value REMOVED"
        (let [current-value muutostapa/removed]
          (testing "is edited"
            (let [change-type :edited]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/added}
                                      current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/removed}
                                      current-value change-permit? change-type new-row?))))))))
          ;; `:edited` and `:reverted` are identical? it seems so.
          (testing "is reverted"
            (let [change-type :reverted]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/added}
                                         current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))))
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/removed}
                                         current-value change-permit? change-type new-row?))))))))
          (testing "neither edited nor reverted"
            (let [change-type nil]
              (testing "is new row => always ADDED"
                (let [new-row? true]
                  (doseq [source-value [muutostapa/added
                                        muutostapa/edited
                                        muutostapa/removed]]
                    (is (= muutostapa/added
                           (muutostapa/resolve-muutostapa
                             (util/mmap current-value source-value change-permit? change-type new-row?)))))))
              (testing "not new row"
                (let [new-row? false]
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/added}
                                         current-value change-permit? change-type new-row?))))
                  (is (= muutostapa/edited
                         (muutostapa/resolve-muutostapa
                           (util/mmap {:source-value muutostapa/edited}
                                      current-value change-permit? change-type new-row?))))
                  (is (nil? (muutostapa/resolve-muutostapa
                              (util/mmap {:source-value muutostapa/removed}
                                         current-value change-permit? change-type new-row?)))))))))))))
