(ns lupapalvelu.ui.pate.appeal
  "ClojureScipt version of the old appeal mechanism."
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.attachments :as att]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(rum/defcs appeal-form < rum/reactive
  (components/initial-value-mixin ::data)
  [{data* ::data} _ close-fn]
  [:div "hello"])

(defn file-cells [{:keys [contentType filename fileId size]}]
  (list [:td
         [:a {:href   (js/sprintf "/api/raw/download-attachment?view=true&file-id=%s&id=%s"
                                  fileId @state/application-id)
              :target "_blank"} filename]]
        [:td (if (js/loc.hasTerm contentType)
               (common/loc contentType)
               contentType)]
        [:td.align--right (js/util.sizeString size)]))

(rum/defcs appeal-list < rum/reactive
  (rum/local {} ::opened)
  [{opened* ::opened}]
  (when-let [appeals (->> (rum/react state/appeals)
                          (sort-by :datestamp))]
    (let [app-id     @state/application-id
          verdict-id @state/current-verdict-id]
      [:div
       [:h3 (common/loc :verdict.muutoksenhaku)]
       [:table.pate-appeals
        [:thead
         [:tr.appeals--header
          [:th (common/loc :verdict.muutoksenhaku.tyyppi)]
          [:th (common/loc :verdict.muutoksenhaku.tekijat)]
          [:th (common/loc :verdict.muutoksenhaku.pvm)]
          [:th (common/loc :verdict.attachments)]
          [:th] ;; File type
          [:th] ;; File size
          [:th]]]
        [:tbody
         (map-indexed (fn [i {:keys [appellant giver editable
                                     datestamp text files type id]
                              :as   appeal}]
                        (let [can-edit?   (and editable (state/auth? :upsert-pate-appeal))
                              can-delete? (and editable (state/auth? :delete-pate-appeal))
                              open?*      (rum/cursor opened* id)
                              toggle-fn   #(swap! open?* not)]
                          (list [:tr.pate-appeal-row {:class (common/css-flags :odd (odd? i)
                                                                               :even (even? i))
                                                      :key   (str "appeal-" i)}
                                 [:td (common/loc :verdict.muutoksenhaku type)]
                                 [:td (or giver appellant)]
                                 [:td.align--right (common/format-timestamp (* 1000 datestamp))]
                                 (map file-cells files)
                                 [:td.align--right
                                  [:a.pate-appeal-operation {:on-click toggle-fn}
                                   (common/loc (if can-edit? :edit :verdict.muutoksenhaku.show-extra))]
                                  (when can-delete?
                                    [:a.pate-appeal-operation
                                     {:on-click #(service/delete-appeal app-id verdict-id id)}
                                     (common/loc :remove)])]]
                                (when (rum/react open?*)
                                  [:tr.pate-appeal-row.note-or-form {:key (str "extra-")}
                                   [:td  {:colSpan "7"}
                                   (if can-edit?
                                     (appeal-form appeal)
                                     [:td.pate-appeal-note
                                      (if (ss/blank? text)
                                        [:span.empty (common/loc :verdict.muutoksenhaku.no-extra)]
                                        text)])]]))))
                      appeals)]]
       (components/debug-atom opened* "Opened")])))

(rum/defc appeals < rum/reactive
  []
  ;; TODO: use verdict auths
  (when (path/react [:tags] state/current-verdict)
    [:div (appeal-list)
     (components/debug-atom state/appeals)]))
