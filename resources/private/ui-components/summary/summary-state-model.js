LUPAPISTE.SummaryStateModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var app = self.application;

  var internalState = ko.observable( app.state() );

  self.state = self.disposedComputed( {
    read: internalState,
    write: function( newState ) {
      if( !_.isBlank( newState )) {
        var id = app.id();
        ajax.command( "change-application-state",
                      {id: id,
                       state: newState})
          .success( _.partial( repository.load, id, null, null, false ))
          .call();
      }
    }
  });

  self.states = ko.observableArray();

  self.optionText = _.partial( loc );

  if( self.editing ) {
    ajax.query( "change-application-state-targets",
                {id: app.id()})
      .success( function( res ) {
        self.states(  res.states.sort( function( a, b ) {
          return util.localeComparator( loc( a ), loc( b ));
        }));
      })
      .call();
  }
};
