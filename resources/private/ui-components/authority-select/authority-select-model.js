LUPAPISTE.AuthoritySelectModel = function(params) {
  "use strict";
  var self = this;

  // view properties
  self.kind = params.kind;

  // global models
  self.authorization = lupapisteApp.models.applicationAuthModel;
  self.authority = lupapisteApp.models.application.authority;

  self.edit = _.partial(hub.send, "show-dialog",
                       {ltitle: "application.assignee",
                        size: "small",
                        component: "authority-select-dialog",
                        componentParams: params});

};
