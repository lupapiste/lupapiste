LUPAPISTE.AuthoritySelectDialogModel = function(params) {
  "use strict";
  var self = this;

  // view properties
  self.kind = params.kind;
  self.pending = ko.observable(false);

  // global models
  self.authorization = lupapisteApp.models.applicationAuthModel;
  self.authority = lupapisteApp.models.application.authority;

  // local state
  self.authorities = ko.observable([ko.toJS(self.authority)]);
  self.assigneeId = ko.observable(self.authority().id());

  ajax.query("application-authorities", {id: lupapisteApp.models.application.id()})
    .success(function(resp) {
      self.authorities(resp.authorities);
    })
    .pending(self.pending)
    .call();

  self.save = function() {
    var currentId = lupapisteApp.models.application.id();
    var assigneeId = self.assigneeId() || "";

    if (assigneeId !== self.authority().id()) {
      ajax.command("assign-application", {id: currentId, assigneeId: assigneeId})
        .processing(self.pending)
        .success(function() {
          repository.load(currentId, self.pending, _.partial(hub.send, "close-dialog"));
        })
        .call();
    } else {
      hub.send("close-dialog");
    }
  };


};
