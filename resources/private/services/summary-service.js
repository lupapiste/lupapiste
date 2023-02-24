LUPAPISTE.SummaryService = function() {
  "use strict";
  var self = this;

  var commands = {address: "change-address",
                  state: "change-application-state",
                  location: "change-location",
                  handlers: "upsert-application-handler",
                  subtype: "change-permit-sub-type",
                  tosFunction: "set-tos-function-for-application",
                  linkPermits: "remove-link-permit-by-app-id"};

  self.authOk =  function authOk( authKey ) {
    var authModel = lupapisteApp.models.applicationAuthModel;
    var action = _.get( commands, authKey );
    return Boolean( authModel && authModel.ok( action ));
  };

  self.editingSupported = ko.pureComputed( function() {
    return _.some( _.keys( commands ), self.authOk );
  });

  var edit = ko.observable( false );

  self.editMode = ko.computed( {
    read: function() {
      return edit() && self.editingSupported();
    },
    write: edit
  });

  function stopEditing() {
    self.editMode( false );
  }

  hub.subscribe( "contextService::enter", stopEditing );

  hub.subscribe( "side-panel-esc-pressed", stopEditing );
};
