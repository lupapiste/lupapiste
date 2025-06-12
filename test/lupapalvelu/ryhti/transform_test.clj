(ns lupapalvelu.ryhti.transform-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.ryhti.transform :as transform]
            [lupapalvelu.ryhti.transform-test-util :as tutil]
            [lupapalvelu.ryhti.util :as ryhti-util]
            [sade.util :as util]))

;; Private function references
(def ryhti-doc? transform/ryhti-doc?)
(def apartment->ryhti transform/apartment->ryhti)
(def ^:private -fill #'transform/-fill)
(def ^:private -handle-ryhti-ids #'transform/-handle-ryhti-ids)
(def ^:private branch? #'transform/branch?)
(def ^:private m-or-nil #'transform/m-or-nil)
(def ^:private from-or-nil #'transform/from-or-nil)
(def ^:private revalue #'transform/revalue)
(def ^:private data-copy #'transform/data-copy)
(def ^:private data-set #'transform/data-set)
(def ^:private data-set-ryhti-ids #'transform/data-set-ryhti-ids)
(def ^:private ctx-wrap #'transform/ctx-wrap)
(def ^:private document-ryhti-id-keys #'transform/document-ryhti-id-keys)
(def ^:private make-ryhti-doc #'transform/make-ryhti-doc)
(def ^:private validation-errors #'transform/validation-errors)
(def ^:private target-type #'transform/target-type)
(def ^:private array-ryhti-updates #'transform/array-ryhti-updates)
(def ^:private non-document-ryhti-updates! #'transform/non-document-ryhti-updates!)
(def ^:private add-missing-document-ryhti-ids #'transform/add-missing-document-ryhti-ids)
(def ^:private set-primary #'transform/set-primary)
(def ^:private process-document #'transform/process-document)

;;
;; Tests for revalue
;;

(deftest revalue-test
  (testing "returns nil if not a map"
    (is (nil? (revalue nil "new")))
    (is (nil? (revalue "not-a-map" "new")))
    (is (nil? (revalue [] "new"))))

  (testing "updates value if it is different"
    (let [m {:value "old" :modified 123}]
      (is (= {:value "new" :modified 123} (revalue m "new")))))

  (testing "returns same map if value is already the same"
    (let [m {:value "same" :modified 123}]
      (is (identical? m (revalue m "same")))))

  (testing "updates sourceValue if it matches old value"
    (let [m {:value "old" :sourceValue "old" :modified 123}]
      (is (= {:value "new" :sourceValue "new" :modified 123} (revalue m "new")))))

  (testing "does not update sourceValue if it differs from old value"
    (let [m {:value "old" :sourceValue "original" :modified 123}]
      (is (= {:value "new" :sourceValue "original" :modified 123} (revalue m "new")))))

  (testing "handles nil new-value"
    (let [m {:value "old" :sourceValue "old"}]
      (is (= {:value nil :sourceValue nil} (revalue m nil))))))

;;
;; Tests for -handle-ryhti-ids
;;

(deftest -handle-ryhti-ids-test
  (testing "generates Ryhti IDs and merges them into map"
    (let [m      {:foo "bar"}
          result (-handle-ryhti-ids false m :id1 :id2)]
      (is (= "bar" (:foo result)))
      (is (string? (get-in result [:ryhti :id1])))
      (is (string? (get-in result [:ryhti :id2])))))

  (testing "wraps IDs when wrap? is true"
    (let [m      {:foo "bar"}
          result (-handle-ryhti-ids true m :id1)]
      (is (= "bar" (:foo result)))
      ;; When wrapped, the structure is :ryhti {:id1 {:value "uuid" ...}}
      (is (some? (get-in result [:ryhti :id1 :value]))))))

;;
;; Tests for ryhti-schema
;;

(deftest ryhti-schema-test
  (testing "Document maps to rakennuskohteenLaajentaminen"
    (doseq [schema-name ["rakennuksen-laajentaminen" "rakennuksen-laajentaminen-ei-huoneistoja"]
            :let        [result (transform/ryhti-schema {:schema-info {:name schema-name}})]]
      (is (some? result))
      (is (= "rakennuskohteenLaajentaminen" (get-in result [:info :name])))))

  (testing "Document maps to rakennuskohteenPurkaminen"
    (let [result (transform/ryhti-schema {:schema-info {:name "purkaminen"}})]
      (is (some? result))
      (is (= "rakennuskohteenPurkaminen" (get-in result [:info :name])))))

  (testing "Document maps to rakennuskohteenPurkaminen-purkamisilmoitus"
    (let [result (transform/ryhti-schema {:schema-info {:name "purkamisilmoitus"}})]
      (is (some? result))
      (is (= "rakennuskohteenPurkaminen-purkamisilmoitus" (get-in result [:info :name])))))

  (testing "Document maps to uusiRakennuskohde"
    (doseq [schema-name ["uusiRakennus" "uusi-rakennus-ei-huoneistoa"
                         "kaupunkikuvatoimenpide" "kaupunkikuvatoimenpide-kokoontumistilalla"
                         "kaupunkikuvatoimenpide-ei-tunnusta"
                         "erityista-toimintaa-varten-rakennettava-alue"]
            :let        [result (transform/ryhti-schema {:schema-info {:name schema-name}})]]
      (is (some? result))
      (is (= "uusiRakennuskohde" (get-in result [:info :name])))))

  (testing "rakennuksen-muuttaminen maps to rakennuskohteenMuuttaminen"
    (let [result (transform/ryhti-schema {:schema-info {:name "rakennuksen-muuttaminen"}})]
      (is (some? result))
      (is (= "rakennuskohteenMuuttaminen" (get-in result [:info :name])))))

  (testing "No Ryhti schema returns nil"
    (is (nil? (transform/ryhti-schema {:schema-info {:name "unknown"}})))
    (is (nil? (transform/ryhti-schema {:schema-info {:name "hakija-r"}})))
    (is (nil? (transform/ryhti-schema {:schema-info {:name "hankkeen-kuvaus"}}))))

  (testing "Alleady Ryhti schema returns nil"
    (is (nil? (transform/ryhti-schema {:schema-info {:name "uusiRakennuskohde"}})))
    (is (nil? (transform/ryhti-schema {:schema-info {:name "rakennuskohteenLaajentaminen"}})))))

;;
;; Tests for ryhti-doc?
;;

(deftest ryhti-doc?-test
  (testing "rakennuskohteenLaajentaminen is a ryhti doc"
    (is (true? (ryhti-doc? {:schema-info {:name "rakennuskohteenLaajentaminen"}}))))

  (testing "rakennuskohteenPurkaminen is a ryhti doc"
    (is (true? (ryhti-doc? {:schema-info {:name "rakennuskohteenPurkaminen"}}))))

  (testing "uusiRakennuskohde is a ryhti doc"
    (is (true? (ryhti-doc? {:schema-info {:name "uusiRakennuskohde"}}))))

  (testing "uusiRakennus is not a ryhti doc"
    (is (false? (ryhti-doc? {:schema-info {:name "uusiRakennus"}}))))

  (testing "hakija-r is not a ryhti doc"
    (is (false? (ryhti-doc? {:schema-info {:name "hakija-r"}}))))

  (testing "nil schema-info name returns false"
    (is (false? (ryhti-doc? {:schema-info {}})))))

;;
;; Tests for transform-needed?
;;

(deftest transform-needed?-test
  (testing "infoRequest applications do not need transform"
    (is (false? (transform/transform-needed? {:infoRequest true :permitType "R"}))))

  (testing "facta-imported applications do not need transform"
    (is (false? (transform/transform-needed? {:facta-imported true :permitType "R"}))))

  (testing "non-R and non-P permit types do not need transform"
    (is (false? (transform/transform-needed? {:permitType "YA"})))
    (is (false? (transform/transform-needed? {:permitType "KT"})))
    (is (false? (transform/transform-needed? {:permitType "YI"}))))

  (testing "change permits do not need transform"
    (is (false? (transform/transform-needed? {:permitType    "R"
                                              :permitSubtype "muutoslupa"}))))

  (testing "continuation applications do not need transform"
    (is (false? (transform/transform-needed? {:permitType       "R"
                                              :primaryOperation {:name "raktyo-aloit-loppuunsaat"}}))))

  (testing "foreman applications do not need transform"
    (is (false? (transform/transform-needed? {:permitType       "R"
                                              :primaryOperation {:name "tyonjohtajan-nimeaminen"}}))))

  (testing "paper permits do not need transform"
    (is (false? (transform/transform-needed? {:permitType       "R"
                                              :primaryOperation {:name "aiemmalla-luvalla-hakeminen"}}))))

  (testing "applications with existing ryhti data and no legacy documents do not need transform"
    (is (false? (transform/transform-needed? {:permitType      "R"
                                              :ryhti-data-2026 true
                                              :documents       [{:schema-info {:name "hakija-r"}}]}))))

  (testing "R applications without ryhti data need transform"
    (is (true? (transform/transform-needed? {:permitType "R"
                                             :documents  [{:schema-info {:name "uusiRakennus"}}]})))
    (is (true? (transform/transform-needed? {:permitType      "R"
                                             :ryhti-data-2026 false
                                             :documents       [{:schema-info {:name "uusiRakennus"}}]}))))

  (testing "P applications without ryhti data need transform"
    (is (true? (transform/transform-needed? {:permitType "P"
                                             :documents  [{:schema-info {:name "purkaminen"}}]}))))

  (testing "applications with ryhti data but legacy building documents still need transform"
    (is (true? (transform/transform-needed? {:permitType      "R"
                                             :ryhti-data-2026 true
                                             :documents       [{:schema-info {:name "uusiRakennus"}}]}))))

  (testing "real legacy application needs transform"
    (is (true? (transform/transform-needed? tutil/legacy-application)))))

;;
;; Tests for branch?
;;

(deftest branch?-test
  (testing "leaf nodes (has value or modified) are not branches"
    (is (false? (branch? {:value "foo"})))
    (is (false? (branch? {:value "bar" :modified 12345})))
    (is (false? (branch? {:modified 12345 :value nil})))
    (is (false? (branch? {:value "foo" :extra "key"}))))

  (testing "maps without value or modfied keys are branches"
    (is (true? (branch? {:a {:value "foo"}})))
    (is (true? (branch? {:foo "bar"})))
    (is (true? (branch? {})))))

;;
;; Tests for m-or-nil
;;

(deftest m-or-nil-test
  (testing "returns map when value is present and fullish"
    (is (= {:value "foo" :modified 123}
           (m-or-nil {:a {:value "foo" :modified 123}} :a)))
    (is (= {:value false :modified 123}
           (m-or-nil {:a {:value false :modified 123}} :a))))

  (testing "returns map when it's a branch (even without value)"
    (is (= {:nested {:value "bar"}}
           (m-or-nil {:a {:nested {:value "bar"}}} :a)))
    (is (= {:value "bar"}
           (m-or-nil {:a {:nested {:value "bar"}}} :a.nested))))

  (testing "returns nil when value is empty string"
    (is (nil? (m-or-nil {:a {:value "" :modified 123}} :a))))

  (testing "returns nil when value is nil and not a branch"
    (is (nil? (m-or-nil {:a {:value nil :modified 123}} :a))))

  (testing "returns map when sourceValue is defined"
    (is (= {:value nil :modified 123 :sourceValue 1}
           (m-or-nil {:a {:value nil :modified 123
                          :sourceValue 1}} :a)))
    (is (= {:value nil :modified 123 :sourceValue nil}
           (m-or-nil {:a {:value nil :modified 123
                          :sourceValue nil}} :a))))

  (testing "returns nil when path does not exist"
    (is (nil? (m-or-nil {:a {:value "foo"}} :b)))))

;;
;; Tests for from-or-nil
;;

(deftest from-or-nil-test
  (let [ctx {:from-doc {:data {:field {:value "test"}
                               :blank {:value " "}
                               :node  {:foo {:value ""}}}}}]
    (testing "returns data when found"
      (is (= {:value "test"} (from-or-nil ctx :field))))

    (testing "returns nil when not found"
      (is (nil? (from-or-nil ctx :nonexistent))))

    (testing "returns nil when found but not fullish."
      (is (nil? (from-or-nil ctx :blank))))

    (testing "returns branch"
      (is (= {:foo {:value ""}} (from-or-nil ctx :node))))))

;;
;; Tests for data-set
;;

(deftest data-set-test
  (testing "sets value at path without prefix"
    (let [ctx {:to-doc {:data {}}}
          result (data-set ctx :field {:value "test"})]
      (is (= {:value "test"} (get-in result [:to-doc :data :field])))))

  (testing "sets value at path with prefix"
    (let [ctx {:to-doc {:data {}}}
          result (data-set ctx :prefix :field {:value "test"})]
      (is (= {:value "test"} (get-in result [:to-doc :data :prefix :field])))))

  (testing "does not modify ctx when value is nil"
    (let [ctx {:to-doc {:data {}}}]
      (is (= ctx (data-set ctx :field nil)))))

  (testing "handles nested paths"
    (let [ctx {:to-doc {:data {}}}
          result (data-set ctx :a.b.c {:value "deep"})]
      (is (= {:value "deep"} (get-in result [:to-doc :data :a :b :c])))
      (is (= result (data-set ctx :a :b.c {:value "deep"}))))))

;;
;; Tests for data-set-ryhti-ids
;;

(deftest data-set-ryhti-ids-test
  (testing "sets Ryhti ids at path"
    (let [ctx {:to-doc {:data {}}}
          result (data-set-ryhti-ids ctx :path :id1 :id2)]
      (is (some? (get-in result [:to-doc :data :path :ryhti :id1 :value])))
      (is (some? (get-in result [:to-doc :data :path :ryhti :id2 :value])))))

  (testing "does not overwrite existing Ryhti data"
    (let [ctx {:to-doc {:data {:path {:ryhti {:existing "data"}}}}}
          result (data-set-ryhti-ids ctx :path :id1)]
      (is (= {:existing "data"} (get-in result [:to-doc :data :path :ryhti]))))))

;;
;; Tests for data-copy
;;

(deftest data-copy-test
  (let [ctx {:from-doc {:data {:source {:value "test" :modified 123}}}
             :to-doc {:data {}}}]
    (testing "copies data using same path"
      (let [result (data-copy ctx :source)]
        (is (= {:value "test" :modified 123}
               (get-in result [:to-doc :data :source])))))

    (testing "copies data to different target path"
      (let [result (data-copy ctx :source :target)]
        (is (= {:value "test" :modified 123}
               (get-in result [:to-doc :data :target])))))

    (testing "copies data with prefix"
      (let [result (data-copy ctx :source :prefix :target)]
        (is (= {:value "test" :modified 123}
               (get-in result [:to-doc :data :prefix :target])))))

    (testing "does not modify ctx when source does not exist"
      (let [result (data-copy ctx :nonexistent)]
        (is (= {} (get-in result [:to-doc :data])))))))

;;
;; Tests for ctx-wrap
;;

(deftest ctx-wrap-test
  (testing "wraps value with modified timestamp"
    (let [ctx    {:from-doc {:created 12345}}
          result (ctx-wrap ctx "test")]
      (is (= {:value "test" :modified 12345} result))))

  (testing "wraps boolean values"
    (let [ctx {:from-doc {:created 99999}}]
      (is (= {:value true :modified 99999} (ctx-wrap ctx true)))
      (is (= {:value false :modified 99999} (ctx-wrap ctx false))))))

;;
;; Tests for apartment->ryhti
;;

(deftest apartment->ryhti-test
  (testing "transforms apartment with all fields"
    (let [apt {:huoneistoTyyppi {:value "asuinhuoneisto"}
               :keittionTyyppi {:value "keittio"}
               :porras {:value "A"}
               :huoneistonumero {:value "001"}
               :huoneistoala {:value "100"}
               :huoneluku {:value "3"}
               :pysyvaHuoneistotunnus {:value "12345"}
               :muutostapa {:value "lisäys"}
               :WCKytkin {:value true}
               :ammeTaiSuihkuKytkin {:value true}
               :saunaKytkin {:value true}
               :parvekeTaiTerassiKytkin {:value true}
               :lamminvesiKytkin {:value true}}
          result (apartment->ryhti apt)]
      ;; Basic fields should be present
      (is (= {:value "A"} (:porras result)))
      (is (= {:value "001"} (:huoneistonumero result)))
      (is (= {:value "100"} (:huoneistoala result)))
      (is (= {:value "3"} (:huoneluku result)))
      (is (= {:value "12345"} (:pysyvaHuoneistotunnus result)))
      (is (= {:value "lisäys"} (:muutostapa result)))
      ;; huoneistoTyyppi and keittionTyyppi should be included
      (is (= {:value "asuinhuoneisto"} (:huoneistoTyyppi result)))
      (is (= {:value "keittio"} (:keittionTyyppi result)))
      ;; Utilities should be transformed
      (is (= {:value "1"} (:wc result)))
      (is (= {:value "1"} (:suihku result)))
      (is (= {:value "1"} (:sauna result)))
      (is (= {:value "1"} (:parveke result)))
      (is (= {:value "1"} (:lammin-vesi result)))
      ;; ryhti id should be added
      (is (some? (get-in result [:ryhti :apartmentKey])))))

  (testing "excludes huoneistoTyyppi when not valid type"
    (let [apt {:huoneistoTyyppi {:value "invalid"}
               :porras {:value "A"}}
          result (apartment->ryhti apt)]
      (is (nil? (:huoneistoTyyppi result)))
      (is (= {:value "A"} (:porras result)))))

  (testing "excludes keittionTyyppi when not valid type"
    (let [apt {:keittionTyyppi {:value "invalid"}
               :porras {:value "A"}}
          result (apartment->ryhti apt)]
      (is (nil? (:keittionTyyppi result)))))

  (testing "blank utilities when false"
    (let [apt {:WCKytkin {:value false}
               :porras {:value "A"}}
          result (apartment->ryhti apt)]
      (is (= {:value ""} (:wc result)))))

  (testing "toimitila is valid huoneistoTyyppi"
    (let [apt {:huoneistoTyyppi {:value "toimitila"}
               :porras {:value "A"}}
          result (apartment->ryhti apt)]
      (is (= {:value "toimitila"} (:huoneistoTyyppi result)))))

  (testing "keittokomero is valid keittionTyyppi"
    (let [apt {:keittionTyyppi {:value "keittokomero"}
               :porras {:value "A"}}
          result (apartment->ryhti apt)]
      (is (= {:value "keittokomero"} (:keittionTyyppi result))))))

(deftest fill-ryhti-test
  (testing "Add missing ryhti ids"
    (let [ctx {:to-doc {:data {}}}
          result (-fill ctx :ryhti)]
      (doseq [rid [:buildingKey :structureKey :areaToBeBuiltForSpecificActivitiesKey]]
        (is (some? (get-in result [:to-doc :data :ryhti rid :value]))))))
  (testing "Do not touch existing ryhti field"
    (let [ctx {:to-doc {:data {:ryhti :hello}}}
          result (-fill ctx :ryhti)]
      (is (= :hello (get-in result [:to-doc :data :ryhti]))))))

;;
;; Tests for set-primary
;;

(deftest set-primary-test
  (testing "adds is-primary flag to map"
    (let [ctx    {:from-doc {:created 12345}}
          m      {:some "data"}
          result (set-primary ctx m)]
      (is (= {:some "data" :is-primary {:value true :modified 12345}} result))))

  (testing "overwrites existing is-primary"
    (let [ctx    {:from-doc {:created 54321}}
          m      {:is-primary {:value false :modified 11111}}
          result (set-primary ctx m)]
      (is (= {:is-primary {:value true :modified 54321}} result)))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.ulkokuorentiedot)
;;

(deftest fill-ulkokuorentiedot-test
  (testing "fills mitat from legacy document"
    (let [ctx {:from-doc {:data {:mitat {:tilavuus    {:value "100"}
                                         :kokonaisala {:value "200"}}}}
               :to-doc {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.ulkokuorentiedot)]
      (is (= {:value "100"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :ulkokuorentiedot :tilavuus])))
      (is (= {:value "200"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :ulkokuorentiedot :kokonaisala])))))

  (testing "fills kokonaisala from structure document"
    (let [ctx {:from-doc {:data {:kokonaisala {:value "123"}}}
               :to-doc {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.ulkokuorentiedot)]
      (is (= {:value "123"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :ulkokuorentiedot :kokonaisala]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa)
;;

(deftest fill-rakennuskohteen-osa-test
  (testing "fills composite rakennuskohteen-osa data"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:varusteet {:kokoontumistilanHenkilomaara {:value "50"}
                                                :vaestonsuoja                 {:value "10"}}
                                    :mitat     {:kellarinpinta-ala {:value "30"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa)]
      ;; Check that ryhti IDs are added
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa :ryhti :buildingSectionKey :value])))
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa :ryhti :structureSectionKey :value])))
      ;; Check that henkilomaara is copied
      (is (= {:value "50"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :kokoontumistilat :0 :henkilomaara])))
      ;; Check that kellariala is copied
      (is (= {:value "30"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :sisatilat :kellariala])))
      ;; Check that vaestonsuoja is filled
      (is (= {:value "10"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :vaestonsuoja :0 :kapasiteetti]))))))

;;
;; Tests for target-type
;;

(deftest target-type-test
  (testing "resolves target-type correctly"
    (is (= "rakennus" (target-type {:from-doc {:schema-info {:op {:name "pientalo"}}}})))
    (is (= "rakennelma" (target-type {:from-doc {:schema-info {:op {:name "aita"}}}})))
    (is (= "etvra" (target-type {:from-doc {:schema-info {:name "erityista-toimintaa-varten-rakennettava-alue"}}})))
    (is (nil? (target-type {:from-doc {:schema-info {:name "unknown"}}})))))

;;
;; Tests for -fill multimethod (kaytto)
;;

(deftest fill-kaytto-test
  (testing "fills kaytto for pientalo as rakennus"
    (let [ctx    {:from-doc {:created     12345
                             :schema-info {:name "uusiRakennus"
                                           :op   {:name "pientalo"}}
                             :data        {:kaytto {:rakennusluokka             {:value "0112"}
                                                    :rakentajaTyyppi            {:value "liiketaloudellinen"}
                                                    :valiaikainenRakennusKytkin {:value true}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :kaytto)]
      (is (= {:value "rakennus" :modified 12345}
             (get-in result [:to-doc :data :kaytto :kohteen-laji])))
      (is (= {:value true}
             (get-in result [:to-doc :data :kaytto :rakentaja-tyyppi])))
      (is (= {:value "0112"}
             (get-in result [:to-doc :data :kaytto :rakennusluokka])))
      (is (= {:value true}
             (get-in result [:to-doc :data :kaytto :valiaikainenRakennusKytkin])))))

  (testing "fills kaytto for pientalo-laaj as rakennus"
    (let [ctx    {:from-doc {:created     12345
                             :schema-info {:name "rakennuksen-laajentaminen"
                                           :op   {:name "pientalo-laaj"}}
                             :data        {:kaytto {:rakennusluokka  {:value "0200"}
                                                    :rakentajaTyyppi {:value "muu"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :kaytto)]
      (is (= {:value "rakennus" :modified 12345}
             (get-in result [:to-doc :data :kaytto :kohteen-laji])))
      (is (= {:value false} (get-in result [:to-doc :data :kaytto :rakentaja-tyyppi])))))

  (testing "fills kaytto for aita as rakennelma"
    (let [ctx    {:from-doc {:created     12345
                             :schema-info {:name "kaupunkikuvatoimenpide-ei-tunnusta"
                                           :op   {:name "aita"}}
                             :data        {:tilapainenRakennelmaKytkin {:value true}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :kaytto)]
      (is (= {:value "rakennelma" :modified 12345}
             (get-in result [:to-doc :data :kaytto :kohteen-laji])))
      (is (= {:value true}
             (get-in result [:to-doc :data :kaytto :valiaikainenRakennusKytkin])))
      (is (nil? (get-in result [:to-doc :data :kaytto :rakentaja-tyyppi])))))

  (testing "fills kaytto for erityista-toimintaa-varten-rakennettava-alue as etvra"
    (let [ctx    {:from-doc {:created     12345
                             :schema-info {:name "erityista-toimintaa-varten-rakennettava-alue"}
                             :data        {:tyyppi {:value "tapahtuma"}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :kaytto)]
      (is (= {:value "etvra" :modified 12345}
             (get-in result [:to-doc :data :kaytto :kohteen-laji])))
      (is (= {:value "tapahtuma"}
             (get-in result [:to-doc :data :kaytto :erityista-toimintaa-tyyppi]))))))


;;
;; Tests for -fill multimethod (mitat)
;;

(deftest fill-mitat-test
  (testing "fills mitat from legacy document"
    (let [ctx {:from-doc {:data {:pintaAla {:value "100"}
                                 :mitat {:kerrosluku {:value "2"}}}}
               :to-doc {:data {}}}
          result (-fill ctx :mitat)]
      (is (= {:value "100"} (get-in result [:to-doc :data :mitat :pintaAla])))
      (is (nil? (get-in result [:to-doc :data :mitat :kerrosluku]))))))

;;
;; Tests for -fill multimethod (sijaintitiedot)
;;

(deftest fill-sijaintitiedot-test
  (testing "fills sijaintitiedot from legacy document"
    (let [ctx    {:command  {:application {:propertyId "12345678901234"}}
                  :from-doc {:created 12345
                             :data    {:osoite {:lahiosoite           {:value "Testikatu 1"}
                                                :postinumero          {:value "00100"}
                                                :postitoimipaikannimi {:value "Helsinki"}
                                                :osoitenumero         {:value "42"}
                                                :osoitenumero2        {:value "44"}
                                                :jakokirjain          {:value "A"}
                                                :jakokirjain2         {:value "B"}
                                                :porras               {:value "C"}
                                                :huoneisto            {:value "12"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :sijaintitiedot)]
      (is (= {:value "Testikatu 1"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :katu])))
      (is (= {:value "00100"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :postinumero])))
      (is (= {:value "Helsinki"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :postitoimipaikannimi])))
      (is (= {:value "42"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :osoitenumero])))
      (is (= {:value "44"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :osoitenumero2])))
      (is (= {:value "A"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :jakokirjain])))
      (is (= {:value "B"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :jakokirjain2])))
      (is (= {:value "C"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :porras])))
      (is (= {:value "12"}
             (get-in result [:to-doc :data :sijaintitiedot :osoite :huoneisto])))
      (is (= {:value "12345678901234" :modified 12345}
             (get-in result [:to-doc :data :sijaintitiedot :sijainti
                             :hallinnollinen-sijainti-yksikko :kiinteistotunnus]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.huoneistot)
;;

(deftest fill-huoneistot-test
  (testing "transforms apartments from legacy document"
    (let [ctx {:command {:created 12345}
               :from-doc {:data {:huoneistot {:0 {:huoneistoTyyppi {:value "asuinhuoneisto"}
                                                  :keittionTyyppi {:value "keittio"}
                                                  :porras {:value "A"}
                                                  :huoneistonumero {:value "001"}
                                                  :WCKytkin {:value true}}
                                              :5 {:huoneistoTyyppi {:value "toimitila"}
                                                  :keittionTyyppi {:value "keittokomero"}
                                                  :porras {:value "A"}
                                                  :huoneistonumero {:value "005"}
                                                  :saunaKytkin {:value true}}}}}
               :to-doc {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.huoneistot)
          apt1 (get-in result [:to-doc :data :rakennuskohteen-osa :huoneistot :0])
          apt5 (get-in result [:to-doc :data :rakennuskohteen-osa :huoneistot :5])]
      (is (= {:value "A"} (:porras apt1)))
      (is (= {:value "001"} (:huoneistonumero apt1)))
      (is (= {:value "1"} (:wc apt1)))
      (is (some? (get-in apt1 [:ryhti :apartmentKey :value])))
      (is (= {:value "A"} (:porras apt5)))
      (is (= {:value "005"} (:huoneistonumero apt5)))
      (is (nil? (:wc apt5)))
      (is (some? (get-in apt5 [:ryhti :apartmentKey :value])))))

  (testing "returns empty when no apartments"
    (let [ctx {:from-doc {:data {}}
               :to-doc {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.huoneistot)]
      (is (nil? (get-in result [:to-doc :data :rakennuskohteen-osa :huoneistot]))))))

;;
;; Tests for -fill multimethod (rakennuksenOmistajat)
;;

(deftest fill-rakennuksenOmistajat-test
  (testing "transforms owners with ryhti ids"
    (let [ctx {:from-doc {:data {:rakennuksenOmistajat
                                 {:0 {:_selected {:value "henkilo"}
                                      :henkilo {:henkilotiedot {:etunimi {:value "Pena"}}}}}}}
               :to-doc {:data {}}}
          result (-fill ctx :rakennuksenOmistajat)
          owner (get-in result [:to-doc :data :rakennuksenOmistajat :0])]
      (is (= {:value "henkilo"} (:_selected owner)))
      (is (= {:henkilotiedot {:etunimi {:value "Pena"}}}
             (:henkilo owner)))
      (is (some? (get-in owner [:ryhti :buildingObjectOwnerKey :value])))))

  (testing "returns nil when no owners"
    (let [ctx {:from-doc {:data {}}
               :to-doc {:data {}}}
          result (-fill ctx :rakennuksenOmistajat)]
      (is (nil? (get-in result [:to-doc :data :rakennuksenOmistajat]))))))

;;
;; Integration tests using test utility data
;;

(deftest transform-with-legacy-application-test
  (testing "new building document is identified as needing transform"
    (is (some? (transform/ryhti-schema tutil/new-document))))

  (testing "expansion document is identified as needing transform"
    (is (some? (transform/ryhti-schema tutil/expansion-document))))

  (testing "demolition document is identified as needing transform"
    (is (some? (transform/ryhti-schema tutil/demolition-document))))

  (testing "non-building documents do not need transform"
    (let [hakija-doc (first (filter #(= "hakija-r" (get-in % [:schema-info :name]))
                                    (:documents tutil/legacy-application)))]
      (is (nil? (transform/ryhti-schema hakija-doc))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.energiatiedot)
;;

(deftest fill-energiatiedot-test
  (testing "fills energy data from legacy document"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:lammitys {:lammitystapa    {:value "vesikeskus"}
                                               :lammonlahde     {:value "kaukolampo"}
                                               :muu-lammonlahde {:value ""}}
                                    :luokitus {:energialuokka {:value "A"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.energiatiedot)]
      (is (= {:value "A"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :energiatiedot
                             :energialuokka])))
      ;; Check that heating method is transformed
      (is (= {:value "vesikeskus"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :energiatiedot
                             :lammitystapa :0 :lammitystapatyyppi])))
      (is (true? (get-in result [:to-doc :data :rakennuskohteen-osa :energiatiedot
                                 :lammitystapa :0 :is-primary :value])))
      ;; Check that ryhti id is added
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa :energiatiedot
                                 :lammitystapa :0 :ryhti :heatingMethodKey :value])))))

  (testing "fills heating source data"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:lammitys {:lammonlahde {:value "sahko"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.energiatiedot)]
      (is (= {:value "sahko"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :energiatiedot
                             :lammonlahde :0 :lammonlahdetyyppi])))
      (is (true? (get-in result [:to-doc :data :rakennuskohteen-osa :energiatiedot
                                 :lammonlahde :0 :is-primary :value]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.tekniset-tiedot)
;;

(deftest fill-tekniset-tiedot-test
  (testing "fills technical data with network connections"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:verkostoliittymat {:viemariKytkin   {:value true}
                                                        :vesijohtoKytkin {:value true}
                                                        :sahkoKytkin     {:value true}
                                                        :maakaasuKytkin  {:value false}
                                                        :kaapeliKytkin   {:value false}}
                                    :varusteet         {:sahkoKytkin                  {:value true}
                                                        :kaasuKytkin                  {:value false}
                                                        :viemariKytkin                {:value true}
                                                        :vesijohtoKytkin              {:value true}
                                                        :hissiKytkin                  {:value false}
                                                        :koneellinenilmastointiKytkin {:value true}
                                                        :lamminvesiKytkin             {:value true}
                                                        :aurinkopaneeliKytkin         {:value false}
                                                        :saunoja                      {:value "2"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.tekniset-tiedot)]
      ;; Check sewer handling
      (is (= "verkostoliittyma"
             (get-in result [:to-doc :data :rakennuskohteen-osa :tekniset-tiedot
                             :jatevesienkasittely :value])))
      ;; Check water handling
      (is (= "verkostoliittymat"
             (get-in result [:to-doc :data :rakennuskohteen-osa :tekniset-tiedot
                             :talousvesityyppi :value])))
      ;; Check network connections are created
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa :tekniset-tiedot
                                 :verkostoliittymat])))
      ;; Check ventilation when mechanical
      (is (= "koneellinen-tulo-ja-poistoilmanvaihto"
             (get-in result [:to-doc :data :rakennuskohteen-osa :tekniset-tiedot
                             :ilmanvaihtotavan-laji :0 :ilmanvaihtotyyppi :value])))
      ;; Check utilities are created
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa :tekniset-tiedot
                                 :varuste]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.materiaalitiedot)
;;

(deftest fill-materiaalitiedot-test
  (testing "fills material data from legacy document"
    (let [ctx    {:from-doc {:data {:rakenne  {:rakentamistapa      {:value       "elementti"
                                                                     :sourceValue "elementti"}
                                               :kantavaRakennusaine {:value       "betoni"
                                                                     :sourceValue "kivi"}
                                               :julkisivu           {:value    "tiili"
                                                                     :modified 56789}}
                                    :luokitus {:paloluokka {:value "P1"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.materiaalitiedot)]
      (is (= {:value "elementti" :sourceValue "elementti"}
             (get-in result [:to-doc :data :rakennuskohteen-osa
                             :materiaalitiedot :rakentamistapa])))
      (is (= {:value "P1"}
             (get-in result [:to-doc :data :rakennuskohteen-osa
                             :materiaalitiedot :paloluokka])))
      (is (= {:value "betoni" :sourceValue "kivi"}
             (get-in result [:to-doc :data :rakennuskohteen-osa
                             :materiaalitiedot :kantavaRakennusaine
                             :0 :rakennusaine])))
      (is (= {:value "tiili" :modified 56789}
             (get-in result [:to-doc :data :rakennuskohteen-osa
                             :materiaalitiedot :julkisivu
                             :0 :julkisivumateriaali])))))

  (testing "transforms paikalla to paikalla-tehty"
    (let [ctx    {:from-doc {:data {:rakenne {:rakentamistapa {:value       "paikalla"
                                                               :sourceValue "paikalla"
                                                               :modified    12345}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.materiaalitiedot)]
      (is (= {:value "paikalla-tehty" :sourceValue "paikalla-tehty" :modified 12345}
             (get-in result [:to-doc :data :rakennuskohteen-osa
                             :materiaalitiedot :rakentamistapa])))))

  (testing "excludes usupported paloluokka"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:luokitus {:paloluokka {:value "paloahidastava"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.materiaalitiedot)]
      (is (nil? (get-in result [:to-doc :data :rakennuskohteen-osa
                                :materiaalitiedot :paloluokka]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.kayttotiedot)
;;

(deftest fill-kayttotiedot-test
  (testing "fills usage data from legacy document"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:kaytto    {:kayttotarkoitus {:value "011 yhden asunnon talot"}}
                                    :varusteet {:kokoontumistilanHenkilomaara {:value "50"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.kayttotiedot)]
      (is (= "011 yhden asunnon talot"
             (get-in result [:to-doc :data :rakennuskohteen-osa :kayttotiedot
                             :kayttotarkoitus :0 :tyyppi :value])))
      (is (= {:value "50"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :kayttotiedot
                             :kayttajamaara]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.sisaankaynti)
;;

(deftest fill-sisaankaynti-test
  (testing "fills entrance data when accessible"
    (let [ctx    {:from-doc {:data {:kaytto {:esteetonKytkin {:value    true
                                                              :modified 12345}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.sisaankaynti)]
      (is (= {:value true :modified 12345}
             (get-in result [:to-doc :data :rakennuskohteen-osa
                             :sisaankaynti :0 :esteeton])))
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa
                                 :sisaankaynti :0 :ryhti :entranceKey
                                 :value])))))

  (testing "does not create entrance when not accessible"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:kaytto {:esteetonKytkin {:value false}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.sisaankaynti)]
      (is (nil? (get-in result [:to-doc :data :rakennuskohteen-osa :sisaankaynti]))))))

;;
;; Tests for -fill multimethod (rakennuskohteen-osa.vaestonsuoja)
;;

(deftest fill-vaestonsuoja-test
  (testing "fills civil defence shelter data when present"
    (let [ctx    {:command  {:created 12345}
                  :from-doc {:data {:varusteet {:vaestonsuoja {:value "100"}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.vaestonsuoja)]
      (is (= {:value "100"}
             (get-in result [:to-doc :data :rakennuskohteen-osa :vaestonsuoja
                             :0 :kapasiteetti])))
      (is (some? (get-in result [:to-doc :data :rakennuskohteen-osa
                                 :vaestonsuoja :0 :ryhti
                                 :civilDefenceShelterKey :value])))))

  (testing "does not create shelter when not present"
    (let [ctx    {:from-doc {:data {:varusteet {:vaestonsuoja {:value ""}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :rakennuskohteen-osa.vaestonsuoja)]
      (is (nil? (get-in result [:to-doc :data :rakennuskohteen-osa :vaestonsuoja]))))))

;;
;; Tests for -fill multimethod (pysyva-rakennustunnus)
;;

(deftest fill-pysyva-rakennustunnus-test
  (testing "copies valtakunnallinenNumero to pysyva-rakennustunnus"
    (let [ctx {:from-doc {:data {:valtakunnallinenNumero {:value "123456789A"}}}
               :to-doc {:data {}}}
          result (-fill ctx :pysyva-rakennustunnus)]
      (is (= {:value "123456789A"}
             (get-in result [:to-doc :data :pysyva-rakennustunnus]))))))

;;
;; Tests for -fill multimethod (poistumanAjankohta)
;;

(deftest fill-poistumanAjankohta-test
  (testing "copies poistumanAjankohta directly"
    (let [ctx {:from-doc {:data {:poistumanAjankohta {:value "01.01.2025" :modified 12345}}}
               :to-doc {:data {}}}
          result (-fill ctx :poistumanAjankohta)]
      (is (= {:value "01.01.2025" :modified 12345}
             (get-in result [:to-doc :data :poistumanAjankohta]))))))

;;
;; Tests for -fill multimethod (laajennuksen-tiedot)
;;

(deftest fill-laajennuksen-tiedot-test
  (testing "copies laajennuksen-tiedot directly"
    (let [ctx    {:from-doc {:data {:laajennuksen-tiedot {:mitat               {:tilavuus {:value "500"}}
                                                          :perusparannuskytkin {:value true}}}}
                  :to-doc   {:data {}}}
          result (-fill ctx :laajennuksen-tiedot)
          data   (get-in result [:to-doc :data :laajennuksen-tiedot])]
      (is (= {:value "500"} (get-in data [:mitat :tilavuus])))
      (is (= {:value true} (:perusparannuskytkin data))))))

;;
;; Tests for array-ryhti-updates
;;

(deftest array-ryhti-updates-test
  (testing "extracts ryhti IDs from array items"
    (let [xs [{:id "1" :ryhti {:key "val1"}}
              {:id "2"}
              {:id "3" :ryhti {:key "val3"}}]
          result (array-ryhti-updates :prefix xs)]
      ;; util/kw-path returns keyword paths like :prefix.0.ryhti
      (is (= {:prefix.0.ryhti {:key "val1"}
              :prefix.2.ryhti {:key "val3"}}
             result))))

  (testing "returns nil for empty or no ryhti IDs"
    (is (nil? (array-ryhti-updates :prefix [])))
    (is (nil? (array-ryhti-updates :prefix [{:id "1"} {:id "2"}])))))

;;
;; Tests for non-document-ryhti-updates!
;;

(deftest non-document-ryhti-updates!-test
  (let [app     {:id "app-1"
                 :primaryOperation {:name "kerrostalo-rivitalo"}
                 :secondaryOperations [{:name "aita"}]
                 :attachments [{:id "att-1"}]}
        command {:application app}]
    (with-redefs [ryhti-util/ryhti-id (fn [& ks] {:ryhti (zipmap ks (repeat "uuid"))})
                  lupapalvelu.ryhti.transform/commit-to-mongo! (fn [_ updates] updates)]
      (testing "updates application with ryhti IDs"
        (let [result (non-document-ryhti-updates! command)
              updated-app (:application result)]
          (is (some? (get-in updated-app [:ryhti :permitApplicationKey])))
          (is (some? (get-in updated-app [:ryhti :constructionProjectKey])))
          (is (true? (:ryhti-data-2026 updated-app)))
          (is (some? (get-in updated-app [:primaryOperation :ryhti :constructionActionKey])))
          (is (some? (get-in updated-app [:secondaryOperations 0 :ryhti :constructionActionKey])))
          (is (some? (get-in updated-app [:attachments 0 :ryhti :attachmentDocumentKey]))))))))

;;
;; Tests for add-missing-document-ryhti-ids
;;

(deftest add-missing-document-ryhti-ids-test
  (let [app {:id "app-1"
             :documents [{:id "doc-1" :schema-info {:name "hakija-r" :op {:id "op-1"}}}]}
        cmd {:application app}]
    (testing "adds missing ryhti IDs to document"
      (with-redefs [lupapalvelu.application-utils/operation-by-id (fn [_ _] {:ryhti {:op-key "op-val"}})
                    lupapalvelu.ryhti.transform/document-ryhti-id-keys (constantly [:doc-key])]
        (let [doc (first (:documents app))
              result (add-missing-document-ryhti-ids cmd doc)]
          (is (some? (get-in result [:data :ryhti :doc-key :value])))
          (is (= {:op-key "op-val"} (get-in result [:schema-info :op :ryhti]))))))

    (testing "does not overwrite existing ryhti IDs"
      (let [doc {:id "doc-1" :data {:ryhti {:existing "val"}}}]
        (is (nil? (add-missing-document-ryhti-ids cmd doc)))))))

;;
;; Tests for command->ryhti!
;;

(deftest command->ryhti!-test
  (let [app     {:id "app-1" :permitType "R" :documents [{:id "doc-1" :schema-info {:name "uusiRakennus"}}]}
        org     {:ryhti-data-2026-start-ts 10000}
        cmd     {:application app :organization (delay org) :created 20000}]
    (with-redefs [lupapalvelu.ryhti.transform/transform-needed? (constantly true)
                  lupapalvelu.ryhti.transform/non-document-ryhti-updates! (fn [c] (assoc-in c [:application :ryhti-updated] true))
                  lupapalvelu.ryhti.transform/process-document (fn [_ d] (assoc d :processed true))]
      (testing "performs full ryhti transform when needed"
        (let [result (transform/command->ryhti! cmd)]
          (is (true? (get-in result [:application :ryhti-updated])))
          (is (true? (get-in result [:application :documents 0 :processed]))))))

    (testing "skips transform if start-ts is in the future"
      (let [cmd-future (assoc cmd :created 5000)]
        (is (= cmd-future (transform/command->ryhti! cmd-future)))))

    (testing "skips transform if not needed"
      (with-redefs [lupapalvelu.ryhti.transform/transform-needed? (constantly false)]
        (is (= cmd (transform/command->ryhti! cmd)))))))

(defn data-val [m kwp]
  (get-in m (util/conjv (util/split-kw-path kwp) :value)))

(defn data-src [m kwp]
  (get-in m (util/conjv (util/split-kw-path kwp) :sourceValue)))

(deftest make-ryhti-doc-test
  (let [cmd {:application  (assoc tutil/legacy-application :ryhti-data-2026 true)
             :created      123456
             :organization (delay {:id "753-R"})}]
    (testing "uusiRakennuskohde."
      (let [{:keys [schema-info data]
             :as   ryhti-doc} (make-ryhti-doc cmd tutil/new-document)]
        (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))
        (is (some? (:ryhti data)))
        (is (= "uusiRakennuskohde" (:name schema-info)))
        (is (= "New" (-> schema-info :op :description)))
        (is (= (:id tutil/new-document) (:id ryhti-doc)))
        (is (= (:created tutil/new-document) (:created ryhti-doc)))
        (is (false? (empty? (:meta ryhti-doc))))
        (is (= (:meta tutil/new-document) (:meta ryhti-doc)))
        (testing "rakennuksenOmistajat"
          (is (= (-> tutil/new-document :data :rakennuksenOmistajat)
                 (->> data :rakennuksenOmistajat
                      (util/map-values #(dissoc % :ryhti))))))
        (testing "kaytto"
          (is (= "rakennus" (data-val data :kaytto.kohteen-laji)))
          (is (false? (data-val data :kaytto.rakentaja-tyyppi))
              "Muu -> false")
          (is (= "0112 rivitalot" (data-val data :kaytto.rakennusluokka))))

        (testing "rakennuskohteen-osa: materiaalitiedot"
          (is (= "elementti" (data-val data :rakennuskohteen-osa.materiaalitiedot.rakentamistapa)))
          (is (= "P1/P2/P3" (data-val data :rakennuskohteen-osa.materiaalitiedot.paloluokka)))
          (is (= "betoni" (data-val data :rakennuskohteen-osa.materiaalitiedot.kantavaRakennusaine.0.rakennusaine)))
          (is (true? (data-val data :rakennuskohteen-osa.materiaalitiedot.kantavaRakennusaine.0.is-primary)))
          (is (some? (data-val data :rakennuskohteen-osa.materiaalitiedot.kantavaRakennusaine.0.ryhti.buildingMaterialOfLoadBearingStructuresKey)))
          (is (= "kivi" (data-val data :rakennuskohteen-osa.materiaalitiedot.julkisivu.0.julkisivumateriaali)))
          (is (true? (data-val data :rakennuskohteen-osa.materiaalitiedot.julkisivu.0.is-primary)))
          (is (some? (data-val data :rakennuskohteen-osa.materiaalitiedot.julkisivu.0.ryhti.facadeMaterialKey))))

        (testing "rakennuskohteen-osa: huoneistot"
          (is (= "asuinhuoneisto" (data-val data :rakennuskohteen-osa.huoneistot.0.huoneistoTyyppi)))
          (is (= "1" (data-val data :rakennuskohteen-osa.huoneistot.0.parveke)))
          (is (= "1" (data-val data :rakennuskohteen-osa.huoneistot.0.sauna)))
          (is (= "keittio" (data-val data :rakennuskohteen-osa.huoneistot.0.keittionTyyppi)))
          (is (= "100" (data-val data :rakennuskohteen-osa.huoneistot.0.huoneistoala)))
          (is (= "3" (data-val data :rakennuskohteen-osa.huoneistot.0.huoneluku)))
          (is (some? (data-val data :rakennuskohteen-osa.huoneistot.0.ryhti.apartmentKey)))
          (is (= "001" (data-val data :rakennuskohteen-osa.huoneistot.0.huoneistonumero)))
          (is (= "1" (data-val data :rakennuskohteen-osa.huoneistot.0.lammin-vesi)))
          (is (= "1" (data-val data :rakennuskohteen-osa.huoneistot.0.suihku)))
          (is (= "1" (data-val data :rakennuskohteen-osa.huoneistot.0.wc)))
          (is (= "A" (data-val data :rakennuskohteen-osa.huoneistot.0.porras)))
          (is (= "lisäys" (data-val data :rakennuskohteen-osa.huoneistot.0.muutostapa)))

          (is (= "toimitila" (data-val data :rakennuskohteen-osa.huoneistot.1.huoneistoTyyppi)))
          (is (= "keittokomero" (data-val data :rakennuskohteen-osa.huoneistot.1.keittionTyyppi)))
          (is (= "120" (data-val data :rakennuskohteen-osa.huoneistot.1.huoneistoala)))
          (is (= "4" (data-val data :rakennuskohteen-osa.huoneistot.1.huoneluku)))
          (is (some? (data-val data :rakennuskohteen-osa.huoneistot.1.ryhti.apartmentKey))))

        (testing "rakennuskohteen-osa: tekniset-tiedot"
          (is (= "verkostoliittyma" (data-val data :rakennuskohteen-osa.tekniset-tiedot.jatevesienkasittely)))
          (is (= "verkostoliittymat" (data-val data :rakennuskohteen-osa.tekniset-tiedot.talousvesityyppi)))
          (is (true? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.0.liitetty)))
          (is (= "jatevesi" (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.0.verkostoliittymatyyppi)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.0.ryhti.networkConnectionKey)))
          (is (true? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.1.liitetty)))
          (is (= "talousvesi" (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.1.verkostoliittymatyyppi)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.1.ryhti.networkConnectionKey)))

          (is (true? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.2.liitetty)))
          (is (= "sahko" (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.2.verkostoliittymatyyppi)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.2.ryhti.networkConnectionKey)))

          (is (true? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.3.liitetty)))
          (is (= "tietoliikennekaapeli" (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.3.verkostoliittymatyyppi)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.verkostoliittymat.3.ryhti.networkConnectionKey)))

          (is (= "koneellinen-tulo-ja-poistoilmanvaihto"
                 (data-val data :rakennuskohteen-osa.tekniset-tiedot.ilmanvaihtotavan-laji.0.ilmanvaihtotyyppi)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.ilmanvaihtotavan-laji.0.ryhti.ventilationMethodKey)))

          (is (= "sauna" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.0.varustetyyppi)))
          (is (= "3" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.0.maara)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.0.ryhti.equipmentKey)))

          (is (= "vesijohto" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.1.varustetyyppi)))
          (is (= "1" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.1.maara)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.1.ryhti.equipmentKey)))

          (is (= "jatevesiviemari" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.2.varustetyyppi)))
          (is (= "1" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.2.maara)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.2.ryhti.equipmentKey)))

          (is (= "koneellinen-ilmastointi"
                 (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.3.varustetyyppi)))
          (is (= "1" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.3.maara)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.3.ryhti.equipmentKey)))

          (is (= "sahko" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.4.varustetyyppi)))
          (is (= "1" (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.4.maara)))
          (is (some? (data-val data :rakennuskohteen-osa.tekniset-tiedot.varuste.4.ryhti.equipmentKey))))

        (testing "rakennuskohteen-osa: sisaankaynti"
          (is (true? (data-val data :rakennuskohteen-osa.sisaankaynti.0.esteeton)))
          (is (true? (data-val data :rakennuskohteen-osa.sisaankaynti.0.is-primary)))
          (is (some? (data-val data :rakennuskohteen-osa.sisaankaynti.0.ryhti.entranceKey))))

        (testing "rakennuskohteen-osa: energiatiedot"
          (is (= "D" (data-val data :rakennuskohteen-osa.energiatiedot.energialuokka)))
          (is (= "ilmakeskus"
                 (data-val data :rakennuskohteen-osa.energiatiedot.lammitystapa.0.lammitystapatyyppi)))
          (is (true? (data-val data :rakennuskohteen-osa.energiatiedot.lammitystapa.0.is-primary)))
          (is (some? (data-val data :rakennuskohteen-osa.energiatiedot.lammitystapa.0.ryhti.heatingMethodKey)))
          (is (= "kauko tai aluelämpö"
                 (data-val data :rakennuskohteen-osa.energiatiedot.lammonlahde.0.lammonlahdetyyppi)))
          (is (true? (data-val data :rakennuskohteen-osa.energiatiedot.lammonlahde.0.is-primary)))
          (is (some? (data-val data :rakennuskohteen-osa.energiatiedot.lammonlahde.0.ryhti.heatingEnergySourceKey))))

        (testing "rakennuskohteen-osa: kayttotiedot"
          (is (= "20" (data-val data :rakennuskohteen-osa.kayttotiedot.kayttajamaara)))
          (is (= "013 muut erilliset talot"
                 (data-val data :rakennuskohteen-osa.kayttotiedot.kayttotarkoitus.0.tyyppi)))
          (is (true? (data-val data :rakennuskohteen-osa.kayttotiedot.kayttotarkoitus.0.is-primary)))
          (is (some? (data-val data :rakennuskohteen-osa.kayttotiedot.kayttotarkoitus.0.ryhti.purposeKey))))

        (testing "rakennuskohteen-osa: vaestonsuoja"
          (is (= "10" (data-val data :rakennuskohteen-osa.vaestonsuoja.0.kapasiteetti)))
          (is (some? (data-val data :rakennuskohteen-osa.vaestonsuoja.0.ryhti.civilDefenceShelterKey))))

        (testing "rakennuskohteen-osa: ulkokuorentiedot"
          (is (= (-> tutil/new-document :data :mitat)
                 (-> data :rakennuskohteen-osa :ulkokuorentiedot))))

        (testing "rakennuskohteen-osa: sisatilat"
          (is (= "500" (data-val data :rakennuskohteen-osa.sisatilat.kellariala))))

        (testing "mitat"
          (is (= "" (data-val data :mitat.kerrosluku))))

        (testing "rakennusluokka-vahahiilisyys"
          (is (= "rivitalo"
                 (data-val data :rakennusluokka-vahahiilisyys.rakennusluokka-vahahiilisyys))))))

    (testing "rakennuskohteenLaajentaminen"
      (let [{:keys [schema-info data]
             :as   ryhti-doc} (make-ryhti-doc cmd tutil/expansion-document)
            old-data          (:data tutil/expansion-document)]
        (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))
        (is (some? (:ryhti data)))
        (is (= "rakennuskohteenLaajentaminen" (:name schema-info)))
        (is (= "Expansion" (-> schema-info :op :description)))
        (is (= (:buildingId data) (:buildingId old-data)))
        (is (= "199887766E" (data-val data :buildingId)))
        (is (= (:laajennuksen-tiedot data) (:laajennuksen-tiedot old-data)))
        (is (= "2000" (data-val data :laajennuksen-tiedot.mitat.kokonaisala)))
        (is (true? (data-val data :kaytto.rakentaja-tyyppi))
            "Liiketaloudellinen -> true")

        (testing "rakennuskohteen-osa: huoneistot"
          (let [apts (m-or-nil data :rakennuskohteen-osa.huoneistot)]
            (is (= 3 (count apts)))

            (is (= "asuinhuoneisto"
                   (data-val apts :0.huoneistoTyyppi)
                   (data-src apts :0.huoneistoTyyppi)))
            (is (= "1"
                   (data-val apts :0.parveke)
                   (data-src apts :0.parveke)))
            (is (= "1"
                   (data-val apts :0.sauna)
                   (data-src apts :0.sauna)))
            (is (= "keittio"
                   (data-val apts :0.keittionTyyppi)
                   (data-src apts :0.keittionTyyppi)))
            (is (= "108"
                   (data-val apts :0.huoneistoala)
                   (data-src apts :0.huoneistoala)))
            (is (= "4"
                   (data-val apts :0.huoneluku)
                   (data-src apts :0.huoneluku)))
            (is (some? (data-val apts :0.ryhti.apartmentKey)))
            (is (= "001"
                   (data-val apts :0.huoneistonumero)
                   (data-src apts :0.huoneistonumero)))
            (is (= "1234567890"
                   (data-val apts :0.pysyvaHuoneistotunnus)
                   (data-src apts :0.pysyvaHuoneistotunnus)))
            (is (= "1"
                   (data-val apts :0.lammin-vesi)
                   (data-src apts :0.lammin-vesi)))
            (is (= "1"
                   (data-val apts :0.suihku)
                   (data-src apts :0.suihku)))
            (is (= "A"
                   (data-val apts :0.porras)
                   (data-src apts :0.porras)))
            (is (= "1"
                   (data-val apts :0.wc)
                   (data-src apts :0.wc)))
            (is (nil? (m-or-nil apts :0.muutostapa)))

            (is (= "200" (data-val apts :1.huoneistoala)))
            (is (= "106" (data-src apts :1.huoneistoala)))
            (is (= "muutos" (data-val apts :1.muutostapa)))

            (is (= "toimitila" (data-val apts :2.huoneistoTyyppi)))
            (is (nil? (data-src apts :2.huoneistoTyyppi)))
            (is (= "lisäys" (data-val apts :2.muutostapa)))
            (is (= "" (data-val apts :2.suihku)))
            (is (nil? (data-src apts :2.suihku)))))

        (testing "rakennuskohteen-osa: materiaalitiedot"
          (let [m (m-or-nil data :rakennuskohteen-osa.materiaalitiedot)]
            (is (= "paikalla-tehty"
                   (data-val m :rakentamistapa)
                   (data-src m :rakentamistapa)))

            (is (= "other" (data-val m :kantavaRakennusaine.0.rakennusaine)))
            (is (= "tiili" (data-src m :kantavaRakennusaine.0.rakennusaine)))
            (is (= "Olki" (data-val m :kantavaRakennusaine.0.muuRakennusaine)))
            (is (= "" (data-src m :kantavaRakennusaine.0.muuRakennusaine)))
            (is (some? (data-val m :kantavaRakennusaine.0.ryhti.buildingMaterialOfLoadBearingStructuresKey)))

            (is (= "other" (data-val m :julkisivu.0.julkisivumateriaali)))
            (is (= "tiili" (data-src m :julkisivu.0.julkisivumateriaali)))
            (is (= "Kvartsi" (data-val m :julkisivu.0.muuMateriaali)))
            (is (= "" (data-src m :julkisivu.0.muuMateriaali)))
            (is (some? (data-val m :julkisivu.0.ryhti.facadeMaterialKey)))))

        (testing "sijaintitiedot"
          (let [addr (m-or-nil data :sijaintitiedot.osoite)]
            (is (= "Kyllikintie" (data-val addr :katu) (data-src addr :katu)))
            (is (= "04200" (data-val addr :postinumero) (data-src addr :postinumero)))
            (is (= "KERAVA"
                   (data-val addr :postitoimipaikannimi)
                   (data-src addr :postitoimipaikannimi)))
            (is (some? (data-val addr :ryhti.addressKey))))

          (is (some? (data-val data :sijaintitiedot.sijainti.ryhti.buildingObjectLocationDataKey)))
          (is (= (:propertyId tutil/legacy-application)
                 (data-val data :sijaintitiedot.sijainti.hallinnollinen-sijainti-yksikko.kiinteistotunnus)))
          (is (some? (data-val data :sijaintitiedot.sijainti.hallinnollinen-sijainti-yksikko.ryhti.administrativeLocationUnitKey))))))

    (testing "rakennuskohteenPurkaminen"
      (let [{:keys [schema-info data]
             :as   ryhti-doc} (make-ryhti-doc cmd tutil/demolition-document)
            old-data          (:data tutil/demolition-document)]
        (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))
        (is (some? (:ryhti data)))
        (is (= "rakennuskohteenPurkaminen" (:name schema-info)))
        (is (= "Demolition" (-> schema-info :op :description)))
        (is (= (:buildingId data) (:buildingId old-data)))
        (is (= "other" (data-val data :buildingId)))
        (is (= "567" (data-val data :manuaalinen_rakennusnro)))
        (is (not= (:poistumanSyy data) (:poistumanSyy old-data)))
        (is (nil? (:poistumanSyy data)))
        (is (= (:poistumanAjankohta data) (:poistumanAjankohta old-data)))

        (testing "rakennuskohteen-osa: materiaalitiedot"
          (let [m (m-or-nil data :rakennuskohteen-osa.materiaalitiedot)]
            (is (nil? (data-val m :rakentamistapa)))
            (is (= "ei tiedossa" (data-val old-data :rakenne.rakentamistapa)))
            (is (= "ei tiedossa" (data-val m :kantavaRakennusaine.0.rakennusaine)))))))

    (testing "uusiRakennus from erityista-toimintaa-varten-rakennettava-alue"
      (let [{:keys [schema-info data]
             :as   ryhti-doc} (make-ryhti-doc cmd tutil/event-document)
            old-data          (:data tutil/event-document)]
        (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))
        (is (some? (:ryhti data)))
        (is (= "uusiRakennuskohde" (:name schema-info)))
        (is (= "Main event" (-> schema-info :op :description)))
        (is (= "What is happening?"
               (data-val data :kuvaus)
               (data-val old-data :kuvaus)))
        (is (= "550" (data-val data :mitat.pintaAla)))
        (is (= "etvra" (data-val data :kaytto.kohteen-laji)))
        (is (= "tapahtuma-alue" (data-val data :kaytto.erityista-toimintaa-tyyppi)))))

    (testing "uusiRakennus for structure"
      (let [{:keys [schema-info data]
             :as   ryhti-doc} (make-ryhti-doc cmd tutil/wall-document)
            old-data          (:data tutil/wall-document)]
        (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))
        (is (some? (:ryhti data)))
        (is (= "uusiRakennuskohde" (:name schema-info)))
        (is (= "Fencing" (-> schema-info :op :description)))
        (is (= "Sand castle."
               (data-val data :kuvaus)
               (data-val old-data :kuvaus)))
        (is (= "123" (data-val data :rakennuskohteen-osa.ulkokuorentiedot.kokonaisala)))
        (is (= "rakennelma" (data-val data :kaytto.kohteen-laji)))
        (is (true? (data-val data :kaytto.valiaikainenRakennusKytkin)))
        (is (= "Rantamuuri" (data-val data :kaytto.rakennelman-kayttotarkoitus)))))

    (testing "rakennuskohteenMuuttaminen"
      (let [{:keys [schema-info data]
             :as   ryhti-doc} (make-ryhti-doc cmd tutil/change-document)
            old-data          (:data tutil/change-document)]
        (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))
        (is (some? (:ryhti data)))
        (is (= "rakennuskohteenMuuttaminen" (:name schema-info)))
        (is (= "Changes" (-> schema-info :op :description)))
        (is (= (:buildingId data) (:buildingId old-data)))
        (is (= "122334455R" (data-val data :buildingId)))
        (testing "Top level muutostyonlaji keys"
          (is (true? (data-val data :perusparannuskytkin)))
          (is (true? (data-val data :rakennustietojaEimuutetaKytkin)))
          (is (= "muut muutostyöt" (data-val data :muutostyolaji))))
        (is (= "6" (data-val data :rakennuskohteen-osa.ulkokuorentiedot.muutosala)))))))

;;
;; Tests for document-ryhti-id-keys
;;

(deftest document-ryhti-id-keys-test
  (testing "extracts Ryhti ID keys from document schema"
    (let [schema   {:body [{:name "ryhti"
                            :body [{:name "key1" :type :ryhti-identifier}
                                   {:name "key2" :type :ryhti-identifier}
                                   {:name "not-id" :type :string}]}]}
          document {:schema-info {:name "test-doc"}}]
      (with-redefs [model/get-document-schema (constantly schema)]
        (is (= [:key1 :key2] (document-ryhti-id-keys document))))))

  (testing "returns nil if no ryhti body found"
    (let [schema   {:body [{:name "other" :body []}]}
          document {:schema-info {:name "test-doc"}}]
      (with-redefs [model/get-document-schema (constantly schema)]
        (is (nil? (document-ryhti-id-keys document))))))

  (testing "returns nil if document is nil"
    (is (nil? (document-ryhti-id-keys nil))))

  (testing "real world schemas"
    (letfn [(drik [schema-name]
              (set (document-ryhti-id-keys {:schema-info {:name schema-name}})))]
      (is (= #{:engagingPartyKey} (drik "hakija-r")))
      (is (= #{:areaToBeBuiltForSpecificActivitiesKey :buildingKey :structureKey}
             (drik "uusiRakennuskohde")))
      (is (= #{:areaToBeBuiltForSpecificActivitiesKey :buildingKey :structureKey}
             (drik "rakennuskohteenLaajentaminen")))
      (is (= #{:areaToBeBuiltForSpecificActivitiesKey :buildingKey :structureKey}
             (drik "rakennuskohteenPurkaminen")))
      (is (= #{} (drik "uusiRakennus")))
      (is (= #{:plannerKey} (drik "suunnittelija"))))))

;;
;; Tests for building->ryhti
;;

(deftest building->ryhti-test
  (testing "transforms KuntaGML building data to Ryhti format"
    (let [building {:kaytto       {:kayttotarkoitus "011 yhden asunnon talot"
                                   :rakennusluokka  "0111 yhden asunnon talot"}
                    :mitat        {:tilavuus    "500"
                                   :kokonaisala "150"
                                   :kerrosluku  "2"}
                    :rakenne      {:rakentamistapa      "paikalla"
                                   :kantavaRakennusaine "puu"
                                   :julkisivu           "puu"}
                    :lammitys     {:lammitystapa "vesikeskus"
                                   :lammonlahde  "sahko"}
                    :verkostoliittymat {:viemariKytkin   true
                                        :vesijohtoKytkin true
                                        :sahkoKytkin     true}}
          ryhti-doc  {:schema-info {:name "uusiRakennuskohde"
                                    :op   {:name "pientalo"}}
                      :data        {}}
          result     (transform/building->ryhti tutil/legacy-application ryhti-doc building)]
      ;; Check that basic transformation occurred
      (is (map? result))
      ;; Check kaytto fields
      (is (= "rakennus" (get-in result [:kaytto :kohteen-laji])))
      ;; Check mitat fields
      (is (nil? (get-in result [:mitat :kerrosluku])))
      ;; Check materiaalitiedot
      (is (= "paikalla-tehty" (get-in result [:rakennuskohteen-osa :materiaalitiedot :rakentamistapa])))
      (is (= "puu" (get-in result [:rakennuskohteen-osa :materiaalitiedot :kantavaRakennusaine :0 :rakennusaine])))
      ;; Check tekniset-tiedot
      (is (= "verkostoliittyma" (get-in result [:rakennuskohteen-osa :tekniset-tiedot :jatevesienkasittely])))
      ;; Check energiatiedot
      (is (= "vesikeskus" (get-in result [:rakennuskohteen-osa :energiatiedot :lammitystapa :0 :lammitystapatyyppi])))))

  (testing "throws assertion error for invalid (non-Ryhti) document"
    ;; The function has a precondition that requires a valid Ryhti document
    (is (thrown? AssertionError
                 (transform/building->ryhti tutil/legacy-application nil {})))
    (is (thrown? AssertionError
                 (transform/building->ryhti tutil/legacy-application
                                            {:schema-info {:name "uusiRakennus"}} {})))))

;;
;; Tests for validation-errors
;;

(deftest validation-errors-test
  (testing "returns nil for valid document"
    (let [cmd       {:application  (assoc tutil/legacy-application :ryhti-data-2026 true)
                     :created      123456
                     :organization (delay {:id "753-R"})}
          ryhti-doc (make-ryhti-doc cmd tutil/new-document)]
      (is (nil? (validation-errors tutil/legacy-application ryhti-doc)))))

  (testing "returns errors for invalid document"
    ;; Create a document with invalid data (missing required fields)
    (let [invalid-doc {:schema-info {:name "uusiRakennuskohde"}
                       :data        {:kaytto {:kohteen-laji {:value "invalid-type"}}}}]
      ;; This will likely return errors since the data doesn't conform to schema
      (is (or (nil? (validation-errors tutil/legacy-application invalid-doc))
              (seq? (validation-errors tutil/legacy-application invalid-doc)))))))

;;
;; Tests for fill-ryhti-doc-data with specific edge cases
;;

(deftest fill-edge-cases-test
  (testing "handles nil values gracefully"
    (let [ctx {:command  {:created 12345}
               :from-doc {:data {:kaytto {:rakentajaTyyppi nil}}}
               :to-doc   {:data {}}}
          result (-fill ctx :kaytto)]
      ;; Should not throw, nil values should be handled
      (is (map? result))))

  (testing "handles empty strings as non-fullish"
    (let [ctx {:from-doc {:data {:osoite {:lahiosoite {:value ""}}}}
               :to-doc   {:data {}}}
          result (-fill ctx :sijaintitiedot)]
      ;; Empty strings should not be copied
      (is (nil? (get-in result [:to-doc :data :sijaintitiedot :osoite :katu])))))

  (testing "preserves sourceValue when different from value"
    (let [ctx {:from-doc {:data {:osoite {:lahiosoite {:value       "New Street"
                                                        :sourceValue "Old Street"
                                                        :modified    12345}}}}
               :to-doc   {:data {}}}
          result (-fill ctx :sijaintitiedot)
          katu   (get-in result [:to-doc :data :sijaintitiedot :osoite :katu])]
      (is (= "New Street" (:value katu)))
      (is (= "Old Street" (:sourceValue katu))))))
