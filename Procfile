web: lein with-profile production trampoline run -m govalert.api $BONSAI_URL
setup: lein with-profile production trampoline run -m govalert.init $BONSAI_URL
notify: lein with-profile production trampoline run -m govalert.run $BONSAI_URL smtp.sendgrid.net $SENDGRID_USERNAME $SENDGRID_PASSWORD
migrate: lein with-profile production trampoline run -m govalert.elastic.migrate $BONSAI_URL 

