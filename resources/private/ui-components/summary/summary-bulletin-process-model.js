LUPAPISTE.SummaryBulletinProcessModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var app = params.application;

  self.internalSelected = app.bulletinProcess;
  self.required = params.required;

  self.selected = self.disposedComputed( {
    read: self.internalSelected,
    write: function( newSelected ) {
      if( !_.isBlank( newSelected )) {
        var id = app.id();
        ajax.command( "change-bulletin-process",
                      {id: id,
                       process: newSelected})
            .success( _.partial( repository.load, id, null, null, false ))
            .call();
      }
    }
  });

  function locText( value, lempty ) {
    return loc( _.isBlank( value ) ? lempty : "bulletin.process." + value );
  }

  self.options = self.disposedPureComputed( function() {
    var choices = app.bulletinProcesses();
    return self.selected() ? _.filter( choices ) : choices;
  });

  self.optionsText = function( item ) {
    return locText( item, "choose" );
  };

  self.text = self.disposedPureComputed( function() {
    return locText( self.selected(), "a11y.summary.missing" );
  });
};
