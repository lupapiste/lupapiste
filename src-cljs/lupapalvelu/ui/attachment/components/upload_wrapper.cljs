(ns lupapalvelu.ui.attachment.components.upload-wrapper
  (:require [lupapalvelu.ui.attachment.components.upload-mixin :as upload-mixin]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]))

(rum/defcs upload-wrapper < (upload-mixin/upload-mixin)
                            rum/reactive ; maybe the 'component' uses rum/react, maybe not...
  "Convenience wrapper file upload components. The idea is that the
  wrapper takes care of the mundane upload details and provides the
  wrapped component with the necessary upload-options.

  Wrapper options [optional]. In addition to upload-mixin options (see
  above):
    :component Wrapped component
    [:test-id]   Test id (prefix).

  The wrapped component receives the following options as the first
  argument:

    :callback Callback function (pass-through from wrapper)
    :test-id  Pass-through from wrapper
    :input    Hidden file input (test-id is wrapper-test-id-option-input)
    :input-id Id of the hidden file input.

  The other-args are passed as is to the wrapped component."
  [{input-id ::upload-mixin/input-id} {:keys [component test-id multiple?] :as options} & other-options]
  (apply component
         (-> options
             (dissoc :component)
             (assoc :input [:input.hide-input
                            (merge {:type "file"
                                    :name "files[]"
                                    :id input-id}
                                   (when test-id
                                     {:data-test-id (common/test-id test-id
                                                                    :input)})
                                   (when multiple?
                                     {:multiple true}))]
                    :input-id input-id))
         other-options))
