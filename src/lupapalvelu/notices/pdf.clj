(ns lupapalvelu.notices.pdf
  "Mechanisms for generating the notice resolution attachment."
  (:require [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.core :as notices]
            [lupapalvelu.notices.util :as notices-util]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.shared-quill :as quill]
            [lupapalvelu.system.util-components :as components]
            [sade.date :as date]
            [sade.strings :as ss]))

(defn- loc [{lang :lang} & k]
  (apply i18n/localize lang k))

(defn- subtitle [command k]
  [:div {:style {:font-weight :bold}}
   (loc command k)])

(defn- applicants [command]
  (when-let [xs (seq (for [{:keys [address name]} (pdf/applicants command)]
                       (ss/trim-join ", " name address)))]
    [(subtitle command (if (= 1 (count xs))
                         :pdf.applicant
                         :pdf.applicants))
     [:div {:style {:white-space :pre-wrap}}
      (ss/join "\n" xs)]]))

(defn- demolition-target [{:keys [application] :as command}]
  (let [data      (-> application
                      notices-util/purkamisilmoitus-doc
                      :data)
        building? (:buildingDemolition data)
        bid       (cond
                    (not building?) nil
                    (= (:buildingId data)
                       "other")     (:manuaalinen_rakennusnro data)
                    :else           (:valtakunnallinenNumero data))
        target    (if building?
                    (ss/trim-join " " (loc command :application.building) bid)
                    (some-> data :kohde ss/trim ss/blank-as-nil))]
    (when target
      [(subtitle command :notice.demolition.target)
       [:div {:style {:white-space :pre-wrap}} target]])))

(defn- operation-name [{:keys [application] :as command}]
  (->> application :primaryOperation :name
       (loc command :operations)))

(defn- body-tags
  [command {:keys [state text user published]}]
  (let [loc             (partial loc command)
        subtitle        (partial subtitle command)
        {:keys [application]
         :as   command} (update-in command [:application :documents]
                                   tools/unwrapped)]
    (reduce (fn [acc row]
              (into acc row))
            [:div {:style {:display               :grid
                           :grid-template-columns "auto 1fr"
                           :gap                   "1em 1em"}}
             [:div {:style {:grid-column "1 / -1"}}
              [:h2 (operation-name command)]]
             (subtitle :applications.id.longtitle)
             [:div (:id application)]]
            [(applicants command)
             (demolition-target command)
             [(subtitle :notice.state)
              [:div (loc state)]]
             [(subtitle :notice.title)
              (quill/->tags text)]
             ;; Empty div needed for grid alignment
             [[:div][:div (date/finnish-date published)]]
             [(subtitle :applications.authority)
              [:div (ss/trim-join " "
                                  (:firstName user)
                                  (:lastName user))]]])))

(defn- header-tags [{:keys [organization lang created]}]
  (let [org-name (ss/trim (get-in (force organization)
                                  [:name (keyword lang)]))]
    [:div.header
     [:div.section.header
      [:div.row.pad-after
       [:div.cell.cell--50
        org-name]
       [:div.cell.cell--50.right
        (date/finnish-date created)]]]]))

(defn- generate-notice-attachment
  "Generates notice resolution pdf and binds it to a new attachment. Returns the attachment
  id."
  [{:keys [application created lang system] :as command} resolution]
  {:pre [(map? system)]}
  (let [html {:header (html/html (header-tags command))
              :body   (html/html (body-tags command resolution))
              :footer ""
              :title  (operation-name command)}
        pdf  (pdf/verdict-html->pdf html system)]
    (if (:ok pdf)
      (:id (pdf/upload-and-attach-pdf!
             {:command            command
              :pdf                pdf
              :attachment-options {:created         created
                                   :attachment-type {:type-group "paatoksenteko"
                                                     :type-id    "ilmoitus"}
                                   :locked          true
                                   :read-only       true
                                   :contents        (loc command :application.tabProcess)}
              :file-options       {:filename (i18n/localize-and-fill lang
                                                                     :notice.demolition.filename
                                                                     (:id application)
                                                                     (date/finnish-datetime
                                                                       created
                                                                       :zero-pad))}}))
      (throw (ex-info (:text pdf) pdf)))))

(defn- handle-pubsub-message
  "Returns true if the message is handled and false if needs to be tried again later."
  [system {:keys [job-id application-id lang]}]
  {:pre [(ss/not-blank? job-id) (ss/not-blank? application-id)]}
  (log/with-logging-context {:applicationId application-id}
    (let [{:keys [organization]
           :as   application} (mongo/by-id :applications application-id)
          resolution          (notices/resolution application)]
      (if-not (= job-id (:job-id resolution))
        ;; Ack and give up, since the resolution seems to have changed after the job
        ;; creation.
        (not (log/warnf "Do not generate notice resolution attachment for %s. Job-id %s does not match %s."
                        application-id job-id (:job-id resolution)))
        (try
          (let [command {:user         (:user resolution)
                         :application  application
                         :organization (delay (mongo/by-id :organizations organization))
                         :lang         lang
                         :created      (date/timestamp (date/now))
                         :system       system}]
            (->> (generate-notice-attachment command resolution)
                 (notices/update-resolution-attachment-id command)
                 boolean))
          (catch Exception e
            (log/error e "PDF generation for notice resolution failed:"
                       (ex-message e))
            false))))))

(defmethod lip/get-pubsub-subscription-handler :notices/resolution-queue
  [_id]
  handle-pubsub-message)

(defn start-generation-job [{:keys [system] :as command}]
  (let [job-id (mongo/create-id)
        msg    {:application-id (-> command :application :id)
                :lang           (:lang command "fi")
                :job-id         job-id}]
    (notices/update-resolution-job-id command job-id)
    (if-let [publisher (components/system->publisher system :notices/resolution-queue)]
      (publisher msg)
      (throw (ex-info "No publisher found!" {:system (keys system)})))
    job-id))
