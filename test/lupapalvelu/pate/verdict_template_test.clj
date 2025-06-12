(ns lupapalvelu.pate.verdict-template-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.application :as app]
            [lupapalvelu.pate.schemas :refer [PateSavedTemplate]]
            [lupapalvelu.pate.settings-schemas :as settings-schemas]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-template :as verdict-template]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [monger.operators :refer :all]
            [schema.core :as sc]))

(def ^:private sync-repeatings #'verdict-template/sync-repeatings)
(def ^:private draft-for-publishing #'verdict-template/draft-for-publishing)
(def ^:private template-inclusions #'verdict-template/template-inclusions)
(def ^:private pack-dependencies #'verdict-template/pack-dependencies)
(def ^:private pack-verdict-dates #'verdict-template/pack-verdict-dates)
(def ^:private published-settings #'verdict-template/published-settings)
(def ^:private make-query #'verdict-template/make-query)
(def ^:private process-draft-dictionary #'verdict-template/process-draft-dictionary)
(def ^:private settings-key #'verdict-template/settings-key)
(def ^:private template-date-set? #'verdict-template/template-date-set?)

(deftest sync-repeatings-test
  (testing "Initially empty"
    (is (= {} (sync-repeatings :reviews nil nil nil)))
    (is (= {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English"}}
           (sync-repeatings :reviews nil {:id1 {:fi "suomi" :sv "svenska" :en "English"}} nil))))
  (testing "New entry"
    (is (= {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello"}
            :id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2"}}
           (sync-repeatings :reviews nil {:id1 {:fi "suomi" :sv "svenska" :en "English"}
                                 :id2 {:fi "suomi2" :sv "svenska2" :en "English2"}}
                            {:id1 {:text-sv "svenska" :foo "hello"}}))))
  (testing "Removed entry"
    (is (= {:id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2"}}
           (sync-repeatings :reviews nil {:id2 {:fi "suomi2" :sv "svenska2" :en "English2"}}
                            {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello"}}))))
  (testing "Fullname with first-name and last-name"
    (is (= {:id1 {:text-fi "Teppo Tulppu (ABC)" :text-sv "Teppo Tulppu (ABC)" :text-en "Teppo Tulppu (ABC)"}}
           (sync-repeatings :reviews nil {:id1 {:fi "foo" :sv "bar" :en "baz" :code "ABC" :first-name "Teppo" :last-name "Tulppu"}} nil))))
  (testing "Fullname with only first-name"
    (is (= {:id1 {:text-fi "Teppo" :text-sv "Teppo" :text-en "Teppo"}}
           (sync-repeatings :reviews nil {:id1 {:fi "foo" :sv "bar" :en "baz" :first-name "Teppo"}} nil))))
  (testing "Fullname with only last-name"
    (is (= {:id1 {:text-fi "Tulppu" :text-sv "Tulppu" :text-en "Tulppu"}}
           (sync-repeatings :reviews nil {:id1 {:fi "foo" :sv "bar" :en "baz" :last-name "Tulppu"}} nil))))
  (testing "Falls back to language text when no fullname"
    (is (= {:id1 {:text-fi "foo" :text-sv "bar" :text-en "baz"}}
           (sync-repeatings :reviews nil {:id1 {:fi "foo" :sv "bar" :en "baz"}} nil))))
  (testing "Blank first-name/last-name falls back to language text"
    (is (= {:id1 {:text-fi "foo" :text-sv "bar" :text-en "baz"}}
           (sync-repeatings :reviews nil {:id1 {:fi "foo" :sv "bar" :en "baz" :first-name "" :last-name "  "}} nil))))
  (testing "Missing language falls back to localized no-name"
    (is (= {:id1 {:text-fi "foo" :text-sv "bar" :text-en "Name missing"}}
           (sync-repeatings :reviews nil {:id1 {:fi "foo" :sv "bar"}} nil)))))

(deftest sync-repeatings-preserve-manual-sort-order-test
  (testing "Initially empty"
    (is (= {} (sync-repeatings :reviews :index nil nil)))
    (is (= {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :index 0}}
           (sync-repeatings :reviews :index {:id1 {:index 0 :fi "suomi" :sv "svenska" :en "English"}} nil))))
  (testing "New entry"
    (is (= {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello" :index 2}
            :id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2" :index 1}}
           (sync-repeatings :reviews :index {:id1 {:fi "suomi" :sv "svenska" :en "English" :index 2}
                                    :id2 {:fi "suomi2" :sv "svenska2" :en "English2" :index 1}}
                            {:id1 {:text-sv "svenska" :foo "hello"}}))))
  (testing "Removed entry"
    (is (= {:id2 {:text-fi "suomi2" :text-sv "svenska2" :text-en "English2" :index 0}}
           (sync-repeatings :reviews :index {:id2 {:fi "suomi2" :sv "svenska2" :en "English2" :index 0}}
                            {:id1 {:text-fi "suomi" :text-sv "svenska" :text-en "English" :foo "hello"}})))))

(deftest sync-repeatings-verdict-givers-validity-test
  (testing "Verdict-givers get validity flags"
    (is (= {:id1 {:text-fi "Teppo Tulppu" :text-sv "Teppo Tulppu" :text-en "Teppo Tulppu"
                  :valid-for-viranhaltija true :valid-for-lautakunta true}}
           (sync-repeatings :verdict-givers nil
                            {:id1 {:fi "foo" :sv "bar" :en "baz" :first-name "Teppo" :last-name "Tulppu"}}
                            nil))))
  (testing "Verdict-giver with only language names - valid for lautakunta only"
    (is (= {:id1 {:text-fi "foo" :text-sv "bar" :text-en "baz"
                  :valid-for-viranhaltija false :valid-for-lautakunta true}}
           (sync-repeatings :verdict-givers nil
                            {:id1 {:fi "foo" :sv "bar" :en "baz"}}
                            nil))))
  (testing "Verdict-giver missing language - not valid for lautakunta"
    (is (= {:id1 {:text-fi "foo" :text-sv "bar" :text-en "Name missing"
                  :valid-for-viranhaltija false :valid-for-lautakunta false}}
           (sync-repeatings :verdict-givers nil
                            {:id1 {:fi "foo" :sv "bar"}}
                            nil))))
  (testing "Non-verdict-givers don't get validity flags"
    (is (= {:id1 {:text-fi "foo" :text-sv "bar" :text-en "baz"}}
           (sync-repeatings :reviews nil
                            {:id1 {:fi "foo" :sv "bar" :en "baz"}}
                            nil)))))

(def test-template
  {:dictionary {:one              {:toggle {}}
                :two              {:date {}}
                :three            {:text {}}
                :four             {:repeating {:four-one   {:toggle {}}
                                               :four-two   {:text {}}
                                               :four-three {:excluded? true
                                                            :date      {}}}}
                :five             {:text {}}
                :six              {:excluded? true
                                   :toggle    {}}
                :removed-sections {:keymap {:first  false
                                            :second false
                                            :third  false}}}
   :sections   [{:id   :first
                 :grid {:columns 4
                        :rows    [[{:dict :one}]]}}
                {:id   :second
                 :grid {:columns 4
                        :rows    [[{:dict :one} {:dict :two}]]}}
                {:id     :third
                 :policy :include
                 :grid   {:columns 4
                          :rows    [[{:dict :three}]
                                    [{:grid {:columns   2
                                             :repeating :four
                                             :rows      [[{:dict :four-one}
                                                          {:dict :four-two}]]}}]]}}]})

(deftest test-template-is-valid-test
  (is (= test-template
         (sc/validate shared-schemas/PateVerdictTemplate test-template))))

(deftest draft-for-publishing-test
  (with-redefs [template-schemas/verdict-template-schema (constantly test-template)]
    (testing "Simple draft"
      (is (= {:one   true
              :three "hello"}
             (draft-for-publishing {:category :r :draft {:one   true
                                                         :three "hello"}}))))
    (testing "Complex draft with removed sections and repeatings"
      (is (= {:one              true
              :removed-sections {:first  true
                                 :second false}
              :four             [{:four-one false :four-two "Yeah"}
                                 {:four-two "haeY"}]}
             (draft-for-publishing {:category :r :draft {:one              true
                                                         :two              nil
                                                         :three            ""
                                                         :removed-sections {:first  true
                                                                            :second false}
                                                         :four             {:id1 {:four-one false
                                                                                  :four-two "Yeah"}
                                                                            :id2 {:four-two   "haeY"
                                                                                  :four-three 12345}}
                                                         :six              true}}))))))

(deftest template-inclusions-test
  (with-redefs [template-schemas/verdict-template-schema (constantly test-template)]
    (testing "Every section included"
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {}})))))
    (testing "First section removed"
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first true}}})))))
    (testing "Second section removed"
      (is (= #{"one" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}})))))
    (testing "Third section removed"
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:third true}}})))))
    (testing "Every section removed"
      (is (= #{"three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}))))))
  (testing "Excluded?"
    (with-redefs [template-schemas/verdict-template-schema
                  (constantly (assoc-in test-template [:dictionary :six :excluded?] false))]
      (is (= #{"one" "two" "three" "four" "five" "six"}
             (set (template-inclusions {:category :r :draft {}})))))))

(deftest operation->category-test
  (testing "R" (is (= :r (verdict-template/operation->category "pientalo"))))
  (testing "YA" (is (= :ya (verdict-template/operation->category "ya-katulupa-kaapelityot"))))
  (testing "P" (is (= :p (verdict-template/operation->category "poikkeamis"))))
  (testing "YI" (is (= :ymp (verdict-template/operation->category "meluilmoitus"))))
  (testing "YM" (is (= :ymp (verdict-template/operation->category "koeluontoinen-toiminta"))))
  (testing "YL" (is (= :ymp (verdict-template/operation->category "pima"))))
  (testing "MAL" (is (= :ymp (verdict-template/operation->category "maa-aineslupa"))))
  (testing "VVVL" (is (= :ymp (verdict-template/operation->category "vvvl-vesijohdosta"))))
  (testing "KT keyword" (is (= :kt (verdict-template/operation->category :tonttijako))))
  (testing "MM" (is (= :kt (verdict-template/operation->category "asemakaava"))))
  (testing "Foreman" (is (= :tj (verdict-template/operation->category "tyonjohtajan-nimeaminen-v2")))))

(defn make-template [category deleted? title subtitle]
  (sc/validate PateSavedTemplate
               (merge {:id        "5eb26fd57560453adbb29d8f"
                       :name      "Name"
                       :category  category
                       :deleted   {:_value    deleted?
                                   :_user     "user"
                                   :_modified 12345}
                       :modified  12345
                       :published {:published  {:_value    12345
                                                :_user     "user"
                                                :_modified 12345}
                                   :data       {:title    title
                                                :subtitle subtitle}
                                   :inclusions []
                                   :settings   {}}})))

(deftest pack-dependencies-test
  (testing "Default sorting"
    (with-redefs [settings-schemas/settings-schema (constantly {})]
      (is (= [{:fi "K1" :sv "S1" :en "R1" :selected true}
              {:fi "K3" :sv "S3" :en "R3" :selected false}]
             (pack-dependencies :r
                                {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1"}
                                           :rid2 {:fi "K2" :sv "S2" :en "R2"}
                                           :rid3 {:fi "K3" :sv "S3" :en "R3"}}}
                                :reviews
                                {:reviews {:rid1 {:included true :selected true}
                                           :rid3 {:included true}}})))))
  (testing "Manual sorting"
    (with-redefs [settings-schemas/settings-schema
                  (constantly {:dictionary {:reviews {:sort-by {:manual :index}}}})]
      (is (= [{:fi "K3" :sv "S3" :en "R3" :selected false}
              {:fi "K1" :sv "S1" :en "R1" :selected true}]
             (pack-dependencies :r
                                {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1" :index 2}
                                           :rid2 {:fi "K2" :sv "S2" :en "R2" :index 1}
                                           :rid3 {:fi "K3" :sv "S3" :en "R3" :index 0}}}
                                :reviews
                                {:reviews {:rid1 {:included true :selected true}
                                           :rid3 {:included true}}})))))
  (testing "Include id"
    (with-redefs [settings-schemas/settings-schema (constantly {})]
      (is (= [{:fi "K1" :sv "S1" :en "R1" :code "C1" :id "rid1" :selected true}
              {:fi "K3" :sv "S3" :en "R3" :code "C3" :id "rid3" :selected false}]
             (pack-dependencies :r
                                {:verdict-givers {:rid1 {:fi   "K1" :sv "S1" :en "R1"
                                                         :code "C1"}
                                                  :rid2 {:fi   "K2" :sv "S2" :en "R2"
                                                         :code "C2"}
                                                  :rid3 {:fi   "K3" :sv "S3" :en "R3"
                                                         :code "C3"}}}
                                :verdict-givers
                                {:verdict-givers {:rid1 {:included true :selected true}
                                                  :rid3 {:included true}}}
                                true))))))

(deftest pack-verdict-dates-test
  (testing "All dates provided"
    (is (= {:aloitettava     {:delta 5 :unit "years"}
            :anto            {:delta 2 :unit "days"}
            :julkipano       {:delta 1 :unit "days"}
            :k-aloitettava   {:delta 0 :unit "years"}
            :k-kuulutus      {:delta 0 :unit "days"}
            :k-lainvoimainen {:delta 0 :unit "days"}
            :k-muutoksenhaku {:delta 0 :unit "days"}
            :k-voimassa      {:delta 0 :unit "years"}
            :lainvoimainen   {:delta 4 :unit "days"}
            :muutoksenhaku   {:delta 3 :unit "days"}
            :voimassa        {:delta 6 :unit "years"}}
           (pack-verdict-dates :r {:julkipano     1 :anto        2 :muutoksenhaku 3
                                   :lainvoimainen 4 :aloitettava 5 :voimassa      6} false))))
  (testing "Some dates missing"
    (is (= {:aloitettava     {:delta 0 :unit "years"}
            :anto            {:delta 0 :unit "days"}
            :julkipano       {:delta 1 :unit "days"}
            :k-aloitettava   {:delta 0 :unit "years"}
            :k-kuulutus      {:delta 0 :unit "days"}
            :k-lainvoimainen {:delta 0 :unit "days"}
            :k-muutoksenhaku {:delta 0 :unit "days"}
            :k-voimassa      {:delta 0 :unit "years"}
            :lainvoimainen   {:delta 4 :unit "days"}
            :muutoksenhaku   {:delta 3 :unit "days"}
            :voimassa        {:delta 6 :unit "years"}}
           (pack-verdict-dates :r {:julkipano     1 :muutoksenhaku 3
                                   :lainvoimainen 4 :voimassa      6} false))))
  (testing "Lautakunta override"
    (is (= {:aloitettava     {:delta 5 :unit "years"}
            :anto            {:delta 2 :unit "days"}
            :julkipano       {:delta 1 :unit "days"}
            :k-aloitettava   {:delta 0 :unit "years"}
            :k-kuulutus      {:delta 0 :unit "days"}
            :k-lainvoimainen {:delta 0 :unit "days"}
            :k-muutoksenhaku {:delta 0 :unit "days"}
            :k-voimassa      {:delta 0 :unit "years"}
            :lainvoimainen   {:delta 4 :unit "days"}
            :muutoksenhaku   {:delta 10 :unit "days"}
            :voimassa        {:delta 6 :unit "years"}}
           (pack-verdict-dates :r {:julkipano                1 :anto        2 :muutoksenhaku 3
                                   :lainvoimainen            4 :aloitettava 5 :voimassa      6
                                   :lautakunta-muutoksenhaku 10} true))))
  (testing "K-prefixed dates only"
    (is (= {:aloitettava     {:delta 0 :unit "years"}
            :anto            {:delta 0 :unit "days"}
            :julkipano       {:delta 0 :unit "days"}
            :k-aloitettava   {:delta 4 :unit "years"}
            :k-kuulutus      {:delta 1 :unit "days"}
            :k-lainvoimainen {:delta 3 :unit "days"}
            :k-muutoksenhaku {:delta 2 :unit "days"}
            :k-voimassa      {:delta 5 :unit "years"}
            :lainvoimainen   {:delta 0 :unit "days"}
            :muutoksenhaku   {:delta 0 :unit "days"}
            :voimassa        {:delta 0 :unit "years"}}
           (pack-verdict-dates :r {:k-kuulutus      1 :k-muutoksenhaku 2
                                   :k-lainvoimainen 3 :k-aloitettava   4
                                   :k-voimassa      5} false)))))

(def ^:private test-settings-data
  {:draft {:organization-name        "Firm"
           :verdict-code             ["one" "two" "three"]
           :plans                    {:p1 {:fi "S1" :sv "P1" :en "P1"}
                                      :p2 {:fi "S2" :sv "P2" :en "P2"}}
           :reviews                  {:r1 {:fi "K1" :sv "S1" :en "R1" :type "hello"}
                                      :r2 {:fi "K2" :sv "S2" :en "R2" :type "world"}}
           :handler-titles           {:h1 {:fi "H1" :sv "H1" :en "H1"}
                                      :h2 {:fi "H2" :sv "H2" :en "H2"}}
           :verdict-giver-titles     {:tid1 {:fi "T1" :sv "T1" :en "T1" :code "CT1"}
                                      :tid2 {:fi "T2" :sv "T2" :en "T2" :code "CT2"}}
           ;; gid1 and gid3 have first-name and last-name (valid for viranhaltija)
           ;; gid2 has only language names (valid for lautakunta only)
           :verdict-givers           {:gid1 {:fi "G1" :sv "G1" :en "G1" :code "CG1"
                                             :first-name "First1" :last-name "Last1"}
                                      :gid2 {:fi "G2" :sv "G2" :en "G2" :code "CG2"}
                                      :gid3 {:fi "G3" :sv "G3" :en "G3" :code "CG3"
                                             :first-name "First3" :last-name "Last3"}}
           :julkipano                1 :anto        2 :muutoksenhaku 3
           :lainvoimainen            4 :aloitettava 5 :voimassa      6
           :lautakunta-muutoksenhaku 10
           :boardname                "Board"}})

(deftest published-settings-test
  (with-redefs [verdict-template/settings (constantly test-settings-data)]
    (testing "No template data"
      (is (= {:verdict-code      ["one" "two" "three"]
              :organization-name "Firm"
              :date-deltas       {:aloitettava     {:delta 5 :unit "years"}
                                  :anto            {:delta 2 :unit "days"}
                                  :julkipano       {:delta 1 :unit "days"}
                                  :k-aloitettava   {:delta 0 :unit "years"}
                                  :k-kuulutus      {:delta 0 :unit "days"}
                                  :k-lainvoimainen {:delta 0 :unit "days"}
                                  :k-muutoksenhaku {:delta 0 :unit "days"}
                                  :k-voimassa      {:delta 0 :unit "years"}
                                  :lainvoimainen   {:delta 4 :unit "days"}
                                  :muutoksenhaku   {:delta 3 :unit "days"}
                                  :voimassa        {:delta 6 :unit "years"}}}
             (published-settings {} :r {}))))

    (testing "Lautakunta giver with reviews and plans"
      (is (= {:verdict-code      ["one" "two" "three"]
              :organization-name "Firm"
              :date-deltas       {:aloitettava     {:delta 5 :unit "years"}
                                  :anto            {:delta 2 :unit "days"}
                                  :julkipano       {:delta 1 :unit "days"}
                                  :k-aloitettava   {:delta 0 :unit "years"}
                                  :k-kuulutus      {:delta 0 :unit "days"}
                                  :k-lainvoimainen {:delta 0 :unit "days"}
                                  :k-muutoksenhaku {:delta 0 :unit "days"}
                                  :k-voimassa      {:delta 0 :unit "years"}
                                  :lainvoimainen   {:delta 4 :unit "days"}
                                  :muutoksenhaku   {:delta 10 :unit "days"}
                                  :voimassa        {:delta 6 :unit "years"}}
              :boardname         "Board"
              :plans             [{:fi "S2" :sv "P2" :en "P2" :selected false}]
              :reviews           [{:fi "K1" :sv "S1" :en "R1" :type "hello" :selected true}]}
             (published-settings {} :r {:giver          "lautakunta"
                                        :reviews        {:r1 {:included true :selected true}}
                                        :plans          {:p2 {:included true}}
                                        :handler-titles {:h2 {:selected true}}}))))

    (testing "Viranhaltija giver with custom date deltas and all dependencies"
      (is (= {:verdict-code         ["one" "two" "three"]
              :organization-name    "Firm"
              :date-deltas          {:aloitettava     {:delta 15 :unit "years"}
                                     :anto            {:delta 12 :unit "days"}
                                     :julkipano       {:delta 11 :unit "days"}
                                     :k-aloitettava   {:delta 0 :unit "years"}
                                     :k-kuulutus      {:delta 0 :unit "days"}
                                     :k-lainvoimainen {:delta 0 :unit "days"}
                                     :k-muutoksenhaku {:delta 0 :unit "days"}
                                     :k-voimassa      {:delta 0 :unit "years"}
                                     :lainvoimainen   {:delta 14 :unit "days"}
                                     :muutoksenhaku   {:delta 13 :unit "days"}
                                     :voimassa        {:delta 16 :unit "years"}}
              :plans                [{:fi "S2" :sv "P2" :en "P2" :selected false}]
              :reviews              [{:fi "K1" :sv "S1" :en "R1" :type "hello" :selected true}]
              :handler-titles       [{:fi "H2" :sv "H2" :en "H2" :selected true}]
              :verdict-giver-titles [{:id "tid1" :fi "T1" :sv "T1" :en "T1" :code "CT1" :selected false}]
              :verdict-givers       [{:id "gid1" :fi "G1" :sv "G1" :en "G1" :code "CG1"
                                      :first-name "First1" :last-name "Last1" :selected false}
                                     {:id "gid3" :fi "G3" :sv "G3" :en "G3" :code "CG3"
                                      :first-name "First3" :last-name "Last3" :selected true}]}
             (published-settings {} :r {:giver                "viranhaltija"
                                        :reviews              {:r1 {:included true :selected true}}
                                        :plans                {:p2 {:included true}}
                                        :handler-titles       {:h2 {:included true :selected true}}
                                        :julkipano            11 :anto        12 :muutoksenhaku 13
                                        :lainvoimainen        14 :aloitettava 15 :voimassa      16
                                        :custom-date-deltas   true
                                        :verdict-giver-titles {:tid1 {:included true}}
                                        :verdict-givers       {:gid1 {:included true}
                                                               :gid2 {:included false}
                                                               :gid3 {:included true :selected true}}}))))

    (let [template-data {:giver                "viranhaltija"
                         :reviews              {:r1 {:included true :selected true}}
                         :plans                {:p2 {:included true}}
                         :handler-titles       {:h2 {:included true :selected true}}
                         :julkipano            11 :anto        12 :muutoksenhaku 13
                         :lainvoimainen        14 :aloitettava 15 :voimassa      16
                         :custom-date-deltas   true
                         :verdict-giver-titles {:tid1 {:included true}}
                         :verdict-givers       {:gid1 {:included true}}}
          removable     [:plans :reviews :verdict-givers :verdict-giver-titles]]
      (testing "Removed sections"
        (testing "Nothing removed"
          (is (= (set removable)
                 (-> (published-settings {} :r (assoc template-data :removed-sections
                                                      (zipmap removable (repeat false))))
                     (select-keys removable)
                     keys
                     set))))
        (testing "Everything removed"
          (let [result (select-keys (published-settings {} :r (assoc template-data :removed-sections
                                                                     (zipmap removable (repeat true))))
                                    removable)]
            (is (= #{:plans :reviews} (set (keys result))))
            (is (some? (:plans result)))
            (is (some? (:reviews result)))))))))

(deftest make-query-test
  (testing "Empty options"
    (is (= {:organization "123-R"}
           (make-query "123-R" {}))))

  (testing "Published and deleted with collections"
    (is (= {:organization   "123-R"
            :published      {$exists true}
            :deleted._value {$ne true}
            :_id            {$in ["LP-1" "LP-2"]}
            :category       {$in [:r :oik]}}
           (make-query "123-R" {:published? true            :deleted? true
                                :id         ["LP-1" "LP-2"] :category [:r :oik]}))))

  (testing "Not published, not deleted, scalar id and category"
    (is (= {:organization   "123-R"
            :published      {$exists false}
            :deleted._value {$ne true}
            :_id            "LP-1"
            :category       :r}
           (make-query "123-R" {:published? false  :deleted? false
                                :id         "LP-1" :category :r}))))

  (testing "Nil values and empty collections are ignored"
    (is (= {:organization "123-R"}
           (make-query "123-R" {:published? nil :deleted? nil
                                :id         []  :category nil}))))

  (testing "Published implies deleted override"
    (is (= {:organization   "123-R"
            :published      {$exists true}
            :deleted._value {$ne true}
            :category       :r}
           (make-query "123-R" {:published? true :category :r}))))

  (testing "Deleted only (without published)"
    (is (= {:organization   "123-R"
            :deleted._value true}
           (make-query "123-R" {:deleted? true}))))

  (testing "Single id is not wrapped in $in"
    (is (= {:organization "123-R"
            :_id          "LP-1"}
           (make-query "123-R" {:id "LP-1"}))))

  (testing "Single category keyword is not wrapped in $in"
    (is (= {:organization "123-R"
            :category     :r}
           (make-query "123-R" {:category :r})))))

;; --------- changes-response ---------

(deftest changes-response-test
  (testing "Add operation appends to changes"
    (is (= {:changes [[:existing :path] [[:new :path] "value"]]}
           (verdict-template/changes-response
             {:changes [[:existing :path]]}
             {:op :add :path [:new :path] :value "value"}))))

  (testing "Add with nil initial changes"
    (is (= {:changes [[[:p] "v"]]}
           (verdict-template/changes-response
             {}
             {:op :add :path [:p] :value "v"}))))

  (testing "Remove operation appends to removals"
    (is (= {:removals [[:old] [:removed :path]]}
           (verdict-template/changes-response
             {:removals [[:old]]}
             {:op :remove :path [:removed :path]}))))

  (testing "Remove with nil initial removals"
    (is (= {:removals [[:removed :path]]}
           (verdict-template/changes-response
             {}
             {:op :remove :path [:removed :path]}))))

  (testing "Order operation flattens value entries into changes"
    (is (= {:changes [[[:root :k1 :d1] "v1"]]}
           (verdict-template/changes-response
             {}
             {:op    :order
              :path  [:root]
              :value {:k1 {:d1 "v1"}}}))))

  (testing "Unknown operation returns response unchanged"
    (is (= {:foo :bar}
           (verdict-template/changes-response
             {:foo :bar}
             {:op :unknown :path [:x] :value "y"})))))

;; --------- verdict-template-summary ---------

(deftest verdict-template-summary-test
  (testing "Extracts summary fields and published timestamp"
    (is (= {:id        "id1"
            :name      "Template"
            :modified  12345
            :deleted   false
            :category  :r
            :published 67890}
           (verdict-template/verdict-template-summary
             {:id        "id1"
              :name      "Template"
              :modified  12345
              :deleted   false
              :category  :r
              :published {:published 67890
                          :data      {:title "T"}
                          :settings  {}}
              :draft     {:some "data"}}))))

  (testing "Nil published when no published data"
    (is (= {:id        "id1"
            :name      "Name"
            :modified  0
            :deleted   true
            :category  :ya
            :published nil}
           (verdict-template/verdict-template-summary
             {:id       "id1"
              :name     "Name"
              :modified 0
              :deleted  true
              :category :ya}))))

  (testing "Extra keys in template are not included"
    (let [result (verdict-template/verdict-template-summary
                   {:id "x" :name "n" :modified 1 :deleted false :category :r
                    :draft {:foo :bar} :organization "org-1" :extra-key true})]
      (is (not (contains? result :draft)))
      (is (not (contains? result :organization)))
      (is (not (contains? result :extra-key))))))

;; --------- process-draft-dictionary ---------

(deftest process-draft-dictionary-test
  (testing "Empty data returns empty map"
    (is (= {} (process-draft-dictionary {:one {:toggle {}}} {}))))

  (testing "Nil and blank string values are excluded"
    (is (= {} (process-draft-dictionary {:one {:text {}}
                                         :two {:date {}}}
                                        {:one nil :two ""}))))

  (testing "Boolean false is included (not blank)"
    (is (= {:flag false}
           (process-draft-dictionary {:flag {:toggle {}}} {:flag false}))))

  (testing "Non-repeating values pass through"
    (is (= {:one true :two "hello"}
           (process-draft-dictionary {:one {:toggle {}} :two {:text {}}}
                                     {:one true :two "hello"}))))

  (testing "Repeating values are transformed from map-of-maps to seq-of-maps"
    (is (= {:items [{:a "x"} {:a "y"}]}
           (process-draft-dictionary
             {:items {:repeating {:a {:text {}}}}}
             {:items {:id1 {:a "x"} :id2 {:a "y"}}}))))

  (testing "Empty repeating map is excluded"
    (is (= {} (process-draft-dictionary
                {:items {:repeating {:a {:text {}}}}}
                {:items {}}))))

  (testing "Keys not in dictionary are excluded"
    (is (= {:known "v"}
           (process-draft-dictionary {:known {:text {}}}
                                     {:known "v" :unknown "x"})))))

;; --------- sync-repeatings edge cases ---------

(deftest sync-repeatings-edge-cases-test
  (testing "Both settings and template are empty maps"
    (is (= {} (sync-repeatings :reviews nil {} {}))))

  (testing "Settings entry overwrites all language keys in template"
    (is (= {:id1 {:text-fi "new-fi" :text-sv "new-sv" :text-en "new-en"}}
           (sync-repeatings :reviews nil
                            {:id1 {:fi "new-fi" :sv "new-sv" :en "new-en"}}
                            {:id1 {:text-fi "old-fi" :text-sv "old-sv" :text-en "old-en"}}))))

  (testing "Template extra fields are preserved when syncing"
    (is (= {:id1 {:text-fi "fi" :text-sv "sv" :text-en "en"
                  :custom-field "kept" :another 42}}
           (sync-repeatings :reviews nil
                            {:id1 {:fi "fi" :sv "sv" :en "en"}}
                            {:id1 {:text-fi "old" :text-sv "old" :text-en "old"
                                   :custom-field "kept" :another 42}})))))

;; --------- pack-dependencies edge cases ---------

(deftest pack-dependencies-edge-cases-test
  (testing "No items included returns nil"
    (with-redefs [settings-schemas/settings-schema (constantly {})]
      (is (nil? (pack-dependencies :r
                                   {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1"}}}
                                   :reviews
                                   {:reviews {:rid1 {:included false}}})))))

  (testing "Empty template data returns nil"
    (with-redefs [settings-schemas/settings-schema (constantly {})]
      (is (nil? (pack-dependencies :r
                                   {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1"}}}
                                   :reviews
                                   {})))))

  (testing "Selected defaults to false when not set"
    (with-redefs [settings-schemas/settings-schema (constantly {})]
      (is (= [{:fi "K1" :sv "S1" :en "R1" :selected false}]
             (pack-dependencies :r
                                {:reviews {:rid1 {:fi "K1" :sv "S1" :en "R1"}}}
                                :reviews
                                {:reviews {:rid1 {:included true}}}))))))

;; --------- make-query edge cases ---------

(deftest make-query-edge-cases-test
  (testing "Only deleted? false without published?"
    (is (= {:organization   "org"
            :deleted._value {$ne true}}
           (make-query "org" {:deleted? false}))))

  (testing "All options nil is same as empty"
    (is (= {:organization "org"}
           (make-query "org" {:published? nil :deleted? nil :id nil :category nil})))))

;; --------- template-date-set? ---------

(deftest template-date-set?-test
  (testing "Returns true when date keyword is in the date set"
    (is (true? (template-date-set?
                 {:published {:data {:verdict-dates [:julkipano :anto]}}}
                 :verdict-dates
                 :julkipano))))

  (testing "Returns false when date keyword is not in the set"
    (is (false? (template-date-set?
                  {:published {:data {:verdict-dates [:anto]}}}
                  :verdict-dates
                  :julkipano))))

  (testing "Returns false when date set key is missing"
    (is (false? (template-date-set?
                  {:published {:data {}}}
                  :verdict-dates
                  :julkipano))))

  (testing "Returns false when published is nil"
    (is (false? (template-date-set? {} :verdict-dates :julkipano))))

  (testing "Works with kuulutus-dates"
    (is (true? (template-date-set?
                 {:published {:data {:kuulutus-dates [:k-kuulutus :k-voimassa]}}}
                 :kuulutus-dates
                 :k-kuulutus)))))

;; --------- template-warning ---------

(deftest template-warning-test
  (let [template-with-dates (fn [& {:keys [verdict-dates kuulutus-dates category]
                                    :or   {category "r"}}]
                              {:category  category
                               :published {:data (cond-> {}
                                                   verdict-dates  (assoc :verdict-dates verdict-dates)
                                                   kuulutus-dates (assoc :kuulutus-dates kuulutus-dates))}})]
    (testing "Foreman template always returns nil"
      (is (nil? (verdict-template/template-warning
                  {:ryhti-data-2026 true :permitType "R"}
                  (template-with-dates :category "tj")))))

    (testing "Ryhti R application without public notice date"
      (is (= :ryhti-data-2026-public-notice-date-missing
             (verdict-template/template-warning
               {:ryhti-data-2026 true :permitType "R"}
               (template-with-dates)))))

    (testing "Ryhti R application with julkipano in verdict-dates"
      (with-redefs [app/bulletin-process (constantly :julkipano)]
        (is (nil? (verdict-template/template-warning
                    {:ryhti-data-2026 true :permitType "R"}
                    (template-with-dates :verdict-dates [:julkipano]))))))

    (testing "Ryhti R application with k-kuulutus in kuulutus-dates"
      (with-redefs [app/bulletin-process (constantly :julkipano)]
        (is (nil? (verdict-template/template-warning
                    {:ryhti-data-2026 true :permitType "R"}
                    (template-with-dates :kuulutus-dates [:k-kuulutus]))))))

    (testing "Ryhti P application without voimassa date"
      (is (= :ryhti-data-2026-public-notice-date-missing
             (verdict-template/template-warning
               {:ryhti-data-2026 true :permitType "P"}
               (template-with-dates)))))

    (testing "Ryhti P application with public notice but no voimassa"
      (is (= :ryhti-data-2026-in-force-until-date-missing
             (verdict-template/template-warning
               {:ryhti-data-2026 true :permitType "P"}
               (template-with-dates :verdict-dates [:julkipano])))))

    (testing "Ryhti P application with both public notice and voimassa"
      (with-redefs [app/bulletin-process (constantly :julkipano)]
        (is (nil? (verdict-template/template-warning
                    {:ryhti-data-2026 true :permitType "P"}
                    (template-with-dates :verdict-dates [:julkipano :voimassa]))))))

    (testing "Ryhti P application with k-voimassa in kuulutus-dates"
      (with-redefs [app/bulletin-process (constantly :julkipano)]
        (is (nil? (verdict-template/template-warning
                    {:ryhti-data-2026 true :permitType "P"}
                    (template-with-dates :verdict-dates [:julkipano]
                                         :kuulutus-dates [:k-voimassa]))))))

    (testing "Non-ryhti application with kuulutus process but no kuulutus date"
      (with-redefs [app/bulletin-process (constantly :kuulutus)]
        (is (= :kuulutus-date-missing
               (verdict-template/template-warning
                 {:permitType "R"}
                 (template-with-dates))))))

    (testing "Non-ryhti application with kuulutus process and kuulutus date set"
      (with-redefs [app/bulletin-process (constantly :kuulutus)]
        (is (nil? (verdict-template/template-warning
                    {:permitType "R"}
                    (template-with-dates :kuulutus-dates [:k-kuulutus]))))))

    (testing "Non-ryhti application with julkipano process returns nil"
      (with-redefs [app/bulletin-process (constantly :julkipano)]
        (is (nil? (verdict-template/template-warning
                    {:permitType "R"}
                    (template-with-dates))))))))

;; --------- settings-key ---------

(deftest settings-key-test
  (testing "Category only"
    (is (= :verdict-settings.r (settings-key :r))))

  (testing "Category with extra path"
    (is (= :verdict-settings.r.draft (settings-key :r :draft))))

  (testing "Category with multiple extra path segments"
    (is (= :verdict-settings.ya.draft.reviews (settings-key :ya [:draft :reviews])))))

;; --------- draft-for-publishing edge cases ---------

(deftest draft-for-publishing-edge-cases-test
  (with-redefs [template-schemas/verdict-template-schema (constantly test-template)]
    (testing "Completely empty draft"
      (is (= {} (draft-for-publishing {:category :r :draft {}}))))

    (testing "Excluded dict values are omitted"
      (is (= {:one true}
             (draft-for-publishing {:category :r :draft {:one true :six true}}))))

    (testing "Only nil and blank values results in empty map"
      (is (= {} (draft-for-publishing {:category :r :draft {:one nil :two nil :three ""}}))))

    (testing "Repeating with all empty sub-values still produces entries"
      ;; four-one is a toggle so false is a valid non-blank value
      (is (= {:four [{:four-one false}]}
             (draft-for-publishing {:category :r :draft {:four {:id1 {:four-one false
                                                                      :four-two ""}}}}))))))

;; --------- template-inclusions edge cases ---------

(deftest template-inclusions-edge-cases-test
  (with-redefs [template-schemas/verdict-template-schema (constantly test-template)]
    (testing "Empty removed-sections map means nothing removed"
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {}}})))))

    (testing "Removed section with false value is not removed"
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second false}}})))))

    (testing "Only second section removed leaves its exclusive dict out"
      ;; :two only belongs to :second section, so removing :second excludes :two
      ;; :one belongs to both :first and :second, so it stays
      (is (not (contains?
                 (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}}))
                 "two")))
      (is (contains?
            (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}}))
            "one")))))

(def test-policy-template
  {:dictionary {:one              {:toggle {}}
                :two              {:date {}}
                :three            {:text {}}
                :four             {:repeating {:four-one   {:toggle {}}
                                               :four-two   {:text {}}
                                               :four-three {:excluded? true
                                                            :date      {}}}}
                :five             {:text {}}
                :six              {:excluded? true
                                   :toggle    {}}
                :removed-sections {:keymap {:first  false
                                            :second false
                                            :third  false}}}
   :sections   [{:id   :first
                 :grid {:columns 4
                        :rows    [[{:dict :one}]]}}
                {:id     :second
                 :policy :copy
                 :grid   {:columns 4
                          :rows    [[{:dict :one} {:dict :two}]]}}
                {:id     :third
                 :policy :include
                 :grid   {:columns 4
                          :rows    [[{:dict :three}]
                                    [{:grid {:columns   2
                                             :repeating :four
                                             :rows      [[{:dict :four-one}
                                                          {:dict :four-two}]]}}]]}}]})

(deftest template-inclusions-policy-test
  (with-redefs [template-schemas/verdict-template-schema (constantly test-policy-template)]
    (testing "All sections included by default"
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {}})))))

    (testing ":policy :copy - Section with :copy policy can be removed"
      ;; :second has :policy :copy, so removing it should exclude :two (exclusive to :second)
      (is (= #{"one" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}})))))

    (testing ":policy :include - Section with :include policy cannot be removed"
      ;; :third has :policy :include, so removing it should NOT affect inclusions
      ;; Dictionary items from :include sections are always included
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:third true}}})))))

    (testing ":policy :include - Dictionary items from :include sections remain even when section removed"
      ;; Both :three and :four come from :third section which has :policy :include
      ;; They should remain included even when :third is removed
      (let [inclusions (set (template-inclusions {:category :r :draft {:removed-sections {:third true}}}))]
        (is (contains? inclusions "three"))
        (is (contains? inclusions "four"))))

    (testing "Removing section without policy (default) excludes its exclusive dicts"
      ;; :first has no policy (default behavior), removing it should work normally
      ;; but :one is shared with :second so it stays
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first true}}})))))

    (testing "Combining :copy and :include policy - removing :copy section"
      ;; Remove :second (:copy) - should exclude :two
      ;; Keep :third (:include) - :three and :four remain
      (is (= #{"one" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}})))))

    (testing "Combining :copy and :include policy - removing both special policy sections"
      ;; Remove :second (:copy) - excludes :two
      ;; Remove :third (:include) - but :three and :four stay due to :include policy
      (is (= #{"one" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second true
                                                                                :third  true}}})))))

    (testing "Removing all sections - :include policy preserves its dicts"
      ;; Remove all three sections
      ;; :one appears only in :first and :second (no :include), both removed -> excluded
      ;; :two appears only in :second (no :include), removed -> excluded
      ;; :three and :four from :third (:include policy) -> remain included
      ;; :five is not in any section -> always included
      (is (= #{"three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}})))))))

(deftest template-inclusions-with-policy-arguments-test
  (with-redefs [template-schemas/verdict-template-schema (constantly test-policy-template)]
    (testing "No policy argument (default :include) - only :include sections preserved when removed"
      ;; Default behavior: only sections with :policy :include are preserved
      (is (= #{"three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}})))))

    (testing "Explicit :include policy argument - same as default"
      ;; Explicitly passing :include should be same as default
      (is (= #{"three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :include)))))

    (testing ":copy policy argument - only sections with :copy policy preserved when removed"
      ;; Passing :copy means only sections with :policy :copy are protected
      ;; :second has :policy :copy, so :two is preserved
      ;; :third has :policy :include (not :copy), so :three and :four are excluded
      ;; :five is not in any section, so it's always included
      (is (= #{"one" "two" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :copy)))))

    (testing "Both :include and :copy policies - both types preserved"
      ;; When both :include and :copy are passed, sections with either policy are preserved
      ;; :second has :copy, :third has :include - both preserved
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :include :copy)))))

    (testing "Neither :include nor :copy - all sections can be removed"
      ;; When no matching policies are passed, all sections can be removed
      ;; Only :five remains (not in any section)
      (is (= #{"five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :some-other-policy)))))

    (testing "Policy argument :copy only - :include section can be removed"
      ;; Passing only :copy means :include sections are NOT protected
      ;; :third with :policy :include can be removed, :second with :copy cannot
      (is (= #{"one" "two" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :copy)))))

    (testing "Multiple policy arguments - order doesn't matter"
      ;; :copy :include should give same result as :include :copy
      (is (= (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :include :copy))
             (set (template-inclusions {:category :r :draft {:removed-sections {:first  true
                                                                                :second true
                                                                                :third  true}}}
                                       :copy :include)))))

    (testing "Policy argument affects which sections are protected from removal"
      ;; Remove :second only - with default :include, :two is excluded
      (is (= #{"one" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}}))))
      ;; Remove :second only - with :copy policy, :two is preserved
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:second true}}}
                                       :copy)))))

    (testing "Removing :include section with default policy - dicts preserved"
      ;; With default :include policy, :third cannot be truly removed
      (is (= #{"one" "two" "three" "four" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:third true}}})))))

    (testing "Removing :include section with :copy policy only - dicts excluded"
      ;; With only :copy policy, :third can be removed since it has :include not :copy
      (is (= #{"one" "two" "five"}
             (set (template-inclusions {:category :r :draft {:removed-sections {:third true}}}
                                       :copy)))))))
