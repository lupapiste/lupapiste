// Allows authority to explicitly set the application state.
LUPAPISTE.ChangeStateModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.states = ["extinct", "constructionStarted",
                 "inUse", "closed", "appealed"];

  self.stateText = function( state ) {
    return loc( "state." + state );
  };

  self.selectedState = ko.observable();


  // When the value is changed the application is reloaded.
  self.changeState = self.disposedComputed( function() {
    var newState = self.selectedState();
      if( _.includes( self.states, newState ) ) {
        var appId = lupapisteApp.models.application.id();
        ajax.command ( "change-application-state",
                       {state: newState,
                       id: appId})
        .success ( function() {
          repository.load( appId );
          // Reset the observable. Otherwise the select could show
          // wrong state if the application is changed.
          self.selectedState( "");
        })
        .call ();
      }
    });
};
