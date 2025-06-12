(ns lupapalvelu.ui.attachment.components.upload-zone
  (:require ["react" :as react]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.common.upload :as upload]))

(defn upload-zone
  "Upload zone for files

  Use with rum lupapalvelu.ui.attachment.components.upload-wrapper"
  [file-input file-input-id]
  [:div.drop-zone-highlight
   [:drop-zone.hidden
    [:div.drop-zone-placeholder
     [:i.lupicon-upload ]
     [:div (loc "dropzone.placeholder")]]]
   file-input
   [:div.upload-zone
    [:i.lupicon-upload]
    [:span.b (loc "uploadzone.multiple.text-one.part-one")]
    " "
    [:span (loc "uploadzone.multiple.text-one.part-two")]
    " "
    [:label {:data-test-id "choose-file-button"
             :on-click #(.click (.getElementById js/document file-input-id))}
     (loc "uploadzone.multiple.label")]
    " "
    [:span (loc "uploadzone.multiple.text-two")]]])

(defn upload-zone-new
  "New version of upload-zone

  Also handles the dropzone and upload service integration
  replacing rum upload-wrapper and upload-mixin."
  ;; NOTE: old wrapper uses multiple? instead of single-file, maybe use that here also?
  [{:keys [input-id single-file on-success on-pending on-failed bucket]
    :or {single-file false}}]
  (let [loc-prefix (if single-file
                     "uploadzone.single."
                     "uploadzone.multiple.")
        ;; Reference to drop-zone highlight element
        zone-ref (react/useRef)
        ;; Reference to file input element
        file-input-ref (react/useRef)
        ;; Fileupload-service requires ID for the file input, passing the
        ;; element itself isn't supported now. If no input-id parameter given,
        ;; generate one and keep it stored on a ref for a stable value.
        input-id (.-current (react/useRef (or input-id (str (gensym)))))]
    ;; Connect file input and dropzone div to fileUploadService and attachmentsService.
    ;; This makes selecting a file to trigger upload, which then triggers hub events.
    (react/useEffect (fn []
                       (upload/bindToElem #js {:id input-id
                                               ;; Goes to jquery $() so element should work?
                                               :dropZone (.-current zone-ref)
                                               :allowMultiple (not single-file)
                                               :bucket bucket})
                       js/undefined)
                     #js [input-id])
    ;; Connect hub events to given callbacks.
    ;; Add other handlers later, if needed.
    (react/useEffect (fn []
                       (let [subscriptions (upload/service-hubscribe input-id
                                                                     (into {} (filter val {:added nil
                                                                                           :bad nil
                                                                                           :pending on-pending
                                                                                           :finished nil
                                                                                           :failed on-failed
                                                                                           :success on-success
                                                                                           :progress nil})))]
                         (fn []
                           (upload/destroy-file-upload-subscriptions input-id (vals subscriptions)))))
                     #js [input-id on-success on-pending on-failed])
    [:div.drop-zone-highlight
     {:ref zone-ref}
     [:drop-zone.hidden
      [:div.drop-zone-placeholder
       [:i.lupicon-upload]
       [:div (loc "dropzone.placeholder")]]]
     ;; NOTE: Some upload-wrapper uses add extra elements here, around the :input
     [:input.hide-input
      {:id input-id
       :type "file"
       :multiple (not single-file)
       :ref file-input-ref}]
     [:div.upload-zone
      [:i.lupicon-upload {:aria-hidden true}]
      [:span.b (loc (str loc-prefix "text-one.part-one"))]
      " "
      [:span (loc (str loc-prefix "text-one.part-two"))]
      " "
      [:label {:data-test-id "choose-file-button"
               :on-click (fn [_e]
                           (.click (.-current file-input-ref)))}
       (loc (str loc-prefix "label"))]
      " "
      [:span (loc (str loc-prefix "text-two"))]]]))
