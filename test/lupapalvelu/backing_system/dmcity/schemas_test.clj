(ns lupapalvelu.backing-system.dmcity.schemas-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.backing-system.dmcity.schemas :as dm-sc]
            [sade.env :as env]))

(deftest schema-functions-test
  (testing "supported-permit-type?"
    (is (true? (dm-sc/supported-permit-type? "R")))
    (is (true? (dm-sc/supported-permit-type? :YA)))
    (is (false? (dm-sc/supported-permit-type? "A")))
    (is (false? (dm-sc/supported-permit-type? :VVL)))
    (is (false? (dm-sc/supported-permit-type? "")))
    (is (false? (dm-sc/supported-permit-type? nil))))

  (testing "default-path"
    (is (= "Rakennusvalvontapaatos" (dm-sc/default-path :R :application)))
    (is (= "Katselmustieto" (dm-sc/default-path "R" "review")))

    (is (= "Poikkeamispaatos" (dm-sc/default-path "P" :application)))
    (is (= "Katselmustieto" (dm-sc/default-path :P "review")))

    (is (= "YleistenAlueidenPaatos" (dm-sc/default-path :YA :application)))
    (is (thrown-with-msg? Exception
                          #"Unsupported dmcity target \(:YA\) and/or path \(:review\)"
                          (dm-sc/default-path :YA :review)))

    (is (= "HakemuksenTilapaivitys" (dm-sc/default-path :state "state")))

    (is (= "RakennuslupienKonversio" (dm-sc/default-path :conversion :R)))
    (is (thrown? IllegalArgumentException (dm-sc/default-path :conversion :P)))
    (is (thrown? IllegalArgumentException (dm-sc/default-path :conversion :YA)))
    (is (= "HakemuksenTilapaivitysKonversio" (dm-sc/default-path :conversion :state)))

    (is (thrown-with-msg? Exception
                          #"Unsupported dmcity target \(:foo\) and/or path \(:bar\)"
                          (dm-sc/default-path :foo :bar)))
    (is (thrown-with-msg? Exception
                          #"Unsupported dmcity target \(null\) and/or path \(null\)"
                          (dm-sc/default-path nil nil))))

  (testing "schema-path?"
    (is (true? (dm-sc/schema-path? :vault)))
    (is (true? (dm-sc/schema-path? :targets :R :application)))
    (is (true? (dm-sc/schema-path? :targets [:state [:state]])))
    (is (true? (dm-sc/schema-path? [[[[["targets"]]][[[[["P" [[[["review"]]]]]]]]]]])))

    (is (not (dm-sc/schema-path? :targets :R :foobar)))
    (is (not (dm-sc/schema-path? :targets [[["YA"]]] :review)))
    (is (true? (dm-sc/schema-path? :targets :link)))
    (is (not (dm-sc/schema-path? nil)))
    (is (not (dm-sc/schema-path?)))
    (is (not (dm-sc/schema-path? [[[[[]]]]]))))

  (testing "target-key?"
    (is (true? (dm-sc/target-key? :R)))
    (is (true? (dm-sc/target-key? "P")))
    (is (true? (dm-sc/target-key? [[[[["YA"]]]]])))
    (is (true? (dm-sc/target-key? [:conversion])))
    (is (true? (dm-sc/target-key? :state)))
    (is (true? (dm-sc/target-key? :link)))

    (is (not (dm-sc/target-key? :vault)))
    (is (not (dm-sc/target-key? nil))))

  (testing "review-support?"
    (is (true? (dm-sc/review-support? "R")))
    (is (true? (dm-sc/review-support? :P)))

    (is (not (dm-sc/review-support? :YA)))
    (is (not (dm-sc/review-support? "state")))
    (is (not (dm-sc/review-support? "conversion"))))

  (testing "skipped-state?"
    (is (true? (dm-sc/skipped-state? "draft")))
    (is (true? (dm-sc/skipped-state? :draft)))
    (is (true? (dm-sc/skipped-state? "open")))
    (is (true? (dm-sc/skipped-state? :open)))
    (is (false? (dm-sc/skipped-state? nil)))
    (is (false? (dm-sc/skipped-state? "")))
    (is (false? (dm-sc/skipped-state? :submitted)))
    (is (false? (dm-sc/skipped-state? "foo")))))

(defn refresh-ns
  "Schemas ns must be reloaded when the feature flag is toggled."
  []
  (require '[lupapalvelu.backing-system.dmcity.schemas :as dm-sc] :reload))

(deftest feature-flag-test
  (env/with-feature-value :dmcity-extra-permit-types false
    (refresh-ns)
    (testing "Extra permit types flag is false"
      (is (false? (dm-sc/supported-permit-type? "MM")))
      (is (false? (dm-sc/supported-permit-type? :KT)))
      (is (thrown-with-msg? Exception
                            #"Unsupported dmcity target \(:KT\) and/or path \(:application\)"
                            (dm-sc/default-path :KT :application)))
      (is (thrown-with-msg? Exception
                            #"Unsupported dmcity target \(:MM\) and/or path \(:application\)"
                            (dm-sc/default-path "MM" "application")))
      (is (not (dm-sc/schema-path? :targets :KT :application)))
      (is (not (dm-sc/schema-path? :targets "MM" "application")))
      (is (not (dm-sc/target-key? :KT)))
      (is (not (dm-sc/target-key? "MM")))
      (is (not (dm-sc/review-support? :KT)))
      (is (not (dm-sc/review-support? "MM")))
      (doseq [pt [:YI "YM" :MAL "VVVL" :YL]]
        (is (not (dm-sc/supported-permit-type? pt)))
        (is (thrown? Exception (dm-sc/default-path pt :application)))
        (is (not (dm-sc/schema-path? :targets pt "application")))
        (is (not (dm-sc/target-key? pt)))
        (is (not (dm-sc/review-support? pt))))))
  (env/with-feature-value :dmcity-extra-permit-types true
    (refresh-ns)
    (testing "Extra permit types flag is true"
      (is (true? (dm-sc/supported-permit-type? "MM")))
      (is (true? (dm-sc/supported-permit-type? :KT)))
      (is (= "KiinteistonmuodostusHakemus" (dm-sc/default-path :KT :application)))
      (is (= "KiinteistonmuodostusHakemus" (dm-sc/default-path "MM" "application")))
      (is (true? (dm-sc/schema-path? :targets :KT :application)))
      (is (true? (dm-sc/schema-path? :targets "MM" "application")))
      (is (true? (dm-sc/target-key? :KT)))
      (is (true? (dm-sc/target-key? "MM")))
      (is (not (dm-sc/review-support? :KT)))
      (is (not (dm-sc/review-support? "MM")))
      (doseq [pt [:YI "YM" :MAL "VVVL" :YL]]
        (is (true? (dm-sc/supported-permit-type? pt)))
        (is (= "YmparistoHakemus" (dm-sc/default-path pt :application)))
        (is (true? (dm-sc/schema-path? :targets pt "application")))
        (is (true? (dm-sc/target-key? pt)))
        (is (not (dm-sc/review-support? pt))))))
  ;; Just in case
  (refresh-ns))
