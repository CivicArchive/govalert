(ns govalert.harvest.govdelivery
    ; ## BOSC - agendas and support docs on the same page
  (:require [clj-http.client :as client]
            [clojure.java.io]
            [net.cgrand.enlive-html :as enlive]
            [clj-http.client :as client]
            [clj-time.format :as time-format]
            [clj-time.core :as time]
            [govalert.harvest.types :as types]
            [url-normalizer.core :as url-normalizer]
            [govalert.harvest.lib :refer (url-str http-get get-html clean-text agenda-from-anchor attachment-from-anchor)]))

(defn govdelivery-agenda 
   "Agenda matching the date and type, or nil if there are no agenda for the day"
   [url type date]
  (let [base (url-str url {:agendaDt date :agendaType type})
        doc (get-html base)
        anchors (enlive/select doc [:td.ContentArea (enlive/has [:b (enlive/text-pred #(re-find #"Agenda Index" %))]) :> :a])]
    (if-not (empty? anchors)
      (-> (first anchors)
          (agenda-from-anchor base)
          (assoc :linked-from base)))))

(defn regroup [{item :item subject :subject attachements :attachements uri :uri}]
  (for [a attachements]
    (assoc a
      :groupname subject
      :groupid item
      :groupurl uri)))

(defn agendadocs [url type date]
  (let [base (url-str url {:agendaDt date :agendaType type})
        doc (get-html base)]
   (flatten
    (for [table (enlive/select doc [:.ContentArea :.ContentArea :table :table enlive/but [:table]])
                :when (= ["Item" "Subject" "Attachments/Supporting Documentation"]
                         (map enlive/text (enlive/select table [:tr :th])))]
       (for [tr (enlive/select table [:tr])]
         (->
           (zipmap 
              [:item :subject :attachements]
              (for [td (enlive/select tr [:td])]
                 td))
           (assoc :linked-from base)
           (update-in [:attachements] (fn [a] (->> (enlive/select a [:a])
                                                   (remove (comp empty? :href :attrs))
                                                   (map #(attachment-from-anchor % base))))) 
           (update-in [:item] enlive/text)
           (update-in [:subject] (comp clean-text enlive/text))
           (regroup)))))))

(defn recent-days [n]
  {:pre [(integer? n)]}
  (let [t (time/now)]
    (for [d (range n)]
      (time/minus t (time/days d)))))

(def county-date-formatter (time-format/formatter "MM/dd/yyyy"))

(defn govdelivery-agendas [url sub n]
  (for [datestamp (map #(time-format/unparse county-date-formatter %) (recent-days n))
        :let [agenda (govdelivery-agenda url sub datestamp)]
        :when (not (empty? agenda))]
     (assoc agenda
             :date datestamp
             :docs {:mode "govdelivery" :datestamp datestamp :sub sub :url url})))

(defmethod types/agendas "govdelivery" [agendas]
  (mapcat 
    #(govdelivery-agendas (:url agendas) % (:history agendas))
    (:sub agendas)))

(defmethod types/attachments "govdelivery" [{docs :docs}]
  (agendadocs (:url docs) (:sub docs) (:datestamp docs)))









 




