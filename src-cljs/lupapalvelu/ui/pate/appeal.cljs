(ns lupapalvelu.ui.pate.appeal
  "ClojureScipt version of the old appeal mechanism."
  (:require [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.attachments :as att]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.components.datepicker :as datepicker]))

(rum/defcs appeal-buttons < rum/reactive
  (rum/local false ::wait?)
  [{wait?* ::wait?} data* derived-filedatas* close-fn]
  [:div (components/icon-button {:icon     :lupicon-save
                                 :class    :positive
                                 :test-id  :save-appeal
                                 :text-loc :save
                                 :wait?    wait?*
                                 :enabled? (boolean (and
                                                     (or (seq (rum/react derived-filedatas*))
                                                         (seq (path/react [:files] data*)))
                                                     (every? :filled? (rum/react derived-filedatas*))
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
                                                                      :datestamp        (.getTime ^js date)
                                                                      :text             (ss/trim text)
                                                                      :filedatas        (service/canonize-filedatas @derived-filedatas*)
                                                                      :deleted-file-ids deleted-file-ids}
                                                                     wait?*
                                                                     close-fn))})
   (components/icon-button {:icon     :lupicon-remove
                            :class    :secondary.pate-left-space
                            :text-loc :cancel
                            :test-id  :cancel-appeal
                            :wait?    wait?*
                            :on-click close-fn})])

(defn- file-cell [postfix {:keys [contentType filename fileId size]}]
  [:div {:key fileId}
   [:a (common/add-test-id {:href   (js/sprintf "/api/raw/download-attachment?view=true&file-id=%s&id=%s"
                                                fileId @state/application-id)
                            :target "_blank"}
                           :appeal-file postfix) filename]
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
                      [:td (file-cell i file)]
                      [:td (components/icon-button
                             (common/add-test-id {:class      :tertiary
                                                  :icon-only? true
                                                  :icon       :lupicon-remove
                                                  :text-loc   :attachment.delete
                                                  :on-click   (fn [_]
                                                                (swap! data* update :deleted-file-ids conj file-id)
                                                                (swap! data* update :files
                                                                       (util/fn->> (remove #(= (:fileId %) file-id)))))}
                                                 :delete-appeal-file i))]])
                   old-files)]]))

(rum/defcs appeal-form
  < rum/reactive
    (components/default-arg->local-atom ::data)
    (rum/local [] ::files)
  (rum/local {} ::fields)
  [{data* ::data files* ::files fields* ::fields} {appeal-id :id :as data} close-fn]
  (let [uid       "appeal-form"
        filedatas (rum-util/derived-atom
                    [files* fields*]
                    (fn [files fields]
                      (->> files
                           (filter #(util/=as-kw (:state %) :success))
                           (mapv (fn [{:keys [filename] :as file}]
                                   (let [{:keys [type contents]
                                          :as   field} (get fields (keyword filename))]
                                     (merge file
                                            field
                                            {:filled? (and type (ss/not-blank? contents))})))))))]
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
                           [:span.formatted (common/add-test-id {} :appeal-type)
                            (path/loc :verdict.muutoksenhaku (path/value :type data*))]
                           (components/dropdown
                             (path/react [:type] data*)
                             {:items     (map (fn [t]
                                                {:value t
                                                 :text  (path/loc :verdict.muutoksenhaku t)})
                                              (cond-> [:appeal :rectification]
                                                (some (util/fn-> :type (util/not=as-kw :appealVerdict))
                                                      @state/appeals) (conj :appealVerdict)))
                              :id        (str uid "-type")
                              :callback #(common/reset-if-needed! (rum/cursor-in data* [:type]) %)
                              :sort-by   :text
                              :test-id   :appeal-type
                              :required? true})))
        (layout/vertical {:label     :verdict.muutoksenhaku.tekijat
                          :col       2
                          :label-for (str uid "-author")
                          :required? true
                          :align     :full}
                         (components/text-edit @(rum/cursor data* :author)
                                                  {:required? true
                                                   :callback  #(swap! data* assoc :author %)
                                                   :test-id   :appeal-authors
                                                   :id        (str uid "-author")}))
        (layout/vertical {:label     :verdict.muutoksenhaku.pvm
                          :col       1
                          :label-for (str uid "-date")
                          :required? true
                          :align     :full}
                         (components/day-edit @(rum/cursor data* :date)
                                              {:callback  #(swap! data* assoc :date %)
                                               :required? true
                                               :test-id   :appeal-date
                                               :id        (str uid "-date")}))]
       [:div.row
        (layout/vertical {:col       4
                          :align     :full
                          :label     :verdict.muutoksenhaku.extra
                          :label-for (str uid "-text")}
                         (components/textarea-edit @(rum/cursor data* :text)
                                                      {:id       (str uid "-text")
                                                       :callback #(swap! data* assoc :text %)
                                                       :test-id  :appeal-text}))]
       [:div.row
        [:div.col-4
         (old-file-list data*)]]
       [:div.row
        [:div.col-4
         [:label.pate-label.required (common/loc :verdict.muutoksenhaku.liitteet)]
         [:span.pate-left-space (common/loc :verdict.muutoksenhaku.liitteet.info)]]]

       [:div.row
        [:div.col-4
         (att/batch-upload-files files* fields* {:bind?     false
                                                 :enabled?  true
                                                 :dropzone  (str "#" uid)
                                                 :multiple? true})]]
       [:div.row
        [:div.col-4
         (appeal-buttons data* filedatas close-fn)]]]]]))

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
          [:th (common/loc :admin.actions)]]]
        (into
          [:tbody]
          (for [[i {:keys [author editable
                           datestamp text files type id]
                    :as   appeal}] (map-indexed vector appeals)
                :let               [date        (new js/Date datestamp)
                                    can-edit?   (and editable (state/verdict-auth? verdict-id :upsert-pate-appeal))
                                    can-delete? (and editable (state/verdict-auth? verdict-id :delete-pate-appeal))
                                    open?*      (rum/cursor opened* id)
                                    toggle-fn   #(swap! open?* not)]]
            (rum/fragment
              [:tr.pate-appeal-row {:class (common/css-flags :odd (odd? i)
                                                             :even (even? i))
                                    :key   (str "appeal-" i)}
               [:td (common/add-test-id {} :appeal i type)
                (path/loc :verdict.muutoksenhaku type)]
               [:td.align--half (common/add-test-id {} :appeal i :authors)
                author]
               [:td.align--right (common/add-test-id {} :appeal i :date)
                (datepicker/->finnish-str date)]
               [:td.align--half (map-indexed (fn [file-i file]
                                               (file-cell [i file-i] file))
                                             files)]
               [:td.align--right
                (components/click-link {:click    toggle-fn
                                        :test-id  [:appeal i (if can-edit? :edit :show)]
                                        :text-loc (if can-edit?
                                                    :edit
                                                    :verdict.muutoksenhaku.show-extra)})
                (when can-delete?
                  (components/click-link {:click    #(service/delete-appeal app-id verdict-id id)
                                          :test-id  [:appeal i :delete]
                                          :class    :gap--l1
                                          :text-loc :remove}))]]
              (when (rum/react open?*)
                [:tr.pate-appeal-row.note-or-form {:key (str "extra-" i)}
                 [:td {:colSpan "7"}
                  (if can-edit?
                    (appeal-form (assoc appeal :date date) toggle-fn)
                    [:div.pate-appeal-note
                     (common/add-test-id {} :appeal i :note)
                     (if (ss/blank? text)
                       [:span.empty (common/add-test-id {} :appeal i :empty)
                        (common/loc :verdict.muutoksenhaku.no-extra)]
                       text)])]]))))]])))

(rum/defcs new-appeal < (rum/local false ::open?)
  [{open?* ::open?}]
  (let [toggle-fn #(swap! open?* not)]
    (if @open?*
      (appeal-form {} toggle-fn)
      (components/icon-button {:icon     :lupicon-circle-plus
                               :class    :positive
                               :test-id  :new-appeal
                               :text-loc :verdict.muutoksenhaku.kirjaa
                               :on-click toggle-fn}))))

(rum/defc appeals < rum/reactive
  []
  (when (path/react [:tags] state/current-verdict)
    [:div
     (appeal-list)
     (when (state/verdict-auth? @state/current-verdict-id :upsert-pate-appeal)
       (new-appeal))]))
