(ns lupapalvelu.backing-system.dynasty.message-test
  (:require [clojure.test :refer [are deftest is use-fixtures]]
            [lupapalvelu.backing-system.dynasty.message :as message]
            [lupapalvelu.backing-system.dynasty.municipal-context :as municipal-config]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as operations]
            [schema.core :as sc]))

(use-fixtures :once
  (fn [test-fn] (sc/with-fn-validation (test-fn)))
  (fn [test-fn] (i18n/with-lang "fi" (test-fn))))

(def application {:id               "LP-297-2022-90001"
                  :primaryOperation {:name "yl-olemassa-oleva-toiminta"}
                  :attachments      [{:id            "535f5fac0a5a948174ef2592"
                                      :latestVersion {:created      #inst "2022-12-21T09:30:00Z"
                                                      :filename     "some_file.pdf"
                                                      :content-type "application/pdf"
                                                      :fileId       "05b3af41-147f-4109-bf97-535f5fac0a5a"}
                                      :metadata      {:nakyvyys "julkinen"}
                                      :type          {:type-id    "palotekninen_lausunto"
                                                      :type-group "pelastusviranomaiselle_esitettavat_suunnitelmat"}}
                                     {:id       "535f5fac0a5a948174ef2593"
                                      :metadata {:nakyvyys "julkinen"}
                                      :type     {:type-id    "muu"
                                                 :type-group "muut"}}]
                  :created          #inst "2022-12-05T09:00:00Z"
                  :creator          {:firstName "Pena"
                                     :lastName  "Banaani"}
                  :documents        [{:data        {:_selected "henkilo"
                                                    :henkilo   {:henkilotiedot {:etunimi  {:value "Sonja"}
                                                                                :sukunimi {:value "Sibbo"}}
                                                                :osoite        {:katu                 {:value "Katuosoite 1 a 1"}
                                                                                :maa                  {:value "FIN"}
                                                                                :postinumero          {:value "33404"}
                                                                                :postitoimipaikannimi {:value "Sipoo"}}
                                                                :yhteystiedot  {:email   {:value "sonja.sibbo@sipoo.fi"}
                                                                                :puhelin {:value "03121991"}}}}
                                      :id          "133f5fa30a5a94e174ef2592"
                                      :schema-info {:subtype :hakija}}]
                  :organization     "297-YMP"
                  :permitType       "YL"})

(def integration {:integration-type :dynasty
                  :targets          #{}
                  :enabled?         false
                  :options          {;; Mimic what the admin would configure in the UI
                                     :host                      "localhost"
                                     :auth-host                 "localhost"
                                     :client                    "user"
                                     :client-secret             "hunter2"
                                     :organization              "0"
                                     :organization-name         "Alueellinen ympäristönsuojelu"
                                     :owner                     "ympäristöjohtaja"
                                     :case-base-id              "IB7"
                                     :handler-id                "svc_extinterfaceasiointitest"
                                     :document-type             "Liite"
                                     :document-phase            "Vireilletulo"
                                     :organization-hierarchy-id "80DD7A73-2C53-4996-915D-DAB2DE60BB6A"
                                     :applicant-role            "Vireillepanija"}})

(deftest kuopio-message-test
  (let [lang :fi
        now  #inst "2022-12-21T10:00:00Z"]
    (is (= [{::message/message-type ::message/case
             ::message/payload      {:case-base          {:id "IB7"}
                                     :case-date          "2022-12-21T12:00:00"
                                     :meta-json          {:initiator "Pena Banaani"}
                                     :organisation       {:name "Alueellinen ympäristönsuojelu"}
                                     :owner              "ympäristöjohtaja"
                                     :pending-start-date "2022-12-05T11:00:00"
                                     :personal-data      "Sisältää henkilötietoja"
                                     :title              "Ympäristöluvan muuttaminen (YSL 89 §)"
                                     :tos-class          "11.01.00"}}
            {::message/message-type   ::message/contact
             ::message/lp-document-id "133f5fa30a5a94e174ef2592"
             ::message/payload        {:channel      "NO_CHANNEL"
                                       :contact-info {:city           "Sipoo"
                                                      :country        "FIN"
                                                      :email          "sonja.sibbo@sipoo.fi"
                                                      :phone          "03121991"
                                                      :postal-address "Katuosoite 1 a 1"
                                                      :postal-code    "33404"}
                                       :person       {:first-name "Sonja"
                                                      :last-name  "Sibbo"}
                                       :role         "Vireillepanija"
                                       :type         "Person"}}
            {::message/message-type ::message/document
             ::message/payload      {:title                   "Ympäristöluvan muutoshakemus"
                                     :state                   "Draft"
                                     :personal-data           "Sisältää henkilötietoja"
                                     :is-public               true
                                     :publicity-class-name    "Julkinen"
                                     :arrival-type            "0"
                                     :case-attachment-options {:case-selector  {}
                                                               :phase-selector {:smart-attach-options {:path-selector [{:title "Vireilletulo"}]}}
                                                               :tos-title      "Muutoshakemus liitteineen"}
                                     :meta-json               {:document-type             "Hakemus"
                                                               :handler-id                "svc_extinterfaceasiointitest"
                                                               :organisation-hierarchy-id "80DD7A73-2C53-4996-915D-DAB2DE60BB6A"
                                                               :received-date             "2022-12-05"}}
             ::message/file-entry   {:mime-type "application/pdf"
                                     :name      "LP-297-2022-90001.pdf"}}
            {::message/message-type  ::message/attachment
             ::message/attachment-id "535f5fac0a5a948174ef2592"
             ::message/file-id       "05b3af41-147f-4109-bf97-535f5fac0a5a"
             ::message/payload       {:title                   "Palotekninen lausunto"
                                      :state                   "Draft"
                                      :personal-data           "Sisältää henkilötietoja"
                                      :publicity-class-name    "Julkinen"
                                      :owner                   "ympäristöjohtaja"
                                      :arrival-type            "0"
                                      :case-attachment-options {:case-selector  {}
                                                                :phase-selector {:smart-attach-options {:path-selector [{:title "Vireilletulo"}]}}
                                                                :tos-title      "Muutoshakemus liitteineen"}
                                      :meta-json               {:document-type             "Liite"
                                                                :handler-id                "svc_extinterfaceasiointitest"
                                                                :organisation-hierarchy-id "80DD7A73-2C53-4996-915D-DAB2DE60BB6A"
                                                                :public-title              "Palotekninen lausunto"
                                                                :received-date             "2022-12-21"}}}]
           (->> (municipal-config/build-municipal-context integration application lang now)
                message/create-case-messages
                vec)))))

(deftest salo-message-test
  (let [application (assoc application :organization "734-YMP")
        lang        :fi
        now         #inst "2022-12-21T10:00:00Z"]
    (is (= [{::message/message-type ::message/case
             ::message/payload      {:case-base          {:id "IB7"}
                                     :case-date          "2022-12-21T12:00:00"
                                     :meta-json          {:initiator "Pena Banaani"}
                                     :organisation       {:name "Alueellinen ympäristönsuojelu"}
                                     :owner              "ympäristöjohtaja"
                                     :pending-start-date "2022-12-05T11:00:00"
                                     :personal-data      "Sisältää henkilötietoja"
                                     :title              "Ympäristöluvan muuttaminen (YSL 89 §)"
                                     :tos-class          "11.01.00.00.01"}}
            {::message/message-type   ::message/contact
             ::message/lp-document-id "133f5fa30a5a94e174ef2592"
             ::message/payload        {:channel      "NO_CHANNEL"
                                       :contact-info {:city           "Sipoo"
                                                      :country        "FIN"
                                                      :email          "sonja.sibbo@sipoo.fi"
                                                      :phone          "03121991"
                                                      :postal-address "Katuosoite 1 a 1"
                                                      :postal-code    "33404"}
                                       :person       {:first-name "Sonja"
                                                      :last-name  "Sibbo"}
                                       :role         "Vireillepanija"
                                       :type         "Person"}}
            {::message/message-type ::message/document
             ::message/payload      {:title                   "Ympäristöluvan käsittely"
                                     :state                   "Draft"
                                     :personal-data           "Sisältää henkilötietoja"
                                     :is-public               true
                                     :publicity-class-name    "Julkinen"
                                     :case-attachment-options {:case-selector  {}
                                                               :phase-selector {:smart-attach-options {:path-selector [{:title "Vireillepano/-tulo"}
                                                                                                                       {:title "Hakemuksen vastaanottaminen"}]}}
                                                               :tos-title      "Ympäristölupahakemus liitteineen"}
                                     :meta-json               {:document-type             "Hakemus"
                                                               :handler-id                "svc_extinterfaceasiointitest"
                                                               :organisation-hierarchy-id "80DD7A73-2C53-4996-915D-DAB2DE60BB6A"
                                                               :received-date             "2022-12-05"}
                                     :arrival-type            "0"}
             ::message/file-entry   {:mime-type "application/pdf"
                                     :name      "LP-297-2022-90001.pdf"}}
            {::message/message-type  ::message/attachment
             ::message/attachment-id "535f5fac0a5a948174ef2592"
             ::message/file-id       "05b3af41-147f-4109-bf97-535f5fac0a5a"
             ::message/payload       {:title                   "Palotekninen lausunto"
                                      :state                   "Draft"
                                      :personal-data           "Sisältää henkilötietoja"
                                      :publicity-class-name    "Julkinen"
                                      :owner                   "ympäristöjohtaja"
                                      :case-attachment-options {:case-selector  {}
                                                                :phase-selector {:smart-attach-options {:path-selector [{:title "Vireillepano/-tulo"}
                                                                                                                        {:title "Hakemuksen vastaanottaminen"}]}}
                                                                :tos-title      "Ympäristölupahakemus liitteineen"}
                                      :meta-json               {:document-type             "Liite"
                                                                :handler-id                "svc_extinterfaceasiointitest"
                                                                :organisation-hierarchy-id "80DD7A73-2C53-4996-915D-DAB2DE60BB6A"
                                                                :public-title              "Palotekninen lausunto"
                                                                :received-date             "2022-12-21"}
                                      :arrival-type            "0"}}]
           (->> (municipal-config/build-municipal-context integration application lang now)
                message/create-case-messages
                vec)))))


(defn- extract-fields
  [operation organization municipality & {:keys [include-subphase?]}]
  (let [{:keys [permit-type]} (operations/get-operation-metadata operation)
        application           (-> application
                                  (assoc :permitType permit-type
                                         :municipality municipality
                                         :organization organization)
                                  (assoc-in [:primaryOperation :name] (name operation)))
        now                   #inst "2022-12-21T10:00:00Z"
        messages              (->> (municipal-config/build-municipal-context integration application :fi now)
                                   message/create-case-messages)
        find-msg              (fn [messages msg-type]
                                (->> messages
                                     (filter (comp #{msg-type} ::message/message-type))
                                     (map ::message/payload)
                                     first))
        case                  (find-msg messages ::message/case)
        document              (find-msg messages ::message/document)]
    (cond-> {::case-title (-> case :title)
             ::tos-class  (-> case :tos-class)
             ::tos-title  (-> document :case-attachment-options :tos-title)
             ::doc-title  (-> document :title)
             ::doc-type   (-> document :meta-json :document-type)}
      include-subphase? (assoc ::subphase (-> document
                                              (get-in [:case-attachment-options
                                                       :phase-selector
                                                       :smart-attach-options
                                                       :path-selector
                                                       1
                                                       :title]))))))

;; Note that minor changes in the results are not catastrophic as the Dynasty authority
;; user can always fine tune the data when a case/document is submitted.
(deftest kuopio-labels
  (are [operation expected] (= expected (extract-fields operation "297-YMP" "297"))
    :maa-aineslupa
    {::case-title "Maa-ainesten ottaminen (Maa-aineslupa, MAL 4 §)"
     ::tos-class  "11.02.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Maa-aineslupahakemus"
     ::doc-type   "Hakemus"}
    :maa-aineslupa-jatkoaika
    {::case-title "Hakemus luvan jatkamiseksi (MAL 10 §)"
     ::tos-class  "11.02.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Hakemus maa-ainesluvan jatkamiseksi"
     ::doc-type   "Hakemus"}
    :maa-ainesten-kotitarveotto
    {::case-title "Ilmoitus maa-ainesten kotitarveotosta (MAL 23a §)"
     ::tos-class  "11.02.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Ilmoitus maa-ainesten kotitarveotosta"
     ::doc-type   "Hakemus"}
    :lannan-levittamisesta-poikkeustilanteessa
    {::case-title "Ilmoitus lannan levittämisestä poikkeustilanteessa (10 § 2 mom.)"
     ::tos-class  "11.02.07"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Ilmoitus lannan levittämisestä poikkeustilanteessa"
     ::doc-type   "Hakemus"}
    :meluilmoitus
    {::case-title "Ilmoitus melua ja tärinää aiheuttavasta tilapäisestä toiminnasta (YSL 118 §)"
     ::tos-class  "11.01.02"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Ilmoitus melua ja tärinää aiheuttavasta tilapäisestä toiminnasta (YSL 118 §)"
     ::doc-type   "Ilmoitus"}
    :kirjallinen-vireillepano
    {::case-title "Kirjallinen vireillepano (YSL 186 §, JL 134 §, VL 11:2 ja 14:14)"
     ::tos-class  "11.01.04"
     ::tos-title  "Ympäristöhaittailmoitus liitteineen"
     ::doc-title  "Kirjallinen vireillepano (YSL 186 §, JL 134 §, VL 11:2 ja 14:14)"
     ::doc-type   "Ilmoitus"}
    :rekisterointi-ilmoitus
    {::case-title "Rekisteröinti-ilmoitus (YSL 116§)"
     ::tos-class  "11.01.03"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Rekisteröinti-ilmoitus (YSL 116§)"
     ::doc-type   "Ilmoitus"}
    ;; The ::tos-title and ::doc-type look wrong: shouldn't they be Ilmoitus?
    :jatteiden-hyodyntaminen-maarakentamisessa
    {::case-title "Valvontailmoitus pienimuotoisesta jätteen hyödyntämisestä maanrakentamisessa"
     ::tos-class  "11.01.04"
     ::tos-title  "Hakemus liitteineen (poikkeaminen)"
     ::doc-title  "Valvontailmoitus pienimuotoisesta jätteen hyödyntämisestä maanrakentamisessa"
     ::doc-type   "Hakemus"}
    :yleinen-ilmoitus
    {::case-title "Yleinen ilmoitus (YSL 115 a §)"
     ::tos-class  "11.01.01"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Ympäristösuojelulain mukainen yleinen ilmoitusmenettely"
     ::doc-type   "Ilmoitus"}
    :yl-uusi-toiminta
    {::case-title "Uusi ympäristöluvanvarainen toiminta (YSL 27 §)"
     ::tos-class  "11.01.00"
     ::tos-title  "Hakemus liitteineen (myös yhteislupa)"
     ::doc-title  "Ympäristölupahakemus"
     ::doc-type   "Hakemus"}
    :yl-olemassa-oleva-toiminta
    {::case-title "Ympäristöluvan muuttaminen (YSL 89 §)"
     ::tos-class  "11.01.00"
     ::tos-title  "Muutoshakemus liitteineen"
     ::doc-title  "Ympäristöluvan muutoshakemus"
     ::doc-type   "Hakemus"}
    :yl-toiminnan-muutos
    {::case-title "Ympäristöluvanvaraisen toiminnan olennainen muuttaminen (YSL 29 §)"
     ::tos-class  "11.01.00"
     ::tos-title  "Muutoshakemus liitteineen"
     ::doc-title  "Ympäristöluvan muutoshakemus"
     ::doc-type   "Hakemus"}
    :jatteen-keraystoiminta
    {::case-title "Ilmoitus jätteen ammattimaisesta keräystoiminnasta (JL 100 §)"
     ::tos-class  "11.02.03"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Ilmoitus jätteen ammattimaisesta keräystoiminnasta"
     ::doc-type   "Ilmoitus"}
    :koeluontoinen-toiminta
    {::case-title "Ilmoitus koeluontoisesta toiminnasta (YSL 119 §)"
     ::tos-class  "11.01.02"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Ilmoitus koeluontoisesta toiminnasta (YSL 119 §)"
     ::doc-type   "Ilmoitus"}
    :ilmoitus-poikkeuksellisesta-tilanteesta
    {::case-title "Ilmoitus poikkeuksellisesta tilanteesta (YSL 120 §, 123 §)"
     ::tos-class  "11.01.02"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Ilmoitus poikkeuksellisesta tilanteesta (YSL 120 §, 123 §)"
     ::doc-type   "Ilmoitus"}
    ;; Note the original excel specified the case title as ""Käytöstä poistetun öljy- .."
    :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan
    {::case-title "Käytöstäpoistetun öljy- tai kemikaalisäiliön jättäminen maaperään"
     ::tos-class  "11.01.04"
     ::tos-title  "Hakemus liitteineen (poikkeaminen)"
     ::doc-title  "Käytöstä poistetun öljy- tai kemikaalisäiliön jättäminen maaperään"
     ::doc-type   "Hakemus"}
    :muistomerkin-rauhoittaminen
    {::case-title "Luonnonmuistomerkin rauhoittaminen (LSL 26 §)"
     ::tos-class  "11.02.05"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Luonnonmuistomerkin rauhoittaminen"
     ::doc-type   "Hakemus"}
    :yhteiskasittely-maa-aines-ja-ymparistoluvalle
    {::case-title "Maa-ainesluvan ja ympäristöluvan yhteiskäsittelyhakemus (MAL 4a § ja YSL 47a §)"
     ::tos-class  "11.01.00"
     ::tos-title  "Hakemus liitteineen (myös yhteislupa)"
     ::doc-title  "Ympäristö- ja maa-ainesluvan yhteislupahakemus"
     ::doc-type   "Hakemus"}
    :maastoliikennelaki-kilpailut-ja-harjoitukset
    {::case-title "Maastoliikennelain (1710/1995) 30 §:n mukainen lupa kilpailuihin ja harjoituksiin"
     ::tos-class  "11.02.02"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Maastoliikennelain mukainen lupa"
     ::doc-type   "Hakemus"}
    :vesiliikennelaki-kilpailut-ja-harjoitukset
    {::case-title "Vesiliikennelain 106§:n mukainen lupa kilpailuihin ja harjoituksiin"
     ::tos-class  "11.02.02"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vesiliikennelain mukainen lupa"
     ::doc-type   "Hakemus"}
    :ymparistoluvan-selventaminen
    {::case-title "Ympäristöluvan selventäminen (YSL 92 §)"
     ::tos-class  "11.01.00"
     ::tos-title  "Muutoshakemus liitteineen"
     ::doc-title  "Ympäristöluvan muutoshakemus"
     ::doc-type   "Hakemus"}
    ;; The ::tos-title and ::doc-type look wrong: shouldn't they be Ilmoitus?
    :lannan-varastointi-v3
    {::case-title "Ilmoitus lannan tai lannoitevalmisteen poikkeuksellisesta varastoinnista (aumausilmoitus) (9 §)"
     ::tos-class  "11.02.07"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus)"
     ::doc-type   "Hakemus"}
    :lannan-varastointi-v2
    {::case-title "Ilmoitus lannan tai lannoitevalmisteen poikkeuksellisesta varastoinnista (aumausilmoitus) (9 §)"
     ::tos-class  "11.02.07"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Ilmoitus lannan varastointitilavuudesta poikkeamisesta (aumausilmoitus)"
     ::doc-type   "Hakemus"}
    :talousjatevesien-kasittelysta-poikkeaminen
    {::case-title "Talousjätevesien käsittelyvaatimuksista poikkeaminen (YSL 156 §)"
     ::tos-class  "11.01.04"
     ::tos-title  "Hakemus liitteineen (poikkeaminen)"
     ::doc-title  "Talousjätevesien käsittelyvaatimuksista poikkeaminen (YSL 156 §)"
     ::doc-type   "Hakemus"}
    :vvvl-vesijohdosta
    {::case-title "Vapautus vesijohtoon liittymisestä (VHL 11 §)"
     ::tos-class  "11.02.01"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vapautus vesijohtoon liittymisestä"
     ::doc-type   "Hakemus"}
    :vvvl-viemarista
    {::case-title "Vapautus jätevesiviemäriin liittymisestä (VHL 11 §)"
     ::tos-class  "11.02.01"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vapautus jätevesiviemäriin liittymisestä"
     ::doc-type   "Hakemus"}
    :vvvl-vesijohdosta-ja-viemarista
    {::case-title "Vapautus vesijohtoon ja jätevesiviemäriin liittymisestä (VHL 11 §)"
     ::tos-class  "11.02.01"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vapautus vesijohtoon ja jätevesiviemäriin liittymisestä"
     ::doc-type   "Hakemus"}
    :leirintaalueilmoitus
    {::case-title "Leirintäalueilmoitus"
     ::tos-class  "11.02.06"
     ::tos-title  "Leirintäalueilmoitus liitteineen"
     ::doc-title  "Leirintäalueilmoitus"
     ::doc-type   "Ilmoitus"}))

(deftest salo-labels
  (are [operation expected] (= expected (extract-fields operation "734-YMP" "734" :include-subphase? true))
    :maa-aineslupa
    {::case-title "Maa-ainesten ottaminen (Maa-aineslupa, MAL 4 §)"
     ::tos-class  "10.03.00.05.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Maa-ainesluvan käsittely"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :maa-aineslupa-jatkoaika
    {::case-title "Hakemus luvan jatkamiseksi (MAL 10 §)"
     ::tos-class  "10.03.00.05.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Maa-ainesluvan käsittely"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :maa-ainesten-kotitarveotto
    {::case-title "Ilmoitus maa-ainesten kotitarveotosta (MAL 23a §)"
     ::tos-class  "10.03.00.05.02"
     ::tos-title  "Kotitarveottoilmoitus liitteineen"
     ::doc-title  "Kotitarveotto"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen pyytäminen ja vastaanottaminen"}
    :lannan-levittamisesta-poikkeustilanteessa
    {::case-title "Ilmoitus lannan levittämisestä poikkeustilanteessa (10 § 2 mom.)"
     ::tos-class  "11.03.03.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Nitriittiasetuksen valvonta"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    :meluilmoitus
    {::case-title "Ilmoitus melua ja tärinää aiheuttavasta tilapäisestä toiminnasta (YSL 118 §)"
     ::tos-class  "11.01.00.02"
     ::tos-title  "Meluilmoitus, ilmoitus koeluonteisesta toiminnasta tai poikkeuksellisesta tilanteesta liitteineen"
     ::doc-title  "Ilmoitusmenettely"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    ;; :kirjallinen-vireillepano => Not in use
    :rekisterointi-ilmoitus
    {::case-title "Rekisteröinti-ilmoitus (YSL 116§)"
     ::tos-class  "11.01.00.03"
     ::tos-title  "Rekisteröinti-ilmoitus liitteineen"
     ::doc-title  "Rekisteröintimenettely"
     ::doc-type   "Ilmoitus"
     ::subphase   "Rekisteröinti-ilmoituksen vastaanottaminen"}
    :jatteiden-hyodyntaminen-maarakentamisessa
    {::case-title "Valvontailmoitus pienimuotoisesta jätteen hyödyntämisestä maanrakentamisessa"
     ::tos-class  "11.03.07.01"
     ::tos-title  "Jätteen hyödyntämisilmoitus liitteineen"
     ::doc-title  "Jätteen hyödyntäminen maarakentamisessa"
     ::doc-type   "Ilmoitus"
     ::subphase   "Hyödyntämisilmoituksen vastaanottaminen"}
    :yleinen-ilmoitus
    {::case-title "Yleinen ilmoitus (YSL 115 a §)"
     ::tos-class  "11.01.00.07"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Yleinen ilmoitusmenettely"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    :yl-uusi-toiminta
    {::case-title "Uusi ympäristöluvanvarainen toiminta (YSL 27 §)"
     ::tos-class  "11.01.00.00.01"
     ::tos-title  "Ympäristölupahakemus liitteineen"
     ::doc-title  "Ympäristöluvan käsittely"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :yl-olemassa-oleva-toiminta
    {::case-title "Ympäristöluvan muuttaminen (YSL 89 §)"
     ::tos-class  "11.01.00.00.01"
     ::tos-title  "Ympäristölupahakemus liitteineen"
     ::doc-title  "Ympäristöluvan käsittely"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :yl-toiminnan-muutos
    {::case-title "Ympäristöluvanvaraisen toiminnan olennainen muuttaminen (YSL 29 §)"
     ::tos-class  "11.01.00.00.01"
     ::tos-title  "Ympäristölupahakemus liitteineen"
     ::doc-title  "Ympäristöluvan käsittely"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :jatteen-keraystoiminta
    {::case-title "Ilmoitus jätteen ammattimaisesta keräystoiminnasta (JL 100 §)"
     ::tos-class  "11.01.06.02"
     ::tos-title  "Ilmoitus ammattimaisesta keräystoiminnasta liitteineen"
     ::doc-title  "Jätteen ammattimainen keräystoiminta"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen ammattimaisesta keräystoiminnasta"}
    :koeluontoinen-toiminta
    {::case-title "Ilmoitus koeluontoisesta toiminnasta (YSL 119 §)"
     ::tos-class  "11.01.00.02"
     ::tos-title  "Meluilmoitus, ilmoitus koeluonteisesta toiminnasta tai poikkeuksellisesta tilanteesta liitteineen"
     ::doc-title  "Ilmoitusmenettely"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    :ilmoitus-poikkeuksellisesta-tilanteesta
    {::case-title "Ilmoitus poikkeuksellisesta tilanteesta (YSL 120 §, 123 §)"
     ::tos-class  "11.01.00.02"
     ::tos-title  "Meluilmoitus, ilmoitus koeluonteisesta toiminnasta tai poikkeuksellisesta tilanteesta liitteineen"
     ::doc-title  "Ilmoitusmenettely"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan
    {::case-title "Käytöstäpoistetun öljy- tai kemikaalisäiliön jättäminen maaperään"
     ::tos-class  "11.01.00.04"
     ::tos-title  "Poikkeamishakemus liitteineen"
     ::doc-title  "Poikkeaminen ympäristönsuojelumääräyksistä"
     ::doc-type   "Hakemus"
     ::subphase   "Poikkeamishakemuksen vastaanottaminen"}
    :muistomerkin-rauhoittaminen
    {::case-title "Luonnonmuistomerkin rauhoittaminen (LSL 26 §)"
     ::tos-class  "11.01.04.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Luonnonmuistomerkin rauhoitus"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemus luonnonmuistomerkin rauhoittamiseksi tai rauhoituksen lakkauttamiseksi"}
    :yhteiskasittely-maa-aines-ja-ymparistoluvalle
    {::case-title "Maa-ainesluvan ja ympäristöluvan yhteiskäsittelyhakemus (MAL 4a § ja YSL 47a §)"
     ::tos-class  "10.03.00.05.05"
     ::tos-title  "Yhteiskäsittelylupahakemus liitteineen"
     ::doc-title  "Yhteiskäsittelyluvan käsittely"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :maastoliikennelaki-kilpailut-ja-harjoitukset
    {::case-title "Maastoliikennelain (1710/1995) 30 §:n mukainen lupa kilpailuihin ja harjoituksiin"
     ::tos-class  "11.01.03.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Kunnan päätökset"
     ::doc-type   "Ilmoitus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :vesiliikennelaki-kilpailut-ja-harjoitukset
    {::case-title "Vesiliikennelain 106§:n mukainen lupa kilpailuihin ja harjoituksiin"
     ::tos-class  "11.01.02.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Kunnan ympäristönsuojeluviranomaisen päätökset"
     ::doc-type   "Ilmoitus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :ymparistoluvan-selventaminen
    {::case-title "Ympäristöluvan selventäminen (YSL 92 §)"
     ::tos-class  "11.01.00.00.01"
     ::tos-title  "Ympäristölupahakemus liitteineen"
     ::doc-title  "Ympäristöluvan käsittely"
     ::doc-type   "Ilmoitus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :lannan-varastointi-v3
    {::case-title "Ilmoitus lannan tai lannoitevalmisteen poikkeuksellisesta varastoinnista (aumausilmoitus) (9 §)"
     ::tos-class  "11.03.03.00"
     ::tos-title  "Aumausilmoitus liitteineen"
     ::doc-title  "Nitriittiasetuksen valvonta"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    :lannan-varastointi-v2
    {::case-title "Ilmoitus lannan tai lannoitevalmisteen poikkeuksellisesta varastoinnista (aumausilmoitus) (9 §)"
     ::tos-class  "11.03.03.00"
     ::tos-title  "Aumausilmoitus liitteineen"
     ::doc-title  "Nitriittiasetuksen valvonta"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}
    :talousjatevesien-kasittelysta-poikkeaminen
    {::case-title "Talousjätevesien käsittelyvaatimuksista poikkeaminen (YSL 156 §)"
     ::tos-class  "11.01.00.05"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Talousjätevesien käsittelyvaatimuksista poikkeaminen"
     ::doc-type   "Ilmoitus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :vvvl-vesijohdosta
    {::case-title "Vapautus vesijohtoon liittymisestä (VHL 11 §)"
     ::tos-class  "11.01.05.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vesihuoltolain mukainen verkostoon liittämisen valvonta"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :vvvl-viemarista
    {::case-title "Vapautus jätevesiviemäriin liittymisestä (VHL 11 §)"
     ::tos-class  "11.01.05.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vesihuoltolain mukainen verkostoon liittämisen valvonta"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :vvvl-vesijohdosta-ja-viemarista
    {::case-title "Vapautus vesijohtoon ja jätevesiviemäriin liittymisestä (VHL 11 §)"
     ::tos-class  "11.01.05.00"
     ::tos-title  "Hakemus liitteineen"
     ::doc-title  "Vesihuoltolain mukainen verkostoon liittämisen valvonta"
     ::doc-type   "Hakemus"
     ::subphase   "Hakemuksen vastaanottaminen"}
    :leirintaalueilmoitus
    {::case-title "Leirintäalueilmoitus"
     ::tos-class  "11.03.08"
     ::tos-title  "Ilmoitus liitteineen"
     ::doc-title  "Leirintäalueet"
     ::doc-type   "Ilmoitus"
     ::subphase   "Ilmoituksen vastaanottaminen"}))
