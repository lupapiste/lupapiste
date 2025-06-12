(ns lupapalvelu.backing-system.asianhallinta.attachment
  (:require [babashka.fs :as fs]
            [lupapalvelu.attachment :as attachment]
            [sade.util :as util]))

(defn insert-attachment! [system unzipped-path application attachment type target contents timestamp user & [extra-options]]
  {:pre [(map? system)]}
  (let [filename  (fs/file-name (:LinkkiLiitteeseen attachment))
        file      (->> (fs/path unzipped-path filename) fs/unixify  fs/file)
        file-size (fs/size file)]
    (attachment/upload-and-attach! (util/mmap application user system)
                                   (merge {:attachment-type type
                                           :target          target
                                           :required        false
                                           :locked          true
                                           :created         timestamp
                                           :contents        contents
                                           :state           :ok}
                                          extra-options)
                                   {:filename filename
                                    :size     file-size
                                    :content  file})))
