(ns lupapalvelu.backing-system.asianhallinta.attachment
  (:require [me.raynes.fs :as fs]
            [sade.strings :as ss]
            [lupapalvelu.attachment :as attachment]))

(defn insert-attachment! [unzipped-path application attachment type target contents timestamp user & [extra-options]]
  (let [filename      (fs/base-name (:LinkkiLiitteeseen attachment))
        file          (fs/file (ss/join "/" [unzipped-path filename]))
        file-size     (.length file)]
    (attachment/upload-and-attach! {:application application :user user}
                                   (merge {:attachment-type type
                                           :target target
                                           :required false
                                           :locked true
                                           :created timestamp
                                           :contents contents
                                           :state :ok}
                                          extra-options)
                                   {:filename filename
                                    :size file-size
                                    :content file})))
