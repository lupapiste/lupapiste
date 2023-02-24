LUPAPISTE.PreambleTitleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  var service = lupapisteApp.services.summaryService;

  var app = params.application;

  self.application = app;

  self.canEdit = service.editingSupported;

  self.editMode = service.editMode;

  self.editAddress = self.disposedPureComputed( function() {
    return self.editMode() && service.authOk( "address" );
  });

  self.editLocation = self.disposedPureComputed( function() {
    return self.editMode() && service.authOk( "location" );
  });

  self.changeLocation = _.wrap( "change-location", hub.send );

  self.operationTitle = self.disposedPureComputed( function() {
    if( !_.isBlank( app.primaryOperation() )) {
      return util.nonBlankJoin( [loc( app.primaryOperationName() ),
                                 app.permitSubtype() === "muutoslupa"
                                 ? loc( "permitSubtype.muutoslupa")
                                 : null],
                                " - " );
    }
  });

  self.changeAddress = function( data, event ) {
    var txt = _.trim( _.get( event, "target.value" ) );
    if( !_.isBlank( txt ) && txt !== app.address() ) {
      ajax.command( "change-address", {id: app.id(),
                                       address: txt})
        .success( function() {
          hub.send( "indicator", {style: "positive"});
          app.address( txt );
        })
        .call();
    }
  };
};
