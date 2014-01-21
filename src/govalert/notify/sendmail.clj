(ns govalert.notify.sendmail
    (:require [postal.core :as postal]))

(defrecord Server [host user pass ssl])

(defn make-server [host & {user :user pass :pass ssl :ssl}]
  (Server. host user pass ssl))

(defn sendmail [to & {from :from subject :subject text :text html :html server :server}]
  (-> {:to (if-not (empty? to) to from)
       :from (or from (:user server))               
       :subject (if-not (empty? subject) subject "Alert")
       :body (remove empty?
                [{:type "text/plain" :content (or text "")}
                  (if-not (empty? html) {:type "text/html" :content html})])}
      (with-meta server)
      (postal/send-message)))

