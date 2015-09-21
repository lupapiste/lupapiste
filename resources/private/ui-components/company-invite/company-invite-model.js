LUPAPISTE.CompanyInviteModel = function(params) {
  "use strict";
  var self = this;

  self.isVisible = ko.pureComputed(function() {
    return lupapisteApp.models.applicationAuthModel.ok("invite-with-role");
  });

  self.inviteCompany = function() {
    hub.send("show-dialog", {ltitle: "company-invite.add",
                             size: "medium",
                             component: "company-invite-dialog",
                             componentParams: params});
  };
};
