// Allows authority to explicitly set the application state.
// Params:
// id: application id
// state: application state
LUPAPISTE.ChangeStateModel = function( params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.states = ko.observableArray();

  self.stateText = function( state ) {
    return loc( state );
  };

  self.selectedState = ko.observable( params.state );

  function fetchStates() {
    var backup = self.selectedState();
    ajax.query( "change-application-state-targets", {id: params.id})
    .success( function( res ) {
      self.states( res.states );
      self.selectedState( backup );
    })
    .call();
  }

  // When the value is changed the application is reloaded.
  self.changeState = self.disposedComputed( function() {
    var newState = self.selectedState();
    if( newState && newState !== params.state
                 && _.includes( self.states.peek(), newState ) ) {
      ajax.command( "change-application-state",
                    {state: newState,
                     id: params.id})
      .success ( function() {
        repository.load( params.id, null, null, false );
      })
      .call();
    }
  });

  // Initialization
  if (params.id) {
    fetchStates();
  }
};
