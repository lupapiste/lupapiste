(ns lupapalvelu.ui.attachment.components.upload-mixin
  (:require [lupapalvelu.common.upload :as upload]
            [lupapalvelu.ui.util :as jsutil]))

(defn upload-mixin
  "The first component argument is either callback function or options map.
  Options [optional]:

    callback: callback function or map. See upload/service-hubscribe
              for details.

    [multiple?]: whether multiple files can be selected/uploaded.

    [dropzone]:  dropzone container (!) selector that will be passed to
                 $() on the jQuery side. Note: the selector is for the
                 some element that (ultimately) contains the drop-zone
                 element.

    [bucket]:    name of the bucket these files should be uploaded in.
                 Only available when uploading non-application files as admin.

    [input-id]: (hidden) file input-id. Default is generated unique id."
  []
  (letfn [(parse-args [state]
            (let [[args] (:rum/args state)]
              (if (map? args)
                (select-keys args [:callback :dropzone :input-id :multiple? :bucket])
                {:callback args})))
          (initialize [state]
            (let [{:keys [callback dropzone
                          multiple? bucket]} (parse-args state)
                  input-id                   (::input-id state)]
              (upload/bindToElem (js-obj "id" input-id
                                         "dropZone" dropzone
                                         "allowMultiple" (boolean multiple?)
                                         "bucket" bucket))
              (assoc state
                     ::fileupload-subscription-ids (upload/service-hubscribe input-id
                                                                             callback))))]
    {:init          (fn [state _]
                      (assoc state ::input-id (get (parse-args state) :input-id (jsutil/unique-elem-id "file-upload-"))))

     :did-mount     initialize

     :should-update (fn [old new]
                      (let [{old-callback :callback} (parse-args old)
                            {new-callback :callback} (parse-args new)]
                        (if (or (not= (::input-id old) (::input-id new))
                                (not= old-callback new-callback))
                          (upload/destroy-file-upload-subscriptions (::input-id old) (vals (::fileupload-subscription-ids old)))
                          false)))

     :did-update    initialize

     :will-unmount  (fn [{::keys [input-id fileupload-subscription-ids] :as state}]
                      (upload/destroy-file-upload-subscriptions input-id (vals fileupload-subscription-ids))
                      (dissoc state ::input-id)
                      state)}))
