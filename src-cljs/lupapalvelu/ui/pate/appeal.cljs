(ns lupapalvelu.ui.pate.appeal
  "ClojureScipt version of the old appeal mechanism."
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.attachments :as att]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(rum/defcs appeal-buttons < rum/reactive
  (rum/local false ::wait?)
  [{wait?* ::wait?} data* filedatas* close-fn]
  [:div (components/icon-button {:icon     :lupicon-save
                                 :class    :positive
                                 :text-loc :save
                                 :wait?    wait?*
                                 :enabled? (boolean (and
                                                     (or (seq (rum/react filedatas*))
                                                         (seq (path/react [:files] data*)))
                                                     (every? :filled? (rum/react filedatas*))
                                                     (path/react [:type] data*)
                                                     (ss/not-blank? (path/react [:author] data*))
                                                     (ss/not-blank? (path/react [:date] data*))))
                                 :on-click #(let [{:keys [id type author date text
                                                          deleted-file-ids]} @data*]
                                              (service/upsert-appeal @state/application-id
                                                                     @state/current-verdict-id
                                                                     {:appeal-id        id
                                                                      :type             type
                                                                      :author           (ss/trim author)
                                                                      ;; Appeal timestamps are in seconds
                                                                      :datestamp        (.unix (js/util.toMoment date "fi"))
                                                                      :text             (ss/trim text)
                                                                      :filedatas        (service/canonize-filedatas @filedatas*)
                                                                      :deleted-file-ids deleted-file-ids}
                                                                     wait?*
                                                                     close-fn))})
   (components/icon-button {:icon     :lupicon-remove
                            :class    :secondary.pate-left-space
                            :text-loc :cancel
                            :on-click close-fn})])

(defn file-cell [{:keys [contentType filename fileId size]}]
  [:div
   [:a {:href   (js/sprintf "/api/raw/download-attachment?view=true&file-id=%s&id=%s"
                            fileId @state/application-id)
        :target "_blank"} filename]
   [:span.pate-left-space (if (js/loc.hasTerm contentType)
      (common/loc contentType)
      contentType)]
   [:span.pate-left-space (js/util.sizeString size)]])

(rum/defc old-file-list < rum/reactive
  [data*]
  (when-let [old-files (some-> data* rum/react :files seq)]
    [:table.pate-appeals.form-files
     [:tbody
      (map-indexed (fn [i {file-id :fileId :as file}]
                     [:tr.pate-appeal-row
                      {::key  (str "file-" i)
                       :class (common/css-flags :odd (odd? i) :even (even? i))}
                      [:td (file-cell file)]
                      [:td [:i.primary.lupicon-remove
                            {:on-click (fn [_]
                                         (swap! data* update :deleted-file-ids conj file-id)
                                         (swap! data* update :files
                                                (util/fn->> (remove #(= (:fileId %) file-id)))))}]]])
                   old-files)]]))

(rum/defc appeal-form < rum/reactive
  [{appeal-id :id :as data} close-fn]
  (let [data*      (atom data)
        uid        (common/unique-id "appeal")
        filedatas* (atom [])]
    [:div.container {:class (common/css-flags :pate-appeal-border (nil? appeal-id))}
     [:div {:id uid}
      (lupapalvelu.ui.attachment.components/dropzone)
      [:div.pate-grid-4
       (when-not appeal-id
         [:div.row.row--tight
          [:div.col-4 (common/loc :verdict.muutoksenhaku.info)]])
       [:div.row
        (layout/vertical {:label     :verdict.muutoksenhaku.tyyppi
                          :label-for (str uid "-type")
                          :required? (not appeal-id)
                          :align     :full}
                         (if appeal-id
                           [:span.formatted (path/loc :verdict.muutoksenhaku (path/value :type data*))]
                           (components/dropdown (rum/cursor data* :type)
                                                {:items     (map (fn [t]
                                                                   {:value t
                                                                    :text  (path/loc :verdict.muutoksenhaku t)})
                                                                 (cond-> [:appeal :rectification]
                                                                   (some (util/fn-> :type (util/not=as-kw :appealVerdict))
                                                                         @state/appeals) (conj :appealVerdict)))
                                                 :id        (str uid "-type")
                                                 :sort-by   :text
                                                 :required? true})))
        (layout/vertical {:label     :verdict.muutoksenhaku.tekijat
                          :col       2
                          :label-for (str uid "-author")
                          :required? true
                          :align     :full}
                         (components/text-edit (rum/cursor data* :author)
                                               {:immediate? true
                                                :required?  true
                                                :id         (str uid "-author")}))
        (layout/vertical {:label     :verdict.muutoksenhaku.pvm
                          :col       1
                          :label-for (str uid "-date")
                          :required? true
                          :align     :full}
                         (components/date-edit  (rum/cursor data* :date)
                                                {:required true
                                                 :id       (str uid "-date")}))]
       [:div.row
        (layout/vertical {:col       4
                          :align     :full
                          :label     :verdict.muutoksenhaku.extra
                          :label-for (str uid "-text")}
                         (components/textarea-edit (rum/cursor data* :text)
                                                   {:id (str uid "-text")}))]
       [:div.row
        [:div.col-4
         (old-file-list data*)]]
       [:div.row
        [:div.col-4
         [:label.pate-label.required (common/loc :verdict.muutoksenhaku.liitteet)]
         [:span.pate-left-space (common/loc :verdict.muutoksenhaku.liitteet.info)]]]

       [:div.row
        [:div.col-4
         (att/batch-upload-files  filedatas* { :bind?    false
                                              :dropzone  (str "#" uid)
                                              :multiple? true})]]
       [:div.row
        [:div.col-4
         (appeal-buttons data* filedatas* close-fn)]]]]]))

(rum/defcs appeal-list < rum/reactive
  (rum/local {} ::opened)
  [{opened* ::opened}]
  (when-let [appeals (->> (rum/react state/appeals)
                          (sort-by :datestamp)
                          not-empty)]
    (let [app-id     @state/application-id
          verdict-id @state/current-verdict-id]
      [:div
       [:h3.pate-published-title (common/loc :verdict.muutoksenhaku)]
       [:table.pate-appeals
        [:thead
         [:tr.appeals--header
          [:th (common/loc :verdict.muutoksenhaku.tyyppi)]
          [:th (common/loc :verdict.muutoksenhaku.tekijat)]
          [:th (common/loc :verdict.muutoksenhaku.pvm)]
          [:th (common/loc :verdict.attachments)]
          [:th]]]
        [:tbody
         (map-indexed (fn [i {:keys [author editable
                                     datestamp text files type id]
                              :as   appeal}]
                        (let [date        (common/format-timestamp (* 1000 datestamp))
                              can-edit?   (and editable (state/verdict-auth? verdict-id
                                                                             :upsert-pate-appeal))
                              can-delete? (and editable (state/verdict-auth? verdict-id
                                                                             :delete-pate-appeal))
                              open?*      (rum/cursor opened* id)
                              toggle-fn   #(swap! open?* not)]
                          (list [:tr.pate-appeal-row {:class (common/css-flags :odd (odd? i)
                                                                               :even (even? i))
                                                      :key   (str "appeal-" i)}
                                 [:td (path/loc :verdict.muutoksenhaku type)]
                                 [:td.align--half author]
                                 [:td.align--right date]
                                 [:td.align--half (map file-cell files)]
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
                                     (appeal-form (assoc appeal :date date) toggle-fn)
                                     [:div.pate-appeal-note
                                      (if (ss/blank? text)
                                        [:span.empty (common/loc :verdict.muutoksenhaku.no-extra)]
                                        text)])]]))))
                      appeals)]]])))

(rum/defcs new-appeal < rum/reactive
  (rum/local false ::open?)
  [{open?* ::open?} _]
  (let [toggle-fn #(swap! open?* not)]
    (if (rum/react open?*)
      (appeal-form {} toggle-fn)
      (components/icon-button {:icon     :lupicon-circle-plus
                               :class    :positive
                               :text-loc :verdict.muutoksenhaku.kirjaa
                               :on-click toggle-fn}))))

(rum/defc appeals < rum/reactive
  []
  ;; TODO: use verdict auths
  (when (path/react [:tags] state/current-verdict)
    [:div
     (appeal-list)
     (when (state/verdict-auth? @state/current-verdict-id :upsert-pate-appeal)
       (new-appeal))]))
