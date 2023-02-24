(ns lupapalvelu.sftp.client
  (:require [clj-ssh.cli :as ssh-cli]
            [clj-ssh.ssh :as ssh]
            [taoensso.timbre :refer [info error]]
            [sade.util :as util]
            [sade.files :as files])
  (:import [java.io FileWriter FileNotFoundException]
           [com.jcraft.jsch JSchException ChannelSftp$LsEntry]))

(defn write-to! [file content]
  (let [writer (FileWriter. file)]
    (.write writer content)
    (.close writer)))

(defn- with-ssh-channel [target-address {:keys [username password private-key-path]} callback-fn-for-channel]
  {:pre [(fn? callback-fn-for-channel)]}
  (try
    (let [agent (ssh/ssh-agent {:use-system-ssh-agent false})] ;; TODO: Make :use-system-ssh-agent configurable?
      (when private-key-path
        (ssh/add-identity agent {:private-key-path private-key-path}))
      (let [session (ssh/session agent target-address (util/assoc-when {:strict-host-key-checking :no
                                                                        :username username}
                                                                       :password password))]
        (ssh/with-connection session
          (let [channel (ssh/ssh-sftp session)]
            (ssh/with-channel-connection channel
              (callback-fn-for-channel channel))))))
    (catch JSchException e
      (error e (str "SSH connection " username "@" target-address))
      (throw e))))

(defn get-file [target-address {:keys [username password]} file-to-get target-file-name]
  (info "sftp" (str username "@" target-address ":" file-to-get) target-file-name)
  (ssh-cli/sftp target-address :get
                file-to-get
                target-file-name
                :username username
                :password password
                :strict-host-key-checking :no))

(defn get-file-with-pred [target-address auth path-to-file file-prefix-or-some-pred target-file-name]
  (info "Getting file from " target-address " in " path-to-file)
  (with-ssh-channel
    target-address
    auth
    (fn [channel]
      (info "sftp: ls" path-to-file)
      (let [some-pred (if (fn? file-prefix-or-some-pred)
                        file-prefix-or-some-pred
                        (fn [^ChannelSftp$LsEntry entry]
                          (when (and (.startsWith (.getFilename entry) file-prefix-or-some-pred)
                                     (.endsWith   (.getFilename entry) ".xml"))
                            (.getFilename entry))))
            filename (->> (ssh/sftp channel {} :ls path-to-file)
                          (sort-by #(.getMTime (.getAttrs ^ChannelSftp$LsEntry %)) >)
                          (some some-pred))
            file-to-get (str path-to-file filename)]
        (if filename
          (do (info "sftp: get" file-to-get)
              (ssh/sftp channel {} :get file-to-get target-file-name)
              (info "sftp: done, saved file to" target-file-name)
              target-file-name)
          (throw (FileNotFoundException. (str "file not found from sftp, pred:" file-prefix-or-some-pred))))))))

(defn upload-file!
  "Upload string content as a file to a remote server
   Usage: (upload-file! <ip-address> <string-content-like-xml> <remote-path-to-file> {:username <sftp-user> :private-key-pah <path-to-local-private-key>})"
  [target-address string-content remote-path auth]
  (info "Uploading file to " target-address " in " remote-path)
  (with-ssh-channel
    target-address
    auth
    (fn [channel]
      (files/with-temp-file temp-file
        (let [file-name (.getAbsolutePath temp-file)]
          (write-to! temp-file string-content)
          (ssh/sftp channel {} :put file-name remote-path))))))
