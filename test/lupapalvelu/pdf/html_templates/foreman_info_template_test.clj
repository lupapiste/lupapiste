(ns lupapalvelu.pdf.html-templates.foreman-info-template-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.pdf.html-templates.foreman-info-template :as foreman-template]))

(def test-application
  {:id            "LP-753-2024-00001"
   :address       "Testikatu 1"
   :state         "verdictGiven"
   :pate-verdicts [{:category  "r"
                    :published {:published 1700000000000}
                    :data      {:verdict-code "hyvaksytty"}}]
   :history       [{:state "verdictGiven"
                    :user  {:id        "authority-user-id"
                            :firstName "Viran"
                            :lastName  "Omainen"}}]})

(def tyonjohtaja-v2-doc
  {:id          "tyonjohtaja-v2-doc-id"
   :schema-info {:name             "tyonjohtaja-v2"
                 :version          1
                 :foreman-document true
                 :op               {:name "tyonjohtajan-nimeaminen-v2"}}
   :data        {:henkilotiedot          {:etunimi  {:value "Pena"}
                                          :sukunimi {:value "Penttilä"}
                                          :hetu     {:value "010101-0101"}}
                 :yhteystiedot           {:puhelin {:value "+358401234567"}
                                          :email   {:value "pena@example.com"}}
                 :osoite                 {:katu                 {:value "Testikatu 1"}
                                          :postinumero          {:value "33800"}
                                          :postitoimipaikannimi {:value "Tuonela"}
                                          :maa                  {:value "FIN"}}
                 :yritys                 {:yritysnimi           {:value "Solita Oy"}
                                          :liikeJaYhteisoTunnus {:value "1060155-5"}}
                 :kuntaRoolikoodi        {:value "vastaava työnjohtaja"}
                 :patevyysvaatimusluokka {:value "A"}
                 :tyonjohtajaHanketieto  {:taysiaikainenOsaaikainen {:value "osaaikainen"}
                                          :hankeKesto               {:value "12"}
                                          :kaytettavaAika           {:value "8"}
                                          :kayntienMaara            {:value "10"}}
                 :vastattavatTyotehtavat {:rakennuksenPurkaminen                  {:value true}
                                          :uudisrakennustyoMaanrakennustoineen    {:value true}
                                          :uudisrakennustyoIlmanMaanrakennustoita {:value false}
                                          :maanrakennustyot                       {:value false}
                                          :rakennuksenMuutosJaKorjaustyo          {:value false}
                                          :linjasaneeraus                         {:value false}
                                          :muuMika                                {:value true}
                                          :muuMikaValue                           {:value "Erikoistyö"}}
                 :sijaistus              {:sijaistettavaHloEtunimi  {:value "Jaska"}
                                          :sijaistettavaHloSukunimi {:value "Jokunen"}
                                          :alkamisPvm               {:value "13.02.2024"}
                                          :paattymisPvm             {:value "20.02.2024"}}
                 :patevyys-tyonjohtaja   {:koulutusvalinta            {:value "arkkitehtiylioppilas"}
                                          :koulutus                   {:value ""}
                                          :valmistumisvuosi           {:value "2010"}
                                          :kokemusvuodet              {:value "5"}
                                          :valvottavienKohteidenMaara {:value "9"}}
                 :tyonjohtajanHyvaksynta {:tyonjohtajanHyvaksynta {:value true}}}})

(def tyonjohtaja-v3-doc
  {:id          "tyonjohtaja-v3-doc-id"
   :tj-document true
   :schema-info {:name             "tyonjohtaja-v3"
                 :version          1
                 :foreman-document true
                 :op               {:name "tyonjohtajan-nimeaminen-v3"}}
   :data        {:henkilotiedot          {:etunimi  {:value "Ilkka"}
                                          :sukunimi {:value "Ilmastoija"}
                                          :hetu     {:value "010266-010B"}}
                 :yhteystiedot           {:puhelin {:value "+358501234567"}
                                          :email   {:value "ilkka@example.com"}}
                 :osoite                 {:katu                 {:value "Ilmakatu 2"}
                                          :postinumero          {:value "00100"}
                                          :postitoimipaikannimi {:value "Helsinki"}
                                          :maa                  {:value "FIN"}}
                 :yritys                 {:yritysnimi           {:value "HVAC Oy"}
                                          :liikeJaYhteisoTunnus {:value "1234567-8"}}
                 :kuntaRoolikoodi        {:value "IV-työnjohtaja"}
                 :patevyysvaatimusluokka {:value "B"}
                 :tyonjohtajaHanketieto  {:taysiaikainenOsaaikainen {:value "taysiaikainen"}
                                          :hankeKesto               {:value "24"}
                                          :kaytettavaAika           {:value "20"}
                                          :kayntienMaara            {:value "50"}}
                 :vastattavatTyotehtavat {:ivLaitoksenAsennustyo         {:value true}
                                          :ivLaitoksenKorjausJaMuutostyo {:value false}
                                          :muuMika                       {:value false}
                                          :muuMikaValue                  {:value ""}}
                 :sijaistus              {}
                 :patevyys-tyonjohtaja   {:koulutusvalinta            {:value "other"}
                                          :koulutus                   {:value "HVAC-insinööri"}
                                          :valmistumisvuosi           {:value "2015"}
                                          :kokemusvuodet              {:value "8"}
                                          :valvottavienKohteidenMaara {:value "15"}
                                          :patevyydentoteaja          {:value "safa"}
                                          :patevyysvoimassapvm        {:value "1.1.2050"}}
                 :tyonjohtajanHyvaksynta {:tyonjohtajanHyvaksynta {:value false}}}
   :changeLog   [{:field "state"
                  :value "rejected"
                  :user  {:id        "tarkastaja-user-id"
                          :firstName "Tarkka"
                          :lastName  "Tarkastaja"}}]})

(deftest single-foreman-html-test
  (with-redefs [foreman-util/get-project-application-for-foreman (constantly test-application)
                foreman-util/get-linked-foreman-applications     (constantly [])]
    (testing "Generate HTML for v2 foreman document"
      (let [{:keys [body header]} (foreman-template/single-foreman-html (app-utils/mask-person-id-partially tyonjohtaja-v2-doc) test-application :fi)
            html                  body]

        (testing "HTML structure exists"
          (is (string? html)))

        (testing "Contains application header"
          (is (re-find #"<b>LP-753-2024-00001, Testikatu 1</b>" header)))

        (testing "Contains foreman header"
          (is (re-find #"<h2[^>]*>Vastaava työnjohtaja Pena Penttilä</h2>" html)))

        (testing "Contains foreman personal information, with masked personal ID"
          (is (re-find #"<h3>Henkilötiedot</h3>" html))
          (is (re-find #"<div><b>Nimi</b><div>Pena Penttilä</div></div>" html))
          (is (re-find #"<div><b>Henkilötunnus</b><div>010101-\*\*\*\*</div></div>" html)))

        (testing "Contains company info"
          (is (re-find #"<h3>Yritys/yhteisö</h3>" html))
          (is (re-find #"<div><b>Nimi</b><div>Solita Oy</div></div>" html))
          (is (re-find #"<div><b>Y-tunnus</b><div>1060155-5</div></div>" html)))

        (testing "Contains contact information"
          (is (re-find #"<h3>Yhteystiedot</h3>" html))
          (is (re-find #"<b>Katuosoite</b><div>Testikatu 1</div>" html))
          (is (re-find #"<b>Postinumero</b><div>33800</div>" html))
          (is (re-find #"<b>Postitoimipaikka</b><div>Tuonela</div>" html))
          (is (re-find #"<b>Puhelin</b><div>\+358401234567</div>" html))
          (is (re-find #"<b>Sähköposti</b><div>pena@example.com</div>" html)))

        (testing "Contains construction project info"
          (is (re-find #"<h3>Toimii tässä rakennushankkeessa</h3>" html))
          (is (re-find #"<div><b>Työnjohtajarooli</b><div>Vastaava työnjohtaja</div></div>" html))
          (is (re-find #"<div><b>Tehtävien vaativuus</b><div>Vaativa</div></div>" html))
          (is (re-find #"<div><b>Täysiaikainen / osa-aikainen</b><div>Osa-aikaisena</div></div>" html))
          (is (re-find #"<div><b>Rakennushankkeen kesto</b><div>12</div></div>" html))
          (is (re-find #"<div><b>Tehtäviin käytettävä aika</b><div>8</div></div>" html))
          (is (re-find #"<div><b>Työmaakäyntien määrä yhteensä</b><div>10</div></div>" html)))

        (testing "Contains foreman tasks"
          (is (re-find #"<h3>Tässä hankkeessa vastattavat työtehtävät</h3>" html))
          (is (re-find #"<li class=\"task-template\".*<b>Rakennuksen purkaminen</b></li>" html))
          (is (re-find #"<li class=\"task-template\".*<b>Uudisrakennustyö maanrakennustöineen</b></li>" html))
          (is (re-find #"<li class=\"task-template\".*<b>Rajattu vastuualue: Erikoistyö</b></li>" html)))

        (testing "Contains substitution info"
          (is (re-find #"<h3>Sijaisuus</h3>" html))
          (is (re-find #"<div><b>Sijaistettavan nimi</b><div>Jaska Jokunen</div></div>" html))
          (is (re-find #"<div><b>Alkaa</b><div>13.02.2024</div></div>" html))
          (is (re-find #"<div><b>Päättyy</b><div>20.02.2024</div></div>" html)))

        (testing "Contains eligibility info"
          (is (re-find #"<h3>Työnjohtajan pätevyys</h3>" html))
          (is (re-find #"<div><b>Tutkinto</b><div>Arkkitehtiylioppilas</div></div>" html))
          (is (re-find #"<div><b>Muu tutkinto tai koulutus</b><div>\(Tyhjä\)</div></div>" html))
          (is (re-find #"<div><b>Valmistumisvuosi</b><div>2010</div></div>" html))
          (is (re-find #"<div><b>Kokemus vuosina</b><div>5</div></div>" html))
          (is (re-find #"<div><b>Valvottavat kohteet \(kpl\)</b><div>9</div></div>" html)))

        (testing "Contains approval info. State should be ok for foreman v2 with verdict"
          (is (re-find #"<h3>Tila</h3>" html))
          (is (re-find #"<div><b>Tila</b><div>Hakemus hyväksytty</div></div>" html))
          (is (re-find #"<div><b>Hyväksyjä</b><div>Viran Omainen</div></div>" html))
          (is (re-find #"<div><b>Hyväksytään vain tähän hankkeeseen</b><div>Kyllä</div></div>" html)))

        (testing "Footer contains page numbering"
          (let [footer (:footer (foreman-template/single-foreman-html (app-utils/mask-person-id-partially tyonjohtaja-v2-doc) test-application :fi))]
            (is (string? footer))
            (is (re-find #"class=\"pageNumber\"" footer))
            (is (re-find #"class=\"totalPages\"" footer))
            (is (re-find #"Sivu" footer))))))

    (testing "Generate HTML for v3 foreman document"
      (let [{:keys [body header]} (foreman-template/single-foreman-html (app-utils/mask-person-id-partially tyonjohtaja-v3-doc) test-application :fi)
            html                  body]

        (testing "HTML structure exists"
          (is (string? html)))

        (testing "Contains application header"
          (is (re-find #"<b>LP-753-2024-00001, Testikatu 1</b>" header)))

        (testing "Contains foreman header"
          (is (re-find #"IV-työnjohtaja Ilkka Ilmastoija" html)))

        (testing "Contains personal info, with masked personal ID"
          (is (re-find #"<h3>Henkilötiedot</h3>" html))
          (is (re-find #"<div><b>Nimi</b><div>Ilkka Ilmastoija</div></div>" html))
          (is (re-find #"<div><b>Henkilötunnus</b><div>010266-\*\*\*\*</div></div>" html)))

        (testing "Contains company info"
          (is (re-find #"<h3>Yritys/yhteisö</h3>" html))
          (is (re-find #"<div><b>Nimi</b><div>HVAC Oy</div></div>" html))
          (is (re-find #"<div><b>Y-tunnus</b><div>1234567-8</div></div>" html)))

        (testing "Contains contact information"
          (is (re-find #"<h3>Yhteystiedot</h3>" html))
          (is (re-find #"<b>Katuosoite</b><div>Ilmakatu 2</div>" html))
          (is (re-find #"<b>Postinumero</b><div>00100</div>" html))
          (is (re-find #"<b>Postitoimipaikka</b><div>Helsinki</div>" html))
          (is (re-find #"<b>Puhelin</b><div>\+358501234567</div>" html))
          (is (re-find #"<b>Sähköposti</b><div>ilkka@example.com</div>" html)))

        (testing "Contains construction project info"
          (is (re-find #"<h3>Toimii tässä rakennushankkeessa</h3>" html))
          (is (re-find #"<div><b>Työnjohtajarooli</b><div>IV-työnjohtaja</div></div>" html))
          (is (re-find #"<div><b>Tehtävien vaativuus</b><div>Tavanomainen</div></div>" html))
          (is (re-find #"<div><b>Täysiaikainen / osa-aikainen</b><div>Täysiaikaisena</div></div>" html))
          (is (re-find #"<div><b>Rakennushankkeen kesto</b><div>24</div></div>" html))
          (is (re-find #"<div><b>Tehtäviin käytettävä aika</b><div>20</div></div>" html))
          (is (re-find #"<div><b>Työmaakäyntien määrä yhteensä</b><div>50</div></div>" html)))

        (testing "Contains foreman tasks"
          (is (re-find #"<h3>Tässä hankkeessa vastattavat työtehtävät</h3>" html))
          (is (re-find #"<li class=\"task-template\".*<b>IV-laitoksen asennustyö</b></li>" html)))

        (testing "Shold not contain substitution info section"
          (is (not (re-find #"<h3>Sijaisuus</h3>" html))))

        (testing "Contains eligibility info"
          (is (re-find #"<h3>Työnjohtajan pätevyys</h3>" html))
          (is (re-find #"<div><b>Tutkinto</b><div>Muu</div></div>" html))
          (is (re-find #"<div><b>Muu tutkinto tai koulutus</b><div>HVAC-insinööri</div></div>" html))
          (is (re-find #"<div><b>Valmistumisvuosi</b><div>2015</div></div>" html))
          (is (re-find #"<div><b>Kokemus vuosina</b><div>8</div></div>" html))
          (is (re-find #"<div><b>Valvottavat kohteet \(kpl\)</b><div>15</div></div>" html)))

        (testing "Contains qualifications info (v3 specific fields)"
          (is (re-find #"<div><b>Pätevyyden toteaja</b><div>Suomen Arkkitehtiliitto - Finlands Arkitektförbund ry</div></div>" html))
          (is (re-find #"<div><b>Pätevyyden voimassaolo päättyy</b><div>1.1.2050</div></div>" html)))

        (testing "Contains approval info. State should be rejected for foreman v3"
          (is (re-find #"<h3>Tila</h3>" html))
          (is (re-find #"<div><b>Tila</b><div>Hylätty</div></div>" html))
          (is (re-find #"<div><b>Hyväksyjä</b><div>Tarkka Tarkastaja</div></div>" html))
          (is (re-find #"<div><b>Hyväksytään vain tähän hankkeeseen</b><div>Ei</div></div>" html)))))))

(deftest foreman-data-test
  (testing "Extract foreman data from foreman document"
    (let [{:keys [get-or-empty data empty-text loc]}
          (foreman-template/foreman-data test-application tyonjohtaja-v3-doc :fi)]
      (is (fn? loc))
      (is (fn? get-or-empty))
      (is (= "(Tyhjä)" empty-text))
      (is (map? data))
      (testing "get-or-empty"
        (testing "Will return empty-text for nonexistent values"
          (is (= empty-text (get-or-empty [:foo :bar]))))
        (testing "Will return value if present"
          (is (= "Ilkka" (get-or-empty [:henkilotiedot :etunimi]))))
        (testing "Will return localized value if given a :loc-prefix"
          (is (= "B" (get-or-empty [:patevyysvaatimusluokka])))
          (is (= "Tavanomainen" (get-or-empty [:patevyysvaatimusluokka]
                                              {:loc-prefix :osapuoli.patevyys.patevyysluokka}))))))))

(deftest extract-foreman-tasks-test
  (testing "Extract selected foreman tasks from data"
    (let [unwrapped-data (:data (tools/unwrapped tyonjohtaja-v3-doc))
          tasks          (foreman-template/extract-foreman-tasks unwrapped-data)]
      (is (seq tasks))
      (is (contains? (set tasks) :ivLaitoksenAsennustyo))
      (is (not (contains? (set tasks) :ivLaitoksenKorjausJaMuutostyo)))
      (is (not (contains? (set tasks) :rakennuksenPurkaminen))))))

(deftest empty-fields-test
  (testing "Handle empty optional fields gracefully"
    (let [minimal-doc           {:id          "minimal-doc-id"
                                 :tj-document true
                                 :schema-info {:name    "tyonjohtaja-v3"
                                               :version 1
                                               :op      {:name "tyonjohtajan-nimeaminen-v3"}}
                                 :data        {:henkilotiedot          {:etunimi  {:value "Test"}
                                                                        :sukunimi {:value "Person"}}
                                               :kuntaRoolikoodi        {:value "vastaava työnjohtaja"}
                                               :patevyysvaatimusluokka {:value "A"}
                                               :vastattavatTyotehtavat {}
                                               :sijaistus              {}
                                               :patevyys-tyonjohtaja   {}}
                                 :changeLog   []}
          {:keys [body header]} (foreman-template/single-foreman-html minimal-doc test-application :fi)
          html                  body]

      (testing "HTML is generated without errors"
        (is (string? html)))

      (testing "Contains required fields"
        (is (re-find #"Test" html))
        (is (re-find #"Person" html))
        ;; Role is localized
        (is (re-find #"Vastaava työnjohtaja" html))
        (is (re-find #"(Tyhjä)" html))))))

(deftest localization-test
  (testing "Generate HTML in Finnish"
    (let [{:keys [body]} (foreman-template/single-foreman-html (app-utils/mask-person-id-partially tyonjohtaja-v3-doc) test-application :fi)]
      (is (string? body))
      (is (seq body))
      (is (re-find #"IV-työnjohtaja" body))))

  (testing "Generate HTML in Swedish"
    (let [{:keys [body]} (foreman-template/single-foreman-html (app-utils/mask-person-id-partially tyonjohtaja-v3-doc) test-application :sv)]
      (is (string? body))
      (is (seq body))
      (is (re-find #"Ventilations-arbetsledare" body))))

  (testing "Generate HTML in English"
    (let [{:keys [body]} (foreman-template/single-foreman-html (app-utils/mask-person-id-partially tyonjohtaja-v3-doc) test-application :en)]
      (is (string? body))
      (is (seq body))
      (is (re-find #"IV supervisor" body)))))

(def test-application-with-foreman-docs
  (assoc test-application
         :documents [tyonjohtaja-v2-doc
                     tyonjohtaja-v3-doc
                     ;; Non-foreman doc should be ignored
                     {:id          "other-doc"
                      :schema-info {:name "hakija-r"}
                      :data        {}}]))

(deftest foreman-list-html-test
  (with-redefs [foreman-util/get-linked-foreman-applications (constantly [])]
    (testing "Generates HTML list document for all foreman documents in application"
      (let [foremen               [tyonjohtaja-v2-doc tyonjohtaja-v3-doc]
            {:keys [body header]} (foreman-template/foreman-list-html (map app-utils/mask-person-id-partially foremen) test-application-with-foreman-docs :fi)
            html                  body]

        (testing "HTML structure exists"
          (is (string? html))
          (is (seq html)))

        (testing "Contains foreman list header"
          (is (re-find #"Työnjohtajaluettelo" header)))

        (testing "Contains application identification"
          (is (re-find #"LP-753-2024-00001, Testikatu 1" header)))

        (testing "Contains v2 foreman info"
          (is (re-find #"Vastaava työnjohtaja Pena Penttilä" html))
          (is (re-find #"Solita Oy" html)))

        (testing "Contains v3 foreman info"
          (is (re-find #"IV-työnjohtaja Ilkka Ilmastoija" html))
          (is (re-find #"HVAC Oy" html)))

        (testing "Personal IDs are masked"
          (is (re-find #"010101-\*\*\*\*" html))
          (is (re-find #"010266-\*\*\*\*" html))
          (is (not (re-find #"010101-0101" html)))
          (is (not (re-find #"010266-010B" html))))))

    (testing "Generates empty body when application has no foreman documents"
      (let [{:keys [body header]} (foreman-template/foreman-list-html [] test-application :fi)
            html                  body]

        (testing "HTML is still generated"
          (is (string? html))
          (is (seq html)))

        (testing "Contains header but no foreman sections"
          (is (re-find #"Työnjohtajaluettelo" header))
          (is (not (re-find #"Henkilötiedot" html))))))

    (testing "Only provided foreman documents appear in output"
      (let [{:keys [body]} (foreman-template/foreman-list-html [(app-utils/mask-person-id-partially tyonjohtaja-v3-doc)] test-application :fi)]
        (is (re-find #"Ilkka Ilmastoija" body))
        (is (not (re-find #"Pena Penttilä" body)))))

    (testing "Footer contains page numbering"
      (let [{:keys [footer]} (foreman-template/foreman-list-html [tyonjohtaja-v2-doc] test-application :fi)]
        (is (string? footer))
        (is (re-find #"class=\"pageNumber\"" footer))
        (is (re-find #"class=\"totalPages\"" footer))
        (is (re-find #"Sivu" footer))))

    (testing "V2 foreman shows linked application id in subheader"
      (let [linked-v2-app {:id        "LP-753-2026-90064"
                           :state     "acknowledged"
                           :history   [{:state "acknowledged"
                                        :user  {:id        "authority-user-id"
                                                :firstName "Viran"
                                                :lastName  "Omainen"}}]
                           :documents [tyonjohtaja-v2-doc]}]
        (with-redefs [foreman-util/get-linked-foreman-applications (constantly [linked-v2-app])]
          (let [{:keys [body]} (foreman-template/foreman-list-html [tyonjohtaja-v2-doc] test-application :fi)]
            (is (re-find #"Hakemus: LP-753-2026-90064" body))))))))

(deftest responsibility-period-test
  (with-redefs [foreman-util/get-project-application-for-foreman (constantly test-application)
                foreman-util/get-linked-foreman-applications     (constantly [])]
    (testing "V3 foreman with submitted date shows responsibility period"
      (let [v3-doc-with-dates (assoc tyonjohtaja-v3-doc
                                     :state "declared"
                                     :submitted 1700000000000)
            {:keys [body]}    (foreman-template/single-foreman-html
                                (app-utils/mask-person-id-partially v3-doc-with-dates)
                                test-application :fi)]
        (is (re-find #"Vastuuaika" body))
        (is (re-find #"15.11.2023" body))))

    (testing "V3 foreman with started and ended dates shows full period"
      (let [v3-doc-with-termination (assoc tyonjohtaja-v3-doc
                                           :state "declared"
                                           :submitted 1700000000000
                                           :foremanTermination {:state   "confirmed"
                                                                :started 1700000000000
                                                                :ended   1702592000000
                                                                :confirmer {:firstName "Admin"
                                                                            :lastName  "User"}
                                                                :reason  "Projekti valmis"})
            {:keys [body]}          (foreman-template/single-foreman-html
                                      (app-utils/mask-person-id-partially v3-doc-with-termination)
                                      test-application :fi)]
        (is (re-find #"Vastuuaika" body))
        (is (re-find #"15.11.2023 - 15.12.2023" body))))

    (testing "V3 foreman without dates shows empty text"
      (let [{:keys [body]} (foreman-template/single-foreman-html
                             (app-utils/mask-person-id-partially tyonjohtaja-v3-doc)
                             test-application :fi)]
        (is (re-find #"Vastuuaika" body))
        (is (re-find #"\(Tyhjä\)" body))))

    (testing "V2 foreman with only start date shows start of responsibility period"
      (with-redefs [foreman-util/get-project-application-for-foreman (constantly test-application)
                    foreman-util/foreman-application-info             (constantly {:started 1700000000000
                                                                                   :ended   nil
                                                                                   :state   "acknowledged"
                                                                                   :history [{:state "acknowledged"
                                                                                              :user  {:id        "authority-user-id"
                                                                                                      :firstName "Viran"
                                                                                                      :lastName  "Omainen"}}]
                                                                                   :documents [tyonjohtaja-v2-doc]})]
        (let [{:keys [body]} (foreman-template/single-foreman-html
                               (app-utils/mask-person-id-partially tyonjohtaja-v2-doc)
                               test-application :fi)]
          (is (re-find #"Vastuuaika" body))
          (is (re-find #"15.11.2023" body)))))

    (testing "V2 foreman with started and ended dates shows full responsibility period"
      (with-redefs [foreman-util/get-project-application-for-foreman (constantly test-application)
                    foreman-util/foreman-application-info             (constantly {:started 1700000000000
                                                                                   :ended   1702592000000
                                                                                   :state   "acknowledged"
                                                                                   :history [{:state "acknowledged"
                                                                                              :user  {:id        "authority-user-id"
                                                                                                      :firstName "Viran"
                                                                                                      :lastName  "Omainen"}}]
                                                                                   :documents [tyonjohtaja-v2-doc]})]
        (let [{:keys [body]} (foreman-template/single-foreman-html
                               (app-utils/mask-person-id-partially tyonjohtaja-v2-doc)
                               test-application :fi)]
          (is (re-find #"Vastuuaika" body))
          (is (re-find #"15.11.2023 - 15.12.2023" body)))))))

(deftest join-name-test
  (testing "Join name with both first and last name present"
    (let [f-data {:data       {:person {:first "John"
                                        :last  "Doe"}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "John Doe" result))))

  (testing "Join name with only first name present"
    (let [f-data {:data       {:person {:first "Jane"}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "Jane" result))))

  (testing "Join name with only last name present"
    (let [f-data {:data       {:person {:last "Smith"}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "Smith" result))))

  (testing "Join name with both names blank - should return empty-text"
    (let [f-data {:data       {:person {}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "(Empty)" result))))

  (testing "Join name with both names as empty strings - should return empty-text"
    (let [f-data {:data       {:person {:first ""
                                        :last  ""}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "(Empty)" result))))

  (testing "Join name with string literals instead of paths"
    (let [f-data {:data       {}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data "Alice" "Wonderland")]
      (is (= "Alice Wonderland" result))))

  (testing "Join name with mixed path and string"
    (let [f-data {:data       {:person {:first "Bob"}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] "Builder")]
      (is (= "Bob Builder" result))))

  (testing "Join name with nil values - should return empty-text"
    (let [f-data {:data       {:person {:first nil
                                        :last  nil}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "(Empty)" result))))

  (testing "Join name handles whitespace-only strings as blank"
    (let [f-data {:data       {:person {:first "   "
                                        :last  "  "}}
                  :empty-text "(Empty)"}
          result (foreman-template/join-name f-data [:person :first] [:person :last])]
      (is (= "(Empty)" result)))))
