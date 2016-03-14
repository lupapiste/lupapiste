// Assign an authority to the application.
LUPAPISTE.AuthoritySelectModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  // Application id can be thought as a link to the application
  // changes. By unwrapping it in the computeds below, we
  // make sure that the authority information is in sync with
  // the current actual application.
  var appId = self.disposedPureComputed( function() {
    return lupapisteApp.models.application.id();
  });

  self.canEdit = self.disposedPureComputed( function() {
    return lupapisteApp.models.applicationAuthModel.ok( "assign-application");
  });

  self.assigneeId = ko.observable();

  self.oldAssignee = self.disposedComputed( function() {
    var old = appId()
            ? ko.mapping.toJS( lupapisteApp.models.application.authority())
            : {};
    self.assigneeId( old.id );
    return old;
  });

  self.pending = ko.observable(false);

  self.authorities = ko.observableArray();

  self.fullName = function( obj ) {
    obj = _.defaults( ko.mapping.toJS( obj ), {firstName: "", lastName: ""});

    return obj.lastName + " " + obj.firstName;
  };

  self.disposedComputed( function() {
    if( appId() && self.canEdit() ) {
      ajax.query("application-authorities", {id: appId()})
      .success(function(res) {
        self.authorities( res.authorities );
      })
      .pending(self.pending)
      .call();
    }
  });


  self.disposedComputed( function() {
    var assigneeId = self.assigneeId() || "";
    var oldId = self.oldAssignee().id || "";
    if( _.size( self.authorities()) && self.canEdit()
         && appId() && assigneeId !== oldId) {
      ajax.command("assign-application", {id: appId(), assigneeId: assigneeId})
        .processing(self.pending)
        .success(function() {
          // Full reload is needed. For example, hetus are shown
          // unmasked to the assigned authority.
          repository.load(appId(), self.pending);
        })
        .call();
    }
  });
};
