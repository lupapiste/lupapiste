(ns lupapalvelu.smoketest.bulletin-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.application-bulletins :as bulletins]
            [monger.operators :refer :all]))

(mongocheck                                                 ; LPK-3539 smoketest comment attachments
  :application-bulletin-comments
  (fn [{:keys [attachments _id]}]
    (when-let [errs (and (seq attachments)
                         (reduce (fn [acc att]
                                   (if-let [err (bulletins/comment-file-checker att)]
                                     (assoc acc (:fileId att) err)
                                     acc))
                                 nil
                                 attachments))]
      {:id _id :errors errs}))
  :attachments :_id)
