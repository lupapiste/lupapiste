// Allows authority to explicitly set the application state.
LUPAPISTE.ChangeStateModel = function() {
  "use strict";
  var self = this;

  self.states = ["extinct", "constructionStarted",
                 "inUse", "closed", "appealed"];

  self.stateText = function( state ) {
    return loc( "state." + state );
  };

  // When the value is changed the application is reloaded. The value
  // never represents the application's current value so the computed
  // always returns undefined.
  self.changeState = ko.computed( {
    read: _.noop,
    write: function( newState ) {
      if( _.includes( self.states, newState ) ) {
        var appId = lupapisteApp.models.application.id();
        ajax.command ( "change-application-state",
                       {state: newState,
                       id: appId})
        .success ( function() {
          repository.load( appId );
        })
        .call ();
      }
    }
  });
};
