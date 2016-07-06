LUPAPISTE.SutiDisplayModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.sutiService;

  self.showSuti = service.sutiEnabled;
  self.open = ko.observable( true );
  self.suti = service.sutiDetails;
  self.waiting = ko.observable();

  function sutiComputed( key, refresh ) {
    return self.disposedComputed( {
      read: function() {
        return _.get( self.suti(), ["suti", key]);
      },
      write: function( value ) {
        var app = lupapisteApp.models.application;
        if( app.id() ) {
          service.updateApplication( app,
                                     _.set( {}, key, value),
                                     {refresh: refresh,
                                      waiting: self.waiting});
        }
      }
    });
  }

  self.sutiId = sutiComputed( "id", true );

  self.sutiAdded = sutiComputed( "added" );

  self.disposedComputed( function() {
    var app = lupapisteApp.models.application;
    if( app.id() ) {
      service.fetchApplicationData( app, {waiting: self.waiting} );
    }
  });

  self.sutiLink = function() {
    window.open( self.suti().www, "_blank");
  };
};
