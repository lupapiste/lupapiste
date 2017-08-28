(ns lupapalvelu.xml.asianhallinta.attachment
  (:require [me.raynes.fs :as fs]
            [sade.strings :as ss]
            [lupapalvelu.attachment :as attachment]))

(defn insert-attachment! [unzipped-path application attachment type target timestamp user]
  (let [filename      (fs/base-name (:LinkkiLiitteeseen attachment))
        file          (fs/file (ss/join "/" [unzipped-path filename]))
        file-size     (.length file)]
    (attachment/upload-and-attach! {:application application :user user}
                                   {:attachment-type type
                                    :target target
                                    :required false
                                    :locked true
                                    :created timestamp
                                    :state :ok}
                                   {:filename filename
                                    :size file-size
                                    :content file})))
