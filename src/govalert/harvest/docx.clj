(ns govalert.harvest.docx
  (:use [clojure.java.io :only (input-stream file)])
  (:import [org.apache.poi.xwpf.extractor XWPFWordExtractor]
           [org.apache.poi.xwpf.usermodel XWPFDocument]))

;; See http://stackoverflow.com/questions/7102511/how-read-doc-or-docx-file-in-java
;;     https://bitbucket.org/smee0815/word-doc-poi/
;; Updated to XWPF to avoid "The supplied data appears to be in the Office 2007+ XML."

(defn read-text
  "Extracts the text from a docx input stream"
  [in]
  (->> (XWPFDocument. in)
       (XWPFWordExtractor.)
       (.getText)))

(defn file-text
  "Extracts the text from a docx file"
  [pathname]
  (->> (file pathname)
       (input-stream)
       (read-text)))