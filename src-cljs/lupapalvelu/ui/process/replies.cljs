(ns lupapalvelu.ui.process.replies
  (:require
   [lupapalvelu.next.event :refer [<sub >evt]]
   [lupapalvelu.ui.common :as common]
   [lupapalvelu.ui.components :as components]
   [lupapalvelu.ui.components.input :as ui-input]
   [lupapalvelu.ui.components.datepicker :as datepicker]
   [lupapalvelu.ui.components.day-edit :as day-edit]
   [re-frame.core :as rf]
   [sade.strings :as ss]
   [sade.util :as util]))

(rf/reg-event-db ::clear-temp-reply
  (fn [db _]
    (assoc-in db [::temp-reply] {})))

(rf/reg-event-fx ::create-reply
  (fn [_ [_]]
    {:dialog/show {:type  :yes-no
                   :text  (common/loc :replies.confirm.create-reply)
                   :event [::create-reply.confirm]}}))

(rf/reg-event-fx ::create-reply.confirm
  (fn [{db :db} [_]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :create-reply
                        :params  {:id    app-id
                                  :reply (::temp-reply db)}
                        :success [::create-reply.success]}})))

(rf/reg-event-db ::create-reply.success
  (fn [db [_ {reply :reply}]]
    (-> db
        (assoc-in [::temp-reply] {})
        (assoc-in [::replies (keyword (:id reply))] reply))))

(rf/reg-event-fx ::delete-reply
  (fn [_ [_ id]]
    {:dialog/show {:type  :yes-no
                   :text  (common/loc :replies.confirm.deletion)
                   :event [::delete-reply-confirmed id]}}))

(rf/reg-event-fx ::delete-reply-confirmed
  (fn [_ [_ id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :delete-reply
                        :params  {:id app-id :replyId id}
                        :success [::fetch-replies]}})))

(rf/reg-event-fx ::answer-reply
  (fn [_ [_ id]]
    {:dialog/show {:type  :yes-no
                   :text  (common/loc :replies.confirm.answer-reply)
                   :event [::answer-reply.confirm id]}}))

(defn- process-answer
  "Removes answer message if (:nothing-to-add answer) is true since that makes the message obsolete."
  [answer]
  (if (:nothing-to-add answer)
    (dissoc answer :message)
    (update answer :message ss/trim)))

(rf/reg-event-fx ::answer-reply.confirm
  (fn [{db :db} [_ id]]
    (let [reply  (-> db ::replies (get (keyword id)))
          answer (-> reply :answer process-answer)]
      {:action/command {:name    :answer-reply
                        :params  {:id      (common/application-id)
                                  :replyId (:id reply)
                                  :answer  answer}
                        :success [::fetch-replies]}})))

(rf/reg-event-db ::update-temp-reply-message
  (fn [db [_ message]]
    (assoc-in db [::temp-reply :message] message)))

(rf/reg-event-db ::update-temp-reply-related-issue
  (fn [db [_ id issue-type checked]]
    (update-in db [::temp-reply :related-issues] (if checked conj disj) {:id id :issue-type issue-type})))

(rf/reg-event-fx ::fetch-replies
  (fn [{db :db} _]
    {:db           (assoc db ::replies :loading)
     :action/query {:name    :replies
                    :params  {:id (common/application-id)}
                    :success [::fetch-replies.success]}}))

(rf/reg-event-db ::fetch-replies.success
  (fn [db [_ {:keys [replies]}]]
    (assoc db ::replies (update-vals replies #(assoc %
                                                     :expanded false
                                                     :show-full-message false
                                                     :show-answer-textbox false)))))

(rf/reg-event-fx ::fetch-replies-targets
  (fn [{db :db} _]
    {:db           (assoc db ::replies-targets :loading)
     :action/query {:name    :get-reply-target-issues
                    :params  {:id (common/application-id)}
                    :success [::fetch-replies-targets.success]}}))

(rf/reg-event-db ::fetch-replies-targets.success
  (fn [db [_ {:keys [replies-targets]}]]
    (assoc db ::replies-targets replies-targets)))

(rf/reg-event-db ::toggle-reply-attribute
  (fn [db [_ id attribute]]
    (update-in db [::replies (keyword id) attribute] not)))

(rf/reg-event-db ::toggle-reply-answer-attribute
  (fn [db [_ id attribute]]
    (update-in db [::replies (keyword id) :answer attribute] not)))

(rf/reg-event-db ::create-answer
  (fn [db [_ id]]
    (assoc-in db [::replies (keyword id) :answer] {:nothing-to-add false
                                                   :message        ""})))

(rf/reg-event-db ::update-reply-answer
  (fn [db [_ id value]]
    (assoc-in db [::replies (keyword id) :answer :message] value)))

(rf/reg-event-fx ::open-issue-stats-modal
  (fn [_ [_ issue]]
    {:dialog/show issue}))

(rf/reg-event-db ::create-new-reply
  (fn [db]
    (let [issue          (get-in db [:route/match :parameters :path])
          related-issues (if (and (:issue-type issue) (:id issue))
                           #{{:id (:id issue) :issue-type (-> issue :issue-type keyword)}}
                           #{})]
      (assoc-in db [::temp-reply] {:id             :new
                                   :application-id (common/application-id)
                                   :related-issues related-issues
                                   :message        ""
                                   :reply-due-date nil}))))

(rf/reg-sub ::replies-targets
  :-> ::replies-targets)

(rf/reg-sub ::replies
  :-> ::replies)

(rf/reg-sub ::temp-reply
  :-> ::temp-reply)

(rf/reg-sub ::selected-reply
  :-> ::selected-reply)

(defn file-link [{:keys [filename href]}]
  [:div
   [:a {:target "_blank"
        :href  href}
    filename]])

(defn reply-issue-modal
  [{:keys [statement-description header issue-type display-name created message files neighbor neighbor-address statement-info]}]
  [:div.content
   [:h4 header]
   [:table
    [:tbody
     [:tr
      [:td (common/loc (util/kw-path :replies issue-type "name"))]
      [:td display-name]]
     (when (= issue-type :neighbor)
       [:<>
        [:tr
         [:td (common/loc :replies.neighbor)]
         [:td neighbor]]
        [:tr
         [:td (common/loc :replies.neighbor.address)]
         [:td neighbor-address]]])
     (when (= issue-type :statement)
       [:<>
        [:tr
         [:td (common/loc :replies.statement.description)]
         [:td statement-description]]
        [:tr
         [:td (common/loc :statement.title)]
         [:td statement-info]]])
     [:tr
      [:td (common/loc :time)]
      [:td (js/util.finnishDate created)]]]]
   (when message
     [:div
      [:h4
       (common/loc :replies.message)]
      [:p.whitespace-pre-line message]])
   (when (seq files)
     [:div
      [:h4 (common/loc :replies.attachments)]
      (doall (for [file files]
               ^{:key (:filename file)} [file-link file]))])
   [:div.button-group.ok
    [:button.positive.btn-dialog {:on-click #(>evt [:dialog/close])}
     (common/loc :close)]]])

(defn- open-neighbor-issue [issue]
  (let [{:keys [state userid created message]} (last (:status issue))
        prefix :neighbors.status-dialog
        data {:header           (common/loc (util/kw-path prefix "message" state))
              :description      (common/loc (util/kw-path prefix "description" state))
              :display-name     (:display-name issue)
              :neighbor         (-> issue :owner :name)
              :neighbor-address (-> issue :owner :address :street)
              :user-id          userid
              :created          created
              :issue-type       (keyword (:issue-type issue))
              :message          message}]
    {:type             :react
     :ltitle           :neighbors.status-dialog.title
     :size             (if (> (count message) 600) :large :medium)
     :dialog-component [reply-issue-modal data]}))

(defn- open-statement-issue [issue]
  (let [{:keys [text given status files person issue-type display-name]} issue
        data {:statement-info        (common/loc (util/kw-path :statement status))
              :display-name          display-name
              :created               given
              :header                (str (common/loc :replies.statement.giver) ":")
              :issue-type            (keyword issue-type)
              :statement-description (:text person)
              :message               text
              :files                 (doall
                                      (map (fn [file] {:href (str "/api/raw/latest-attachment-version?attachment-id=" (:id file))
                                                       :filename (-> file :latestVersion :filename)}) files))}]
    {:type             :react
     :ltitle           :replies.statement
     :size             (if (> (count text) 600) :large :medium)
     :dialog-component [reply-issue-modal data]}))

(defn- open-bulletin-issue [issue]
  (let [{:keys [created attachments issue-type] comment-issue :comment} issue
        data {:header       (str (common/loc :replies.bulletin.giver) ":")
              :display-name (:display-name issue)
              :created      created
              :issue-type   (keyword issue-type)
              :message      comment-issue
              :files        (doall
                             (map (fn [attachment]
                                    {:href (str "/api/raw/download-bulletin-comment-attachment?attachmentId=" (:fileId attachment))
                                     :filename (-> attachment :filename)}) attachments))}]
    {:type             :react
     :ltitle           :replies.bulletin
     :size             (if (> (count comment-issue) 600) :large :medium)
     :dialog-component [reply-issue-modal data]}))

(defn open-issue-modal
  "Formats the different types of issues opens up the issue stats modal"
  [issue]
  (case (keyword (:issue-type issue))
    :neighbor  (open-neighbor-issue issue)
    :statement (open-statement-issue issue)
    :bulletin  (open-bulletin-issue issue)))

(defn- create-reply-issue-link
  [{:keys [id date checked issue-name issue-type] :as issue}]
  [:span {:key id}
   (ui-input/checkbox {:checked?  checked
                       :on-change #(>evt [::update-temp-reply-related-issue id issue-type %])})
   [:a {:on-click #(>evt [::open-issue-stats-modal (open-issue-modal issue)])}
    (str issue-name " - " (js/util.finnishDate date))]])

(defn- render-create-reply-issues [targets]
  (let [{:keys [statements neighbors bulletins]} targets
        {:keys [related-issues]}                 (<sub [::temp-reply])]
    [:div.flex.flex-col
     (when (or (seq bulletins)
               (seq neighbors)
               (seq statements))
       [:<>
        [:h3 (common/loc :replies.new.pick-related-issues)]
        (when (seq statements)
          [:div.statements.mb-6.flex.flex-col
           [:h4 (common/loc :replies.statements)]

           (->> statements
                (map (fn [{:keys [id person given] :as issue}]
                       (create-reply-issue-link (assoc issue
                                                       :issue-type :statement
                                                       :issue-name (str (:text person) " - " (:name person))
                                                       :date given
                                                       :checked (contains? related-issues {:id id :issue-type :statement})))))
                doall)])
        (when (seq bulletins)
          [:div.bulletins.mb-6.flex.flex-col
           [:h4 (common/loc :replies.bulletins)]
           (->> bulletins
                (map (fn [{:keys [id created display-name] :as issue}]
                       (create-reply-issue-link (assoc issue
                                                       :issue-type :bulletin
                                                       :issue-name display-name
                                                       :date created
                                                       :checked (contains? related-issues {:id id :issue-type :bulletin})))))
                doall)])
        (when (seq neighbors)
          [:div.neighbors.mb-6.flex.flex-col
           [:h4 (common/loc :replies.neighbors)]
           (->> neighbors
                (map (fn [{:keys [id status display-name] :as issue}]
                       (let [given-comment (->> status (filter #(-> % :state (= "response-given-comments"))) first)]
                         (create-reply-issue-link (assoc issue
                                                         :issue-type :neighbor
                                                         :issue-name (str (-> issue :owner :address :street) " - " display-name)
                                                         :date (:created given-comment)
                                                         :checked  (contains? related-issues {:id id :issue-type :neighbor}))))))
                doall)])])]))

(defn- create-new-reply-view []
  (let [{:keys [reply-due-date message related-issues]} (<sub [::temp-reply])
        replies-targets                                 (<sub [::replies-targets])
        due-date-in-past?                               (< reply-due-date (js/Date.))]
    [:div.p-1
     [:h2.mt-2.mb-4 (common/loc :replies.new.create-reply)]
     (render-create-reply-issues replies-targets)
     [:div.message.mb-6
      [:h4 (common/loc :replies.new.message)]
      [:textarea {:cols      80
                  :rows      10
                  :value     message
                  :on-change #(>evt [::update-temp-reply-message (util/tval %)])}]]
     [:div.mb-6
      [:h4 (common/loc :replies.reply.due-date)]
      [day-edit/day-edit reply-due-date {:id        "reply-due-date"
                                         :callback  #(>evt [:value/set ::temp-reply :reply-due-date (datepicker/day-begin-ts %)])
                                         :invalid?  (and (some? reply-due-date)
                                                         due-date-in-past?)
                                         :required? true}]]
     (components/icon-button {:class     :primary.gap--r1
                              :on-click  #(>evt [::create-reply])
                              :icon      :lupicon-save
                              :disabled? (or due-date-in-past?
                                             (empty? related-issues)
                                             (empty? message))
                              :text-loc  :save})
     (components/icon-button {:class    :secondary
                              :on-click #(>evt [::clear-temp-reply])
                              :icon     :lupicon-remove
                              :text-loc :cancel})]))

(defn- reply-trunc-message [{:keys [message show-full-message id]}]
  (let [limit 600]
    [:div.pb-4
     [:h4 (common/loc :replies.related-message)]
     [:p.whitespace-pre-line
      (if (< (count message) limit)
        message
        (if show-full-message
          [:<> (str message " ")
           [:a
            {:on-click #(>evt [::toggle-reply-attribute id :show-full-message])}
            (common/loc :replies.hide-full-text)]]
          [:<> (str (ss/trim (subs message 0 limit)) "... ")
           [:a
            {:on-click #(>evt [::toggle-reply-attribute id :show-full-message])}
            (common/loc :replies.show.full-text)]]))]]))

(defn- display-given-answer [answer]
  [:div
   [:h4 (common/loc :replies.answer)]
   [:h5 (common/loc :replies.answer.answer-given (js/util.finnishDate (:created answer)))]
   (if (:nothing-to-add answer)
     [:div.flex
      (ui-input/checkbox {:id        "nothing-to-add"
                          :disabled? true
                          :checked?  true})
      [:label.ml-1 {:for "nothing-to-add"} [:h5 (common/loc :replies.answer.nothing-to-add.label)]]]
     [:p.whitespace-pre-line (:message answer)])])

(defn- display-answer-form [show-answer-textbox id answer]
  [:div.mb-6
   (if-not show-answer-textbox
     (components/icon-button {:class    :primary.gap--r1
                              :on-click #(do (>evt [::toggle-reply-attribute id :show-answer-textbox])
                                             (>evt [::create-answer id]))
                              :text-loc :replies.show.answer-textbox})
     [:div.p-4.flex.flex-col
      [:div.flex
       (ui-input/checkbox {:id        "nothing-to-add"
                           :checked?  (:nothing-to-add answer)
                           :on-change #(>evt [::toggle-reply-answer-attribute id :nothing-to-add])})
       [:label.ml-1 {:for "nothing-to-add"} [:h5 (common/loc :replies.answer.nothing-to-add.label)]]]
      [:div.mt-4
       {:class (when (:nothing-to-add answer) "hidden")}
       [:label.lux {:for "answer"} (common/loc :replies.answer.given-answer)]
       [:textarea.answer.mb-2.w-full.h-48
        {:class "lg:w-2/3"
         :disabled  (:nothing-to-add answer)
         :on-change #(>evt [::update-reply-answer id (util/tval %)])}]]
      [:div.mb-6.mt-6
       (components/icon-button {:class     :primary.gap--r1
                                :on-click  #(>evt [::answer-reply id])
                                :icon      :lupicon-save
                                :disabled? (and (not (:nothing-to-add answer))
                                                (empty? (:message answer)))
                                :text-loc  :replies.answer.create-answer})
       (components/icon-button {:class    :primary.gap--r0
                                :on-click #(do (>evt [::toggle-reply-attribute id :show-answer-textbox])
                                               (>evt [::create-answer id]))
                                :text-loc :cancel})]])])

(defn- reply-answer [{:keys [answer show-answer-textbox id]}]
  (let [can-answer? (<sub [:auth/application? :answer-reply])]
    [:div
     [:div.pb-4.pt-4.border-gray-300.border-solid.border-0.border-t-2
      (cond
        (:created answer) (display-given-answer answer)
        can-answer? (display-answer-form show-answer-textbox id answer))]]))

(defn- reply-related-issue
  "Renders an issue related to some reply.
  The issue is displayed as a link that opens up a modal for more information about the issue"
  [{:keys [id display-name issue-type] :as issue}]
  [:div {:key id}
   [:a {:on-click #(do (.stopPropagation %)
                       (>evt [::open-issue-stats-modal (open-issue-modal issue)]))}
    (case issue-type
      "statement" (str (common/loc :replies.statement) " - " (-> issue :person :text) " - " display-name)
      "neighbor"  (str (common/loc :replies.neighbor) " - " (-> issue :owner :address :street) " - " display-name)
      "bulletin"  (str (common/loc :replies.bulletin) " - " display-name))]])

(defn- reply-row-information [{:keys [id expanded reply-created reply-due-date answer]}]
  (let [has-answer? (boolean (:created answer))
        can-delete? (<sub [:auth/application? :delete-reply])]
    [:tr
     {:id id
      :aria-expanded expanded
      :on-click #(>evt [::toggle-reply-attribute id :expanded])
      :style  {:border-bottom "none"}}
     [:td
      [:button.tertiary
       [:i {:class (if expanded :lupicon-chevron-up :lupicon-chevron-down)}]]]
     [:td (js/util.finnishDate reply-created)]
     [:td (when reply-due-date (js/util.finnishDate reply-due-date))]
     [:td (when has-answer? (-> answer :created (js/util.finnishDate)))]
     [:td (when has-answer? (str (-> answer :user :firstName) " " (-> answer :user :lastName)))]
     [:td (when (and (not has-answer?) can-delete?) [:button.lupicon-remove.bg-inherit.tertiary
                                                     {:on-click #(do (.stopPropagation %)
                                                                     (>evt [::delete-reply id]))}])]]))

(defn- reply-row-related-issues [{:keys [id expanded related-issues]}]
  [:tr {:on-click #(>evt [::toggle-reply-attribute id :expanded])
        :style    {:border-top    "none"
                   :border-bottom (when expanded "none")}}
   [:td {:col-span 6}
    [:div.mb-2
     [:h4 (common/loc :replies.related-issues)]
     (doall (for [issue related-issues]
              (reply-related-issue issue)))]]])

(defn- expanded-reply-row [reply]
  [:tr.expanded-2.rounded-sm {:style {:border-top "none"}}
   [:td.gap--t2 {:col-span 6}
    (reply-trunc-message reply)
    (reply-answer reply)]])

(defn- reply-row
  "A row in the replies table consists of 3 sub-rows.
  The first sub-row displays some information about when the reply was created, answered, due-date, creator and a remove button
  The second sub-row displays the related issues connected to the reply
  The third sub-row is hidden by default and contains the reply message and a form for answering the reply, or if answered will instead display the given answer"
  [{:keys [expanded] :as reply}]
  [:<>
   (reply-row-information reply)
   (reply-row-related-issues reply)
   (when expanded (expanded-reply-row reply))
   [:tr.empty {:aria-hidden true} [:td]]])

(defn- replies-table [headers replies]
  [:table.w-full.table.no-stripes.mb-2
   [:thead
    [:tr.bg-gray-200
     [:th {:width "50%"}]
     (doall (for [header headers]
              [:th.text-left.p-1 {:key header}
               (common/loc (util/kw-path :replies header))]))]]
   [:tbody
    (doall (for [reply (sort-by :reply-created (vals replies))]
             ^{:key (:id reply)} [reply-row reply]))]])

(defn replies []
  (let [headers     ["reply.created-date" "reply.due-date" "reply.given-date" "reply.giver" "remove"]
        replies     (<sub [::replies])
        temp-reply  (<sub [::temp-reply])
        can-create? (<sub [:auth/application? :create-reply])]
    [:div.mt-16
     [:h2 (common/loc :replies.header)]
     (cond
       (not-empty temp-reply)  (create-new-reply-view)
       (not= :loading replies) [:div (when can-create?
                                       [:button.primary.my-4 {:on-click #(>evt [::create-new-reply])}
                                        (common/loc :replies.button.open-create)])
                                (if (seq replies)
                                  (replies-table headers replies)
                                  [:p (common/loc :replies.no-replies)])])]))
