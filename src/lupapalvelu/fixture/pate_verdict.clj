(ns lupapalvelu.fixture.pate-verdict
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schemas :as ps]
            [lupapalvelu.system.util-components :as components]
            [sade.core :refer :all]
            [schema.core :as sc]))

(def created (now))

(def users (filter (comp #{"admin" "sonja" "ronja" "pena" "mikko@example.com"
                           "kaino@solita.fi" "erkki@example.com" "sipoo_asiointiapi"
                           "sipoo-r-backend" "sipoo" "matti-rest-api-user"}
                         :username)
                   minimal/users))

(defn wrap [v]
  ((metadata/wrapper "person" 12345) v))

(defn plan [fi sv en type]
  {:fi (wrap fi) :sv (wrap sv) :en (wrap en) :type (wrap type)})

(defn review [fi sv en type]
  (plan fi sv en type))

(def settings-r (sc/validate ps/PateSettingsByCategory
                             {:r  {:draft    {:organization-name        "Sipoo Building Control"
                                              :voimassa                 (wrap "1")
                                              :julkipano                (wrap "2")
                                              :boardname                (wrap "asd")
                                              :muutoksenhaku            (wrap "1")
                                              :anto                     (wrap "0")
                                              :aloitettava              (wrap "1")
                                              :verdict-code             (wrap ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"])
                                              :lainvoimainen            (wrap "1")
                                              :lautakunta-muutoksenhaku (wrap "2")
                                              :k-kuulutus               (wrap "1")
                                              :k-muutoksenhaku          (wrap "2")
                                              :k-voimassa               (wrap "1")
                                              :k-aloitettava            (wrap "1")
                                              :k-lainvoimainen          (wrap "1")
                                              :reviews                  {:5a7affbf5266a1d9c1581957 (review "Startti" "start" "start"
                                                                                                           "aloituskokous")
                                                                         :5a7affcc5266a1d9c1581958 (review "Loppukatselmus" "Slut" "End"
                                                                                                           "loppukatselmus")
                                                                         :5a7affe15266a1d9c1581959 (review "Katselmus" "Syn" "Review"
                                                                                                           "muu-katselmus")}
                                              :plans                    {:5a85960a809b5a1e454f3233 (plan "Suunnitelmat" "Planer" "Plans"
                                                                                                         "muu-suunnitelma")
                                                                         :5a85960a809b5a1e454f3234 (plan "ErityisSuunnitelmat"
                                                                                                         "SpecialPlaner" "SpecialPlans"
                                                                                                         "muu-suunnitelma")}}
                                   :modified created}
                              :tj {:draft    {:anto                     (wrap "2")
                                              :lainvoimainen            (wrap "2")
                                              :muutoksenhaku            (wrap "2")
                                              :verdict-code             (wrap ["myonnetty" "hyvaksytty"])
                                              :lautakunta-muutoksenhaku (wrap "14")
                                              :boardname                (wrap "Lauta ja Kunta")
                                              :julkipano                (wrap "1")
                                              :aloitettava              (wrap "1")
                                              :voimassa                 (wrap "1")}
                                   :modified created}
                              :p  {:draft    {:voimassa                 (wrap "1")
                                              :julkipano                (wrap "10")
                                              :boardname                (wrap "Lautakuntas")
                                              :muutoksenhaku            (wrap "12")
                                              :anto                     (wrap "13")
                                              :organization-name        (wrap "Sipoon poikkeusvalvonta")
                                              :verdict-code             (wrap ["evatty" "puollettu" "hyvaksytty"])
                                              :lainvoimainen            (wrap "14")
                                              :lautakunta-muutoksenhaku (wrap "15")
                                              :k-kuulutus               (wrap "1")
                                              :k-muutoksenhaku          (wrap "2")
                                              :k-voimassa               (wrap "1")
                                              :k-aloitettava            (wrap "1")
                                              :k-lainvoimainen          (wrap "1")}
                                   :modified created}}))

(def verdict-templates-r
  "Some PATE verdict-templates to help with testing building control applications including R, TJ, and P templates"
  (sc/validate [ps/PateSavedTemplate]
               [{:id           "5a7aff3e5266a1d9c1581956"
                 :organization "753-R"
                 :draft        {:verdict-dates         (wrap ["lainvoimainen" "anto" "julkipano"])
                                :kuulutus-dates        (wrap ["k-lainvoimainen" "k-kuulutus"])
                                :bulletinOpDescription (wrap "")
                                :giver                 (wrap "viranhaltija")
                                :vastaava-tj           (wrap true)
                                :vastaava-tj-included  (wrap true)
                                :conditions            {}
                                :language              (wrap "fi")
                                :reviews               {:5a7affbf5266a1d9c1581957 {:included (wrap true)
                                                                                   :selected (wrap true)}
                                                        :5a7affcc5266a1d9c1581958 {:included (wrap true)
                                                                                   :selected (wrap true)}
                                                        :5a7affe15266a1d9c1581959 {:included (wrap true)
                                                                                   :selected (wrap true)}}
                                :paatosteksti          (wrap "Ver Dict")
                                :upload                (wrap true)
                                :paloluokka            (wrap true)}
                 :name         (wrap "Päätöspohja")
                 :category     "r"
                 :modified     created
                 :deleted      (wrap false)
                 :published    {:published  (wrap created)
                                :data       {:verdict-dates         ["lainvoimainen" "anto" "julkipano"]
                                             :kuulutus-dates        ["k-lainvoimainen" "k-kuulutus"]
                                             :bulletinOpDescription ""
                                             :giver                 "viranhaltija"
                                             :plans                 ["5a85960a809b5a1e454f3233"
                                                                     "5a85960a809b5a1e454f3234"]
                                             :vastaava-tj           true
                                             :vastaava-tj-included  true
                                             :language              "fi"
                                             :paatosteksti          "Ver Dict"
                                             :upload                true
                                             :paloluokka            true}
                                :inclusions ["appeal"
                                             "verdict-dates"
                                             "kuulutus-dates"
                                             "kuulutus-dates-help-text"
                                             "vss-luokka"
                                             "kokoontumistilanHenkilomaara"
                                             "bulletinOpDescription"
                                             "purpose"
                                             "giver"
                                             "complexity"
                                             "plans"
                                             "autopaikat"
                                             "tj"
                                             "tj-included"
                                             "link-to-settings"
                                             "verdict-code"
                                             "complexity-text"
                                             "extra-info"
                                             "iv-tj"
                                             "conditions"
                                             "rights"
                                             "iv-tj-included"
                                             "language"
                                             "erityis-tj-included"
                                             "vv-tj-included"
                                             "reviews"
                                             "paatosteksti"
                                             "vastaava-tj"
                                             "deviations"
                                             "neighbors"
                                             "vastaava-tj-included"
                                             "link-to-settings-no-label"
                                             "vv-tj"
                                             "upload"
                                             "statements"
                                             "erityis-tj"
                                             "add-condition"
                                             "paloluokka"]
                                :settings   {:verdict-code ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
                                             :foremen      ["vastaava-tj"]
                                             :date-deltas  {:julkipano       {:delta 2 :unit "days"}
                                                            :anto            {:delta 0 :unit "days"}
                                                            :muutoksenhaku   {:delta 1 :unit "days"}
                                                            :lainvoimainen   {:delta 1 :unit "days"}
                                                            :aloitettava     {:delta 1 :unit "years"}
                                                            :voimassa        {:delta 1 :unit "years"}
                                                            :k-aloitettava   {:delta 1 :unit "years"}
                                                            :k-kuulutus      {:delta 1 :unit "days"}
                                                            :k-lainvoimainen {:delta 1 :unit "days"}
                                                            :k-muutoksenhaku {:delta 1 :unit "days"}
                                                            :k-voimassa      {:delta 1 :unit "years"}}
                                             :plans        [{:fi   "Suunnitelmat"    :sv       "Planer" :en "Plans"
                                                             :type "muu-suunnitelma" :selected true}
                                                            {:fi   "ErityisSuunnitelmat" :sv       "SpecialPlaner" :en "SpecialPlans"
                                                             :type "muu-suunnitelma"     :selected true}]
                                             :reviews      [{:fi   "Aloituskokous" :sv       "start" :en "start"
                                                             :type "aloituskokous" :selected true}
                                                            {:fi   "Loppukatselmus" :sv       "Loppu" :en "Loppu"
                                                             :type "loppukatselmus" :selected true}
                                                            {:fi   "Katselmus"     :sv       "Syn" :en "Review"
                                                             :type "muu-katselmus" :selected true}]}}}
                {:id           "5a7c4f33d98b0fe901eee1e6"
                 :organization "753-R"
                 :draft        {}
                 :name         (wrap "Päätöspohja")
                 :category     "r"
                 :modified     created
                 :deleted      (wrap false)}
                {:id           "5acc68c953b771ded5d45605"
                 :organization "753-R"
                 :draft        {:verdict-dates         (wrap ["voimassa" "anto"])
                                :kuulutus-dates        (wrap ["k-voimassa"])
                                :language              (wrap "fi")
                                :giver                 (wrap "viranhaltija")
                                :verdict-code          (wrap "hyvaksytty")
                                :paatosteksti          (wrap "\nVerdict given\n")
                                :removed-sections      {:appeal     (wrap true)
                                                        :purpose    (wrap true)
                                                        :buildings  (wrap true)
                                                        :complexity (wrap true)
                                                        :plans      (wrap true)
                                                        :foremen    (wrap true)
                                                        :extra-info (wrap true)
                                                        :conditions (wrap true)
                                                        :rights     (wrap true)
                                                        :reviews    (wrap true)
                                                        :deviations (wrap true)
                                                        :neighbors  (wrap true)
                                                        :statements (wrap true)}
                                :bulletinOpDescription (wrap "\nBulletin\n")}
                 :name         (wrap "Jatkoaika")
                 :category     "r"
                 :modified     1523345647736
                 :deleted      (wrap false)
                 :published    {:published  (wrap 1523345649254)
                                :data       {:verdict-dates         ["voimassa" "anto"]
                                             :kuulutus-dates        ["k-voimassa"]
                                             :bulletinOpDescription "\nBulletin\n"
                                             :giver                 "viranhaltija"
                                             :plans                 []
                                             :removed-sections      {:appeal     true
                                                                     :purpose    true
                                                                     :buildings  true
                                                                     :complexity true
                                                                     :plans      true
                                                                     :foremen    true
                                                                     :extra-info true
                                                                     :conditions true
                                                                     :rights     true
                                                                     :reviews    true
                                                                     :deviations true
                                                                     :neighbors  true
                                                                     :statements true}
                                             :verdict-code          "hyvaksytty"
                                             :language              "fi"
                                             :reviews               []
                                             :paatosteksti          "\nVerdict given\n"}
                                :inclusions ["verdict-dates"
                                             "kuulutus-dates"
                                             "kuulutus-dates-help-text"
                                             "bulletinOpDescription"
                                             "giver"
                                             "link-to-settings"
                                             "verdict-code"
                                             "language"
                                             "paatosteksti"
                                             "upload"]
                                :settings   {:verdict-code ["evatty"
                                                            "myonnetty"
                                                            "osittain-myonnetty"
                                                            "hyvaksytty"
                                                            "annettu-lausunto"
                                                            "ei-puollettu"]
                                             :foremen      ["erityis-tj"]
                                             :date-deltas  {:julkipano       {:delta 1 :unit "days"}
                                                            :anto            {:delta 1 :unit "days"}
                                                            :muutoksenhaku   {:delta 1 :unit "days"}
                                                            :lainvoimainen   {:delta 1 :unit "days"}
                                                            :aloitettava     {:delta 1 :unit "years"}
                                                            :voimassa        {:delta 1 :unit "years"}
                                                            :k-aloitettava   {:delta 1 :unit "years"}
                                                            :k-kuulutus      {:delta 1 :unit "days"}
                                                            :k-lainvoimainen {:delta 1 :unit "days"}
                                                            :k-muutoksenhaku {:delta 1 :unit "days"}
                                                            :k-voimassa      {:delta 1 :unit "years"}}
                                             :plans        []
                                             :reviews      []}}}
                {:id           "5b0689f8cb66507187fbc18f"
                 :organization "753-R"
                 :draft        {:verdict-dates  (wrap ["muutoksenhaku" "lainvoimainen" "anto"])
                                :kuulutus-dates (wrap ["k-muutoksenhaku" "k-lainvoimainen"])
                                :language       (wrap "fi")
                                :giver          (wrap "viranhaltija")
                                :paatosteksti   (wrap "Paatos annettu\n")
                                :verdict-code   (wrap "hyvaksytty")
                                :appeal         (wrap "Ohje muutoksen hakuun.\n")
                                :upload         (wrap true)}
                 :name         (wrap "TJ verdict template")
                 :category     "tj"
                 :modified     1527155907891
                 :deleted      (wrap false)
                 :published    {:published  (wrap 1527155908947)
                                :data       {:appeal         "Ohje muutoksen hakuun.\n"
                                             :verdict-dates  ["muutoksenhaku" "lainvoimainen" "anto"]
                                             :kuulutus-dates ["k-muutoksenhaku" "k-lainvoimainen"]
                                             :giver          "viranhaltija"
                                             :verdict-code   "hyvaksytty"
                                             :language       "fi"
                                             :paatosteksti   "Paatos annettu\n"
                                             :upload         true}
                                :inclusions ["link-to-settings-no-label"
                                             "verdict-dates"
                                             "kuulutus-dates"
                                             "kuulutus-dates-help-text"
                                             "giver"
                                             "link-to-settings"
                                             "verdict-code"
                                             "language"
                                             "paatosteksti"
                                             "appeal"
                                             "upload"]
                                :settings   {:verdict-code ["myonnetty" "hyvaksytty"]
                                             :date-deltas  {:julkipano       {:delta 0 :unit "days"}
                                                            :anto            {:delta 2 :unit "days"}
                                                            :muutoksenhaku   {:delta 2 :unit "days"}
                                                            :lainvoimainen   {:delta 2 :unit "days"}
                                                            :aloitettava     {:delta 0 :unit "days"}
                                                            :voimassa        {:delta 0 :unit "days"}
                                                            :k-aloitettava   {:delta 1 :unit "years"}
                                                            :k-kuulutus      {:delta 1 :unit "days"}
                                                            :k-lainvoimainen {:delta 1 :unit "days"}
                                                            :k-muutoksenhaku {:delta 1 :unit "days"}
                                                            :k-voimassa      {:delta 1 :unit "years"}}
                                             :plans        []
                                             :reviews      []}}}
                {:id           "ba7aff3e5266a1d9c1581666"
                 :organization "753-R"
                 :draft        {:verdict-dates         (wrap ["julkipano"])
                                :kuulutus-dates        (wrap ["k-kuulutus" "k-muutoksenhaku"])
                                :bulletinOpDescription (wrap "Pate bulletin description")
                                :giver                 (wrap "viranhaltija")
                                ;; Note that `:plans` is only included in the published template -> republishing will drop it
                                :vastaava-tj           (wrap true)
                                :vastaava-tj-included  (wrap true)
                                :conditions            {}
                                :language              (wrap "fi")
                                :reviews               {:5a7affbf5266a1d9c1581957 {:included (wrap true)
                                                                                   :selected (wrap true)}
                                                        :5a7affcc5266a1d9c1581958 {:included (wrap true)
                                                                                   :selected (wrap true)}
                                                        :5a7affe15266a1d9c1581959 {:included (wrap true)
                                                                                   :selected (wrap true)}}
                                :paatosteksti          (wrap "Ver Dict")
                                :upload                (wrap true)
                                :paloluokka            (wrap true)}
                 :name         (wrap "Päätöspohja")
                 :category     "r"
                 :modified     created
                 :deleted      (wrap false)
                 :published    {:published  (wrap created)
                                :data       {:verdict-dates         ["julkipano"]
                                             :kuulutus-dates        ["k-kuulutus" "k-muutoksenhaku"]
                                             :bulletinOpDescription "Pate bulletin description"
                                             :giver                 "viranhaltija"
                                             :plans                 ["5a85960a809b5a1e454f3233"
                                                                     "5a85960a809b5a1e454f3234"]
                                             :vastaava-tj           true
                                             :vastaava-tj-included  true
                                             :language              "fi"
                                             :paatosteksti          "Ver Dict"
                                             :upload                true
                                             :paloluokka            true}
                                :inclusions ["appeal"
                                             "verdict-dates"
                                             "kuulutus-dates"
                                             "kuulutus-dates-help-text"
                                             "vss-luokka"
                                             "kokoontumistilanHenkilomaara"
                                             "bulletinOpDescription"
                                             "purpose"
                                             "giver"
                                             "complexity"
                                             "plans"
                                             "autopaikat"
                                             "tj"
                                             "tj-included"
                                             "link-to-settings"
                                             "verdict-code"
                                             "complexity-text"
                                             "extra-info"
                                             "iv-tj"
                                             "conditions"
                                             "rights"
                                             "iv-tj-included"
                                             "language"
                                             "erityis-tj-included"
                                             "vv-tj-included"
                                             "reviews"
                                             "paatosteksti"
                                             "vastaava-tj"
                                             "deviations"
                                             "neighbors"
                                             "vastaava-tj-included"
                                             "link-to-settings-no-label"
                                             "vv-tj"
                                             "upload"
                                             "statements"
                                             "erityis-tj"
                                             "add-condition"
                                             "paloluokka"]
                                :settings   {:verdict-code      ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
                                             :foremen           ["vastaava-tj"]
                                             :date-deltas       {:julkipano       {:delta 2 :unit "days"}
                                                                 :anto            {:delta 0 :unit "days"}
                                                                 :muutoksenhaku   {:delta 1 :unit "days"}
                                                                 :lainvoimainen   {:delta 1 :unit "days"}
                                                                 :aloitettava     {:delta 1 :unit "years"}
                                                                 :voimassa        {:delta 1 :unit "years"}
                                                                 :k-aloitettava   {:delta 1 :unit "years"}
                                                                 :k-kuulutus      {:delta 1 :unit "days"}
                                                                 :k-lainvoimainen {:delta 1 :unit "days"}
                                                                 :k-muutoksenhaku {:delta 1 :unit "days"}
                                                                 :k-voimassa      {:delta 1 :unit "years"}}
                                             :plans             [{:fi   "Suunnitelmat"    :sv       "Planer" :en "Plans"
                                                                  :type "muu-suunnitelma" :selected true}
                                                                 {:fi   "ErityisSuunnitelmat" :sv       "SpecialPlaner" :en "SpecialPlans"
                                                                  :type "muu-suunnitelma"     :selected true}]
                                             :reviews           [{:fi   "Aloituskokous" :sv       "start" :en "start"
                                                                  :type "aloituskokous" :selected true}
                                                                 {:fi   "Loppukatselmus" :sv       "Loppu" :en "Loppu"
                                                                  :type "loppukatselmus" :selected true}
                                                                 {:fi   "Katselmus"     :sv       "Syn" :en "Review"
                                                                  :type "muu-katselmus" :selected true}]
                                             :organization-name "Sipoo Building Control"
                                             :boardname         "The Board"}}}
                {:id           "69aaaa39615f8072bec823be"
                 :organization "753-R"
                 :draft        {:verdict-dates         (wrap ["julkipano"])
                                :kuulutus-dates        (wrap ["k-kuulutus" "k-muutoksenhaku" "k-lainvoimainen" "k-aloitettava" "k-voimassa"])
                                :bulletinOpDescription (wrap "Pate bulletin description")
                                :giver                 (wrap "viranhaltija")
                                ;; Note that `:plans` is only included in the published template -> republishing will drop it
                                :vastaava-tj           (wrap true)
                                :vastaava-tj-included  (wrap true)
                                :conditions            {}
                                :language              (wrap "fi")
                                :reviews               {:5a7affbf5266a1d9c1581957 {:included (wrap true)
                                                                                   :selected (wrap true)}
                                                        :5a7affcc5266a1d9c1581958 {:included (wrap true)
                                                                                   :selected (wrap true)}
                                                        :5a7affe15266a1d9c1581959 {:included (wrap true)
                                                                                   :selected (wrap true)}}
                                :paatosteksti          (wrap "Ver Dict")
                                :upload                (wrap true)
                                :paloluokka            (wrap true)}
                 :name         (wrap "Päätöspohja")
                 :category     "r"
                 :modified     created
                 :deleted      (wrap false)
                 :published    {:published  (wrap created)
                                :data       {:verdict-dates         ["julkipano"]
                                             :kuulutus-dates        ["k-kuulutus" "k-muutoksenhaku" "k-lainvoimainen" "k-aloitettava" "k-voimassa"]
                                             :bulletinOpDescription "Pate bulletin description"
                                             :giver                 "viranhaltija"
                                             :plans                 ["5a85960a809b5a1e454f3233"
                                                                     "5a85960a809b5a1e454f3234"]
                                             :vastaava-tj           true
                                             :vastaava-tj-included  true
                                             :language              "fi"
                                             :paatosteksti          "Ver Dict"
                                             :upload                true
                                             :paloluokka            true}
                                :inclusions ["appeal"
                                             "verdict-dates"
                                             "kuulutus-dates"
                                             "kuulutus-dates-help-text"
                                             "vss-luokka"
                                             "kokoontumistilanHenkilomaara"
                                             "bulletinOpDescription"
                                             "purpose"
                                             "giver"
                                             "complexity"
                                             "plans"
                                             "autopaikat"
                                             "tj"
                                             "tj-included"
                                             "link-to-settings"
                                             "verdict-code"
                                             "complexity-text"
                                             "extra-info"
                                             "iv-tj"
                                             "conditions"
                                             "rights"
                                             "iv-tj-included"
                                             "language"
                                             "erityis-tj-included"
                                             "vv-tj-included"
                                             "reviews"
                                             "paatosteksti"
                                             "vastaava-tj"
                                             "deviations"
                                             "neighbors"
                                             "vastaava-tj-included"
                                             "link-to-settings-no-label"
                                             "vv-tj"
                                             "upload"
                                             "statements"
                                             "erityis-tj"
                                             "add-condition"
                                             "paloluokka"]
                                :settings   {:verdict-code      ["evatty" "hyvaksytty" "myonnetty" "ehdollinen" "annettu-lausunto"]
                                             :foremen           ["vastaava-tj"]
                                             :date-deltas       {:julkipano       {:delta 2 :unit "days"}
                                                                 :anto            {:delta 0 :unit "days"}
                                                                 :muutoksenhaku   {:delta 1 :unit "days"}
                                                                 :lainvoimainen   {:delta 1 :unit "days"}
                                                                 :aloitettava     {:delta 1 :unit "years"}
                                                                 :voimassa        {:delta 1 :unit "years"}
                                                                 :k-aloitettava   {:delta 1 :unit "years"}
                                                                 :k-kuulutus      {:delta 1 :unit "days"}
                                                                 :k-lainvoimainen {:delta 1 :unit "days"}
                                                                 :k-muutoksenhaku {:delta 1 :unit "days"}
                                                                 :k-voimassa      {:delta 1 :unit "years"}}
                                             :plans             [{:fi   "Suunnitelmat"    :sv       "Planer" :en "Plans"
                                                                  :type "muu-suunnitelma" :selected true}
                                                                 {:fi   "ErityisSuunnitelmat" :sv       "SpecialPlaner" :en "SpecialPlans"
                                                                  :type "muu-suunnitelma"     :selected true}]
                                             :reviews           [{:fi   "Aloituskokous" :sv       "start" :en "start"
                                                                  :type "aloituskokous" :selected true}
                                                                 {:fi   "Loppukatselmus" :sv       "Loppu" :en "Loppu"
                                                                  :type "loppukatselmus" :selected true}
                                                                 {:fi   "Katselmus"     :sv       "Syn" :en "Review"
                                                                  :type "muu-katselmus" :selected true}]
                                             :organization-name "Sipoo Building Control"
                                             :boardname         "The Board"}}}
                {:id           "650d58aa13d3006be5aaa451"
                 :organization "753-R"
                 :name         (wrap "Päätöspohja")
                 :category     "p"
                 :modified     created
                 :deleted      (wrap false)
                 :draft        {:language      (wrap "fi")
                                :giver         (wrap "lautakunta")
                                :verdict-dates (wrap ["julkipano"])}
                 :published    {:published  1695626654436
                                :data       {:verdict-dates  ["julkipano"]
                                             :kuulutus-dates ["k-kuulutus"]
                                             :giver          "lautakunta"
                                             :title          "Test"
                                             :language       "fi"}
                                :inclusions ["add-condition"
                                             "appeal"
                                             "bulletinOpDescription"
                                             "conditions"
                                             "deviations"
                                             "giver"
                                             "language"
                                             "neighbors"
                                             "paatosteksti"
                                             "proposaltext"
                                             "purpose"
                                             "rights"
                                             "start-info"
                                             "statements"
                                             "subtitle"
                                             "title"
                                             "upload"
                                             "verdict-code"
                                             "verdict-dates"
                                             "kuulutus-dates"
                                             "verdict-giver-titles"
                                             "verdict-givers"]
                                :settings   {:boardname            "Lautakunta"
                                             :verdict-giver-titles []
                                             :handler-titles       []
                                             :date-deltas          {:julkipano       {:delta 1 :unit "days"}
                                                                    :anto            {:delta 12 :unit "days"}
                                                                    :muutoksenhaku   {:delta 13 :unit "days"}
                                                                    :lainvoimainen   {:delta 14 :unit "days"}
                                                                    :voimassa        {:delta 15 :unit "years"}
                                                                    :k-aloitettava   {:delta 1 :unit "years"}
                                                                    :k-kuulutus      {:delta 1 :unit "days"}
                                                                    :k-lainvoimainen {:delta 1 :unit "days"}
                                                                    :k-muutoksenhaku {:delta 1 :unit "days"}
                                                                    :k-voimassa      {:delta 1 :unit "years"}}
                                             :plans                []
                                             :organization-name    "Sipoo P"
                                             :verdict-code         ["evatty" "puollettu" "hyvaksytty"]
                                             :verdict-givers       []
                                             :reviews              []}}}]))

(def settings-ya (sc/validate ps/PateSettingsByCategory
                              {:ya {:draft    {:julkipano                (wrap "2")
                                               :boardname                (wrap "Lautakunta")
                                               :handler-titles           {}
                                               :muutoksenhaku            (wrap "12")
                                               :anto                     (wrap "6")
                                               :plans                    {}
                                               :aloitettava              (wrap "30")
                                               :organization-name        (wrap "Sipoo")
                                               :verdict-code             (wrap ["hyvaksytty" "puollettu" "myonnetty" "evatty"])
                                               :lainvoimainen            (wrap "20")
                                               :lautakunta-muutoksenhaku (wrap "15")}
                                    :modified created}}))

(def verdict-templates-ya
  (sc/validate [ps/PateSavedTemplate]
               [{:id        "6131f1db58f49f0bc57493e0"
                 :organization "753-YA"
                 :name      (wrap "Päätöspohja")
                 :category  "ya"
                 :modified  created
                 :deleted   (wrap false)
                 :draft     {:language      (wrap "fi")
                             :verdict-dates (wrap ["lainvoimainen" "muutoksenhaku"])
                             :giver         (wrap "viranhaltija")}
                 :published {:published  created
                             :data       {:verdict-dates ["lainvoimainen" "muutoksenhaku"]
                                          :giver         "viranhaltija"
                                          :language      "fi"}
                             :inclusions ["add-condition"
                                          "appeal"
                                          "bulletinOpDescription"
                                          "conditions"
                                          "giver"
                                          "handler-titles"
                                          "inform-others"
                                          "language"
                                          "paatosteksti"
                                          "plans"
                                          "proposaltext"
                                          "review-info"
                                          "reviews"
                                          "statements"
                                          "subtitle"
                                          "title"
                                          "upload"
                                          "verdict-code"
                                          "verdict-dates"]
                             :settings   {:verdict-code      ["hyvaksytty" "puollettu" "myonnetty" "evatty" "annettu-lausunto"]
                                          :organization-name "Sipoo"
                                          :date-deltas       {:julkipano     {:delta 2 :unit "days"}
                                                              :anto          {:delta 6 :unit "days"}
                                                              :muutoksenhaku {:delta 12 :unit "days"}
                                                              :lainvoimainen {:delta 20 :unit "days"}
                                                              :aloitettava   {:delta 30 :unit "years"}}
                                          :plans             []
                                          :reviews           []
                                          :handler-titles    []}}}]))

(defn update-sipoo [org]
  (case (:id org)
    "753-R" (assoc org
                   :verdict-settings settings-r
                   :scope (mapv (fn [sco] (assoc-in sco [:pate :enabled] true)) (:scope org)))
    "753-YA" (assoc org
                    :verdict-settings settings-ya
                    :scope (mapv (fn [sco] (assoc-in sco [:pate :enabled] true)) (:scope org)))
    org))

(defn organizations
  [opts]
  (->> (minimal/organizations opts)
       (filter (comp (set (mapcat (comp keys :orgAuthz) users)) keyword :id))
       (mapv update-sipoo)))

(defmethod fixture/fixture :pate-verdict
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (organizations {:http-port (components/system->http-port system)}))
  (mongo/insert-batch :verdict-templates (concat verdict-templates-r verdict-templates-ya)))

(defmethod fixture/users :pate-verdict
  [_]
  users)
