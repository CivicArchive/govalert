(ns govalert.notify.broadcast
  (:use [govalert.notify.sendmail :only (sendmail)]
        [govalert.notify.mailformat :only (format-email)])
  (:require [govalert.elastic.search :as search]
            [clj-time.format :as time-format]
            [clj-time.core :as time]))

(defn cached-time [document]
  (:timestamp document))

(defn reduce-by [test key collection]
  (reduce
   (fn [lead candidate]
     (if lead
       (let [value (key candidate)]
         (if (test lead value) lead value))
       (key candidate)))
   nil
   collection))

(defn documents-updated 
  "Returns the most recent datetime for the documents" 
  [documents]
  (reduce-by time/after? cached-time documents))

(defn broadcast-alert
  "Sends an alert to email with new documents based on query, keeping a record of the sent documents as a side effect"
  ;; careful with side effects, so completed? is tested before completed! modifies the db
  ;; Keep side effects isolated here, particularly the registration of completion 
  [email query & {govbody :govbody template :template after :after server :server sender :sender}]
     (let [dockets (search/matching-dockets govbody :query query :after after)
           updated (documents-updated (flatten (map :docs dockets)))]
        (if updated 
           (let [req {:q query :t govbody :items dockets}
                 html (apply str (format-email req template))] 
             (assert (not (empty? html)))
             (sendmail email :subject (str "Alert: " query) :from sender :html html :server server)         
             {:count (reduce + 0 (map count (map :docs dockets)))
              :html html
              :updated updated}))))

(defn broadcast [alerts & {server :server}]
    (doseq [{q :query govbody :govbody email :email template :template} alerts]
      (broadcast-alert email q 
        :govbody govbody
        :server server
        :template (if (string? template) (java.net.URL. template) template))))


