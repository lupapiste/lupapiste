(ns lupapalvelu.invoices.integration.general.core
  (:require [lupapalvelu.invoices.integration.general.converter :as converter]
            [lupapalvelu.invoices.integration.generate-impl :as impl]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [sade.date :as date]))

(def ^:private sftp-datetime (date/pattern-formatter "YYMMddHHmmss"))

(defn- filename-for-xml-sftp [cfg timestamp]
  (str (tb/filename-prefix cfg "Lupapiste_")
       (date/zoned-format timestamp sftp-datetime)
       ".xml"))

(defn ->xml
  [cfg created invoices]
  (converter/->xml cfg created invoices))

(defmethod impl/generate-invoice-transfer-batch "general"
  [{:keys [cfg created invoices]}]
  {:content      (->xml cfg created invoices)
   :filename     (filename-for-xml-sftp cfg created)
   :content-type "text/xml;charset=UTF-8"})
