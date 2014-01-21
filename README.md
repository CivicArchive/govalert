# govalert

Get timely government insight.

## Usage

GovAlert runs on the Java Virtual Machine, making it possible to install on systems independent of underlying hardware. 

Heroku is one of many available options for running GovAlert:

## Heroku Quick Start

GovAlert can run on the Heroku PAAS <http://heroku.com>. Assuming you have an account on
Heroku.com and have installed the Heroku Toolbelt on your workstation, start by cloning the repo:

    $ git clone git@github.com:TerjeNorderhaug/govalert.git
    $ cd govalert

Create the app by executing the following in a shell with the govalert root directory
as current path:

    $ heroku apps:create

Add a free ElasticSearch instance:

    $ heroku addons:add bonsai:starter

Add free email:

    $ heroku addons:add sendgrid:starter

Deploy the code:

    $ git push heroku master

## Heroku Setup

The file called Procfile in the govalert root directory defines Heroku run actions.

You can run setup to migrate the ElasticSearch database and add harvesting rules. 
For example, this will define a crawler for the City of La Mesa in the default index (all quotes, ampersands and slashes have to be escaped with a backslash - see separate section for various JSON crawl patterns):

    $ heroku run setup gov \'{\"title\":\"La Mesa\",\"govbody\":\"us.ca.lamesa\",\"agendas\":{\"url\":\"http://www.cityoflamesa.com/archive.aspx?AMID=30\&Type=\&ADID=\"}}\'

For convenience, you may automatically escape the harvesting pattern from a multiline input:

    $ sed -e "s/'/\'/g" -e 's/"/\"/g' -e "s/&/\&/g" -e "1s/^/\\'/" -e "\$s/\$/\\'/" | xargs -0 heroku run setup gov 
      {"title" : "Carlsbad",
       "govbody" : "ca.sd.carlsbad",
       "agendas" : {"mode" : "granicus",
                    "url" : "http://carlsbad.granicus.com/ViewPublisherRSS.php",
                    "args" : {"view_id" : "6",
                              "mode" : "agendas"}}}

Make sure to end the multiline input with ctrl-d.

See the section below for other harvesting patterns.

Run setup with no arguments at any time to list all database indices and their associated harvesting rules:

    $ heroku run setup

## Subscribing to Alerts

If you need to be able to submit subscriptions from web forms, ensure you have a Heroku dyno for the web process:

    $ heroku ps:scale web=1
    $ heroku ps

A basic web page with a subscription form is now available:

    $ heroku open

Customize the html file in the distribution, or submit another form to the same location.

## Broadcasting Alerts

To make a test run harvesting documents and broadcasting alerts, run a notify command (preferably with an admin/reply email address as additional argument):

    $ heroku run notify

Expect to receive emails to the admin/reply address regarding the harvesting, as well as email notifications for the subscriptions.  

## Scheduling Alerts

You can set up regular harvesting of government documents and alerts
by adding the free Heroku Scheduler and open its dashboard:

    $ heroku addons:add scheduler:standard
    $ heroku addons:open scheduler

On the Scheduler Dashboard:
  1. click “Add Job…”
  2. enter 'notify' as task after the dollar sign. 
  3. in the same field, enter an email address of yours for admin/replies
  4. select a frequency (e.g. daily)
  5. specify dyno size (usually 1X, 2X to process large documents) 
  6. set next run time adjusted for time zone (typically at night, or in a few minutes for testing)

Please carefully read the Scheduler instructions if you're setting up production deployment
crawling massive document repositories: 
https://devcenter.heroku.com/articles/scheduler

## ElasticSearch JSON Harvesting patterns

Crawling the Granicus server for Encinitas:

    {"govbody" : "ca.sd.encinitas",
     "title" : "Encinitas",
     "agendas" : {"mode" :"granicus",
                  "url" : "http://encinitas.granicus.com/ViewPublisherRSS.php?view_id=7&mode=agendas"} }

Crawl America's Finest City:

      {"title" : "San Diego City Council",
        "govbody" : "ca.sd.sd",
        "agendas" : {"mode" : "sire",
                     "url" : "http://google.sannet.gov/search",
                     "args" : {"as_qdr" : "d7",
                               "output" : "xml_no_dtd",
                               "requiredfields" : "PATH:councildockets|PATH:councilminutes|PATH:councilresults",
                               "getfields" : "DOCUMENT_URL.DOC_DATE.TITLE.SORTORDER",
                               "sort" : "date:D:S:d1",
                               "ie" : "UTF-8",
                               "client" : "scs_ocd",
                               "filter" : "0",
                               "site" : "documents",
                                "q" : "Council+inmeta:DOC_DATE_NUM:20130101..20230101"}},
         "docs" : {"mode" : "sire",
                   "url" : "http://dockets.sandiego.gov/sirepub/agview.aspx?agviewdoctype=Agenda&agviewmeetid="}}


Harvesting the various subcommittees of San Diego County:

     {"title" : "San Diego County",
      "govbody" : "ca.sd",
      "agendas" : {"mode" : "govdelivery",
                   "url" : "http://bosagenda.sdcounty.ca.gov/agendadocs/materials.jsp",
                   "sub" : ["Air Pollution",
                            "Flood",
                            "Sanitation",
                            "Housing Authority",
                            "Redevelopment Successor Agency",
                            "Regular",
                            "Special Meeting",
                            "IHSS", 
                            "Regular Old"],
                   "history" : 30}}


Note that to have multiple search patterns for the same govbody, each has to be given a name when using setup or the ElasticSearch API.

## Using the ElasticSearch API

The ElasticSearch API is documented at:

    http://www.elasticsearch.org/guide/reference/api/

GovAlert creates a default index called 'gov':

    $ curl -XPUT 'http://localhost:9200/gov/'

Substitute 'http://localhost:9200' in the examples with the URL for your ElasticSearch cluster, and 'gov' with your index name if different.

The "harvester" mappings in the elasticSearch database declares JSON patterns that controls how to crawl when harvesting content from government websites.
To retrieve the harvester mapping definition, execute : 

    $ curl -XGET 'http://localhost:9200/gov/harvester/_mapping'

This should return a JSON structure like:

    {"harvester":  
      {"properties":
        {"govbody":{"type":"string","index":"not_analyzed","store":true,"omit_norms":true,"index_options":"docs"},
         "title":{"type":"string","index":"not_analyzed","store":true,"omit_norms":true,"index_options":"docs"},
         "agendas":{"type":"object"},
         "docs":{"type":"object"}}}}

Add named crawling patterns using the "harvester" mapping, such as:

    $ curl -XPUT 'http://localhost:9200/gov/harvester/ca.sd.encinitas' '{
    "govbody":"ca.sd.encinitas",
    "title":"Encinitas",
    "agendas":{"mode":"granicus","url":"http://encinitas.granicus.com/ViewPublisherRSS.php?view_id=7&mode=agendas"} }'

This declares a crawl pattern to harvest agendas and support documents from the specified Granicus server for the city of Encinitas.

The "subscription" mappings the the ElasticSearch database 

    $ curl -XGET 'http://localhost:9200/gov/subscription/_mapping'

You can add subscriptions:

    $ curl -XPUT 'http://localhost:9200/gov/subscription/1' '{
    "email":"joe@doe.com",
    "query":"*" }'

## License

Copyright © 2013-2014
Made possible by a generous grant from the Knight Foundation.
Distributed under the Eclipse Public License, the same as Clojure.
