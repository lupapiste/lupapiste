// Allows authority to explicitly set the application state.
// Params:
// id: application id
// state: application state
LUPAPISTE.ChangeStateModel = function( params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.states = ["extinct", "constructionStarted",
                 "inUse", "closed", "appealed"];

  self.stateText = function( state ) {
    return loc( "state." + state );
  };

  self.selectedState = ko.observable( params.state );

  // When the value is changed the application is reloaded.
  self.changeState = self.disposedComputed( function() {
    var newState = self.selectedState();
    if( _.includes( self.states, newState ) ) {
      ajax.command( "change-application-state",
                    {state: newState,
                     id: params.id})
      .success ( function() {
        repository.load( params.id );
      })
      .call();
    }
  });
};
