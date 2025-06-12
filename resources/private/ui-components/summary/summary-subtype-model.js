LUPAPISTE.SummarySubtypeModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var app = params.application;

  self.selected = app.permitSubtype;
  self.required = params.required;
  self.help = app.permitSubtypeHelp;

  function locText( value, lempty ) {
    return loc( _.isBlank( value ) ? lempty : "permitSubtype." + value );
  }

  self.subtypes = self.disposedPureComputed( function() {
    // The empty selection is available only initially
    var subtypes = app.permitSubtypes();
    return self.selected() ? _.filter( subtypes ) : subtypes;
  });

  self.optionsText = function( item ) {
    return locText( item, "choose" );
  };

  self.text = self.disposedPureComputed( function() {
    return locText( self.selected(), "a11y.summary.missing" );
  });
};
