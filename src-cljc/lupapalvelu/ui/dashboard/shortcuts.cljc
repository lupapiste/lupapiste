(ns lupapalvelu.ui.dashboard.shortcuts
  (:require [lupapalvelu.ui.dashboard.queries :as queries]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]))

;; TODO: Name/labels using localization?
;; TODO: Cljc widget type declarations (numeric, numbers)
;; somewhere, with per type schema, use to create schemas here.

(def BaseShortcutWidgetModel
  [:map
   ;; TODO: Build the enum vals from widget declarations
   [:type [:enum :numbers :numeric]]
   ;; TODO: Consider if these should be ints or how do define these?
   [:width [:enum "1" "2" "4"]]
   [:height [:enum "1" "2" "4"]]])

(def WidgetModel
  [:multi
   {:dispatch :type}
   [:numeric
    (mu/assoc BaseShortcutWidgetModel
              :options [:map
                        [:name :string]
                        [:query queries/SearchModel]])]
   [:numbers
    (mu/assoc BaseShortcutWidgetModel
              :options [:map
                        [:name :string]
                        [:control {:optional true}
                         [:map
                          [:type [:= :toggle-group]]
                          [:options [:vector [:map
                                              [:title :string]
                                              [:value :any]]]]
                          [:filter (:filter-schema (:search/applications queries/type->query))]]]
                        [:queries [:vector queries/SearchModel]]])]])

(def WidgetModels
  [:map-of :keyword WidgetModel])

(def bim-hankkeet
  {:type :numbers
   :width "2"
   :height "2"
   :options {:name "BIM-hankkeet"
             ;; FIXME: Move label out of query data
             ;; TODO: Labels as loc keys for shortcuts,
             ;; apply localization if user makes a copy of the widget to edit themselves?
             :queries [{:query/id 0
                        :query/type :search/applications
                        :label "Tietomallin sisältävät hakemukset"
                        ;; Filters are combined with AND
                        :query/filters [;; "Käyttäjällä on oikeus" is implictly always applied?
                                        ;; Ifc-tiedostoja
                                        {:filter/type :has-ifc-models
                                         :filter/value true}
                                        ;; Hakemus on jätetty
                                        {:filter/type :application-submitted
                                         :filter/value true}
                                        ;; Lupapäätöstä ei ole tehty
                                        {:filter/type :decision-given
                                         :filter/value false}]
                        :query/results {:id :applications/count}}
                       {:query/id 1
                        :query/type :search/applications
                        :label "Tietomallimuodossa haettavat luvat"
                        :query/filters [{:filter/type :has-ifc-models
                                         :filter/value true}
                                        {:filter/type :application-submitted
                                         :filter/value true}
                                        ;; tietomallivälilehdellä on valittu hakutavaksi “Rakennuksen tietomallilla”
                                        {:filter/type :models-data-source
                                         :filter/value :bim}
                                        {:filter/type :decision-given
                                         :filter/value false}]
                        :query/results {:id :applications/count}}
                       {:query/id 2
                        :query/type :search/applications
                        :label "Tietomallimuodossa myönnetyt luvat"
                        :query/filters [{:filter/type :has-ifc-models
                                         :filter/value true}
                                        {:filter/type :application-submitted
                                         :filter/value true}
                                        {:filter/type :models-data-source
                                         :filter/value :bim}
                                        {:filter/type :decision-given
                                         :filter/value true}]
                        :query/results {:id :applications/count}}
                       {:query/id 3
                        :query/type :search/applications
                        :label "Tietomallin sisältävät luvat"
                        :query/filters [{:filter/type :has-ifc-models
                                         :filter/value true}
                                        ;; Lupapäätös on tehty
                                        {:filter/type :decision-given
                                         :filter/value true}
                                        ;; hanke ei valmis
                                        {:filter/type :application-ready
                                         :filter/value false}]
                        :query/results {:id :applications/count}}
                       {:query/id 4
                        :query/type :search/applications
                        :label "Omat tietomallin sisältävät hakemukset"
                        :query/filters [;; Käyttäjä on jossain käsittelijäroolisa
                                        {:filter/type :user-has-role
                                         :filter/value true}
                                        {:filter/type :has-ifc-models
                                         :filter/value true}
                                        {:filter/type :application-submitted
                                         :filter/value true}]
                        :query/results {:id :applications/count}}
                       {:query/id 5
                        :query/type :search/applications
                        :label "Omat tietomallin sisältävät luvat"
                        :query/filters [{:filter/type :user-has-role
                                         :filter/value true}
                                        {:filter/type :has-ifc-models
                                         :filter/value true}
                                        {:filter/type :decision-given
                                         :filter/value true}
                                        {:filter/type :application-submitted
                                         :filter/value true}
                                        {:filter/type :application-ready
                                         :filter/value false}]
                        :query/results {:id :applications/count}}]
             ;; TODO: Declare the detail panel columns, filters, sorting etc.?
             }})

(def ifc-toimitukset
  {:type :numbers
   :width "2"
   :height "2"
   :options {:name "IFC-toimitukset"
             :control {:type :toggle-group
                       :options [{:title "1 vrk"
                                  :value 1}
                                 {:title "7 vrk"
                                  :value 7}
                                 {:title "30 vrk"
                                  :value 30}
                                 {:title "365 vrk"
                                  :value 365}]
                       :filter {:filter/type :ifc-upload-date
                                ;; ylöslatauksen aika valitun aikajakson sisällä
                                :filter/value {:start :control-value
                                               :end :today}}}
             :queries [{:query/id 0
                        :query/type :search/applications
                        :label "Toimitetut IFC-tiedostot"
                        :query/filters [{:filter/type :ui/control-filter}]
                        :query/results {:id :ifc-models/count}}
                       {:query/id 1
                        :query/type :search/applications
                        :label "Hakemusvaiheen mallit"
                        :query/filters [{:filter/type :ui/control-filter}
                                        {:filter/type :model-delivered
                                         ;; malli on toimitettu ennen luvan myöntämistä
                                         :filter/value :before-decision}]
                        :query/results {:id :ifc-models/count}}
                       {:query/id 2
                        :query/type :search/applications
                        :label "Rakentamisen aikaiset ja toteumamallit"
                        :query/filters [{:filter/type :ui/control-filter}
                                        {:filter/type :model-delivered
                                         ;; malli on toimitettu luvan myöntämisen jälkeen
                                         :filter/value :after-decision}]
                        :query/results {:id :ifc-models/count}}
                       {:query/id 3
                        :query/type :search/applications
                        :label "Omat toimitetut IFC-tiedostot"
                        :query/filters [{:filter/type :ui/control-filter}
                                        {:filter/type :user-has-role
                                         :filter/value true}]
                        :query/results {:id :ifc-models/count}}
                       {:query/id 4
                        :query/type :search/applications
                        :label "Omat hakemusvaiheen mallit"
                        :query/filters [{:filter/type :ui/control-filter}
                                        {:filter/type :user-has-role
                                         :filter/value true}
                                        {:filter/type :model-delivered
                                         ;; malli on toimitettu ennen luvan myöntämistä
                                         :filter/value :before-decision}]
                        :query/results {:id :ifc-models/count}}
                       {:query/id 5
                        :query/type :search/applications
                        :label "Omat rakentamisen aikaiset ja toteumamallit"
                        :query/filters [{:filter/type :ui/control-filter}
                                        {:filter/type :user-has-role
                                         :filter/value true}
                                        {:filter/type :model-delivered
                                         ;; malli on toimitettu luvan myöntämisen jälkeen
                                         :filter/value :after-decision}]
                        :query/results {:id :ifc-models/count}}]}})

(def cards
  {:bim-hankkeet bim-hankkeet
   :ifc-toimitukset ifc-toimitukset})

(when-not (m/validate WidgetModels cards)
  (throw (ex-info "Invalid shortcut cards data"
                  (me/humanize (m/explain WidgetModels cards)))))
