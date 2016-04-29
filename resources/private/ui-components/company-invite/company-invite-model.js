LUPAPISTE.CompanyInviteModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.isVisible = self.disposedPureComputed(function() {
    return lupapisteApp.models.applicationAuthModel.ok("invite-with-role");
  });

  self.inviteCompany = function() {
    hub.send("show-dialog", {ltitle: "company-invite.add",
                             size: "medium",
                             component: "company-invite-dialog",
                             componentParams: params});
  };
};
