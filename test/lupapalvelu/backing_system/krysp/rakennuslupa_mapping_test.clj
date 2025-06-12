(ns lupapalvelu.backing-system.krysp.rakennuslupa-mapping-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping]
            [lupapalvelu.document.rakennuslupa-canonical :as canonical]))

(def ^:private maalampokaivo-counts
  @#'lupapalvelu.backing-system.krysp.rakennuslupa-mapping/maalampokaivo-counts)

(def ^:private multiply-maalampokaivo-toimenpiteet
  @#'lupapalvelu.backing-system.krysp.rakennuslupa-mapping/multiply-maalampokaivo-toimenpiteet)

(def toimenpidetieto-path
  [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto])

(def avain-arvo-path
  [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :avainArvoTieto])

(def sample-maalampokaivo-doc
  {:schema-info {:name           "maalampokaivo"
                 :maalampokaivo? true
                 :op             {:id "ML-001"}}
   :data        {:kaivojen-lukumaara {:value "3"}}})

(def sample-maalampokaivo-v2-doc
  {:schema-info {:name "maalampokaivo-v2"
                 :maalampokaivo? true
                 :op   {:id "ML-002"}}
   :data        {:kaivojen-lukumaara {:value "2"}}})

(def other-doc
  {:schema-info {:name "paasuunnittelija"}
   :data        {}})

(defn- make-rakennelma-toimenpide
  [kayttotarkoitus id jarjestysnumero]
  {:kayttotarkoitus kayttotarkoitus
   :Toimenpide      {:rakennelmatieto {:Rakennelma {:yksilointitieto id
                                                    :tunnus {:jarjestysnumero jarjestysnumero}
                                                    :kayttotarkoitus kayttotarkoitus}}}})

(defn- make-canonical
  "Builds a minimal canonical structure with the given toimenpiteet and optional avainArvoTieto."
  [toimenpiteet & [avain-arvot]]
  (cond-> {:Rakennusvalvonta
            {:rakennusvalvontaAsiatieto
              {:RakennusvalvontaAsia
                {:toimenpidetieto toimenpiteet}}}}
    avain-arvot
    (assoc-in avain-arvo-path avain-arvot)))

(deftest maalampokaivo-counts-test
  (testing "returns count map from maalampokaivo document"
    (is (= {"ML-001" 3}
           (maalampokaivo-counts {:documents [other-doc sample-maalampokaivo-doc]}))))

  (testing "returns count map from maalampokaivo-v2 document"
    (is (= {"ML-002" 2}
           (maalampokaivo-counts {:documents [other-doc sample-maalampokaivo-v2-doc]}))))

  (testing "returns counts from both maalampokaivo and maalampokaivo-v2 documents"
    (is (= {"ML-001" 3 "ML-002" 2}
           (maalampokaivo-counts {:documents [sample-maalampokaivo-doc sample-maalampokaivo-v2-doc]}))))

  (testing "returns empty map when no maalampokaivo document"
    (is (= {} (maalampokaivo-counts {:documents [other-doc]}))))

  (testing "defaults to 1 when kaivojen-lukumaara is nil"
    (is (= {"ML-001" 1}
           (maalampokaivo-counts {:documents [{:schema-info {:name           "maalampokaivo" 
                                                             :maalampokaivo? true 
                                                             :op             {:id "ML-001"}}
                                               :data        {:kaivojen-lukumaara {:value nil}}}]}))))

  (testing "defaults to 1 when kaivojen-lukumaara is empty string"
    (is (= {"ML-001" 1}
           (maalampokaivo-counts {:documents [{:schema-info {:name "maalampokaivo" :maalampokaivo? true :op {:id "ML-001"}}
                                               :data        {:kaivojen-lukumaara {:value ""}}}]}))))

  (testing "returns count 1 for value \"1\""
    (is (= {"ML-001" 1}
           (maalampokaivo-counts {:documents [{:schema-info {:name "maalampokaivo" :maalampokaivo? true :op {:id "ML-001"}}
                                               :data        {:kaivojen-lukumaara {:value "1"}}}]}))))

  (testing "defaults blank count to 1 alongside explicit count"
    (is (= {"ML-001" 3 "ML-002" 1}
           (maalampokaivo-counts {:documents [sample-maalampokaivo-doc
                                              {:schema-info {:name "maalampokaivo-v2" :maalampokaivo? true :op {:id "ML-002"}}
                                               :data        {:kaivojen-lukumaara {:value ""}}}]})))))

(deftest multiply-maalampokaivo-toimenpiteet-test
  (let [maalampo-tp  (make-rakennelma-toimenpide canonical/maalampokaivo-kayttotarkoitus "ML-001" 1)
        other-tp     (make-rakennelma-toimenpide "Muu rakennelma" "MR-001" 2)
        canonical    (make-canonical [maalampo-tp other-tp])
        application  {:documents [sample-maalampokaivo-doc]}
        org-enabled  (delay {:kuntagml-maalampokaivo-count-enabled true})
        org-disabled (delay {:kuntagml-maalampokaivo-count-enabled false})]

    (testing "duplicates maalampokaivo toimenpide when n > 1 and org enabled"
      (let [result       (multiply-maalampokaivo-toimenpiteet canonical application org-enabled)
            toimenpiteet (get-in result toimenpidetieto-path)
            avain-arvot  (get-in result avain-arvo-path)]
        ;; 3 maalampo copies + 1 other = 4 total
        (is (= 4 (count toimenpiteet)))
        ;; Maalampo copies appear first (original position), with unique yksilointitieto suffixes
        (is (= "ML-001-1" (get-in (nth toimenpiteet 0) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-2" (get-in (nth toimenpiteet 1) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-3" (get-in (nth toimenpiteet 2) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        ;; Other toimenpide preserved in original position (after maalampo)
        (is (= "MR-001" (get-in (nth toimenpiteet 3) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        ;; avainArvoTieto added
        (is (= 1 (count avain-arvot)))
        (is (= "energiakaivojen lkm" (get-in (first avain-arvot) [:AvainArvoPari :avain])))
        (is (= "3" (get-in (first avain-arvot) [:AvainArvoPari :arvo])))))

    (testing "preserves ordering: other toimenpiteet stay in their original position"
      (let [canonical2   (make-canonical [other-tp maalampo-tp])
            result       (multiply-maalampokaivo-toimenpiteet canonical2 application org-enabled)
            toimenpiteet (get-in result toimenpidetieto-path)]
        ;; Other comes first (original position), then maalampo copies
        (is (= "MR-001" (get-in (nth toimenpiteet 0) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-1" (get-in (nth toimenpiteet 1) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-2" (get-in (nth toimenpiteet 2) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-3" (get-in (nth toimenpiteet 3) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))))

    (testing "with multiple maalampokaivo documents, each toimenpide expanded by its own count"
      (let [maalampo-tp2 (make-rakennelma-toimenpide canonical/maalampokaivo-kayttotarkoitus "ML-002" 2)
            canonical2   (make-canonical [maalampo-tp maalampo-tp2 other-tp])
            app-both     {:documents [sample-maalampokaivo-doc sample-maalampokaivo-v2-doc]}
            result       (multiply-maalampokaivo-toimenpiteet canonical2 app-both org-enabled)
            toimenpiteet (get-in result toimenpidetieto-path)
            avain-arvot  (get-in result avain-arvo-path)]
        ;; ML-001 expanded to 3 + ML-002 expanded to 2 + 1 other = 6 total
        (is (= 6 (count toimenpiteet)))
        ;; ML-001 copies
        (is (= "ML-001-1" (get-in (nth toimenpiteet 0) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-2" (get-in (nth toimenpiteet 1) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-3" (get-in (nth toimenpiteet 2) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        ;; ML-002 copies
        (is (= "ML-002-1" (get-in (nth toimenpiteet 3) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-002-2" (get-in (nth toimenpiteet 4) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        ;; Other preserved
        (is (= "MR-001" (get-in (nth toimenpiteet 5) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        ;; avainArvoTieto reflects the total sum
        (is (= "5" (get-in (first avain-arvot) [:AvainArvoPari :arvo])))))

    (testing "with two maalampo toimenpiteet but only one document, unmatched passes through as-is"
      (let [maalampo-tp2 (make-rakennelma-toimenpide canonical/maalampokaivo-kayttotarkoitus "ML-002" 2)
            canonical2   (make-canonical [maalampo-tp maalampo-tp2 other-tp])
            result       (multiply-maalampokaivo-toimenpiteet canonical2 application org-enabled)
            toimenpiteet (get-in result toimenpidetieto-path)]
        ;; ML-001 expanded to 3 + ML-002 unmatched (count defaults to 1, passes through) + 1 other = 5
        (is (= 5 (count toimenpiteet)))
        (is (= "ML-001-1" (get-in (nth toimenpiteet 0) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-2" (get-in (nth toimenpiteet 1) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "ML-001-3" (get-in (nth toimenpiteet 2) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        ;; ML-002 preserved unchanged (no matching document, defaults to 1)
        (is (= "ML-002" (get-in (nth toimenpiteet 3) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))
        (is (= "MR-001" (get-in (nth toimenpiteet 4) [:Toimenpide :rakennelmatieto :Rakennelma :yksilointitieto])))))

    (testing "n = 1 does not duplicate toimenpiteet but adds avainArvoTieto"
      (let [app-1    {:documents [{:schema-info {:name "maalampokaivo" :maalampokaivo? true :op {:id "ML-001"}}
                                   :data        {:kaivojen-lukumaara {:value "1"}}}]}
            result   (multiply-maalampokaivo-toimenpiteet canonical app-1 org-enabled)]
        (is (= (get-in canonical toimenpidetieto-path)
               (get-in result toimenpidetieto-path)))
        (is (= [{:AvainArvoPari {:avain "energiakaivojen lkm"
                                  :arvo  "1"}}]
               (get-in result avain-arvo-path)))))

    (testing "blank field defaults to count 1 and adds avainArvoTieto"
      (let [app-blank {:documents [{:schema-info {:name "maalampokaivo" :maalampokaivo? true :op {:id "ML-001"}}
                                    :data        {:kaivojen-lukumaara {:value ""}}}]}
            result    (multiply-maalampokaivo-toimenpiteet canonical app-blank org-enabled)]
        (is (= (get-in canonical toimenpidetieto-path)
               (get-in result toimenpidetieto-path)))
        (is (= [{:AvainArvoPari {:avain "energiakaivojen lkm"
                                  :arvo  "1"}}]
               (get-in result avain-arvo-path)))))

    (testing "no-op when org flag is disabled"
      (is (= canonical
             (multiply-maalampokaivo-toimenpiteet canonical application org-disabled))))

    (testing "no-op when no maalampokaivo document"
      (let [app-none {:documents [other-doc]}]
        (is (= canonical
               (multiply-maalampokaivo-toimenpiteet canonical app-none org-enabled)))))

    (testing "no-op when org flag is nil / missing"
      (is (= canonical
             (multiply-maalampokaivo-toimenpiteet canonical application (delay {})))))

    (testing "does not corrupt building toimenpiteet"
      (let [rakennus-tp  {:Toimenpide {:uusi {:kuvaus "Uusi rakennus"}
                                       :rakennustieto {:Rakennus {:yksilointitieto "R-001"}}}}
            canonical3   (make-canonical [maalampo-tp rakennus-tp])
            result       (multiply-maalampokaivo-toimenpiteet canonical3 application org-enabled)
            toimenpiteet (get-in result toimenpidetieto-path)]
        ;; 3 maalampo + 1 building = 4
        (is (= 4 (count toimenpiteet)))
        ;; Building toimenpide preserved in original position (after maalampo copies)
        (is (nil? (get-in (last toimenpiteet) [:Toimenpide :rakennelmatieto])))))

    (testing "appends to existing avainArvoTieto"
      (let [existing   [{:AvainArvoPari {:avain "existing" :arvo "value"}}]
            canonical2 (make-canonical [maalampo-tp] existing)
            result     (multiply-maalampokaivo-toimenpiteet canonical2 application org-enabled)
            avain-arvot (get-in result avain-arvo-path)]
        (is (= 2 (count avain-arvot)))
        (is (= "existing" (get-in (first avain-arvot) [:AvainArvoPari :avain])))
        (is (= "energiakaivojen lkm" (get-in (second avain-arvot) [:AvainArvoPari :avain])))))))
