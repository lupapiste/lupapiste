// Warranty period rows for the YA construction table.
LUPAPISTE.WarrantyModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel());

  var application  = lupapisteApp.models.application;

  var authOk = lupapisteApp.models.applicationAuthModel.ok;

  self.warrantyStart = ko.observable( application.warrantyStart() );
  self.warrantyEnd = ko.observable( application.warrantyEnd() );

  self.canEditStart = self.disposedPureComputed( function() {
    return authOk( "change-warranty-start-date" );
  });

  self.canEditEnd = self.disposedPureComputed( function() {
    return authOk( "change-warranty-end-date" );
    });

  self.showWarrantyStart = self.disposedPureComputed( function() {
    return self.warrantyStart() || self.canEditStart();
  });

  self.showWarrantyEnd = self.disposedPureComputed( function() {
    return self.warrantyEnd() || self.canEditEnd();
  });

  self.badStart = ko.observable();
  self.badEnd = ko.observable();

  function timestamp( date ) {
    return moment( date ).valueOf();
  }

  function sendDate( cmd, date, badObs, param, appObs ) {
    var ts = timestamp( date );
    if( !badObs() && ts !== timestamp( appObs() )) {
      ajax.command( cmd, _.set ({id: application.id()},
                                param, ts))
        .success( function( res ) {
          util.showSavedIndicator( res );
          appObs( date );
        })
        .call();
    }
  }

  // Delay in ms to make sure that badObs is up-to-date.
  var wait = 200;

  self.disposedSubscribe( self.warrantyStart, function( date ) {
    _.delay( sendDate,
             wait,
             "change-warranty-start-date",
             date,
             self.badStart,
             "startDate",
             application.warrantyStart );
  });

  self.disposedSubscribe( self.warrantyEnd, function( date ) {
    _.delay( sendDate,
             wait,
             "change-warranty-end-date",
             date,
             self.badEnd,
             "endDate",
             application.warrantyEnd );
  });

    // The warranty period can change outside of this component, when
  // the application is closed.
  self.disposedSubscribe( application.warrantyStart, function( v ) {
    self.warrantyStart( v );
    self.badStart( false );
  });

  self.disposedSubscribe( application.warrantyEnd, function( v ) {
    self.warrantyEnd( v );
    self.badEnd( false );
  });

};
