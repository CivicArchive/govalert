(ns govalert.run
  ; Scheduled update of the search index and broadcast of alerts 
  (:require [govalert.harvest.crawl :refer [crawl]]
            [govalert.elastic.db :as db]
            [govalert.elastic.store :as store]
            [govalert.elastic.search :as search]
            [govalert.notify.broadcast :refer (broadcast-alert)]
            [govalert.notify.sendmail :refer (make-server sendmail)]
            [clojure.tools.logging :as log]))

(defn harvest 
  ([index]
     (doseq [h (search/harvesters index)]
        (crawl h 
          :handle-agenda (partial (db/taking-index store/index-agenda) index) 
          :handle-attachment (partial (db/taking-index store/index-attachment) index)))))

(defn notify
  ([index & {server :server sender :sender}]
      (doseq [{email :email query :query updated :updated :as subscription}
              (search/subscriptions index)]
        (try 
          (let [{c :count html :html updated :updated}
            (broadcast-alert email query
                :govbody index
                :after updated
                :sender sender
                :server server)]
            (if updated
              (store/add-subscription :email email :query query :updated updated))
            (log/info "Alerted" email "for query" query "on" index "with" c "matches."))
;          (catch Exception e
;            (log/error "Failed to notify for" index "subscription" subscription))
))))

(defn db-count [index]
  (db/with-index index 
    [(search/count-all-agendas)
     (search/count-all-docs)]))

(defn -main [& args]
  (let [[es-endpoint email-host email-user email-pass email-admin index] args
         tag (if index (str " for " index) "")]
    (log/debug "Running Harvest & Alert" tag "with" args)
    (let [srv (make-server email-host :user email-user :pass email-pass)
          feedback (fn [subject & [msg]] 
                     (if email-admin
                       (sendmail email-admin :subject subject :text (or msg subject) :server srv)
                       (log/info subject ":" msg)))]
      (feedback (str "GovAlert" tag) "Starting harvesting for")
      (identity ; future
        (try
          (do (assert es-endpoint)
              (db/init es-endpoint)
              (doseq [index (if index [index] (db/indices))]
                 (let [[n-agendas n-docs] (db-count index)]
                   (harvest index)
                   (let [[na2 nd2] (db-count index)]
                     (log/info "Harvested" (- n-agendas na2) "of" na2 "agendas and" (- n-docs nd2) "of" nd2 "attachments.")))
                 (notify index :server srv :sender email-admin)))
         ;(catch Exception e 
         ;  (feedback (str "Harvest failed" tag) (.getMessage e)))
         (finally 
          (feedback (str "GovAlert" tag) "Harvesting completed...")))))))

  
  





