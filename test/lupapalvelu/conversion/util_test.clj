(ns lupapalvelu.conversion.util-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.date :as date]
            [sade.env :as env]
            [sade.files :refer [with-temp-file]]))

(set! *warn-on-reflection* true)

(deftest ->double-test
  (testing "parses decimals with dot/comma and trims"
    (is (= 1.25 (util/->double "1.25")))
    (is (= 1.25 (util/->double "1,25")))
    (is (= 1.25 (util/->double "  1,25  "))))
  (testing "bad data returns nil"
    (is (nil? (util/->double nil)))
    (is (nil? (util/->double "")))
    (is (nil? (util/->double "  ")))
    (is (nil? (util/->double "foo")))))

(deftest valid-permit-id-or-nil-test
  (is (= "75-0549-16-DJ" (util/valid-permit-id-or-nil "75-0549-16-DJ")))
  (is (nil? (util/valid-permit-id-or-nil "nope")))
  (is (nil? (util/valid-permit-id-or-nil nil)))
  (is (nil? (util/valid-permit-id-or-nil "  "))))

(deftest find-xml-ids-test
  ;; Note: `find-xml-ids` uses `get-tunnus-xml-path` selectors, so the test XML must match
  ;; expected KRYSP structure (without namespaces after stripping).
  (testing "detects R permit type and returns both ids when present"
    (with-temp-file f
      (spit f (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                   "<RakennusvalvontaAsia>\n"
                   "  <luvanTunnisteTiedot>\n"
                   "    <LupaTunnus>\n"
                   "      <kuntalupatunnus>12-0089-D 17</kuntalupatunnus>\n"
                   "      <muuTunnustieto><MuuTunnus><tunnus>LP-123</tunnus></MuuTunnus></muuTunnustieto>\n"
                   "    </LupaTunnus>\n"
                   "  </luvanTunnisteTiedot>\n"
                   "</RakennusvalvontaAsia>\n"))
      (is (= {:backend-id "12-0089-D 17"
              :application-id "LP-123"}
             (util/find-xml-ids (.getPath f))))))

  (testing "detects P permit type and returns only available data"
    (with-temp-file f
      (spit f (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                   "<Poikkeamisasia>\n"
                   "  <luvanTunnistetiedot>\n"
                   "    <LupaTunnus>\n"
                   "      <kuntalupatunnus>164 10-3001-R</kuntalupatunnus>\n"
                   "    </LupaTunnus>\n"
                   "  </luvanTunnistetiedot>\n"
                   "</Poikkeamisasia>\n"))
      (is (= {:backend-id "164 10-3001-R"}
             (util/find-xml-ids (.getPath f))))))

  (testing "returns error for unsupported permit type"
    (with-temp-file f
      (spit f "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<FooBar></FooBar>")
      (is (contains? (util/find-xml-ids (.getPath f)) :error))))

  (testing "returns error when file cannot be read"
    (is (contains? (util/find-xml-ids "this-file-does-not-exist.xml") :error))))

(deftest a-large-number-of-unique-application-ids-can-be-generated
  (with-redefs [mongo/get-next-sequence-value (let [counter (atom 0)]
                                               (fn [_] (swap! counter inc)))
                mongo/any?                   (fn [_ _] false)
                env/feature?                 (fn [_] false)]
    (let [ids (take 10000 (repeatedly #(util/make-converted-application-id "092" 2013)))]
      (is (= 10000 (-> ids set count))))))

(deftest generated-application-id-vs-feature
  (testing "feature disabled"
    (with-redefs [mongo/get-next-sequence-value (fn [_] 12)
                  mongo/any?                   (fn [_ _] false)
                  env/feature?                 (fn [_] false)]
      (is (= "LP-123-1999-00012"
             (util/make-converted-application-id "123" 1999)))))
  (testing "feature enabled"
    (with-redefs [mongo/get-next-sequence-value (fn [_] 12)
                  mongo/any?                   (fn [_ _] false)
                  env/feature?                 (fn [_] true)]
      (is (= "LP-123-1999-90012"
             (util/make-converted-application-id "123" 1999))))))

(deftest kuntalupatunnus->description-test
  (is (= "Muutostyö" (util/kuntalupatunnus->description "12-0089-D 17")))
  (is (= "Uusi rakennus" (util/kuntalupatunnus->description "98-0088-10-A")))
  (is (nil? (util/kuntalupatunnus->description "11-5555-FOO")))
  (is (thrown? Exception (util/kuntalupatunnus->description "hello"))))

(defn make-house-op [op-key usage description]
  (assoc-in {op-key {:hello "world"}}
            [:rakennustieto :Rakennus :rakennuksenTiedot]
            {:rakennustunnus  {:rakennuksenSelite description}
             :kayttotarkoitus usage}))

(defn make-shed-op [op-key description id-description usecase]
  (assoc-in {op-key {:hello "world"}}
            [:rakennelmatieto :Rakennelma]
            {:tunnus          {:rakennuksenSelite id-description}
             :kuvaus          {:kuvaus description}
             :kayttotarkoitus usecase}))

(defn dop [suffix & args]
  (apply util/deduce-operation-type {:suffix suffix} args))

(deftest deduce-operation-type-test
  (testing "No operation information"
    (are [suffix desc expected] (= expected (dop suffix desc))
      :tjo "hello" "tyonjohtajan-nimeaminen-v2"
      :p "hello" "purkaminen"
      :pi "hello" "purkaminen"
      :hej "hello" "raktyo-aloit-loppuunsaat"
      :j "hello" "raktyo-aloit-loppuunsaat"
      :jl "hello" "raktyo-aloit-loppuunsaat"
      :mai "hello" "muu-maisema-toimenpide"
      :mai "Seinän KaaTAmiNEN" "puun-kaataminen"
      :mai " KAATOAPAIKKA " "puun-kaataminen"
      :mai "einesvalmistelenkkimakkara" "muu-tontti-tai-kort-muutos"
      :mai "Kaivurikoneen kuljettaja" "kaivuu"
      :mai "Raakaivamato" "kaivuu"
      :puu "Timber!" "puun-kaataminen"
      :mai "PYSÄKÖINTI KIELLETTY!" "tontin-jarjestelymuutos"
      :mai "Puhelinliittymä" "tontin-jarjestelymuutos"
      :foo "Whatever" "konversio"
      nil "Whoa!" "konversio"))

  (testing "With operation information"
    (testing "purkaminen"
      (is (= "purkaminen" (dop :tjo "Whatever" (make-house-op :purkaminen "foo" "bar"))))
      (is (= "purkaminen" (dop :p "Whatever" (make-house-op :uusi "foo" "bar"))))
      (is (= "purkaminen" (dop :pi "Whatever" (make-house-op :uusi "foo" "bar")))))

    (testing "uusi: pientalo"
      (is (= "pientalo" (dop :qq "Whatever" (make-house-op :uusi "011 yhden ASUNNON taLOT foo" "bar"))))
      (is (= "pientalo" (dop :qq "Whatever" (make-house-op :uusi "jonkun KAHDEN asunnon loukku" "bar"))))
      (is (= "pientalo" (dop :qq "Whatever" (make-house-op :uusi "yeah" "OMAkotiTALO"))))
      (is (= "pientalo" (dop :qq "Whatever" (make-house-op :uusi "yeah" "eripaRITALO")))))

    (testing "uusi: varasto-tms"
      (is (= "varasto-tms" (dop :qq "Whatever" (make-house-op :uusi "yeah" "TalOUsRaKennUS"))))
      (is (= "varasto-tms" (dop :qq "Whatever" (make-house-op :uusi "KUONTALOusrakennukKUJA" "DOH"))))
      (is (= "varasto-tms" (dop :qq "Whatever" (make-house-op :uusi "SaunaKytkin" "DOH")))))

    (testing "uusi: kerrostalo-rivitalo"
      (is (= "kerrostalo-rivitalo" (dop :qq "Whatever" (make-house-op :uusi "rIvItAlO rules!" "One"))))
      (is (= "kerrostalo-rivitalo" (dop :qq "Whatever" (make-house-op :uusi "KERROStalo" "Two"))))
      (is (= "kerrostalo-rivitalo" (dop :qq "Whatever" (make-house-op :uusi "What the hell is LUhtiTaLO?" "Three"))))
      (is (= "kerrostalo-rivitalo" (dop :qq "Whatever" (make-house-op :uusi "One" "KeRrOsTaLo"))))
      (is (= "kerrostalo-rivitalo" (dop :qq "Whatever" (make-house-op :uusi "Two" "asuINKeRrOsTaLo"))))
      (is (= "kerrostalo-rivitalo" (dop :qq "Whatever" (make-house-op :uusi "Three" "RIVItalo")))))

    (testing "uusi: vapaa-ajan-asuinrakennus"
      (is (= "vapaa-ajan-asuinrakennus" (dop :qq "Whatever" (make-house-op :uusi "Erivapaa-AJANTAJU" "Hello")))))

    (testing "uusi: auto-katos"
      (is (= "auto-katos" (dop :qq "KAtos" (make-shed-op :uusi "aUtoKaTOs" "Hello" nil))))
      (is (= "auto-katos" (dop :qq "KAtos" (make-shed-op :uusi "Hello" "AuToKaTo" nil))))
      (is (= "auto-katos" (dop :qq "" (make-shed-op :uusi "" "Vaja" "autoTalLi"))))
      (is (= "auto-katos" (dop :qq "" (make-shed-op :uusi "Autotallin" "rakentaminen" nil)))))

    (testing "structure: jatevesi"
      (is (= "jatevesi" (dop :qq "Hello" (make-shed-op :foo "Onpahan" "vaan" "Jätevesijärjestelmä"))))
      (is (= "jatevesi" (dop :qq "Hello" (make-shed-op :foo "Jätevettä pukkaa" "" nil))))
      (is (= "jatevesi" (dop :qq "Hello" (make-shed-op :foo "Hi" "Jäteveden ihmetteleyä" nil)))))

    (testing "structure: maalämpö"
      (is (= "maalampo" (dop :qq "MAALÄMPÖ" (make-shed-op :foo "" "" "maalämpökaiva"))))
      (is (= "maalampo" (dop :qq "Geysir" (make-shed-op :foo "maalämpöä" "pukkaa" "")))))

    (testing "structure: aita"
      (is (= "aita" (dop :qq "Hello" (make-shed-op :foo "" "" "AITA"))))
      (is (= "aita" (dop :qq "Hello" (make-shed-op :foo "Aidan" "pystytys" ""))))
      (is (= "aita" (dop :qq "Hello" (make-shed-op :foo "" "aItAportti" nil)))))

    (testing "structure: mainoslaite"
      (is (= "mainoslaite" (dop :qq "Hello" (make-shed-op :foo "" "" "jokuMAINOShomma"))))
      (is (= "mainoslaite" (dop :qq "Hello" (make-shed-op :foo "Missä mainos?" "" ""))))
      (is (= "mainoslaite" (dop :qq "Hello" (make-shed-op :foo "" "Hei mainostaja!" nil)))))

    (testing "structure: masto-tms"
      (is (= "masto-tms" (dop :qq "Hello" (make-shed-op :foo "" "" "umpilaituri"))))
      (is (= "masto-tms" (dop :qq "Hello" (make-shed-op :foo "Mastodontti" "" ""))))
      (is (= "masto-tms" (dop :qq "Hello" (make-shed-op :foo "" "Messukylän markiisi" nil)))))

    (testing "laajentaminen: talousrakennus-laaj"
      (is (= "talousrakennus-laaj" (dop :qq "Whatever" (make-house-op :laajennus "KotiTOIMisto" "Hello"))))
      (is (= "talousrakennus-laaj" (dop :qq "Whatever" (make-house-op :laajennus "KotiTalousRISOTTO" "Hello"))))
      (is (= "talousrakennus-laaj" (dop :qq "Whatever" (make-house-op :wha "KotiTalousRISOTTO" "LAAJennus"))))
      (is (= "talousrakennus-laaj" (dop :b "Whatever" (make-house-op :wha "KotiTalousRISOTTO" "Hello")))))

    (testing "laajentaminen: teollisuusrakennus-laaj"
      (is (= "teollisuusrakennus-laaj"
             (dop :qq "Whatever"
                  (make-house-op :laajennus "KotiTEOLLISUUDEN tuotantorakoROCK, rock!" "Hello")))))

    (testing "laajentaminen: pientalo-laaj"
      (is (= "pientalo-laaj" (dop :qq "Whatever" (make-house-op :laajennus "jonkun YHDEN asunnon talot jossain" "Hello"))))
      (is (= "pientalo-laaj" (dop :qq "Whatever" (make-house-op :laajennus "Hello" "OMAKOTITALO")))))

    (testing "laajentaminen: kerrostalo-rt-laaj"
      (is (= "kerrostalo-rt-laaj" (dop :qq "Whatever" (make-house-op :laajennus "Monirivitalikko" "Hello"))))
      (is (= "kerrostalo-rt-laaj" (dop :qq "Whatever" (make-house-op :laajennus "MoniKERROStalikko" "Hello"))))
      (is (= "kerrostalo-rt-laaj" (dop :qq "Whatever" (make-house-op :laajennus "Hello" "KERROStalo"))))
      (is (= "kerrostalo-rt-laaj" (dop :qq "Whatever" (make-house-op :laajennus "Hello" "riviTALO")))))

    (testing "laajentaminen: vapaa-ajan-rakennus-laaj"
      (is (= "vapaa-ajan-rakennus-laaj" (dop :qq "Whatever" (make-house-op :laajennus "Kovapaa-ajantaju" "Hello")))))

    (testing "muuttaminen: julkisivu-muutos"
      (is (= "julkisivu-muutos" (dop :qq "MULkoiLIja" (make-house-op :muuMuutosTyo "Hello" "World"))))
      (is (= "julkisivu-muutos" (dop :qq "MULkoiLIja" (make-house-op :uudelleenrakentaminen "Hello" "World"))))
      (is (= "julkisivu-muutos" (dop :d "PuolijulkiSIVULLINEN" (make-house-op :what "Hello" "World"))))
      (is (= "julkisivu-muutos" (dop :l "PuolijulkiSIVULLINEN" (make-house-op :what "Hello" "World")))))

    (testing "muuttaminen: sisatila-muutos"
      (is (= "sisatila-muutos" (dop :qq "Kohuoneeksieni" (make-house-op :muuMuutosTyo "Hello" "World"))))
      (is (= "sisatila-muutos" (dop :qq "Rääsyineen ja raasuineen" (make-house-op :uudelleenrakentaminen "Hello" "World")))))

    (testing "uusi: muu-uusi-rakentaminen"
      (is (= "muu-uusi-rakentaminen" (dop :qq "Whatever" (make-house-op :uusi "Hello" "World")))))

    (testing "laajentaminen: muu-rakennus-laaj"
      (is (= "muu-rakennus-laaj" (dop :qq "Whatever" (make-house-op :laajennus "Hello" "World")))))

    (testing "konversio"
      (is (= "konversio-rakennus" (dop :qq "Whatever" (make-house-op :muuMuutosTyo "Hello" "World"))))
      (is (= "konversio" (dop :qq "Whatever" nil))))))

(deftest get-elements-from-document-datas-test
  (let [doc-datas [[[:foo] 1]
                   [[:bar] {:x 2}]
                   [[:foo] 3]
                   [[:baz] nil]]]
    (is (= [1 3] (util/get-elements-from-document-datas :foo doc-datas)))
    (is (= [{:x 2}] (util/get-elements-from-document-datas :bar doc-datas)))
    (is (= [] (util/get-elements-from-document-datas :nope doc-datas)))))

(deftest remove-empty-rakennuspaikka-test
  (let [app {:documents [{:schema-info {:name "rakennuspaikka"}
                          :id "to-be-removed"}
                         {:schema-info {:name "rakennuspaikka-kuntagml"}
                          :id "keep"}
                         {:schema-info {:name "other"}
                          :id "keep-too"}]}
        result (util/remove-empty-rakennuspaikka app)]
    (is (= ["keep" "keep-too"] (map :id (:documents result))))))

(def midsummer (date/timestamp "22.6.2024"))

(deftest valid-timestamp-or-nil-test
  (testing "invalid"
    (doseq [arg [nil " " "foobar" "1.7.1777" "1.9.19999"
                 -30578693989000 330578693989000]]
      (is (nil? (util/valid-timestamp-or-nil arg)) (str "Nil: \"" arg "\""))))
  (testing "valid"
    (doseq [arg [" 22.6.2024 " midsummer (date/iso-date midsummer)
                 (date/iso-datetime midsummer)]]
      (is (= midsummer (util/valid-timestamp-or-nil arg)) (str "Good: \"" arg "\"")))))

(def base {:enabled   true
           :firstName "Lupapiste"
           :id        "conversion-user"
           :lastName  "Konversio"
           :role      "authority"
           :username  "konversio@lupapiste.fi"})

(deftest conversion-user-test
  (is (= base util/conversion-user-data))
  (is (= base (util/conversion-user)))
  (is (= base (util/conversion-user nil)))
  (is (= base (util/conversion-user "  ")))
  (is (= (assoc-in base [:orgAuthz :123-R] #{:authority})
         (util/conversion-user "123-R"))))

(deftest conversion-user?-test
  (is (false? (util/conversion-user? nil)))
  (is (false? (util/conversion-user? {})))
  (is (false? (util/conversion-user? usr/batchrun-user-data)))
  (is (false? (util/conversion-user? (usr/batchrun-user ["123-R"])) ))
  (is (true? (util/conversion-user? base)))
  (is (true? (util/conversion-user? (util/conversion-user "123-R"))))
  (is (true? (util/conversion-user? util/conversion-user-data))))

(deftest conversion-or-batchrun-user?-test
  (is (false? (util/conversion-or-batchrun-user? nil)))
  (is (false? (util/conversion-or-batchrun-user? {})))
  (is (true? (util/conversion-or-batchrun-user? usr/batchrun-user-data)))
  (is (true? (util/conversion-or-batchrun-user? (usr/batchrun-user ["123-R"])) ))
  (is (true? (util/conversion-or-batchrun-user? base)))
  (is (true? (util/conversion-or-batchrun-user? (util/conversion-user "123-R"))))
  (is (true? (util/conversion-or-batchrun-user? util/conversion-user-data))))

(deftest column-key-test
  (testing "basic text conversion to kebab-case keyword"
    (is (= :hello-world (util/column-key "hello world")))
    (is (= :hello-world (util/column-key "HelloWorld")))
    (is (= :hello-world (util/column-key "hello_world"))))

  (testing "removes parentheses from text"
    (is (= :something-totally-different (util/column-key "Something (totally) different")))
    (is (= :other-id-foo (util/column-key "Other id (foo)"))))

  (testing "removes slashes from text"
    (is (= :foobar (util/column-key "foo/bar"))))

  (testing "removes multiple slashes from text"
    (is (= :foobarbiz (util/column-key "foo/bar/biz"))))

  (testing "removes colons from text"
    (is (= :foobar (util/column-key "foo:bar"))))

  (testing "removes combined special characters"
    (is (= :foobarbazqux (util/column-key "foo(bar)/baz:qux"))))

  (testing "handles unicode characters"
    (is (= :älä-mölö-title (util/column-key "ÄläMölÖ title")))
    (is (= :å-ä-ö (util/column-key "Å ä Ö"))))

  (testing "handles whitespace"
    (is (= :hello-world (util/column-key "  hello world  "))))

  (testing "nil and blank values return a generated symbol as keyword"
    (let [nil-key   (util/column-key nil)
          blank-key (util/column-key "")]
      (is (keyword? nil-key))
      (is (keyword? blank-key))
      ;; Generated symbols contain "nil-col"
      (is (re-find #"nil-col" (name nil-key)))
      (is (re-find #"nil-col" (name blank-key)))))

  (testing "blank whitespace string returns a generated symbol as keyword"
    (let [ws-key (util/column-key "   ")]
      (is (keyword? ws-key))
      (is (re-find #"nil-col" (name ws-key))))))
