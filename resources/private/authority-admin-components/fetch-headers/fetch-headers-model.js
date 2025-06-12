// Admin admin tool for fetching arbitrary GET request status and
// headers.
LUPAPISTE.FetchHeadersModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.url = ko.observable();
  self.headers = ko.observableArray();
  self.status = ko.observable();
  self.waiting = ko.observable();

  self.clear = function() {
    self.headers( [] );
    self.status( "" );
  };

  self.fetchHeaders = function() {
    self.clear();
    if( !_.isBlank( self.url() )) {
      ajax.query( "fetch-response-headers", {url: _.trim( self.url() )})
        .processing( self.waiting )
        .success( function( resp ) {
          self.status( resp.status );
          self.headers( resp.headers );
        })
        .error( function( resp ) {
          self.status( resp.text );
        })
        .call();
    }
  };
};
